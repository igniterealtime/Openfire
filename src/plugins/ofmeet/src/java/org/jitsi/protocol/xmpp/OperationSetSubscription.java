/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.protocol.xmpp;

import net.java.sip.communicator.service.protocol.*;

/**
 * Operation set exposes underlying protocol's subscription for notifications.
 * In case of XMPP this is pub-sub nodes which is currently used by
 * {@link org.jitsi.jicofo.BridgeSelector}.
 *
 * @author Pawel Domas
 */
public interface OperationSetSubscription
    extends OperationSet
{
    /**
     * Subscribes to given <tt>node</tt> for notifications.
     *
     * @param node the of the node to which given listener will be subscribed to
     * @param listener the {@link SubscriptionListener} instance that will be
     *                 notified of updates from the node.
     */
    void subscribe(String node, SubscriptionListener listener);

    /**
     * Cancels subscriptions for given <tt>node</tt>.
     * @param node the nod for which subscription wil be cancelled.
     */
    void unSubscribe(String node);
}
