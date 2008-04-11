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
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.RoutableChannelHandler;
import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.ClusterEventListener;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.commands.AdHocCommandManager;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.disco.*;
import org.jivesoftware.openfire.forms.DataForm;
import org.jivesoftware.openfire.forms.spi.XDataFormImpl;
import org.jivesoftware.openfire.forms.spi.XFormFieldImpl;
import org.jivesoftware.openfire.pubsub.models.AccessModel;
import org.jivesoftware.openfire.pubsub.models.PublisherModel;
import org.jivesoftware.util.*;
import org.xmpp.packet.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Module that implements JEP-60: Publish-Subscribe. By default node collections and
 * instant nodes are supported.
 *
 * @author Matt Tucker
 */
public class PubSubModule extends BasicModule implements ServerItemsProvider, DiscoInfoProvider,
        DiscoItemsProvider, RoutableChannelHandler, PubSubService, ClusterEventListener, PropertyEventListener {

    /**
     * the chat service's hostname
     */
    private String serviceName = null;

    /**
     * Collection node that acts as the root node of the entire node hierarchy.
     */
    private CollectionNode rootCollectionNode = null;

    /**
     * Nodes managed by this manager, table: key nodeID (String); value Node
     */
    private Map<String, Node> nodes = new ConcurrentHashMap<String, Node>();
    
    /**
     * Keep a registry of the presence's show value of users that subscribed to a node of
     * the pubsub service and for which the node only delivers notifications for online users
     * or node subscriptions deliver events based on the user presence show value. Offline
     * users will not have an entry in the map. Note: Key-> bare JID and Value-> Map whose key
     * is full JID of connected resource and value is show value of the last received presence.
     */
    private Map<String, Map<String, String>> barePresences =
            new ConcurrentHashMap<String, Map<String, String>>();
    
    /**
     * Queue that holds the items that need to be added to the database.
     */
    private Queue<PublishedItem> itemsToAdd = new LinkedBlockingQueue<PublishedItem>();
    /**
     * Queue that holds the items that need to be deleted from the database.
     */
    private Queue<PublishedItem> itemsToDelete = new LinkedBlockingQueue<PublishedItem>();
    
    /**
     * Manager that keeps the list of ad-hoc commands and processing command requests.
     */
    private AdHocCommandManager manager;
    
    /**
     * The time to elapse between each execution of the maintenance process. Default
     * is 2 minutes.
     */
    private int items_task_timeout = 2 * 60 * 1000;

    /**
     * Task that saves or deletes published items from the database.
     */
    private PublishedItemTask publishedItemTask;

    /**
     * Timer to save published items to the database or remove deleted or old items.
     */
    private Timer timer = new Timer("PubSub maintenance");

    /**
     * Returns the permission policy for creating nodes. A true value means that not anyone can
     * create a node, only the JIDs listed in <code>allowedToCreate</code> are allowed to create
     * nodes.
     */
    private boolean nodeCreationRestricted = false;

    /**
     * Flag that indicates if a user may have more than one subscription with the node. When multiple
     * subscriptions is enabled each subscription request, event notification and unsubscription request
     * should include a subid attribute.
     */
    private boolean multipleSubscriptionsEnabled = true;

    /**
     * Bare jids of users that are allowed to create nodes. An empty list means that anyone can
     * create nodes.
     */
    private Collection<String> allowedToCreate = new CopyOnWriteArrayList<String>();

    /**
     * Bare jids of users that are system administrators of the PubSub service. A sysadmin
     * has the same permissions as a node owner.
     */
    private Collection<String> sysadmins = new CopyOnWriteArrayList<String>();

    /**
     * The packet router for the server.
     */
    private PacketRouter router = null;

    private RoutingTable routingTable = null;

    /**
     * Default configuration to use for newly created leaf nodes.
     */
    private DefaultNodeConfiguration leafDefaultConfiguration;
    /**
     * Default configuration to use for newly created collection nodes.
     */
    private DefaultNodeConfiguration collectionDefaultConfiguration;

    /**
     * Private component that actually performs the pubsub work.
     */
    private PubSubEngine engine = null;

    /**
     * Flag that indicates if the service is enabled.
     */
    private boolean serviceEnabled = true;

    public PubSubModule() {
        super("Publish Subscribe Service");
        
        // Initialize the ad-hoc commands manager to use for this pubsub service
        manager = new AdHocCommandManager();
        manager.addCommand(new PendingSubscriptionsCommand(this));
        
        // Save or delete published items from the database every 2 minutes starting in
        // 2 minutes (default values)
        publishedItemTask = new PublishedItemTask(this);
        timer.schedule(publishedItemTask, items_task_timeout, items_task_timeout);
    }

    public void process(Packet packet) {
        // TODO Remove this method when moving PubSub as a component and removing module code
        // The MUC service will receive all the packets whose domain matches the domain of the MUC
        // service. This means that, for instance, a disco request should be responded by the
        // service itself instead of relying on the server to handle the request.
        try {
            // Check if the packet is a disco request or a packet with namespace iq:register
            if (packet instanceof IQ) {
                if (!engine.process(this, (IQ) packet)) {
                    process((IQ) packet);
                }
            }
            else if (packet instanceof Presence) {
                engine.process(this, (Presence) packet);
            }
            else {
                engine.process(this, (Message) packet);
            }
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            if (packet instanceof IQ) {
                // Send internal server error
                IQ reply = IQ.createResultIQ((IQ) packet);
                reply.setError(PacketError.Condition.internal_server_error);
                send(reply);
            }
        }
    }

    private void process(IQ iq) {
        // Ignore IQs of type ERROR
        if (IQ.Type.error == iq.getType()) {
            return;
        }
        Element childElement = iq.getChildElement();
        String namespace = null;

        if (childElement != null) {
            namespace = childElement.getNamespaceURI();
        }
        if ("http://jabber.org/protocol/disco#info".equals(namespace)) {
            // TODO PubSub should have an IQDiscoInfoHandler of its own when PubSub becomes
            // a component
            IQ reply = XMPPServer.getInstance().getIQDiscoInfoHandler().handleIQ(iq);
            router.route(reply);
        }
        else if ("http://jabber.org/protocol/disco#items".equals(namespace)) {
            // TODO PubSub should have an IQDiscoItemsHandler of its own when PubSub becomes
            // a component
            IQ reply = XMPPServer.getInstance().getIQDiscoItemsHandler().handleIQ(iq);
            router.route(reply);
        }
        else {
            // Unknown namespace requested so return error to sender
            engine.sendErrorPacket(iq, PacketError.Condition.service_unavailable, null);
        }
    }

    public String getServiceID() {
        return "pubsub";
    }

    public boolean canCreateNode(JID creator) {
        // Node creation is always allowed for sysadmin
        if (isNodeCreationRestricted() && !isServiceAdmin(creator)) {
            // The user is not allowed to create nodes
            return false;
        }
        return true;
    }

    public boolean isServiceAdmin(JID user) {
        return sysadmins.contains(user.toBareJID()) || allowedToCreate.contains(user.toBareJID()) ||
                InternalComponentManager.getInstance().hasComponent(user);
    }

    public boolean isInstantNodeSupported() {
        return true;
    }

    public boolean isCollectionNodesSupported() {
        return true;
    }

    public CollectionNode getRootCollectionNode() {
        return rootCollectionNode;
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

    public void presenceSubscriptionNotRequired(Node node, JID user) {
        PubSubEngine.presenceSubscriptionNotRequired(this, node, user);
    }

    public void presenceSubscriptionRequired(Node node, JID user) {
        PubSubEngine.presenceSubscriptionRequired(this, node, user);
    }

    public void queueItemToAdd(PublishedItem newItem) {
        PubSubEngine.queueItemToAdd(this, newItem);
    }

    public void queueItemToRemove(PublishedItem removedItem) {
        PubSubEngine.queueItemToRemove(this, removedItem);
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getServiceDomain() {
        return serviceName + "." + XMPPServer.getInstance().getServerInfo().getXMPPDomain();
    }

    public JID getAddress() {
        // TODO Cache this JID for performance?
        return new JID(null, getServiceDomain(), null);
    }

    public Collection<String> getUsersAllowedToCreate() {
        return allowedToCreate;
    }

    public Collection<String> getSysadmins() {
        return sysadmins;
    }

    public void addSysadmin(String userJID) {
        sysadmins.add(userJID.trim().toLowerCase());
        // Update the config.
        String[] jids = new String[sysadmins.size()];
        jids = sysadmins.toArray(jids);
        JiveGlobals.setProperty("xmpp.pubsub.sysadmin.jid", fromArray(jids));
    }

    public void removeSysadmin(String userJID) {
        sysadmins.remove(userJID.trim().toLowerCase());
        // Update the config.
        String[] jids = new String[sysadmins.size()];
        jids = sysadmins.toArray(jids);
        JiveGlobals.setProperty("xmpp.pubsub.sysadmin.jid", fromArray(jids));
    }

    public boolean isNodeCreationRestricted() {
        return nodeCreationRestricted;
    }

    public boolean isMultipleSubscriptionsEnabled() {
        return multipleSubscriptionsEnabled;
    }

    public void setNodeCreationRestricted(boolean nodeCreationRestricted) {
        this.nodeCreationRestricted = nodeCreationRestricted;
        JiveGlobals.setProperty("xmpp.pubsub.create.anyone", Boolean.toString(nodeCreationRestricted));
    }

    public void addUserAllowedToCreate(String userJID) {
        // Update the list of allowed JIDs to create nodes.
        allowedToCreate.add(userJID.trim().toLowerCase());
        // Update the config.
        String[] jids = new String[allowedToCreate.size()];
        jids = allowedToCreate.toArray(jids);
        JiveGlobals.setProperty("xmpp.pubsub.create.jid", fromArray(jids));
    }

    public void removeUserAllowedToCreate(String userJID) {
        // Update the list of allowed JIDs to create nodes.
        allowedToCreate.remove(userJID.trim().toLowerCase());
        // Update the config.
        String[] jids = new String[allowedToCreate.size()];
        jids = allowedToCreate.toArray(jids);
        JiveGlobals.setProperty("xmpp.pubsub.create.jid", fromArray(jids));
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);

        // Listen to property events so that the template is always up to date
        PropertyEventDispatcher.addListener(this);

        serviceEnabled = JiveGlobals.getBooleanProperty("xmpp.pubsub.enabled", true);
        serviceName = JiveGlobals.getProperty("xmpp.pubsub.service");
        if (serviceName == null) {
            serviceName = "pubsub";
        }
        // Load the list of JIDs that are sysadmins of the PubSub service
        String property = JiveGlobals.getProperty("xmpp.pubsub.sysadmin.jid");
        String[] jids;
        if (property != null) {
            jids = property.split(",");
            for (String jid : jids) {
                sysadmins.add(jid.trim().toLowerCase());
            }
        }
        nodeCreationRestricted = JiveGlobals.getBooleanProperty("xmpp.pubsub.create.anyone", false);
        // Load the list of JIDs that are allowed to create nodes
        property = JiveGlobals.getProperty("xmpp.pubsub.create.jid");
        if (property != null) {
            jids = property.split(",");
            for (String jid : jids) {
                allowedToCreate.add(jid.trim().toLowerCase());
            }
        }

        multipleSubscriptionsEnabled = JiveGlobals.getBooleanProperty("xmpp.pubsub.multiple-subscriptions", true);

        routingTable = server.getRoutingTable();
        router = server.getPacketRouter();

        engine = new PubSubEngine(server.getPacketRouter());

        // Load default configuration for leaf nodes
        leafDefaultConfiguration = PubSubPersistenceManager.loadDefaultConfiguration(this, true);
        if (leafDefaultConfiguration == null) {
            // Create and save default configuration for leaf nodes;
            leafDefaultConfiguration = new DefaultNodeConfiguration(true);
            leafDefaultConfiguration.setAccessModel(AccessModel.open);
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
        collectionDefaultConfiguration =
                PubSubPersistenceManager.loadDefaultConfiguration(this, false);
        if (collectionDefaultConfiguration == null ) {
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
            collectionDefaultConfiguration
                    .setAssociationPolicy(CollectionNode.LeafNodeAssociationPolicy.all);
            collectionDefaultConfiguration.setMaxLeafNodes(-1);
            PubSubPersistenceManager
                    .createDefaultConfiguration(this, collectionDefaultConfiguration);
        }

        // Load nodes to memory
        PubSubPersistenceManager.loadNodes(this);
        // Ensure that we have a root collection node
        String rootNodeID = JiveGlobals.getProperty("xmpp.pubsub.root.nodeID", "");
        if (nodes.isEmpty()) {
            // Create root collection node
            String creator = JiveGlobals.getProperty("xmpp.pubsub.root.creator");
//            JID creatorJID = creator != null ? new JID(creator) : server.getAdmins().iterator().next();
            JID creatorJID = creator != null ? new JID(creator) : new JID(server.getServerInfo().getXMPPDomain());
            rootCollectionNode = new CollectionNode(this, null, rootNodeID, creatorJID);
            // Add the creator as the node owner
            rootCollectionNode.addOwner(creatorJID);
            // Save new root node
            rootCollectionNode.saveToDB();
        }
        else {
            rootCollectionNode = (CollectionNode) getNode(rootNodeID);
        }
        // Listen to cluster events
        ClusterManager.addListener(this);
    }

    public void start() {
        // Check that the service is enabled
        if (!isServiceEnabled()) {
            return;
        }
        super.start();
        // Add the route to this service
        routingTable.addComponentRoute(getAddress(), this);
        // Start the pubsub engine
        engine.start(this);
        ArrayList<String> params = new ArrayList<String>();
        params.clear();
        params.add(getServiceDomain());
        Log.info(LocaleUtils.getLocalizedString("startup.starting.pubsub", params));
    }

    public void stop() {
        super.stop();
        // Remove the route to this service
        routingTable.removeComponentRoute(getAddress());
        // Stop the pubsub engine. This will gives us the chance to
        // save queued items to the database.
        engine.shutdown(this);
    }

    private void enableService(boolean enabled) {
        if (serviceEnabled == enabled) {
            // Do nothing if the service status has not changed
            return;
        }
        XMPPServer server = XMPPServer.getInstance();
        if (!enabled) {
            // Disable disco information
            server.getIQDiscoItemsHandler().removeServerItemsProvider(this);
            // Stop the service/module
            stop();
        }
        serviceEnabled = enabled;
        if (enabled) {
            // Start the service/module
            start();
            // Enable disco information
            server.getIQDiscoItemsHandler().addServerItemsProvider(this);
        }
    }

    public void setServiceEnabled(boolean enabled) {
        // Enable/disable the service
        enableService(enabled);
        // Store the new setting
        JiveGlobals.setProperty("xmpp.pubsub.enabled", Boolean.toString(enabled));
    }

    /**
     * Returns true if the service is available. Use {@link #setServiceEnabled(boolean)} to
     * enable or disable the service.
     *
     * @return true if the MUC service is available.
     */
    public boolean isServiceEnabled() {
        return serviceEnabled;
    }

    public void joinedCluster() {
        // Disable the service until we know that we are the senior cluster member
        enableService(false);
    }

    public void joinedCluster(byte[] nodeID) {
        // Do nothing
    }

    public void leftCluster() {
        // Offer the service when not running in a cluster
        enableService(true);
    }

    public void leftCluster(byte[] nodeID) {
        // Do nothing
    }

    public void markedAsSeniorClusterMember() {
        // Offer the service since we are the senior cluster member
        enableService(true);
    }

    public Iterator<DiscoServerItem> getItems() {
        // Check if the service is disabled. Info is not available when disabled.
        if (!isServiceEnabled()) {
            return null;
        }
        ArrayList<DiscoServerItem> items = new ArrayList<DiscoServerItem>();
		final DiscoServerItem item = new DiscoServerItem(new JID(
			getServiceDomain()), "Publish-Subscribe service", null, null, this,
			this);
		items.add(item);
		return items.iterator();
    }

    public Iterator<Element> getIdentities(String name, String node, JID senderJID) {
        ArrayList<Element> identities = new ArrayList<Element>();
        if (name == null && node == null) {
            // Answer the identity of the PubSub service
            Element identity = DocumentHelper.createElement("identity");
            identity.addAttribute("category", "pubsub");
            identity.addAttribute("name", "Publish-Subscribe service");
            identity.addAttribute("type", "service");

            identities.add(identity);
        }
        else if (name == null) {
            // Answer the identity of a given node
            Node pubNode = getNode(node);
            if (canDiscoverNode(pubNode)) {
                Element identity = DocumentHelper.createElement("identity");
                identity.addAttribute("category", "pubsub");
                identity.addAttribute("type", pubNode.isCollectionNode() ? "collection" : "leaf");

                identities.add(identity);
            }
        }
        return identities.iterator();
    }

    public Iterator<String> getFeatures(String name, String node, JID senderJID) {
        ArrayList<String> features = new ArrayList<String>();
        if (name == null && node == null) {
            // Answer the features of the PubSub service
            features.add("http://jabber.org/protocol/pubsub");
            if (isCollectionNodesSupported()) {
                // Collection nodes are supported
                features.add("http://jabber.org/protocol/pubsub#collections");
            }
            // Configuration of node options is supported
            features.add("http://jabber.org/protocol/pubsub#config-node");
            // Simultaneous creation and configuration of nodes is supported
            features.add("http://jabber.org/protocol/pubsub#create-and-configure");
            // Creation of nodes is supported
            features.add("http://jabber.org/protocol/pubsub#create-nodes");
            // Deletion of nodes is supported
            features.add("http://jabber.org/protocol/pubsub#delete-nodes");
            // Retrieval of pending subscription approvals is supported
            features.add("http://jabber.org/protocol/pubsub#get-pending");
            if (isInstantNodeSupported()) {
                // Creation of instant nodes is supported
                features.add("http://jabber.org/protocol/pubsub#instant-nodes");
            }
            // Publishers may specify item identifiers
            features.add("http://jabber.org/protocol/pubsub#item-ids");
            // TODO Time-based subscriptions are supported (clean up thread missing, rest is supported)
            //features.add("http://jabber.org/protocol/pubsub#leased-subscription");
            // Node meta-data is supported
            features.add("http://jabber.org/protocol/pubsub#meta-data");
            // Node owners may modify affiliations
            features.add("http://jabber.org/protocol/pubsub#modify-affiliations");
            // Node owners may manage subscriptions.
            features.add("http://jabber.org/protocol/pubsub#manage-subscriptions");
            // A single entity may subscribe to a node multiple times
            features.add("http://jabber.org/protocol/pubsub#multi-subscribe");
            // The outcast affiliation is supported
            features.add("http://jabber.org/protocol/pubsub#outcast-affiliation");
            // Persistent items are supported
            features.add("http://jabber.org/protocol/pubsub#persistent-items");
            // Presence-based delivery of event notifications is supported
            features.add("http://jabber.org/protocol/pubsub#presence-notifications");
            // Publishing items is supported (note: not valid for collection nodes)
            features.add("http://jabber.org/protocol/pubsub#publish");
            // The publisher affiliation is supported
            features.add("http://jabber.org/protocol/pubsub#publisher-affiliation");
            // Purging of nodes is supported
            features.add("http://jabber.org/protocol/pubsub#purge-nodes");
            // Item retraction is supported
            features.add("http://jabber.org/protocol/pubsub#retract-items");
            // Retrieval of current affiliations is supported
            features.add("http://jabber.org/protocol/pubsub#retrieve-affiliations");
            // Retrieval of default node configuration is supported.
            features.add("http://jabber.org/protocol/pubsub#retrieve-default");
            // Item retrieval is supported
            features.add("http://jabber.org/protocol/pubsub#retrieve-items");
            // Retrieval of current subscriptions is supported.
            features.add("http://jabber.org/protocol/pubsub#retrieve-subscriptions");
            // Subscribing and unsubscribing are supported
            features.add("http://jabber.org/protocol/pubsub#subscribe");
            // Configuration of subscription options is supported
            features.add("http://jabber.org/protocol/pubsub#subscription-options");
            // Default access model for nodes created on the service
            String modelName = getDefaultNodeConfiguration(true).getAccessModel().getName();
            features.add("http://jabber.org/protocol/pubsub#default_access_model_" + modelName);

        }
        else if (name == null) {
            // Answer the features of a given node
            Node pubNode = getNode(node);
            if (canDiscoverNode(pubNode)) {
                // Answer the features of the PubSub service
                features.add("http://jabber.org/protocol/pubsub");
            }
        }
        return features.iterator();
    }

    public XDataFormImpl getExtendedInfo(String name, String node, JID senderJID) {
        if (name == null && node != null) {
            // Answer the extended info of a given node
            Node pubNode = getNode(node);
            if (canDiscoverNode(pubNode)) {
                // Get the metadata data form
                org.xmpp.forms.DataForm metadataForm = pubNode.getMetadataForm();

                // Convert Whack data form into old data form format (will go away someday)
                XDataFormImpl dataForm = new XDataFormImpl(DataForm.TYPE_RESULT);
                for (org.xmpp.forms.FormField formField : metadataForm.getFields()) {
                    XFormFieldImpl field = new XFormFieldImpl(formField.getVariable());
                    field.setLabel(formField.getLabel());
                    for (String value : formField.getValues()) {
                        field.addValue(value);
                    }
                    dataForm.addField(field);
                }

                return dataForm;
            }
        }
        return null;
    }

    public boolean hasInfo(String name, String node, JID senderJID) {
        // Check if the service is disabled. Info is not available when disabled.
        if (!isServiceEnabled()) {
            return false;
        }
        if (name == null && node == null) {
            // We always have info about the Pubsub service
            return true;
        }
        else if (name == null) {
            // We only have info if the node exists
            return hasNode(node);
        }
        return false;
    }

    public Iterator<DiscoItem> getItems(String name, String node, JID senderJID) {
        // Check if the service is disabled. Info is not available when disabled.
        if (!isServiceEnabled()) {
            return null;
        }
        List<DiscoItem> answer = new ArrayList<DiscoItem>();
        String serviceDomain = getServiceDomain();
        if (name == null && node == null) {
            // Answer all first level nodes
            for (Node pubNode : rootCollectionNode.getNodes()) {
                if (canDiscoverNode(pubNode)) {
                	final DiscoItem item = new DiscoItem(
						new JID(serviceDomain), pubNode.getName(),
						pubNode.getNodeID(), null);
                    answer.add(item);
                }
            }
        }
        else if (name == null) {
            Node pubNode = getNode(node);
            if (pubNode != null && canDiscoverNode(pubNode)) {
                if (pubNode.isCollectionNode()) {
                    // Answer all nested nodes as items
                    for (Node nestedNode : pubNode.getNodes()) {
                        if (canDiscoverNode(nestedNode)) {
                        	final DiscoItem item = new DiscoItem(new JID(serviceDomain), nestedNode.getName(),
								nestedNode.getNodeID(), null);
                            answer.add(item);
                        }
                    }
                }
                else {
                    // This is a leaf node so answer the published items which exist on the service
                    for (PublishedItem publishedItem : pubNode.getPublishedItems()) {
                        answer.add(new DiscoItem(new JID(serviceDomain), publishedItem.getID(), null, null));
                    }
                }
            }
            else {
                // Answer null to indicate that specified item was not found
                return null;
            }
        }
        return answer.iterator();
    }

    public void broadcast(Node node, Message message, Collection<JID> jids) {
        // TODO Possibly use a thread pool for sending packets (based on the jids size)
        message.setFrom(getAddress());
        for (JID jid : jids) {
            message.setTo(jid);
            message.setID(
                    node.getNodeID() + "__" + jid.toBareJID() + "__" + StringUtils.randomString(5));
            router.route(message);
        }
    }

    public void send(Packet packet) {
        router.route(packet);
    }

    public void sendNotification(Node node, Message message, JID jid) {
        message.setFrom(getAddress());
        message.setTo(jid);
        message.setID(
                node.getNodeID() + "__" + jid.toBareJID() + "__" + StringUtils.randomString(5));
        router.route(message);
    }

    public Node getNode(String nodeID) {
        return nodes.get(nodeID);
    }

    public Collection<Node> getNodes() {
        return nodes.values();
    }

    private boolean hasNode(String nodeID) {
        return getNode(nodeID) != null;
    }

    public void addNode(Node node) {
        nodes.put(node.getNodeID(), node);
    }

    public void removeNode(String nodeID) {
        nodes.remove(nodeID);
    }

    private boolean canDiscoverNode(Node pubNode) {
        return true;
    }

    /**
     * Converts an array to a comma-delimitted String.
     *
     * @param array the array.
     * @return a comma delimtted String of the array values.
     */
    private static String fromArray(String [] array) {
        StringBuilder buf = new StringBuilder();
        for (int i=0; i<array.length; i++) {
            buf.append(array[i]);
            if (i != array.length-1) {
                buf.append(",");
            }
        }
        return buf.toString();
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

    public void propertySet(String property, Map<String, Object> params) {
        if (property.equals("xmpp.pubsub.enabled")) {
            boolean enabled = Boolean.parseBoolean((String)params.get("value"));
            // Enable/disable the service
            enableService(enabled);
        }
    }

    public void propertyDeleted(String property, Map<String, Object> params) {
        if (property.equals("xmpp.pubsub.enabled")) {
            // Enable/disable the service
            enableService(true);
        }
    }

    public void xmlPropertySet(String property, Map<String, Object> params) {
        // Do nothing
    }

    public void xmlPropertyDeleted(String property, Map<String, Object> params) {
        // Do nothing
    }
}
