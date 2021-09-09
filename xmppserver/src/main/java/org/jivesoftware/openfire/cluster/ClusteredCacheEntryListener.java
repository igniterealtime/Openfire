package org.jivesoftware.openfire.cluster;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface ClusteredCacheEntryListener<K, V> {
    void entryAdded(@Nonnull final K key, @Nullable final V newValue, @Nonnull final NodeID nodeID);
    void entryRemoved(@Nonnull final K key, @Nullable final V oldValue, @Nonnull final NodeID nodeID);
    void entryUpdated(@Nonnull final K key, @Nullable final V oldValue, @Nullable final V newValue, @Nonnull final NodeID nodeID);
    void entryEvicted(@Nonnull final K key, @Nullable final V oldValue, @Nonnull final NodeID nodeID);
    void mapCleared(@Nonnull final NodeID nodeID);
    void mapEvicted(@Nonnull final NodeID nodeID);
}
