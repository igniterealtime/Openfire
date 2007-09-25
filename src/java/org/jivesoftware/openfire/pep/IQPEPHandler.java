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
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>
 * An IQHandler used to implement XEP-0163: "Personal Eventing via Pubsub."
 * </p>
 * 
 * <p>
 * For each user on the server there is an associated PEPService interacting
 * with a single PubSubEngine for managing the user's PEP nodes.
 * </p>
 * 
 * <p>
 * An IQHandler can only handle one namespace in its IQHandlerInfo. However, PEP
 * related packets are seen having a variety of different namespaces. Thus,
 * classes like IQPEPOwnerHandler are used to forward packets having these other
 * namespaces to IQPEPHandler.handleIQ().
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

    /**
     * Map of PEP services. Table, Key: bare JID (String); Value: PEPService
     */
    private Map<String, PEPService> pepServices;

    /**
     * Nodes to send filtered notifications for, table: key JID (String); value Set of nodes
     * 
     * filteredNodesMap are used for Contact Notification Filtering as described in XEP-0163. The JID
     * of a user is associated with a set of PEP node IDs they are interested in receiving notifications
     * for.
     */
    private Map<String, Set<String>> filteredNodesMap = new ConcurrentHashMap<String, Set<String>>();

    private IQHandlerInfo info;

    private PubSubEngine pubSubEngine = null;

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

        // TODO: This will need to be refactored once XEP-0115 (Entity Capabilities) is implemented.
        /*
        // Add this PEP handler as a packet interceptor so we may deal with
        // client packets that send disco#info's explaining capabilities
        // including PEP contact notification filters.
        InterceptorManager.getInstance().addInterceptor(this);
        */
    }

    /**
     * Loads a PEP service from the database, if it exists.
     * 
     * @param jid the JID of the owner of the PEP service.
     * @return the loaded PEP service, or null if not found.
     */
    private PEPService loadPEPServiceFromDB(String jid) {
        String GET_PEP_SERVICE = "SELECT DISTINCT serviceID FROM pubsubNode " +
                                 "WHERE serviceID='" + jid + "'";
        PEPService pepService = null;
        
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            // Get all PEP services
            pstmt = con.prepareStatement(GET_PEP_SERVICE);
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
     * Returns the filteredNodesMap.
     * 
     * @return the filteredNodesMap
     */
    public Map<String, Set<String>> getFilteredNodesMap() {
        return filteredNodesMap;
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
        if (packet.getTo() == null && packet.getType() == IQ.Type.set) {
            String jidFrom = senderJID.toBareJID();

            PEPService pepService = getPEPService(jidFrom);

            // If no service exists yet for jidFrom, create one.
            if (pepService == null) {
                // Return an error if the packet is from an anonymous, unregistered user
                // or remote user
                if (!XMPPServer.getInstance().isLocal(senderJID) ||
                        !UserManager.getInstance().isRegisteredUser(senderJID.getNode())) {
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
                    if (Log.isDebugEnabled()) {
                        Log.debug("PEP: Created node ('" + newNode.getNodeID() + "') for " + jidFrom);
                    }
                }
            }

            // Process with PubSub as usual.
            pubSubEngine.process(pepService, packet);

        }
        else if (packet.getType() == IQ.Type.get || packet.getType() == IQ.Type.set) {
            String jidTo = packet.getTo().toBareJID();

            PEPService pepService = getPEPService(jidTo);

            if (pepService != null) {
                pubSubEngine.process(pepService, packet);
            }
            else {
                // Process with PubSub using a dummyService.
                PEPService dummyService = new PEPService(XMPPServer.getInstance(), senderJID.toBareJID());
                pubSubEngine.process(dummyService, packet);
            }

        }
        else {
            // Ignore IQ packets of type 'error' or 'result'.
            return null;
        }

        // Other error flows are handled in pubSubEngine.process(...)
        return null;
    }
    
    /**
     * Retrieves a PEP service -- attempting first from memory, then from the database.
     * 
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

            if (Log.isDebugEnabled()) {
                Log.debug("PEP: " + unsubscriber + " subscription to " + serviceOwner + "'s PEP service was cancelled.");
            }
        }
        else {
            return;
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

        String recipientJID = XMPPServer.getInstance().createJID(name, null).toBareJID();
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
        if (Log.isDebugEnabled()) {
            Log.debug("PEP: " + unsubscriberJID + " unsubscribed from " + recipientJID + "'s presence.");
        }

        cancelSubscriptionToPEPService(unsubscriberJID, recipientJID);
    }

    public void availableSession(ClientSession session, Presence presence) {
         JID newlyAvailableJID = presence.getFrom();

        // Send the last published items for the contacts on newlyAvailableJID's roster. 
        try {
            Roster roster = XMPPServer.getInstance().getRosterManager().getRoster(newlyAvailableJID.getNode());
            for (RosterItem item : roster.getRosterItems()) {
                if (item.getSubStatus() == RosterItem.SUB_BOTH) {
                    PEPService pepService = getPEPService(item.getJid().toBareJID());
                    if (pepService != null) {
                        pepService.sendLastPublishedItems(newlyAvailableJID);
                    }
                }
            }
        }
        catch (UserNotFoundException e) {
            // Do nothing
        }
    }

    public void remoteUserAvailable(Presence presence) {
        JID jidFrom = presence.getFrom();
        JID jidTo   = presence.getTo();

        // Manage the cache of remote presence resources.
        Set<JID> remotePresenceSet = knownRemotePresences.get(jidTo.toBareJID());

        if (jidFrom.getResource() != null) {
            if (remotePresenceSet != null) {
                if (remotePresenceSet.add(jidFrom)) {
                    if (Log.isDebugEnabled()) {
                        Log.debug("PEP: added " + jidFrom + " to " + jidTo + "'s knownRemotePresences");
                    }
                }
            }
            else {
                remotePresenceSet = new HashSet<JID>();
                if (remotePresenceSet.add(jidFrom)) {
                    if (Log.isDebugEnabled()) {
                        Log.debug("PEP: added " + jidFrom + " to " + jidTo + "'s knownRemotePresences");
                    }
                    knownRemotePresences.put(jidTo.toBareJID(), remotePresenceSet);
                }
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
        JID jidTo   = presence.getTo();
        
        // Manage the cache of remote presence resources.
        Set<JID> remotePresenceSet = knownRemotePresences.get(jidTo.toBareJID());
        
        if (remotePresenceSet != null && remotePresenceSet.remove(jidFrom)) {
            if (Log.isDebugEnabled()) {
                Log.debug("PEP: removed " + jidFrom + " from " + jidTo + "'s knownRemotePresences");
            }
        }
    }

    public void contactDeleted(Roster roster, RosterItem item) {
        JID rosterOwner = XMPPServer.getInstance().createJID(roster.getUsername(), null);
        JID deletedContact = item.getJid();

        if (Log.isDebugEnabled()) {
            Log.debug("PEP: contactDeleted: rosterOwner is " + rosterOwner + " and deletedContact is" + deletedContact);
        }

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

    // TODO: This will need to be refactored once XEP-0115 (Entity Capabilities) is implemented.
    /*
    public void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed) throws PacketRejectedException {
        if (processed && packet instanceof IQ && ((IQ) packet).getType() == IQ.Type.result) {
            // Examine the packet and return if it does not look like a disco#info result containing
            // Entity Capabilities for a client. The sooner we return the better, as this method will be called
            // quite a lot.
            Element element = packet.getElement();
            if (element == null) {
                return;
            }
            Element query = element.element("query");
            if (query == null) {
                return;
            }
            else {
                if (query.attributeValue("node") == null) {
                    return;
                }
                String queryNamespace = query.getNamespaceURI();
                if (queryNamespace == null || !queryNamespace.equals("http://jabber.org/protocol/disco#info")) {
                    return;
                }
            }

            if (Log.isDebugEnabled()) {
                Log.debug("PEP: Intercepted a caps result packet: " + packet.toString());
            }

            Iterator featuresIterator = query.elementIterator("feature");
            if (featuresIterator == null) {
                return;
            }

            // Get the sender's full JID considering they may be logged in from multiple
            // clients with different notification filters.
            String jidFrom = packet.getFrom().toString();

            // For each feature variable, or in this case node ID, ending in "+notify" -- add
            // the node ID to the set of filtered nodes that jidFrom is interested in being
            // notified about.
            //
            // If none of the feature variables contain the node ID ending in "+notify",
            // remove it from the set of filtered nodes that jidFrom is interested in being
            // notified about.
            Set<String> supportedNodesSet = new HashSet<String>();
            while (featuresIterator.hasNext()) {
                Element featureElement = (Element) featuresIterator.next();

                String featureVar = featureElement.attributeValue("var");
                if (featureVar == null) {
                    continue;
                }

                supportedNodesSet.add(featureVar);
            }

            for (String nodeID : supportedNodesSet) {
                if (nodeID.endsWith("+notify")) {
                    // Add the nodeID to the sender's filteredNodesSet.
                    Set<String> filteredNodesSet = filteredNodesMap.get(jidFrom);

                    if (filteredNodesSet == null) {
                        filteredNodesSet = new HashSet<String>();
                        filteredNodesSet.add(nodeID);
                        filteredNodesMap.put(jidFrom, filteredNodesSet);

                        if (Log.isDebugEnabled()) {
                            Log.debug("PEP: Created filteredNodesSet for " + jidFrom);
                            Log.debug("PEP: Added " + nodeID + " to " + jidFrom + "'s set of filtered nodes.");
                        }
                    }
                    else {
                        if (filteredNodesSet.add(nodeID)) {
                            if (Log.isDebugEnabled()) {
                                Log.debug("PEP: Added " + nodeID + " to " + jidFrom + "'s set of filtered nodes: ");
                                Iterator tempIter = filteredNodesSet.iterator();
                                while (tempIter.hasNext()) {
                                    Log.debug("PEP: " + tempIter.next());
                                }
                            }
                        }
                    }

                }
                else {
                    // Remove the nodeID from the sender's filteredNodesSet if nodeIDPlusNotify
                    // is not in supportedNodesSet.
                    Set<String> filteredNodesSet = filteredNodesMap.get(jidFrom);
                    if (filteredNodesSet == null) {
                        return;
                    }

                    String nodeIDPlusNotify = nodeID + "+notify";

                    if (!supportedNodesSet.contains(nodeIDPlusNotify) && filteredNodesSet.remove(nodeIDPlusNotify)) {
                        if (Log.isDebugEnabled()) {
                            Log.debug("PEP: Removed " + nodeIDPlusNotify + " from " + jidFrom + "'s set of filtered nodes: ");
                            Iterator tempIter = filteredNodesSet.iterator();
                            while (tempIter.hasNext()) {
                                Log.debug("PEP: " + tempIter.next());
                            }
                        }
                    }
                }
            }
        }
    }
    */

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
