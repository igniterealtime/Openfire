/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
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

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.JiveProperties;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A cluster manager is responsible for triggering events related to clustering.
 * A future version will also provide statistics about the cluster.
 *
 * @author Gaston Dombiak
 */
public class ClusterManager {
	
	private static final Logger Log = LoggerFactory.getLogger(ClusterManager.class);

    public static String CLUSTER_PROPERTY_NAME = "clustering.enabled";
    private static Queue<ClusterEventListener> listeners = new ConcurrentLinkedQueue<ClusterEventListener>();
    private static BlockingQueue<Event> events = new LinkedBlockingQueue<Event>(10000);

    static {
        Thread thread = new Thread("ClusterManager events dispatcher") {
            @Override
			public void run() {
                for (; ;) {
                    try {
                        Event event = events.take();
                        EventType eventType = event.getType();
                        // Make sure that CacheFactory is getting this events first (to update cache structure)
                        if (eventType == EventType.joined_cluster && event.getNodeID() == null) {
                            // Replace standalone caches with clustered caches. Local cached data is not moved.
                            CacheFactory.joinedCluster();
                        }
                        // Now notify rest of the listeners
                        for (ClusterEventListener listener : listeners) {
                            try {
                                switch (eventType) {
                                    case joined_cluster: {
                                        if (event.getNodeID() == null) {
                                            listener.joinedCluster();
                                        }
                                        else {
                                            listener.joinedCluster(event.getNodeID());
                                        }
                                        break;
                                    }
                                    case left_cluster: {
                                        if (event.getNodeID() == null) {
                                            listener.leftCluster();
                                        }
                                        else {
                                            listener.leftCluster(event.getNodeID());
                                        }
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
                                Log.error(e.getMessage(), e);
                            }
                        }
                        // Mark event as processed
                        event.setProcessed(true);
                    } catch (InterruptedException e) {
                        Log.warn(e.getMessage(), e);
                    } catch (Exception e) {
                        Log.error(e.getMessage(), e);
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
     * This event will be triggered in another thread. This will avoid potential deadlocks
     * in Coherence.
     *
     * @param asynchronous true if event will be triggered in background
     */
    public static void fireJoinedCluster(boolean asynchronous) {
        try {
            Event event = new Event(EventType.joined_cluster, null);
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

    /**
     * Triggers event indicating that another JVM is now part of a cluster.<p>
     *
     * This event will be triggered in another thread. This will avoid potential deadlocks
     * in Coherence.
     *
     * @param nodeID    nodeID assigned to the JVM when joining the cluster.
     * @param asynchronous true if event will be triggered in background
     */
    public static void fireJoinedCluster(byte[] nodeID, boolean asynchronous) {
        try {
            Event event = new Event(EventType.joined_cluster, nodeID);
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

    /**
     * Triggers event indicating that this JVM is no longer part of the cluster. This could
     * happen when disabling clustering support or removing the enterprise plugin that provides
     * clustering support.<p>
     *
     * Moreover, if we were in a "split brain" scenario (ie. separated cluster islands) and the
     * island were this JVM belonged was marked as "old" then all nodes of that island will
     * get the <tt>left cluster event</tt> and <tt>joined cluster events</tt>. That means that
     * caches will be reset and thus will need to be repopulated again with fresh data from this JVM.
     * This also includes the case where this JVM was the senior cluster member and when the islands
     * met again then this JVM stopped being the senior member.
     */
    public static void fireLeftCluster() {
        // Now notify rest of the listeners
        for (ClusterEventListener listener : listeners) {
            try {
                listener.leftCluster();
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
        }
    }

    /**
     * Triggers event indicating that another JVM is no longer part of the cluster. This could
     * happen when disabling clustering support or removing the enterprise plugin that provides
     * clustering support.
     *
     * @param nodeID    nodeID assigned to the JVM when joining the cluster.
     */
    public static void fireLeftCluster(byte[] nodeID) {
        try {
            Event event = new Event(EventType.left_cluster, nodeID);
            events.put(event);
        } catch (InterruptedException e) {
            // Should never happen
            Log.error(e.getMessage(), e);
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
     * {@link #fireLeftCluster()} and {@link #fireJoinedCluster(boolean)} events.<p>
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
     *
     * Before starting a cluster the
     * {@link XMPPServer#setRemoteSessionLocator(org.jivesoftware.openfire.session.RemoteSessionLocator)} and
     * {@link org.jivesoftware.openfire.RoutingTable#setRemotePacketRouter(org.jivesoftware.openfire.RemotePacketRouter)}
     * need to be properly configured.
     */
    public static synchronized void startup() {
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
            CacheFactory.startClustering();
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
        // Reset packet router to use to deliver packets to remote cluster nodes
        XMPPServer.getInstance().getRoutingTable().setRemotePacketRouter(null);
        if (isClusteringStarted()) {
            Log.debug("ClusterManager: Shutting down clustered cache service.");
            CacheFactory.stopClustering();
        }
        // Reset the session locator to use
        XMPPServer.getInstance().setRemoteSessionLocator(null);
    }

    /**
     * Sets true if clustering support is enabled. An attempt to start or join
     * an existing cluster will be attempted in the service was enabled. On the
     * other hand, if disabled then this JVM will leave or stop the cluster.
     *
     * @param enabled if clustering support is enabled.
     */
    public static void setClusteringEnabled(boolean enabled) {
        if (enabled) {
            // Check that clustering is not already enabled and we are already in a cluster
            if (isClusteringEnabled() && isClusteringStarted()) {
                return;
            }
        }
        else {
            // Check that clustering is disabled
            if (!isClusteringEnabled()) {
                return;
            }
        }
        JiveGlobals.setXMLProperty(CLUSTER_PROPERTY_NAME, Boolean.toString(enabled));
        if (!enabled) {
            shutdown();
        }
        else {
            // Reload Jive properties. This will ensure that this nodes copy of the
            // properties starts correct.
           JiveProperties.getInstance().init();
           startup();
        }
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
     * Returns true if clustering is installed and can be used by this JVM
     * to join a cluster. A false value could mean that either clustering
     * support is not available or the license does not allow to have more
     * than 1 cluster node.
     *
     * @return true if clustering is installed and can be used by 
     * this JVM to join a cluster.
     */
    public static boolean isClusteringAvailable() {
        return CacheFactory.isClusteringAvailable();
    }

    /**
     * Returns true is clustering is currently being started. Once the cluster
     * is started or failed to be started this value will be false.
     *
     * @return true is clustering is currently being started.
     */
    public static boolean isClusteringStarting() {
        return CacheFactory.isClusteringStarting();
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
     * Returns basic information about the current members of the cluster or an empty
     * collection if not running in a cluster.
     *
     * @return information about the current members of the cluster or an empty
     *         collection if not running in a cluster.
     */
    public static Collection<ClusterNodeInfo> getNodesInfo() {
        return CacheFactory.getClusterNodesInfo();
    }

    /**
     * Returns the maximum number of cluster members allowed. Both values 0 and 1 mean that clustering
     * is not available. However, a value of 1 means that it's a license problem rather than not having
     * the ability to do clustering as defined with value 0.
     *
     * @return the maximum number of cluster members allowed or 0 or 1 if clustering is not allowed.
     */
    public static int getMaxClusterNodes() {
        return CacheFactory.getMaxClusterNodes();
    }

    /**
     * Returns the id of the node that is the senior cluster member. When not in a cluster the returned
     * node id will be the {@link XMPPServer#getNodeID()}.
     *
     * @return the id of the node that is the senior cluster member.
     */
    public static NodeID getSeniorClusterMember() {
        byte[] clusterMemberID = CacheFactory.getSeniorClusterMemberID();
        if (clusterMemberID == null) {
            return XMPPServer.getInstance().getNodeID();
        }
        return NodeID.getInstance(clusterMemberID);
    }

    /**
     * Returns true if the specified node ID belongs to a known cluster node
     * of this cluster.
     *
     * @param nodeID the ID of the node to verify.
     * @return true if the specified node ID belongs to a known cluster node
     *         of this cluster.
     */
    public static boolean isClusterMember(byte[] nodeID) {
        for (ClusterNodeInfo nodeInfo : getNodesInfo()) {
            if (nodeInfo.getNodeID().equals(nodeID)) {
                return true;
            }
        }
        return false;
    }

    private static class Event {
        private EventType type;
        private byte[] nodeID;
        private boolean processed;

        public Event(EventType type, byte[] oldNodeID) {
            this.type = type;
            this.nodeID = oldNodeID;
        }

        public EventType getType() {
            return type;
        }

        public byte[] getNodeID() {
            return nodeID;
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

        /**
         * This JVM joined a cluster.
         */
        joined_cluster,

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
