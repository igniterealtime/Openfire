/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.protocol.xmpp;

import net.java.sip.communicator.service.protocol.*;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.filter.*;

/**
 * The operation set that allows to deal with {@link XmppConnection} directly.
 *
 * @author Pawel Domas
 */
public interface OperationSetDirectSmackXmpp
    extends OperationSet
{
    /**
     * Returns <tt>XmppConnection</tt> object for the XMPP connection of the
     * <tt>ProtocolProviderService</tt>.
     */
    XmppConnection getXmppConnection();

    /**
     * Adds packet listener and a filter that limits the packets reaching
     * listener object.
     *
     * @param listener the <tt>PacketListener</tt> that will be notified about
     *                 XMPP packets received.
     * @param filter the <tt>PacketFilter</tt> that filters out packets reaching
     *               <tt>listener</tt> object.
     */
    void addPacketHandler(PacketListener listener, PacketFilter filter);

    /**
     * Removes packet listener and the filter applied to it, so that it will no
     * longer be notified about incoming XMPP packets.
     *
     * @param listener the <tt>PacketListener</tt> instance to be removed from
     *                 listeners set.
     */
    void removePacketHandler(PacketListener listener);
}
