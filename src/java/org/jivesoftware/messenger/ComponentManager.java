/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger;

import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the registration and delegation of Components. The ComponentManager
 * is responsible for managing registration and delegation of {@link Component Components},
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
     * to particular jid. A route for the new Component will be added to
     * the <code>RoutingTable</code> based on the address of the component.
     * Note: The address of the component will be a JID wih empties node and resource.
     *
     * @param jid       the jid to map to.
     * @param component the <code>Component</code> to register.
     */
    public void addComponent(String jid, Component component) {
        jid = new JID(jid).toBareJID();
        components.put(jid, component);

        // Add the route to the new service provided by the component
        XMPPServer.getInstance().getRoutingTable().addRoute(component.getAddress(), component);

        // Check for potential interested users.
        checkPresences();
    }

    /**
     * Removes a <code>Component</code> from the server. The route for the new Component
     * will be removed from the <code>RoutingTable</code>.
     *
     * @param jid the jid mapped to the particular component.
     */
    public void removeComponent(String jid) {
        JID componentJID = new JID(jid);
        components.remove(componentJID.toBareJID());

        // Remove the route for the service provided by the component
        if (XMPPServer.getInstance().getRoutingTable() != null) {
            XMPPServer.getInstance().getRoutingTable().removeRoute(componentJID);
        }
    }

    /**
     * Retrieves the <code>Component</code> which is mapped
     * to the specified JID.
     *
     * @param jid the jid mapped to the component.
     * @return the component with the specified id.
     */
    public Component getComponent(String jid) {
        jid = new JID(jid).toBareJID();
        if (components.containsKey(jid)) {
            return components.get(jid);
        }
        else {
            String serverName = new JID(jid).getDomain();
            int index = serverName.indexOf(".");
            if (index != -1) {
                String serviceName = serverName.substring(0, index);
                jid = serviceName;
            }
        }
        return components.get(jid);
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
        router = XMPPServer.getInstance().getPacketRouter();
        if (router != null) {
            router.route(packet);
        }
    }

    private void checkPresences() {
        for (JID prober : presenceMap.keySet()) {
            JID probee = presenceMap.get(prober);

            Component component = getComponent(probee.toBareJID());
            if (component != null) {
                Presence presence = new Presence();
                presence.setFrom(prober);
                presence.setTo(probee);
                try {
                    component.process(presence);

                    // No reason to hold onto prober reference.
                    presenceMap.remove(prober);
                }
                catch (UnauthorizedException e) {
                    // Do nothing. This error should never occur
                }

            }
        }
    }
}