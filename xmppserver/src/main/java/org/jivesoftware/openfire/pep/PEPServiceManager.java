/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.entitycaps.EntityCapabilities;
import org.jivesoftware.openfire.entitycaps.EntityCapabilitiesListener;
import org.jivesoftware.openfire.pubsub.*;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.CacheableOptional;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

/**
 * Manages the creation, persistence and removal of {@link PEPService}
 * instances.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 *
 */
public class PEPServiceManager implements EntityCapabilitiesListener {

    public static final Logger Log = LoggerFactory
            .getLogger(PEPServiceManager.class);

    /**
     * Cache of PEP services. Table, Key: bare JID; Value: PEPService
     */
    private final Cache<JID, CacheableOptional<PEPService>> pepServices = CacheFactory
        .createLocalCache("PEPServiceManager");

    private PubSubEngine pubSubEngine = null;

    public void initialize() {
        XMPPServer.getInstance().getEntityCapabilitiesManager().addListener(this);
    }

    public void destroy() {
        XMPPServer.getInstance().getEntityCapabilitiesManager().removeListener(this);
    }

    /**
     * Retrieves a PEP service -- attempting first from memory, then from the
     * database.
     *
     * This method will automatically create a PEP service if one does not exist.
     *
     * @param uniqueIdentifier
     *            the unique identifier of the PEP service.
     * @return the requested PEP service.
     */
    public PEPService getPEPService( PubSubService.UniqueIdentifier uniqueIdentifier )
    {
        return getPEPService( uniqueIdentifier, true );
    }

    /**
     * Retrieves a PEP service -- attempting first from memory, then from the
     * database.
     *
     * This method can automatically create a PEP service if one does not exist.
     *
     * @param uniqueIdentifier
     *            the unique identifier of the PEP service.
     * @param autoCreate
     *            true if a PEP service that does not yet exist needs to be created.
     * @return the requested PEP service if found or null if not found.
     */
    public PEPService getPEPService( PubSubService.UniqueIdentifier uniqueIdentifier, boolean autoCreate )
    {
        // PEP Services use the JID as their service identifier.
        final JID needle;
        try {
            needle = new JID(uniqueIdentifier.getServiceId());
        } catch (IllegalArgumentException ex) {
            Log.warn( "Unable to get PEP service. Provided unique identifier does not contain a valid JID: " + uniqueIdentifier, ex );
            return null;
        }
        return getPEPService( needle, autoCreate );
    }

    /**
     * Retrieves a PEP service -- attempting first from memory, then from the
     * database.
     *
     * This method will automatically create a PEP service if one does not exist.
     *
     * @param jid
     *            the JID of the user that owns the PEP service.
     * @return the requested PEP service.
     */
    public PEPService getPEPService( JID jid ) {
        return getPEPService( jid, true );
    }

    /**
     * Retrieves a PEP service -- attempting first from memory, then from the
     * database.
     *
     * This method can automatically create a PEP service if one does not exist.
     *
     * @param jid
     *            the JID of the user that owns the PEP service.
     * @param autoCreate
     *            true if a PEP service that does not yet exist needs to be created.
     * @return the requested PEP service if found or null if not found.
     */
    public PEPService getPEPService( JID jid, boolean autoCreate ) {
        jid = jid.asBareJID();
        PEPService pepService;

        final Lock lock = pepServices.getLock(jid);
        lock.lock();
        try {
            if (pepServices.containsKey(jid)) {
                // lookup in cache
                if ( pepServices.get(jid).isAbsent() && autoCreate ) {
                    // needs auto-create despite negative cache.
                    pepService = null;
                } else {
                    return pepServices.get(jid).get();
                }
            } else {
                // lookup in database.
                pepService = XMPPServer.getInstance().getPubSubModule().getPersistenceProvider().loadPEPServiceFromDB(jid);
                pepServices.put(jid, CacheableOptional.of(pepService));
                if ( pepService != null ) {
                    pepService.initialize();
                }
            }

            if ( pepService != null ) {
                Log.debug("PEP: Restored service for {} from the database.", jid);
                pubSubEngine.start(pepService);
            } else if (autoCreate) {
                Log.debug("PEP: Auto-created service for {}.", jid);
                pepService = this.create(jid);

                // Probe presences
                pubSubEngine.start(pepService);

                // Those who already have presence subscriptions to jidFrom
                // will now automatically be subscribed to this new
                // PEPService.
                XMPPServer.getInstance().getIQPEPHandler().addSubscriptionForRosterItems( pepService );
            }
        } finally {
            lock.unlock();
        }

        return pepService;
    }

    /**
     * Retrieves a PEP service -- attempting first from memory, then from the
     * database.
     *
     * This method will automatically create a PEP service if one does not exist.
     *
     * @param jid
     *            the bare JID of the user that owns the PEP service.
     * @return the requested PEP service.
     * @deprecated Replaced by {@link #getPEPService(JID)}
     */
    @Deprecated
    public PEPService getPEPService( String jid ) {
        return getPEPService( jid, true );
    }

    /**
     * Retrieves a PEP service -- attempting first from memory, then from the
     * database.
     *
     * This method can automatically create a PEP service if one does not exist.
     *
     * @param jid
     *            the bare JID of the user that owns the PEP service.
     * @param autoCreate
     *            true if a PEP service that does not yet exist needs to be created.
     * @return the requested PEP service if found or null if not found.
     * @deprecated Replaced by {@link #getPEPService(JID, boolean)}
     */
    @Deprecated
    public PEPService getPEPService( String jid, boolean autoCreate ) {
        return getPEPService( new JID(jid), autoCreate );
    }

    public PEPService create(JID owner) {
        // Return an error if the packet is from an anonymous, unregistered user
        // or remote user
        if (!UserManager.getInstance().isRegisteredUser(owner, false)) {
            throw new IllegalArgumentException(
                    "Request must be initiated by a local, registered user, but is not: "
                            + owner);
        }

        PEPService pepService = null;
        final JID bareJID = owner.asBareJID();
        final Lock lock = pepServices.getLock(bareJID);
        lock.lock();
        try {

            if (pepServices.get(bareJID) != null) {
                pepService = pepServices.get(bareJID).get();
            }

            if (pepService == null) {
                pepService = new PEPService(XMPPServer.getInstance(), bareJID);
                pepServices.put(bareJID, CacheableOptional.of(pepService));
                pepService.initialize();

                Log.debug("PEPService created for: '{}'", bareJID);
            }
        } finally {
            lock.unlock();
        }

        return pepService;
    }

    /**
     * Deletes the {@link PEPService} belonging to the specified owner.
     *
     * @param owner
     *            The JID of the owner of the service to be deleted.
     */
    public void remove(JID owner)
    {
        final JID address = owner.asBareJID();
        final Lock lock = pepServices.getLock(address);
        lock.lock();
        try {
            final PEPService pepService = getPEPService(address, false);
            if ( pepService == null ) {
                return;
            }

            // To remove individual nodes, the PEPService must still be registered. Do not remove the service until
            // after all nodes are deleted (OF-2020)
            pubSubEngine.shutdown(pepService); // TODO would shutting down first, and deleting after unrighteously withhold notifications reflecting the deletion of nodes?

            // Delete the user's PEP nodes from memory and the database.
            // FIXME OF-2104: this implementation does not appear to remove all data.
            CollectionNode rootNode = pepService.getRootCollectionNode();
            for ( final Node node : pepService.getNodes() )
            {
                if ( rootNode.isChildNode(node) )
                {
                    node.delete();
                }
            }
            rootNode.delete();

            // All nodes are now deleted. The service itself can now be deleted.

            // Remove from cache if it was in.
            pepServices.remove(address).get();
            Log.debug("PEPService destroyed for: '{}'", address);
        } finally {
            lock.unlock();
        }
    }

    public void start(PEPService pepService) {
        pubSubEngine.start(pepService);
    }

    public void start() {
        Log.debug("Starting...");
        pubSubEngine = new PubSubEngine(XMPPServer.getInstance().getPacketRouter());
    }

    public void stop() {
        Log.debug("Stopping...");
        for (final CacheableOptional<PEPService> service : pepServices.values()) {
            if (service.isPresent()) {
                pubSubEngine.shutdown(service.get());
            }
        }

        pubSubEngine = null;
    }

    public void process(PEPService service, IQ iq) {
        pubSubEngine.process(service, iq);
    }

    public boolean hasCachedService(JID owner) {
        return pepServices.get(owner.asBareJID()) != null;
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

        if ( nodeIDs.isEmpty() ) {
            return;
        }
        Log.debug( "Entity '{}' expressed new interest in receiving notifications for nodes '{}'", entity, String.join( ", ", nodeIDs ) );

        // Find all the nodes that the entity is subscribed to, including its own.
        final Set<Node> nodesToBeProcessed = new HashSet<>();
        for ( final String nodeID : nodeIDs ) {
            nodesToBeProcessed.addAll(findSubscribedNodes(entity, nodeID));
        }
        if (XMPPServer.getInstance().isLocal( entity ) && UserManager.getInstance().isRegisteredUser( entity.getNode() ) ) {
            final PEPService service = getPEPService( entity );
            for ( final String nodeID : nodeIDs ) {
                final Node node = service.getNode( nodeID );
                if ( node != null ) {
                    nodesToBeProcessed.add(node);
                }
            }
        }

        Log.debug( "Entity '{}' has {} applicable nodes (through ownership and subscription).", entity, nodesToBeProcessed.size() );
        if ( nodesToBeProcessed.isEmpty() )
        {
            return;
        }

        Log.trace( "Entity '{}' is eligible to receive notifications of nodes '{}'. Sending last published items for each of these nodes.", entity, String.join( ", ", nodesToBeProcessed.stream().map(Node::getUniqueIdentifier).map(Node.UniqueIdentifier::toString).collect(Collectors.toSet()) ) );
        for ( final Node node : nodesToBeProcessed )
        {
            ((PEPService)node.getService()).sendLastPublishedItems(entity, nodeIDs);
        }
    }

    /**
     * Returns all PEP nodes with a specific ID that the provided entity is a subscriber to. This would typically
     * return a similar node for many different services (eg: the 'user-tune' node of the PEP services of all of
     * entity's contacts.
     *
     * @param entity The entity address.
     * @param nodeId The NodeID of the nodes to return
     * @return A collection of nodes (possibly empty).
     */
    @Nonnull
    public Set<Node> findSubscribedNodes(@Nonnull final JID entity, @Nonnull final String nodeId)
    {
        final Set<Node> result = new HashSet<>();

        // Find all nodes that the entity has a direct subscription to. Most of these will be root nodes (representing a service)
        // for which subscriptions apply to all child nodes. The resulting nodes could also be intermediate collection nodes
        // (that might similarly have subscriptions bubbling up), or specific leaf nodes.
        final Set<Node.UniqueIdentifier> directlySubscribedNodes = XMPPServer.getInstance().getPubSubModule().getPersistenceProvider().findDirectlySubscribedNodes(entity);

        // For all of the services and collection nodes, see if any of their children match the nodeIdFilter. The implementation here
        // checks if the corresponding service has a node with a matching nodeID at all. If it does, it explicitly checks if the
        // entity has a subscription to that node (which recursively looks at its parents).
        final Set<PubSubService.UniqueIdentifier> relatedServiceUIDs = directlySubscribedNodes.stream().map(Node.UniqueIdentifier::getServiceIdentifier).collect(Collectors.toSet());
        for( final PubSubService.UniqueIdentifier relatedServiceUID : relatedServiceUIDs ) {
            // Here, we're only interested in PEP services, not generic Pubsub services.
            final PEPService service = getPEPService( relatedServiceUID, false );
            if ( service != null ) {
                final Node node = service.getNode( nodeId );
                if (node != null) {
                    // TODO should we consider other nodes than LeafNode?
                    if ( node instanceof LeafNode && ((LeafNode) node).getAffiliatesToNotify().stream().anyMatch(
                        nodeAffiliate -> nodeAffiliate.getJID().equals(entity) || nodeAffiliate.getJID().equals(entity.asBareJID())) )
                    {
                        result.add( node );
                    }
                }
            }
        }

        Log.trace( "Entity '{}' is subscribed to {} nodes that have NodeID {}", entity, result.size(), nodeId);
        return result;
    }
}
