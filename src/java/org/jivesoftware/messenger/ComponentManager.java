package org.jivesoftware.messenger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jivesoftware.util.StringUtils;

public class ComponentManager {
    private Map<String, Component> components = new ConcurrentHashMap<String, Component>();

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
}
