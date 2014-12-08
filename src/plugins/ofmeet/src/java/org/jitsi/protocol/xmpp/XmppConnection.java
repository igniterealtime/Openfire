/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.protocol.xmpp;

import org.jivesoftware.smack.packet.*;

/**
 * The interface for Smack XMPP connection.
 *
 * @author Pawel Domas
 */
public interface XmppConnection
{
    /**
     * Sends given XMPP packet through this connection.
     *
     * @param packet the packet to be sent.
     */
    void sendPacket(Packet packet);

    /**
     * Sends the packet and wait for reply in blocking mode.
     *
     * @param packet the packet to be sent.
     *
     * @return the response packet received within the time limit
     *         or <tt>null</tt> if no response was collected.
     */
    Packet sendPacketAndGetReply(Packet packet);
}
