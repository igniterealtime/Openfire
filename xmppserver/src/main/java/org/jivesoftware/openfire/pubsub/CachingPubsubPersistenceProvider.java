/*
 * Copyright (C) 2020-2026 Ignite Realtime Foundation. All rights reserved.
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

import com.google.common.annotations.VisibleForTesting;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.pep.PEPService;
import org.jivesoftware.openfire.pubsub.cluster.FlushTask;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.TaskEngine;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;

/**
 * A persistence provider for Pub/Sub functionality that adds caching behavior. Instead of 'writing through' to the
 * persistence layer, the caching implementation will create batches of operations (optimizing away redundant actions).
 * Additionally, recently accessed published data items are cached. This improves performance when processing many
 * pub/sub operations (node modifications, item publications, etc).
 *
 * This provider itself does not persist data. Instead, it uses a different persistence provider as a delegate to
 * perform these actions.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class CachingPubsubPersistenceProvider implements PubSubPersistenceProvider
{
    private static final Logger log = LoggerFactory.getLogger(CachingPubsubPersistenceProvider.class);

    /**
     * The class definition used to instantiate the delegate, used by this instance to interact with persistent data
     * storage.
     */
    public static final SystemProperty<Class> DELEGATE = SystemProperty.Builder.ofType(Class.class)
        .setKey("provider.pubsub-persistence.caching.delegate-className")
        .setBaseClass(PubSubPersistenceProvider.class)
        .setDefaultValue(DefaultPubSubPersistenceProvider.class)
        .setDynamic(false)
        .build();

    /**
     * The delegate instance, used by this instance to interact with persistent data storage.
     */
    @VisibleForTesting
    PubSubPersistenceProvider delegate;

    /**
     * Pseudo-random number generator is used to offset timing for scheduled tasks
     * within a cluster (so they don't run at the same time on all members).
     */
    private final Random prng = new Random();

    /**
     * Flush timer delay is configurable, but not less than 20 seconds (default: 2 mins)
     */
    private static final Duration flushTimerDelay = Duration.ofSeconds(Math.max( 20, JiveGlobals.getIntProperty( "xmpp.pubsub.flush.timer", 120)));

    /**
     * Maximum number of published items allowed in the write cache
     * before being flushed to the database.
     */
    private static final int MAX_ITEMS_FLUSH = JiveGlobals.getIntProperty("xmpp.pubsub.flush.max", 1000);

    /**
     * Queue that holds the (wrapped) items that need to be added to the database.
     */
    @VisibleForTesting
    @GuardedBy("itemsPending")
    Map<Node.UniqueIdentifier, Deque<PublishedItem>> itemsToAdd = new HashMap<>();

    /**
     * Queue that holds the items that need to be deleted from the database.
     */
    @VisibleForTesting
    @GuardedBy("itemsPending")
    Map<Node.UniqueIdentifier, Deque<PublishedItem>> itemsToDelete = new HashMap<>();

    /**
     * Keeps reference to published items that haven't been persisted yet so they
     * can be removed before being deleted.
     */
    @VisibleForTesting
    @GuardedBy("itemsPending")
    final Map<PublishedItem.UniqueIdentifier, PublishedItem> itemsPending = new HashMap<>();

    @VisibleForTesting
    final ConcurrentMap<Node.UniqueIdentifier, Deque<NodeOperation>> nodesToProcess = new ConcurrentHashMap<>();

    /**
     * Cache name for recently accessed published items.
     */
    private static final String ITEM_CACHE = "Published Items";

    /**
     * Cache for recently accessed published items.
     */
    private final Cache<PublishedItem.UniqueIdentifier, PublishedItem> itemCache = CacheFactory.createCache( ITEM_CACHE );

    private TimerTask flushTask;

    @Override
    public void initialize()
    {
        log.debug( "Initializing" );

        initDelegate();
        try {
            if (MAX_ITEMS_FLUSH > 0) {
                flushTask = new TimerTask()
                {
                    @Override
                    public void run() { flushPendingChanges(false ); } // this member only
                };
                TaskEngine.getInstance().schedule(flushTask, Duration.ofMillis(Math.abs(prng.nextLong())%flushTimerDelay.toMillis()), flushTimerDelay);
            }

        } catch (Exception ex) {
            log.error("Failed to initialize pubsub maintenance tasks", ex);
        }
    }

    private void initDelegate()
    {
        // Check if we need to reset the provider class
        final Class clazz = DELEGATE.getValue();
        if (delegate == null || !clazz.equals(delegate.getClass())) {
            if ( delegate != null ) {
                delegate.shutdown();
                delegate = null;
            }
            try {
                log.info("Loading PubSub persistence provider to delegate to: {}.", clazz);
                delegate = (PubSubPersistenceProvider) clazz.newInstance();
                delegate.initialize();
            }
            catch (Exception e) {
                log.error("Error loading PubSub persistence provider to delegate to: {}. Using default provider instead.", clazz, e);
                delegate = new DefaultPubSubPersistenceProvider();
                delegate.initialize();
            }
        }
    }

    @Override
    public void shutdown()
    {
        // OF-2086: Persist cached pubsub data prior to shutdown
        flushPendingChanges( false );
        TaskEngine.getInstance().cancelScheduledTask( flushTask );
        delegate.shutdown();
    }

    private void flushPendingNodes()
    {
        log.trace( "Flushing pending nodes (count: {})", nodesToProcess.size() );

        for (final Node.UniqueIdentifier nodeId : new ArrayList<>(nodesToProcess.keySet())) {
            flushPendingNode(nodeId); // remove inside flushPendingNode() is safe because ConcurrentMap supports concurrent removal.
        }
    }

    private void flushPendingNodes( PubSubService.UniqueIdentifier serviceIdentifier )
    {
        log.trace( "Flushing pending nodes for service: {}", serviceIdentifier );

        for (final Node.UniqueIdentifier nodeId : new ArrayList<>(nodesToProcess.keySet())) {
            if (serviceIdentifier.owns(nodeId)) {
                flushPendingNode(nodeId); // remove inside flushPendingNode() is safe because ConcurrentMap supports concurrent removal.
            }
        }
    }

    private void flushPendingNode( Node.UniqueIdentifier uniqueIdentifier )
    {
        // TODO verify if this is having the desired effect. - nodes could be in a hierarchy, which could warrant for flushing the entire tree.
        log.trace( "Flushing pending node: {} for service: {}", uniqueIdentifier.getNodeId(), uniqueIdentifier.getServiceIdentifier().getServiceId() );

        final Deque<NodeOperation> operations = nodesToProcess.remove(uniqueIdentifier);
        if (operations == null) {
            return;
        }

        NodeOperation op;
        while ((op = operations.pollFirst()) != null) {
            process(op);
        }
    }

    @Override
    public void createNode(Node node) {
        log.debug( "Creating node: {}", node.getUniqueIdentifier() );
        final Deque<NodeOperation> operations = nodesToProcess.computeIfAbsent( node.getUniqueIdentifier(), id -> new ConcurrentLinkedDeque<>() );
        // Don't purge pending operations. It'd be odd for operations to already exist at this stage. It'd be possible for
        // a node to be recreated, which could mean that there's a DELETE. When there are other operations, the pre-'nodesToProcess'
        // behavior will kick in (which would presumably lead to database constraint-related exceptions).
        operations.addLast( NodeOperation.create( node ) );

        // If the node that's created is the root node of a service, persist it immediately. Without this, the pubsub (pep) service
        // that is defined by this root node doesn't exist, which causes issues when attempting to create the service. OF-2016
        if ( node instanceof CollectionNode && node.getParent() == null) {
            flushPendingNode( node.getUniqueIdentifier() );
        }
    }

    @Override
    public void updateNode(Node node) {
        log.debug( "Updating node: {}", node.getUniqueIdentifier() );

        final Deque<NodeOperation> operations = nodesToProcess.computeIfAbsent( node.getUniqueIdentifier(), id -> new ConcurrentLinkedDeque<>() );

        // This update can replace any pending updates (since the last create/delete or affiliation change).
        final Iterator<NodeOperation> iter = operations.descendingIterator();
        while ( iter.hasNext() ) {
            final NodeOperation existing = iter.next();
            if ( existing.action.equals( NodeOperation.Action.UPDATE ) ) {
                iter.remove(); // This is replaced by the update that's being added.
            } else {
                break; // Operations that precede anything other than the last operations that are UPDATE shouldn't be replaced.
            }
        }
        operations.addLast( NodeOperation.update( node ));
    }

    @Override
    public void removeNode(Node node) {
        log.debug( "Removing node: {}", node.getUniqueIdentifier() );

        if ( node instanceof LeafNode ) {
            purgeNode( (LeafNode) node );
        }

        final Deque<NodeOperation> operations = nodesToProcess.computeIfAbsent( node.getUniqueIdentifier(), id -> new ConcurrentLinkedDeque<>() );
        final boolean hadCreate = operations.stream().anyMatch(operation -> operation.action.equals(NodeOperation.Action.CREATE));
        operations.removeIf(operation -> !operation.action.equals(NodeOperation.Action.REMOVE)); // Any previously recorded, but as of yet unsaved operations, can be skipped.

        if (!hadCreate) { // If one of the operations that have not been executed was a node create, we need not delete the node either.
            operations.addLast(NodeOperation.remove(node));
        }
    }

    @Override
    public void loadNodes(PubSubService service)
    {
        log.debug( "Loading nodes for service: {}", service.getUniqueIdentifier() );

        // Make sure that all changes to nodes have been written to the database
        // before the nodes are retrieved.
        flushPendingNodes( service.getUniqueIdentifier() );

        delegate.loadNodes( service );
    }

    @Override
    public void loadNode(PubSubService service, Node.UniqueIdentifier nodeIdentifier)
    {
        log.debug( "Loading node: {}", nodeIdentifier );

        // Make sure that all changes to nodes have been written to the database
        // before the nodes are retrieved.
        flushPendingNode( nodeIdentifier );

        delegate.loadNode( service, nodeIdentifier );
    }

    @Override
    public void loadSubscription(Node node, String subId)
    {
        flushPendingNode( node.getUniqueIdentifier() );

        delegate.loadSubscription(node, subId);
    }

    @Override
    @Nonnull
    public Set<Node.UniqueIdentifier> findDirectlySubscribedNodes(@Nonnull JID address) {
        flushPendingNodes();
        return delegate.findDirectlySubscribedNodes(address);
    }

    @Override
    public void createAffiliation(Node node, NodeAffiliate affiliate)
    {
        log.debug( "Creating node affiliation for {} (type: {}) on node {}", affiliate.getJID(), affiliate.getAffiliation(), node.getUniqueIdentifier() );
        final Deque<NodeOperation> operations = nodesToProcess.computeIfAbsent( node.getUniqueIdentifier(), id -> new ConcurrentLinkedDeque<>() );
        final NodeOperation operation = NodeOperation.createAffiliation( node, affiliate );
        operations.addLast( operation );
    }

    @Override
    public void updateAffiliation(Node node, NodeAffiliate affiliate)
    {
        log.debug( "Updating node affiliation for {} (type: {}) on node {}", affiliate.getJID(), affiliate.getAffiliation(), node.getUniqueIdentifier() );
        final Deque<NodeOperation> operations = nodesToProcess.computeIfAbsent( node.getUniqueIdentifier(), id -> new ConcurrentLinkedDeque<>() );
        // This affiliation update can replace any pending updates of the same affiliate (since the last create/delete of the node or affiliation change of this affiliate to the node).
        final Iterator<NodeOperation> iter = operations.descendingIterator();
        while ( iter.hasNext() ) {
            final NodeOperation existing = iter.next();
            if ( existing.action.equals( NodeOperation.Action.UPDATE_AFFILIATION ) ) {
                if ( affiliate.getJID().equals( existing.affiliate.getJID() ) ) {
                    iter.remove(); // This is replaced by the update that's being added.
                }
            } else {
                break; // Operations that precede anything other than the last operations that are UPDATE_AFFILIATE shouldn't be replaced.
            }
        }

        final NodeOperation operation = NodeOperation.updateAffiliation( node, affiliate );
        operations.addLast( operation );
    }

    @Override
    public void removeAffiliation(Node node, NodeAffiliate affiliate) {
        log.debug( "Removing node affiliation for {} (type: {}) on node {}", affiliate.getJID(), affiliate.getAffiliation(), node.getUniqueIdentifier() );
        final Deque<NodeOperation> operations = nodesToProcess.computeIfAbsent( node.getUniqueIdentifier(), id -> new ConcurrentLinkedDeque<>() );

        // This affiliation removal can replace any pending creation, update or delete of the same affiliate (since the last create/delete of the node or affiliation change of this affiliate to the node).
        boolean hadCreate = false;
        final Iterator<NodeOperation> iter = operations.descendingIterator();
        while ( iter.hasNext() ) {
            final NodeOperation existing = iter.next();
            if ( Arrays.asList( NodeOperation.Action.CREATE_AFFILIATION, NodeOperation.Action.UPDATE_AFFILIATION ).contains( existing.action ) ) {
                if ( affiliate.getJID().equals( existing.affiliate.getJID() ) ) {
                    if (existing.action.equals(NodeOperation.Action.CREATE_AFFILIATION)) {
                        hadCreate = true;
                    }
                    iter.remove(); // This is replaced by the update that's being added.
                }
            } else {
                break; // Operations that precede anything other than the last operations that are affiliate changes shouldn't be replaced.
            }
        }

        if (!hadCreate) { // If one of the operations that have not been executed was an affiliation create, we need not delete the affiliation either.
            operations.addLast(NodeOperation.removeAffiliation(node, affiliate));
        }
    }

    @Override
    public void createSubscription(Node node, NodeSubscription subscription) {
        log.debug( "Creating node subscription for owner {} to node {} (subscription ID: {})", subscription.getOwner(), node.getUniqueIdentifier(), subscription.getID() );

        final Deque<NodeOperation> operations = nodesToProcess.computeIfAbsent( node.getUniqueIdentifier(), id -> new ConcurrentLinkedDeque<>() );
        final NodeOperation operation = NodeOperation.createSubscription( node, subscription );
        operations.addLast( operation );
    }

    @Override
    public void updateSubscription(Node node, NodeSubscription subscription) {
        log.debug( "Updating node subscription for owner {} to node {} (subscription ID: {})", subscription.getOwner(), node.getUniqueIdentifier(), subscription.getID() );
        final Deque<NodeOperation> operations = nodesToProcess.computeIfAbsent( node.getUniqueIdentifier(), id -> new ConcurrentLinkedDeque<>() );

        // This subscription update can replace any pending updates of the same subscription (since the last create/delete of the node or subscription change of this affiliate to the node).
        final Iterator<NodeOperation> iter = operations.descendingIterator();
        while ( iter.hasNext() ) {
            final NodeOperation op = iter.next();
            if ( op.action.equals( NodeOperation.Action.UPDATE_SUBSCRIPTION ) ) {
                if ( subscription.getID().equals( op.subscription.getID() ) ) {
                    iter.remove(); // This is replaced by the update that's being added.
                }
            } else {
                break; // Operations that precede anything other than the last operations that are UPDATE_AFFILIATE shouldn't be replaced.
            }
        }

        final NodeOperation operation = NodeOperation.updateSubscription( node, subscription );
        operations.addLast( operation );
    }

    @Override
    public void removeSubscription(NodeSubscription subscription) {
        log.debug( "Removing node subscription for owner {} to node {} (subscription ID: {})", subscription.getOwner(), subscription.getNode().getUniqueIdentifier(), subscription.getID() );

        final Deque<NodeOperation> operations = nodesToProcess.computeIfAbsent( subscription.getNode().getUniqueIdentifier(), id -> new ConcurrentLinkedDeque<>() );

        // This subscription removal can replace any pending creation or update of the same subscription (since the last create/delete of the node or subscription change of this subscription to the node).
        boolean hadCreate = false;
        final Iterator<NodeOperation> iter = operations.descendingIterator();
        while ( iter.hasNext() ) {
            final NodeOperation operation = iter.next();
            if ( Arrays.asList( NodeOperation.Action.CREATE_SUBSCRIPTION, NodeOperation.Action.UPDATE_SUBSCRIPTION ).contains( operation.action ) ) {
                if ( subscription.getID().equals( operation.subscription.getID() ) ) {
                    if (operation.action == NodeOperation.Action.CREATE_SUBSCRIPTION) {
                        hadCreate = true;
                    }
                    iter.remove(); // This is replaced by the update that's being added.
                }
            } else {
                break; // Operations that precede anything other than the last operations that are subscription changes shouldn't be replaced.
            }
        }

        if (!hadCreate) { // If one of the operations that have not been executed was a subscription create, we need not delete the subscription either.
            operations.addLast(NodeOperation.removeSubscription(subscription.getNode(), subscription));
        }
    }

    private void process( final NodeOperation operation ) {
        switch ( operation.action )
        {
            case CREATE:
                delegate.createNode( operation.node );
                break;

            case UPDATE:
                delegate.updateNode( operation.node );
                break;

            case REMOVE:
                delegate.removeNode( operation.node );
                break;

            case CREATE_AFFILIATION:
                delegate.createAffiliation( operation.node, operation.affiliate );
                break;

            case UPDATE_AFFILIATION:
                delegate.updateAffiliation( operation.node, operation.affiliate );
                break;

            case REMOVE_AFFILIATION:
                delegate.removeAffiliation( operation.node, operation.affiliate );
                break;

            case CREATE_SUBSCRIPTION:
                delegate.createSubscription( operation.node, operation.subscription );
                break;

            case UPDATE_SUBSCRIPTION:
                delegate.updateSubscription( operation.node, operation.subscription );
                break;

            case REMOVE_SUBSCRIPTION:
                delegate.removeSubscription( operation.subscription );
                break;

            default:
                throw new IllegalStateException(); // This indicates a bug in the implementation of this class.
        }
    }


    @Override
    public void purgeNode( final LeafNode leafNode )
    {
        // If there are any pending items for this node, don't bother processing them.
        synchronized (itemsPending) {
            itemsPending.values().removeIf( publishedItem -> leafNode.getUniqueIdentifier().equals( publishedItem.getNode().getUniqueIdentifier() ) );
            itemsToAdd.remove(leafNode.getUniqueIdentifier());
            itemsToDelete.remove(leafNode.getUniqueIdentifier());
        }

        // drop cached items for purged node
        synchronized (itemCache)
        {
            for (PublishedItem item : itemCache.values())
            {
                if (leafNode.getUniqueIdentifier().equals(item.getNode().getUniqueIdentifier()))
                {
                    itemCache.remove(item.getUniqueIdentifier());
                }
            }
        }
        delegate.purgeNode( leafNode );
    }

    @Override
    public void savePublishedItem(PublishedItem item) {
        final Node.UniqueIdentifier nodeKey = item.getNode().getUniqueIdentifier();
        final PublishedItem.UniqueIdentifier itemKey = item.getUniqueIdentifier();
        log.debug( "Saving published item {} {}", nodeKey, item.getID() );

        itemCache.put(itemKey, item);
        log.debug("Added new (inbound) item to cache");
        synchronized (itemsPending) {
            // Remove any earlier 'adds' (this new one will replace those).
            final Deque<PublishedItem> adds = itemsToAdd.get(nodeKey);
            if (adds != null) {
                adds.removeIf(i -> i.getUniqueIdentifier().equals(itemKey));
            }

            // Add to per-node add queue
            itemsToAdd.computeIfAbsent(nodeKey, k -> new ArrayDeque<>()).addLast(item);

            // Remove from delete queue (as we're now storing a new item with that ID, that one shouldn't be removed)
            final Deque<PublishedItem> deletes = itemsToDelete.get(nodeKey);
            if (deletes != null) {
                deletes.removeIf(i -> i.getUniqueIdentifier().equals(itemKey));
                if (deletes.isEmpty()) {
                    itemsToDelete.remove(nodeKey);
                }
            }

            itemsPending.put(itemKey, item);
        }

        if (itemsPending.size() > MAX_ITEMS_FLUSH) {
            TaskEngine.getInstance().submit(() -> flushPendingChanges(false));
        }
    }

    public void flushPendingChanges( Node.UniqueIdentifier nodeUniqueId )
    {
        flushPendingChanges(nodeUniqueId, ClusterManager.isClusteringEnabled());
    }

    public void flushPendingChanges( Node.UniqueIdentifier nodeUniqueId, boolean sendToCluster )
    {
        // forward to other cluster members and wait for response
        if (sendToCluster) {
            CacheFactory.doSynchronousClusterTask( new FlushTask(nodeUniqueId), false);
        }

        // TODO: figure out if it's required to first flush pending nodes, cluster-wide, synchronously, before flushing items.
        flushPendingNode(nodeUniqueId);

        if (itemsToAdd.isEmpty() && itemsToDelete.isEmpty()) {
            return;	 // nothing left to do for this cluster member.
        }

        List<PublishedItem> addList;
        List<PublishedItem> delList;

        synchronized(itemsPending)
        {
            final Deque<PublishedItem> adds = itemsToAdd.remove(nodeUniqueId);
            final Deque<PublishedItem> deletes = itemsToDelete.remove(nodeUniqueId);

            addList = adds == null ? Collections.emptyList() : new ArrayList<>(adds);
            delList = deletes == null ? Collections.emptyList() : new ArrayList<>(deletes);

            // Remove flushed items from itemsPending map
            addList.forEach(i -> itemsPending.remove(i.getUniqueIdentifier()));
            delList.forEach(i -> itemsPending.remove(i.getUniqueIdentifier()));

            // Ensure pending items are available via the item read cache;
            // this allows the item(s) to be fetched by other request threads
            // while being written to the DB from this thread
            // TODO Determine if this works as intended when items are being queued for adding as well as removal.
            int copied = 0;
            for (final PublishedItem itemToAdd : addList) {
                final PublishedItem.UniqueIdentifier key = itemToAdd.getUniqueIdentifier();
                if (!itemCache.containsKey(key)) {
                    itemCache.put(key, itemToAdd);
                    copied++;
                }
            }
            if (log.isDebugEnabled() && copied > 0) {
                log.debug("Added " + copied + " pending items to published item cache");
            }
        }

        delegate.bulkPublishedItems( addList, delList );
    }

    /**
     * Persists any changes that have been applied to the caches by invoking the relevant methods of the delegate.
     *
     * A flush can be performed either local (typically used to periodically persist data) or cluster-wide (useful to
     * ensure that a particular state is reached, cluster-wide).
     *
     * @param sendToCluster set to 'true' to trigger a flush on all cluster nodes. If false, a flush will occur only on
     *                      the local node.
     */
    public void flushPendingChanges( boolean sendToCluster )
    {
        // forward to other cluster members and wait for response
        if (sendToCluster) {
            CacheFactory.doSynchronousClusterTask(new FlushTask(), false);
        }

        // TODO: figure out if it's required to first flush pending nodes, cluster-wide, synchronously, before flushing items.
        flushPendingNodes();

        if (itemsToAdd.isEmpty() && itemsToDelete.isEmpty()) {
            return;	 // Nothing left to do for this cluster member.
        }

        // Flush per node. Create a snapshot to avoid concurrent modification exceptions.
        for (final Node.UniqueIdentifier nodeId : new ArrayList<>(itemsToAdd.keySet())) {
            flushPendingChanges(nodeId, sendToCluster);
        }
        for (final Node.UniqueIdentifier nodeId : new ArrayList<>(itemsToDelete.keySet())) {
            flushPendingChanges(nodeId, sendToCluster);
        }
    }

    @Override
    public void removePublishedItem(PublishedItem item) {
        final PublishedItem.UniqueIdentifier itemKey = item.getUniqueIdentifier();
        final Node.UniqueIdentifier nodeKey = item.getNode().getUniqueIdentifier();
        log.debug( "Removing published item {} {}", nodeKey, item.getID() );

        itemCache.remove(itemKey);
        synchronized (itemsPending) {
            // Remove any earlier 'deletes' (this new one will replace those).
//            final Deque<PublishedItem> deletes = itemsToDelete.get(nodeKey);
//            if (deletes != null) {
//                deletes.removeIf(i -> i.getUniqueIdentifier().equals(itemKey));
//            }

            // Add to per-node delete queue
            itemsToDelete.computeIfAbsent(nodeKey, k -> new ArrayDeque<>()).addLast(item);

            // Remove from add queue (as we're now deleting an item with that ID, that one shouldn't be added)
            final Deque<PublishedItem> adds = itemsToAdd.get(nodeKey);
            if (adds != null) {
                adds.removeIf(i -> i.getUniqueIdentifier().equals(itemKey));
                if (adds.isEmpty()) {
                    itemsToAdd.remove(nodeKey);
                }
            }

            itemsPending.remove(itemKey);
        }
    }

    @Override
    public PEPService loadPEPServiceFromDB( final JID jid )
    {
        return delegate.loadPEPServiceFromDB( jid );
    }

    @Override
    public void bulkPublishedItems( final List<PublishedItem> addList, final List<PublishedItem> delList )
    {
        addList.removeAll( delList );
        delList.forEach( this::removePublishedItem );
        addList.forEach( this::savePublishedItem );
    }

    @Override
    public DefaultNodeConfiguration loadDefaultConfiguration( final PubSubService.UniqueIdentifier serviceIdentifier, final boolean isLeafType )
    {
        return delegate.loadDefaultConfiguration( serviceIdentifier, isLeafType );
    }

    @Override
    public void createDefaultConfiguration( final PubSubService.UniqueIdentifier serviceIdentifier, final DefaultNodeConfiguration config )
    {
        delegate.createDefaultConfiguration( serviceIdentifier, config );
    }

    @Override
    public void updateDefaultConfiguration( final PubSubService.UniqueIdentifier serviceIdentifier, final DefaultNodeConfiguration config )
    {
        delegate.updateDefaultConfiguration( serviceIdentifier, config );
    }

    @Override
    public List<PublishedItem> getPublishedItems( final LeafNode node )
    {
        flushPendingChanges( node.getUniqueIdentifier() );
        return delegate.getPublishedItems( node );
    }

    @Override
    public List<PublishedItem> getPublishedItems( final LeafNode node, final int maxRows )
    {
        flushPendingChanges( node.getUniqueIdentifier() );
        return delegate.getPublishedItems( node, maxRows );
    }

    @Override
    public PublishedItem getLastPublishedItem( final LeafNode node )
    {
        flushPendingChanges( node.getUniqueIdentifier() );
        return delegate.getLastPublishedItem( node );
    }

    @Override
    public PublishedItem getPublishedItem( final LeafNode node, final PublishedItem.UniqueIdentifier itemIdentifier )
    {
        flushPendingChanges( node.getUniqueIdentifier() );

        // try to fetch from cache first without locking
        PublishedItem result = itemCache.get(itemIdentifier);
        if (result == null) {
            Lock itemLock = itemCache.getLock( itemIdentifier );
            itemLock.lock();
            try {
                // Acquire lock, then re-check cache before reading from DB;
                // allows clustered item cache to be primed by first request
                result = itemCache.get(itemIdentifier);
                if (result == null) {
                    log.trace("No cached item found. Obtaining it from delegate. Item identifier: {}", itemIdentifier);
                    result = delegate.getPublishedItem( node, itemIdentifier );
                    if (result != null) {
                        log.trace("Caching item obtained from delegate.");
                        itemCache.put(itemIdentifier, result);
                    } else {
                        log.trace("Delegate doesn't have an item. It does not appear to exist.");
                    }
                } else {
                    log.trace("Found cached item on second attempt (after acquiring lock). Item identifier: {}", itemIdentifier);
                }

            } finally {
                itemLock.unlock();
            }
        } else {
            log.trace("Found cached item on first attempt (no lock). Item identifier: {}", itemIdentifier);
        }
        log.debug( "Item for item identifier {} was {} on node {}", itemIdentifier, result == null ? "not found" : "found", node.getUniqueIdentifier() );
        return result;
    }

    static class NodeOperation {

        enum Action {
            CREATE,
            UPDATE,
            REMOVE,
            CREATE_AFFILIATION,
            UPDATE_AFFILIATION,
            REMOVE_AFFILIATION,
            CREATE_SUBSCRIPTION,
            UPDATE_SUBSCRIPTION,
            REMOVE_SUBSCRIPTION
        }

        final Node node;
        final Action action;
        final NodeAffiliate affiliate;
        final NodeSubscription subscription;

        private NodeOperation( final Node node, final Action action, final NodeAffiliate affiliate, final NodeSubscription subscription ) {
            if ( node == null ) {
                throw new IllegalArgumentException( "Argument 'node' cannot be null." );
            }
            if ( action == null ) {
                throw new IllegalArgumentException( "Argument 'action' cannot be null." );
            }
            if ( affiliate == null && Arrays.asList( Action.CREATE_AFFILIATION, Action.UPDATE_AFFILIATION, Action.REMOVE_AFFILIATION ).contains( action ) ) {
                throw new IllegalArgumentException( "Argument 'affiliate' cannot be null when 'action' is " + action );
            }
            if ( subscription == null && Arrays.asList( Action.CREATE_SUBSCRIPTION, Action.UPDATE_SUBSCRIPTION, Action.REMOVE_SUBSCRIPTION ).contains( action ) ) {
                throw new IllegalArgumentException( "Argument 'subscription' cannot be null when 'action' is " + action );
            }
            this.node = node;
            this.action = action;
            this.affiliate = affiliate;
            this.subscription = subscription;
        }

        static NodeOperation create( final Node node ) {
            return new NodeOperation( node, Action.CREATE, null, null );
        }
        static NodeOperation update( final Node node ) {
            return new NodeOperation( node, Action.UPDATE, null, null );
        }
        static NodeOperation remove( final Node node ) {
            return new NodeOperation( node, Action.REMOVE, null, null );
        }
        static NodeOperation createAffiliation( final Node node, final NodeAffiliate affiliate ) {
            return new NodeOperation( node, Action.CREATE_AFFILIATION, affiliate, null );
        }
        static NodeOperation updateAffiliation( final Node node, final NodeAffiliate affiliate ) {
            return new NodeOperation( node, Action.UPDATE_AFFILIATION, affiliate, null );
        }
        static NodeOperation removeAffiliation( final Node node, final NodeAffiliate affiliate ) {
            return new NodeOperation( node, Action.REMOVE_AFFILIATION, affiliate, null );
        }
        static NodeOperation createSubscription( final Node node, final NodeSubscription subscription ) {
            return new NodeOperation( node, Action.CREATE_SUBSCRIPTION, null, subscription);
        }
        static NodeOperation updateSubscription( final Node node, final NodeSubscription subscription ) {
            return new NodeOperation( node, Action.UPDATE_SUBSCRIPTION, null, subscription );
        }
        static NodeOperation removeSubscription( final Node node, final NodeSubscription subscription ) {
            return new NodeOperation( node, Action.REMOVE_SUBSCRIPTION, null, subscription );
        }
    }
}
