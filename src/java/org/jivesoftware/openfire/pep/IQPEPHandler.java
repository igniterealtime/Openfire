/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.jivesoftware.openfire.disco.ServerIdentitiesProvider;
import org.jivesoftware.openfire.disco.UserIdentitiesProvider;
import org.jivesoftware.openfire.disco.UserItemsProvider;
import org.jivesoftware.openfire.event.UserEventDispatcher;
import org.jivesoftware.openfire.event.UserEventListener;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.openfire.pubsub.CollectionNode;
import org.jivesoftware.openfire.pubsub.LeafNode;
import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.openfire.pubsub.NodeSubscription;
import org.jivesoftware.openfire.pubsub.PubSubEngine;
import org.jivesoftware.openfire.pubsub.models.AccessModel;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.roster.RosterEventDispatcher;
import org.jivesoftware.openfire.roster.RosterEventListener;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.roster.RosterManager;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.user.PresenceEventDispatcher;
import org.jivesoftware.openfire.user.PresenceEventListener;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserNotFoundException;
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
 * <p>
 *
 * <p>
 * This handler is used for the following namespaces:
 * <ul>
 * <li><i>http://jabber.org/protocol/pubsub</i></li>
 * <li><i>http://jabber.org/protocol/pubsub#owner</i></li>
 * </ul>
 * </p>
 *
 * @author Armando Jagucki
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class IQPEPHandler extends IQHandler implements ServerIdentitiesProvider, ServerFeaturesProvider,
        UserIdentitiesProvider, UserItemsProvider, PresenceEventListener,
        RosterEventListener, UserEventListener {

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
    public Iterator<Element> getIdentities() {
        ArrayList<Element> identities = new ArrayList<Element>();
        Element identity = DocumentHelper.createElement("identity");
        identity.addAttribute("category", "pubsub");
        identity.addAttribute("type", "pep");
        identities.add(identity);
        return identities.iterator();
    }

    /**
     * Implements ServerFeaturesProvider to include all supported XEP-0060 features
     * in the server's disco#info result (as per section 4 of XEP-0163).
     */
    public Iterator<String> getFeatures() {
        return XMPPServer.getInstance().getPubSubModule().getFeatures(null, null, null);
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
    public IQ handleIQ(IQ packet) throws UnauthorizedException {
        // Do nothing if server is not enabled
        if (!isEnabled()) {
            IQ reply = IQ.createResultIQ(packet);
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(PacketError.Condition.service_unavailable);
            return reply;
        }

        final JID senderJID = packet.getFrom();
        if (packet.getTo() == null) {
        	// packet addressed to service itself (not to a node/user)
            final String jidFrom = senderJID.toBareJID();
            packet = packet.createCopy();
            packet.setTo(jidFrom);

            if (packet.getType() == IQ.Type.set) {
                PEPService pepService = pepServiceManager.getPEPService(jidFrom);

                // If no service exists yet for jidFrom, create one.
                if (pepService == null) {
                	try {
                		pepService = pepServiceManager.create(senderJID);                		
                	} catch (IllegalArgumentException ex) {
            			final IQ reply = IQ.createResultIQ(packet);
            			reply.setChildElement(packet.getChildElement().createCopy());
            			reply.setError(PacketError.Condition.not_allowed);
            			return reply;
                	}

            		// Probe presences
            		pepServiceManager.start(pepService);

            		// Those who already have presence subscriptions to jidFrom
					// will now automatically be subscribed to this new
					// PEPService.
					try {
						final RosterManager rm = XMPPServer.getInstance()
								.getRosterManager();
						final Roster roster = rm.getRoster(senderJID.getNode());
						for (final RosterItem item : roster.getRosterItems()) {
							if (item.getSubStatus() == RosterItem.SUB_BOTH
									|| item.getSubStatus() == RosterItem.SUB_FROM) {
								createSubscriptionToPEPService(pepService, item
										.getJid(), senderJID);
							}
						}
					} catch (UserNotFoundException e) {
						// Do nothing
					}
                }

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
                        final JID creator = new JID(jidFrom);
                        final LeafNode newNode = new LeafNode(pepService, pepService.getRootCollectionNode(), nodeID, creator);
                        newNode.addOwner(creator);
                        newNode.saveToDB();
                    }
                }

                // Process with PubSub as usual.
                pepServiceManager.process(pepService, packet);
            } else if (packet.getType() == IQ.Type.get) {
                final PEPService pepService = pepServiceManager.getPEPService(jidFrom);            	
                
                if (pepService != null) {
                	pepServiceManager.process(pepService, packet);
                } else {
                    // Process with PubSub using a dummyService. In the case where an IQ packet is sent to
                    // a user who does not have a PEP service, we wish to utilize the error reporting flow
                    // already present in the PubSubEngine. This gives the illusion that every user has a
                    // PEP service, as required by the specification.
                    PEPService dummyService = new PEPService(XMPPServer.getInstance(), senderJID.toBareJID());
                    pepServiceManager.process(dummyService, packet);
                }
            }
        }
        else if (packet.getType() == IQ.Type.get || packet.getType() == IQ.Type.set) {
        	// packet was addressed to a node.
        	
            final String jidTo = packet.getTo().toBareJID();

            final PEPService pepService = pepServiceManager.getPEPService(jidTo);

            if (pepService != null) {
            	pepServiceManager.process(pepService, packet);
            } else {
                // Process with PubSub using a dummyService. In the case where an IQ packet is sent to
                // a user who does not have a PEP service, we wish to utilize the error reporting flow
                // already present in the PubSubEngine. This gives the illusion that every user has a
                // PEP service, as required by the specification.
                PEPService dummyService = new PEPService(XMPPServer.getInstance(), senderJID.toBareJID());
                pepServiceManager.process(dummyService, packet);
            }
        } else {
            // Ignore IQ packets of type 'error' or 'result'.
            return null;
        }

        // Other error flows were handled in pubSubEngine.process(...)
        return null;
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
        PEPService pepService = pepServiceManager.getPEPService(serviceOwner.toBareJID());
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
    public Iterator<Element> getUserItems(String name, JID senderJID) {
        ArrayList<Element> items = new ArrayList<Element>();

        String recipientJID = XMPPServer.getInstance().createJID(name, null, true).toBareJID();
        PEPService pepService = pepServiceManager.getPEPService(recipientJID);

        if (pepService != null) {
            CollectionNode rootNode = pepService.getRootCollectionNode();

            Element defaultItem = DocumentHelper.createElement("item");
            defaultItem.addAttribute("jid", recipientJID);

            for (Node node : pepService.getNodes()) {
                // Do not include the root node as an item element.
                if (node == rootNode) {
                    continue;
                }

                AccessModel accessModel = node.getAccessModel();
                if (accessModel.canAccessItems(node, senderJID, new JID(recipientJID))) {
                    Element item = defaultItem.createCopy();
                    item.addAttribute("node", node.getNodeID());
                    items.add(item);
                }
            }
        }

        return items.iterator();
    }

    public void subscribedToPresence(JID subscriberJID, JID authorizerJID) {
        final PEPService pepService = pepServiceManager.getPEPService(authorizerJID.toBareJID());
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

    public void unsubscribedToPresence(JID unsubscriberJID, JID recipientJID) {
        cancelSubscriptionToPEPService(unsubscriberJID, recipientJID);
    }

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

    public void contactDeleted(Roster roster, RosterItem item) {
        JID rosterOwner = XMPPServer.getInstance().createJID(roster.getUsername(), null);
        JID deletedContact = item.getJid();

        cancelSubscriptionToPEPService(deletedContact, rosterOwner);
    }

    public void userDeleting(User user, Map<String, Object> params) {
        final JID bareJID = XMPPServer.getInstance().createJID(user.getUsername(), null);
        final PEPService pepService = pepServiceManager.getPEPService(bareJID.toString());

        if (pepService == null) {
            return;
        }

        // Remove the user's PEP service, finally.
        pepServiceManager.remove(bareJID);
    }

    /**
     *  The following functions are unimplemented required interface methods.
     */
    public void unavailableSession(ClientSession session, Presence presence) {
        // Do nothing
    }

    public void presenceChanged(ClientSession session, Presence presence) {
        // Do nothing
    }

    public boolean addingContact(Roster roster, RosterItem item, boolean persistent) {
        // Do nothing
        return true;
    }

    public void contactAdded(Roster roster, RosterItem item) {
        // Do nothing
    }

    public void contactUpdated(Roster roster, RosterItem item) {
        // Do nothing
    }

    public void rosterLoaded(Roster roster) {
        // Do nothing
    }

    public void userCreated(User user, Map<String, Object> params) {
        // Do nothing
    }

    public void userModified(User user, Map<String, Object> params) {
        // Do nothing
    }

    private class GetNotificationsOnInitialPresence implements Runnable {
    	
    	private final JID availableSessionJID;
    	public GetNotificationsOnInitialPresence(final JID availableSessionJID) {
    		this.availableSessionJID = availableSessionJID;
    	}
    	
        public void run() {
            // Send the last published items for the contacts on availableSessionJID's roster.
            try {
                final XMPPServer server = XMPPServer.getInstance();
                final Roster roster = server.getRosterManager().getRoster(availableSessionJID.getNode());
                for (final RosterItem item : roster.getRosterItems()) {
                    if (server.isLocal(item.getJid()) && (item.getSubStatus() == RosterItem.SUB_BOTH ||
                            item.getSubStatus() == RosterItem.SUB_TO)) {
                        PEPService pepService = pepServiceManager.getPEPService(item.getJid().toBareJID());
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
