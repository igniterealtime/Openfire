/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.pubsub;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dom4j.Element;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.pep.PEPService;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.util.cache.CacheSizes;
import org.jivesoftware.util.cache.Cacheable;
import org.jivesoftware.util.cache.CannotCalculateSizeException;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

/**
 * A NodeAffiliate keeps information about the affiliation of an entity with a node. Possible
 * affiliations are: owner, publisher, none or outcast. All except for outcast affiliations
 * may have a {@link NodeSubscription} with the node.
 *
 * @author Matt Tucker
 */
public class NodeAffiliate implements Cacheable
{
    private JID jid;
    private Node node;

    private Affiliation affiliation;

    public NodeAffiliate(Node node, JID jid) {
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

    public void setAffiliation(Affiliation affiliation) {
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
     * Sends an event notification for the published items to the affiliate. The event
     * notification may contain zero, one or many published items based on the items
     * included in the original publication. If the affiliate has many subscriptions and
     * many items were published then the affiliate will get a notification for each set
     * of items that affected the same subscriptions.
     *
     * If the affiliate is an owner of the node, and the node is in a PEP service, then
     * all connected resources of the affiliated user will be sent an event notification.
     *
     * @param notification the message to sent to the subscribers. The message will be completed
     *        with the items to include in each notification.
     * @param event the event Element included in the notification message. Passed as an
     *        optimization to avoid future look ups.
     * @param leafNode the leaf node where the items where published.
     * @param publishedItems the list of items that were published. Could be an empty list.
     */
    void sendPublishedNotifications(Message notification, Element event, LeafNode leafNode,
            List<PublishedItem> publishedItems) {

        if (!publishedItems.isEmpty()) {
            Map<List<NodeSubscription>, List<PublishedItem>> itemsBySubs =
                    getItemsBySubscriptions(leafNode, publishedItems);

            // Send one notification for published items that affect the same subscriptions
            for (List<NodeSubscription> nodeSubscriptions : itemsBySubs.keySet()) {
                // Add items information
                Element items = event.addElement("items");
                items.addAttribute("node", getNode().getUniqueIdentifier().getNodeId());
                for (PublishedItem publishedItem : itemsBySubs.get(nodeSubscriptions)) {
                    // FIXME: This was added for compatibility with PEP supporting clients.
                    //        Alternate solution needed when XEP-0163 version > 1.0 is released.
                    //
                    // If the node ID looks like a JID, replace it with the published item's node ID.
                    if (getNode().getUniqueIdentifier().getNodeId().contains("@")) {
                        items.addAttribute("node", publishedItem.getNodeID());                        
                    }

                    // Add item information to the event notification
                    Element item = items.addElement("item");
                    if (leafNode.isItemRequired()) {
                        item.addAttribute("id", publishedItem.getID());
                    }
                    if (leafNode.isPayloadDelivered()) {
                        item.add(publishedItem.getPayload().createCopy());
                    }
                    // Add leaf leafNode information if affiliated leafNode and node
                    // where the item was published are different
                    if (leafNode != getNode()) {
                        item.addAttribute("node", leafNode.getUniqueIdentifier().getNodeId());
                    }
                }
                // Send the event notification
                sendEventNotification(notification, nodeSubscriptions);
                // Remove the added items information
                event.remove(items);
            }
        }
        else {
            // Filter affiliate subscriptions and only use approved and configured ones
            List<NodeSubscription> affectedSubscriptions = new ArrayList<>();
            for (NodeSubscription subscription : getSubscriptions()) {
                if (subscription.canSendPublicationEvent(leafNode, null)) {
                    affectedSubscriptions.add(subscription);
                }
            }
            // Add item information to the event notification
            Element items = event.addElement("items");
            items.addAttribute("node", leafNode.getUniqueIdentifier().getNodeId());
            // Send the event notification
            sendEventNotification(notification, affectedSubscriptions);
            // Remove the added items information
            event.remove(items);
        }

        // TODO this horribly duplicates part of the functionality above. Code-clutter should be reduced.
        // XEP-0136 specifies that all connected resources of the owner of the PEP service should also get a notification.
        if ( leafNode.getService() instanceof PEPService ) {
            final PEPService service = (PEPService) leafNode.getService();
            final Collection<ClientSession> sessions = SessionManager.getInstance().getSessions(service.getAddress().getNode());
            for( final ClientSession session : sessions ) {
                // Add item information to the event notification
                Element items = event.addElement("items");
                items.addAttribute("node", leafNode.getUniqueIdentifier().getNodeId());

                for (PublishedItem publishedItem : publishedItems) {
                    // FIXME: This was added for compatibility with PEP supporting clients.
                    //        Alternate solution needed when XEP-0163 version > 1.0 is released.
                    //
                    // If the node ID looks like a JID, replace it with the published item's node ID.
                    if (getNode().getUniqueIdentifier().getNodeId().contains("@")) {
                        items.addAttribute("node", publishedItem.getNodeID());
                    }

                    // Add item information to the event notification
                    Element item = items.addElement("item");
                    if (leafNode.isItemRequired()) {
                        item.addAttribute("id", publishedItem.getID());
                    }
                    if (leafNode.isPayloadDelivered()) {
                        item.add(publishedItem.getPayload().createCopy());
                    }
                    // Add leaf leafNode information if affiliated leafNode and node
                    // where the item was published are different
                    if (leafNode != getNode()) {
                        item.addAttribute("node", leafNode.getUniqueIdentifier().getNodeId());
                    }
                }

                // Send the event notification
                service.sendNotification(leafNode, notification, session.getAddress());
                // Remove the added items information
                event.remove(items);
            }
        }
    }

    /**
     * Sends an event notification to the affiliate for the deleted items. The event
     * notification may contain one or many published items based on the items included
     * in the original publication. If the affiliate has many subscriptions and many
     * items were deleted then the affiliate will get a notification for each set
     * of items that affected the same subscriptions.
     *
     * @param notification the message to sent to the subscribers. The message will be completed
     *        with the items to include in each notification.
     * @param event the event Element included in the notification message. Passed as an
     *        optimization to avoid future look ups.
     * @param leafNode the leaf node where the items where deleted from.
     * @param publishedItems the list of items that were deleted.
     */
    void sendDeletionNotifications(Message notification, Element event, LeafNode leafNode,
            List<PublishedItem> publishedItems) {

        if (!publishedItems.isEmpty()) {
            Map<List<NodeSubscription>, List<PublishedItem>> itemsBySubs =
                    getItemsBySubscriptions(leafNode, publishedItems);

            // Send one notification for published items that affect the same subscriptions
            for (List<NodeSubscription> nodeSubscriptions : itemsBySubs.keySet()) {
                // Add items information
                Element items = event.addElement("items");
                items.addAttribute("node", leafNode.getUniqueIdentifier().getNodeId());
                for (PublishedItem publishedItem : itemsBySubs.get(nodeSubscriptions)) {
                    // Add retract information to the event notification
                    Element item = items.addElement("retract");
                    if (leafNode.isItemRequired()) {
                        item.addAttribute("id", publishedItem.getID());
                    }
                }
                // Send the event notification
                sendEventNotification(notification, nodeSubscriptions);
                // Remove the added items information
                event.remove(items);
            }
        }
    }

    /**
     * Sends an event notification to each affected subscription of the affiliate. If the owner
     * has many subscriptions from the same full JID then a single notification is going to be
     * sent including a detail of the subscription IDs for which the notification is being sent.<p>
     *
     * Event notifications may include notifications of new published items or of items that
     * were deleted.<p>
     *
     * The original publication to the node may or may not contain a {@link PublishedItem}. The
     * subscriptions of the affiliation will be filtered based on the published item (if one was
     * specified), the subscription status and originating node.
     *
     * @param notification the message to send containing the event notification.
     * @param notifySubscriptions list of subscriptions that were affected and are going to be
     *        included in the notification message. The list should not be empty.
     */
    private void sendEventNotification(Message notification,
            List<NodeSubscription> notifySubscriptions) {
        if (node.isMultipleSubscriptionsEnabled()) {
            // Group subscriptions with the same subscriber JID
            Map<JID, Collection<String>> groupedSubs = new HashMap<>();
            for (NodeSubscription subscription : notifySubscriptions) {
                Collection<String> subIDs = groupedSubs.get(subscription.getJID());
                if (subIDs == null) {
                    subIDs = new ArrayList<>();
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
            // Affiliate should have at most one subscription per unique JID
            if (!notifySubscriptions.isEmpty()) {
                List<JID> subs = new ArrayList<>();
                for(NodeSubscription subscription: notifySubscriptions) {
                    JID sub = subscription.getJID();
                    if (!subs.contains(sub)) {
                        node.sendEventNotification(sub, notification, null);
                        subs.add(sub);
                    }
                }
            }
        }
    }

    private Map<List<NodeSubscription>, List<PublishedItem>> getItemsBySubscriptions(
            LeafNode leafNode, List<PublishedItem> publishedItems) {
        // Identify which subscriptions can receive each item
        Map<PublishedItem, List<NodeSubscription>> subsByItem =
                new HashMap<>();

        // Filter affiliate subscriptions and only use approved and configured ones
        Collection<NodeSubscription> subscriptions = getSubscriptions();
        for (PublishedItem publishedItem : publishedItems) {
            for (NodeSubscription subscription : subscriptions) {
                if (subscription.canSendPublicationEvent(leafNode, publishedItem)) {
                    List<NodeSubscription> nodeSubscriptions = subsByItem.get(publishedItem);
                    if (nodeSubscriptions == null) {
                        nodeSubscriptions = new ArrayList<>();
                        subsByItem.put(publishedItem, nodeSubscriptions);
                    }
                    nodeSubscriptions.add(subscription);
                }
            }
        }

        // Identify which items should be sent together to the same subscriptions
        Map<List<NodeSubscription>, List<PublishedItem>> itemsBySubs =
                new HashMap<>();
        List<PublishedItem> affectedSubscriptions;
        for (PublishedItem publishedItem : subsByItem.keySet()) {
            affectedSubscriptions = itemsBySubs.get(subsByItem.get(publishedItem));
            if (affectedSubscriptions == null) {
                List<PublishedItem> items = new ArrayList<>(publishedItems.size());
                items.add(publishedItem);
                itemsBySubs.put(subsByItem.get(publishedItem), items);
            }
            else {
                affectedSubscriptions.add(publishedItem);
            }
        }
        return itemsBySubs;
    }

    @Override
    public String toString() {
        return super.toString() + " - JID: " + getJID() + " - Affiliation: " +
                getAffiliation().name();
    }

    /**
     * Returns the approximate size of the Object in bytes. The size should be
     * considered to be a best estimate of how much memory the Object occupies
     * and may be based on empirical trials or dynamic calculations.<p>
     *
     * @return the size of the Object in bytes.
     */
    @Override
    public int getCachedSize() throws CannotCalculateSizeException
    {
        int size = CacheSizes.sizeOfObject();
        size += CacheSizes.sizeOfAnything( jid );
        size += CacheSizes.sizeOfInt(); // affiliation
        size += CacheSizes.sizeOfInt(); // reference to node
        return size;
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
        outcast
    }
}
