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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the implementation of {@link CachingPubsubPersistenceProvider}
 *
 * The unit tests in this class are limited to the operations that create, update and remove nodes.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
@ExtendWith(MockitoExtension.class)
public class CachingPubsubPersistenceProviderNodeOperationsTest
{
    /**
     * Asserts that an invocation of {@link CachingPubsubPersistenceProvider#createNode(Node)} causes a corresponding
     * operation to be scheduled.
     */
    @Test
    public void testCreateNode() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));

        // Execute system under test.
        provider.createNode(mockNode);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ArrayList<>());
        assertEquals(1, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation nodeOperation = pending.get(0);
        assertEquals(mockNode.getUniqueIdentifier(), nodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.CREATE, nodeOperation.action);
        assertNull(nodeOperation.subscription);
        assertNull(nodeOperation.affiliate);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Asserts that an invocation of {@link CachingPubsubPersistenceProvider#updateNode(Node)} causes a corresponding
     * operation to be scheduled.
     */
    @Test
    public void testUpdateNode() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));

        // Execute system under test.
        provider.updateNode(mockNode);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ArrayList<>());
        assertEquals(1, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation nodeOperation = pending.get(0);
        assertEquals(mockNode.getUniqueIdentifier(), nodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.UPDATE, nodeOperation.action);
        assertNull(nodeOperation.subscription);
        assertNull(nodeOperation.affiliate);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Asserts that an invocation of {@link CachingPubsubPersistenceProvider#removeNode(Node)} causes a corresponding
     * operation to be scheduled.
     */
    @Test
    public void testDeleteNode() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));

        // Execute system under test.
        provider.removeNode(mockNode);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ArrayList<>());
        assertEquals(1, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation nodeOperation = pending.get(0);
        assertEquals(mockNode.getUniqueIdentifier(), nodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.REMOVE, nodeOperation.action);
        assertNull(nodeOperation.subscription);
        assertNull(nodeOperation.affiliate);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#createNode(Node)} attempts to create the
     * same node 'twice'.
     *
     * This tests an erroneous invocation. The design of the caching provider is such that it shouldn't try to 'clean up'
     * such misbehavior. Instead, it should pass this through to the delegate, which is expected to generate an
     * appropriate error response.
     *
     * Therefor, this test asserts that for each invocation, a corresponding operation is scheduled.
     */
    @Test
    public void testCreateNodeTwice() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockOrigNode = Mockito.mock(Node.class);
        final Node.UniqueIdentifier nodeId = new Node.UniqueIdentifier("mock-service-1", "mock-node-a");
        Mockito.lenient().when(mockOrigNode.getUniqueIdentifier()).thenReturn(nodeId);
        Mockito.lenient().when(mockOrigNode.getCreationDate()).thenReturn(new Date(0));
        final Node mockReplaceNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockReplaceNode.getUniqueIdentifier()).thenReturn(nodeId);
        Mockito.lenient().when(mockReplaceNode.getCreationDate()).thenReturn(new Date(1));

        // Execute system under test.
        provider.createNode(mockOrigNode);
        provider.createNode(mockReplaceNode);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(nodeId, id -> new ArrayList<>());
        assertEquals(2, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation firstNodeOperation = pending.get(0);
        assertEquals(mockOrigNode.getUniqueIdentifier(), firstNodeOperation.node.getUniqueIdentifier());
        assertEquals(new Date(0), firstNodeOperation.node.getCreationDate());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.CREATE, firstNodeOperation.action);
        assertNull(firstNodeOperation.subscription);
        assertNull(firstNodeOperation.affiliate);

        final CachingPubsubPersistenceProvider.NodeOperation secondNodeOperation = pending.get(1);
        assertEquals(mockOrigNode.getUniqueIdentifier(), secondNodeOperation.node.getUniqueIdentifier());
        assertNotEquals(new Date(0), secondNodeOperation.node.getCreationDate());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.CREATE, secondNodeOperation.action);
        assertNull(secondNodeOperation.subscription);
        assertNull(secondNodeOperation.affiliate);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#updateNode(Node)} attempts to update the
     * same node 'twice'.
     *
     * Updates of a node are never partial. This allows the caching provider to optimize two sequential updates.
     *
     * Therefor, this test asserts that after each invocation, only one operation, corresponding to the last update, is
     * scheduled.
     */
    @Test
    public void testUpdateNodeTwice() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockOrigNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockOrigNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        Mockito.lenient().when(mockOrigNode.getCreationDate()).thenReturn(new Date(0));
        final Node mockReplaceNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockReplaceNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        Mockito.lenient().when(mockReplaceNode.getCreationDate()).thenReturn(new Date(1));

        // Execute system under test.
        provider.updateNode(mockOrigNode);
        provider.updateNode(mockReplaceNode);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockOrigNode.getUniqueIdentifier(), id -> new ArrayList<>());
        assertEquals(1, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation nodeOperation = pending.get(0);
        assertEquals(mockReplaceNode.getUniqueIdentifier(), nodeOperation.node.getUniqueIdentifier());
        assertNotEquals(new Date(0), nodeOperation.node.getCreationDate());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.UPDATE, nodeOperation.action);
        assertNull(nodeOperation.subscription);
        assertNull(nodeOperation.affiliate);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#createNode(Node)} and
     * {@link CachingPubsubPersistenceProvider#removeNode(Node)} creates and immediately removes a node again.
     *
     * The caching provider can optimize these two operations, where the 'net effect' of them is to have no operation.
     *
     * Therefor, this test asserts that after each invocation, no operation is scheduled.
     */
    @Test
    public void testDeleteNodeAfterCreateNode() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));

        // Execute system under test.
        provider.createNode(mockNode);
        provider.removeNode(mockNode);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ArrayList<>());
        assertEquals(0, pending.size());

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#removeNode(Node)} and
     * {@link CachingPubsubPersistenceProvider#createNode(Node)} removes (a node that is thought to preexist) and then
     * recreate a node again.
     *
     * This test asserts that after both invocations, two corresponding operation are scheduled in that order.
     */
    @Test
    public void testDeleteNodeDoesNotVoidNewerCreate() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));

        // Execute system under test.
        provider.removeNode(mockNode);
        provider.createNode(mockNode);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ArrayList<>());
        assertEquals(2, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation firstOperation = pending.get(0);
        assertEquals(mockNode.getUniqueIdentifier(), firstOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.REMOVE, firstOperation.action);
        assertNull(firstOperation.subscription);
        assertNull(firstOperation.affiliate);

        final CachingPubsubPersistenceProvider.NodeOperation secondOperation = pending.get(1);
        assertEquals(mockNode.getUniqueIdentifier(), secondOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.CREATE, secondOperation.action);
        assertNull(secondOperation.subscription);
        assertNull(secondOperation.affiliate);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#createNode(Node)},
     * {@link CachingPubsubPersistenceProvider#removeNode(Node)} and {@link CachingPubsubPersistenceProvider#createNode(Node)}
     * creates, then removes and then recreate a node again.
     *
     * The caching provider can optimize the first two operations, where the 'net effect' of them is to have no operation.
     *
     * Therefor, this test asserts that after all invocations, only one operation that represents the last invocation,
     * is scheduled.
     */
    @Test
    public void testDeleteNodeDoesNotVoidNewerCreate2() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));

        // Execute system under test.
        provider.createNode(mockNode);
        provider.removeNode(mockNode);
        provider.createNode(mockNode);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ArrayList<>());
        assertEquals(1, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation nodeOperation = pending.get(0);
        assertEquals(mockNode.getUniqueIdentifier(), nodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.CREATE, nodeOperation.action);
        assertNull(nodeOperation.subscription);
        assertNull(nodeOperation.affiliate);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#updateNode(Node)} and
     * {@link CachingPubsubPersistenceProvider#removeNode(Node)} first updates (a node that is thought to preexist) and
     * then removes a node.
     *
     * The caching provider can optimize the two operations, as the 'net effect' of them is to have removal.
     *
     * Therefor, this test asserts that after all invocations, only one operation that represents the last invocation,
     * is scheduled.
     */
    @Test
    public void testDeleteNodeReplacesOlderUpdate() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));

        // Execute system under test.
        provider.updateNode(mockNode);
        provider.removeNode(mockNode);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ArrayList<>());
        assertEquals(1, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation nodeOperation = pending.get(0);
        assertEquals(mockNode.getUniqueIdentifier(), nodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.REMOVE, nodeOperation.action);
        assertNull(nodeOperation.subscription);
        assertNull(nodeOperation.affiliate);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }


    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#createNode(Node)},
     * {@link CachingPubsubPersistenceProvider#updateNode(Node)} and {@link CachingPubsubPersistenceProvider#removeNode(Node)}
     * a node is created, updated and immediately removes a node again.
     *
     * The caching provider can optimize these three operations, where the 'net effect' of them is to have no operation.
     *
     * Therefor, this test asserts that after each invocation, no operation is scheduled.
     */
    @Test
    public void testDeleteNodeVoidsOlderCreateWithUpdate() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));

        // Execute system under test.
        provider.createNode(mockNode);
        provider.updateNode(mockNode);
        provider.removeNode(mockNode);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ArrayList<>());
        assertEquals(0, pending.size());

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#removeNode(Node)} attempts to remove the
     * same node 'twice'.
     *
     * This tests an erroneous invocation. The design of the caching provider is such that it shouldn't try to 'clean up'
     * such misbehavior. Instead, it should pass this through to the delegate, which is expected to generate an
     * appropriate error response.
     *
     * Therefor, this test asserts that for each invocation, a corresponding operation is scheduled.
     */
    @Test
    public void testDeleteNodeTwice() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));

        // Execute system under test.
        provider.removeNode(mockNode);
        provider.removeNode(mockNode);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ArrayList<>());
        assertEquals(2, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation firstNodeOperation = pending.get(0);
        assertEquals(mockNode.getUniqueIdentifier(), firstNodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.REMOVE, firstNodeOperation.action);
        assertNull(firstNodeOperation.subscription);
        assertNull(firstNodeOperation.affiliate);

        final CachingPubsubPersistenceProvider.NodeOperation secondNodeOperation = pending.get(1);
        assertEquals(mockNode.getUniqueIdentifier(), secondNodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.REMOVE, secondNodeOperation.action);
        assertNull(secondNodeOperation.subscription);
        assertNull(secondNodeOperation.affiliate);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }
}
