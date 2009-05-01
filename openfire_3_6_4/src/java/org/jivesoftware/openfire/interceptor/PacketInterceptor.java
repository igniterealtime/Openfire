/**
 * $RCSfile: PacketInterceptor.java,v $
 * $Revision: 3010 $
 * $Date: 2005-10-31 20:28:11 -0300 (Mon, 31 Oct 2005) $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.interceptor;

import org.jivesoftware.openfire.session.Session;
import org.xmpp.packet.Packet;

/**
 * A packet interceptor encapsulates an action that is invoked on a packet immediately
 * before or after it was received by a SocketReader and also when the packet is about to
 * be sent in SocketConnection. These types of actions fall into two broad categories:<ul>
 *      <li> Interceptors that reject the packet by throwing an exception (only when the packet
 *            has not been processed yet).
 *      <li> Interceptors that dynamically transform the packet content.
 * </ul>
 *
 * Any number of interceptors can be installed and removed at run-time. They can be installed
 * globally or per-user. Global interceptors are run first, followed by any that are installed
 * for the username.<p>
 *
 * @see InterceptorManager
 * @author Gaston Dombiak
 */
public interface PacketInterceptor {

    /**
     * Invokes the interceptor on the specified packet. The interceptor can either modify
     * the packet, or throw a PacketRejectedException to block it from being sent or processed
     * (when read).<p>
     *
     * An exception can only be thrown when <tt>processed</tt> is false which means that the read
     * packet has not been processed yet or the packet was not sent yet. If the exception is thrown
     * with a "read" packet then the sender of the packet will receive an answer with an error. But
     * if the exception is thrown with a "sent" packet then nothing will happen.<p>
     *
     * Note that for each packet, every interceptor will be called twice: once before processing
     * is complete (<tt>processing==true</tt>) and once after processing is complete. Typically,
     * an interceptor will want to ignore one or the other case.
     *
     * @param packet the packet to take action on.
     * @param session the session that received or is sending the packet.
     * @param incoming flag that indicates if the packet was read by the server or sent from
     *      the server.
     * @param processed flag that indicates if the action (read/send) was performed. (PRE vs. POST).
     * @throws PacketRejectedException if the packet should be prevented from being processed.
     */
    void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed)
            throws PacketRejectedException;
}
