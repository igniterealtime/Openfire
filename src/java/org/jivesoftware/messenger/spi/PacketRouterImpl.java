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

package org.jivesoftware.messenger.spi;

import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.container.TrackInfo;
import org.jivesoftware.messenger.*;

/**
 * Generic packet routing base class.
 *
 * @author Iain Shigeoka
 */
public class PacketRouterImpl extends BasicModule implements PacketRouter {

    public IQRouter iqRouter;
    public PresenceRouter presenceRouter;
    public MessageRouter messageRouter;

    /**
     * Create a packet router.
     */
    public PacketRouterImpl() {
        super("XMPP Packet Router");
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
    public void route(XMPPPacket packet) {
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
        iqRouter.route(packet);
    }

    public void route(Message packet) {
        messageRouter.route(packet);
    }

    public void route(Presence packet) {
        presenceRouter.route(packet);
    }

    protected TrackInfo getTrackInfo() {
        TrackInfo trackInfo = new TrackInfo();
        trackInfo.getTrackerClasses().put(IQRouter.class, "iqRouter");
        trackInfo.getTrackerClasses().put(MessageRouter.class, "messageRouter");
        trackInfo.getTrackerClasses().put(PresenceRouter.class, "presenceRouter");
        return trackInfo;
    }
}
