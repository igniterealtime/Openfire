/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */

package org.jivesoftware.messenger;

import org.jivesoftware.messenger.auth.UnauthorizedException;

/**
 * Interface to handle packets delivered by Channels.
 *
 * @author Matt Tucker
 */
public interface ChannelHandler<T extends XMPPPacket> {

    /**
     * Process an XMPP packet.
     *
     * @param packet a packet to process.
     * @throws UnauthorizedException thrown if the packet's sender lacks authorization
     *      to access resources (will result in uniform unauthorized access error reply).
     * @throws PacketException thrown if the packet is malformed (results in the sender's
     *      session being shutdown).
     */
    public abstract void process(T packet) throws UnauthorizedException, PacketException;
}