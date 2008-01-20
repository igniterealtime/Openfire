/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire;

import org.xmpp.packet.IQ;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

/**
 * A router that handles incoming packets. Packets will be routed to their
 * corresponding handler. A router is much like a forwarded with some logic
 * to figute out who is the target for each packet.
 *
 * @author Gaston Dombiak
 */
public interface PacketRouter {

    /**
     * Routes the given packet based on its type.
     *
     * @param packet The packet to route.
     */
    void route(Packet packet);

    /**
     * Routes the given IQ packet.
     *
     * @param packet The packet to route.
     */
    void route(IQ packet);

    /**
     * Routes the given Message packet.
     *
     * @param packet The packet to route.
     */
    void route(Message packet);

    /**
     * Routes the given Presence packet.
     *
     * @param packet The packet to route.
     */
    void route(Presence packet);
}
