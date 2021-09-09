/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.cluster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A clustered cache manager is responsible for triggering events related to updates in clustered caches.
 *
 * @author Emiel van der Herberg
 */
public class ClusteredCacheEventManager {
    
    private static final Logger Log = LoggerFactory.getLogger(ClusteredCacheEventManager.class);

    private static Map<String, Queue<ClusteredCacheEntryListener>> listeners = new ConcurrentHashMap<>();
    private static BlockingQueue<CacheEntryEvent> events = new LinkedBlockingQueue<>(10000);
    private static Thread dispatcher;

    /**
     * Registers a listener to receive entry events for a specific cache.
     *
     * @param listener the listener
     * @param cacheName the name of the cache that the listener needs to receive updates from
     */
    public static void addListener(final ClusteredCacheEntryListener listener, final String cacheName) {
        getListenerQueueForCache(cacheName).add(listener);
    }

    /**
     * Unregisters a listener to receive events.
     *
     * @param listener the listener
     * @param cacheName the cache name
     */
    public static void removeListener(final ClusteredCacheEntryListener listener, final String cacheName) {
        getListenerQueueForCache(cacheName).remove(listener);
    }

    /**
     * Finds the listener queue for the specified cache, and creates it if it didn't exist yet.
     * @param cacheName name of the cache
     * @return
     */
    @Nonnull
    private static Queue<ClusteredCacheEntryListener> getListenerQueueForCache(final @Nonnull String cacheName) {
        return listeners.computeIfAbsent(cacheName, k -> new ConcurrentLinkedQueue<>());
    }

    /**
     * Instantiate and start the cache event dispatcher thread
     */
    private static void initEventDispatcher() {
        if (dispatcher == null || !dispatcher.isAlive()) {
            dispatcher = new Thread("Clustered cache events dispatcher") {
                @Override
                public void run() {
                    // exit thread if/when clustering is disabled
                    while (ClusterManager.isClusteringEnabled()) {
                        try {
                            CacheEntryEvent event = events.take();
                            EventType eventType = event.getType();

                            for (ClusteredCacheEntryListener listener : getListenerQueueForCache(event.getCacheName())) {
                                try {
                                    switch (eventType) {
                                        case entry_added: {
                                            listener.entryAdded(event.getKey(), listener.handlesValues() ? event.getNewValue() : null, event.getNodeID());
                                            break;
                                        }
                                        case entry_removed: {
                                            listener.entryRemoved(event.getKey(), listener.handlesValues() ? event.getOldValue() : null, event.getNodeID());
                                            break;
                                        }
                                        case entry_updated: {
                                            listener.entryUpdated(event.getKey(), listener.handlesValues() ? event.getOldValue() : null, listener.handlesValues() ? event.getNewValue() : null, event.getNodeID());
                                            break;
                                        }
                                        case entry_evicted: {
                                            listener.entryEvicted(event.getKey(), listener.handlesValues() ? event.getOldValue() : null, event.getNodeID());
                                            break;
                                        }
                                        case map_cleared: {
                                            listener.mapCleared(event.getNodeID());
                                            break;
                                        }
                                        case map_evicted: {
                                            listener.mapEvicted(event.getNodeID());
                                            break;
                                        }
                                        default:
                                            break;
                                    }
                                }
                                catch (Exception e) {
                                    Log.error(e.getMessage(), e);
                                }
                            }
                            // Mark event as processed
                            event.setProcessed(true);
                        } catch (Exception e) {
                            Log.warn(e.getMessage(), e);
                        }
                    }
                }
            };
            dispatcher.setDaemon(true);
            dispatcher.start();
        }
    }


    /**
     * Triggers event indicating that something has happened to an entry in a clustered cache.
     *
     * @param event object containing information about what exactly happened to which entry in which cache
     * @param asynchronous true if event will be triggered in background
     */
    private static void fireCacheEntryEvent(final CacheEntryEvent event, final boolean asynchronous) {
        try {
            Log.debug("Firing cache {} event for item {} in cache {}", event.getType(), event.getKey(), event.getCacheName());
            events.put(event);
            if (!asynchronous) {
                while (!event.isProcessed()) {
                    Thread.sleep(50);
                }
            }
        } catch (InterruptedException e) {
            // Should never happen
            Log.error(e.getMessage(), e);
        }
    }

    public static void fireEntryAddedEvent(final Object key, final Object newValue, final String cacheName, final NodeID nodeID, final boolean asynchronous) {
        CacheEntryEvent event = new CacheEntryEvent(EventType.entry_added, nodeID, cacheName, key, null, newValue);
        fireCacheEntryEvent(event, asynchronous);
    }

    public static void fireEntryRemovedEvent(final Object key, final Object oldValue, final String cacheName, final NodeID nodeID, final boolean asynchronous) {
        CacheEntryEvent event = new CacheEntryEvent(EventType.entry_removed, nodeID, cacheName, key, oldValue, null);
        fireCacheEntryEvent(event, asynchronous);
    }

    public static void fireEntryUpdatedEvent(final Object key, final Object oldValue, final String newValue, final String cacheName, final NodeID nodeID, final boolean asynchronous) {
        CacheEntryEvent event = new CacheEntryEvent(EventType.entry_updated, nodeID, cacheName, key, oldValue, newValue);
        fireCacheEntryEvent(event, asynchronous);
    }

    public static void fireEntryEvictedEvent(final Object key, final Object oldValue, final String cacheName, final NodeID nodeID, final boolean asynchronous) {
        CacheEntryEvent event = new CacheEntryEvent(EventType.entry_evicted, nodeID, cacheName, key, oldValue, null);
        fireCacheEntryEvent(event, asynchronous);
    }

    public static void fireMapClearedEvent(final String cacheName, final NodeID nodeID, final boolean asynchronous) {
        CacheEntryEvent event = new CacheEntryEvent(EventType.map_cleared, nodeID, cacheName, null, null, null);
        fireCacheEntryEvent(event, asynchronous);
    }

    public static void fireMapEvictedEvent(final String cacheName, final NodeID nodeID, final boolean asynchronous) {
        CacheEntryEvent event = new CacheEntryEvent(EventType.map_evicted, nodeID, cacheName, null, null, null);
        fireCacheEntryEvent(event, asynchronous);
    }

    private static class CacheEntryEvent {
        public CacheEntryEvent(final EventType type, final NodeID nodeID, final String cacheName, final Object key, final Object oldValue, final Object newValue) {
            this.type = type;
            this.nodeID = nodeID;
            this.cacheName = cacheName;
            this.key = key;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }
        private EventType type;
        private NodeID nodeID;
        private String cacheName;
        private boolean processed;
        private Object key;
        private Object oldValue;

        public Object getKey() {
            return key;
        }

        public Object getOldValue() {
            return oldValue;
        }

        public Object getNewValue() {
            return newValue;
        }

        private Object newValue;

        public EventType getType() {
            return type;
        }

        public NodeID getNodeID() {
            return nodeID;
        }

        public String getCacheName() {
            return cacheName;
        }

        public boolean isProcessed() {
            return processed;
        }

        public void setProcessed(boolean processed) {
            this.processed = processed;
        }

        @Override
        public String toString() {
            return super.toString() + " type: " + type;
        }
    }

    /**
     * Represents valid event types.
     */
    private enum EventType {
        entry_added,
        entry_removed,
        entry_updated,
        entry_evicted,
        map_cleared,
        map_evicted
    }
}
