/*
 * Copyright (C) 2025 Ignite Realtime Foundation. All rights reserved.
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xmpp.packet.JID;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the implementation of {@link CachingPubsubPersistenceProvider}
 *
 * The unit tests in this class are limited to the operations that create and remove (published) items.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
@ExtendWith(MockitoExtension.class)
public class CachingPubsubPersistenceProviderItemOperationsTest
{
    /**
     * Asserts that an invocation of {@link CachingPubsubPersistenceProvider#savePublishedItem(PublishedItem)} causes a
     * corresponding item to be scheduled for processing.
     */
    @Test
    public void testSavePublishedItem() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final LeafNode mockNode = Mockito.mock(LeafNode.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID publisher = new JID("test-user-1", "example.org", null);
        final PublishedItem item = new PublishedItem(mockNode, publisher, UUID.randomUUID().toString(), new Date());

        // Execute system under test.
        provider.savePublishedItem(item);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ArrayList<>());
        assertEquals(0, pending.size());

        assertEquals(1, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Asserts that an invocation of {@link CachingPubsubPersistenceProvider#removePublishedItem(PublishedItem)} causes a
     * corresponding item to be scheduled for processing.
     */
    @Test
    public void testRemovePublishedItem() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final LeafNode mockNode = Mockito.mock(LeafNode.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID publisher = new JID("test-user-1", "example.org", null);
        final PublishedItem item = new PublishedItem(mockNode, publisher, UUID.randomUUID().toString(), new Date());

        // Execute system under test.
        provider.removePublishedItem(item);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ArrayList<>());
        assertEquals(0, pending.size());

        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(1, provider.itemsToDelete.size());
    }

    /**
     * Asserts that an invocations {@link CachingPubsubPersistenceProvider#savePublishedItem(PublishedItem)} to
     * save two distinct items cause corresponding items to be scheduled for processing.
     */
    @Test
    public void testSaveTwoPublishedItemsDistinctIdentifier() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final LeafNode mockNode = Mockito.mock(LeafNode.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID publisher = new JID("test-user-1", "example.org", null);
        final PublishedItem itemA = new PublishedItem(mockNode, publisher, UUID.randomUUID().toString(), new Date());
        final PublishedItem itemB = new PublishedItem(mockNode, publisher, UUID.randomUUID().toString(), new Date());

        // Execute system under test.
        provider.savePublishedItem(itemA);
        provider.savePublishedItem(itemB);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ArrayList<>());
        assertEquals(0, pending.size());

        assertEquals(2, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Asserts that an invocations {@link CachingPubsubPersistenceProvider#savePublishedItem(PublishedItem)} to
     * save two items with the same identifier causes the provider to optimize: the second 'save' will overwrite the
     * first, thus that can be discarded.
     */
    @Test
    public void testSaveTwoPublishedItemsSameIdentifier() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final LeafNode mockNode = Mockito.mock(LeafNode.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID publisher = new JID("test-user-1", "example.org", null);
        final String identifier = UUID.randomUUID().toString();
        final PublishedItem itemA = new PublishedItem(mockNode, publisher, identifier, new Date());
        final PublishedItem itemB = new PublishedItem(mockNode, publisher, identifier, new Date());

        // Execute system under test.
        provider.savePublishedItem(itemA);
        provider.savePublishedItem(itemB);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ArrayList<>());
        assertEquals(0, pending.size());

        assertEquals(1, provider.itemsToAdd.size());
        assertEquals(itemB.getID(), provider.itemsToAdd.getFirst().getID());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Asserts that an invocations {@link CachingPubsubPersistenceProvider#removePublishedItem(PublishedItem)} to
     * remove two distinct items cause corresponding items to be scheduled for processing.
     */
    @Test
    public void testRemoveTwoPublishedItemDistinctIdentifier() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final LeafNode mockNode = Mockito.mock(LeafNode.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID publisher = new JID("test-user-1", "example.org", null);
        final PublishedItem itemA = new PublishedItem(mockNode, publisher, UUID.randomUUID().toString(), new Date());
        final PublishedItem itemB = new PublishedItem(mockNode, publisher, UUID.randomUUID().toString(), new Date());

        // Execute system under test.
        provider.removePublishedItem(itemA);
        provider.removePublishedItem(itemB);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ArrayList<>());
        assertEquals(0, pending.size());

        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(2, provider.itemsToDelete.size());
    }

    /**
     * Asserts that an invocations {@link CachingPubsubPersistenceProvider#removePublishedItem(PublishedItem)} to
     * remove two items with the same identifier DOES NOT cause the provider to optimize: this is an erroneous invocation,
     * and the delegate provider should (possibly) thrown an exception (or silently ignore the duplcate).
     */
    @Test
    public void testRemoveTwoPublishedItemsSameIdentifier() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final LeafNode mockNode = Mockito.mock(LeafNode.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID publisher = new JID("test-user-1", "example.org", null);
        final String identifier = UUID.randomUUID().toString();
        final PublishedItem itemA = new PublishedItem(mockNode, publisher, identifier, new Date());
        final PublishedItem itemB = new PublishedItem(mockNode, publisher, identifier, new Date());

        // Execute system under test.
        provider.removePublishedItem(itemA);
        provider.removePublishedItem(itemB);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ArrayList<>());
        assertEquals(0, pending.size());

        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(2, provider.itemsToDelete.size());
    }
    /**
     * Asserts that an invocation of {@link CachingPubsubPersistenceProvider#savePublishedItem(PublishedItem)} followed
     * by an invocation of {@link CachingPubsubPersistenceProvider#removePublishedItem(PublishedItem)} to represent
     * update and removal of the same item causes the provider to optimize: the update can be discarded.
     *
     * Note that a 'save' can represent either a 'create' or an 'update'. As the provider doesn't attempt to complete
     * exact track of pre-existing items, the removal needs to be executed (and cannot be discarded).
     */
    @Test
    public void testSaveAndRemovePublishedItem() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final LeafNode mockNode = Mockito.mock(LeafNode.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID publisher = new JID("test-user-1", "example.org", null);
        final PublishedItem item = new PublishedItem(mockNode, publisher, UUID.randomUUID().toString(), new Date());

        // Execute system under test.
        provider.savePublishedItem(item);
        provider.removePublishedItem(item);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ArrayList<>());
        assertEquals(0, pending.size());

        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(1, provider.itemsToDelete.size());
    }

    /**
     * Asserts that an invocation of {@link CachingPubsubPersistenceProvider#removePublishedItem(PublishedItem)} followed
     * by an invocation of {@link CachingPubsubPersistenceProvider#savePublishedItem(PublishedItem)} to represent
     * removal and then creation of the same item causes the provider to optimize: the removal can be discarded.
     *
     * Note that the XML content can be different in both items. The latter save will still overwrite what would've been
     * otherwise explicitly been deleted first.
     */
    @Test
    public void testRemoveAndSavePublishedItem() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final LeafNode mockNode = Mockito.mock(LeafNode.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID publisher = new JID("test-user-1", "example.org", null);
        final PublishedItem item = new PublishedItem(mockNode, publisher, UUID.randomUUID().toString(), new Date());

        // Execute system under test.
        provider.removePublishedItem(item);
        provider.savePublishedItem(item);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ArrayList<>());
        assertEquals(0, pending.size());

        assertEquals(1, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#savePublishedItem(PublishedItem)}
     * and {@link CachingPubsubPersistenceProvider#removeNode(Node)} attempts to save an item in a node, after
     * which the node is removed.
     *
     * When a node is deleted, all its associated data (including items, affiliations and affiliations) are removed.
     * Any pending operations on that node can thus be removed.
     *
     * The caching provider can optimize the operations, where the 'net effect' of them is to have only the remove-node
     * operation.
     *
     * Therefor, this test asserts that after both invocations, one operation is scheduled.
     */
    @Test
    public void testDeleteNodeVoidsSavePublishedItem() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final PubSubPersistenceProvider mockDelegate = Mockito.mock(PubSubPersistenceProvider.class);
        provider.delegate = mockDelegate;
        final LeafNode mockNode = Mockito.mock(LeafNode.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID publisher = new JID("test-user-1", "example.org", null);
        final PublishedItem item = new PublishedItem(mockNode, publisher, UUID.randomUUID().toString(), new Date());

        // Execute system under test.
        provider.savePublishedItem(item);
        provider.removeNode(mockNode);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ArrayList<>());
        assertEquals(1, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation nodeOperation = pending.get(0);
        assertEquals(mockNode.getUniqueIdentifier(), nodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.REMOVE, nodeOperation.action);
        assertNull(nodeOperation.affiliate);
        assertNull(nodeOperation.subscription);

        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#removePublishedItem(PublishedItem)}
     * and {@link CachingPubsubPersistenceProvider#removeNode(Node)} attempts to remove an item from a node, after
     * which the node is removed.
     *
     * When a node is deleted, all its associated data (including items, affiliations and affiliations) are removed.
     * Any pending operations on that node can thus be removed.
     *
     * The caching provider can optimize the operations, where the 'net effect' of them is to have only the remove-node
     * operation.
     *
     * Therefor, this test asserts that after both invocations, one operation is scheduled.
     */
    @Test
    public void testDeleteNodeVoidsRemovePublishedItem() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final PubSubPersistenceProvider mockDelegate = Mockito.mock(PubSubPersistenceProvider.class);
        provider.delegate = mockDelegate;
        final LeafNode mockNode = Mockito.mock(LeafNode.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID publisher = new JID("test-user-1", "example.org", null);
        final PublishedItem item = new PublishedItem(mockNode, publisher, UUID.randomUUID().toString(), new Date());

        // Execute system under test.
        provider.removePublishedItem(item);
        provider.removeNode(mockNode);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ArrayList<>());
        assertEquals(1, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation nodeOperation = pending.get(0);
        assertEquals(mockNode.getUniqueIdentifier(), nodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.REMOVE, nodeOperation.action);
        assertNull(nodeOperation.affiliate);
        assertNull(nodeOperation.subscription);

        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }
}
