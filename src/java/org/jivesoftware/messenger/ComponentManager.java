package org.jivesoftware.messenger;

import java.util.Map;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import org.jivesoftware.util.StringUtils;
import org.jivesoftware.messenger.spi.PresenceImpl;

public class ComponentManager {
    private Map<String, Component> components = new ConcurrentHashMap<String, Component>();
    private Map<XMPPAddress, XMPPAddress> presenceMap = new ConcurrentHashMap<XMPPAddress, XMPPAddress>();

    static private ComponentManager singleton;
    private final static Object LOCK = new Object();


    /**
     * Returns the singleton instance of <CODE>ComponentManager</CODE>,
     * creating it if necessary.
     * <p/>
     *
     * @return the singleton instance of <Code>UserManager</CODE>
     */
    public static ComponentManager getInstance() {
        // Synchronize on LOCK to ensure that we don't end up creating
        // two singletons.
        synchronized (LOCK) {
            if (null == singleton) {
                ComponentManager controller = new ComponentManager();
                singleton = controller;
                return controller;
            }
        }
        return singleton;
    }

    private ComponentManager() {
    }

    public void addComponent(String jid, Component component){
        jid = validateJID(jid);
        components.put(jid, component);

        // Check for potential interested users.
        checkPresences();
    }

    public void removeComponent(String jid){
        components.remove(validateJID(jid));
    }

    public Component getComponent(String jid){
        if(components.containsKey(validateJID(jid))){
            return components.get(validateJID(jid));
        }
        else {
            String serverName = StringUtils.parseServer(validateJID(jid));
            int index = serverName.indexOf(".");
            if(index != -1){
                String serviceName = serverName.substring(0, index);
                jid = serviceName;
            }
        }
        return components.get(validateJID(jid));
    }

    private String validateJID(String jid){
        jid = jid.trim().toLowerCase();
        return jid;
    }

    public void addPresenceRequest(XMPPAddress prober, XMPPAddress probee){
        presenceMap.put(prober, probee);
    }

    private void checkPresences(){
        for(XMPPAddress prober : presenceMap.keySet()){
            XMPPAddress probee = presenceMap.get(prober);

            Component component = getComponent(probee.toBareStringPrep());
            if(component != null){
                Presence presence = new PresenceImpl();
                presence.setSender(prober);
                presence.setRecipient(probee);
                component.processPacket(presence);
            }
        }
    }
}
