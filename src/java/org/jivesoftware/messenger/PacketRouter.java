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

import org.xmpp.packet.Packet;
import org.xmpp.packet.Message;
import org.xmpp.packet.Presence;
import org.xmpp.packet.IQ;
import org.jivesoftware.messenger.container.BasicModule;

/**
 * <p>An uber router that can handle any packet type.</p>
 * <p>The interface is provided primarily as a convenience for services
 * that must route all packet types (e.g. s2s routing, e2e encryption, etc).</p>
 *
 * @author Iain Shigeoka
 */
public class PacketRouter extends BasicModule {

    private IQRouter iqRouter;
    private PresenceRouter presenceRouter;
    private MessageRouter messageRouter;

    /**
     * Initialize ComponentManager to handle delegation of packets.
     */
    private ComponentManager componentManager;

    /**
     * Constructs a packet router.
     */
    public PacketRouter() {
        super("XMPP Packet Router");

        componentManager = ComponentManager.getInstance();
    }

    /**
     * Routes the given packet based on packet recipient and sender. The
     * router defers actual routing decisions to other classes.
     * <h2>Warning</h2>
     * Be careful to enforce concurrency DbC of concurrent by synchronizing
     * any accesses to class resources.
     *
     * @param packet The packet to route
     * @throws NullPointerException If the packet is null or the packet could not be routed
     */
    public void route(Packet packet) {
        if(hasRouted(packet)){
            return;
        }

        if (packet instanceof Message) {
            route((Message)packet);
        }
        else if (packet instanceof Presence) {
            route((Presence)packet);
        }
        else if (packet instanceof IQ) {
            route((IQ)packet);
        }
        else {
            throw new IllegalArgumentException();
        }
    }

    public void route(IQ packet) {
        if (!hasRouted(packet)){
          iqRouter.route(packet);
        }
    }

    public void route(Message packet) {
        if (!hasRouted(packet)){
           messageRouter.route(packet);
        }
    }

    public void route(Presence packet) {
        if (!hasRouted(packet)) {
          presenceRouter.route(packet);
        }
    }

    public boolean hasRouted(Packet packet){
        if (packet.getTo() == null) {
            return false;
        }
         // Check for registered components
        Component component = componentManager.getComponent(packet.getTo().toBareJID());
        if (component != null) {
            component.processPacket(packet);
            return true;
        }
        return false;
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);
        iqRouter = server.getIQRouter();
        messageRouter = server.getMessageRouter();
        presenceRouter = server.getPresenceRouter();
    }
}