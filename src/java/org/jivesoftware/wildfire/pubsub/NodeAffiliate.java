/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.pubsub;

import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import java.util.*;

/**
 * A NodeAffiliate keeps information about the affiliation of an entity with a node. Possible
 * affiliations are: owner, publisher, none or outcast. All except for outcast affiliations
 * may have a {@link NodeSubscription} with the node.
 *
 * @author Matt Tucker
 */
public class NodeAffiliate {

    private JID jid;
    private Node node;

    private Affiliation affiliation;

    NodeAffiliate(Node node, JID jid) {
        this.node = node;
        this.jid = jid;
    }

    public Node getNode() {
        return node;
    }

    public JID getJID() {
        return jid;
    }

    public Affiliation getAffiliation() {
        return affiliation;
    }

    void setAffiliation(Affiliation affiliation) {
        this.affiliation = affiliation;
    }

    /**
     * Returns the list of subscriptions of the affiliate in the node.
     *
     * @return the list of subscriptions of the affiliate in the node.
     */
    public Collection<NodeSubscription> getSubscriptions() {
        return node.getSubscriptions(jid);
    }

    /**
     * Sends an event notification to each affected subscription of the affiliate. If the owner
     * has many subscriptions from the same full JID then a single notification is going to be
     * sent including a detail of the subscription IDs for which the notification is being sent.<p>
     *
     * The original publication to the node may or may not contain a {@link PublishedItem}. The
     * subscriptions of the affiliation will be filtered based on the published item (if one was
     * specified), the subscription status and originating node.
     *
     * @param notification the message to send containing the event notification.
     * @param node the node that received a new publication.
     * @param publishedItem the item was was published in the publication or null if none
     *        was published.
     */
    public void sendEventNotification(Message notification, LeafNode node,
            PublishedItem publishedItem) {
        // Filter affiliate subscriptions and only use approved and configured ones
        List<NodeSubscription> notifySubscriptions = new ArrayList<NodeSubscription>();
        for (NodeSubscription subscription : getSubscriptions()) {
            if (subscription.canSendEventNotification(node, publishedItem)) {
                notifySubscriptions.add(subscription);
            }
        }
        if (node.isMultipleSubscriptionsEnabled()) {
            // Group subscriptions with the same subscriber JID
            Map<JID, Collection<String>> groupedSubs = new HashMap<JID, Collection<String>>();
            for (NodeSubscription subscription : notifySubscriptions) {
                Collection<String> subIDs = groupedSubs.get(subscription.getJID());
                if (subIDs == null) {
                    subIDs = new ArrayList<String>();
                    groupedSubs.put(subscription.getJID(), subIDs);
                }
                subIDs.add(subscription.getID());
            }
            // Send an event notification to each subscriber with a different JID
            for (JID subscriberJID : groupedSubs.keySet()) {
                // Get ID of affected subscriptions
                Collection<String> subIDs = groupedSubs.get(subscriberJID);
                // Send the notification to the subscriber
                node.sendEventNotification(subscriberJID, notification, subIDs);
            }
        }
        else {
            // Affiliate should have at most one subscription so send the notification to
            // the subscriber
            if (!notifySubscriptions.isEmpty()) {
                NodeSubscription subscription = notifySubscriptions.get(0);
                node.sendEventNotification(subscription.getJID(), notification, null);
            }
        }
    }

    public String toString() {
        return super.toString() + " - JID: " + getJID() + " - Affiliation: " +
                getAffiliation().name();
    }

    /**
     * Affiliation with a node defines user permissions.
     */
    public static enum Affiliation {

        /**
         * An owner can publish, delete and purge items as well as configure and delete the node.
         */
        owner,
        /**
         * A publisher can subscribe and publish items to the node.
         */
        publisher,
        /**
         * A user with no affiliation can susbcribe to the node.
         */
        none,
        /**
         * Outcast users are not allowed to subscribe to the node.
         */
        outcast;
    }
}
