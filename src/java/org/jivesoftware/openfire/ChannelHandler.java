/**
 * $RCSfile$
 * $Revision: 1200 $
 * $Date: 2005-04-04 03:36:48 -0300 (Mon, 04 Apr 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire;

import org.xmpp.packet.Packet;
import org.jivesoftware.openfire.auth.UnauthorizedException;

/**
 * Interface to handle packets delivered by Channels.
 *
 * @author Matt Tucker
 */
public interface ChannelHandler<T extends Packet> {

    /**
     * Process an XMPP packet.
     *
     * @param packet a packet to process.
     * @throws UnauthorizedException if not allowed to process the packet.
     * @throws PacketException thrown if the packet is malformed (results in the sender's
     *      session being shutdown).
     */
    public abstract void process(T packet) throws UnauthorizedException, PacketException;
}