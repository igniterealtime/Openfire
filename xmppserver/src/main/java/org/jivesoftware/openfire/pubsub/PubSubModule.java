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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.RoutableChannelHandler;
import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.commands.AdHocCommandManager;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.disco.DiscoInfoProvider;
import org.jivesoftware.openfire.disco.DiscoItem;
import org.jivesoftware.openfire.disco.DiscoItemsProvider;
import org.jivesoftware.openfire.disco.DiscoServerItem;
import org.jivesoftware.openfire.disco.IQDiscoInfoHandler;
import org.jivesoftware.openfire.disco.IQDiscoItemsHandler;
import org.jivesoftware.openfire.disco.ServerItemsProvider;
import org.jivesoftware.openfire.entitycaps.EntityCapabilities;
import org.jivesoftware.openfire.entitycaps.EntityCapabilitiesListener;
import org.jivesoftware.openfire.pubsub.models.AccessModel;
import org.jivesoftware.openfire.pubsub.models.PublisherModel;
import org.jivesoftware.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.forms.DataForm;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.Presence;

import javax.annotation.Nonnull;

/**
 * Module that implements XEP-60: Publish-Subscribe. By default node collections and
 * instant nodes are supported.
 *
 * @author Matt Tucker
 */
public class PubSubModule extends BasicModule implements ServerItemsProvider, DiscoInfoProvider,
        DiscoItemsProvider, RoutableChannelHandler, PubSubService, PropertyEventListener, EntityCapabilitiesListener {

    private static final Logger Log = LoggerFactory.getLogger(PubSubModule.class);

    /**
     * Returns the permission policy for creating nodes. A false value means that not anyone can
     * create a node, only the JIDs listed in <code>xmpp.pubsub.create.jid</code> are allowed to create
     * nodes.
     */
    public static SystemProperty<Boolean> PUBSUB_CREATE_ANYONE = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.pubsub.create.anyone")
        .setDynamic(true)
        .setDefaultValue(true)
        .build();

    /**
     * Bare jids of users that are allowed to create nodes. An empty list means that anyone can
     * create nodes.
     */
    public static SystemProperty<Set<JID>> PUBSUB_ALLOWED_TO_CREATE = SystemProperty.Builder.ofType(Set.class)
        .setKey("xmpp.pubsub.create.jid")
        .setDynamic(false)
        .setDefaultValue( new HashSet<>() )
        .buildSet(JID.class);

    /**
     * Bare jids of users that are system administrators of the PubSub service. A sysadmin
     * has the same permissions as a node owner.
     */
    public static SystemProperty<Set<JID>> PUBSUB_SYSADMINS = SystemProperty.Builder.ofType(Set.class)
        .setKey("xmpp.pubsub.sysadmin.jid")
        .setDynamic(false)
        .setDefaultValue( new HashSet<>() )
        .buildSet(JID.class);

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
    private final Map<Node.UniqueIdentifier, Node> nodes = new ConcurrentHashMap<>();
    
    /**
     * Keep a registry of the presence's show value of users that subscribed to a node of
     * the pubsub service and for which the node only delivers notifications for online users
     * or node subscriptions deliver events based on the user presence show value. Offline
     * users will not have an entry in the map. Note: Key-> bare JID and Value-> Map whose key
     * is full JID of connected resource and value is show value of the last received presence.
     */
    private final Map<JID, Map<JID, String>> barePresences = new ConcurrentHashMap<>();
    
    /**
     * Manager that keeps the list of ad-hoc commands and processing command requests.
     */
    private final AdHocCommandManager manager;
    
    /**
     * Flag that indicates if a user may have more than one subscription with the node. When multiple
     * subscriptions is enabled each subscription request, event notification and unsubscription request
     * should include a subid attribute.
     */
    private boolean multipleSubscriptionsEnabled = true;

    /**
     * The packet router for the server.
     */
    private PacketRouter router = null;

    private RoutingTable routingTable = null;

    /**
     * The disco info handler for this module
     */
    private IQDiscoInfoHandler iqDiscoInfoHandler = null;
   /**
    * The disco items handler for this module
    */ 
    private IQDiscoItemsHandler iqDiscoItemsHandler = null;

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

    /**
     * Manager of entities that interact with persistent storage of data (eg: the database).
     */
    private PubSubPersistenceProviderManager persistenceProviderManager;

    public PubSubModule() {
        super("Publish Subscribe Service");

        // Initialize the ad-hoc commands manager to use for this pubsub service
        manager = new AdHocCommandManager();
        manager.addCommand(new PendingSubscriptionsCommand(this));
    }

    @Override
    public void process(Packet packet) {
        try {
            // Check if the packet is a disco request or a packet with namespace iq:register
            if (packet instanceof IQ) {
                if (engine.process(this, (IQ) packet) == null) {
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

    private void sendServiceUnavailablePacket(IQ iq) {
        engine.sendErrorPacket(iq, PacketError.Condition.service_unavailable, null);
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
            if (iqDiscoInfoHandler != null) {
                IQ reply = iqDiscoInfoHandler.handleIQ(iq);
                router.route(reply);
            } else {
                sendServiceUnavailablePacket(iq);
                return;
            }
        }
        else if ("http://jabber.org/protocol/disco#items".equals(namespace)) {
            if (iqDiscoItemsHandler != null) {
                IQ reply = iqDiscoItemsHandler.handleIQ(iq);
                router.route(reply);
            } else {
                sendServiceUnavailablePacket(iq);
                return;
            }
            
        }
        else {
            // Unknown namespace requested so return error to sender
            sendServiceUnavailablePacket(iq);
        }
    }

    @Override
    public String getServiceID() {
        return "pubsub";
    }

    @Override
    public boolean canCreateNode(JID creator) {
        // Node creation is always allowed for sysadmin
        return !isNodeCreationRestricted() || isServiceAdmin(creator);
    }

    @Override
    public boolean isServiceAdmin(JID user) {
        return PUBSUB_SYSADMINS.getValue().contains(user.asBareJID())
            || PUBSUB_ALLOWED_TO_CREATE.getValue().contains(user.asBareJID())
            || InternalComponentManager.getInstance().hasComponent(user);
    }

    @Override
    public boolean isInstantNodeSupported() {
        return true;
    }

    @Override
    public boolean isCollectionNodesSupported() {
        return true;
    }

    @Override
    public CollectionNode getRootCollectionNode() {
        return rootCollectionNode;
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
    public void presenceSubscriptionNotRequired(Node node, JID user) {
        PubSubEngine.presenceSubscriptionNotRequired(this, node, user);
    }

    @Override
    public void presenceSubscriptionRequired(Node node, JID user) {
        PubSubEngine.presenceSubscriptionRequired(this, node, user);
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getServiceDomain() {
        return serviceName + "." + XMPPServer.getInstance().getServerInfo().getXMPPDomain();
    }

    @Override
    public JID getAddress() {
        // TODO Cache this JID for performance?
        return new JID(null, getServiceDomain(), null);
    }

    public Collection<JID> getUsersAllowedToCreate() {
        return PUBSUB_ALLOWED_TO_CREATE.getValue();
    }

    public Collection<JID> getSysadmins() {
        return PUBSUB_SYSADMINS.getValue();
    }

    public void setSysadmins(Collection<JID> userJIDs) {
        PUBSUB_SYSADMINS.setValue( userJIDs.stream().map(JID::asBareJID).collect(Collectors.toSet()) );
    }

    public void addSysadmin(JID userJID) {
        final Set<JID> existing = PUBSUB_SYSADMINS.getValue();
        existing.add( userJID.asBareJID() );
        PUBSUB_SYSADMINS.setValue( existing );
    }

    public void removeSysadmin(JID userJID) {
        final Set<JID> existing = PUBSUB_SYSADMINS.getValue();
        existing.remove( userJID ); // just in case...
        existing.remove( userJID.asBareJID() );
        PUBSUB_SYSADMINS.setValue( existing );
    }

    public boolean isNodeCreationRestricted() {
        return !PUBSUB_CREATE_ANYONE.getValue();
    }

    @Override
    public boolean isMultipleSubscriptionsEnabled() {
        return multipleSubscriptionsEnabled;
    }

    public void setNodeCreationRestricted(boolean nodeCreationRestricted) {
        PUBSUB_CREATE_ANYONE.setValue(!nodeCreationRestricted);
    }

    public void setUserAllowedToCreate(Collection<JID> userJIDs) {
        PUBSUB_ALLOWED_TO_CREATE.setValue( userJIDs.stream().map(JID::asBareJID).collect(Collectors.toSet()) );
    }

    public void addUserAllowedToCreate(JID userJID) {
        // Update the list of allowed JIDs to create nodes.
        final Set<JID> existing = PUBSUB_ALLOWED_TO_CREATE.getValue();
        existing.add( userJID.asBareJID() );
        PUBSUB_ALLOWED_TO_CREATE.setValue( existing );
    }

    public void removeUserAllowedToCreate(JID userJID) {
        // Update the list of allowed JIDs to create nodes.
        final Set<JID> existing = PUBSUB_ALLOWED_TO_CREATE.getValue();
        existing.remove( userJID ); // just in case...
        existing.remove( userJID.asBareJID() );
        PUBSUB_ALLOWED_TO_CREATE.setValue( existing );
    }

    @Override
    public void initialize(XMPPServer server) {
        super.initialize(server);
        
        JiveGlobals.migrateProperty("xmpp.pubsub.enabled");
        JiveGlobals.migrateProperty("xmpp.pubsub.service");
        JiveGlobals.migrateProperty("xmpp.pubsub.root.nodeID");
        JiveGlobals.migrateProperty("xmpp.pubsub.root.creator");
        JiveGlobals.migrateProperty("xmpp.pubsub.multiple-subscriptions");

        // Initializing persistence
        persistenceProviderManager = new PubSubPersistenceProviderManager();
        persistenceProviderManager.initialize();

        // Listen to property events so that the template is always up to date
        PropertyEventDispatcher.addListener(this);

        setIQDiscoItemsHandler(XMPPServer.getInstance().getIQDiscoItemsHandler());
        setIQDiscoInfoHandler(XMPPServer.getInstance().getIQDiscoInfoHandler());
        serviceEnabled = JiveGlobals.getBooleanProperty("xmpp.pubsub.enabled", true);
        serviceName = JiveGlobals.getProperty("xmpp.pubsub.service");
        if (serviceName == null) {
            serviceName = "pubsub";
        }
        Log.debug( "Initializing using service name: '{}'", serviceName );

        multipleSubscriptionsEnabled = JiveGlobals.getBooleanProperty("xmpp.pubsub.multiple-subscriptions", true);

        routingTable = server.getRoutingTable();
        router = server.getPacketRouter();

        engine = new PubSubEngine(router);

        // Load default configuration for leaf nodes
        leafDefaultConfiguration = getPersistenceProvider().loadDefaultConfiguration(this.getUniqueIdentifier(), true);
        if (leafDefaultConfiguration == null) {
            Log.debug( "Create and save default configuration for leaf nodes" );
            leafDefaultConfiguration = new DefaultNodeConfiguration(true);
            leafDefaultConfiguration.setAccessModel(AccessModel.open);
            leafDefaultConfiguration.setPublisherModel(PublisherModel.publishers);
            leafDefaultConfiguration.setDeliverPayloads(true);
            leafDefaultConfiguration.setLanguage("English");
            leafDefaultConfiguration.setMaxPayloadSize(10 * 1024 * 1024); // Probably should not be larger than the max read buffer for stanzas!);
            leafDefaultConfiguration.setNotifyConfigChanges(true);
            leafDefaultConfiguration.setNotifyDelete(true);
            leafDefaultConfiguration.setNotifyRetract(true);
            leafDefaultConfiguration.setPersistPublishedItems(false);
            leafDefaultConfiguration.setMaxPublishedItems(1);
            leafDefaultConfiguration.setPresenceBasedDelivery(false);
            leafDefaultConfiguration.setSendItemSubscribe(true);
            leafDefaultConfiguration.setSubscriptionEnabled(true);
            leafDefaultConfiguration.setReplyPolicy(null);
            getPersistenceProvider().createDefaultConfiguration(this.getUniqueIdentifier(), leafDefaultConfiguration);
        }
        // Load default configuration for collection nodes
        collectionDefaultConfiguration = getPersistenceProvider().loadDefaultConfiguration(this.getUniqueIdentifier(), false);
        if (collectionDefaultConfiguration == null ) {
            Log.debug( "Create and save default configuration for collection nodes" );
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
            getPersistenceProvider().createDefaultConfiguration(this.getUniqueIdentifier(), collectionDefaultConfiguration);
        }

        // Load nodes to memory
        getPersistenceProvider().loadNodes(this);

        // Ensure that we have a root collection node
        String rootNodeID = JiveGlobals.getProperty("xmpp.pubsub.root.nodeID", "");
        if (nodes.isEmpty()) {
            Log.debug( "Create a new root collection node" );
            // Create root collection node
            String creator = JiveGlobals.getProperty("xmpp.pubsub.root.creator");
//            JID creatorJID = creator != null ? new JID(creator) : server.getAdmins().iterator().next();
            JID creatorJID = creator != null ? new JID(creator) : new JID(server.getServerInfo().getXMPPDomain());
            rootCollectionNode = new CollectionNode(this.getUniqueIdentifier(), null, rootNodeID, creatorJID, collectionDefaultConfiguration);
            // Add the creator as the node owner
            rootCollectionNode.addOwner(creatorJID);
            // Save new root node
            rootCollectionNode.saveToDB();
        }
        else {
            Log.debug( "Load root collection node ('{}') from database.", rootNodeID );
            rootCollectionNode = (CollectionNode) getNode(rootNodeID);
        }

        XMPPServer.getInstance().getEntityCapabilitiesManager().addListener(this);
    }

    @Override
    public void start() {
        // Check that the service is enabled
        if (!isServiceEnabled()) {
            Log.info( "Not starting service with name '{}', as pubsub is disabled by configuration.", serviceName );
            return;
        }
        Log.debug( "Starting service with name '{}'.", serviceName );
        super.start();
        // Add the route to this service
        routingTable.addComponentRoute(getAddress(), this);
        // Start the pubsub engine
        engine.start(this);
        ArrayList<String> params = new ArrayList<>();
        params.add(getServiceDomain());
        Log.info(LocaleUtils.getLocalizedString("startup.starting.pubsub", params));
    }

    @Override
    public void stop() {
        Log.debug( "Stopping service with name '{}'.", serviceName );
        super.stop();
        // Remove the route to this service
        routingTable.removeComponentRoute(getAddress());
        // Stop the pubsub engine. This will gives us the chance to
        // save queued items to the database.
        engine.shutdown(this);
    }

    @Override
    public void destroy() {
        Log.debug( "Destroying service with name '{}'.", serviceName );
        XMPPServer.getInstance().getEntityCapabilitiesManager().removeListener(this);

        super.destroy();
        if (persistenceProviderManager!= null) {
            persistenceProviderManager.shutdown();
        }
    }

    public void setIQDiscoItemsHandler(IQDiscoItemsHandler iqDiscoItemsHandler) {
        this.iqDiscoItemsHandler = iqDiscoItemsHandler;
    }
    
    public void setIQDiscoInfoHandler(IQDiscoInfoHandler iqDiscoInfoHandler) {
        this.iqDiscoInfoHandler = iqDiscoInfoHandler ;
    }

    private void enableService(boolean enabled) {
        if (serviceEnabled == enabled) {
            // Do nothing if the service status has not changed
            return;
        }
        if (!enabled) {
            // Disable disco information
            if (iqDiscoItemsHandler != null) {
                iqDiscoItemsHandler.removeServerItemsProvider(this);
            }
            // Stop the service/module
            stop();
        }
        serviceEnabled = enabled;
        if (enabled) {
            // Start the service/module
            start();
            // Enable disco information
            if (iqDiscoItemsHandler != null) {
                iqDiscoItemsHandler.addServerItemsProvider(this);
            }
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

    public void markedAsSeniorClusterMember() {
        // Offer the service since we are the senior cluster member
        // enableService(true);
    }

    @Override
    public Iterator<DiscoServerItem> getItems() {
        // Check if the service is disabled. Info is not available when disabled.
        if (!isServiceEnabled()) {
            return null;
        }
        ArrayList<DiscoServerItem> items = new ArrayList<>();
        final DiscoServerItem item = new DiscoServerItem(new JID(
            getServiceDomain()), "Publish-Subscribe service", null, null, this,
            this);
        items.add(item);
        return items.iterator();
    }

    @Override
    public Iterator<Element> getIdentities(String name, String node, JID senderJID) {
        ArrayList<Element> identities = new ArrayList<>();
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

    @Override
    public Iterator<String> getFeatures(String name, String node, JID senderJID) {
        ArrayList<String> features = new ArrayList<>();
        if (name == null && node == null) {
            // Answer the features of the PubSub service
            features.add("http://jabber.org/protocol/pubsub");
            // Default access model for nodes created on the service
            String modelName = getDefaultNodeConfiguration(true).getAccessModel().getName();
            features.add("http://jabber.org/protocol/pubsub#access-" + modelName);
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
            if (isMultipleSubscriptionsEnabled()) {
                // A single entity may subscribe to a node multiple times
                features.add( "http://jabber.org/protocol/pubsub#multi-subscribe" );
            }
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
            // Publishing options are supported.
            features.add("http://jabber.org/protocol/pubsub#publish-options");
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

    @Override
    public DataForm getExtendedInfo(String name, String node, JID senderJID) {
        return IQDiscoInfoHandler.getFirstDataForm(this.getExtendedInfos(name, node, senderJID));
    }
    
    @Override
    public Set<DataForm> getExtendedInfos(String name, String node, JID senderJID) {
        if (name == null && node != null) {
            // Answer the extended info of a given node
            Node pubNode = getNode(node);
            Set<DataForm> dataForms = new HashSet<>();
            if (canDiscoverNode(pubNode)) {
                dataForms.add(pubNode.getMetadataForm());
                // Get the metadata data form
                return dataForms;
            }
        }
        return new HashSet<>();
    }

    @Override
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

    @Override
    public Iterator<DiscoItem> getItems(String name, String node, JID senderJID) {
        // Check if the service is disabled. Info is not available when disabled.
        if (!isServiceEnabled()) {
            return null;
        }
        List<DiscoItem> answer = new ArrayList<>();
        String serviceDomain = getServiceDomain();
        if (name == null && node == null) {
            // Answer all first level nodes
            for (Node pubNode : rootCollectionNode.getNodes()) {
                if (canDiscoverNode(pubNode)) {
                    final DiscoItem item = new DiscoItem(
                        new JID(serviceDomain), pubNode.getName(),
                        pubNode.getUniqueIdentifier().getNodeId(), null);
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
                                nestedNode.getUniqueIdentifier().getNodeId(), null);
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

    @Override
    public void broadcast(Node node, Message message, Collection<JID> jids) {
        // TODO Possibly use a thread pool for sending packets (based on the jids size)
        message.setFrom(getAddress());
        for (JID jid : jids) {
            message.setTo(jid);
            message.setID(StringUtils.randomString(8));
            router.route(message);
        }
    }

    @Override
    public void send(Packet packet) {
        router.route(packet);
    }

    @Override
    public void sendNotification(Node node, Message message, JID jid) {
        message.setFrom(getAddress());
        message.setTo(jid);
        message.setID(StringUtils.randomString(8));
        router.route(message);
    }

    @Override
    public Node getNode(Node.UniqueIdentifier nodeID) {
        return nodes.get(nodeID);
    }

    @Override
    public Collection<Node> getNodes() {
        return nodes.values();
    }

    private boolean hasNode(String nodeID) {
        return getNode(nodeID) != null;
    }

    @Override
    public void addNode(Node node) {
        nodes.put(node.getUniqueIdentifier(), node);
    }

    @Override
    public void removeNode(Node.UniqueIdentifier nodeID) {
        nodes.remove(nodeID);
    }

    private boolean canDiscoverNode(Node pubNode) {
        return true;
    }

    /**
     * Converts an array to a comma-delimited String.
     *
     * @param array the array.
     * @return a comma delimited String of the array values.
     */
    private static String fromArray(String [] array) {
        StringBuilder buf = new StringBuilder();
        for (int i=0; i<array.length; i++) {
            buf.append(array[i]);
            if (i != array.length-1) {
                buf.append(',');
            }
        }
        return buf.toString();
    }

    @Override
    public Map<JID, Map<JID, String>> getSubscriberPresences() {
        return barePresences;
    }

    @Override
    public AdHocCommandManager getManager() {
        return manager;
    }

    @Override
    public void propertySet(String property, Map<String, Object> params) {
        if (property.equals("xmpp.pubsub.enabled")) {
            boolean enabled = Boolean.parseBoolean((String)params.get("value"));
            // Enable/disable the service
            enableService(enabled);
        }
    }

    @Override
    public void propertyDeleted(String property, Map<String, Object> params) {
        if (property.equals("xmpp.pubsub.enabled")) {
            // Enable/disable the service
            enableService(true);
        }
    }

    @Override
    public void xmlPropertySet(String property, Map<String, Object> params) {
        // Do nothing
    }

    @Override
    public void xmlPropertyDeleted(String property, Map<String, Object> params) {
        // Do nothing
    }

    /**
     * Returns a data access object that can be used to interact with backend storage that holds pubsub data.
     *
     * This method returns null only when the this instance of PubSubModule has not been initialized yet, or has been
     * destroyed.
     *
     * @return The configured and initialized persistence provider
     */
    public PubSubPersistenceProvider getPersistenceProvider()
    {
        return persistenceProviderManager.getProvider();
    }

    @Override
    public void entityCapabilitiesChanged( @Nonnull final JID entity,
                                           @Nonnull final EntityCapabilities updatedEntityCapabilities,
                                           @Nonnull final Set<String> featuresAdded,
                                           @Nonnull final Set<String> featuresRemoved,
                                           @Nonnull final Set<String> identitiesAdded,
                                           @Nonnull final Set<String> identitiesRemoved )
    {
        // Look for new +notify features. Those are the nodes that the entity is now interested in.
        final Set<String> nodeIDs = featuresAdded.stream()
            .filter(feature -> feature.endsWith("+notify"))
            .map(feature -> feature.substring(0, feature.length() - "+notify".length()))
            .collect(Collectors.toSet());

        if ( !nodeIDs.isEmpty() )
        {
            Log.debug( "Entity '{}' expressed new interest in receiving notifications for nodes '{}'", entity, String.join( ", ", nodeIDs ) );
            nodes.values().stream()
                .filter( Node::isSendItemSubscribe )
                .filter( node -> nodeIDs.contains( node.getUniqueIdentifier().getNodeId()) )
                .forEach( node -> {
                    final NodeSubscription subscription = node.getSubscription(entity);
                    if (subscription != null && subscription.isActive()) {
                        PublishedItem lastItem = node.getLastPublishedItem();
                        if (lastItem != null) {
                            subscription.sendLastPublishedItem(lastItem);
                        }
                    }
                });
        }
    }
}
