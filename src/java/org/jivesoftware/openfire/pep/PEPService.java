/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.pep;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.commands.AdHocCommandManager;
import org.jivesoftware.openfire.pubsub.CollectionNode;
import org.jivesoftware.openfire.pubsub.DefaultNodeConfiguration;
import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.openfire.pubsub.PendingSubscriptionsCommand;
import org.jivesoftware.openfire.pubsub.PubSubEngine;
import org.jivesoftware.openfire.pubsub.PubSubPersistenceManager;
import org.jivesoftware.openfire.pubsub.PubSubService;
import org.jivesoftware.openfire.pubsub.PublishedItem;
import org.jivesoftware.openfire.pubsub.PublishedItemTask;
import org.jivesoftware.openfire.pubsub.models.AccessModel;
import org.jivesoftware.openfire.pubsub.models.PublisherModel;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.StringUtils;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketExtension;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A PEPService is a PubSubService for use with XEP-0163: "Personal Eventing via
 * Pubsub."
 * 
 * @author Armando Jagucki
 * 
 */
public class PEPService implements PubSubService {
    /**
     * The bare JID that this service is identified by.
     */
    private String serviceOwnerJID;

    /**
     * Collection node that acts as the root node of the entire node hierarchy.
     */
    private CollectionNode rootCollectionNode = null;

    /**
     * Nodes managed by this service, table: key nodeID (String); value Node
     */
    private Map<String, Node> nodes = new ConcurrentHashMap<String, Node>();

    /**
     * The packet router for the server.
     */
    private PacketRouter router = null;

    /**
     * Default configuration to use for newly created leaf nodes.
     */
    private DefaultNodeConfiguration leafDefaultConfiguration;

    /**
     * Default configuration to use for newly created collection nodes.
     */
    private DefaultNodeConfiguration collectionDefaultConfiguration;

    /**
     * Returns the permission policy for creating nodes. A true value means that
     * not anyone can create a node, only the service admin.
     */
    private boolean nodeCreationRestricted = true;

    /**
     * Flag that indicates if a user may have more than one subscription with
     * the node. When multiple subscriptions is enabled each subscription
     * request, event notification, and unsubscription request should include a
     * subid attribute.
     */
    private boolean multipleSubscriptionsEnabled = false;

    /**
     * Keep a registry of the presence's show value of users that subscribed to
     * a node of the pep service and for which the node only delivers
     * notifications for online users or node subscriptions deliver events based
     * on the user presence show value. Offline users will not have an entry in
     * the map. Note: Key-> bare JID and Value-> Map whose key is full JID of
     * connected resource and value is show value of the last received presence.
     */
    private Map<String, Map<String, String>> barePresences = new ConcurrentHashMap<String, Map<String, String>>();

    /**
     * Queue that holds the items that need to be added to the database.
     */
    private Queue<PublishedItem> itemsToAdd = new LinkedBlockingQueue<PublishedItem>();

    /**
     * Queue that holds the items that need to be deleted from the database.
     */
    private Queue<PublishedItem> itemsToDelete = new LinkedBlockingQueue<PublishedItem>();

    /**
     * Manager that keeps the list of ad-hoc commands and processing command
     * requests.
     */
    private AdHocCommandManager manager;

    /**
     * The time to elapse between each execution of the maintenance process.
     * Default is 2 minutes.
     */
    public int items_task_timeout = 2 * 60 * 1000;

    /**
     * Task that saves or deletes published items from the database.
     */
    private PublishedItemTask publishedItemTask;

    /**
     * Timer to save published items to the database or remove deleted or old
     * items.
     */
    private Timer timer = new Timer("PEP service maintenance");

    /**
     * Constructs a PEPService.
     * 
     * @param server  the XMPP server.
     * @param bareJID the bare JID (service ID) of the user owning the service.
     */
    public PEPService(XMPPServer server, String bareJID) {
        this.serviceOwnerJID = bareJID;
        router = server.getPacketRouter();

        // Initialize the ad-hoc commands manager to use for this pep service
        manager = new AdHocCommandManager();
        manager.addCommand(new PendingSubscriptionsCommand(this));

        // Save or delete published items from the database every 2 minutes
        // starting in 2 minutes (default values)
        publishedItemTask = new PublishedItemTask(this);
        timer.schedule(publishedItemTask, items_task_timeout, items_task_timeout);

        // Load default configuration for leaf nodes
        leafDefaultConfiguration = PubSubPersistenceManager.loadDefaultConfiguration(this, true);
        if (leafDefaultConfiguration == null) {
            // Create and save default configuration for leaf nodes;
            leafDefaultConfiguration = new DefaultNodeConfiguration(true);
            leafDefaultConfiguration.setAccessModel(AccessModel.presence);
            leafDefaultConfiguration.setPublisherModel(PublisherModel.publishers);
            leafDefaultConfiguration.setDeliverPayloads(true);
            leafDefaultConfiguration.setLanguage("English");
            leafDefaultConfiguration.setMaxPayloadSize(5120);
            leafDefaultConfiguration.setNotifyConfigChanges(true);
            leafDefaultConfiguration.setNotifyDelete(true);
            leafDefaultConfiguration.setNotifyRetract(true);
            leafDefaultConfiguration.setPersistPublishedItems(false);
            leafDefaultConfiguration.setMaxPublishedItems(-1);
            leafDefaultConfiguration.setPresenceBasedDelivery(false);
            leafDefaultConfiguration.setSendItemSubscribe(true);
            leafDefaultConfiguration.setSubscriptionEnabled(true);
            leafDefaultConfiguration.setReplyPolicy(null);
            PubSubPersistenceManager.createDefaultConfiguration(this, leafDefaultConfiguration);
        }
        // Load default configuration for collection nodes
        collectionDefaultConfiguration = PubSubPersistenceManager.loadDefaultConfiguration(this, false);
        if (collectionDefaultConfiguration == null) {
            // Create and save default configuration for collection nodes;
            collectionDefaultConfiguration = new DefaultNodeConfiguration(false);
            collectionDefaultConfiguration.setAccessModel(AccessModel.presence);
            collectionDefaultConfiguration.setPublisherModel(PublisherModel.publishers);
            collectionDefaultConfiguration.setDeliverPayloads(false);
            collectionDefaultConfiguration.setLanguage("English");
            collectionDefaultConfiguration.setNotifyConfigChanges(true);
            collectionDefaultConfiguration.setNotifyDelete(true);
            collectionDefaultConfiguration.setNotifyRetract(true);
            collectionDefaultConfiguration.setPresenceBasedDelivery(false);
            collectionDefaultConfiguration.setSubscriptionEnabled(true);
            collectionDefaultConfiguration.setReplyPolicy(null);
            collectionDefaultConfiguration.setAssociationPolicy(CollectionNode.LeafNodeAssociationPolicy.all);
            collectionDefaultConfiguration.setMaxLeafNodes(-1);
            PubSubPersistenceManager.createDefaultConfiguration(this, collectionDefaultConfiguration);
        }

        // Load nodes to memory
        PubSubPersistenceManager.loadNodes(this);
        // Ensure that we have a root collection node
        if (nodes.isEmpty()) {
            // Create root collection node
            JID creatorJID = new JID(bareJID);
            rootCollectionNode = new CollectionNode(this, null, bareJID, creatorJID);
            // Add the creator as the node owner
            rootCollectionNode.addOwner(creatorJID);
            // Save new root node
            rootCollectionNode.saveToDB();
        }
        else {
            rootCollectionNode = (CollectionNode) getNode(bareJID);
        }
    }

    public void addNode(Node node) {
        nodes.put(node.getNodeID(), node);
    }

    public void broadcast(Node node, Message message, Collection<JID> jids) {
        message.setFrom(getAddress());
        for (JID jid : jids) {
            message.setTo(jid);
            message.setID(node.getNodeID() + "__" + jid.toBareJID() + "__" + StringUtils.randomString(5));
            router.route(message);
        }
    }

    public boolean canCreateNode(JID creator) {
        // Node creation is always allowed for sysadmin
        if (isNodeCreationRestricted() && !isServiceAdmin(creator)) {
            // The user is not allowed to create nodes
            return false;
        }
        return true;
    }

    public JID getAddress() {
        return new JID(serviceOwnerJID);
    }

    public DefaultNodeConfiguration getDefaultNodeConfiguration(boolean leafType) {
        if (leafType) {
            return leafDefaultConfiguration;
        }
        return collectionDefaultConfiguration;
    }

    public Node getNode(String nodeID) {
        return nodes.get(nodeID);
    }

    public Collection<Node> getNodes() {
        return nodes.values();
    }

    public CollectionNode getRootCollectionNode() {
        return rootCollectionNode;
    }

    public String getServiceID() {
        // The bare JID of the user is the service ID for PEP
        return serviceOwnerJID;
    }

    public Collection<String> getShowPresences(JID subscriber) {
        return PubSubEngine.getShowPresences(this, subscriber);
    }

    public boolean isCollectionNodesSupported() {
        return true;
    }

    public boolean isInstantNodeSupported() {
        return true;
    }

    public boolean isMultipleSubscriptionsEnabled() {
        return multipleSubscriptionsEnabled;
    }

    public boolean isServiceAdmin(JID user) {
        // Here we consider a 'service admin' to be the user that this PEPService
        // is associated with.
        if (serviceOwnerJID.equals(user.toBareJID())) {
            return true;
        }
        else {
            return false;
        }
    }

    public void presenceSubscriptionNotRequired(Node node, JID user) {
        PubSubEngine.presenceSubscriptionNotRequired(this, node, user);
    }

    public void presenceSubscriptionRequired(Node node, JID user) {
        PubSubEngine.presenceSubscriptionRequired(this, node, user);
    }

    public void removeNode(String nodeID) {
        nodes.remove(nodeID);
    }

    public void send(Packet packet) {
        router.route(packet);
    }

    public void sendNotification(Node node, Message message, JID recipientJID) {
        message.setFrom(getAddress());

        // If this PEPService can retrieve presence information for the recipient,
        // collect all of their full JIDs and send the notification to each below.
        Collection<JID> recipientFullJIDs = new ArrayList<JID>();
        if (XMPPServer.getInstance().isLocal(recipientJID)) {
            for (ClientSession clientSession : SessionManager.getInstance().getSessions(recipientJID.getNode())) {
                recipientFullJIDs.add(clientSession.getAddress());
            }
        }
        else {
            recipientFullJIDs.add(recipientJID);
        }

        message.setTo(recipientJID);
        message.setID(node.getNodeID() + "__" + recipientJID.toBareJID() + "__" + StringUtils.randomString(5));

        for (JID recipientFullJID : recipientFullJIDs) {
            // Include an Extended Stanza Addressing "replyto" extension specifying the publishing
            // resource. However, only include the extension if the receiver has a presence subscription
            // to the service owner.
            try {
                // Get the full JID of the item publisher from the node that was published to.
                // This full JID will be used as the "replyto" address in the addressing extension.
                JID publisher = null;

                Element itemsElement = message.getElement().element("event").element("items");
                String publishedItemID = itemsElement.element("item").attributeValue("id");
                String nodeIDPublishedTo = itemsElement.attributeValue("node");

                // Check if the recipientFullJID is interested in notifications for this node.
                // If the recipient has not yet requested any notification filtering, continue and send
                // the notification.
                Map<String, HashSet<String>> filteredNodesMap = XMPPServer.getInstance().getIQPEPHandler().getFilteredNodesMap();
                HashSet<String> filteredNodesSet = filteredNodesMap.get(recipientFullJID.toString());
                if (filteredNodesSet != null && !filteredNodesSet.contains(nodeIDPublishedTo)) {
                    return;
                }

                if (node.isCollectionNode()) {
                    for (Node leafNode : node.getNodes()) {
                        if (leafNode.getNodeID().equals(nodeIDPublishedTo)) {
                            publisher = leafNode.getPublishedItem(publishedItemID).getPublisher();

                            // Ensure the recipientJID has access to receive notifications for items published to the leaf node.
                            AccessModel accessModel = leafNode.getAccessModel();
                            if (!accessModel.canAccessItems(leafNode, recipientFullJID, publisher)) {
                                return;
                            }

                            break;
                        }
                    }
                }
                else {
                    publisher = node.getPublishedItem(publishedItemID).getPublisher();
                }

                // Ensure the recipient is subscribed to the service owner's (publisher's) presence.
                Roster roster = XMPPServer.getInstance().getRosterManager().getRoster(publisher.getNode());
                RosterItem item = roster.getRosterItem(recipientFullJID);

                if (item.getSubStatus() == RosterItem.SUB_BOTH || item.getSubStatus() == RosterItem.SUB_FROM) {
                    Element addresses = DocumentHelper.createElement(QName.get("addresses", "http://jabber.org/protocol/address"));
                    Element address = addresses.addElement("address");
                    address.addAttribute("type", "replyto");
                    address.addAttribute("jid", publisher.toString());

                    Message extendedMessage = message.createCopy();
                    extendedMessage.addExtension(new PacketExtension(addresses));

                    extendedMessage.setTo(recipientFullJID);
                    router.route(extendedMessage);

                    return;
                }
            }
            catch (IndexOutOfBoundsException e) {
                // Do not add addressing extension to message.
            }
            catch (UserNotFoundException e) {
                // Do not add addressing extension to message.
            }
            catch (NullPointerException e) {
                // Do not add addressing extension to message.
            }
        }
        router.route(message);
    }

    public boolean isNodeCreationRestricted() {
        return nodeCreationRestricted;
    }

    public void queueItemToAdd(PublishedItem newItem) {
        PubSubEngine.queueItemToAdd(this, newItem);

    }

    public void queueItemToRemove(PublishedItem removedItem) {
        PubSubEngine.queueItemToRemove(this, removedItem);

    }

    public Map<String, Map<String, String>> getBarePresences() {
        return barePresences;
    }

    public Queue<PublishedItem> getItemsToAdd() {
        return itemsToAdd;
    }

    public Queue<PublishedItem> getItemsToDelete() {
        return itemsToDelete;
    }

    public AdHocCommandManager getManager() {
        return manager;
    }

    public PublishedItemTask getPublishedItemTask() {
        return publishedItemTask;
    }

    public void setPublishedItemTask(PublishedItemTask task) {
        publishedItemTask = task;
    }

    public Timer getTimer() {
        return timer;
    }

    public int getItemsTaskTimeout() {
        return items_task_timeout;
    }

    public void setItemsTaskTimeout(int timeout) {
        items_task_timeout = timeout;
    }

}
