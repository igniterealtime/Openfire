/*
 * Copyright (C) 2025-2026 Ignite Realtime Foundation. All rights reserved.
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
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies the implementation of {@link CachingPubsubPersistenceProvider}
 *
 * The unit tests in this class are limited to the operations that create, update and remove affiliations.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
@ExtendWith(MockitoExtension.class)
public class CachingPubsubPersistenceProviderAffiliationOperationsTest
{
    /**
     * Asserts that an invocation of {@link CachingPubsubPersistenceProvider#createAffiliation(Node, NodeAffiliate)}
     * causes a corresponding operation to be scheduled.
     */
    @Test
    public void testCreateAffiliation() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID affiliateAddress = new JID("test-user-1", "example.org", null);
        final NodeAffiliate affiliate = new NodeAffiliate(mockNode, affiliateAddress);
        final NodeAffiliate.Affiliation affiliation = NodeAffiliate.Affiliation.publisher;
        affiliate.setAffiliation(affiliation);

        // Execute system under test.
        provider.createAffiliation(mockNode, affiliate);

        // Verify results.
        final Deque<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ConcurrentLinkedDeque<>());
        assertEquals(1, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation nodeOperation = pending.getFirst();
        assertEquals(mockNode.getUniqueIdentifier(), nodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.CREATE_AFFILIATION, nodeOperation.action);
        assertEquals(affiliateAddress, nodeOperation.affiliate.getJID());
        assertEquals(affiliation, nodeOperation.affiliate.getAffiliation());
        assertNull(nodeOperation.subscription);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Asserts that an invocation of {@link CachingPubsubPersistenceProvider#updateAffiliation(Node, NodeAffiliate)}
     * causes a corresponding operation to be scheduled.
     */
    @Test
    public void testUpdateAffiliation() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID affiliateAddress = new JID("test-user-1", "example.org", null);
        final NodeAffiliate affiliate = new NodeAffiliate(mockNode, affiliateAddress);
        final NodeAffiliate.Affiliation affiliation = NodeAffiliate.Affiliation.publisher;
        affiliate.setAffiliation(affiliation);
    
        // Execute system under test.
        provider.updateAffiliation(mockNode, affiliate);

        // Verify results.
        final Deque<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ConcurrentLinkedDeque<>());
        assertEquals(1, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation nodeOperation = pending.getFirst();
        assertEquals(mockNode.getUniqueIdentifier(), nodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.UPDATE_AFFILIATION, nodeOperation.action);
        assertEquals(affiliateAddress, nodeOperation.affiliate.getJID());
        assertEquals(affiliation, nodeOperation.affiliate.getAffiliation());
        assertNull(nodeOperation.subscription);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Asserts that an invocation of {@link CachingPubsubPersistenceProvider#removeAffiliation(Node, NodeAffiliate)} causes a corresponding
     * operation to be scheduled.
     */
    @Test
    public void testDeleteAffiliation() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID affiliateAddress = new JID("test-user-1", "example.org", null);
        final NodeAffiliate affiliate = new NodeAffiliate(mockNode, affiliateAddress);
        final NodeAffiliate.Affiliation affiliation = NodeAffiliate.Affiliation.publisher;
        affiliate.setAffiliation(affiliation);

        // Execute system under test.
        provider.removeAffiliation(mockNode, affiliate);

        // Verify results.
        final Deque<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ConcurrentLinkedDeque<>());
        assertEquals(1, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation nodeOperation = pending.getFirst();
        assertEquals(mockNode.getUniqueIdentifier(), nodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.REMOVE_AFFILIATION, nodeOperation.action);
        assertEquals(affiliateAddress, nodeOperation.affiliate.getJID());
        assertEquals(affiliation, nodeOperation.affiliate.getAffiliation());
        assertNull(nodeOperation.subscription);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#createAffiliation(Node, NodeAffiliate)}
     * attempts to create the same affiliation 'twice'.
     *
     * This tests an erroneous invocation. The design of the caching provider is such that it shouldn't try to 'clean up'
     * such misbehavior. Instead, it should pass this through to the delegate, which is expected to generate an
     * appropriate error response.
     *
     * Therefor, this test asserts that for each invocation, a corresponding operation is scheduled.
     */
    @Test
    public void testCreateAffiliationTwice() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID affiliateAddress = new JID("test-user-1", "example.org", null);
        final NodeAffiliate affiliate = new NodeAffiliate(mockNode, affiliateAddress);
        final NodeAffiliate.Affiliation affiliation = NodeAffiliate.Affiliation.publisher;
        affiliate.setAffiliation(affiliation);

        // Execute system under test.
        provider.createAffiliation(mockNode, affiliate);
        provider.createAffiliation(mockNode, affiliate);

        // Verify results.
        final Deque<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ConcurrentLinkedDeque<>());
        assertEquals(2, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation firstNodeOperation = pending.getFirst();
        assertEquals(mockNode.getUniqueIdentifier(), firstNodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.CREATE_AFFILIATION, firstNodeOperation.action);
        assertEquals(affiliateAddress, firstNodeOperation.affiliate.getJID());
        assertEquals(affiliation, firstNodeOperation.affiliate.getAffiliation());
        assertNull(firstNodeOperation.subscription);

        final CachingPubsubPersistenceProvider.NodeOperation secondNodeOperation = new ArrayList<>(pending).get(1);
        assertEquals(mockNode.getUniqueIdentifier(), secondNodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.CREATE_AFFILIATION, secondNodeOperation.action);
        assertEquals(affiliateAddress, secondNodeOperation.affiliate.getJID());
        assertEquals(affiliation, secondNodeOperation.affiliate.getAffiliation());
        assertNull(secondNodeOperation.subscription);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#createAffiliation(Node, NodeAffiliate)}
     * attempts to create the multiple, different affiliations.
     *
     * This test asserts that for each invocation, a corresponding operation is scheduled.
     */
    @Test
    public void testCreateAffiliations() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID firstAffiliateAddress = new JID("test-user-1", "example.org", null);
        final NodeAffiliate firstAffiliate = new NodeAffiliate(mockNode, firstAffiliateAddress);
        final NodeAffiliate.Affiliation firstAffiliation = NodeAffiliate.Affiliation.publisher;
        firstAffiliate.setAffiliation(firstAffiliation);
        final JID secondAffiliateAddress = new JID("test-user-2", "example.org", null);
        final NodeAffiliate secondAffiliate = new NodeAffiliate(mockNode, secondAffiliateAddress);
        final NodeAffiliate.Affiliation secondAffiliation = NodeAffiliate.Affiliation.outcast;
        secondAffiliate.setAffiliation(secondAffiliation);

        // Execute system under test.
        provider.createAffiliation(mockNode, firstAffiliate);
        provider.createAffiliation(mockNode, secondAffiliate);

        // Verify results.
        final Deque<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ConcurrentLinkedDeque<>());
        assertEquals(2, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation firstNodeOperation = pending.getFirst();
        assertEquals(mockNode.getUniqueIdentifier(), firstNodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.CREATE_AFFILIATION, firstNodeOperation.action);
        assertEquals(firstAffiliateAddress, firstNodeOperation.affiliate.getJID());
        assertEquals(firstAffiliation, firstNodeOperation.affiliate.getAffiliation());
        assertNull(firstNodeOperation.subscription);

        final CachingPubsubPersistenceProvider.NodeOperation secondNodeOperation = new ArrayList<>(pending).get(1);
        assertEquals(mockNode.getUniqueIdentifier(), secondNodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.CREATE_AFFILIATION, secondNodeOperation.action);
        assertEquals(secondAffiliateAddress, secondNodeOperation.affiliate.getJID());
        assertEquals(secondAffiliation, secondNodeOperation.affiliate.getAffiliation());
        assertNull(secondNodeOperation.subscription);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#updateAffiliation(Node, NodeAffiliate)}
     * attempts to update the same affiliation (with the same user and same node) 'twice'.
     *
     * Updates of an affiliation are never partial. This allows the caching provider to optimize two sequential updates.
     *
     * Therefor, this test asserts that after each invocation, only one operation, corresponding to the last update, is
     * scheduled.
     */
    @Test
    public void testUpdateAffiliationTwice() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID affiliateAddress = new JID("test-user-1", "example.org", null);
        final NodeAffiliate firstAffiliate = new NodeAffiliate(mockNode, affiliateAddress);
        final NodeAffiliate.Affiliation firstAffiliation = NodeAffiliate.Affiliation.publisher;
        firstAffiliate.setAffiliation(firstAffiliation);
        final NodeAffiliate secondAffiliate = new NodeAffiliate(mockNode, affiliateAddress);
        final NodeAffiliate.Affiliation secondAffiliation = NodeAffiliate.Affiliation.outcast;
        secondAffiliate.setAffiliation(secondAffiliation);

        // Execute system under test.
        provider.updateAffiliation(mockNode, firstAffiliate);
        provider.updateAffiliation(mockNode, secondAffiliate);

        // Verify results.
        final Deque<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ConcurrentLinkedDeque<>());
        assertEquals(1, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation nodeOperation = pending.getFirst();
        assertEquals(mockNode.getUniqueIdentifier(), nodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.UPDATE_AFFILIATION, nodeOperation.action);
        assertEquals(affiliateAddress, nodeOperation.affiliate.getJID());
        assertEquals(secondAffiliation, nodeOperation.affiliate.getAffiliation()); // must match that of the last invocation.
        assertNull(nodeOperation.subscription);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#createAffiliation(Node, NodeAffiliate)}
     * and {@link CachingPubsubPersistenceProvider#removeAffiliation(Node, NodeAffiliate)} creates and immediately removes
     * an affiliation.
     *
     * The caching provider can optimize these two operations, where the 'net effect' of them is to have no operation.
     *
     * Therefor, this test asserts that after each invocation, no operation is scheduled.
     */
    @Test
    public void testDeleteAffiliationAfterCreateAffiliation() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID affiliateAddress = new JID("test-user-1", "example.org", null);
        final NodeAffiliate affiliate = new NodeAffiliate(mockNode, affiliateAddress);
        final NodeAffiliate.Affiliation affiliation = NodeAffiliate.Affiliation.publisher;
        affiliate.setAffiliation(affiliation);

        // Execute system under test.
        provider.createAffiliation(mockNode, affiliate);
        provider.removeAffiliation(mockNode, affiliate);

        // Verify results.
        final Deque<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ConcurrentLinkedDeque<>());
        assertEquals(0, pending.size());

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#removeAffiliation(Node, NodeAffiliate)} and
     * {@link CachingPubsubPersistenceProvider#createAffiliation(Node, NodeAffiliate)} removes (an affiliation that
     * is thought to preexist) and then recreate an affiliation again.
     *
     * This test asserts that after both invocations, two corresponding operation are scheduled in that order.
     */
    @Test
    public void testDeleteAffiliationDoesNotVoidNewerCreate() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID affiliateAddress = new JID("test-user-1", "example.org", null);
        final NodeAffiliate affiliate = new NodeAffiliate(mockNode, affiliateAddress);
        final NodeAffiliate.Affiliation affiliation = NodeAffiliate.Affiliation.publisher;
        affiliate.setAffiliation(affiliation);

        // Execute system under test.
        provider.removeAffiliation(mockNode, affiliate);
        provider.createAffiliation(mockNode, affiliate);

        // Verify results.
        final Deque<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ConcurrentLinkedDeque<>());
        assertEquals(2, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation firstOperation = pending.getFirst();
        assertEquals(mockNode.getUniqueIdentifier(), firstOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.REMOVE_AFFILIATION, firstOperation.action);
        assertEquals(affiliateAddress, firstOperation.affiliate.getJID());
        assertEquals(affiliation, firstOperation.affiliate.getAffiliation());
        assertNull(firstOperation.subscription);

        final CachingPubsubPersistenceProvider.NodeOperation secondOperation = new ArrayList<>(pending).get(1);
        assertEquals(mockNode.getUniqueIdentifier(), secondOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.CREATE_AFFILIATION, secondOperation.action);
        assertEquals(affiliateAddress, secondOperation.affiliate.getJID());
        assertEquals(affiliation, secondOperation.affiliate.getAffiliation());
        assertNull(secondOperation.subscription);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#createAffiliation(Node, NodeAffiliate)},
     * {@link CachingPubsubPersistenceProvider#removeAffiliation(Node, NodeAffiliate)} and
     * {@link CachingPubsubPersistenceProvider#createAffiliation(Node, NodeAffiliate)}
     * creates, then removes and then recreate an affiliation again.
     *
     * The caching provider can optimize the first two operations, where the 'net effect' of them is to have no operation.
     *
     * Therefor, this test asserts that after all invocations, only one operation that represents the last invocation,
     * is scheduled.
     */
    @Test
    public void testDeleteAffiliationDoesNotVoidNewerCreate2() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID affiliateAddress = new JID("test-user-1", "example.org", null);
        final NodeAffiliate affiliate = new NodeAffiliate(mockNode, affiliateAddress);
        final NodeAffiliate.Affiliation affiliation = NodeAffiliate.Affiliation.publisher;
        affiliate.setAffiliation(affiliation);

        // Execute system under test.
        provider.createAffiliation(mockNode, affiliate);
        provider.removeAffiliation(mockNode, affiliate);
        provider.createAffiliation(mockNode, affiliate);

        // Verify results.
        final Deque<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ConcurrentLinkedDeque<>());
        assertEquals(1, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation nodeOperation = pending.getFirst();
        assertEquals(mockNode.getUniqueIdentifier(), nodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.CREATE_AFFILIATION, nodeOperation.action);
        assertEquals(affiliateAddress, nodeOperation.affiliate.getJID());
        assertEquals(affiliation, nodeOperation.affiliate.getAffiliation());
        assertNull(nodeOperation.subscription);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#updateAffiliation(Node, NodeAffiliate)}
     * and {@link CachingPubsubPersistenceProvider#removeAffiliation(Node, NodeAffiliate)} first updates (an affiliation
     * that is thought to preexist) and then removes that affiliation.
     *
     * The caching provider can optimize the two operations, as the 'net effect' of them is to have removal.
     *
     * Therefor, this test asserts that after all invocations, only one operation that represents the last invocation,
     * is scheduled.
     */
    @Test
    public void testDeleteAffiliationReplacesOlderUpdate() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID affiliateAddress = new JID("test-user-1", "example.org", null);
        final NodeAffiliate affiliate = new NodeAffiliate(mockNode, affiliateAddress);
        final NodeAffiliate.Affiliation affiliation = NodeAffiliate.Affiliation.publisher;
        affiliate.setAffiliation(affiliation);

        // Execute system under test.
        provider.updateAffiliation(mockNode, affiliate);
        provider.removeAffiliation(mockNode, affiliate);

        // Verify results.
        final Deque<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ConcurrentLinkedDeque<>());
        assertEquals(1, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation nodeOperation = pending.getFirst();
        assertEquals(mockNode.getUniqueIdentifier(), nodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.REMOVE_AFFILIATION, nodeOperation.action);
        assertEquals(affiliateAddress, nodeOperation.affiliate.getJID());
        assertEquals(affiliation, nodeOperation.affiliate.getAffiliation());
        assertNull(nodeOperation.subscription);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }


    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#createAffiliation(Node, NodeAffiliate)},
     * {@link CachingPubsubPersistenceProvider#updateAffiliation(Node, NodeAffiliate)} and
     * {@link CachingPubsubPersistenceProvider#removeAffiliation(Node, NodeAffiliate)} an affiliation is created, updated
     * and immediately removes again.
     *
     * The caching provider can optimize these three operations, where the 'net effect' of them is to have no operation.
     *
     * Therefor, this test asserts that after each invocation, no operation is scheduled.
     */
    @Test
    public void testDeleteAffiliationVoidsOlderCreateWithUpdate() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID affiliateAddress = new JID("test-user-1", "example.org", null);
        final NodeAffiliate affiliate = new NodeAffiliate(mockNode, affiliateAddress);
        final NodeAffiliate.Affiliation affiliation = NodeAffiliate.Affiliation.publisher;
        affiliate.setAffiliation(affiliation);
    
        // Execute system under test.
        provider.createAffiliation(mockNode, affiliate);
        provider.updateAffiliation(mockNode, affiliate);
        provider.removeAffiliation(mockNode, affiliate);

        // Verify results.
        final Deque<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ConcurrentLinkedDeque<>());
        assertEquals(0, pending.size());

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#removeAffiliation(Node, NodeAffiliate)}
     * attempts to remove the same affiliation 'twice'.
     *
     * This tests an erroneous invocation. The design of the caching provider is such that it shouldn't try to 'clean up'
     * such misbehavior. Instead, it should pass this through to the delegate, which is expected to generate an
     * appropriate error response.
     *
     * Therefor, this test asserts that for each invocation, a corresponding operation is scheduled.
     */
    @Test
    public void testDeleteAffiliationTwice() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID affiliateAddress = new JID("test-user-1", "example.org", null);
        final NodeAffiliate affiliate = new NodeAffiliate(mockNode, affiliateAddress);
        final NodeAffiliate.Affiliation affiliation = NodeAffiliate.Affiliation.publisher;
        affiliate.setAffiliation(affiliation);
    
        // Execute system under test.
        provider.removeAffiliation(mockNode, affiliate);
        provider.removeAffiliation(mockNode, affiliate);

        // Verify results.
        final Deque<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ConcurrentLinkedDeque<>());
        assertEquals(2, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation firstNodeOperation = pending.getFirst();
        assertEquals(mockNode.getUniqueIdentifier(), firstNodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.REMOVE_AFFILIATION, firstNodeOperation.action);
        assertEquals(affiliateAddress, firstNodeOperation.affiliate.getJID());
        assertEquals(affiliation, firstNodeOperation.affiliate.getAffiliation());
        assertNull(firstNodeOperation.subscription);

        final CachingPubsubPersistenceProvider.NodeOperation secondNodeOperation = new ArrayList<>(pending).get(1);
        assertEquals(mockNode.getUniqueIdentifier(), secondNodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.REMOVE_AFFILIATION, secondNodeOperation.action);
        assertEquals(affiliateAddress, secondNodeOperation.affiliate.getJID());
        assertEquals(affiliation, secondNodeOperation.affiliate.getAffiliation());
        assertNull(secondNodeOperation.subscription);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#createAffiliation(Node, NodeAffiliate)}
     * and {@link CachingPubsubPersistenceProvider#removeAffiliation(Node, NodeAffiliate)} creates an affiliation, and
     * then removes a different affiliation.
     *
     * As these operations relate to two different affiliations, the caching provider MUST NOT optimize these two
     * operations (where the 'net effect' of them would be to have no operation).
     *
     * Therefor, this test asserts that after both invocations, two operations are scheduled.
     */
    @Test
    public void testDeleteAffiliationAfterCreateDifferentAffiliation() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID firstAffiliateAddress = new JID("test-user-1", "example.org", null);
        final NodeAffiliate firstAffiliate = new NodeAffiliate(mockNode, firstAffiliateAddress);
        final NodeAffiliate.Affiliation firstAffiliation = NodeAffiliate.Affiliation.publisher;
        firstAffiliate.setAffiliation(firstAffiliation);
        final JID secondAffiliateAddress = new JID("test-user-2", "example.org", null);
        final NodeAffiliate secondAffiliate = new NodeAffiliate(mockNode, secondAffiliateAddress);
        final NodeAffiliate.Affiliation secondAffiliation = NodeAffiliate.Affiliation.outcast;
        secondAffiliate.setAffiliation(secondAffiliation);

        // Execute system under test.
        provider.createAffiliation(mockNode, firstAffiliate);
        provider.removeAffiliation(mockNode, secondAffiliate);

        // Verify results.
        final Deque<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ConcurrentLinkedDeque<>());
        assertEquals(2, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation firstNodeOperation = pending.getFirst();
        assertEquals(mockNode.getUniqueIdentifier(), firstNodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.CREATE_AFFILIATION, firstNodeOperation.action);
        assertEquals(firstAffiliateAddress, firstNodeOperation.affiliate.getJID());
        assertEquals(firstAffiliation, firstNodeOperation.affiliate.getAffiliation());
        assertNull(firstNodeOperation.subscription);

        final CachingPubsubPersistenceProvider.NodeOperation secondNodeOperation = new ArrayList<>(pending).get(1);
        assertEquals(mockNode.getUniqueIdentifier(), secondNodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.REMOVE_AFFILIATION, secondNodeOperation.action);
        assertEquals(secondAffiliateAddress, secondNodeOperation.affiliate.getJID());
        assertEquals(secondAffiliation, secondNodeOperation.affiliate.getAffiliation());
        assertNull(secondNodeOperation.subscription);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#updateAffiliation(Node, NodeAffiliate)}
     * attempts to update two different affiliations (with a different user but same node).
     *
     * As these operations relate to two different affiliations, the caching provider MUST NOT optimize these two
     * operations (where the 'net effect' of them would be to have one operation).
     *
     * Therefor, this test asserts that after both invocations, two operations are scheduled.
     */
    @Test
    public void testUpdateTwoAffiliations() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID firstAffiliateAddress = new JID("test-user-1", "example.org", null);
        final NodeAffiliate firstAffiliate = new NodeAffiliate(mockNode, firstAffiliateAddress);
        final NodeAffiliate.Affiliation firstAffiliation = NodeAffiliate.Affiliation.publisher;
        firstAffiliate.setAffiliation(firstAffiliation);
        final JID secondAffiliateAddress = new JID("test-user-2", "example.org", null);
        final NodeAffiliate secondAffiliate = new NodeAffiliate(mockNode, secondAffiliateAddress);
        final NodeAffiliate.Affiliation secondAffiliation = NodeAffiliate.Affiliation.outcast;
        secondAffiliate.setAffiliation(secondAffiliation);

        // Execute system under test.
        provider.updateAffiliation(mockNode, firstAffiliate);
        provider.updateAffiliation(mockNode, secondAffiliate);

        // Verify results.
        final Deque<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ConcurrentLinkedDeque<>());
        assertEquals(2, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation firstNodeOperation = pending.getFirst();
        assertEquals(mockNode.getUniqueIdentifier(), firstNodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.UPDATE_AFFILIATION, firstNodeOperation.action);
        assertEquals(firstAffiliateAddress, firstNodeOperation.affiliate.getJID());
        assertEquals(firstAffiliation, firstNodeOperation.affiliate.getAffiliation());
        assertNull(firstNodeOperation.subscription);

        final CachingPubsubPersistenceProvider.NodeOperation secondNodeOperation = new ArrayList<>(pending).get(1);
        assertEquals(mockNode.getUniqueIdentifier(), secondNodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.UPDATE_AFFILIATION, secondNodeOperation.action);
        assertEquals(secondAffiliateAddress, secondNodeOperation.affiliate.getJID());
        assertEquals(secondAffiliation, secondNodeOperation.affiliate.getAffiliation());
        assertNull(secondNodeOperation.subscription);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#createAffiliation(Node, NodeAffiliate)}
     * and {@link CachingPubsubPersistenceProvider#removeNode(Node)} attempts to create an affiliation for a node, after
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
    public void testDeleteNodeVoidsCreateAffiliation() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID affiliateAddress = new JID("test-user-1", "example.org", null);
        final NodeAffiliate affiliate = new NodeAffiliate(mockNode, affiliateAddress);
        final NodeAffiliate.Affiliation affiliation = NodeAffiliate.Affiliation.publisher;
        affiliate.setAffiliation(affiliation);

        // Execute system under test.
        provider.createAffiliation(mockNode, affiliate);
        provider.removeNode(mockNode);

        // Verify results.
        final Deque<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ConcurrentLinkedDeque<>());
        assertEquals(1, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation nodeOperation = pending.getFirst();
        assertEquals(mockNode.getUniqueIdentifier(), nodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.REMOVE, nodeOperation.action);
        assertNull(nodeOperation.affiliate);
        assertNull(nodeOperation.subscription);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#updateAffiliation(Node, NodeAffiliate)}
     * and {@link CachingPubsubPersistenceProvider#removeNode(Node)} attempts to update an affiliation for a node, after
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
    public void testDeleteNodeVoidsUpdateAffiliation() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID affiliateAddress = new JID("test-user-1", "example.org", null);
        final NodeAffiliate affiliate = new NodeAffiliate(mockNode, affiliateAddress);
        final NodeAffiliate.Affiliation affiliation = NodeAffiliate.Affiliation.publisher;
        affiliate.setAffiliation(affiliation);

        // Execute system under test.
        provider.updateAffiliation(mockNode, affiliate);
        provider.removeNode(mockNode);

        // Verify results.
        final Deque<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ConcurrentLinkedDeque<>());
        assertEquals(1, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation nodeOperation = pending.getFirst();
        assertEquals(mockNode.getUniqueIdentifier(), nodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.REMOVE, nodeOperation.action);
        assertNull(nodeOperation.affiliate);
        assertNull(nodeOperation.subscription);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#removeAffiliation(Node, NodeAffiliate)}
     * and {@link CachingPubsubPersistenceProvider#removeNode(Node)} attempts to remove an affiliation for a node, after
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
    public void testDeleteNodeVoidsDeleteAffiliation() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID affiliateAddress = new JID("test-user-1", "example.org", null);
        final NodeAffiliate affiliate = new NodeAffiliate(mockNode, affiliateAddress);
        final NodeAffiliate.Affiliation affiliation = NodeAffiliate.Affiliation.publisher;
        affiliate.setAffiliation(affiliation);

        // Execute system under test.
        provider.removeAffiliation(mockNode, affiliate);
        provider.removeNode(mockNode);

        // Verify results.
        final Deque<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ConcurrentLinkedDeque<>());
        assertEquals(1, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation nodeOperation = pending.getFirst();
        assertEquals(mockNode.getUniqueIdentifier(), nodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.REMOVE, nodeOperation.action);
        assertNull(nodeOperation.affiliate);
        assertNull(nodeOperation.subscription);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }
}
