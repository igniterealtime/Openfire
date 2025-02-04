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

import org.checkerframework.checker.units.qual.N;
import org.jivesoftware.util.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xmpp.packet.JID;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the implementation of {@link CachingPubsubPersistenceProvider}
 *
 * The unit tests in this class are limited to the operations that create, update and remove subscriptions.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
@ExtendWith(MockitoExtension.class)
public class CachingPubsubPersistenceProviderSubscriptionOperationsTest
{
    /**
     * Asserts that an invocation of {@link CachingPubsubPersistenceProvider#createSubscription(Node, NodeSubscription)}
     * causes a corresponding operation to be scheduled.
     */
    @Test
    public void testCreateSubscription() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID subscriber = new JID("test-user-1", "example.org", null);
        final String subscriptionId = "test-subscription-id-" + StringUtils.randomString(7);
        final NodeSubscription subscription = new NodeSubscription(mockNode, subscriber, subscriber, NodeSubscription.State.subscribed, subscriptionId);

        // Execute system under test.
        provider.createSubscription(mockNode, subscription);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ArrayList<>());
        assertEquals(1, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation nodeOperation = pending.get(0);
        assertEquals(mockNode.getUniqueIdentifier(), nodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.CREATE_SUBSCRIPTION, nodeOperation.action);
        assertEquals(subscriptionId, nodeOperation.subscription.getID());
        assertNull(nodeOperation.affiliate);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Asserts that an invocation of {@link CachingPubsubPersistenceProvider#updateSubscription(Node, NodeSubscription)}
     * causes a corresponding operation to be scheduled.
     */
    @Test
    public void testUpdateSubscription() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID subscriber = new JID("test-user-1", "example.org", null);
        final String subscriptionId = "test-subscription-id-" + StringUtils.randomString(7);
        final NodeSubscription subscription = new NodeSubscription(mockNode, subscriber, subscriber, NodeSubscription.State.subscribed, subscriptionId);

        // Execute system under test.
        provider.updateSubscription(mockNode, subscription);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ArrayList<>());
        assertEquals(1, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation nodeOperation = pending.get(0);
        assertEquals(mockNode.getUniqueIdentifier(), nodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.UPDATE_SUBSCRIPTION, nodeOperation.action);
        assertEquals(subscriptionId, nodeOperation.subscription.getID());
        assertNull(nodeOperation.affiliate);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Asserts that an invocation of {@link CachingPubsubPersistenceProvider#removeSubscription(NodeSubscription)} causes a corresponding
     * operation to be scheduled.
     */
    @Test
    public void testDeleteSubscription() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID subscriber = new JID("test-user-1", "example.org", null);
        final String subscriptionId = "test-subscription-id-" + StringUtils.randomString(7);
        final NodeSubscription subscription = new NodeSubscription(mockNode, subscriber, subscriber, NodeSubscription.State.subscribed, subscriptionId);

        // Execute system under test.
        provider.removeSubscription(subscription);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ArrayList<>());
        assertEquals(1, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation nodeOperation = pending.get(0);
        assertEquals(mockNode.getUniqueIdentifier(), nodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.REMOVE_SUBSCRIPTION, nodeOperation.action);
        assertEquals(subscriptionId, nodeOperation.subscription.getID());
        assertNull(nodeOperation.affiliate);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#createSubscription(Node, NodeSubscription)}
     * attempts to create the same subscription 'twice'.
     *
     * This tests an erroneous invocation. The design of the caching provider is such that it shouldn't try to 'clean up'
     * such misbehavior. Instead, it should pass this through to the delegate, which is expected to generate an
     * appropriate error response.
     *
     * Therefor, this test asserts that for each invocation, a corresponding operation is scheduled.
     */
    @Test
    public void testCreateSubscriptionTwice() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID subscriber = new JID("test-user-1", "example.org", null);
        final String subscriptionId = "test-subscription-id-" + StringUtils.randomString(7);
        final NodeSubscription subscription = new NodeSubscription(mockNode, subscriber, subscriber, NodeSubscription.State.subscribed, subscriptionId);

        // Execute system under test.
        provider.createSubscription(mockNode, subscription);
        provider.createSubscription(mockNode, subscription);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ArrayList<>());
        assertEquals(2, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation firstNodeOperation = pending.get(0);
        assertEquals(mockNode.getUniqueIdentifier(), firstNodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.CREATE_SUBSCRIPTION, firstNodeOperation.action);
        assertEquals(subscriptionId, firstNodeOperation.subscription.getID());
        assertNull(firstNodeOperation.affiliate);

        final CachingPubsubPersistenceProvider.NodeOperation secondNodeOperation = pending.get(1);
        assertEquals(mockNode.getUniqueIdentifier(), secondNodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.CREATE_SUBSCRIPTION, secondNodeOperation.action);
        assertEquals(subscriptionId, secondNodeOperation.subscription.getID());
        assertNull(secondNodeOperation.affiliate);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#createSubscription(Node, NodeSubscription)}
     * attempts to create the multiple, different subscriptions.
     *
     * This test asserts that for each invocation, a corresponding operation is scheduled.
     */
    @Test
    public void testCreateSubscriptions() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID firstSubscriber = new JID("test-user-1", "example.org", null);
        final String firstSubscriptionId = "test-subscription-id-" + StringUtils.randomString(7);
        final NodeSubscription firstSubscription = new NodeSubscription(mockNode, firstSubscriber, firstSubscriber, NodeSubscription.State.subscribed, firstSubscriptionId);
        final JID secondSubscriber = new JID("test-user-2", "example.org", null); // Technically, the same user can be subscribed more than once, but lets keep things simple.
        final String secondSubscriptionId = "test-subscription-id-" + StringUtils.randomString(7);
        final NodeSubscription secondSubscription = new NodeSubscription(mockNode, secondSubscriber, secondSubscriber, NodeSubscription.State.subscribed, secondSubscriptionId);

        // Execute system under test.
        provider.createSubscription(mockNode, firstSubscription);
        provider.createSubscription(mockNode, secondSubscription);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ArrayList<>());
        assertEquals(2, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation firstNodeOperation = pending.get(0);
        assertEquals(mockNode.getUniqueIdentifier(), firstNodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.CREATE_SUBSCRIPTION, firstNodeOperation.action);
        assertEquals(firstSubscriptionId, firstNodeOperation.subscription.getID());
        assertEquals(firstSubscriber, firstNodeOperation.subscription.getJID());
        assertEquals(firstSubscriber, firstNodeOperation.subscription.getOwner());
        assertNull(firstNodeOperation.affiliate);

        final CachingPubsubPersistenceProvider.NodeOperation secondNodeOperation = pending.get(1);
        assertEquals(mockNode.getUniqueIdentifier(), secondNodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.CREATE_SUBSCRIPTION, secondNodeOperation.action);
        assertEquals(secondSubscriptionId, secondNodeOperation.subscription.getID());
        assertEquals(secondSubscriber, secondNodeOperation.subscription.getJID());
        assertEquals(secondSubscriber, secondNodeOperation.subscription.getOwner());
        assertNull(secondNodeOperation.affiliate);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#updateSubscription(Node, NodeSubscription)}
     * attempts to update the same subscription (with the same ID, same user and same node) 'twice'.
     *
     * Updates of a subscription are never partial. This allows the caching provider to optimize two sequential updates.
     *
     * Therefor, this test asserts that after each invocation, only one operation, corresponding to the last update, is
     * scheduled.
     */
    @Test
    public void testUpdateSubscriptionTwice() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID subscriber = new JID("test-user-1", "example.org", null);
        final String subscriptionId = "test-subscription-id-" + StringUtils.randomString(7);
        final NodeSubscription firstSubscription = new NodeSubscription(mockNode, subscriber, subscriber, NodeSubscription.State.pending, subscriptionId);
        final NodeSubscription secondSubscription = new NodeSubscription(mockNode, subscriber, subscriber, NodeSubscription.State.subscribed, subscriptionId);

        // Execute system under test.
        provider.updateSubscription(mockNode, firstSubscription);
        provider.updateSubscription(mockNode, secondSubscription);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ArrayList<>());
        assertEquals(1, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation nodeOperation = pending.get(0);
        assertEquals(mockNode.getUniqueIdentifier(), nodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.UPDATE_SUBSCRIPTION, nodeOperation.action);
        assertEquals(subscriptionId, nodeOperation.subscription.getID());
        assertEquals(NodeSubscription.State.subscribed, nodeOperation.subscription.getState()); // match the second subscription.
        assertNull(nodeOperation.affiliate);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#createSubscription(Node, NodeSubscription)}
     * and {@link CachingPubsubPersistenceProvider#removeSubscription(NodeSubscription)} creates and immediately removes
     * a subscription.
     *
     * The caching provider can optimize these two operations, where the 'net effect' of them is to have no operation.
     *
     * Therefor, this test asserts that after each invocation, no operation is scheduled.
     */
    @Test
    public void testDeleteSubscriptionAfterCreateSubscription() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID subscriber = new JID("test-user-1", "example.org", null);
        final String subscriptionId = "test-subscription-id-" + StringUtils.randomString(7);
        final NodeSubscription subscription = new NodeSubscription(mockNode, subscriber, subscriber, NodeSubscription.State.subscribed, subscriptionId);

        // Execute system under test.
        provider.createSubscription(mockNode, subscription);
        provider.removeSubscription(subscription);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ArrayList<>());
        assertEquals(0, pending.size());

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#removeSubscription(NodeSubscription)} and
     * {@link CachingPubsubPersistenceProvider#createSubscription(Node, NodeSubscription)} removes (a subscription that
     * is thought to preexist) and then recreate a subscription again.
     *
     * This test asserts that after both invocations, two corresponding operation are scheduled in that order.
     */
    @Test
    public void testDeleteSubscriptionDoesNotVoidNewerCreate() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID subscriber = new JID("test-user-1", "example.org", null);
        final String subscriptionId = "test-subscription-id-" + StringUtils.randomString(7);
        final NodeSubscription subscription = new NodeSubscription(mockNode, subscriber, subscriber, NodeSubscription.State.subscribed, subscriptionId);

        // Execute system under test.
        provider.removeSubscription(subscription);
        provider.createSubscription(mockNode, subscription);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ArrayList<>());
        assertEquals(2, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation firstOperation = pending.get(0);
        assertEquals(mockNode.getUniqueIdentifier(), firstOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.REMOVE_SUBSCRIPTION, firstOperation.action);
        assertEquals(subscriptionId, firstOperation.subscription.getID());
        assertNull(firstOperation.affiliate);

        final CachingPubsubPersistenceProvider.NodeOperation secondOperation = pending.get(1);
        assertEquals(mockNode.getUniqueIdentifier(), secondOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.CREATE_SUBSCRIPTION, secondOperation.action);
        assertEquals(subscriptionId, secondOperation.subscription.getID());
        assertNull(secondOperation.affiliate);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#createSubscription(Node, NodeSubscription)},
     * {@link CachingPubsubPersistenceProvider#removeSubscription(NodeSubscription)} and
     * {@link CachingPubsubPersistenceProvider#createSubscription(Node, NodeSubscription)}
     * creates, then removes and then recreate a subscription again.
     *
     * The caching provider can optimize the first two operations, where the 'net effect' of them is to have no operation.
     *
     * Therefor, this test asserts that after all invocations, only one operation that represents the last invocation,
     * is scheduled.
     */
    @Test
    public void testDeleteSubscriptionDoesNotVoidNewerCreate2() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID subscriber = new JID("test-user-1", "example.org", null);
        final String subscriptionId = "test-subscription-id-" + StringUtils.randomString(7);
        final NodeSubscription subscription = new NodeSubscription(mockNode, subscriber, subscriber, NodeSubscription.State.subscribed, subscriptionId);

        // Execute system under test.
        provider.createSubscription(mockNode, subscription);
        provider.removeSubscription(subscription);
        provider.createSubscription(mockNode, subscription);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ArrayList<>());
        assertEquals(1, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation nodeOperation = pending.get(0);
        assertEquals(mockNode.getUniqueIdentifier(), nodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.CREATE_SUBSCRIPTION, nodeOperation.action);
        assertEquals(subscriptionId, nodeOperation.subscription.getID());
        assertNull(nodeOperation.affiliate);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#updateSubscription(Node, NodeSubscription)}
     * and {@link CachingPubsubPersistenceProvider#removeSubscription(NodeSubscription)} first updates (a subscription
     * that is thought to preexist) and then removes that subscription.
     *
     * The caching provider can optimize the two operations, as the 'net effect' of them is to have removal.
     *
     * Therefor, this test asserts that after all invocations, only one operation that represents the last invocation,
     * is scheduled.
     */
    @Test
    public void testDeleteSubscriptionReplacesOlderUpdate() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID subscriber = new JID("test-user-1", "example.org", null);
        final String subscriptionId = "test-subscription-id-" + StringUtils.randomString(7);
        final NodeSubscription subscription = new NodeSubscription(mockNode, subscriber, subscriber, NodeSubscription.State.subscribed, subscriptionId);

        // Execute system under test.
        provider.updateSubscription(mockNode, subscription);
        provider.removeSubscription(subscription);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ArrayList<>());
        assertEquals(1, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation nodeOperation = pending.get(0);
        assertEquals(mockNode.getUniqueIdentifier(), nodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.REMOVE_SUBSCRIPTION, nodeOperation.action);
        assertEquals(subscriptionId, nodeOperation.subscription.getID());
        assertNull(nodeOperation.affiliate);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }


    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#createSubscription(Node, NodeSubscription)},
     * {@link CachingPubsubPersistenceProvider#updateSubscription(Node, NodeSubscription)} and
     * {@link CachingPubsubPersistenceProvider#removeSubscription(NodeSubscription)} a subscription is created, updated
     * and immediately removes again.
     *
     * The caching provider can optimize these three operations, where the 'net effect' of them is to have no operation.
     *
     * Therefor, this test asserts that after each invocation, no operation is scheduled.
     */
    @Test
    public void testDeleteSubscriptionVoidsOlderCreateWithUpdate() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID subscriber = new JID("test-user-1", "example.org", null);
        final String subscriptionId = "test-subscription-id-" + StringUtils.randomString(7);
        final NodeSubscription subscription = new NodeSubscription(mockNode, subscriber, subscriber, NodeSubscription.State.subscribed, subscriptionId);

        // Execute system under test.
        provider.createSubscription(mockNode, subscription);
        provider.updateSubscription(mockNode, subscription);
        provider.removeSubscription(subscription);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ArrayList<>());
        assertEquals(0, pending.size());

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#removeSubscription(NodeSubscription)}
     * attempts to remove the same subscription 'twice'.
     *
     * This tests an erroneous invocation. The design of the caching provider is such that it shouldn't try to 'clean up'
     * such misbehavior. Instead, it should pass this through to the delegate, which is expected to generate an
     * appropriate error response.
     *
     * Therefor, this test asserts that for each invocation, a corresponding operation is scheduled.
     */
    @Test
    public void testDeleteSubscriptionTwice() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID subscriber = new JID("test-user-1", "example.org", null);
        final String subscriptionId = "test-subscription-id-" + StringUtils.randomString(7);
        final NodeSubscription subscription = new NodeSubscription(mockNode, subscriber, subscriber, NodeSubscription.State.subscribed, subscriptionId);

        // Execute system under test.
        provider.removeSubscription(subscription);
        provider.removeSubscription(subscription);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ArrayList<>());
        assertEquals(2, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation firstNodeOperation = pending.get(0);
        assertEquals(mockNode.getUniqueIdentifier(), firstNodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.REMOVE_SUBSCRIPTION, firstNodeOperation.action);
        assertEquals(subscriptionId, firstNodeOperation.subscription.getID());
        assertNull(firstNodeOperation.affiliate);

        final CachingPubsubPersistenceProvider.NodeOperation secondNodeOperation = pending.get(1);
        assertEquals(mockNode.getUniqueIdentifier(), secondNodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.REMOVE_SUBSCRIPTION, secondNodeOperation.action);
        assertEquals(subscriptionId, firstNodeOperation.subscription.getID());
        assertNull(secondNodeOperation.affiliate);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#createSubscription(Node, NodeSubscription)}
     * and {@link CachingPubsubPersistenceProvider#removeSubscription(NodeSubscription)} creates a subscription, and
     * then removes a different subscription.
     *
     * As these operations relate to two different subscriptions, the caching provider MUST NOT optimize these two
     * operations (where the 'net effect' of them is to have no operation).
     *
     * Therefor, this test asserts that after both invocations, two operations are scheduled.
     */
    @Test
    public void testDeleteSubscriptionAfterCreateDifferentSubscription() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID subscriber = new JID("test-user-1", "example.org", null);
        final String firstSubscriptionId = "test-subscription-id-" + StringUtils.randomString(7);
        final NodeSubscription firstSubscription = new NodeSubscription(mockNode, subscriber, subscriber, NodeSubscription.State.pending, firstSubscriptionId);
        final String secondSubscriptionId = "test-subscription-id-" + StringUtils.randomString(7);
        final NodeSubscription secondSubscription = new NodeSubscription(mockNode, subscriber, subscriber, NodeSubscription.State.unconfigured, secondSubscriptionId);

        // Execute system under test.
        provider.createSubscription(mockNode, firstSubscription);
        provider.removeSubscription(secondSubscription);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ArrayList<>());
        assertEquals(2, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation firstNodeOperation = pending.get(0);
        assertEquals(mockNode.getUniqueIdentifier(), firstNodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.CREATE_SUBSCRIPTION, firstNodeOperation.action);
        assertEquals(firstSubscriptionId, firstNodeOperation.subscription.getID());
        assertNull(firstNodeOperation.affiliate);

        final CachingPubsubPersistenceProvider.NodeOperation secondNodeOperation = pending.get(1);
        assertEquals(mockNode.getUniqueIdentifier(), secondNodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.REMOVE_SUBSCRIPTION, secondNodeOperation.action);
        assertEquals(secondSubscriptionId, secondNodeOperation.subscription.getID());
        assertNull(secondNodeOperation.affiliate);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#updateSubscription(Node, NodeSubscription)}
     * attempts to update two different subscriptions (with a different ID, but same user and same node).
     *
     * As these operations relate to two different subscriptions, the caching provider MUST NOT optimize these two
     * operations (where the 'net effect' of them is to have no operation).
     *
     * Therefor, this test asserts that after both invocations, two operations are scheduled.
     */
    @Test
    public void testUpdateTwoSubscriptions() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID subscriber = new JID("test-user-1", "example.org", null);
        final String firstSubscriptionId = "test-subscription-id-" + StringUtils.randomString(7);
        final NodeSubscription firstSubscription = new NodeSubscription(mockNode, subscriber, subscriber, NodeSubscription.State.pending, firstSubscriptionId);
        final String secondSubscriptionId = "test-subscription-id-" + StringUtils.randomString(7);
        final NodeSubscription secondSubscription = new NodeSubscription(mockNode, subscriber, subscriber, NodeSubscription.State.unconfigured, secondSubscriptionId);

        // Execute system under test.
        provider.updateSubscription(mockNode, firstSubscription);
        provider.updateSubscription(mockNode, secondSubscription);

        // Verify results.
        final List<CachingPubsubPersistenceProvider.NodeOperation> pending = provider.nodesToProcess.computeIfAbsent(mockNode.getUniqueIdentifier(), id -> new ArrayList<>());
        assertEquals(2, pending.size());

        final CachingPubsubPersistenceProvider.NodeOperation firstNodeOperation = pending.get(0);
        assertEquals(mockNode.getUniqueIdentifier(), firstNodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.UPDATE_SUBSCRIPTION, firstNodeOperation.action);
        assertEquals(firstSubscriptionId, firstNodeOperation.subscription.getID());
        assertNull(firstNodeOperation.affiliate);

        final CachingPubsubPersistenceProvider.NodeOperation secondNodeOperation = pending.get(1);
        assertEquals(mockNode.getUniqueIdentifier(), secondNodeOperation.node.getUniqueIdentifier());
        assertEquals(CachingPubsubPersistenceProvider.NodeOperation.Action.UPDATE_SUBSCRIPTION, secondNodeOperation.action);
        assertEquals(secondSubscriptionId, secondNodeOperation.subscription.getID());
        assertNull(secondNodeOperation.affiliate);

        assertEquals(0, provider.itemsPending.size());
        assertEquals(0, provider.itemsToAdd.size());
        assertEquals(0, provider.itemsToDelete.size());
    }

    /**
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#createSubscription(Node, NodeSubscription)}
     * and {@link CachingPubsubPersistenceProvider#removeNode(Node)} attempts to create a subscription for a node, after
     * which the node is removed.
     *
     * When a node is deleted, all its associated data (including items, subscriptions and affiliations) are removed.
     * Any pending operations on that node can thus be removed.
     *
     * The caching provider can optimize the operations, where the 'net effect' of them is to have only the remove-node
     * operation.
     *
     * Therefor, this test asserts that after both invocations, one operation is scheduled.
     */
    @Test
    public void testDeleteNodeVoidsCreateSubscription() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID subscriber = new JID("test-user-1", "example.org", null);
        final String subscriptionId = "test-subscription-id-" + StringUtils.randomString(7);
        final NodeSubscription subscription = new NodeSubscription(mockNode, subscriber, subscriber, NodeSubscription.State.subscribed, subscriptionId);

        // Execute system under test.
        provider.createSubscription(mockNode, subscription);
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
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#updateSubscription(Node, NodeSubscription)}
     * and {@link CachingPubsubPersistenceProvider#removeNode(Node)} attempts to update a subscription for a node, after
     * which the node is removed.
     *
     * When a node is deleted, all its associated data (including items, subscriptions and affiliations) are removed.
     * Any pending operations on that node can thus be removed.
     *
     * The caching provider can optimize the operations, where the 'net effect' of them is to have only the remove-node
     * operation.
     *
     * Therefor, this test asserts that after both invocations, one operation is scheduled.
     */
    @Test
    public void testDeleteNodeVoidsUpdateSubscription() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID subscriber = new JID("test-user-1", "example.org", null);
        final String subscriptionId = "test-subscription-id-" + StringUtils.randomString(7);
        final NodeSubscription subscription = new NodeSubscription(mockNode, subscriber, subscriber, NodeSubscription.State.subscribed, subscriptionId);

        // Execute system under test.
        provider.updateSubscription(mockNode, subscription);
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
     * Executes a test that, through {@link CachingPubsubPersistenceProvider#removeSubscription(NodeSubscription)}
     * and {@link CachingPubsubPersistenceProvider#removeNode(Node)} attempts to remove a subscription for a node, after
     * which the node is removed.
     *
     * When a node is deleted, all its associated data (including items, subscriptions and affiliations) are removed.
     * Any pending operations on that node can thus be removed.
     *
     * The caching provider can optimize the operations, where the 'net effect' of them is to have only the remove-node
     * operation.
     *
     * Therefor, this test asserts that after both invocations, one operation is scheduled.
     */
    @Test
    public void testDeleteNodeVoidsDeleteSubscription() throws Exception
    {
        // Setup test fixture.
        final CachingPubsubPersistenceProvider provider = new CachingPubsubPersistenceProvider();
        final Node mockNode = Mockito.mock(Node.class);
        Mockito.lenient().when(mockNode.getUniqueIdentifier()).thenReturn(new Node.UniqueIdentifier("mock-service-1", "mock-node-a"));
        final JID subscriber = new JID("test-user-1", "example.org", null);
        final String subscriptionId = "test-subscription-id-" + StringUtils.randomString(7);
        final NodeSubscription subscription = new NodeSubscription(mockNode, subscriber, subscriber, NodeSubscription.State.subscribed, subscriptionId);

        // Execute system under test.
        provider.removeSubscription(subscription);
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
}
