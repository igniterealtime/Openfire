/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.cluster;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.cache.CacheFactory;
import org.jivesoftware.util.lock.LocalLockFactory;
import org.jivesoftware.util.lock.LockManager;

import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A cluster manager is responsible for triggering events related to clustering.
 * A future version will also provide statistics about the cluster.
 *
 * @author Gaston Dombiak
 */
public class ClusterManager {

    private static String CLUSTER_PROPERTY_NAME = "cache.clustering.enabled";
    private static Queue<ClusterEventListener> listeners = new ConcurrentLinkedQueue<ClusterEventListener>();
    private static BlockingQueue<Event> events = new LinkedBlockingQueue<Event>();

    static {
        Thread thread = new Thread("ClusterManager events dispatcher") {
            public void run() {
                for (; ;) {
                    try {
                        Event event = events.take();
                        EventType eventType = event.getType();
                        // Make sure that CacheFactory is getting this events first (to update cache structure)
                        if (eventType == EventType.joined_cluster) {
                            // Replace standalone caches with clustered caches. Local cached data is not moved.
                            CacheFactory.joinedCluster();
                        }
                        else if (eventType == EventType.left_cluster) {
                            // Replace clustered caches with standalone caches. Cached data is not moved to new cache.
                            CacheFactory.leftCluster();
                        }
                        // Now notify rest of the listeners
                        for (ClusterEventListener listener : listeners) {
                            try {
                                switch (eventType) {
                                    case joined_cluster: {
                                        listener.joinedCluster(event.getOldNodeID());
                                        break;
                                    }
                                    case leaving_cluster: {
                                        listener.leavingCluster();
                                        break;
                                    }
                                    case left_cluster: {
                                        listener.leftCluster();
                                        break;
                                    }
                                    case marked_senior_cluster_member: {
                                        listener.markedAsSeniorClusterMember();
                                        break;
                                    }
                                    default:
                                        break;
                                }
                            }
                            catch (Exception e) {
                                Log.error(e);
                            }
                        }
                        // Mark event as processed
                        event.setProcessed(true);
                    } catch (InterruptedException e) {
                        Log.warn(e);
                    } catch (Exception e) {
                        Log.error(e);
                    }
                }
            }
        };
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Registers a listener to receive events.
     *
     * @param listener the listener.
     */
    public static void addListener(ClusterEventListener listener) {
        if (listener == null) {
            throw new NullPointerException();
        }
        listeners.add(listener);
    }

    /**
     * Unregisters a listener to receive events.
     *
     * @param listener the listener.
     */
    public static void removeListener(ClusterEventListener listener) {
        listeners.remove(listener);
    }


    /**
     * Triggers event indicating that this JVM is now part of a cluster. At this point the
     * {@link org.jivesoftware.openfire.XMPPServer#getNodeID()} holds the new nodeID value and
     * the old nodeID value is passed in case the listener needs it.<p>
     * <p/>
     * When joining the cluster as the senior cluster member the {@link #fireMarkedAsSeniorClusterMember()}
     * event will be sent right after this event.<p>
     * <p/>
     * This event could be triggered in another thread. This will avoid potential deadlocks
     * in Coherence.
     *
     * @param oldNodeID    nodeID used by this JVM before joining the cluster.
     * @param asynchronous true if event will be triggered in background
     */
    public static void fireJoinedCluster(byte[] oldNodeID, boolean asynchronous) {
        try {
            Event event = new Event(EventType.joined_cluster, oldNodeID);
            events.put(event);
            if (!asynchronous) {
                while (!event.isProcessed()) {
                    Thread.sleep(100);
                }
            }
        } catch (InterruptedException e) {
            // Should never happen
        }
    }

    /**
     * Triggers event indicating that this JVM is about to leave the cluster. This could
     * happen when disabling clustering support, removing the enterprise plugin that provides
     * clustering support or even shutdown the server.<p>
     * <p/>
     * This event will be triggered in another thread. This will avoid potential deadlocks
     * in Coherence.
     */
    public static void fireLeavingCluster() {
        try {
            events.put(new Event(EventType.leaving_cluster, null));
        } catch (InterruptedException e) {
            // Should never happen
        }
    }

    /**
     * Triggers event indicating that this JVM is no longer part of the cluster. This could
     * happen when disabling clustering support or removing the enterprise plugin that provides
     * clustering support.<p>
     * <p/>
     * Moreover, if we were in a "split brain" scenario (ie. separated cluster islands) and the
     * island were this JVM belonged was marked as "old" then all nodes of that island will
     * get the <tt>left cluster event</tt> and <tt>joined cluster events</tt>. That means that
     * caches will be reset and thus will need to be repopulated again with fresh data from this JVM.
     * This also includes the case where this JVM was the senior cluster member and when the islands
     * met again then this JVM stopped being the senior member.<p>
     * <p/>
     * This event will be triggered in another thread. This will avoid potential deadlocks
     * in Coherence.
     */
    public static void fireLeftCluster() {
        try {
            events.put(new Event(EventType.left_cluster, null));
        } catch (InterruptedException e) {
            // Should never happen
        }
    }

    /**
     * Triggers event indicating that this JVM is now the senior cluster member. This
     * could either happen when initially joining the cluster or when the senior cluster
     * member node left the cluster and this JVM was marked as the new senior cluster member.<p>
     * <p/>
     * Moreover, in the case of a "split brain" scenario (ie. separated cluster islands) each
     * island will have its own senior cluster member. However, when the islands meet again there
     * could only be one senior cluster member so one of the senior cluster members will stop playing
     * that role. When that happens the JVM no longer playing that role will receive the
     * {@link #fireLeftCluster()} and {@link #fireJoinedCluster(byte[],boolean)} events.<p>
     * <p/>
     * This event will be triggered in another thread. This will avoid potential deadlocks
     * in Coherence.
     */
    public static void fireMarkedAsSeniorClusterMember() {
        try {
            events.put(new Event(EventType.marked_senior_cluster_member, null));
        } catch (InterruptedException e) {
            // Should never happen
        }
    }

    /**
     * Starts the cluster service if clustering is enabled. The process of starting clustering
     * will recreate caches as distributed caches.<p>
     * <p/>
     * Before starting a cluster the {@link LockManager#setLockFactory(org.jivesoftware.util.lock.LockFactory)},
     * {@link XMPPServer#setRemoteSessionLocator(org.jivesoftware.openfire.session.RemoteSessionLocator)} and
     * {@link org.jivesoftware.openfire.RoutingTable#setRemotePacketRouter(org.jivesoftware.openfire.RemotePacketRouter)}
     * need to be properly configured.
     */
    public static void startup() {
        if (isClusteringStarted()) {
            return;
        }
        // See if clustering should be enabled.
        if (isClusteringEnabled()) {
            if (XMPPServer.getInstance().getRemoteSessionLocator() == null) {
                throw new IllegalStateException("No RemoteSessionLocator was found.");
            }
            if (XMPPServer.getInstance().getRoutingTable().getRemotePacketRouter() == null) {
                throw new IllegalStateException("No RemotePacketRouter was found.");
            }
            // Start up the cluster and reset caches
            CacheFactory.startup();
        }
    }

    /**
     * Shuts down the clustering service. This method should be called when the Jive
     * system is shutting down, and must not be called otherwise. Failing to call
     * this method may temporarily impact cluster performance, as the system will
     * have to do extra work to recover from a non-clean shutdown.
     * If clustering is not enabled, this method will do nothing.
     */
    public static synchronized void shutdown() {
        // Reset the LockFactory to the default one
        LockManager.setLockFactory(new LocalLockFactory());
        // Reset the session locator to use
        XMPPServer.getInstance().setRemoteSessionLocator(null);
        // Reset packet router to use to deliver packets to remote cluster nodes
        XMPPServer.getInstance().getRoutingTable().setRemotePacketRouter(null);
        if (isClusteringStarted()) {
            CacheFactory.shutdown();
        }
    }

    /**
     * Sets true if clustering support is enabled. This does not mean
     * that clustering has started or that clustering will be able to start.
     *
     * @param enabled if clustering support is enabled.
     */
    public static void setClusteringEnabled(boolean enabled) {
        JiveGlobals.setXMLProperty(CLUSTER_PROPERTY_NAME, Boolean.toString(enabled));
    }

    /**
     * Returns true if clustering support is enabled. This does not mean
     * that clustering has started or that clustering will be able to start.
     *
     * @return true if clustering support is enabled.
     */
    public static boolean isClusteringEnabled() {
        return JiveGlobals.getXMLProperty(CLUSTER_PROPERTY_NAME, false);
    }

    /**
     * Returns true if this JVM is part of a cluster. The cluster may have many nodes
     * or this JVM could be the only node.
     *
     * @return true if this JVM is part of a cluster.
     */
    public static boolean isClusteringStarted() {
        return CacheFactory.isClusteringStarted();
    }

    /**
     * Returns true if this member is the senior member in the cluster. If clustering
     * is not enabled, this method will also return true. This test is useful for
     * tasks that should only be run on a single member in a cluster.
     *
     * @return true if this cluster member is the senior or if clustering is not enabled.
     */
    public static boolean isSeniorClusterMember() {
        return CacheFactory.isSeniorClusterMember();
    }

    private static class Event {
        private EventType type;
        private byte[] oldNodeID;
        private boolean processed;

        public Event(EventType type, byte[] oldNodeID) {
            this.type = type;
            this.oldNodeID = oldNodeID;
        }

        public EventType getType() {
            return type;
        }

        public byte[] getOldNodeID() {
            return oldNodeID;
        }

        public boolean isProcessed() {
            return processed;
        }

        public void setProcessed(boolean processed) {
            this.processed = processed;
        }

        public String toString() {
            return super.toString() + " type: " + type;
        }
    }

    /**
     * Represents valid event types.
     */
    private enum EventType {

        /**
         * This JVM joined a cluster.
         */
        joined_cluster,

        /**
         * This JVM is about to leave the cluster.
         */
        leaving_cluster,

        /**
         * This JVM is no longer part of the cluster.
         */
        left_cluster,

        /**
         * This JVM is now the senior cluster member.
         */
        marked_senior_cluster_member
    }
}
