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

/**
 * <p>An uber router that can handle any packet type.</p>
 * <p>The interface is provided primarily as a convenience for services
 * that must route all packet types (e.g. s2s routing, e2e encryption, etc).</p>
 *
 * @author Iain Shigeoka
 */
public interface PacketRouter extends IQRouter, MessageRouter, PresenceRouter {

    /**
     * <p>Routes the given packet based on packet recipient and sender.</p>
     * <h2>Warning</h2>
     * <p>Be careful to enforce concurrency DbC of concurrent by synchronizing
     * any accesses to class resources.</p>
     *
     * @param packet The packet to route
     * @throws NullPointerException     If the packet is null
     * @throws IllegalArgumentException If the packet is not one of the three XMPP packet types
     */
    public void route(Packet packet) throws IllegalArgumentException, NullPointerException;
}
