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
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A cluster manager is responsible for triggering events related to clustering.
 * A future version will also provide statistics about the cluster.
 *
 * @author Gaston Dombiak
 */
public class ClusterManager {

    private static String CLUSTER_PROPERTY_NAME = "cache.clustering.enabled";
    private static Queue<ClusterEventListener> listeners = new ConcurrentLinkedQueue<ClusterEventListener>();

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
     * Dispatches an event to all listeners.
     *
     * @param eventType the event type.
     */
    public static void dispatchEvent(EventType eventType) {
        // TODO Dispath events in another thread to prevent deadlocks in coherence 
        for (ClusterEventListener listener : listeners) {
            try {
                switch (eventType) {
                    case joined_cluster: {
                        listener.joinedCluster();
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
    }

    /**
     * Starts the cluster service if clustering is enabled. The process of starting clustering
     * will recreate caches as distributed caches.<p>
     *
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

    /**
     * Represents valid event types.
     */
    public enum EventType {

        /**
         * This JVM joined a cluster.
         */
        joined_cluster,

        /**
         * This JVM is no longer part of a cluster. 
         */
        left_cluster,

        /**
         * This JVM is now the senior cluster member. 
         */
        marked_senior_cluster_member
    }
}
