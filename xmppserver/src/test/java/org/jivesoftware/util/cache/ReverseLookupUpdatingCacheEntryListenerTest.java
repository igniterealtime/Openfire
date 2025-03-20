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
package org.jivesoftware.util.cache;

import org.jivesoftware.openfire.cluster.NodeID;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReverseLookupUpdatingCacheEntryListenerTest
{
    /**
     * Simulates a scenario where one cluster node adds an entry to an otherwise empty cache, with a listener that
     * allows for entries to be owned by more than one cluster node.
     */
    @Test
    public void testAddOnceForNonUnique() throws Exception
    {
        // Setup text fixture, Simulating things for a cache with this signature: Cache<String, Set<NodeID>> cache;
        final ConcurrentMap<NodeID, Set<String>> reverseLookupMap = new ConcurrentHashMap<>();
        final ReverseLookupUpdatingCacheEntryListener<String, Set<NodeID>> listener = new ReverseLookupUpdatingCacheEntryListener<>(reverseLookupMap, false);
        final NodeID clusterNode = NodeID.getInstance(UUID.randomUUID().toString().getBytes());

        // Execute system under test.
        listener.entryAdded("somekey", null, clusterNode);

        // Assert result
        assertTrue(reverseLookupMap.containsKey(clusterNode) && reverseLookupMap.get(clusterNode).contains("somekey"));
    }

    /**
     * Simulates a scenario where two cluster node add an equal entry to an otherwise empty cache, with a listener that
     * allows for entries to be owned by more than one cluster node.
     */
    @Test
    public void testAddTwiceForNonUnique() throws Exception
    {
        // Setup text fixture, Simulating things for a cache with this signature: Cache<String, Set<NodeID>> cache;
        final ConcurrentMap<NodeID, Set<String>> reverseLookupMap = new ConcurrentHashMap<>();
        final ReverseLookupUpdatingCacheEntryListener<String, Set<NodeID>> listener = new ReverseLookupUpdatingCacheEntryListener<>(reverseLookupMap, false);
        final NodeID clusterNodeA = NodeID.getInstance(UUID.randomUUID().toString().getBytes());
        final NodeID clusterNodeB = NodeID.getInstance(UUID.randomUUID().toString().getBytes());

        // Execute system under test.
        listener.entryAdded("somekey", null, clusterNodeA);
        listener.entryAdded("somekey", null, clusterNodeB);

        // Assert result
        assertTrue(reverseLookupMap.containsKey(clusterNodeA) && reverseLookupMap.get(clusterNodeA).contains("somekey"));
        assertTrue(reverseLookupMap.containsKey(clusterNodeB) && reverseLookupMap.get(clusterNodeB).contains("somekey"));
    }

    /**
     * Simulates a scenario where one cluster node adds an entry to an otherwise empty cache, with a listener that
     * does not allow for entries to be owned by more than one cluster node.
     */
    @Test
    public void testAddOnceForUnique() throws Exception
    {
        // Setup text fixture, Simulating things for a cache with this signature: Cache<String, Set<NodeID>> cache;
        final ConcurrentMap<NodeID, Set<String>> reverseLookupMap = new ConcurrentHashMap<>();
        final ReverseLookupUpdatingCacheEntryListener<String, Set<NodeID>> listener = new ReverseLookupUpdatingCacheEntryListener<>(reverseLookupMap, true);
        final NodeID clusterNode = NodeID.getInstance(UUID.randomUUID().toString().getBytes());

        // Execute system under test.
        listener.entryAdded("somekey", null, clusterNode);

        // Assert result
        assertTrue(reverseLookupMap.containsKey(clusterNode) && reverseLookupMap.get(clusterNode).contains("somekey"));
    }

    /**
     * Simulates a scenario where two cluster node add an equal entry to an otherwise empty cache, with a listener that
     * does not allow for entries to be owned by more than one cluster node.
     */
    @Test
    public void testAddTwiceForUnique() throws Exception
    {
        // Setup text fixture, Simulating things for a cache with this signature: Cache<String, Set<NodeID>> cache;
        final ConcurrentMap<NodeID, Set<String>> reverseLookupMap = new ConcurrentHashMap<>();
        final ReverseLookupUpdatingCacheEntryListener<String, Set<NodeID>> listener = new ReverseLookupUpdatingCacheEntryListener<>(reverseLookupMap, true);
        final NodeID clusterNodeA = NodeID.getInstance(UUID.randomUUID().toString().getBytes());
        final NodeID clusterNodeB = NodeID.getInstance(UUID.randomUUID().toString().getBytes());

        // Execute system under test.
        listener.entryAdded("somekey", null, clusterNodeA);
        listener.entryAdded("somekey", null, clusterNodeB);

        // Assert result
        assertFalse(reverseLookupMap.containsKey(clusterNodeA) && reverseLookupMap.get(clusterNodeA).contains("somekey"));
        assertTrue(reverseLookupMap.containsKey(clusterNodeB) && reverseLookupMap.get(clusterNodeB).contains("somekey"));
    }

    /**
     * Simulates a scenario where a cluster node adds an entry to an otherwise empty cache, followed by an update of
     * that entry, with a listener that allows for entries to be owned by more than one cluster node.
     *
     * In this scenario, the event listeners are fired in the same order as the order in which the insertions occur.
     * Due to the asynchronous behavior, this is not guaranteed to occur (see #testUpdateForNonUniqueInWrongOrder).
     */
    @Test
    public void testUpdateForNonUnique() throws Exception
    {
        final ConcurrentMap<NodeID, Set<String>> reverseLookupMap = new ConcurrentHashMap<>();
        final ReverseLookupUpdatingCacheEntryListener<String, Set<NodeID>> listener = new ReverseLookupUpdatingCacheEntryListener<>(reverseLookupMap, false);
        final NodeID clusterNode = NodeID.getInstance(UUID.randomUUID().toString().getBytes());

        // Execute system under test.
        listener.entryAdded("somekey", null, clusterNode);
        listener.entryUpdated("somekey", null, null, clusterNode);

        // Assert result
        assertTrue(reverseLookupMap.containsKey(clusterNode) && reverseLookupMap.get(clusterNode).contains("somekey"));
    }

    /**
     * Simulates a scenario where a cluster node adds an entry to an otherwise empty cache, followed by an update of
     * that entry, with a listener that does not allow for entries to be owned by more than one cluster node.
     *
     * In this scenario, the event listeners are fired in the same order as the order in which the insertions occur.
     * Due to the asynchronous behavior, this is not guaranteed to occur (see #testUpdateForUniqueInWrongOrder).
     */
    @Test
    public void testUpdateForUnique() throws Exception
    {
        final ConcurrentMap<NodeID, Set<String>> reverseLookupMap = new ConcurrentHashMap<>();
        final ReverseLookupUpdatingCacheEntryListener<String, Set<NodeID>> listener = new ReverseLookupUpdatingCacheEntryListener<>(reverseLookupMap, true);
        final NodeID clusterNode = NodeID.getInstance(UUID.randomUUID().toString().getBytes());

        // Execute system under test.
        listener.entryAdded("somekey", null, clusterNode);
        listener.entryUpdated("somekey", null, null, clusterNode);

        // Assert result
        assertTrue(reverseLookupMap.containsKey(clusterNode) && reverseLookupMap.get(clusterNode).contains("somekey"));
    }

    /**
     * Simulates a scenario where one cluster node adds an entry to an otherwise empty cache, followed by another
     * cluster node updating that entry, establishing itself as an 'owner' of the entry, with a listener that allows for
     * entries to be owned by more than one cluster node.
     *
     * In this scenario, the event listeners are fired in the same order as the order in which the insertions occur.
     * Due to the asynchronous behavior, this is not guaranteed to occur (see #testUpdateFromAnotherNodeForNonUniqueInWrongOrder).
     */
    @Test
    public void testUpdateFromAnotherNodeForNonUnique() throws Exception
    {
        final ConcurrentMap<NodeID, Set<String>> reverseLookupMap = new ConcurrentHashMap<>();
        final ReverseLookupUpdatingCacheEntryListener<String, Set<NodeID>> listener = new ReverseLookupUpdatingCacheEntryListener<>(reverseLookupMap, false);
        final NodeID clusterNodeA = NodeID.getInstance(UUID.randomUUID().toString().getBytes());
        final NodeID clusterNodeB = NodeID.getInstance(UUID.randomUUID().toString().getBytes());

        // Execute system under test.
        listener.entryAdded("somekey", null, clusterNodeA);
        listener.entryUpdated("somekey", null, null, clusterNodeB);

        // Assert result
        assertTrue(reverseLookupMap.containsKey(clusterNodeA) && reverseLookupMap.get(clusterNodeA).contains("somekey"));
        assertTrue(reverseLookupMap.containsKey(clusterNodeB) && reverseLookupMap.get(clusterNodeB).contains("somekey"));
    }

    /**
     * Simulates a scenario where one cluster node adds an entry to an otherwise empty cache, followed by another
     * cluster node updating that entry, establishing itself as an 'owner' of the entry, with a listener that does not
     * allow entries to be owned by more than one cluster node.
     *
     * In this scenario, the event listeners are fired in the same order as the order in which the insertions occur.
     * Due to the asynchronous behavior, this is not guaranteed to occur (see #testUpdateFromAnotherNodeForUniqueInWrongOrder).
     */
    @Test
    public void testUpdateFromAnotherNodeForUnique() throws Exception
    {
        final ConcurrentMap<NodeID, Set<String>> reverseLookupMap = new ConcurrentHashMap<>();
        final ReverseLookupUpdatingCacheEntryListener<String, Set<NodeID>> listener = new ReverseLookupUpdatingCacheEntryListener<>(reverseLookupMap, true);
        final NodeID clusterNodeA = NodeID.getInstance(UUID.randomUUID().toString().getBytes());
        final NodeID clusterNodeB = NodeID.getInstance(UUID.randomUUID().toString().getBytes());

        // Execute system under test.
        listener.entryAdded("somekey", null, clusterNodeA);
        listener.entryUpdated("somekey", null, null, clusterNodeB);

        // Assert result
        assertFalse(reverseLookupMap.containsKey(clusterNodeA) && reverseLookupMap.get(clusterNodeA).contains("somekey"));
        assertTrue(reverseLookupMap.containsKey(clusterNodeB) && reverseLookupMap.get(clusterNodeB).contains("somekey"));
    }

    /**
     * Simulates a scenario where a cluster node adds an entry to an otherwise empty cache, followed by an update of
     * that entry, with a listener that allows for entries to be owned by more than one cluster node.
     *
     * In this scenario, the events that are generated by these actions arrive in the reversed order (which, as this is
     * an async operation, can occur).
     */
    @Test
    public void testUpdateForNonUniqueInWrongOrder() throws Exception
    {
        final ConcurrentMap<NodeID, Set<String>> reverseLookupMap = new ConcurrentHashMap<>();
        final ReverseLookupUpdatingCacheEntryListener<String, Set<NodeID>> listener = new ReverseLookupUpdatingCacheEntryListener<>(reverseLookupMap, false);
        final NodeID clusterNode = NodeID.getInstance(UUID.randomUUID().toString().getBytes());

        // Execute system under test.
        listener.entryUpdated("somekey", null, null, clusterNode);
        listener.entryAdded("somekey", null, clusterNode);

        // Assert result
        assertTrue(reverseLookupMap.containsKey(clusterNode) && reverseLookupMap.get(clusterNode).contains("somekey"));
    }

    /**
     * Simulates a scenario where a cluster node adds an entry to an otherwise empty cache, followed by an update of
     * that entry, with a listener that does not allow for entries to be owned by more than one cluster node.
     *
     * In this scenario, the events that are generated by these actions arrive in the reversed order (which, as this is
     * an async operation, can occur).
     */
    @Test
    public void testUpdateForUniqueInWrongOrder() throws Exception
    {
        final ConcurrentMap<NodeID, Set<String>> reverseLookupMap = new ConcurrentHashMap<>();
        final ReverseLookupUpdatingCacheEntryListener<String, Set<NodeID>> listener = new ReverseLookupUpdatingCacheEntryListener<>(reverseLookupMap, true);
        final NodeID clusterNode = NodeID.getInstance(UUID.randomUUID().toString().getBytes());

        // Execute system under test.
        listener.entryUpdated("somekey", null, null, clusterNode);
        listener.entryAdded("somekey", null, clusterNode);

        // Assert result
        assertTrue(reverseLookupMap.containsKey(clusterNode) && reverseLookupMap.get(clusterNode).contains("somekey"));
    }

    /**
     * Simulates a scenario where one cluster node adds an entry to an otherwise empty cache, followed by another
     * cluster node updating that entry, establishing itself as an 'owner' of the entry, with a listener that allows for
     * entries to be owned by more than one cluster node.
     *
     * In this scenario, the events that are generated by these actions arrive in the reversed order (which, as this is
     * an async operation, can occur).
     */
    @Test
    public void testUpdateFromAnotherNodeForNonUniqueInWrongOrder() throws Exception
    {
        final ConcurrentMap<NodeID, Set<String>> reverseLookupMap = new ConcurrentHashMap<>();
        final ReverseLookupUpdatingCacheEntryListener<String, Set<NodeID>> listener = new ReverseLookupUpdatingCacheEntryListener<>(reverseLookupMap, false);
        final NodeID clusterNodeA = NodeID.getInstance(UUID.randomUUID().toString().getBytes());
        final NodeID clusterNodeB = NodeID.getInstance(UUID.randomUUID().toString().getBytes());

        // Execute system under test.
        listener.entryUpdated("somekey", null, null, clusterNodeB);
        listener.entryAdded("somekey", null, clusterNodeA);

        // Assert result
        assertTrue(reverseLookupMap.containsKey(clusterNodeA) && reverseLookupMap.get(clusterNodeA).contains("somekey"));
        assertTrue(reverseLookupMap.containsKey(clusterNodeB) && reverseLookupMap.get(clusterNodeB).contains("somekey"));
    }

    /**
     * Simulates a scenario where one cluster node adds an entry to an otherwise empty cache, followed by another
     * cluster node updating that entry, establishing itself as an 'owner' of the entry, with a listener that does not
     * allow entries to be owned by more than one cluster node.
     *
     * In this scenario, the events that are generated by these actions arrive in the reversed order (which, as this is
     * an async operation, can occur).
     */
    @Test
    @Disabled("This test defines desired behavior that is not implemented in the system under test.")
    public void testUpdateFromAnotherNodeForUniqueInWrongOrder() throws Exception
    {
        final ConcurrentMap<NodeID, Set<String>> reverseLookupMap = new ConcurrentHashMap<>();
        final ReverseLookupUpdatingCacheEntryListener<String, Set<NodeID>> listener = new ReverseLookupUpdatingCacheEntryListener<>(reverseLookupMap, true);
        final NodeID clusterNodeA = NodeID.getInstance(UUID.randomUUID().toString().getBytes());
        final NodeID clusterNodeB = NodeID.getInstance(UUID.randomUUID().toString().getBytes());

        // Execute system under test.
        listener.entryUpdated("somekey", null, null, clusterNodeB);
        listener.entryAdded("somekey", null, clusterNodeA);

        // Assert result
        assertFalse(reverseLookupMap.containsKey(clusterNodeA) && reverseLookupMap.get(clusterNodeA).contains("somekey"));
        assertTrue(reverseLookupMap.containsKey(clusterNodeB) && reverseLookupMap.get(clusterNodeB).contains("somekey"));
    }
}
