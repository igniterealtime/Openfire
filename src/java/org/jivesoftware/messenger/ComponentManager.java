package org.jivesoftware.messenger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jivesoftware.messenger.spi.BasicServer;
import org.jivesoftware.util.StringUtils;
import org.xmpp.packet.Packet;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

/**
 * Manages the registration and delegation of Components. The ComponentManager
 * is responsible for managing registration and delegation of <code>Components</code>,
 * as well as offering a facade around basic server functionallity such as sending and
 * receiving of packets.
 *
 * @author Derek DeMoro
 */
public class ComponentManager {

    private Map<String, Component> components = new ConcurrentHashMap<String, Component>();
    private Map<JID, JID> presenceMap = new ConcurrentHashMap<JID, JID>();

    private static ComponentManager instance = new ComponentManager();


    /**
     * Returns the singleton instance of <CODE>ComponentManager</CODE>,
     * creating it if necessary.
     * <p/>
     *
     * @return the singleton instance of <Code>ComponentManager</CODE>
     */
    public static ComponentManager getInstance() {
        return instance;
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
     *
     * @param prober the jid probing.
     * @param probee the presence being probed.
     */
    public void addPresenceRequest(JID prober, JID probee) {
        presenceMap.put(prober, probee);
    }

    /**
     * Send a packet to the specified recipient. Please note that this sends packets only
     * to outgoing jids and does to the incoming server reader.
     *
     * @param packet the packet to send.
     */
    public void sendPacket(Packet packet) {
        PacketRouter router;
        router = BasicServer.getInstance().getPacketRouter();
        if (router != null) {
            router.route(packet);
        }
    }

    private String validateJID(String jid) {
        jid = jid.trim().toLowerCase();
        return jid;
    }

    private void checkPresences() {
        for (JID prober : presenceMap.keySet()) {
            JID probee = presenceMap.get(prober);

            Component component = getComponent(probee.toBareJID());
            if (component != null) {
                Presence presence = new Presence();
                presence.setFrom(prober);
                presence.setTo(probee);
                component.processPacket(presence);

                // No reason to hold onto prober reference.
                presenceMap.remove(prober);
            }
        }
    }
}