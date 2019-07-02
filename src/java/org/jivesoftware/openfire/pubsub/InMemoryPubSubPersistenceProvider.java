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

import org.jivesoftware.openfire.cluster.ClusterManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * An memory-based PubSub persistence provider.
 *
 * Note that any data stored in this provider will not survive a restart of the JVM.
 */
// FIXME: make compatible with clustering.
public class InMemoryPubSubPersistenceProvider implements PubSubPersistenceProvider
{
    private static final Logger log = LoggerFactory.getLogger( InMemoryPubSubPersistenceProvider.class );

    private final ConcurrentHashMap<Node.UniqueIdentifier, Node> uidToNodeMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Node.UniqueIdentifier>> serviceIdToNodeIdsMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DefaultNodeConfiguration> serviceIdToDefaultLeafNodeConfigMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DefaultNodeConfiguration> serviceIdToDefaultNodeConfigMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Node.UniqueIdentifier, List<PublishedItem>> uidToPublishedItemMap = new ConcurrentHashMap<>();

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

        synchronized ( node.getUniqueIdentifier().toString().intern() )
        {
            uidToNodeMap.put( node.getUniqueIdentifier(), node );
            serviceIdToNodeIdsMap.compute( node.getService().getServiceID(), ( s, list ) -> {
                if ( list != null )
                {
                    if ( !list.contains( node.getUniqueIdentifier() ) )
                    {
                        list.add( node.getUniqueIdentifier() );
                    }
                    return list;
                }
                else
                {
                    List<Node.UniqueIdentifier> newList = new ArrayList<>();
                    newList.add( node.getUniqueIdentifier() );
                    return newList;
                }
            } );
        }
    }

    @Override
    public void updateNode( Node node )
    {
        log.debug( "Updating node: {}", node.getUniqueIdentifier() );

        synchronized ( node.getUniqueIdentifier().toString().intern() )
        {
            uidToNodeMap.put( node.getUniqueIdentifier(), node );
        }
    }

    @Override
    public void removeNode( Node node )
    {
        log.debug( "Removing node: {}", node.getUniqueIdentifier() );

        synchronized ( node.getUniqueIdentifier().toString().intern() )
        {
            uidToNodeMap.remove( node.getUniqueIdentifier() );
            try
            {
                serviceIdToNodeIdsMap.computeIfPresent( node.getService().getServiceID(), ( s, list ) -> {
                    list.remove( node.getUniqueIdentifier() );
                    return list.isEmpty() ? null : list;
                } );
                if ( node instanceof LeafNode )
                {
                    purgeNode( (LeafNode) node );
                }
            }
            catch ( Exception e )
            {
                log.warn( "MemOnlyPubSubPersistenceManager removeNode " + node.getUniqueIdentifier().toString() + "  unexpected exception.", e );
            }
        }
    }

    @Override
    public void loadNodes( PubSubService service )
    {
        log.debug( "Loading nodes for service: {}", service.getServiceID() );

        final List<Node.UniqueIdentifier> listUniqueIds = serviceIdToNodeIdsMap.get( service.getServiceID() );
        if ( listUniqueIds != null )
        {
            for ( Node.UniqueIdentifier uniqueId : listUniqueIds )
            {
                synchronized ( uniqueId.toString().intern() )
                {
                    Node node = uidToNodeMap.get( uniqueId );
                    if ( node != null )
                    {
                        service.addNode( node );
                    }
                }
            }
        }
    }

    @Override
    public void loadNode( PubSubService service, String nodeId )
    {
        final Node.UniqueIdentifier uniqueIdentifier = new Node.UniqueIdentifier( service.getServiceID(), nodeId );
        log.debug( "Loading node: {}", uniqueIdentifier );
        synchronized ( uniqueIdentifier.toString().intern() )
        {
            final Node node = uidToNodeMap.get( uniqueIdentifier );
            if ( node != null )
            {
                service.addNode( node );
            }
        }
    }

    @Override
    public void loadSubscription( PubSubService service, Node node, String subId )
    {
        // Affiliate change should already be stored in the node.
        log.debug( "Loading subscription {} for node: {} (NOP)", subId, node.getUniqueIdentifier() );
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

    @Override
    public void flushPendingItems( Node.UniqueIdentifier nodeUniqueId )
    {
        log.debug( "Flushing pending items for node {} (NOP)", nodeUniqueId );
        // Do nothing. In-memory state does not need 'flushing' to a persistent backend.
    }

    @Override
    public void flushPendingItems()
    {
        log.debug( "Flushing all pending items (NOP)." );
        // Do nothing. In-memory state does not need 'flushing' to a persistent backend.
    }

    @Override
    public void flushPendingItems( Node.UniqueIdentifier nodeUniqueId, boolean sendToCluster )
    {
        log.debug( "Flushing pending items for node {} (send to cluster: {}) (NOP)", nodeUniqueId, sendToCluster );
        // Do nothing. In-memory state does not need 'flushing' to a persistent backend.
    }

    @Override
    public void flushPendingItems( boolean sendToCluster )
    {
        log.debug( "Flushing all pending items (send to cluster: {}) (NOP).", sendToCluster );
        // Do nothing. In-memory state does not need 'flushing' to a persistent backend.
    }

    @Override
    public DefaultNodeConfiguration loadDefaultConfiguration( PubSubService service, boolean isLeafType )
    {
        log.debug( "Loading default node configuration for service {} (for leaf type: {}).", service.getServiceID(), isLeafType );

        if ( isLeafType )
        {
            return serviceIdToDefaultLeafNodeConfigMap.get( service.getServiceID() );
        }
        return serviceIdToDefaultNodeConfigMap.get( service.getServiceID() );
    }

    @Override
    public void createDefaultConfiguration( PubSubService service, DefaultNodeConfiguration config )
    {
        log.debug( "Creating default node configuration for service {} (for leaf type: {}).", service.getServiceID(), config.isLeaf() );

        if ( config.isLeaf() )
        {
            serviceIdToDefaultLeafNodeConfigMap.put( service.getServiceID(), config );
        }
        else
        {
            serviceIdToDefaultNodeConfigMap.put( service.getServiceID(), config );
        }
    }

    @Override
    public void updateDefaultConfiguration( PubSubService service, DefaultNodeConfiguration config )
    {
        log.debug( "Updating default node configuration for service {} (for leaf type: {}).", service.getServiceID(), config.isLeaf() );
        if ( config.isLeaf() )
        {
            serviceIdToDefaultLeafNodeConfigMap.put( service.getServiceID(), config );
        }
        else
        {
            serviceIdToDefaultNodeConfigMap.put( service.getServiceID(), config );
        }
    }

    @Override
    public void savePublishedItem( PublishedItem item )
    {
        log.debug( "Saving published item for node {}: {}", item.getNode().getUniqueIdentifier(), item.getID() );
        synchronized ( item.getNode().getUniqueIdentifier().toString().intern() )
        {
            uidToPublishedItemMap.compute( item.getNode().getUniqueIdentifier(), ( s, list ) -> {
                if ( list != null )
                {
                    if ( list.stream().noneMatch( i -> item.getItemKey().equals( i.getItemKey() ) ) )
                    {
                        list.add( item );
                    }
                    return list;
                }
                else
                {
                    List<PublishedItem> newList = new ArrayList<>();
                    newList.add( item );
                    return newList;
                }
            } );
        }
    }

    @Override
    public void removePublishedItem( PublishedItem item )
    {
        log.debug( "Removing published item for node {}: {}", item.getNode().getUniqueIdentifier(), item.getID() );
        synchronized ( item.getNode().getUniqueIdentifier().toString().intern() )
        {
            uidToPublishedItemMap.compute( item.getNode().getUniqueIdentifier(), ( s, list ) -> {
                if ( list != null )
                {
                    list.removeIf( publishedItem -> item.getItemKey().equals( publishedItem.getItemKey() ) );
                }
                return list;
            } );
        }
    }

    @Override
    public List<PublishedItem> getPublishedItems( LeafNode node )
    {
        log.debug( "Getting published items for node {}", node.getUniqueIdentifier() );
        List<PublishedItem> publishedItems;
        synchronized ( node.getUniqueIdentifier().toString().intern() )
        {
            final List<PublishedItem> items = uidToPublishedItemMap.get( node.getUniqueIdentifier() );
            publishedItems = items != null ? items : new ArrayList<>();
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
        PublishedItem lastPublishedItem = null;
        synchronized ( node.getUniqueIdentifier().toString().intern() )
        {
            final List<PublishedItem> publishedItems = uidToPublishedItemMap.get( node.getUniqueIdentifier() );
            if ( publishedItems != null && !publishedItems.isEmpty() )
            {
                lastPublishedItem = publishedItems.get( publishedItems.size() - 1 );
            }
        }
        return lastPublishedItem;
    }

    @Override
    public PublishedItem getPublishedItem( LeafNode node, String itemID )
    {
        log.debug( "Getting published item {} for node {}", itemID, node.getUniqueIdentifier() );

        PublishedItem lastPublishedItem = null;
        synchronized ( node.getUniqueIdentifier().toString().intern() )
        {
            final List<PublishedItem> publishedItems = uidToPublishedItemMap.get( node.getUniqueIdentifier() );
            if ( publishedItems != null )
            {
                final List<PublishedItem> collect = publishedItems.stream().filter( publishedItem -> publishedItem.getItemKey().equals( itemID ) ).collect( Collectors.toList() );
                if ( !collect.isEmpty() )
                {
                    if ( collect.size() > 1 )
                    {
                        log.warn( "Detected duplicate item key " + itemID + " usage for node " + node.getUniqueIdentifier().toString() );
                    }
                    lastPublishedItem = collect.get( collect.size() - 1 );
                }
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
        synchronized ( uid.toString().intern() )
        {
            uidToPublishedItemMap.remove( uid );
            Node node = uidToNodeMap.remove( uid );
            if ( node != null )
            {
                String serviceId = node.getService().getServiceID();
                serviceIdToNodeIdsMap.compute( serviceId, ( s, list ) -> {
                    if ( list != null )
                    {
                        list.remove( uid );
                        return list;
                    }
                    return list;
                } );
            }
        }
    }
}