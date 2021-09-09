package org.jivesoftware.openfire.cluster;

public interface ClusteredCacheEntryListener<K, V> {
    void entryAdded(K key, V newValue, NodeID nodeID);
    void entryRemoved(K key, V oldValue, NodeID nodeID);
    void entryUpdated(K key, V oldValue, V newValue, NodeID nodeID);
    void entryEvicted(K key, V oldValue, NodeID nodeID);
    void mapCleared(NodeID nodeID);
    void mapEvicted(NodeID nodeID);
    boolean handlesValues();
}
