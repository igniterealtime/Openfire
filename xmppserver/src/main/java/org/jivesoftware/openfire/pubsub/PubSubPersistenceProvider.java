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

import org.jivesoftware.openfire.pep.PEPService;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

/**
 * Defines an implementation responsible for persisting pubsub-related data
 * to a backend data store.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public interface PubSubPersistenceProvider
{
    void initialize();
    void shutdown();

    /**
     * Schedules the node to be created in the database.
     *
     * @param node The newly created node.
     */
    void createNode(Node node);

    /**
     * Schedules the node to be updated in the database.
     *
     * @param node The updated node.
     */
    void updateNode(Node node);

    /**
     * Schedules the node to be removed in the database.
     *
     * @param node The node that is being deleted.
     */
    void removeNode(Node node);

    /**
     * Loads all nodes from the database and adds them to the PubSub service.
     *
     * @param service the pubsub service that is hosting the nodes.
     */
    void loadNodes(PubSubService service);

    /**
     * Loads all nodes from the database and adds them to the PubSub service.
     *
     * @param service
     *            the pubsub service that is hosting the nodes.
     * @param nodeIdentifier
     *            the identifier of the node to load.
     */
    void loadNode(PubSubService service, Node.UniqueIdentifier nodeIdentifier);

    @Deprecated
    default void loadSubscription(PubSubService service, Node node, String subId) {
        loadSubscription(node, subId);
    }

    void loadSubscription(Node node, String subId);

    /**
     * Returns identifiers for all pubsub nodes to which the provided address is a direct subscriber.
     *
     * Note that the results do not include nodes to which the provided address is a subscriber through inheritance!
     *
     * The result can include root nodes, (other) collection nodes as well as leaf nodes.
     *
     * When a node is subscribed to using a full JID, that node will be returned only if the address used as an
     * argument in this method matches that full JID. If the node was subscribed to using a bare JID, it will be
     * returned when the provided argument's bare JID representation matches the JID used for the subscription.
     *
     * @param address The address (bare of full JID) for which to return nodes.
     * @return A collection of node identifiers, possibly empty.
     */
    @Nonnull Set<Node.UniqueIdentifier> findDirectlySubscribedNodes(@Nonnull JID address);

    /**
     * Creates a new affiliation of the user in the node.
     *
     * @param node      The node where the affiliation of the user was updated.
     * @param affiliate The new affiliation of the user in the node.
     */
    void createAffiliation(Node node, NodeAffiliate affiliate);

    /**
     * Updates an affiliation of the user in the node.
     *
     * @param node      The node where the affiliation of the user was updated.
     * @param affiliate The new affiliation of the user in the node.
     */
    void updateAffiliation(Node node, NodeAffiliate affiliate);

    /**
     * Removes the affiliation and subscription state of the user from the DB.
     *
     * @param node      The node where the affiliation of the user was updated.
     * @param affiliate The existing affiliation and subsription state of the user in the node.
     */
    void removeAffiliation(Node node, NodeAffiliate affiliate);

    /**
     * Adds the new subscription of the user to the node to the database.
     *
     * @param node      The node where the user has subscribed to.
     * @param subscription The new subscription of the user to the node.
     */
    void createSubscription(Node node, NodeSubscription subscription);

    /**
     * Updates the subscription of the user to the node to the database.
     *
     * @param node      The node where the user has subscribed to.
     * @param subscription The new subscription of the user to the node.
     */
    void updateSubscription(Node node, NodeSubscription subscription);

    /**
     * Removes the subscription of the user from the DB.
     *
     * @param subscription The existing subscription of the user to the node.
     */
    void removeSubscription(NodeSubscription subscription);

    /**
     * Creates and stores the published item in the database.
     *
     * When an item with the same ID was previously saved, this item will be replaced by the new item.
     *
     * @param item The published item to save.
     */
    void savePublishedItem(PublishedItem item);

    /**
     * Removes the specified published item from the DB.
     *
     * @param item The published item to delete.
     */
    void removePublishedItem(PublishedItem item);

    /**
     * Loads from the database the default node configuration for the specified node type
     * and pubsub service.
     *
     * @param service the default node configuration used by this pubsub service.
     * @param isLeafType true if loading default configuration for leaf nodes.
     * @return the loaded default node configuration for the specified node type and service
     *         or <tt>null</tt> if none was found.
     * @deprecated Replaced by {@link #loadDefaultConfiguration(PubSubService.UniqueIdentifier, boolean)}
     */
    @Deprecated
    default DefaultNodeConfiguration loadDefaultConfiguration(PubSubService service, boolean isLeafType)
    {
        return loadDefaultConfiguration( service.getUniqueIdentifier(), isLeafType );
    }

    /**
     * Loads from the database the default node configuration for the specified node type
     * and pubsub service.
     *
     * @param serviceIdentifier Identifier of the service
     * @param isLeafType true if loading default configuration for leaf nodes.
     * @return the loaded default node configuration for the specified node type and service
     *         or <tt>null</tt> if none was found.
     */
    DefaultNodeConfiguration loadDefaultConfiguration(PubSubService.UniqueIdentifier serviceIdentifier, boolean isLeafType);

    /**
     * Creates a new default node configuration for the specified service.
     *
     * @param service the default node configuration used by this pubsub service.
     * @param config the default node configuration to create in the database.
     * @deprecated Replaced by {@link #createDefaultConfiguration(PubSubService.UniqueIdentifier, DefaultNodeConfiguration)}
     */
    @Deprecated
    default void createDefaultConfiguration(PubSubService service, DefaultNodeConfiguration config)
    {
        createDefaultConfiguration(service.getUniqueIdentifier(), config);
    }

    /**
     * Creates a new default node configuration for the specified service.
     *
     * @param serviceIdentifier Identifier of the service
     * @param config the default node configuration to create in the database.
     */
    void createDefaultConfiguration(PubSubService.UniqueIdentifier serviceIdentifier, DefaultNodeConfiguration config);

    /**
     * Updates the default node configuration for the specified service.
     *
     * @param service the default node configuration used by this pubsub service.
     * @param config the default node configuration to update in the database.
     * @deprecated Replaced by {@link #updateDefaultConfiguration(PubSubService.UniqueIdentifier, DefaultNodeConfiguration)}
     */
    @Deprecated
    default void updateDefaultConfiguration(PubSubService service, DefaultNodeConfiguration config) {
        updateDefaultConfiguration( service.getUniqueIdentifier(), config);
    }

    /**
     * Updates the default node configuration for the specified service.
     *
     * @param serviceIdentifier Identifier of the service
     * @param config the default node configuration to update in the database.
     */
    void updateDefaultConfiguration(PubSubService.UniqueIdentifier serviceIdentifier, DefaultNodeConfiguration config);

    /**
     * Fetches all the results for the specified node, limited by {@link LeafNode#getMaxPublishedItems()}.
     *
     * @param node the leaf node to load its published items.
     */
    List<PublishedItem> getPublishedItems(LeafNode node);

    /**
     * Fetches all the results for the specified node, limited by {@link LeafNode#getMaxPublishedItems()}.
     *
     * @param node the leaf node to load its published items.
     */
    List<PublishedItem> getPublishedItems(LeafNode node, int maxRows);

    /**
     * Fetches the last published item for the specified node.
     *
     * @param node the leaf node to load its last published items.
     */
    PublishedItem getLastPublishedItem(LeafNode node);

    PublishedItem getPublishedItem(LeafNode node, PublishedItem.UniqueIdentifier itemIdentifier);

    void purgeNode(LeafNode leafNode);

    /**
     * Loads a PEP service from the database, if it exists.
     *
     * @param jid
     *            the JID of the owner of the PEP service.
     * @return the loaded PEP service, or null if not found.
     * @deprecated Replaced by {@link #loadPEPServiceFromDB(JID)}
     */
    default PEPService loadPEPServiceFromDB(String jid) {
        return loadPEPServiceFromDB( new JID(jid));
    }

    /**
     * Loads a PEP service from the database, if it exists.
     *
     * Note that the returned service is not yet initialized!
     *
     * @param jid
     *            the JID of the owner of the PEP service.
     * @return the loaded PEP service, or null if not found.
     */
    PEPService loadPEPServiceFromDB(JID jid);

    /**
     * Writes large changesets, using batches and transactions when available.
     *
     * The 'delete' list takes precedence over the 'add' list: when an item exists
     * on both lists, it is removed (and not re-added) from storage
     *
     * To prevent duplicates to exist, this method will attempt to
     * remove all items to-be-added, before re-adding them.
     *
     * @param addList A list of items to be added.
     * @param delList A list of items to be removed.
     */
    void bulkPublishedItems( List<PublishedItem> addList, List<PublishedItem> delList );
}
