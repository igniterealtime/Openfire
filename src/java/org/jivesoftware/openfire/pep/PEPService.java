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

package org.jivesoftware.openfire.pep;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.commands.AdHocCommandManager;
import org.jivesoftware.openfire.entitycaps.EntityCapabilities;
import org.jivesoftware.openfire.entitycaps.EntityCapabilitiesManager;
import org.jivesoftware.openfire.pubsub.*;
import org.jivesoftware.openfire.pubsub.models.AccessModel;
import org.jivesoftware.openfire.pubsub.models.PublisherModel;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.FastDateFormat;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.StringUtils;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketExtension;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A PEPService is a {@link PubSubService} for use with XEP-0163: "Personal Eventing via
 * Pubsub" Version 1.0
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
    private AdHocCommandManager adHocCommandManager;

    /**
     * Used to handle filtered-notifications.
     */
    private EntityCapabilitiesManager entityCapsManager = EntityCapabilitiesManager.getInstance();

    /**
     * The time to elapse between each execution of the maintenance process.
     * Default is 2 minutes.
     */
    private int items_task_timeout = 2 * 60 * 1000;

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
     * Date format to use for time stamps in delayed event notifications.
     */
    private static final FastDateFormat fastDateFormat;

    static {
        fastDateFormat = FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", TimeZone.getTimeZone("UTC"));
    }

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
        adHocCommandManager = new AdHocCommandManager();
        adHocCommandManager.addCommand(new PendingSubscriptionsCommand(this));

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

    public void removeNode(String nodeID) {
        nodes.remove(nodeID);
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

    public JID getAddress() {
        return new JID(serviceOwnerJID);
    }

    public String getServiceID() {
        // The bare JID of the user is the service ID for PEP
        return serviceOwnerJID;
    }

    public DefaultNodeConfiguration getDefaultNodeConfiguration(boolean leafType) {
        if (leafType) {
            return leafDefaultConfiguration;
        }
        return collectionDefaultConfiguration;
    }

    public Collection<String> getShowPresences(JID subscriber) {
        return PubSubEngine.getShowPresences(this, subscriber);
    }

    public boolean canCreateNode(JID creator) {
        // Node creation is always allowed for sysadmin
        if (isNodeCreationRestricted() && !isServiceAdmin(creator)) {
            // The user is not allowed to create nodes
            return false;
        }
        return true;
    }

    /**
     * Returns true if the the prober is allowed to see the presence of the probee.
     *
     * @param prober the user that is trying to probe the presence of another user.
     * @param probee the username of the uset that is being probed.
     * @return true if the the prober is allowed to see the presence of the probee.
     * @throws UserNotFoundException If the probee does not exist in the local server or the prober
     *         is not present in the roster of the probee.
     */
    private boolean canProbePresence(JID prober, JID probee) throws UserNotFoundException {
        Roster roster;
        roster = XMPPServer.getInstance().getRosterManager().getRoster(prober.getNode());
        RosterItem item = roster.getRosterItem(probee);

        if (item.getSubStatus() == RosterItem.SUB_BOTH || item.getSubStatus() == RosterItem.SUB_FROM) {
            return true;
        }

        return false;
    }

    public boolean isCollectionNodesSupported() {
        return true;
    }

    public boolean isInstantNodeSupported() {
        return true;
    }

    public boolean isMultipleSubscriptionsEnabled() {
        return false;
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

    public boolean isNodeCreationRestricted() {
        return nodeCreationRestricted;
    }

    public void presenceSubscriptionNotRequired(Node node, JID user) {
        PubSubEngine.presenceSubscriptionNotRequired(this, node, user);
    }

    public void presenceSubscriptionRequired(Node node, JID user) {
        PubSubEngine.presenceSubscriptionRequired(this, node, user);
    }

    public void send(Packet packet) {
        router.route(packet);
    }

    public void broadcast(Node node, Message message, Collection<JID> jids) {
        message.setFrom(getAddress());
        for (JID jid : jids) {
            message.setTo(jid);
            message.setID(node.getNodeID() + "__" + jid.toBareJID() + "__" + StringUtils.randomString(5));
            router.route(message);
        }
    }

    public void sendNotification(Node node, Message message, JID recipientJID) {
        message.setTo(recipientJID);
        message.setFrom(getAddress());
        message.setID(node.getNodeID() + "__" + recipientJID.toBareJID() + "__" + StringUtils.randomString(5));

        // If the recipient subscribed with a bare JID and this PEPService can retrieve
        // presence information for the recipient, collect all of their full JIDs and
        // send the notification to each below.
        Set<JID> recipientFullJIDs = new HashSet<JID>();
        if (XMPPServer.getInstance().isLocal(recipientJID)) {
            if (recipientJID.getResource() == null) {
                for (ClientSession clientSession : SessionManager.getInstance().getSessions(recipientJID.getNode())) {
                    recipientFullJIDs.add(clientSession.getAddress());
                }
            }
        }
        else {
            // Since recipientJID is not local, try to get presence info from cached known remote
            // presences.
            Map<String, Set<JID>> knownRemotePresences = XMPPServer.getInstance().getIQPEPHandler().getKnownRemotePresenes();

            Set<JID> remotePresenceSet = knownRemotePresences.get(getAddress().toBareJID());
            if (remotePresenceSet != null) {
                for (JID remotePresence : remotePresenceSet) {
                    if (recipientJID.toBareJID().equals(remotePresence.toBareJID())) {
                        recipientFullJIDs.add(remotePresence);
                    }
                }
            }
        }

        if (recipientFullJIDs.isEmpty()) {
            router.route(message);
            return;
        }

        for (JID recipientFullJID : recipientFullJIDs) {
            // Include an Extended Stanza Addressing "replyto" extension specifying the publishing
            // resource. However, only include the extension if the receiver has a presence subscription
            // to the service owner.
            try {
                JID publisher = null;

                // Get the ID of the node that had an item published to or retracted from.
                Element itemsElement = message.getElement().element("event").element("items");
                String nodeID = itemsElement.attributeValue("node");

                // Get the ID of the item that was published or retracted.
                String itemID = null;
                Element itemElement = itemsElement.element("item");
                if (itemElement == null) {
                    Element retractElement = itemsElement.element("retract");
                    if (retractElement != null) {
                        itemID = retractElement.attributeValue("id");
                    }
                }
                else {
                    itemID = itemElement.attributeValue("id");
                }

                // Check if the recipientFullJID is interested in notifications for this node.
                // If the recipient has not yet requested any notification filtering, continue and send
                // the notification.
                EntityCapabilities entityCaps = entityCapsManager.getEntityCapabilities(recipientFullJID);
                if (entityCaps != null) {
                    if (!entityCaps.containsFeature(nodeID + "+notify")) {
                        return;
                    }
                }

                // Get the full JID of the item publisher from the node that was published to.
                // This full JID will be used as the "replyto" address in the addressing extension.
                if (node.isCollectionNode()) {
                    for (Node leafNode : node.getNodes()) {
                        if (leafNode.getNodeID().equals(nodeID)) {
                            publisher = leafNode.getPublishedItem(itemID).getPublisher();

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
                    publisher = node.getPublishedItem(itemID).getPublisher();
                }

                // Ensure the recipient is subscribed to the service owner's (publisher's) presence.
                if (canProbePresence(publisher, recipientFullJID)) {
                    Element addresses = DocumentHelper.createElement(QName.get("addresses", "http://jabber.org/protocol/address"));
                    Element address = addresses.addElement("address");
                    address.addAttribute("type", "replyto");
                    address.addAttribute("jid", publisher.toString());

                    Message extendedMessage = message.createCopy();
                    extendedMessage.addExtension(new PacketExtension(addresses));

                    extendedMessage.setTo(recipientFullJID);
                    router.route(extendedMessage);
                }
            }
            catch (IndexOutOfBoundsException e) {
                // Do not add addressing extension to message.
            }
            catch (UserNotFoundException e) {
                // Do not add addressing extension to message.
                router.route(message);
            }
            catch (NullPointerException e) {
                try {
                    if (canProbePresence(getAddress(), recipientFullJID)) {
                        message.setTo(recipientFullJID);
                    }
                }
                catch (UserNotFoundException e1) {
                    // Do nothing
                }
                router.route(message);
            }
        }
    }

    /**
     * Sends an event notification for the last published item of each leaf node under the
     * root collection node to the recipient JID. If the recipient has no subscription to
     * the root collection node, has not yet been authorized, or is pending to be
     * configured -- then no notifications are going to be sent.<p>
     *
     * Depending on the subscription configuration the event notifications may or may not have
     * a payload, may not be sent if a keyword (i.e. filter) was defined and it was not matched.
     *
     * @param recipientJID the recipient that is to receive the last published item notifications.
     */
    public void sendLastPublishedItems(JID recipientJID) {
        // Ensure the recipient has a subscription to this service's root collection node.
        NodeSubscription subscription = rootCollectionNode.getSubscription(recipientJID);
        if (subscription == null) {
            subscription = rootCollectionNode.getSubscription(new JID(recipientJID.toBareJID()));
        }
        if (subscription == null) {
            return;
        }

        // Send the last published item of each leaf node to the recipient.
        for (Node leafNode : rootCollectionNode.getNodes()) {
            // Retrieve last published item for the leaf node.
            PublishedItem leafLastPublishedItem = null;
            leafLastPublishedItem = leafNode.getLastPublishedItem();
            if (leafLastPublishedItem == null) {
                continue;
            }

            // Check if the published item can be sent to the subscriber
            if (!subscription.canSendPublicationEvent(leafLastPublishedItem.getNode(), leafLastPublishedItem)) {
                return;
            }

            // Send event notification to the subscriber
            Message notification = new Message();
            Element event = notification.getElement().addElement("event", "http://jabber.org/protocol/pubsub#event");
            Element items = event.addElement("items");
            items.addAttribute("node", leafLastPublishedItem.getNode().getNodeID());
            Element item = items.addElement("item");
            if (((LeafNode) leafLastPublishedItem.getNode()).isItemRequired()) {
                item.addAttribute("id", leafLastPublishedItem.getID());
            }
            if (leafLastPublishedItem.getNode().isPayloadDelivered() && leafLastPublishedItem.getPayload() != null) {
                item.add(leafLastPublishedItem.getPayload().createCopy());
            }
            // Add a message body (if required)
            if (subscription.isIncludingBody()) {
                notification.setBody(LocaleUtils.getLocalizedString("pubsub.notification.message.body"));
            }
            // Include date when published item was created
            notification.getElement().addElement("x", "jabber:x:delay").addAttribute("stamp", fastDateFormat.format(leafLastPublishedItem.getCreationDate()));
            // Send the event notification to the subscriber
            this.sendNotification(subscription.getNode(), notification, subscription.getJID());
        }
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
        return adHocCommandManager;
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
