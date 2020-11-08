/*
 * Copyright (C) 2019 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.pep.PEPService;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.jivesoftware.util.cache.CacheUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

/**
 * An memory-based PubSub persistence provider.
 *
 * Note that any data stored in this provider will not survive a restart of the JVM.
 */
public class InMemoryPubSubPersistenceProvider implements PubSubPersistenceProvider
{
    private static final Logger log = LoggerFactory.getLogger( InMemoryPubSubPersistenceProvider.class );

    /**
     * Cache for default configurations
     */
    private final Cache<String, DefaultNodeConfiguration> defaultNodeConfigurationCache;

    /**
     * Cache that holds all nodes (mapped by service ID).
     */
    private final Cache<PubSubService.UniqueIdentifier, ArrayList<Node>> serviceIdToNodesCache;

    /**
     * Cache that holds all published items (mapped by node ID).
     */
    private final Cache<Node.UniqueIdentifier, LinkedList<PublishedItem>> itemsCache;

    public InMemoryPubSubPersistenceProvider()
    {
        // This implementation provides an in-memory only store of data. The caches are used to store all of the data,
        // which makes it crucial to avoid cache entries from being purged. Note that the caches from DefaultPubSubPersistenceProvider
        // cannot be re-used for the same reason: data is not stored in those caches indefinitely.
        defaultNodeConfigurationCache = CacheFactory.createCache( "Pubsub InMemory Default Node Config" );
        defaultNodeConfigurationCache.setMaxCacheSize( -1L );
        defaultNodeConfigurationCache.setMaxLifetime( -1L );

        serviceIdToNodesCache = CacheFactory.createCache( "Pubsub InMemory Nodes" );
        serviceIdToNodesCache.setMaxCacheSize( -1L );
        serviceIdToNodesCache.setMaxLifetime( -1L );

        itemsCache = CacheFactory.createCache( "Pubsub InMemory Published Items" );
        itemsCache.setMaxCacheSize( -1L );
        itemsCache.setMaxLifetime( -1L );
    }

    public void initialize()
    {
        log.debug( "Initializing" );
    }

    public void shutdown()
    {
        log.debug( "Shutting down" );
    }

    @Override
    public void createNode( Node node )
    {
        log.debug( "Creating node: {}", node.getUniqueIdentifier() );
        CacheUtil.addValueToMultiValuedCache( serviceIdToNodesCache, node.getUniqueIdentifier().getServiceIdentifier(), node, ArrayList::new );
    }

    @Override
    public void updateNode( Node node )
    {
        log.debug( "Updating node: {}", node.getUniqueIdentifier() );

        final PubSubService.UniqueIdentifier serviceIdentifier = node.getUniqueIdentifier().getServiceIdentifier();
        final Lock lock = serviceIdToNodesCache.getLock(serviceIdentifier);
        lock.lock();
        try {
            CacheUtil.removeValueFromMultiValuedCache( serviceIdToNodesCache, serviceIdentifier, node );
            CacheUtil.addValueToMultiValuedCache( serviceIdToNodesCache, serviceIdentifier, node, ArrayList::new );
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void removeNode( Node node )
    {
        log.debug( "Removing node: {}", node.getUniqueIdentifier() );

        final PubSubService.UniqueIdentifier serviceIdentifier = node.getUniqueIdentifier().getServiceIdentifier();
        final Lock lock = serviceIdToNodesCache.getLock( serviceIdentifier );
        lock.lock();
        try {
            serviceIdToNodesCache.computeIfPresent( serviceIdentifier, ( s, list ) -> {
                list.remove( node );
                return list.isEmpty() ? null : list;
            } );
            if ( node instanceof LeafNode )
            {
                purgeNode( (LeafNode) node );
            }

            if ( node instanceof CollectionNode && node.getParent() == null ) {
                log.debug( "Node is the root node. Also removing other service references." );
                this.defaultNodeConfigurationCache.remove( getDefaultNodeConfigurationCacheKey( serviceIdentifier, true ) );
                this.defaultNodeConfigurationCache.remove( getDefaultNodeConfigurationCacheKey( serviceIdentifier, false ) );
                final Set<Node.UniqueIdentifier> toBeDeletedItemIds = this.itemsCache.keySet().stream().filter(key -> key.getServiceIdentifier().equals(serviceIdentifier)).collect(Collectors.toSet());
                for ( final Node.UniqueIdentifier itemId : toBeDeletedItemIds ) {
                    this.itemsCache.remove( itemId );
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void loadNodes( PubSubService service )
    {
        log.debug( "Loading nodes for service: {}", service.getServiceID() );

        final Lock lock = serviceIdToNodesCache.getLock( service.getUniqueIdentifier() );
        lock.lock();
        try {
            final List<Node> nodes = serviceIdToNodesCache.get(service.getUniqueIdentifier());
            if ( nodes != null )
            {
                nodes.forEach(service::addNode);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void loadNode( PubSubService service, Node.UniqueIdentifier nodeIdentifier )
    {
        log.debug( "Loading node: {}", nodeIdentifier );

        final Lock lock = serviceIdToNodesCache.getLock( service.getUniqueIdentifier() );
        lock.lock();
        try {
            final List<Node> nodes = serviceIdToNodesCache.get(service.getUniqueIdentifier());
            if ( nodes != null )
            {
                final Optional<Node> optionalNode = nodes.stream().filter(node -> node.getUniqueIdentifier().equals(nodeIdentifier)).findAny();
                optionalNode.ifPresent(service::addNode);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void loadSubscription( Node node, String subId )
    {
        // Affiliate change should already be stored in the node.
        log.debug( "Loading subscription {} for node: {} (NOP)", subId, node.getUniqueIdentifier() );
    }

    @Override
    @Nonnull
    public Set<Node.UniqueIdentifier> findDirectlySubscribedNodes(@Nonnull JID address) {
        log.debug( "Finding all nodes to which {} is subscribed", address );
        final Set<Node.UniqueIdentifier> result = new HashSet<>();
        for ( final List<Node> nodes : serviceIdToNodesCache.values() ) {
            for ( final Node node : nodes ) {
                if ( node.getSubscriptions().stream().anyMatch( s ->
                        s.isActive() &&
                        (s.getJID().equals(address) || s.getJID().equals(address.asBareJID()) )
                )) {
                    result.add(node.getUniqueIdentifier());
                }
            }
        }
        return result;
    }

    @Override
    public void createAffiliation( Node node, NodeAffiliate affiliate )
    {
        // Affiliate change should already be stored in the node.
        log.debug( "Creating node affiliation (NOP): {} {}", node.getUniqueIdentifier(), affiliate.toString() );
    }

    @Override
    public void updateAffiliation( Node node, NodeAffiliate affiliate )
    {
        // Affiliate change should already be stored in the node.
        log.debug( "Updating node affiliation (NOP): {} {}", node.getUniqueIdentifier(), affiliate.toString() );
    }

    @Override
    public void removeAffiliation( Node node, NodeAffiliate affiliate )
    {
        // Affiliate change should already be stored in the node.
        log.debug( "Removing node affiliation (NOP): {} {}", node.getUniqueIdentifier(), affiliate.toString() );
    }

    @Override
    public void createSubscription( Node node, NodeSubscription subscription )
    {
        // Subscription change should already be stored in the node.
        log.debug( "Creating node subscription (NOP): {} {}", node.getUniqueIdentifier(), subscription.getID() );
    }

    @Override
    public void updateSubscription( Node node, NodeSubscription subscription )
    {
        // Subscription change should already be stored in the node.
        log.debug( "Updating node subscription (NOP): {} {}", node.getUniqueIdentifier(), subscription.getID() );
    }

    @Override
    public void removeSubscription( NodeSubscription subscription )
    {
        // Subscription change should already be stored in the node.
        log.debug( "Removing node subscription (NOP): {} {}", subscription.getNode().getUniqueIdentifier(), subscription.getID() );
    }

    // This mimics the cache usage as pre-existed in DefaultPubSubPersenceProvider.
    private static String getDefaultNodeConfigurationCacheKey( PubSubService.UniqueIdentifier serviceIdentifier, boolean isLeafType )
    {
        return serviceIdentifier.getServiceId() + "|" + isLeafType;
    }

    @Override
    public DefaultNodeConfiguration loadDefaultConfiguration( PubSubService.UniqueIdentifier serviceIdentifier, boolean isLeafType )
    {
        log.debug( "Loading default node configuration for service {} (for leaf type: {}).", serviceIdentifier, isLeafType );
        final String key = getDefaultNodeConfigurationCacheKey( serviceIdentifier, isLeafType );
        return defaultNodeConfigurationCache.get( key );
    }

    @Override
    public void createDefaultConfiguration( PubSubService.UniqueIdentifier serviceIdentifier, DefaultNodeConfiguration config )
    {
        log.debug( "Creating default node configuration for service {} (for leaf type: {}).", serviceIdentifier, config.isLeaf() );
        final String key = getDefaultNodeConfigurationCacheKey( serviceIdentifier, config.isLeaf() );
        defaultNodeConfigurationCache.put( key, config );
    }

    @Override
    public void updateDefaultConfiguration( PubSubService.UniqueIdentifier serviceIdentifier, DefaultNodeConfiguration config )
    {
        log.debug( "Updating default node configuration for service {} (for leaf type: {}).", serviceIdentifier, config.isLeaf() );
        final String key = getDefaultNodeConfigurationCacheKey( serviceIdentifier, config.isLeaf() );
        defaultNodeConfigurationCache.put( key, config );
    }

    @Override
    public void savePublishedItem( PublishedItem item )
    {
        log.debug( "Saving published item for node {}: {}", item.getNode().getUniqueIdentifier(), item.getID() );
        final Lock lock = itemsCache.getLock(item.getNode().getUniqueIdentifier());
        lock.lock();
        try {

            // Find and remove an item with the same ID, if one is present.
            final LinkedList<PublishedItem> allNodeItems = itemsCache.get(item.getNode().getUniqueIdentifier());
            if (allNodeItems != null) {
                final Optional<PublishedItem> oldItem = allNodeItems.stream().filter(i -> i.getUniqueIdentifier().equals(item.getUniqueIdentifier())).findAny();
                oldItem.ifPresent(publishedItem -> CacheUtil.removeValueFromMultiValuedCache(itemsCache, item.getNode().getUniqueIdentifier(), publishedItem));
            }

            // Add the new item.
            CacheUtil.addValueToMultiValuedCache( itemsCache, item.getNode().getUniqueIdentifier(), item, LinkedList::new );
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void removePublishedItem( PublishedItem item )
    {
        log.debug( "Removing published item for node {}: {}", item.getNode().getUniqueIdentifier(), item.getID() );
        CacheUtil.removeValueFromMultiValuedCache( itemsCache, item.getNode().getUniqueIdentifier(), item );
    }

    @Override
    public List<PublishedItem> getPublishedItems( LeafNode node )
    {
        log.debug( "Getting published items for node {}", node.getUniqueIdentifier() );
        List<PublishedItem> publishedItems;
        final Lock lock = itemsCache.getLock( node.getUniqueIdentifier() );
        lock.lock();
        try {
            final List<PublishedItem> items = itemsCache.get( node.getUniqueIdentifier() );
            publishedItems = items != null ? items : new ArrayList<>();
        } finally {
            lock.unlock();
        }

        return publishedItems;
    }

    @Override
    public List<PublishedItem> getPublishedItems( LeafNode node, int maxRows )
    {
        log.debug( "Getting published items for node {} (max: {}).", node.getUniqueIdentifier(), maxRows );
        List<PublishedItem> publishedItems = getPublishedItems( node );
        if ( publishedItems.size() > maxRows )
        {
            publishedItems = publishedItems.subList( publishedItems.size() - maxRows, publishedItems.size() );
        }
        return publishedItems;
    }

    @Override
    public PublishedItem getLastPublishedItem( LeafNode node )
    {
        log.debug( "Getting last published item for node {}", node.getUniqueIdentifier() );
        final List<PublishedItem> publishedItems = getPublishedItems( node );
        if ( publishedItems != null && !publishedItems.isEmpty() ) {
            return publishedItems.get( publishedItems.size() - 1 );
        }

        return null;
    }

    @Override
    public PublishedItem getPublishedItem( LeafNode node, PublishedItem.UniqueIdentifier itemIdentifier )
    {
        log.debug( "Getting published item {} for node {}", itemIdentifier.getItemId(), node.getUniqueIdentifier() );

        PublishedItem lastPublishedItem = null;
        final List<PublishedItem> publishedItems = getPublishedItems( node );
        if ( publishedItems != null )
        {
            final List<PublishedItem> collect = publishedItems.stream().filter( publishedItem -> publishedItem.getID().equals( itemIdentifier.getItemId() ) ).collect( Collectors.toList() );
            if ( !collect.isEmpty() )
            {
                if ( collect.size() > 1 )
                {
                    log.warn( "Detected duplicate item key " + itemIdentifier.getItemId() + " usage for node " + node.getUniqueIdentifier().toString() );
                }
                lastPublishedItem = collect.get( collect.size() - 1 );
            }
        }
        return lastPublishedItem;
    }

    @Deprecated
    public void saveSubscription( Node node, NodeSubscription subscription, boolean create )
    {
        if ( create )
        {
            createSubscription( node, subscription );
        }
        else
        {
            updateSubscription( node, subscription );
        }
    }

    @Deprecated
    public void saveAffiliation( Node node, NodeAffiliate affiliate, boolean create )
    {
        if ( create )
        {
            createAffiliation( node, affiliate );
        }
        else
        {
            updateAffiliation( node, affiliate );
        }
    }

    @Override
    public void purgeNode( LeafNode leafNode )
    {
        Node.UniqueIdentifier uid = leafNode.getUniqueIdentifier();
        log.debug( "Purging node {}", uid );

        final Lock lock = itemsCache.getLock( leafNode.getUniqueIdentifier() );
        lock.lock();
        try {
            itemsCache.remove( leafNode.getUniqueIdentifier() );
        } finally {
            lock.unlock();
        }

        CacheUtil.removeValueFromMultiValuedCache( serviceIdToNodesCache, leafNode );
    }

    @Override
    public PEPService loadPEPServiceFromDB(JID jid)
    {
        final PubSubService.UniqueIdentifier id = new PubSubService.UniqueIdentifier( jid.toString() );
        final Lock lock = serviceIdToNodesCache.getLock( id );
        lock.lock();
        try {
            if ( serviceIdToNodesCache.containsKey( id ) ) {
                final PEPService pepService = new PEPService( XMPPServer.getInstance(), jid );
                pepService.initialize();

                // The JDBC variant stores subscriptions in the database. The in-memory variant cannot rely on this.
                // Subscriptions have to be repopulated from the roster instead.
                XMPPServer.getInstance().getIQPEPHandler().addSubscriptionForRosterItems( pepService );

                return pepService;
            } else {
                return null;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void bulkPublishedItems( final List<PublishedItem> addList, final List<PublishedItem> delList )
    {
        addList.removeAll( delList );
        delList.forEach( this::removePublishedItem );
        addList.forEach( this::savePublishedItem );
    }
}
