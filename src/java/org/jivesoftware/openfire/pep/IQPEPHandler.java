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
import org.jivesoftware.database.DbConnectionManager;
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
import org.jivesoftware.openfire.pubsub.*;
import org.jivesoftware.openfire.pubsub.models.AccessModel;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.roster.RosterEventDispatcher;
import org.jivesoftware.openfire.roster.RosterEventListener;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.user.*;
import org.jivesoftware.util.Log;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.Presence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

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
 *
 */
public class IQPEPHandler extends IQHandler implements ServerIdentitiesProvider, ServerFeaturesProvider,
        UserIdentitiesProvider, UserItemsProvider, PresenceEventListener, RemotePresenceEventListener,
        RosterEventListener, UserEventListener {

    final static String GET_PEP_SERVICE = "SELECT DISTINCT serviceID FROM pubsubNode WHERE serviceID=?";

    /**
     * Map of PEP services. Table, Key: bare JID (String); Value: PEPService
     */
    private Map<String, PEPService> pepServices;

    private IQHandlerInfo info;

    private PubSubEngine pubSubEngine = null;

    /**
     * Queue that will store the JID of the local users that came online. This queue
     * will be consumed by another thread to improve performance of the server.
     */
    private static BlockingQueue<JID> availableSessions = new LinkedBlockingQueue<JID>();

    /**
     * A map of all known full JIDs that have sent presences from a remote server.
     * table: key Bare JID (String); value Set of JIDs
     *
     * This map is convenient for sending notifications to the full JID of remote users
     * that have sent available presences to the PEP service.
     */
    private Map<String, Set<JID>> knownRemotePresences = new ConcurrentHashMap<String, Set<JID>>();

    public IQPEPHandler() {
        super("Personal Eventing Handler");
        pepServices = new ConcurrentHashMap<String, PEPService>();
        info = new IQHandlerInfo("pubsub", "http://jabber.org/protocol/pubsub");
        // Create a thread that will process the queued JIDs of the sessions that came online. We
        // are processing the events one at a time so we no longer have the paralellism to the database
        // that was slowing down the server
        Thread thread = new Thread("PEP avaiable sessions handler ") {
            public void run() {
                for (; ;) {
                    try {
                        JID availableSessionJID = availableSessions.take();

                        // Send the last published items for the contacts on availableSessionJID's roster.
                        try {
                            Roster roster = XMPPServer.getInstance().getRosterManager()
                                    .getRoster(availableSessionJID.getNode());
                            for (RosterItem item : roster.getRosterItems()) {
                                if (item.getSubStatus() == RosterItem.SUB_BOTH) {
                                    PEPService pepService = getPEPService(item.getJid().toBareJID());
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
                    catch (Exception e) {
                        Log.error(e);
                    }
                }
            }
        };
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void initialize(XMPPServer server) {
        super.initialize(server);

        // Listen to presence events to manage PEP auto-subscriptions.
        PresenceEventDispatcher.addListener(this);

        // Listen to remote presence events to manage the knownRemotePresences map.
        RemotePresenceEventDispatcher.addListener(this);

        // Listen to roster events for PEP subscription cancelling on contact deletion.
        RosterEventDispatcher.addListener(this);

        // Listen to user events in order to destroy a PEP service when a user is deleted.
        UserEventDispatcher.addListener(this);

        pubSubEngine = new PubSubEngine(server.getPacketRouter());
    }

    /**
     * Loads a PEP service from the database, if it exists.
     *
     * @param jid the JID of the owner of the PEP service.
     * @return the loaded PEP service, or null if not found.
     */
    private PEPService loadPEPServiceFromDB(String jid) {
        PEPService pepService = null;

        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            // Get all PEP services
            pstmt = con.prepareStatement(GET_PEP_SERVICE);
            pstmt.setString(1, jid);
            ResultSet rs = pstmt.executeQuery();
            // Restore old PEPServices
            while (rs.next()) {
                String serviceID = rs.getString(1);

                // Create a new PEPService
                pepService = new PEPService(XMPPServer.getInstance(), serviceID);
                pepServices.put(serviceID, pepService);
                pubSubEngine.start(pepService);

                if (Log.isDebugEnabled()) {
                    Log.debug("PEP: Restored service for " + serviceID + " from the database.");
                }
            }
            rs.close();
            pstmt.close();
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            try {
                if (pstmt != null)
                    pstmt.close();
            }
            catch (Exception e) {
                Log.error(e);
            }
            try {
                if (con != null)
                    con.close();
            }
            catch (Exception e) {
                Log.error(e);
            }
        }

        return pepService;
    }

    public void stop() {
        super.stop();
        for (PEPService service : pepServices.values()) {
            pubSubEngine.shutdown(service);
        }
    }

    public void destroy() {
        super.destroy();
        // Remove listeners
        PresenceEventDispatcher.removeListener(this);
        RemotePresenceEventDispatcher.removeListener(this);
        RosterEventDispatcher.removeListener(this);
        UserEventDispatcher.removeListener(this);
    }

    @Override
    public IQHandlerInfo getInfo() {
        return info;
    }

    /**
     * Returns the knownRemotePresences map.
     *
     * @return the knownRemotePresences map
     */
    public Map<String, Set<JID>> getKnownRemotePresenes() {
        return knownRemotePresences;
    }

    @Override
    public IQ handleIQ(IQ packet) throws UnauthorizedException {
        JID senderJID = packet.getFrom();
        if (packet.getTo() == null) {
            if (packet.getType() == IQ.Type.set) {
                String jidFrom = senderJID.toBareJID();

                PEPService pepService = getPEPService(jidFrom);

                // If no service exists yet for jidFrom, create one.
                if (pepService == null) {
                    // Return an error if the packet is from an anonymous, unregistered user
                    // or remote user
                    if (!XMPPServer.getInstance().isLocal(senderJID) || !UserManager.getInstance().isRegisteredUser(senderJID.getNode())) {
                        IQ reply = IQ.createResultIQ(packet);
                        reply.setChildElement(packet.getChildElement().createCopy());
                        reply.setError(PacketError.Condition.not_allowed);
                        return reply;
                    }

                    pepService = new PEPService(XMPPServer.getInstance(), jidFrom);
                    pepServices.put(jidFrom, pepService);

                    // Probe presences
                    pubSubEngine.start(pepService);
                    if (Log.isDebugEnabled()) {
                        Log.debug("PEP: " + jidFrom + " had a PEPService created");
                    }

                    // Those who already have presence subscriptions to jidFrom
                    // will now automatically be subscribed to this new PEPService.
                    try {
                        Roster roster = XMPPServer.getInstance().getRosterManager().getRoster(senderJID.getNode());
                        for (RosterItem item : roster.getRosterItems()) {
                            if (item.getSubStatus() == RosterItem.SUB_BOTH || item.getSubStatus() == RosterItem.SUB_FROM) {
                                createSubscriptionToPEPService(pepService, item.getJid(), senderJID);
                            }
                        }
                    }
                    catch (UserNotFoundException e) {
                        // Do nothing
                    }
                }

                // If publishing a node, and the node doesn't exist, create it.
                Element childElement = packet.getChildElement();
                Element publishElement = childElement.element("publish");
                if (publishElement != null) {
                    String nodeID = publishElement.attributeValue("node");

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
                        JID creator = new JID(jidFrom);
                        LeafNode newNode = new LeafNode(pepService, pepService.getRootCollectionNode(), nodeID, creator);
                        newNode.addOwner(creator);
                        newNode.saveToDB();
                    }
                }

                // Process with PubSub as usual.
                pubSubEngine.process(pepService, packet);
            }
        }
        else if (packet.getType() == IQ.Type.get || packet.getType() == IQ.Type.set) {
            String jidTo = packet.getTo().toBareJID();

            PEPService pepService = getPEPService(jidTo);

            if (pepService != null) {
                pubSubEngine.process(pepService, packet);
            }
            else {
                // Process with PubSub using a dummyService. In the case where an IQ packet is sent to
                // a user who does not have a PEP service, we wish to utilize the error reporting flow
                // already present in the PubSubEngine. This gives the illusion that every user has a
                // PEP service, as required by the specification.
                PEPService dummyService = new PEPService(XMPPServer.getInstance(), senderJID.toBareJID());
                pubSubEngine.process(dummyService, packet);
            }

        }
        else {
            // Ignore IQ packets of type 'error' or 'result'.
            return null;
        }

        // Other error flows were handled in pubSubEngine.process(...)
        return null;
    }

    /**
     * Retrieves a PEP service -- attempting first from memory, then from the database. Note
     * that if no PEP service was found the next request of the PEP service will hit the
     * database since we are not caching 'no PEP services'.
     *
     * @param jid the bare JID of the user that owns the PEP service.
     * @return the requested PEP service if found or null if not found.
     */
    private PEPService getPEPService(String jid) {
        PEPService pepService = pepServices.get(jid);

        if (pepService == null) {
            pepService = loadPEPServiceFromDB(jid);
        }

        return pepService;
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

        pubSubEngine.process(pepService, subscriptionPacket);
    }

    /**
     * Cancels a subscription to a PEPService's root collection node.
     *
     * @param unsubscriber the JID of the subscriber whose subscription is being canceled.
     * @param serviceOwner the JID of the owner of the PEP service for which the subscription is being canceled.
     */
    private void cancelSubscriptionToPEPService(JID unsubscriber, JID serviceOwner) {
        // Retrieve recipientJID's PEP service, if it exists.
        PEPService pepService = getPEPService(serviceOwner.toBareJID());
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
     * Implements UserItemsProvider, adding PEP related items to a disco#items
     * result.
     */
    public Iterator<Element> getUserItems(String name, JID senderJID) {
        ArrayList<Element> items = new ArrayList<Element>();

        String recipientJID = XMPPServer.getInstance().createJID(name, null, true).toBareJID();
        PEPService pepService = getPEPService(recipientJID);

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
        PEPService pepService = getPEPService(authorizerJID.toBareJID());
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
        JID newlyAvailableJID = presence.getFrom();

        if (newlyAvailableJID == null) {
            return;
        }
        // Store the JID of the session that became online. The processing of this
        // event will take place in another thread to improve performance of the server
        availableSessions.add(newlyAvailableJID);
    }

    public void remoteUserAvailable(Presence presence) {
        JID jidFrom = presence.getFrom();
        JID jidTo = presence.getTo();

        // Manage the cache of remote presence resources.
        Set<JID> remotePresenceSet = knownRemotePresences.get(jidTo.toBareJID());

        if (jidFrom.getResource() != null) {
            if (remotePresenceSet != null) {
                remotePresenceSet.add(jidFrom);
            }
            else {
                remotePresenceSet = new HashSet<JID>();
                remotePresenceSet.add(jidFrom);
                knownRemotePresences.put(jidTo.toBareJID(), remotePresenceSet);
            }

            // Send the presence packet recipient's last published items to the remote user.
            PEPService pepService = getPEPService(jidTo.toBareJID());
            if (pepService != null) {
                pepService.sendLastPublishedItems(jidFrom);
            }
        }
    }

    public void remoteUserUnavailable(Presence presence) {
        JID jidFrom = presence.getFrom();
        JID jidTo = presence.getTo();

        // Manage the cache of remote presence resources.
        Set<JID> remotePresenceSet = knownRemotePresences.get(jidTo.toBareJID());

        if (remotePresenceSet != null) {
            remotePresenceSet.remove(jidFrom);
        }
    }

    public void contactDeleted(Roster roster, RosterItem item) {
        JID rosterOwner = XMPPServer.getInstance().createJID(roster.getUsername(), null);
        JID deletedContact = item.getJid();

        cancelSubscriptionToPEPService(deletedContact, rosterOwner);

    }

    public void userDeleting(User user, Map<String, Object> params) {
        JID bareJID = XMPPServer.getInstance().createJID(user.getUsername(), null);
        PEPService pepService = getPEPService(bareJID.toString());

        if (pepService == null) {
            return;
        }

        // Delete the user's PEP nodes from memory and the database.
        CollectionNode rootNode = pepService.getRootCollectionNode();
        for (Node node : pepService.getNodes()) {
            if (rootNode.isChildNode(node)) {
                node.delete();
            }
        }
        rootNode.delete();

        // Remove the user's PEP service, finally.
        pepServices.remove(bareJID.toString());
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

    public void presencePriorityChanged(ClientSession session, Presence presence) {
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

}
