/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.protocol.xmpp;

import org.jivesoftware.smack.packet.*;

/**
 * Class used to listen for subscription updates through
 * {@link org.jitsi.protocol.xmpp.OperationSetSubscription}.
 *
 * @author Pawel Domas
 */
public interface SubscriptionListener
{
    /**
     * Callback called when update is received on some subscription node.
     *
     * @param node the source node of the event.
     * @param payload the payload of notification.
     */
    void onSubscriptionUpdate(String node, PacketExtension payload);
}
