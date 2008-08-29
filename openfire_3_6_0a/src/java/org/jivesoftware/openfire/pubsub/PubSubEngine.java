/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.pubsub;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.XMPPServerListener;
import org.jivesoftware.openfire.pubsub.models.AccessModel;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A PubSubEngine is responsible for handling packets sent to a pub-sub service.
 *
 * @author Matt Tucker
 */
public class PubSubEngine {

    /**
     * The packet router for the server.
     */
    private PacketRouter router = null;

    public PubSubEngine(PacketRouter router) {
        this.router = router;
    }

    /**
     * Handles IQ packets sent to the pubsub service. Requests of disco#info and disco#items
     * are not being handled by the engine. Instead the service itself should handle disco packets.
     *
     * @param service the PubSub service this action is to be performed for.
     * @param iq the IQ packet sent to the pubsub service.
     * @return true if the IQ packet was handled by the engine.
     */
    public boolean process(PubSubService service, IQ iq) {
        // Ignore IQs of type ERROR or RESULT
        if (IQ.Type.error == iq.getType() || IQ.Type.result == iq.getType()) {
            return true;
        }
        Element childElement = iq.getChildElement();
        String namespace = null;

        if (childElement != null) {
            namespace = childElement.getNamespaceURI();
        }
        if ("http://jabber.org/protocol/pubsub".equals(namespace)) {
            Element action = childElement.element("publish");
            if (action != null) {
                // Entity publishes an item
                publishItemsToNode(service, iq, action);
                return true;
            }
            action = childElement.element("subscribe");
            if (action != null) {
                // Entity subscribes to a node
                subscribeNode(service, iq, childElement, action);
                return true;
            }
            action = childElement.element("options");
            if (action != null) {
                if (IQ.Type.get == iq.getType()) {
                    // Subscriber requests subscription options form
                    getSubscriptionConfiguration(service, iq, childElement, action);
                }
                else {
                    // Subscriber submits completed options form
                    configureSubscription(service, iq, action);
                }
                return true;
            }
            action = childElement.element("create");
            if (action != null) {
                // Entity is requesting to create a new node
                createNode(service, iq, childElement, action);
                return true;
            }
            action = childElement.element("unsubscribe");
            if (action != null) {
                // Entity unsubscribes from a node
                unsubscribeNode(service, iq, action);
                return true;
            }
            action = childElement.element("subscriptions");
            if (action != null) {
                // Entity requests all current subscriptions
                getSubscriptions(service, iq, childElement);
                return true;
            }
            action = childElement.element("affiliations");
            if (action != null) {
                // Entity requests all current affiliations
                getAffiliations(service, iq, childElement);
                return true;
            }
            action = childElement.element("items");
            if (action != null) {
                // Subscriber requests all active items
                getPublishedItems(service, iq, action);
                return true;
            }
            action = childElement.element("retract");
            if (action != null) {
                // Entity deletes an item
                deleteItems(service, iq, action);
                return true;
            }
            // Unknown action requested
            sendErrorPacket(iq, PacketError.Condition.bad_request, null);
            return true;
        }
        else if ("http://jabber.org/protocol/pubsub#owner".equals(namespace)) {
            Element action = childElement.element("configure");
            if (action != null) {
                String nodeID = action.attributeValue("node");
                if (nodeID == null) {
                    // if user is not sysadmin then return nodeid-required error
                    if (!service.isServiceAdmin(iq.getFrom()) ||
                            !service.isCollectionNodesSupported()) {
                        // Configure elements must have a node attribute so answer an error
                        Element pubsubError = DocumentHelper.createElement(QName.get(
                                "nodeid-required", "http://jabber.org/protocol/pubsub#errors"));
                        sendErrorPacket(iq, PacketError.Condition.bad_request, pubsubError);
                        return true;
                    }
                    else {
                        // Sysadmin is trying to configure root collection node
                        nodeID = service.getRootCollectionNode().getNodeID();
                    }
                }
                if (IQ.Type.get == iq.getType()) {
                    // Owner requests configuration form of a node
                    getNodeConfiguration(service, iq, childElement, nodeID);
                }
                else {
                    // Owner submits or cancels node configuration form
                    configureNode(service, iq, action, nodeID);
                }
                return true;
            }
            action = childElement.element("default");
            if (action != null) {
                // Owner requests default configuration options for
                // leaf or collection nodes
                getDefaultNodeConfiguration(service, iq, childElement, action);
                return true;
            }
            action = childElement.element("delete");
            if (action != null) {
                // Owner deletes a node
                deleteNode(service, iq, action);
                return true;
            }
            action = childElement.element("subscriptions");
            if (action != null) {
                if (IQ.Type.get == iq.getType()) {
                    // Owner requests all affiliated entities
                    getNodeSubscriptions(service, iq, action);
                }
                else {
                    modifyNodeSubscriptions(service, iq, action);
                }
                return true;
            }
            action = childElement.element("affiliations");
            if (action != null) {
                if (IQ.Type.get == iq.getType()) {
                    // Owner requests all affiliated entities
                    getNodeAffiliations(service, iq, action);
                }
                else {
                    modifyNodeAffiliations(service, iq, action);
                }
                return true;
            }
            action = childElement.element("purge");
            if (action != null) {
                // Owner purges items from a node
                purgeNode(service, iq, action);
                return true;
            }
            // Unknown action requested so return error to sender
            sendErrorPacket(iq, PacketError.Condition.bad_request, null);
            return true;
        }
        else if ("http://jabber.org/protocol/commands".equals(namespace)) {
            // Process ad-hoc command
            IQ reply = service.getManager().process(iq);
            router.route(reply);
            return true;
        }
        return false;
    }

    /**
     * Handles Presence packets sent to the pubsub service. Only process available and not
     * available presences.
     *
     * @param service the PubSub service this action is to be performed for.
     * @param presence the Presence packet sent to the pubsub service.
     */
    public void process(PubSubService service, Presence presence) {
        if (presence.isAvailable()) {
            JID subscriber = presence.getFrom();
            Map<String, String> fullPresences = service.getBarePresences().get(subscriber.toBareJID());
            if (fullPresences == null) {
                synchronized (subscriber.toBareJID().intern()) {
                    fullPresences = service.getBarePresences().get(subscriber.toBareJID());
                    if (fullPresences == null) {
                        fullPresences = new ConcurrentHashMap<String, String>();
                        service.getBarePresences().put(subscriber.toBareJID(), fullPresences);
                    }
                }
            }
            Presence.Show show = presence.getShow();
            fullPresences.put(subscriber.toString(), show == null ? "online" : show.name());
        }
        else if (presence.getType() == Presence.Type.unavailable) {
            JID subscriber = presence.getFrom();
            Map<String, String> fullPresences = service.getBarePresences().get(subscriber.toBareJID());
            if (fullPresences != null) {
                fullPresences.remove(subscriber.toString());
                if (fullPresences.isEmpty()) {
                    service.getBarePresences().remove(subscriber.toBareJID());
                }
            }
        }
    }

    /**
     * Handles Message packets sent to the pubsub service. Messages may be of type error
     * when an event notification was sent to a susbcriber whose address is no longer available.<p>
     *
     * Answers to authorization requests sent to node owners to approve pending subscriptions
     * will also be processed by this method.
     *
     * @param service the PubSub service this action is to be performed for.
     * @param message the Message packet sent to the pubsub service.
     */
    public void process(PubSubService service, Message message) {
        if (message.getType() == Message.Type.error) {
            // Process Messages of type error to identify possible subscribers that no longer exist
            if (message.getError().getType() == PacketError.Type.cancel) {
                // TODO Assuming that owner is the bare JID (as defined in the JEP). This can be replaced with an explicit owner specified in the packet
                JID owner = new JID(message.getFrom().toBareJID());
                // Terminate the subscription of the entity to all nodes hosted at the service
               cancelAllSubscriptions(service, owner);
            }
            else if (message.getError().getType() == PacketError.Type.auth) {
                // TODO Queue the message to be sent again later (will retry a few times and
                // will be discarded when the retry limit is reached)
            }
        }
        else if (message.getType() == Message.Type.normal) {
            // Check that this is an answer to an authorization request
            DataForm authForm = (DataForm) message.getExtension("x", "jabber:x:data");
            if (authForm != null && authForm.getType() == DataForm.Type.submit) {
                String formType = authForm.getField("FORM_TYPE").getValues().get(0);
                // Check that completed data form belongs to an authorization request
                if ("http://jabber.org/protocol/pubsub#subscribe_authorization".equals(formType)) {
                    // Process the answer to the authorization request
                    processAuthorizationAnswer(service, authForm, message);
                }
            }
        }
    }

    private void publishItemsToNode(PubSubService service, IQ iq, Element publishElement) {
        String nodeID = publishElement.attributeValue("node");
        Node node;
        if (nodeID == null) {
            // No node was specified. Return bad_request error
            Element pubsubError = DocumentHelper.createElement(QName.get(
                    "nodeid-required", "http://jabber.org/protocol/pubsub#errors"));
            sendErrorPacket(iq, PacketError.Condition.bad_request, pubsubError);
            return;
        }
        else {
            // Look for the specified node
            node = service.getNode(nodeID);
            if (node == null) {
                // Node does not exist. Return item-not-found error
                sendErrorPacket(iq, PacketError.Condition.item_not_found, null);
                return;
            }
        }

        JID from = iq.getFrom();
        // TODO Assuming that owner is the bare JID (as defined in the JEP). This can be replaced with an explicit owner specified in the packet
        JID owner = new JID(from.toBareJID());
        if (!node.getPublisherModel().canPublish(node, owner) && !service.isServiceAdmin(owner)) {
            // Entity does not have sufficient privileges to publish to node
            sendErrorPacket(iq, PacketError.Condition.forbidden, null);
            return;
        }

        if (node.isCollectionNode()) {
            // Node is a collection node. Return feature-not-implemented error
            Element pubsubError = DocumentHelper.createElement(
                    QName.get("unsupported", "http://jabber.org/protocol/pubsub#errors"));
            pubsubError.addAttribute("feature", "publish");
            sendErrorPacket(iq, PacketError.Condition.feature_not_implemented, pubsubError);
            return;
        }

        LeafNode leafNode = (LeafNode) node;
        Iterator itemElements = publishElement.elementIterator("item");

        // Check that an item was included if node persist items or includes payload
        if (!itemElements.hasNext() && leafNode.isItemRequired()) {
            Element pubsubError = DocumentHelper.createElement(QName.get(
                    "item-required", "http://jabber.org/protocol/pubsub#errors"));
            sendErrorPacket(iq, PacketError.Condition.bad_request, pubsubError);
            return;
        }
        // Check that no item was included if node doesn't persist items and doesn't
        // includes payload
        if (itemElements.hasNext() && !leafNode.isItemRequired()) {
            Element pubsubError = DocumentHelper.createElement(QName.get(
                    "item-forbidden", "http://jabber.org/protocol/pubsub#errors"));
            sendErrorPacket(iq, PacketError.Condition.bad_request, pubsubError);
            return;
        }
        List<Element> items = new ArrayList<Element>();
        List entries;
        Element payload;
        while (itemElements.hasNext()) {
            Element item = (Element) itemElements.next();
            entries = item.elements();
            payload = entries.isEmpty() ? null : (Element) entries.get(0);
            // Check that a payload was included if node is configured to include payload
            // in notifications
            if (payload == null && leafNode.isPayloadDelivered()) {
                Element pubsubError = DocumentHelper.createElement(QName.get(
                        "payload-required", "http://jabber.org/protocol/pubsub#errors"));
                sendErrorPacket(iq, PacketError.Condition.bad_request, pubsubError);
                return;
            }
            // Check that the payload (if any) contains only one child element
            if (entries.size() > 1) {
                Element pubsubError = DocumentHelper.createElement(QName.get(
                        "invalid-payload", "http://jabber.org/protocol/pubsub#errors"));
                sendErrorPacket(iq, PacketError.Condition.bad_request, pubsubError);
                return;
            }
            items.add(item);
        }

        // Return success operation
        router.route(IQ.createResultIQ(iq));
        // Publish item and send event notifications to subscribers
        leafNode.publishItems(from, items);
    }

    private void deleteItems(PubSubService service, IQ iq, Element retractElement) {
        String nodeID = retractElement.attributeValue("node");
        Node node;
        if (nodeID == null) {
            // No node was specified. Return bad_request error
            Element pubsubError = DocumentHelper.createElement(QName.get(
                    "nodeid-required", "http://jabber.org/protocol/pubsub#errors"));
            sendErrorPacket(iq, PacketError.Condition.bad_request, pubsubError);
            return;
        }
        else {
            // Look for the specified node
            node = service.getNode(nodeID);
            if (node == null) {
                // Node does not exist. Return item-not-found error
                sendErrorPacket(iq, PacketError.Condition.item_not_found, null);
                return;
            }
        }
        // Get the items to delete
        Iterator itemElements = retractElement.elementIterator("item");
        if (!itemElements.hasNext()) {
            Element pubsubError = DocumentHelper.createElement(QName.get(
                    "item-required", "http://jabber.org/protocol/pubsub#errors"));
            sendErrorPacket(iq, PacketError.Condition.bad_request, pubsubError);
            return;
        }

        if (node.isCollectionNode()) {
            // Cannot delete items from a collection node. Return an error.
            Element pubsubError = DocumentHelper.createElement(QName.get(
                    "unsupported", "http://jabber.org/protocol/pubsub#errors"));
            pubsubError.addAttribute("feature", "persistent-items");
            sendErrorPacket(iq, PacketError.Condition.feature_not_implemented, pubsubError);
            return;
        }
        LeafNode leafNode = (LeafNode) node;

        if (!leafNode.isItemRequired()) {
            // Cannot delete items from a leaf node that doesn't handle itemIDs. Return an error.
            Element pubsubError = DocumentHelper.createElement(QName.get(
                    "unsupported", "http://jabber.org/protocol/pubsub#errors"));
            pubsubError.addAttribute("feature", "persistent-items");
            sendErrorPacket(iq, PacketError.Condition.feature_not_implemented, pubsubError);
            return;
        }

        List<PublishedItem> items = new ArrayList<PublishedItem>();
        while (itemElements.hasNext()) {
            Element itemElement = (Element) itemElements.next();
            String itemID = itemElement.attributeValue("id");
            if (itemID != null) {
                PublishedItem item = node.getPublishedItem(itemID);
                if (item == null) {
                    // ItemID does not exist. Return item-not-found error
                    sendErrorPacket(iq, PacketError.Condition.item_not_found, null);
                    return;
                }
                else {
                    if (item.canDelete(iq.getFrom())) {
                        items.add(item);
                    }
                    else {
                        // Publisher does not have sufficient privileges to delete this item
                        sendErrorPacket(iq, PacketError.Condition.forbidden, null);
                        return;
                    }
                }
            }
            else {
                // No item ID was specified so return a bad_request error
                Element pubsubError = DocumentHelper.createElement(QName.get(
                        "item-required", "http://jabber.org/protocol/pubsub#errors"));
                sendErrorPacket(iq, PacketError.Condition.bad_request, pubsubError);
                return;
            }
        }
        // Send reply with success
        router.route(IQ.createResultIQ(iq));
        // Delete items and send subscribers a notification
        leafNode.deleteItems(items);
    }

    private void subscribeNode(PubSubService service, IQ iq, Element childElement, Element subscribeElement) {
        String nodeID = subscribeElement.attributeValue("node");
        Node node;
        if (nodeID == null) {
            if (service.isCollectionNodesSupported()) {
                // Entity subscribes to root collection node
                node = service.getRootCollectionNode();
            }
            else {
                // Service does not have a root collection node so return a nodeid-required error
                Element pubsubError = DocumentHelper.createElement(QName.get(
                        "nodeid-required", "http://jabber.org/protocol/pubsub#errors"));
                sendErrorPacket(iq, PacketError.Condition.bad_request, pubsubError);
                return;
            }
        }
        else {
            // Look for the specified node
            node = service.getNode(nodeID);
            if (node == null) {
                // Node does not exist. Return item-not-found error
                sendErrorPacket(iq, PacketError.Condition.item_not_found, null);
                return;
            }
        }
        // Check if sender and subscriber JIDs match or if a valid "trusted proxy" is being used
        JID from = iq.getFrom();
        JID subscriberJID = new JID(subscribeElement.attributeValue("jid"));
        if (!from.toBareJID().equals(subscriberJID.toBareJID()) && !service.isServiceAdmin(from)) {
            // JIDs do not match and requestor is not a service admin so return an error
            Element pubsubError = DocumentHelper.createElement(
                    QName.get("invalid-jid", "http://jabber.org/protocol/pubsub#errors"));
            sendErrorPacket(iq, PacketError.Condition.bad_request, pubsubError);
            return;
        }
        // TODO Assumed that the owner of the subscription is the bare JID of the subscription JID. Waiting StPeter answer for explicit field.
        JID owner = new JID(subscriberJID.toBareJID());
        // Check if the node's access model allows the subscription to proceed
        AccessModel accessModel = node.getAccessModel();
        if (!accessModel.canSubscribe(node, owner, subscriberJID)) {
            sendErrorPacket(iq, accessModel.getSubsriptionError(),
                    accessModel.getSubsriptionErrorDetail());
            return;
        }
        // Check if the subscriber is an anonymous user
        if (!UserManager.getInstance().isRegisteredUser(subscriberJID)) {
            // Anonymous users cannot subscribe to the node. Return forbidden error
            sendErrorPacket(iq, PacketError.Condition.forbidden, null);
            return;
        }
        // Check if the subscription owner is a user with outcast affiliation
        NodeAffiliate nodeAffiliate = node.getAffiliate(owner);
        if (nodeAffiliate != null &&
                nodeAffiliate.getAffiliation() == NodeAffiliate.Affiliation.outcast) {
            // Subscriber is an outcast. Return forbidden error
            sendErrorPacket(iq, PacketError.Condition.forbidden, null);
            return;
        }
        // Check that subscriptions to the node are enabled
        if (!node.isSubscriptionEnabled() && !service.isServiceAdmin(from)) {
            // Sender is not a sysadmin and subscription is disabled so return an error
            sendErrorPacket(iq, PacketError.Condition.not_allowed, null);
            return;
        }

        // Get any configuration form included in the options element (if any)
        DataForm optionsForm = null;
        Element options = childElement.element("options");
        if (options != null) {
            Element formElement = options.element(QName.get("x", "jabber:x:data"));
            if (formElement != null) {
                optionsForm = new DataForm(formElement);
            }
        }

        // If leaf node does not support multiple subscriptions then check whether subscriber is
        // creating another subscription or not
        if (!node.isCollectionNode() && !node.isMultipleSubscriptionsEnabled()) {
            NodeSubscription existingSubscription = node.getSubscription(subscriberJID);
            if (existingSubscription != null) {
                // User is trying to create another subscription so
                // return current subscription state
                existingSubscription.sendSubscriptionState(iq);
                return;
            }
        }

        // Check if subscribing twice to a collection node using same subscription type
        if (node.isCollectionNode()) {
            // By default assume that new subscription is of type node
            boolean isNodeType = true;
            if (optionsForm != null) {
                FormField field = optionsForm.getField("pubsub#subscription_type");
                if (field != null) {
                    if ("items".equals(field.getValues().get(0))) {
                        isNodeType = false;
                    }
                }
            }
            if (nodeAffiliate != null) {
                for (NodeSubscription subscription : nodeAffiliate.getSubscriptions()) {
                    if (isNodeType) {
                        // User is requesting a subscription of type "nodes"
                        if (NodeSubscription.Type.nodes == subscription.getType()) {
                            // Cannot have 2 subscriptions of the same type. Return conflict error
                            sendErrorPacket(iq, PacketError.Condition.conflict, null);
                            return;
                        }
                    }
                    else if (!node.isMultipleSubscriptionsEnabled()) {
                        // User is requesting a subscription of type "items" and
                        // multiple subscriptions is not allowed
                        if (NodeSubscription.Type.items == subscription.getType()) {
                            // User is trying to create another subscription so
                            // return current subscription state
                            subscription.sendSubscriptionState(iq);
                            return;
                        }
                    }
                }
            }
        }

        // Create a subscription and an affiliation if the subscriber doesn't have one
        node.createSubscription(iq, owner, subscriberJID, accessModel.isAuthorizationRequired(),
                optionsForm);
    }

    private void unsubscribeNode(PubSubService service, IQ iq, Element unsubscribeElement) {
        String nodeID = unsubscribeElement.attributeValue("node");
        String subID = unsubscribeElement.attributeValue("subid");
        Node node;
        if (nodeID == null) {
            if (service.isCollectionNodesSupported()) {
                // Entity unsubscribes from root collection node
                node = service.getRootCollectionNode();
            }
            else {
                // Service does not have a root collection node so return a nodeid-required error
                Element pubsubError = DocumentHelper.createElement(QName.get(
                        "nodeid-required", "http://jabber.org/protocol/pubsub#errors"));
                sendErrorPacket(iq, PacketError.Condition.bad_request, pubsubError);
                return;
            }
        }
        else {
            // Look for the specified node
            node = service.getNode(nodeID);
            if (node == null) {
                // Node does not exist. Return item-not-found error
                sendErrorPacket(iq, PacketError.Condition.item_not_found, null);
                return;
            }
        }
        NodeSubscription subscription;
        if (node.isMultipleSubscriptionsEnabled()) {
            if (subID == null) {
                // No subid was specified and the node supports multiple subscriptions
                Element pubsubError = DocumentHelper.createElement(
                        QName.get("subid-required", "http://jabber.org/protocol/pubsub#errors"));
                sendErrorPacket(iq, PacketError.Condition.bad_request, pubsubError);
                return;
            }
            else {
                // Check if the specified subID belongs to an existing node subscription
                subscription = node.getSubscription(subID);
                if (subscription == null) {
                    Element pubsubError = DocumentHelper.createElement(
                            QName.get("invalid-subid", "http://jabber.org/protocol/pubsub#errors"));
                    sendErrorPacket(iq, PacketError.Condition.not_acceptable, pubsubError);
                    return;
                }
            }
        }
        else {
            String jidAttribute = unsubscribeElement.attributeValue("jid");
            // Check if the specified JID has a subscription with the node
            if (jidAttribute == null) {
                // No JID was specified so return an error indicating that jid is required
                Element pubsubError = DocumentHelper.createElement(
                        QName.get("jid-required", "http://jabber.org/protocol/pubsub#errors"));
                sendErrorPacket(iq, PacketError.Condition.bad_request, pubsubError);
                return;
            }
            JID subscriberJID = new JID(jidAttribute);
            subscription = node.getSubscription(subscriberJID);
            if (subscription == null) {
                Element pubsubError = DocumentHelper.createElement(
                        QName.get("not-subscribed", "http://jabber.org/protocol/pubsub#errors"));
                sendErrorPacket(iq, PacketError.Condition.unexpected_request, pubsubError);
                return;
            }
        }
        JID from = iq.getFrom();
        // Check that unsubscriptions to the node are enabled
        if (!node.isSubscriptionEnabled() && !service.isServiceAdmin(from)) {
            // Sender is not a sysadmin and unsubscription is disabled so return an error
            sendErrorPacket(iq, PacketError.Condition.not_allowed, null);
            return;
        }

        // TODO Assumed that the owner of the subscription is the bare JID of the subscription JID. Waiting StPeter answer for explicit field.
        JID owner = new JID(from.toBareJID());
        // A subscription was found so check if the user is allowed to cancel the subscription
        if (!subscription.canModify(from) && !subscription.canModify(owner)) {
            // Requestor is prohibited from unsubscribing entity
            sendErrorPacket(iq, PacketError.Condition.forbidden, null);
            return;
        }

        // Cancel subscription
        node.cancelSubscription(subscription);
        // Send reply with success
        router.route(IQ.createResultIQ(iq));
    }

    private void getSubscriptionConfiguration(PubSubService service, IQ iq,
                                              Element childElement, Element optionsElement) {
        String nodeID = optionsElement.attributeValue("node");
        String subID = optionsElement.attributeValue("subid");
        Node node;
        if (nodeID == null) {
            if (service.isCollectionNodesSupported()) {
                // Entity requests subscription options of root collection node
                node = service.getRootCollectionNode();
            }
            else {
                // Service does not have a root collection node so return a nodeid-required error
                Element pubsubError = DocumentHelper.createElement(QName.get(
                        "nodeid-required", "http://jabber.org/protocol/pubsub#errors"));
                sendErrorPacket(iq, PacketError.Condition.bad_request, pubsubError);
                return;
            }
        }
        else {
            // Look for the specified node
            node = service.getNode(nodeID);
            if (node == null) {
                // Node does not exist. Return item-not-found error
                sendErrorPacket(iq, PacketError.Condition.item_not_found, null);
                return;
            }
        }
        NodeSubscription subscription;
        if (node.isMultipleSubscriptionsEnabled()) {
            if (subID == null) {
                // No subid was specified and the node supports multiple subscriptions
                Element pubsubError = DocumentHelper.createElement(
                        QName.get("subid-required", "http://jabber.org/protocol/pubsub#errors"));
                sendErrorPacket(iq, PacketError.Condition.bad_request, pubsubError);
                return;
            }
            else {
                // Check if the specified subID belongs to an existing node subscription
                subscription = node.getSubscription(subID);
                if (subscription == null) {
                    Element pubsubError = DocumentHelper.createElement(
                            QName.get("invalid-subid", "http://jabber.org/protocol/pubsub#errors"));
                    sendErrorPacket(iq, PacketError.Condition.not_acceptable, pubsubError);
                    return;
                }
            }
        }
        else {
            // Check if the specified JID has a subscription with the node
            String jidAttribute = optionsElement.attributeValue("jid");
            if (jidAttribute == null) {
                // No JID was specified so return an error indicating that jid is required
                Element pubsubError = DocumentHelper.createElement(
                        QName.get("jid-required", "http://jabber.org/protocol/pubsub#errors"));
                sendErrorPacket(iq, PacketError.Condition.bad_request, pubsubError);
                return;
            }
            JID subscriberJID = new JID(jidAttribute);
            subscription = node.getSubscription(subscriberJID);
            if (subscription == null) {
                Element pubsubError = DocumentHelper.createElement(
                        QName.get("not-subscribed", "http://jabber.org/protocol/pubsub#errors"));
                sendErrorPacket(iq, PacketError.Condition.unexpected_request, pubsubError);
                return;
            }
        }

        // A subscription was found so check if the user is allowed to get the subscription options
        if (!subscription.canModify(iq.getFrom())) {
            // Requestor is prohibited from getting the subscription options
            sendErrorPacket(iq, PacketError.Condition.forbidden, null);
            return;
        }

        // Return data form containing subscription configuration to the subscriber
        IQ reply = IQ.createResultIQ(iq);
        Element replyChildElement = childElement.createCopy();
        reply.setChildElement(replyChildElement);
        replyChildElement.element("options").add(subscription.getConfigurationForm().getElement());
        router.route(reply);
    }

    private void configureSubscription(PubSubService service, IQ iq, Element optionsElement) {
        String nodeID = optionsElement.attributeValue("node");
        String subID = optionsElement.attributeValue("subid");
        Node node;
        if (nodeID == null) {
            if (service.isCollectionNodesSupported()) {
                // Entity submits new subscription options of root collection node
                node = service.getRootCollectionNode();
            }
            else {
                // Service does not have a root collection node so return a nodeid-required error
                Element pubsubError = DocumentHelper.createElement(QName.get(
                        "nodeid-required", "http://jabber.org/protocol/pubsub#errors"));
                sendErrorPacket(iq, PacketError.Condition.bad_request, pubsubError);
                return;
            }
        }
        else {
            // Look for the specified node
            node = service.getNode(nodeID);
            if (node == null) {
                // Node does not exist. Return item-not-found error
                sendErrorPacket(iq, PacketError.Condition.item_not_found, null);
                return;
            }
        }
        NodeSubscription subscription;
        if (node.isMultipleSubscriptionsEnabled()) {
            if (subID == null) {
                // No subid was specified and the node supports multiple subscriptions
                Element pubsubError = DocumentHelper.createElement(
                        QName.get("subid-required", "http://jabber.org/protocol/pubsub#errors"));
                sendErrorPacket(iq, PacketError.Condition.bad_request, pubsubError);
                return;
            }
            else {
                // Check if the specified subID belongs to an existing node subscription
                subscription = node.getSubscription(subID);
                if (subscription == null) {
                    Element pubsubError = DocumentHelper.createElement(
                            QName.get("invalid-subid", "http://jabber.org/protocol/pubsub#errors"));
                    sendErrorPacket(iq, PacketError.Condition.not_acceptable, pubsubError);
                    return;
                }
            }
        }
        else {
            // Check if the specified JID has a subscription with the node
            String jidAttribute = optionsElement.attributeValue("jid");
            if (jidAttribute == null) {
                // No JID was specified so return an error indicating that jid is required
                Element pubsubError = DocumentHelper.createElement(
                        QName.get("jid-required", "http://jabber.org/protocol/pubsub#errors"));
                sendErrorPacket(iq, PacketError.Condition.bad_request, pubsubError);
                return;
            }
            JID subscriberJID = new JID(jidAttribute);
            subscription = node.getSubscription(subscriberJID);
            if (subscription == null) {
                Element pubsubError = DocumentHelper.createElement(
                        QName.get("not-subscribed", "http://jabber.org/protocol/pubsub#errors"));
                sendErrorPacket(iq, PacketError.Condition.unexpected_request, pubsubError);
                return;
            }
        }

        // A subscription was found so check if the user is allowed to submits
        // new subscription options
        if (!subscription.canModify(iq.getFrom())) {
            // Requestor is prohibited from setting new subscription options
            sendErrorPacket(iq, PacketError.Condition.forbidden, null);
            return;
        }

        Element formElement = optionsElement.element(QName.get("x", "jabber:x:data"));
        if (formElement != null) {
            // Change the subscription configuration based on the completed form
            subscription.configure(iq, new DataForm(formElement));
        }
        else {
            // No data form was included so return bad request error
            sendErrorPacket(iq, PacketError.Condition.bad_request, null);
        }
    }

    private void getSubscriptions(PubSubService service, IQ iq, Element childElement) {
        // TODO Assuming that owner is the bare JID (as defined in the JEP). This can be replaced with an explicit owner specified in the packet
        JID owner = new JID(iq.getFrom().toBareJID());
        // Collect subscriptions of owner for all nodes at the service
        Collection<NodeSubscription> subscriptions = new ArrayList<NodeSubscription>();
        for (Node node : service.getNodes()) {
            subscriptions.addAll(node.getSubscriptions(owner));
        }
        // Create reply to send
        IQ reply = IQ.createResultIQ(iq);
        Element replyChildElement = childElement.createCopy();
        reply.setChildElement(replyChildElement);
        Element affiliationsElement = replyChildElement.element("subscriptions");
        // Add information about subscriptions including existing affiliations
        for (NodeSubscription subscription : subscriptions) {
            Element subElement = affiliationsElement.addElement("subscription");
            Node node = subscription.getNode();
            NodeAffiliate nodeAffiliate = subscription.getAffiliate();
            // Do not include the node id when node is the root collection node
            if (!node.isRootCollectionNode()) {
                subElement.addAttribute("node", node.getNodeID());
            }
            subElement.addAttribute("jid", subscription.getJID().toString());
            subElement.addAttribute("affiliation", nodeAffiliate.getAffiliation().name());
            subElement.addAttribute("subscription", subscription.getState().name());
            if (node.isMultipleSubscriptionsEnabled()) {
                subElement.addAttribute("subid", subscription.getID());
            }
        }
        // Send reply
        router.route(reply);
    }

    private  void getAffiliations(PubSubService service, IQ iq, Element childElement) {
        // TODO Assuming that owner is the bare JID (as defined in the JEP). This can be replaced with an explicit owner specified in the packet
        JID owner = new JID(iq.getFrom().toBareJID());
        // Collect affiliations of owner for all nodes at the service
        Collection<NodeAffiliate> affiliations = new ArrayList<NodeAffiliate>();
        for (Node node : service.getNodes()) {
            NodeAffiliate nodeAffiliate = node.getAffiliate(owner);
            if (nodeAffiliate != null) {
                affiliations.add(nodeAffiliate);
            }
        }
        // Create reply to send
        IQ reply = IQ.createResultIQ(iq);
        Element replyChildElement = childElement.createCopy();
        reply.setChildElement(replyChildElement);
        if (affiliations.isEmpty()) {
            // User does not have any affiliation or subscription with the pubsub service
            reply.setError(PacketError.Condition.item_not_found);
        }
        else {
            Element affiliationsElement = replyChildElement.element("affiliations");
            // Add information about affiliations without subscriptions
            for (NodeAffiliate affiliate : affiliations) {
                Element affiliateElement = affiliationsElement.addElement("affiliation");
                // Do not include the node id when node is the root collection node
                if (!affiliate.getNode().isRootCollectionNode()) {
                    affiliateElement.addAttribute("node", affiliate.getNode().getNodeID());
                }
                affiliateElement.addAttribute("jid", affiliate.getJID().toString());
                affiliateElement.addAttribute("affiliation", affiliate.getAffiliation().name());
            }
        }
        // Send reply
        router.route(reply);
    }

    private void getPublishedItems(PubSubService service, IQ iq, Element itemsElement) {
        String nodeID = itemsElement.attributeValue("node");
        String subID = itemsElement.attributeValue("subid");
        Node node;
        if (nodeID == null) {
            // User must specify a leaf node ID so return a nodeid-required error
            Element pubsubError = DocumentHelper.createElement(QName.get(
                    "nodeid-required", "http://jabber.org/protocol/pubsub#errors"));
            sendErrorPacket(iq, PacketError.Condition.bad_request, pubsubError);
            return;
        }
        else {
            // Look for the specified node
            node = service.getNode(nodeID);
            if (node == null) {
                // Node does not exist. Return item-not-found error
                sendErrorPacket(iq, PacketError.Condition.item_not_found, null);
                return;
            }
        }
        if (node.isCollectionNode()) {
            // Node is a collection node. Return feature-not-implemented error
            Element pubsubError = DocumentHelper.createElement(
                    QName.get("unsupported", "http://jabber.org/protocol/pubsub#errors"));
            pubsubError.addAttribute("feature", "retrieve-items");
            sendErrorPacket(iq, PacketError.Condition.feature_not_implemented, pubsubError);
            return;
        }
        // Check if sender and subscriber JIDs match or if a valid "trusted proxy" is being used
        JID subscriberJID = iq.getFrom();
        // TODO Assumed that the owner of the subscription is the bare JID of the subscription JID. Waiting StPeter answer for explicit field.
        JID owner = new JID(subscriberJID.toBareJID());
        // Check if the node's access model allows the subscription to proceed
        AccessModel accessModel = node.getAccessModel();
        if (!accessModel.canAccessItems(node, owner, subscriberJID)) {
            sendErrorPacket(iq, accessModel.getSubsriptionError(),
                    accessModel.getSubsriptionErrorDetail());
            return;
        }
        // Check that the requester is not an outcast
        NodeAffiliate affiliate = node.getAffiliate(owner);
        if (affiliate != null && affiliate.getAffiliation() == NodeAffiliate.Affiliation.outcast) {
            sendErrorPacket(iq, PacketError.Condition.forbidden, null);
            return;
        }

        // Get the user's subscription
        NodeSubscription subscription = null;
        if (node.isMultipleSubscriptionsEnabled() && !node.getSubscriptions(owner).isEmpty()) {
            if (subID == null) {
                // No subid was specified and the node supports multiple subscriptions
                Element pubsubError = DocumentHelper.createElement(
                        QName.get("subid-required", "http://jabber.org/protocol/pubsub#errors"));
                sendErrorPacket(iq, PacketError.Condition.bad_request, pubsubError);
                return;
            }
            else {
                // Check if the specified subID belongs to an existing node subscription
                subscription = node.getSubscription(subID);
                if (subscription == null) {
                    Element pubsubError = DocumentHelper.createElement(
                            QName.get("invalid-subid", "http://jabber.org/protocol/pubsub#errors"));
                    sendErrorPacket(iq, PacketError.Condition.not_acceptable, pubsubError);
                    return;
                }
            }
        }


        if (subscription != null && !subscription.isActive()) {
            Element pubsubError = DocumentHelper.createElement(
                    QName.get("not-subscribed", "http://jabber.org/protocol/pubsub#errors"));
            sendErrorPacket(iq, PacketError.Condition.not_authorized, pubsubError);
            return;
        }

        LeafNode leafNode = (LeafNode) node;
        // Get list of items to send to the user
        boolean forceToIncludePayload = false;
        List<PublishedItem> items;
        String max_items = itemsElement.attributeValue("max_items");
        int recentItems = 0;
        if (max_items != null) {
            try {
                // Parse the recent number of items requested
                recentItems = Integer.parseInt(max_items);
            }
            catch (NumberFormatException e) {
                // There was an error parsing the number so assume that all items were requested
                Log.warn("Assuming that all items were requested", e);
                max_items = null;
            }
        }
        if (max_items != null) {
            // Get the N most recent published items
            items = new ArrayList<PublishedItem>(leafNode.getPublishedItems(recentItems));
        }
        else {
            List requestedItems = itemsElement.elements("item");
            if (requestedItems.isEmpty()) {
                // Get all the active items that were published to the node
                items = new ArrayList<PublishedItem>(leafNode.getPublishedItems());
            }
            else {
                items = new ArrayList<PublishedItem>();
                // Indicate that payload should be included (if exists) no matter
                // the node configuration
                forceToIncludePayload = true;
                // Get the items as requested by the user
                for (Iterator it = requestedItems.iterator(); it.hasNext();) {
                    Element element = (Element) it.next();
                    String itemID = element.attributeValue("id");
                    PublishedItem item = leafNode.getPublishedItem(itemID);
                    if (item != null) {
                        items.add(item);
                    }
                }
            }
        }

        if (subscription != null && subscription.getKeyword() != null) {
            // Filter items that do not match the subscription keyword
            for (Iterator<PublishedItem> it = items.iterator(); it.hasNext();) {
                PublishedItem item = it.next();
                if (!subscription.isKeywordMatched(item)) {
                    // Remove item that does not match keyword
                    it.remove();
                }
            }
        }

        // Send items to the user
        leafNode.sendPublishedItems(iq, items, forceToIncludePayload);
    }

    private void createNode(PubSubService service, IQ iq, Element childElement, Element createElement) {
        // Get sender of the IQ packet
        JID from = iq.getFrom();
        // Verify that sender has permissions to create nodes
        if (!service.canCreateNode(from) || !UserManager.getInstance().isRegisteredUser(from)) {
            // The user is not allowed to create nodes so return an error
            sendErrorPacket(iq, PacketError.Condition.forbidden, null);
            return;
        }
        DataForm completedForm = null;
        CollectionNode parentNode = null;
        String nodeID = createElement.attributeValue("node");
        String newNodeID = nodeID;
        if (nodeID == null) {
            // User requested an instant node
            if (!service.isInstantNodeSupported()) {
                // Instant nodes creation is not allowed so return an error
                Element pubsubError = DocumentHelper.createElement(
                        QName.get("nodeid-required", "http://jabber.org/protocol/pubsub#errors"));
                sendErrorPacket(iq, PacketError.Condition.not_acceptable, pubsubError);
                return;
            }
            do {
                // Create a new nodeID and make sure that the random generated string does not
                // match an existing node. Probability to match an existing node are very very low
                // but they exist :)
                newNodeID = StringUtils.randomString(15);
            }
            while (service.getNode(newNodeID) != null);
        }
        boolean collectionType = false;
        // Check if user requested to configure the node (using a data form)
        Element configureElement = childElement.element("configure");
        if (configureElement != null) {
            // Get the data form that contains the parent nodeID
            completedForm = getSentConfigurationForm(configureElement);
            if (completedForm != null) {
                // Calculate newNodeID when new node is affiliated with a Collection
                FormField field = completedForm.getField("pubsub#collection");
                if (field != null) {
                    List<String> values = field.getValues();
                    if (!values.isEmpty()) {
                        String parentNodeID = values.get(0);
                        Node tempNode = service.getNode(parentNodeID);
                        if (tempNode == null) {
                            // Requested parent node was not found so return an error
                            sendErrorPacket(iq, PacketError.Condition.item_not_found, null);
                            return;
                        }
                        else if (!tempNode.isCollectionNode()) {
                            // Requested parent node is not a collection node so return an error
                            sendErrorPacket(iq, PacketError.Condition.not_acceptable, null);
                            return;
                        }
                        parentNode = (CollectionNode) tempNode;
                    }
                }
                field = completedForm.getField("pubsub#node_type");
                if (field != null) {
                    // Check if user requested to create a new collection node
                    List<String> values = field.getValues();
                    if (!values.isEmpty()) {
                        collectionType = "collection".equals(values.get(0));
                    }
                }
            }
        }
        // If no parent was defined then use the root collection node
        if (parentNode == null && service.isCollectionNodesSupported()) {
            parentNode = service.getRootCollectionNode();
        }
        // Check that the requested nodeID does not exist
        Node existingNode = service.getNode(newNodeID);
        if (existingNode != null) {
            // There is a conflict since a node with the same ID already exists
            sendErrorPacket(iq, PacketError.Condition.conflict, null);
            return;
        }

        if (collectionType && !service.isCollectionNodesSupported()) {
            // Cannot create a collection node since the service doesn't support it
            Element pubsubError = DocumentHelper.createElement(
                    QName.get("unsupported", "http://jabber.org/protocol/pubsub#errors"));
            pubsubError.addAttribute("feature", "collections");
            sendErrorPacket(iq, PacketError.Condition.feature_not_implemented, pubsubError);
            return;
        }

        if (parentNode != null && !collectionType) {
            // Check if requester is allowed to add a new leaf child node to the parent node
            if (!parentNode.isAssociationAllowed(from)) {
                // User is not allowed to add child leaf node to parent node. Return an error.
                sendErrorPacket(iq, PacketError.Condition.forbidden, null);
                return;
            }
            // Check if number of child leaf nodes has not been exceeded
            if (parentNode.isMaxLeafNodeReached()) {
                // Max number of child leaf nodes has been reached. Return an error.
                Element pubsubError = DocumentHelper.createElement(QName.get("max-nodes-exceeded",
                        "http://jabber.org/protocol/pubsub#errors"));
                sendErrorPacket(iq, PacketError.Condition.conflict, pubsubError);
                return;
            }
        }

        // Create and configure the node
        boolean conflict = false;
        Node newNode = null;
        try {
            // TODO Assumed that the owner of the subscription is the bare JID of the subscription JID. Waiting StPeter answer for explicit field.
            JID owner = new JID(from.toBareJID());
            synchronized (newNodeID.intern()) {
                if (service.getNode(newNodeID) == null) {
                    // Create the node
                    if (collectionType) {
                        newNode = new CollectionNode(service, parentNode, newNodeID, from);
                    }
                    else {
                        newNode = new LeafNode(service, parentNode, newNodeID, from);
                    }
                    // Add the creator as the node owner
                    newNode.addOwner(owner);
                    // Configure and save the node to the backend store
                    if (completedForm != null) {
                        newNode.configure(completedForm);
                    }
                    else {
                        newNode.saveToDB();
                    }
                }
                else {
                    conflict = true;
                }
            }
            if (conflict) {
                // There is a conflict since a node with the same ID already exists
                sendErrorPacket(iq, PacketError.Condition.conflict, null);
            }
            else {
                // Return success to the node owner
                IQ reply = IQ.createResultIQ(iq);
                // Include new nodeID if it has changed from the original nodeID
                if (!newNode.getNodeID().equals(nodeID)) {
                    Element elem =
                            reply.setChildElement("pubsub", "http://jabber.org/protocol/pubsub");
                    elem.addElement("create").addAttribute("node", newNode.getNodeID());
                }
                router.route(reply);
            }
        }
        catch (NotAcceptableException e) {
            // Node should have at least one owner. Return not-acceptable error.
            sendErrorPacket(iq, PacketError.Condition.not_acceptable, null);
        }
    }

    private void getNodeConfiguration(PubSubService service, IQ iq, Element childElement, String nodeID) {
        Node node = service.getNode(nodeID);
        if (node == null) {
            // Node does not exist. Return item-not-found error
            sendErrorPacket(iq, PacketError.Condition.item_not_found, null);
            return;
        }
        if (!node.isAdmin(iq.getFrom())) {
            // Requesting entity is prohibited from configuring this node. Return forbidden error
            sendErrorPacket(iq, PacketError.Condition.forbidden, null);
            return;
        }

        // Return data form containing node configuration to the owner
        IQ reply = IQ.createResultIQ(iq);
        Element replyChildElement = childElement.createCopy();
        reply.setChildElement(replyChildElement);
        replyChildElement.element("configure").add(node.getConfigurationForm().getElement());
        router.route(reply);
    }

    private void getDefaultNodeConfiguration(PubSubService service, IQ iq,
                                             Element childElement, Element defaultElement) {
        String type = defaultElement.attributeValue("type");
        type = type == null ? "leaf" : type;

        boolean isLeafType = "leaf".equals(type);
        DefaultNodeConfiguration config = service.getDefaultNodeConfiguration(isLeafType);
        if (config == null) {
            // Service does not support the requested node type so return an error
            Element pubsubError = DocumentHelper.createElement(
                    QName.get("unsupported", "http://jabber.org/protocol/pubsub#errors"));
            pubsubError.addAttribute("feature", isLeafType ? "leaf" : "collections");
            sendErrorPacket(iq, PacketError.Condition.feature_not_implemented, pubsubError);
            return;
        }

        // Return data form containing default node configuration
        IQ reply = IQ.createResultIQ(iq);
        Element replyChildElement = childElement.createCopy();
        reply.setChildElement(replyChildElement);
        replyChildElement.element("default").add(config.getConfigurationForm().getElement());
        router.route(reply);
    }

    private void configureNode(PubSubService service, IQ iq, Element configureElement, String nodeID) {
        Node node = service.getNode(nodeID);
        if (node == null) {
            // Node does not exist. Return item-not-found error
            sendErrorPacket(iq, PacketError.Condition.item_not_found, null);
            return;
        }
        if (!node.isAdmin(iq.getFrom())) {
            // Requesting entity is not allowed to get node configuration. Return forbidden error
            sendErrorPacket(iq, PacketError.Condition.forbidden, null);
            return;
        }

        // Get the data form that contains the parent nodeID
        DataForm completedForm = getSentConfigurationForm(configureElement);
        if (completedForm != null) {
            try {
                // Update node configuration with the provided data form
                // (and update the backend store)
                node.configure(completedForm);
                // Return that node configuration was successful
                router.route(IQ.createResultIQ(iq));
            }
            catch (NotAcceptableException e) {
                // Node should have at least one owner. Return not-acceptable error.
                sendErrorPacket(iq, PacketError.Condition.not_acceptable, null);
            }
        }
        else {
            // No data form was included so return bad-request error
            sendErrorPacket(iq, PacketError.Condition.bad_request, null);
        }
    }

    private void deleteNode(PubSubService service, IQ iq, Element deleteElement) {
        String nodeID = deleteElement.attributeValue("node");
        if (nodeID == null) {
            // NodeID was not provided. Return bad-request error
            sendErrorPacket(iq, PacketError.Condition.bad_request, null);
            return;
        }
        Node node = service.getNode(nodeID);
        if (node == null) {
            // Node does not exist. Return item-not-found error
            sendErrorPacket(iq, PacketError.Condition.item_not_found, null);
            return;
        }
        if (!node.isAdmin(iq.getFrom())) {
            // Requesting entity is prohibited from deleting this node. Return forbidden error
            sendErrorPacket(iq, PacketError.Condition.forbidden, null);
            return;
        }
        if (node.isRootCollectionNode()) {
            // Root collection node cannot be deleted. Return not-allowed error
            sendErrorPacket(iq, PacketError.Condition.not_allowed, null);
            return;
        }

        // Delete the node
        if (node.delete()) {
            // Return that node was deleted successfully
            router.route(IQ.createResultIQ(iq));
        }
        else {
            // Some error occured while trying to delete the node
            sendErrorPacket(iq, PacketError.Condition.internal_server_error, null);
        }
    }

    private void purgeNode(PubSubService service, IQ iq, Element purgeElement) {
        String nodeID = purgeElement.attributeValue("node");
        if (nodeID == null) {
            // NodeID was not provided. Return bad-request error
            sendErrorPacket(iq, PacketError.Condition.bad_request, null);
            return;
        }
        Node node = service.getNode(nodeID);
        if (node == null) {
            // Node does not exist. Return item-not-found error
            sendErrorPacket(iq, PacketError.Condition.item_not_found, null);
            return;
        }
        if (!node.isAdmin(iq.getFrom())) {
            // Requesting entity is prohibited from configuring this node. Return forbidden error
            sendErrorPacket(iq, PacketError.Condition.forbidden, null);
            return;
        }
        if (!((LeafNode) node).isPersistPublishedItems()) {
            // Node does not persist items. Return feature-not-implemented error
            Element pubsubError = DocumentHelper.createElement(
                    QName.get("unsupported", "http://jabber.org/protocol/pubsub#errors"));
            pubsubError.addAttribute("feature", "persistent-items");
            sendErrorPacket(iq, PacketError.Condition.feature_not_implemented, pubsubError);
            return;
        }
        if (node.isCollectionNode()) {
            // Node is a collection node. Return feature-not-implemented error
            Element pubsubError = DocumentHelper.createElement(
                    QName.get("unsupported", "http://jabber.org/protocol/pubsub#errors"));
            pubsubError.addAttribute("feature", "purge-nodes");
            sendErrorPacket(iq, PacketError.Condition.feature_not_implemented, pubsubError);
            return;
        }

        // Purge the node
        ((LeafNode) node).purge();
        // Return that node purged successfully
        router.route(IQ.createResultIQ(iq));
    }

    private void getNodeSubscriptions(PubSubService service, IQ iq, Element affiliationsElement) {
        String nodeID = affiliationsElement.attributeValue("node");
        if (nodeID == null) {
            // NodeID was not provided. Return bad-request error.
            sendErrorPacket(iq, PacketError.Condition.bad_request, null);
            return;
        }
        Node node = service.getNode(nodeID);
        if (node == null) {
            // Node does not exist. Return item-not-found error.
            sendErrorPacket(iq, PacketError.Condition.item_not_found, null);
            return;
        }
        if (!node.isAdmin(iq.getFrom())) {
            // Requesting entity is prohibited from getting affiliates list. Return forbidden error.
            sendErrorPacket(iq, PacketError.Condition.forbidden, null);
            return;
        }

        // Ask the node to send the list of subscriptions to the owner
        node.sendSubscriptions(iq);
    }

    private void modifyNodeSubscriptions(PubSubService service, IQ iq, Element entitiesElement) {
        String nodeID = entitiesElement.attributeValue("node");
        if (nodeID == null) {
            // NodeID was not provided. Return bad-request error.
            sendErrorPacket(iq, PacketError.Condition.bad_request, null);
            return;
        }
        Node node = service.getNode(nodeID);
        if (node == null) {
            // Node does not exist. Return item-not-found error.
            sendErrorPacket(iq, PacketError.Condition.item_not_found, null);
            return;
        }
        if (!node.isAdmin(iq.getFrom())) {
            // Requesting entity is prohibited from getting affiliates list. Return forbidden error.
            sendErrorPacket(iq, PacketError.Condition.forbidden, null);
            return;
        }

        IQ reply = IQ.createResultIQ(iq);

        // Process modifications or creations of affiliations and subscriptions.
        for (Iterator it = entitiesElement.elementIterator("subscription"); it.hasNext();) {
            Element entity = (Element) it.next();
            JID subscriber = new JID(entity.attributeValue("jid"));
            // TODO Assumed that the owner of the subscription is the bare JID of the subscription JID. Waiting StPeter answer for explicit field.
            JID owner = new JID(subscriber.toBareJID());
            String subStatus = entity.attributeValue("subscription");
            String subID = entity.attributeValue("subid");
            // Process subscriptions changes
            // Get current subscription (if any)
            NodeSubscription subscription = null;
            if (node.isMultipleSubscriptionsEnabled()) {
                if (subID != null) {
                    subscription = node.getSubscription(subID);
                }
            }
            else {
                subscription = node.getSubscription(subscriber);
            }
            if ("none".equals(subStatus) && subscription != null) {
                // Owner is cancelling an existing subscription
                node.cancelSubscription(subscription);
            }
            else if ("subscribed".equals(subStatus)) {
                if (subscription != null) {
                    // Owner is approving a subscription (i.e. making active)
                    node.approveSubscription(subscription, true);
                }
                else {
                    // Owner is creating a subscription for an entity to the node
                    node.createSubscription(null, owner, subscriber, false, null);
                }
            }
        }

        // Send reply
        router.route(reply);
    }

    private void getNodeAffiliations(PubSubService service, IQ iq, Element affiliationsElement) {
        String nodeID = affiliationsElement.attributeValue("node");
        if (nodeID == null) {
            // NodeID was not provided. Return bad-request error.
            sendErrorPacket(iq, PacketError.Condition.bad_request, null);
            return;
        }
        Node node = service.getNode(nodeID);
        if (node == null) {
            // Node does not exist. Return item-not-found error.
            sendErrorPacket(iq, PacketError.Condition.item_not_found, null);
            return;
        }
        if (!node.isAdmin(iq.getFrom())) {
            // Requesting entity is prohibited from getting affiliates list. Return forbidden error.
            sendErrorPacket(iq, PacketError.Condition.forbidden, null);
            return;
        }

        // Ask the node to send the list of affiliations to the owner
        node.sendAffiliations(iq);
    }

    private void modifyNodeAffiliations(PubSubService service, IQ iq, Element entitiesElement) {
        String nodeID = entitiesElement.attributeValue("node");
        if (nodeID == null) {
            // NodeID was not provided. Return bad-request error.
            sendErrorPacket(iq, PacketError.Condition.bad_request, null);
            return;
        }
        Node node = service.getNode(nodeID);
        if (node == null) {
            // Node does not exist. Return item-not-found error.
            sendErrorPacket(iq, PacketError.Condition.item_not_found, null);
            return;
        }
        if (!node.isAdmin(iq.getFrom())) {
            // Requesting entity is prohibited from getting affiliates list. Return forbidden error.
            sendErrorPacket(iq, PacketError.Condition.forbidden, null);
            return;
        }

        IQ reply = IQ.createResultIQ(iq);
        Collection<JID> invalidAffiliates = new ArrayList<JID>();

        // Process modifications or creations of affiliations
        for (Iterator it = entitiesElement.elementIterator("affiliation"); it.hasNext();) {
            Element affiliation = (Element) it.next();
            JID owner = new JID(affiliation.attributeValue("jid"));
            String newAffiliation = affiliation.attributeValue("affiliation");
            // Get current affiliation of this user (if any)
            NodeAffiliate affiliate = node.getAffiliate(owner);

            // Check that we are not removing the only owner of the node
            if (affiliate != null && !affiliate.getAffiliation().name().equals(newAffiliation)) {
                // Trying to modify an existing affiliation
                if (affiliate.getAffiliation() == NodeAffiliate.Affiliation.owner &&
                        node.getOwners().size() == 1) {
                    // Trying to remove the unique owner of the node. Include in error answer.
                    invalidAffiliates.add(owner);
                    continue;
                }
            }

            // Owner is setting affiliations for new entities or modifying
            // existing affiliations
            if ("owner".equals(newAffiliation)) {
                node.addOwner(owner);
            }
            else if ("publisher".equals(newAffiliation)) {
                node.addPublisher(owner);
            }
            else if ("none".equals(newAffiliation)) {
                node.addNoneAffiliation(owner);
            }
            else  {
                node.addOutcast(owner);
            }
        }

        // Process invalid entities that tried to remove node owners. Send original affiliation
        // of the invalid entities.
        if (!invalidAffiliates.isEmpty()) {
            reply.setError(PacketError.Condition.not_acceptable);
            Element child =
                    reply.setChildElement("pubsub", "http://jabber.org/protocol/pubsub#owner");
            Element entities = child.addElement("affiliations");
            if (!node.isRootCollectionNode()) {
                entities.addAttribute("node", node.getNodeID());
            }
            for (JID affiliateJID : invalidAffiliates) {
                NodeAffiliate affiliate = node.getAffiliate(affiliateJID);
                Element entity = entities.addElement("affiliation");
                entity.addAttribute("jid", affiliate.getJID().toString());
                entity.addAttribute("affiliation", affiliate.getAffiliation().name());
            }
        }
        // Send reply
        router.route(reply);
    }

    /**
     * Terminates the subscription of the specified entity to all nodes hosted at the service.
     * The affiliation with the node will be removed if the entity was not a node owner or
     * publisher.
     *
     * @param service the PubSub service this action is to be performed for.
     * @param user the entity that no longer exists.
     */
    private void cancelAllSubscriptions(PubSubService service, JID user) {
        for (Node node : service.getNodes()) {
            NodeAffiliate affiliate = node.getAffiliate(user);
            if (affiliate == null) {
                continue;
            }
            for (NodeSubscription subscription : affiliate.getSubscriptions()) {
                // Cancel subscription
                node.cancelSubscription(subscription);
            }
        }
    }

    private void processAuthorizationAnswer(PubSubService service, DataForm authForm, Message message) {
        String nodeID = authForm.getField("pubsub#node").getValues().get(0);
        String subID = authForm.getField("pubsub#subid").getValues().get(0);
        String allow = authForm.getField("pubsub#allow").getValues().get(0);
        boolean approved;
        if ("1".equals(allow) || "true".equals(allow)) {
            approved = true;
        }
        else if ("0".equals(allow) || "false".equals(allow)) {
            approved = false;
        }
        else {
            // Unknown allow value. Ignore completed form
            Log.warn("Invalid allow value in completed authorization form: " + message.toXML());
            return;
        }
        // Approve or cancel the pending subscription to the node
        Node node = service.getNode(nodeID);
        if (node != null) {
            NodeSubscription subscription = node.getSubscription(subID);
            if (subscription != null) {
                node.approveSubscription(subscription, approved);
            }
        }
    }

    /**
     * Generate a conflict packet to indicate that the nickname being requested/used is already in
     * use by another user.
     *
     * @param packet the packet to be bounced.
     */
    void sendErrorPacket(IQ packet, PacketError.Condition error, Element pubsubError) {
        IQ reply = IQ.createResultIQ(packet);
        reply.setChildElement(packet.getChildElement().createCopy());
        reply.setError(error);
        if (pubsubError != null) {
            // Add specific pubsub error if available
            reply.getError().getElement().add(pubsubError);
        }
        router.route(reply);
    }

    /**
     * Returns the data form included in the configure element sent by the node owner or
     * <tt>null</tt> if none was included or access model was defined. If the
     * owner just wants to set the access model to use for the node and optionally set the
     * list of roster groups (i.e. contacts present in the node owner roster in the
     * specified groups are allowed to access the node) allowed to access the node then
     * instead of including a data form the owner can just specify the "access" attribute
     * of the configure element and optionally include a list of group elements. In this case,
     * the method will create a data form including the specified data. This is a nice way
     * to accept both ways to configure a node but always returning a data form.
     *
     * @param configureElement the configure element sent by the owner.
     * @return the data form included in the configure element sent by the node owner or
     *         <tt>null</tt> if none was included or access model was defined.
     */
    private DataForm getSentConfigurationForm(Element configureElement) {
        DataForm completedForm = null;
        FormField formField;
        Element formElement = configureElement.element(QName.get("x", "jabber:x:data"));
        if (formElement != null) {
            completedForm = new DataForm(formElement);
        }
        String accessModel = configureElement.attributeValue("access");
        if (accessModel != null) {
            if (completedForm == null) {
                // Create a form (i.e. simulate that the user sent a form with roster groups)
                completedForm = new DataForm(DataForm.Type.submit);
                // Add the hidden field indicating that this is a node config form
                formField = completedForm.addField();
                formField.setVariable("FORM_TYPE");
                formField.setType(FormField.Type.hidden);
                formField.addValue("http://jabber.org/protocol/pubsub#node_config");
            }
            if (completedForm.getField("pubsub#access_model") == null) {
                // Add the field that will specify the access model of the node
                formField = completedForm.addField();
                formField.setVariable("pubsub#access_model");
                formField.addValue(accessModel);
            }
            else {
                Log.debug("PubSubEngine: Owner sent access model in data form and as attribute: " +
                        configureElement.asXML());
            }
            // Check if a list of groups was specified
            List groups = configureElement.elements("group");
            if (!groups.isEmpty()) {
                // Add the field that will contain the specified groups
                formField = completedForm.addField();
                formField.setVariable("pubsub#roster_groups_allowed");
                // Add each group as a value of the groups field
                for (Iterator it = groups.iterator(); it.hasNext();) {
                    formField.addValue(((Element) it.next()).getTextTrim());
                }
            }
        }
        return completedForm;
    }

    public void start(final PubSubService service) {
        // Probe presences of users that this service has subscribed to (once the server
        // has started)
        
        if (XMPPServer.getInstance().isStarted()) {
            probePresences(service);
        }
        else {
            XMPPServer.getInstance().addServerListener(new XMPPServerListener() {
                public void serverStarted() {
                    probePresences(service);
                }

                public void serverStopping() {
                }
            });
        }        
    }

    private void probePresences(final PubSubService service) {
        Set<JID> affiliates = new HashSet<JID>();
        for (Node node : service.getNodes()) {
            affiliates.addAll(node.getPresenceBasedSubscribers());
        }
        for (JID jid : affiliates) {
            // Send probe presence
            Presence subscription = new Presence(Presence.Type.probe);
            subscription.setTo(jid);
            subscription.setFrom(service.getAddress());
            service.send(subscription);
        }
    }

    public void shutdown(PubSubService service) {
        // Stop the maintenance processes
        service.getTimer().cancel();
        // Delete from the database items contained in the itemsToDelete queue
        PublishedItem entry;
        while (!service.getItemsToDelete().isEmpty()) {
            entry = service.getItemsToDelete().poll();
            if (entry != null) {
                PubSubPersistenceManager.removePublishedItem(service, entry);
            }
        }
        // Save to the database items contained in the itemsToAdd queue
        while (!service.getItemsToAdd().isEmpty()) {
            entry = service.getItemsToAdd().poll();
            if (entry != null) {
                PubSubPersistenceManager.createPublishedItem(service, entry);
            }
        }
        // Stop executing ad-hoc commands
        service.getManager().stop();
    }

    /*******************************************************************************
     * Methods related to presence subscriptions to subscribers' presence.
     ******************************************************************************/

    /**
     * Returns the show values of the last know presence of all connected resources of the
     * specified subscriber. When the subscriber JID is a bare JID then the answered collection
     * will have many entries one for each connected resource. Moreover, if the user
     * is offline then an empty collectin is returned. Available show status is represented
     * by a <tt>online</tt> value. The rest of the possible show values as defined in RFC 3921.
     *
     * @param service the PubSub service this action is to be performed for.
     * @param subscriber the JID of the subscriber. This is not the JID of the affiliate.
     * @return an empty collection when offline. Otherwise, a collection with the show value
     *         of each connected resource.
     */
    public static Collection<String> getShowPresences(PubSubService service, JID subscriber) {
        Map<String, String> fullPresences = service.getBarePresences().get(subscriber.toBareJID());
        if (fullPresences == null) {
            // User is offline so return empty list
            return Collections.emptyList();
        }
        if (subscriber.getResource() == null) {
            // Subscriber used bared JID so return show value of all connected resources
            return fullPresences.values();
        }
        else {
            // Look for the show value using the full JID
            String show = fullPresences.get(subscriber.toString());
            if (show == null) {
                // User at the specified resource is offline so return empty list
                return Collections.emptyList();
            }
            // User is connected at specified resource so answer list with presence show value
            return Arrays.asList(show);
        }
    }

    /**
     * Requests the pubsub service to subscribe to the presence of the user. If the service
     * has already subscribed to the user's presence then do nothing.
     *
     * @param service the PubSub service this action is to be performed for.
     * @param node the node that originated the subscription request.
     * @param user the JID of the affiliate to subscribe to his presence.
     */
    public static void presenceSubscriptionNotRequired(PubSubService service, Node node, JID user) {
        // Check that no node is requiring to be subscribed to this user
        for (Node hostedNode : service.getNodes()) {
            if (hostedNode.isPresenceBasedDelivery(user)) {
                // Do not unsubscribe since presence subscription is still required
                return;
            }
        }
        // Unscribe from the user presence
        Presence subscription = new Presence(Presence.Type.unsubscribe);
        subscription.setTo(user);
        subscription.setFrom(service.getAddress());
        service.send(subscription);
    }

    /**
     * Requests the pubsub service to unsubscribe from the presence of the user. If the service
     * was not subscribed to the user's presence or any node still requires to be subscribed to
     * the user presence then do nothing.
     *
     * @param service the PubSub service this action is to be performed for.
     * @param node the node that originated the unsubscription request.
     * @param user the JID of the affiliate to unsubscribe from his presence.
     */
    public static void presenceSubscriptionRequired(PubSubService service, Node node, JID user) {
        Map<String, String> fullPresences = service.getBarePresences().get(user.toString());
        if (fullPresences == null || fullPresences.isEmpty()) {
            Presence subscription = new Presence(Presence.Type.subscribe);
            subscription.setTo(user);
            subscription.setFrom(service.getAddress());
            service.send(subscription);
            // Sending subscription requests based on received presences may generate
            // that a sunscription request is sent to an offline user (since offline
            // presences are not stored in the service's "barePresences"). However, this
            // not optimal algorithm shouldn't bother the user since the user's server
            // should reply when already subscribed to the user's presence instead of
            // asking the user to accept the subscription request.
        }
    }

    /*******************************************************************************
     * Methods related to PubSub maintenance tasks. Such as
     * saving or deleting published items.
     ******************************************************************************/

    /**
     * Schedules the maintenance task for repeated <i>fixed-delay execution</i>,
     * beginning after the specified delay.  Subsequent executions take place
     * at approximately regular intervals separated by the specified period.
     *
     * @param service the PubSub service this action is to be performed for.
     * @param timeout the new frequency of the maintenance task.
     */
    void setPublishedItemTaskTimeout(PubSubService service, int timeout) {
        int items_task_timeout = service.getItemsTaskTimeout();
        if (items_task_timeout == timeout) {
            return;
        }
        // Cancel the existing task because the timeout has changed
        PublishedItemTask publishedItemTask = service.getPublishedItemTask();
        if (publishedItemTask != null) {
            publishedItemTask.cancel();
        }
        service.setItemsTaskTimeout(timeout);
        // Create a new task and schedule it with the new timeout
        service.setPublishedItemTask(new PublishedItemTask(service));
        service.getTimer().schedule(publishedItemTask, items_task_timeout, items_task_timeout);
    }

    /**
     * Adds the item to the queue of items to remove from the database. The queue is going
     * to be processed by another thread.
     *
     * @param service the PubSub service this action is to be performed for.
     * @param removedItem the item to remove from the database.
     */
    public static void queueItemToRemove(PubSubService service, PublishedItem removedItem) {
        // Remove the removed item from the queue of items to add to the database
        if (!service.getItemsToAdd().remove(removedItem)) {
            // The item is already present in the database so add the removed item
            // to the queue of items to delete from the database
            service.getItemsToDelete().add(removedItem);
        }
    }

    /**
     * Adds the item to the queue of items to add to the database. The queue is going
     * to be processed by another thread.
     *
     * @param service the PubSub service this action is to be performed for.
     * @param newItem the item to add to the database.
     */
    public static void queueItemToAdd(PubSubService service, PublishedItem newItem) {
        service.getItemsToAdd().add(newItem);
    }

    /**
     * Cancels any queued operation for the specified list of items. This operation is
     * usually required when a node was deleted so any pending operation of the node items
     * should be cancelled.
     *
     * @param service the PubSub service this action is to be performed for.
     * @param items the list of items to remove the from queues.
     */
    void cancelQueuedItems(PubSubService service, Collection<PublishedItem> items) {
        for (PublishedItem item : items) {
            service.getItemsToAdd().remove(item);
            service.getItemsToDelete().remove(item);
        }
    }

}
