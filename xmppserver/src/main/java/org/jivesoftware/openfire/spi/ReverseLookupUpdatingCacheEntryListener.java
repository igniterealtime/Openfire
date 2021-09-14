package org.jivesoftware.openfire.spi;

import org.jivesoftware.openfire.cluster.ClusteredCacheEntryListener;
import org.jivesoftware.openfire.cluster.NodeID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * Cache entry listener implementation that maintains a reverse lookup map for the cache that is being observed.
 */
public class ReverseLookupUpdatingCacheEntryListener<K, V> implements ClusteredCacheEntryListener<K, V> {
    private final ConcurrentMap<NodeID, Set<K>> reverseCacheRepresentation;

    public ReverseLookupUpdatingCacheEntryListener(@Nonnull final ConcurrentMap<NodeID, Set<K>> reverseCacheRepresentation) {
        this.reverseCacheRepresentation = reverseCacheRepresentation;
    }

    @Override
    public void entryAdded(@Nonnull final K key, @Nullable final V value, @Nonnull final NodeID nodeID) {
        reverseCacheRepresentation.computeIfAbsent(nodeID, k -> new HashSet<>()).add(key);
    }

    @Override
    public void entryRemoved(@Nonnull final K key, @Nullable final V oldValue, @Nonnull final NodeID nodeID) {
        reverseCacheRepresentation.computeIfPresent(nodeID, (k, v) -> {
            v.remove(key);
            return v;
        });
    }

    @Override
    public void entryUpdated(@Nonnull final K key, @Nullable final V oldValue, @Nullable final V newValue, @Nonnull final NodeID nodeID) {
        // Don't do anything, because we only care about keys.
    }

    @Override
    public void entryEvicted(@Nonnull final K key, @Nullable final V oldValue, @Nonnull final NodeID nodeID) {
        entryRemoved(key, oldValue, nodeID);
    }

    @Override
    public void mapCleared(@Nonnull final NodeID nodeID) {
        reverseCacheRepresentation.remove(nodeID);
    }

    @Override
    public void mapEvicted(@Nonnull final NodeID nodeID) {
        reverseCacheRepresentation.remove(nodeID);
    }
}
