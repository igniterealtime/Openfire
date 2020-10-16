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

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.disco.*;
import org.jivesoftware.openfire.event.UserEventDispatcher;
import org.jivesoftware.openfire.event.UserEventListener;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.openfire.pubsub.*;
import org.jivesoftware.openfire.pubsub.models.AccessModel;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.roster.RosterEventDispatcher;
import org.jivesoftware.openfire.roster.RosterEventListener;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.roster.RosterManager;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.user.*;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.Presence;

/**
 * <p>
 * An {@link IQHandler} used to implement XEP-0163: "Personal Eventing via Pubsub"
 * Version 1.0
 * </p>
 *
 * <p>
 * For each user on the server there is an associated {@link PEPService} interacting
 * with a single {@link PubSubEngine} for managing the user's PEP nodes.
 * </p>
 *
 * <p>
 * An IQHandler can only handle one namespace in its IQHandlerInfo. However, PEP
 * related packets are seen having a variety of different namespaces. Thus,
 * classes like {@link IQPEPOwnerHandler} are used to forward packets having these other
 * namespaces to {@link IQPEPHandler#handleIQ(IQ)}.
 *
 * <p>
 * This handler is used for the following namespaces:</p>
 * <ul>
 * <li><i>http://jabber.org/protocol/pubsub</i></li>
 * <li><i>http://jabber.org/protocol/pubsub#owner</i></li>
 * </ul>
 *
 * @author Armando Jagucki
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class IQPEPHandler extends IQHandler implements ServerIdentitiesProvider, ServerFeaturesProvider,
        UserIdentitiesProvider, UserFeaturesProvider, UserItemsProvider, PresenceEventListener,
        RosterEventListener, UserEventListener, DiscoInfoProvider {

    private static final Logger Log = LoggerFactory.getLogger(IQPEPHandler.class);

    /**
     * Metadata that relates to the IQ processing capabilities of this specific {@link IQHandler}.
     */
    private final IQHandlerInfo info;

    private PEPServiceManager pepServiceManager = null;

    /**
     * The managed thread pool that will do most of the processing. The amount
     * of worker threads in this pool should be kept low to avoid resource
     * contention.
     */
    // There's room for future improvement here. If anywhere in the future,
    // Openfire allows implementations to use dedicated resource pools, we can
    // significantly increase the number of worker threads in this executor. The
    // bottleneck for this particular executor is the database pool. During
    // startup, PEP queries the database a lot, which causes all of the
    // connections in the generic database pool to be used up by this PEP
    // implementation. This can cause problems in other parts of Openfire that
    // depend on database access (ideally, these should get dedicated resource
    // pools too).
    private ExecutorService executor = null;

    /**
     * Constructs a new {@link IQPEPHandler} instance.
     */
    public IQPEPHandler() {
        super("Personal Eventing Handler");
        info = new IQHandlerInfo("pubsub", "http://jabber.org/protocol/pubsub");
    }

    /* 
     * (non-Javadoc)
     * @see org.jivesoftware.openfire.handler.IQHandler#initialize(org.jivesoftware.openfire.XMPPServer)
     */
    @Override
    public void initialize(XMPPServer server) {
        super.initialize(server);

        pepServiceManager = new PEPServiceManager();
        pepServiceManager.initialize();
    }

    public PEPServiceManager getServiceManager()
    {
        return pepServiceManager;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.jivesoftware.openfire.container.BasicModule#destroy()
     */
    @Override
    public void destroy() {
        if ( pepServiceManager != null ) {
            pepServiceManager.destroy();
            pepServiceManager = null;
        }
        super.destroy();
    }

    /*
     * (non-Javadoc)
     *
     * @see org.jivesoftware.openfire.container.BasicModule#start()
     */
    @Override
    public void start() {
        super.start();

        // start the service manager
        pepServiceManager.start();

        // start a new executor service
        startExecutor();
        
        // Listen to presence events to manage PEP auto-subscriptions.
        PresenceEventDispatcher.addListener(this);
        // Listen to roster events for PEP subscription cancelling on contact deletion.
        RosterEventDispatcher.addListener(this);
        // Listen to user events in order to destroy a PEP service when a user is deleted.
        UserEventDispatcher.addListener(this);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.jivesoftware.openfire.container.BasicModule#stop()
     */
    @Override
    public void stop() {
        super.stop();
        
        // Remove listeners
        PresenceEventDispatcher.removeListener(this);
        RosterEventDispatcher.removeListener(this);
        UserEventDispatcher.removeListener(this);        
        
        // stop the executor service
        stopExecutor();
        
        // stop the pepservicemananger
        pepServiceManager.stop();
    }
    
    /**
     * Starts a new thread pool, unless an existing one is still running.
     */
    private void startExecutor() {
        if (executor == null || executor.isShutdown()) {
            // keep the amount of workers low! See comment that goes with the
            // field named 'executor'.
            Log.debug("Starting executor service...");
            executor = Executors.newScheduledThreadPool(2);
        }
    }
    
    /**
     * Shuts down the executor by dropping all tasks from the queue. This method
     * will allow the executor to finish operations on running tasks for a
     * period of two seconds. After that, tasks are forcefully stopped.
     * <p>
     * The order in which the various shutdown routines of the executor are
     * called, is:
     * <ol>
     * <li>{@link ExecutorService#shutdown()}</li>
     * <li>{@link ExecutorService#awaitTermination(long, TimeUnit)} (two
     * seconds)</li>
     * <li>{@link ExecutorService#shutdownNow()}</li>
     * </ol>
     */
    private void stopExecutor() {
        Log.debug("Stopping executor service...");
        /*
         * This method gets called as part of the Component#shutdown() routine.
         * If that method gets called, the component has already been removed
         * from the routing tables. We don't need to worry about new packets to
         * arrive - there won't be any.
         */
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                Log.debug("Forcing a shutdown for the executor service (after a two-second timeout has elapsed...");
                executor.shutdownNow();
                // Note that if any IQ request stanzas had been scheduled, they
                // MUST be responded to with an error here. A list of tasks that
                // have never been commenced by the executor is returned by the
                // #shutdownNow() method of the ExecutorService.
            }
        } catch (InterruptedException e) {
            // ignore, as we're shutting down anyway.
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see org.jivesoftware.openfire.handler.IQHandler#getInfo()
     */
    @Override
    public IQHandlerInfo getInfo() {
        return info;
    }

    /**
     * Implements ServerIdentitiesProvider and UserIdentitiesProvider, adding
     * the PEP identity to the respective disco#info results.
     */
    @Override
    public Iterator<Element> getIdentities() {
        Element identity = DocumentHelper.createElement("identity");
        identity.addAttribute("category", "pubsub");
        identity.addAttribute("type", "pep");
        return Collections.singleton(identity).iterator();
    }

    /**
     * Implements ServerFeaturesProvider to include all supported XEP-0060 features
     * in the server's disco#info result (as per section 4 of XEP-0163).
     */
    @Override
    public Iterator<String> getFeatures() {
        Iterator<String> it = XMPPServer.getInstance().getPubSubModule().getFeatures(null, null, null);
        ArrayList<String> features = new ArrayList<>();
        while (it.hasNext()) {
            features.add(it.next());
        }
        // Auto Creation of nodes is supported in PEP
        features.add("http://jabber.org/protocol/pubsub#auto-create");
        features.add("http://jabber.org/protocol/pubsub#auto-subscribe");
        features.add("http://jabber.org/protocol/pubsub#filtered-notifications");
        return features.iterator();
    }


    /**
     * Returns true if the PEP service is enabled in the server.
     *
     * @return true if the PEP service is enabled in the server.
     */
    // TODO: listen for property changes to stop/start this module.
    public boolean isEnabled() {
        return JiveGlobals.getBooleanProperty("xmpp.pep.enabled", true);
    }

    // *****************************************************************
    // *** Generic module management ends here. From this point on   ***
    // *** more specific PEP related implementation after this point ***
    // *****************************************************************
    
    /*
     * (non-Javadoc)
     *
     * @see
     * org.jivesoftware.openfire.handler.IQHandler#handleIQ(org.xmpp.packet.IQ)
     */
    @Override
    public IQ handleIQ(IQ packet) {
        // Do nothing if server is not enabled
        if (!isEnabled()) {
            IQ reply = IQ.createResultIQ(packet);
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(PacketError.Condition.service_unavailable);
            return reply;
        }

        if (packet.getTo() == null || packet.getTo().equals( new JID(XMPPServer.getInstance().getServerInfo().getXMPPDomain())) )
        {
            // packet addressed to service itself (not to a node/user)
            switch ( packet.getType() )
            {
                case set:
                    return handleIQSetToService(packet );
                case get:
                    return handleIQGetToService(packet );
                default:
                    return null; // Ignore 'error' and 'result' stanzas.
            }
        }
        else
        {
            // packet was addressed to a node.
            if ( packet.isRequest() ) {
                return handleIQRequestToUser( packet );
            } else {
                return null; // Ignore IQ packets of type 'error' or 'result'.
            }
        }
    }

    /**
     * Process an IQ get stanza that was addressed to the service (rather than to a node/user).
     *
     * @param packet The stanza to process.
     * @return A response (can be null).
     */
    private IQ handleIQGetToService(IQ packet) {
        final JID senderJID = packet.getFrom();
        final JID bareJidFrom = senderJID.asBareJID();
        packet = packet.createCopy();
        packet.setTo(bareJidFrom);

        final PEPService pepService = pepServiceManager.getPEPService(bareJidFrom);
        pepServiceManager.process(pepService, packet);
        return null;
    }

    /**
     * Process an IQ set stanza that was addressed to the service (rather than to a node/user).
     *
     * @param packet The stanza to process.
     * @return A response (can be null).
     */
    private IQ handleIQSetToService( IQ packet) {
        final JID senderJID = packet.getFrom();
        final JID bareJidFrom = senderJID.asBareJID();
        packet = packet.createCopy();
        packet.setTo(bareJidFrom);

        // Only service local, registered users.
        if ( !UserManager.getInstance().isRegisteredUser( senderJID, false ))
        {
            final IQ reply = IQ.createResultIQ(packet);
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(PacketError.Condition.not_allowed);
            return reply;
        }

        PEPService pepService = pepServiceManager.getPEPService(bareJidFrom);

        // If publishing a node, and the node doesn't exist, create it.
        final Element childElement = packet.getChildElement();
        final Element publishElement = childElement.element("publish");
        if (publishElement != null) {
            final String nodeID = publishElement.attributeValue("node");

            // Do not allow User Avatar nodes to be created.
            // TODO: Implement XEP-0084
            if (nodeID.startsWith("http://www.xmpp.org/extensions/xep-0084.html")) {
                IQ reply = IQ.createResultIQ(packet);
                reply.setChildElement(packet.getChildElement().createCopy());
                reply.setError(PacketError.Condition.feature_not_implemented);
                return reply;
            }

            if (pepService.getNode(nodeID) == null) {
                // Create the node
                final JID creator = bareJidFrom;
                final DefaultNodeConfiguration defaultConfiguration = pepService.getDefaultNodeConfiguration(true);
                final LeafNode newNode = new LeafNode(pepService.getUniqueIdentifier(), pepService.getRootCollectionNode(), nodeID, creator, defaultConfiguration);
                final DataForm publishOptions = PubSubEngine.getPublishOptions( packet );
                if ( publishOptions != null )
                {
                    try
                    {
                        newNode.configure( publishOptions );
                    }
                    catch ( NotAcceptableException e )
                    {
                        Log.warn( "Unable to apply publish-options when creating a new PEP node {} for {}", nodeID, creator, e );
                    }
                }
                newNode.addOwner(creator);
                newNode.saveToDB();
            }
        }

        // Process with PubSub as usual.
        pepServiceManager.process(pepService, packet);
        return null;
    }

    /**
     * Process an IQ set stanza that was addressed to the a node/user.
     *
     * @param packet The stanza to process.
     * @return A response (can be null).
     */
    private IQ handleIQRequestToUser(IQ packet)
    {
        final JID jidTo = packet.getTo().asBareJID();
        final PEPService pepService = pepServiceManager.getPEPService(jidTo);
        pepServiceManager.process(pepService, packet);
        return null;
    }

    /**
     * Populates the PEPService instance with subscriptions. The subscriptions that
     * are added to the PEPService are based on the roster of the owner of the PEPService:
     * every entity that's subscribed to the presence of the owner, is added as a
     * subscriber of the PEPService.
     *
     * This method adds, but does not remove of update existing subscriptions.
     *
     * @param pepService The PEPService to be populated with subscriptions.
     */
    public void addSubscriptionForRosterItems( final PEPService pepService )
    {
        try {
            final RosterManager rm = XMPPServer.getInstance().getRosterManager();
            final Roster roster = rm.getRoster(pepService.getAddress().getNode());
            for (final RosterItem item : roster.getRosterItems()) {
                if (item.getSubStatus() == RosterItem.SUB_BOTH || item.getSubStatus() == RosterItem.SUB_FROM) {
                    createSubscriptionToPEPService(pepService, item.getJid(), pepService.getAddress());
                }
            }
        } catch (UserNotFoundException e) {
            Log.warn("Attempting to manage subscriptions for a PEP node that is associated to an unrecognized user: {}", pepService.getAddress(), e);
        }
    }

    /**
     * Generates and processes an IQ stanza that subscribes to a PEP service.
     *
     * @param pepService the PEP service of the owner.
     * @param subscriber the JID of the entity that is subscribing to the PEP service.
     * @param owner      the JID of the owner of the PEP service.
     */
    private void createSubscriptionToPEPService(PEPService pepService, JID subscriber, JID owner) {
        // If `owner` has a PEP service, generate and process a pubsub subscription packet
        // that is equivalent to: (where 'from' field is JID of subscriber and 'to' field is JID of owner)
        //
        //        <iq type='set'
        //            from='nurse@capulet.com/chamber'
        //            to='juliet@capulet.com
        //            id='collsub'>
        //          <pubsub xmlns='http://jabber.org/protocol/pubsub'>
        //            <subscribe jid='nurse@capulet.com'/>
        //            <options>
        //              <x xmlns='jabber:x:data'>
        //                <field var='FORM_TYPE' type='hidden'>
        //                  <value>http://jabber.org/protocol/pubsub#subscribe_options</value>
        //                </field>
        //                <field var='pubsub#subscription_type'>
        //                  <value>items</value>
        //                </field>
        //                <field var='pubsub#subscription_depth'>
        //                  <value>all</value>
        //                </field>
        //              </x>
        //           </options>
        //         </pubsub>
        //        </iq>

        IQ subscriptionPacket = new IQ(IQ.Type.set);
        subscriptionPacket.setFrom(subscriber);
        subscriptionPacket.setTo(owner.toBareJID());

        Element pubsubElement = subscriptionPacket.setChildElement("pubsub", "http://jabber.org/protocol/pubsub");

        Element subscribeElement = pubsubElement.addElement("subscribe");
        subscribeElement.addAttribute("jid", subscriber.toBareJID());

        Element optionsElement = pubsubElement.addElement("options");
        Element xElement = optionsElement.addElement(QName.get("x", "jabber:x:data"));

        DataForm dataForm = new DataForm(xElement);

        FormField formField = dataForm.addField();
        formField.setVariable("FORM_TYPE");
        formField.setType(FormField.Type.hidden);
        formField.addValue("http://jabber.org/protocol/pubsub#subscribe_options");

        formField = dataForm.addField();
        formField.setVariable("pubsub#subscription_type");
        formField.addValue("items");

        formField = dataForm.addField();
        formField.setVariable("pubsub#subscription_depth");
        formField.addValue("all");

        pepServiceManager.process(pepService, subscriptionPacket);
    }

    /**
     * Cancels a subscription to a PEPService's root collection node.
     *
     * @param unsubscriber the JID of the subscriber whose subscription is being canceled.
     * @param serviceOwner the JID of the owner of the PEP service for which the subscription is being canceled.
     */
    private void cancelSubscriptionToPEPService(JID unsubscriber, JID serviceOwner) {
        // Retrieve recipientJID's PEP service, if it exists.
        PEPService pepService = pepServiceManager.getPEPService(serviceOwner.asBareJID(), false);
        if (pepService == null) {
            return;
        }

        // Cancel unsubscriberJID's subscription to recipientJID's PEP service, if it exists.
        CollectionNode rootNode = pepService.getRootCollectionNode();
        NodeSubscription nodeSubscription = rootNode.getSubscription(unsubscriber);
        if (nodeSubscription != null) {
            rootNode.cancelSubscription(nodeSubscription);
        }
    }

    /**
     * Implements UserItemsProvider, adding PEP related items to a disco#items
     * result.
     */
    @Override
    public Iterator<Element> getUserItems(String name, JID senderJID) {
        ArrayList<Element> items = new ArrayList<>();

        JID recipientJID = XMPPServer.getInstance().createJID(name, null, true).asBareJID();
        PEPService pepService = pepServiceManager.getPEPService(recipientJID, false);

        if (pepService != null) {
            CollectionNode rootNode = pepService.getRootCollectionNode();

            Element defaultItem = DocumentHelper.createElement("item");
            defaultItem.addAttribute("jid", recipientJID.toString());

            for (Node node : pepService.getNodes()) {
                // Do not include the root node as an item element.
                if (node == rootNode) {
                    continue;
                }

                AccessModel accessModel = node.getAccessModel();
                if (accessModel.canAccessItems(node, senderJID, recipientJID)) {
                    Element item = defaultItem.createCopy();
                    item.addAttribute("node", node.getUniqueIdentifier().getNodeId());
                    items.add(item);
                }
            }
        }

        return items.iterator();
    }

    @Override
    public void subscribedToPresence(JID subscriberJID, JID authorizerJID) {
        final PEPService pepService = pepServiceManager.getPEPService(authorizerJID.asBareJID(), false);
        if (pepService != null) {
            createSubscriptionToPEPService(pepService, subscriberJID, authorizerJID);

            // Delete any leaf node subscriptions the subscriber may have already
            // had (since a subscription to the PEP service, and thus its leaf PEP
            // nodes, would be duplicating publish notifications from previous leaf
            // node subscriptions).
            CollectionNode rootNode = pepService.getRootCollectionNode();
            for (Node node : pepService.getNodes()) {
                if (rootNode.isChildNode(node)) {
                    for (NodeSubscription subscription : node.getSubscriptions(subscriberJID)) {
                        node.cancelSubscription(subscription);
                    }
                }
            }

            pepService.sendLastPublishedItems(subscriberJID);
        }
    }

    @Override
    public void unsubscribedToPresence(JID unsubscriberJID, JID recipientJID) {
        cancelSubscriptionToPEPService(unsubscriberJID, recipientJID);
    }

    @Override
    public void availableSession(ClientSession session, Presence presence) {
        // Do nothing if server is not enabled
        if (!isEnabled()) {
            return;
        }
        JID newlyAvailableJID = presence.getFrom();

        if (newlyAvailableJID == null) {
            return;
        }
        
        final GetNotificationsOnInitialPresence task = new GetNotificationsOnInitialPresence(newlyAvailableJID);
        executor.submit(task);
    }

    @Override
    public void contactDeleted(Roster roster, RosterItem item) {
        JID rosterOwner = XMPPServer.getInstance().createJID(roster.getUsername(), null);
        JID deletedContact = item.getJid();

        cancelSubscriptionToPEPService(deletedContact, rosterOwner);
    }

    @Override
    public void userDeleting(User user, Map<String, Object> params) {
        final JID bareJID = XMPPServer.getInstance().createJID(user.getUsername(), null);
        final PEPService pepService = pepServiceManager.getPEPService(bareJID, false);

        if (pepService == null) {
            return;
        }

        // Remove the user's PEP service, finally.
        pepServiceManager.remove(bareJID);
    }

    /**
     *  The following functions are unimplemented required interface methods.
     */
    @Override
    public void unavailableSession(ClientSession session, Presence presence) {
        // Do nothing
    }

    @Override
    public void presenceChanged(ClientSession session, Presence presence) {
        // Do nothing
    }

    @Override
    public boolean addingContact(Roster roster, RosterItem item, boolean persistent) {
        // Do nothing
        return true;
    }

    @Override
    public void contactAdded(Roster roster, RosterItem item) {
        // Do nothing
    }

    @Override
    public void contactUpdated(Roster roster, RosterItem item) {
        // Do nothing
    }

    @Override
    public void rosterLoaded(Roster roster) {
        // Do nothing
    }

    @Override
    public void userCreated(User user, Map<String, Object> params) {
        // Do nothing
    }

    @Override
    public void userModified(User user, Map<String, Object> params) {
        // Do nothing
    }

    // DiscoInfoProvider

    /*
     * With all these, there are basically two axes of binary choice.
     * Either the PEPService exists already - in which case we can defer to it,
     * or else it doesn't, in which case we should use the generic engine.
     *
     * In either case, we might be being asked for a node, or the root. If we're
     * asked for a node when the PEPService doesn't exist that's an error. Otherwise
     * if we're asked for the root we can safely defer to the generic engine.
     */

    @Override
    public Iterator<Element> getIdentities(String name, String node, JID senderJID) {
        JID recipientJID = XMPPServer.getInstance().createJID(name, null, true).asBareJID();
        PEPService pepService = pepServiceManager.getPEPService(recipientJID);

        if (node != null && pepService != null) {
            Node pubNode = pepService.getNode(node);
            if (pubNode == null) return null;
            // Answer the identity of a given node
            Element identity = DocumentHelper.createElement("identity");
            identity.addAttribute("category", "pubsub");
            identity.addAttribute("type", pubNode.isCollectionNode() ? "collection" : "leaf");

            List<Element> identities = new LinkedList<>();
            identities.add(identity);
            return identities.iterator();
        } else if (node != null) {
            return null;
        } else {
            PubSubModule pubsub = XMPPServer.getInstance().getPubSubModule();
            return pubsub.getIdentities(null, null, senderJID);
        }
    }

    @Override
    public Iterator<String> getFeatures(String name, String node, JID senderJID) {
        if (node == null) {
            PubSubModule pubsub = XMPPServer.getInstance().getPubSubModule();
            return pubsub.getFeatures(null, null, senderJID);
        } else {
            List<String> features = new LinkedList<>();
            features.add("http://jabber.org/protocol/pubsub");
            return features.iterator();
        }
    }

    @Override
    public DataForm getExtendedInfo(String name, String node, JID senderJID) {
        return IQDiscoInfoHandler.getFirstDataForm(this.getExtendedInfos(name, node, senderJID));
    }

    @Override
    public Set<DataForm> getExtendedInfos(String name, String node, JID senderJID) {
        JID recipientJID = XMPPServer.getInstance().createJID(name, null, true).asBareJID();
        PEPService pepService = pepServiceManager.getPEPService(recipientJID);
        if (node != null) {
            // Answer the extended info of a given node
            Node pubNode = pepService.getNode(node);
            // Get the metadata data form
            final Set<DataForm> dataForms = new HashSet<>();
            dataForms.add(pubNode.getMetadataForm());
            return dataForms;
        }
        return new HashSet<>();
    }

    @Override
    public boolean hasInfo(String name, String node, JID senderJID) {
        if (node == null) return true;
        JID recipientJID = XMPPServer.getInstance().createJID(name, null, true).asBareJID();
        PEPService pepService = pepServiceManager.getPEPService(recipientJID);

        return pepService.getNode(node) != null;
    }

    private class GetNotificationsOnInitialPresence implements Runnable {

        private final JID availableSessionJID;
        public GetNotificationsOnInitialPresence(final JID availableSessionJID) {
            this.availableSessionJID = availableSessionJID;
        }

        @Override
        public void run() {
            // Send the last published items for the contacts on availableSessionJID's roster.
            try {
                final XMPPServer server = XMPPServer.getInstance();
                final Roster roster = server.getRosterManager().getRoster(availableSessionJID.getNode());
                for (final RosterItem item : roster.getRosterItems()) {
                    if (server.isLocal(item.getJid()) && (item.getSubStatus() == RosterItem.SUB_BOTH ||
                            item.getSubStatus() == RosterItem.SUB_TO)) {
                        PEPService pepService = pepServiceManager.getPEPService(item.getJid().asBareJID());
                        if (pepService != null) {
                            pepService.sendLastPublishedItems(availableSessionJID);
                        }
                    }
                }
            }
            catch (UserNotFoundException e) {
                // Do nothing
            }
        }    	
    }
}
