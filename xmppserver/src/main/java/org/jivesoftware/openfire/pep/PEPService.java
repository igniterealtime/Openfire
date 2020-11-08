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

package org.jivesoftware.openfire.pep;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.jivesoftware.util.cache.Cacheable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketExtension;

/**
 * A PEPService is a {@link PubSubService} for use with XEP-0163: "Personal Eventing via
 * Pubsub" Version 1.0
 * 
 * Note: Although this class implements {@link Cacheable}, instances should only be 
 * cached in caches that have time-based (as opposed to size-based) eviction policies.
 * 
 * @author Armando Jagucki 
 */
public class PEPService implements PubSubService, Cacheable {

    private static final Logger Log = LoggerFactory.getLogger( PEPService.class );

    /**
     * The bare JID that this service is identified by.
     */
    private final JID serviceOwner;

    /**
     * Collection node that acts as the root node of the entire node hierarchy.
     */
    private CollectionNode rootCollectionNode = null;

    /**
     * Nodes managed by this service, table: key nodeID (String); value Node
     */
    private final Map<Node.UniqueIdentifier, Node> nodes = new ConcurrentHashMap<>();

    /**
     * The packet router for the server.
     */
    private final PacketRouter router;

    /**
     * Default configuration to use for newly created leaf nodes.
     */
    private DefaultNodeConfiguration leafDefaultConfiguration;

    /**
     * Default configuration to use for newly created collection nodes.
     */
    private DefaultNodeConfiguration collectionDefaultConfiguration;

    /**
     * Keep a registry of the presence's show value of users that subscribed to
     * a node of the pep service and for which the node only delivers
     * notifications for online users or node subscriptions deliver events based
     * on the user presence show value. Offline users will not have an entry in
     * the map. Note: Key-> bare JID and Value-> Map whose key is full JID of
     * connected resource and value is show value of the last received presence.
     */
    private final Map<JID, Map<JID, String>> barePresences = new ConcurrentHashMap<>();

    /**
     * Manager that keeps the list of ad-hoc commands and processing command
     * requests.
     */
    private final AdHocCommandManager adHocCommandManager;

    /**
     * Used to handle filtered-notifications.
     */
    private final EntityCapabilitiesManager entityCapsManager = XMPPServer.getInstance().getEntityCapabilitiesManager();

    /**
     * Constructs a PEPService.
     *
     * @param server  the XMPP server.
     * @param bareJID the bare JID (service ID) of the user owning the service.
     * @deprecated Replaced by {@link #PEPService(XMPPServer, JID)}
     */
    @Deprecated
    public PEPService(XMPPServer server, String bareJID) {
        this(server, new JID(bareJID).asBareJID());
    }

    /**
     * Constructs a PEPService.
     * 
     * @param server  the XMPP server.
     * @param bareJID the bare JID (service ID) of the user owning the service.
     */
    public PEPService(XMPPServer server, JID bareJID) {
        this.serviceOwner = bareJID.asBareJID();
        router = server.getPacketRouter();

        // Initialize the ad-hoc commands manager to use for this pep service
        adHocCommandManager = new AdHocCommandManager();
        adHocCommandManager.addCommand(new PendingSubscriptionsCommand(this));

        // Load default configuration for leaf nodes
        final PubSubPersistenceProvider persistenceProvider = XMPPServer.getInstance().getPubSubModule().getPersistenceProvider();
        leafDefaultConfiguration = persistenceProvider.loadDefaultConfiguration(this.getUniqueIdentifier(), true);
        if (leafDefaultConfiguration == null) {
            // Create and save default configuration for leaf nodes;
            leafDefaultConfiguration = new DefaultNodeConfiguration(true);
            leafDefaultConfiguration.setAccessModel(AccessModel.presence);
            leafDefaultConfiguration.setPublisherModel(PublisherModel.publishers);
            leafDefaultConfiguration.setDeliverPayloads(true);
            leafDefaultConfiguration.setLanguage("English");
            leafDefaultConfiguration.setMaxPayloadSize(10 * 1024 * 1024); // Probably should not be larger than the max read buffer for stanzas!
            leafDefaultConfiguration.setNotifyConfigChanges(true);
            leafDefaultConfiguration.setNotifyDelete(true);
            leafDefaultConfiguration.setNotifyRetract(true);
            leafDefaultConfiguration.setPersistPublishedItems(true);
            leafDefaultConfiguration.setMaxPublishedItems(1);
            leafDefaultConfiguration.setPresenceBasedDelivery(false);
            leafDefaultConfiguration.setSendItemSubscribe(true);
            leafDefaultConfiguration.setSubscriptionEnabled(true);
            leafDefaultConfiguration.setReplyPolicy(null);
            persistenceProvider.createDefaultConfiguration(this.getUniqueIdentifier(), leafDefaultConfiguration);
        }
        // Load default configuration for collection nodes
        collectionDefaultConfiguration = persistenceProvider.loadDefaultConfiguration(this.getUniqueIdentifier(), false);
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
            persistenceProvider.createDefaultConfiguration(this.getUniqueIdentifier(), collectionDefaultConfiguration);
        }
    }

    public void initialize() {
        // Load nodes to memory
        XMPPServer.getInstance().getPubSubModule().getPersistenceProvider().loadNodes(this);
        // Ensure that we have a root collection node
        if (nodes.isEmpty()) {
            // Create root collection node
            rootCollectionNode = new CollectionNode(this.getUniqueIdentifier(), null, this.serviceOwner.toString(), this.serviceOwner, collectionDefaultConfiguration);

            // Save new root node
            rootCollectionNode.saveToDB();

            // Add the creator as the node owner
            rootCollectionNode.addOwner(this.serviceOwner);
        }
        else {
            rootCollectionNode = (CollectionNode) getNode(this.serviceOwner.toString());
        }
    }

    @Override
    public void addNode(Node node) {
        nodes.put(node.getUniqueIdentifier(), node);
    }

    @Override
    public void removeNode(Node.UniqueIdentifier nodeID) {
        nodes.remove(nodeID);
    }

    @Override
    public Node getNode(Node.UniqueIdentifier nodeID) {
        return nodes.get(nodeID);
    }

    @Override
    public Collection<Node> getNodes() {
        return nodes.values();
    }

    @Override
    public CollectionNode getRootCollectionNode() {
        return rootCollectionNode;
    }

    @Override
    public JID getAddress() {
        return serviceOwner;
    }

    @Override
    public String getServiceID() {
        // The bare JID of the user is the service ID for PEP
        return serviceOwner.toString();
    }

    @Override
    public DefaultNodeConfiguration getDefaultNodeConfiguration(boolean leafType) {
        if (leafType) {
            return leafDefaultConfiguration;
        }
        return collectionDefaultConfiguration;
    }

    @Override
    public Collection<String> getShowPresences(JID subscriber) {
        return PubSubEngine.getShowPresences(this, subscriber);
    }

    @Override
    public boolean canCreateNode(JID creator) {
        // Node creation is always allowed for sysadmin
        return !isNodeCreationRestricted() || isServiceAdmin(creator);
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

        return item.getSubStatus() == RosterItem.SUB_BOTH || item.getSubStatus() == RosterItem.SUB_FROM;
    }

    @Override
    public boolean isCollectionNodesSupported() {
        return true;
    }

    @Override
    public boolean isInstantNodeSupported() {
        return true;
    }

    @Override
    public boolean isMultipleSubscriptionsEnabled() {
        return false;
    }

    @Override
    public boolean isServiceAdmin(JID user) {
        // Here we consider a 'service admin' to be the user that this PEPService is associated with.
        return serviceOwner.equals(user.asBareJID());
    }

    /**
     * Returns the permission policy for creating nodes. A true value means that not anyone can create a node,
     * only the service admin.
     *
     * Note that PEP services will always return 'true'.
     *
     * @return true
     */
    public boolean isNodeCreationRestricted() {
        return true;
    }

    @Override
    public void presenceSubscriptionNotRequired(Node node, JID user) {
        PubSubEngine.presenceSubscriptionNotRequired(this, node, user);
    }

    @Override
    public void presenceSubscriptionRequired(Node node, JID user) {
        PubSubEngine.presenceSubscriptionRequired(this, node, user);
    }

    @Override
    public void send(Packet packet) {
        router.route(packet);
    }

    @Override
    public void broadcast(Node node, Message message, Collection<JID> jids) {
        if ( Log.isTraceEnabled() ) {
            Log.trace( "Service '{}' is broadcasting a notification on node '{}' to a collection of JIDs: {}", this.getServiceID(), node.getUniqueIdentifier().getNodeId(), jids.stream().map(JID::toString).collect(Collectors.joining(", ")) );
        }
        message.setFrom(getAddress());
        for (JID jid : jids) {
            message.setTo(jid);
            message.setID(StringUtils.randomString(8));
            router.route(message);
        }
    }

    @Override
    public void sendNotification(Node node, Message orig, JID recipientJID) {
        Log.trace( "Recipient '{}' is an intended recipient of service '{}' sending a notification for node '{}': {}", recipientJID, this.getServiceID(), node.getUniqueIdentifier().getNodeId(), orig.toXML() );

        final boolean recipientIsOwner = serviceOwner.asBareJID().equals(recipientJID.asBareJID());

        final Set<JID> deliveryAddresses = new HashSet<>();

        if (XMPPServer.getInstance().isLocal(recipientJID)) {
            // When the recipient is a local entity, then we can be sure that we have appropriate presence information
            // to determine all desirable delivery addresses.
            if (recipientJID.getResource() == null) {
                // If the recipient subscribed with a bare JID collect all of their full JIDs based on active sessions
                // and send the notification to each below.
                Log.trace("Recipient '{}' is a local user using a bare JID. Notifications will be sent to all its active sessions.", recipientJID);
                for (final ClientSession clientSession : SessionManager.getInstance().getSessions(recipientJID.getNode())) {
                    if (!clientSession.isClosed()) {
                        deliveryAddresses.add(clientSession.getAddress());
                    }
                }
            } else {
                // If the recipient subscribed with a full JID, only send a notification if their session is online.
                final ClientSession session = SessionManager.getInstance().getSession(recipientJID);
                final boolean doDeliver = session != null && !session.isClosed();
                Log.trace("Recipient '{}' is a local user using a full JID. Notifications will be sent only if there's an active session (which there {})", recipientJID, doDeliver ? "is" : "is not");
                if (doDeliver) {
                    deliveryAddresses.add(recipientJID);
                }
            }
        } else {
            // When the recipient is not a local entity, this service does not have appropriate presence information
            // and can only use the provided recipient address.
            Log.trace("Recipient '{}' is a remote user. Notifications will be sent to to the address as-is.", recipientJID);
            deliveryAddresses.add(recipientJID);
        }

        if (deliveryAddresses.isEmpty()) {
            Log.trace("Recipient '{}': Done processing, recipient has no delivery addresses", recipientJID);
            return;
        }

        if ( Log.isTraceEnabled() ) {
            Log.trace("Recipient '{}' has these delivery address(es): {}", recipientJID, deliveryAddresses.stream().map(JID::toString).collect(Collectors.joining(", ")));
        }

        final Message message = orig.createCopy(); // Defensive copy: Do not let data from one iteration leak into the next.
        message.setTo(recipientJID);
        message.setFrom(getAddress());
        message.setID(StringUtils.randomString(8));

        for (final JID deliveryAddress : deliveryAddresses) {
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
                EntityCapabilities entityCaps = entityCapsManager.getEntityCapabilities(deliveryAddress);
                if (entityCaps != null) {
                    if (!entityCaps.containsFeature(nodeID + "+notify")) {
                        Log.trace( "Recipient '{}': Not sending notification to address '{}' that does not have CAPS {}+notify", recipientJID, deliveryAddress, nodeID );
                        continue;
                    }
                }

                // Get the full JID of the item publisher from the node that was published to.
                // This full JID will be used as the "replyto" address in the addressing extension.
                if (node.isCollectionNode()) {
                    for (Node leafNode : node.getNodes()) {
                        if (leafNode.getUniqueIdentifier().getNodeId().equals(nodeID)) {
                            publisher = leafNode.getPublishedItem(itemID).getPublisher();

                            // Ensure the recipientJID has access to receive notifications for items published to the leaf node.
                            AccessModel accessModel = leafNode.getAccessModel();
                            if (!accessModel.canAccessItems(leafNode, deliveryAddress, publisher)) {
                                Log.trace( "Recipient '{}': Not sending notification to address '{}': This JID is not allowed to access items on node {}", recipientJID, deliveryAddress, nodeID );
                                continue;
                            }

                            break;
                        }
                    }
                }
                else {
                    publisher = node.getPublishedItem(itemID).getPublisher();
                }

                // Ensure the recipient is subscribed to the service owner's (publisher's) presence.
                if (publisher == null) {
                    Log.warn( "Item {} on node {} has no known publisher.", itemID, node.getUniqueIdentifier());
                } else if (recipientIsOwner || canProbePresence(publisher, deliveryAddress)) {
                    Element addresses = DocumentHelper.createElement(QName.get("addresses", "http://jabber.org/protocol/address"));
                    Element address = addresses.addElement("address");
                    address.addAttribute("type", "replyto");
                    address.addAttribute("jid", publisher.toString());

                    Message extendedMessage = message.createCopy();
                    extendedMessage.addExtension(new PacketExtension(addresses));

                    extendedMessage.setTo(deliveryAddress);
                    Log.trace( "Recipient '{}': Sending notification to recipient address: '{}'", recipientJID, extendedMessage.getTo() );
                    router.route(extendedMessage);
                } else {
                    Log.trace( "Recipient '{}': Not sending notification to address '{}': This address is not allowed to probe presence of publisher '{}'", recipientJID, deliveryAddress, publisher );
                }
            }
            catch (IndexOutOfBoundsException e) {
                // Do not add addressing extension to message.
                Log.trace( "IndexOutOfBoundException occurred while trying to send PEP notification.", e );
            }
            catch (UserNotFoundException e) {
                // Do not add addressing extension to message.
                Log.trace( "Recipient '{}': Service '{}' is sending a notification for node '{}' to address: {}", recipientJID, this.getServiceID(), node.getUniqueIdentifier().getNodeId(), message.getTo(), e );
                router.route(message);
            }
            catch (NullPointerException e) {
                try {
                    if (recipientIsOwner || canProbePresence(getAddress(), deliveryAddress)) {
                        message.setTo(deliveryAddress);
                    }
                }
                catch (UserNotFoundException e1) {
                    // Do nothing
                }
                Log.trace( "Recipient '{}': Service '{}' is sending a notification on node '{}' to address: {}", recipientJID, this.getServiceID(), node.getUniqueIdentifier().getNodeId(), message.getTo(), e );
                router.route(message);
            }
        }
        Log.trace( "Recipient '{}': Done processing notification for service '{}' on node '{}'", recipientJID, this.getServiceID(), node.getUniqueIdentifier().getNodeId() );
    }

    /**
     * Sends an event notification for the last published item of each leaf node under the
     * root collection node to the recipient JID. If the recipient is not the owner of this service,
     * has no subscription to the root collection node, has not yet been authorized, or is pending to be
     * configured -- then no notifications are going to be sent.<p>
     *
     * Depending on the subscription configuration the event notifications may or may not have
     * a payload, may not be sent if a keyword (i.e. filter) was defined and it was not matched.
     *
     * @param recipientJID the recipient that is to receive the last published item notifications.
     */
    public void sendLastPublishedItems(JID recipientJID) {
        sendLastPublishedItems(recipientJID, null);
    }

    /**
     * Sends an event notification for the last published item of each leaf node under the
     * root collection node to the recipient JID. If the recipient is not the owner of this service,
     * has no subscription to the root collection node, has not yet been authorized, or is pending to be
     * configured -- then no notifications are going to be sent.<p>
     *
     * Depending on the subscription configuration the event notifications may or may not have
     * a payload, may not be sent if a keyword (i.e. filter) was defined and it was not matched.
     *
     * An optional filter for nodes to be processed can be provided in the second argument to this method. When non-null
     * only the nodes that match an ID in the argument will be processed.
     *
     * @param recipientJID the recipient that is to receive the last published item notifications.
     * @param nodeIdFilter An optional filter of nodes to process (only IDs that are included in the filter are processed).
     */
    public void sendLastPublishedItems(JID recipientJID, Set<String> nodeIdFilter) {
        // Ensure the recipient has a subscription to this service's root collection node, or is its owner.
        final boolean isOwner = recipientJID.asBareJID().equals(this.serviceOwner);
        NodeSubscription subscription = rootCollectionNode.getSubscription(recipientJID);
        if (subscription == null) {
            subscription = rootCollectionNode.getSubscription(new JID(recipientJID.toBareJID()));
        }
        if (subscription == null && !isOwner) {
            return;
        }

        // Send the last published item of each leaf node to the recipient.
        for (Node leafNode : rootCollectionNode.getNodes()) {
            if ( nodeIdFilter != null && !nodeIdFilter.contains( leafNode.getUniqueIdentifier().getNodeId() ) ) {
                continue;
            }
            // Retrieve last published item for the leaf node.
            PublishedItem leafLastPublishedItem = leafNode.getLastPublishedItem();
            if (leafLastPublishedItem == null) {
                continue;
            }

            // Check if the published item can be sent to the subscriber
            if (subscription != null && !subscription.canSendPublicationEvent(leafLastPublishedItem.getNode(), leafLastPublishedItem)) {
                return;
            }

            // Send event notification to the subscriber
            Message notification = new Message();
            Element event = notification.getElement().addElement("event", "http://jabber.org/protocol/pubsub#event");
            Element items = event.addElement("items");
            items.addAttribute("node", leafLastPublishedItem.getNodeID());
            Element item = items.addElement("item");
            if (leafLastPublishedItem.getNode().isItemRequired()) {
                item.addAttribute("id", leafLastPublishedItem.getID());
            }
            if (leafLastPublishedItem.getNode().isPayloadDelivered() && leafLastPublishedItem.getPayload() != null) {
                item.add(leafLastPublishedItem.getPayload().createCopy());
            }
            // Add a message body (if required)
            if (subscription != null && subscription.isIncludingBody()) {
                notification.setBody(LocaleUtils.getLocalizedString("pubsub.notification.message.body"));
            }
            // Include date when published item was created
            notification.getElement().addElement("delay", "urn:xmpp:delay").addAttribute("stamp", XMPPDateTimeFormat.format(leafLastPublishedItem.getCreationDate()));
            // Send the event notification to the subscriber
            this.sendNotification(subscription != null ? subscription.getNode() : leafNode, notification, subscription != null ? subscription.getJID() : recipientJID);
        }
    }

    @Override
    public Map<JID, Map<JID, String>> getSubscriberPresences() {
        return barePresences;
    }

    @Override
    public AdHocCommandManager getManager() {
        return adHocCommandManager;
    }

    @Override
    public int getCachedSize() {
        // Rather arbitrary. Don't use this for size-based eviction policies!
        return 600;
    }
}
