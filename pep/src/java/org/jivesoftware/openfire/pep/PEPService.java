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
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.StringUtils;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import java.util.Collection;
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
    private String bareJID;

    /**
     * Collection node that acts as the root node of the entire node hierarchy.
     */
    private CollectionNode rootCollectionNode = null;

    /**
     * Nodes managed by this service, table: key nodeID (String); value Node
     */
    private Map<String, Node> nodes = new ConcurrentHashMap<String, Node>();

    /**
     * Keep a registry of the presence's show value of users that subscribed to
     * a node of the pubsub service and for which the node only delivers
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
     * Returns the permission policy for creating nodes. A true value means that
     * not anyone can create a node, only the JIDs listed in
     * <code>allowedToCreate</code> are allowed to create nodes.
     */
    private boolean nodeCreationRestricted = false;

    /**
     * Flag that indicates if a user may have more than one subscription with
     * the node. When multiple subscriptions is enabled each subscription
     * request, event notification and unsubscription request should include a
     * subid attribute.
     */
    private boolean multipleSubscriptionsEnabled = true;

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
     * Constructs a PEPService.
     * 
     * @param server  the XMPP server.
     * @param bareJID the bare JID (service ID) of the user owning the service.
     */
    public PEPService(XMPPServer server, String bareJID) {
        this.bareJID = bareJID;
        router = server.getPacketRouter();

        // Initialize the ad-hoc commands manager to use for this pubsub service
        manager = new AdHocCommandManager();
        manager.addCommand(new PendingSubscriptionsCommand(this));

        // Save or delete published items from the database every 2 minutes
        // starting in
        // 2 minutes (default values)
        publishedItemTask = new PublishedItemTask(this);
        timer.schedule(publishedItemTask, items_task_timeout, items_task_timeout);

        multipleSubscriptionsEnabled = JiveGlobals.getBooleanProperty("xmpp.pubsub.multiple-subscriptions", true);

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
            collectionDefaultConfiguration.setAccessModel(AccessModel.open);
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
        String rootNodeID = JiveGlobals.getProperty("xmpp.pubsub.root.nodeID", bareJID);
        if (nodes.isEmpty()) {
            // Create root collection node
            String creator = JiveGlobals.getProperty("xmpp.pubsub.root.creator");
            JID creatorJID = creator != null ? new JID(creator) : server.getAdmins().iterator().next();
            rootCollectionNode = new CollectionNode(this, null, rootNodeID, creatorJID);
            // Add the creator as the node owner
            rootCollectionNode.addOwner(creatorJID);
            // Save new root node
            rootCollectionNode.saveToDB();
        }
        else {
            rootCollectionNode = (CollectionNode) getNode(rootNodeID);
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
        return new JID(bareJID);
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
        return bareJID;     // The bare JID of the user is the service ID
    }

    public Collection<String> getShowPresences(JID subscriber) {
        return PubSubEngine.getShowPresences(this, subscriber);
    }

    public boolean isCollectionNodesSupported() {
        return false;
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
        if (bareJID == user.toBareJID()) {
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

    public void sendNotification(Node node, Message message, JID jid) {
        message.setFrom(getAddress());
        message.setTo(jid);
        message.setID(node.getNodeID() + "__" + jid.toBareJID() + "__" + StringUtils.randomString(5));
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
