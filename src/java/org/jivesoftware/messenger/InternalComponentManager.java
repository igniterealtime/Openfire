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
import org.xmpp.component.*;

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
public class InternalComponentManager implements ComponentManager {

    private Map<String, Component> components = new ConcurrentHashMap<String, Component>();
    private Map<JID, JID> presenceMap = new ConcurrentHashMap<JID, JID>();

    private static InternalComponentManager instance;

    static {
        instance = new InternalComponentManager();
        ComponentManagerFactory.setComponentManager(instance);
    }

    public static InternalComponentManager getInstance() {
        return instance;
    }

    private InternalComponentManager() {
    }

    public void addComponent(String subdomain, Component component) {
        components.put(subdomain, component);

        JID componentJID = new JID(subdomain + "." +
                XMPPServer.getInstance().getServerInfo().getName());

        // Add the route to the new service provided by the component
        XMPPServer.getInstance().getRoutingTable().addRoute(componentJID,
                new RoutableComponent(componentJID, component));

        // Check for potential interested users.
        checkPresences();
    }

    public void removeComponent(String subdomain) {
        components.remove(subdomain);

        JID componentJID = new JID(subdomain + "." +
                XMPPServer.getInstance().getServerInfo().getName());

        // Remove the route for the service provided by the component
        if (XMPPServer.getInstance().getRoutingTable() != null) {
            XMPPServer.getInstance().getRoutingTable().removeRoute(componentJID);
        }
    }

    public void sendPacket(Component component, Packet packet) {
        PacketRouter router;
        router = XMPPServer.getInstance().getPacketRouter();
        if (router != null) {
            router.route(packet);
        }
    }

    public String getProperty(String name) {
        return JiveGlobals.getProperty(name);
    }

    public void setProperty(String name, String value) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isExternalMode() {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Log getLog() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
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

    /**
     * Exposes a Component as a RoutableChannelHandler.
     */
    public static class RoutableComponent implements RoutableChannelHandler {

        private JID jid;
        private Component component;

        public RoutableComponent(JID jid, Component component) {
            this.jid = jid;
            this.component = component;
        }

        public JID getAddress() {
            return jid;
        }

        public void process(Packet packet) throws UnauthorizedException, PacketException {
            component.processPacket(packet);
        }
    }
}