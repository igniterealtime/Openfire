package org.jivesoftware.messenger;

import org.jivesoftware.messenger.container.ServiceLookupFactory;
import org.jivesoftware.messenger.spi.PacketDelivererImpl;
import org.jivesoftware.messenger.spi.PresenceImpl;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>Manages the registration and delegation of Components.</p>
 * <p/>
 * <p>The ComponentManager is responsible for managing registration and delegation of <code>Components</code>,
 * as well as offering a facade around basic server functionallity such as sending and receiving of
 * packets.
 *
 * @author Derek DeMoro
 */
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
     * @return the singleton instance of <Code>ComponentManager</CODE>
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

    /**
     * Registers a <code>Component</code> with the server and maps
     * to particular jid.
     *
     * @param jid       the jid to map to.
     * @param component the <code>Component</code> to register.
     */
    public void addComponent(String jid, Component component) {
        jid = validateJID(jid);
        components.put(jid, component);

        // Check for potential interested users.
        checkPresences();
    }

    /**
     * Removes a <code>Component</code> from the server.
     *
     * @param jid the jid mapped to the particular component.
     */
    public void removeComponent(String jid) {
        components.remove(validateJID(jid));
    }

    /**
     * Retrieves the <code>Component</code> which is mapped
     * to the specified JID.
     *
     * @param jid the jid mapped to the component.
     * @return
     */
    public Component getComponent(String jid) {
        if (components.containsKey(validateJID(jid))) {
            return components.get(validateJID(jid));
        }
        else {
            String serverName = StringUtils.parseServer(validateJID(jid));
            int index = serverName.indexOf(".");
            if (index != -1) {
                String serviceName = serverName.substring(0, index);
                jid = serviceName;
            }
        }
        return components.get(validateJID(jid));
    }

    /**
     * Registers Probeers who have not yet been serviced.
     * @param prober the jid probing.
     * @param probee the presence being probed.
     */
    public void addPresenceRequest(XMPPAddress prober, XMPPAddress probee) {
        presenceMap.put(prober, probee);
    }

    /**
     * Send a packet to the specified recipient. Please note that this sends packets only
     * to outgoing jids and does to the incoming server reader.
     * @param packet the packet to send.
     */
    public void sendPacket(XMPPPacket packet) {
        try {
            PacketDelivererImpl deliverer = (PacketDelivererImpl) ServiceLookupFactory.getLookup().lookup(PacketDelivererImpl.class);
            if (deliverer != null) {
                // Hand off to the delegated deliverer
                deliverer.deliver(packet);
            }
        }
        catch (Exception e) {
            Log.error("Unable to deliver packet", e);
        }
    }


    private String validateJID(String jid) {
        jid = jid.trim().toLowerCase();
        return jid;
    }


    private void checkPresences() {
        for (XMPPAddress prober : presenceMap.keySet()) {
            XMPPAddress probee = presenceMap.get(prober);

            Component component = getComponent(probee.toBareStringPrep());
            if (component != null) {
                Presence presence = new PresenceImpl();
                presence.setSender(prober);
                presence.setRecipient(probee);
                component.processPacket(presence);

                // No reason to hold onto prober reference.
                presenceMap.remove(prober);
            }
        }
    }

}
