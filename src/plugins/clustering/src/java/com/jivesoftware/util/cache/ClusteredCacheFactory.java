/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007-2009 Jive Software. All rights reserved.
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

package com.jivesoftware.util.cache;

import com.jivesoftware.util.cluster.CoherenceClusterNodeInfo;
import com.tangosol.net.*;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.cluster.ClusterNodeInfo;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactoryStrategy;
import org.jivesoftware.util.cache.CacheWrapper;
import org.jivesoftware.util.cache.ClusterTask;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * CacheFactory implementation to use when using Coherence in cluster mode.
 *
 * @author Gaston Dombiak
 */
public class ClusteredCacheFactory implements CacheFactoryStrategy {

    /**
     * Storage for cache statistics
     */
    private static Map<String, Map<String, long[]>> cacheStats;

    /**
     * Maintain a reference to the Coherence Cluster object so that we can get cluster time from
     * it.
     */
    private static Cluster cluster = null;

    /**
     * Service used for executing tasks across the cluster
     */
    private static InvocationService taskService;

    private ClusterListener clusterListener;
    /**
     * Keeps that running state. Initial state is stopped.
     */
    private State state = State.stopped;

    public boolean startCluster() {
        ClassLoader oldLoader = null;
        // Set that we are starting up the cluster service
        state = State.starting;
        try {
            // Store previous class loader (in case we change it)
            oldLoader = Thread.currentThread().getContextClassLoader();
            // See if the license allows for additional cluster members.
            int allowedMembers = getMaxClusterNodes();
            // If more than 1 cluster member is allowed...
            if (allowedMembers > 1) {
                ClassLoader loader = new ClusterClassLoader();
                Thread.currentThread().setContextClassLoader(loader);
                cluster = com.tangosol.net.CacheFactory.ensureCluster();
                // Make sure Coherence uses the correct class loader.
                cluster.setContextClassLoader(loader);
                int memberCount = cluster.getMemberSet().size();
                // See if adding this cluster bumps us over the allowed
                // number. If so, shut down the cluster and use local cache.
                if (memberCount > allowedMembers) {
                    com.tangosol.net.CacheFactory.shutdown();
                    cluster = null;
                    Log.error("Error joining clustered cache: your "
                            + "license only allows for " + allowedMembers +
                            " cluster nodes. Using local cache instead.");
                }
                else {
                    com.tangosol.net.CacheFactory.getCache("opt-$cacheStats");
                    taskService = (InvocationService) com.tangosol.net.CacheFactory.getService("OpenFire Cluster Service");

                    // Update the running state of the cluster
                    state = cluster != null ? State.started : State.stopped;

                    Member localMember = cluster.getLocalMember();
                    Member seniorMember = cluster.getOldestMember();

                    // Set the ID of this cluster node
                    XMPPServer.getInstance().setNodeID(NodeID.getInstance(getClusterMemberID()));
                    // Trigger cluster events
                    ClusterManager.fireJoinedCluster(false);
                    // CacheFactory is now using clustered caches. We can add our listeners.
                    clusterListener = new ClusterListener();
                    taskService.addMemberListener(clusterListener);

                    if (isSeniorClusterMember()) {
                        ClusterManager.fireMarkedAsSeniorClusterMember();
                    }
                    Log.info("Joining cluster as node: " + localMember.getUid() + ". Senior Member: " +
                            (localMember == seniorMember ? "YES" : "NO"));
                }
            }
            // Only 1 cluster member is allowed, so use local cache.
            else {
                Log.error("Error joining clustered cache: your " +
                        "license only allows for " + allowedMembers +
                        " cluster nodes. Using local cache instead.");
            }

            return cluster != null;
        }
        catch (Exception e) {
            Log.error("Unable to start clustering - continuing in local mode", e);
        }
        finally {
            if (oldLoader != null) {
                // Restore previous class loader
                Thread.currentThread().setContextClassLoader(oldLoader);
            }
        }
        // For some reason the cluster was not started so update the status
        state = State.stopped;
        return false;
    }

    public void stopCluster() {
        // Stop the cache services.
        cacheStats = null;
        taskService = null;
        // Update the running state of the cluster
        state = State.stopped;
        // Stop the cluster
        com.tangosol.net.CacheFactory.shutdown();
        cluster = null;
        // Wait until the server has updated its internal state
        while (!clusterListener.isDone()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // Ignore
            }
        }
        // Reset the node ID
        XMPPServer.getInstance().setNodeID(null);
    }

    public Cache createCache(String name) {
        // Check if cluster is being started up
        while (state == State.starting) {
            // Wait until cluster is fully started (or failed)
            try {
                Thread.sleep(250);
            }
            catch (InterruptedException e) {
                // Ignore
            }
        }
        if (state == State.stopped) {
            throw new IllegalStateException("Cannot create clustered cache when not in a cluster");
        }
        return new ClusteredCache(name);
    }

    public void destroyCache(Cache cache) {
        if (cache instanceof CacheWrapper) {
            cache = ((CacheWrapper)cache).getWrappedCache();
        }

        ClusteredCache clustered = (ClusteredCache)cache;
        clustered.destroy();
    }

    public boolean isSeniorClusterMember() {
        if (taskService != null) {
            return taskService.getInfo().getOldestMember().getUid()
                    .equals(cluster.getLocalMember().getUid());
        }
        else {
            return true;
        }
    }

    public Collection<ClusterNodeInfo> getClusterNodesInfo() {
        if (cluster == null) {
            return Collections.emptyList();
        }
        List<ClusterNodeInfo> nodesInfo = new ArrayList<ClusterNodeInfo>();
        for (Object member : cluster.getMemberSet()) {
            nodesInfo.add(new CoherenceClusterNodeInfo((Member) member));
        }
        return nodesInfo;
    }

    public int getMaxClusterNodes() {
        // No longer depends on license code so just return a big number
        return 10000;
    }

    public byte[] getSeniorClusterMemberID() {
        if (taskService != null) {
            return taskService.getInfo().getOldestMember().getUid().toByteArray();
        }
        else {
            return null;
        }
    }

    public byte[] getClusterMemberID() {
        if (cluster != null) {
            return com.tangosol.net.CacheFactory.getCluster().getLocalMember().getUid().toByteArray();
        }
        else {
            return null;
        }
    }

    public void doClusterTask(final ClusterTask task) {
        if (taskService != null) {
            Member current = taskService.getCluster().getLocalMember();
            Set setMembers = taskService.getInfo().getServiceMembers();
            // Don't send to the local instance.
            setMembers.remove(current);

            // Asynchronously execute the task.
            taskService.execute(buildInvocable(task), setMembers, null);
        }
    }

    public boolean doClusterTask(final ClusterTask task, byte[] nodeID) {
        if (taskService != null) {
            // Get members of the service
            Set setMembers = taskService.getInfo().getServiceMembers();
            // Remove all members except requested nodeID
            for (Iterator it=setMembers.iterator(); it.hasNext();) {
                Member member = (Member) it.next();
                if (!Arrays.equals(member.getUid().toByteArray(), nodeID)) {
                    it.remove();
                }
            }

            // Check that the requested member was found
            if (!setMembers.isEmpty()) {
                // Asynchronously execute the task.
                taskService.execute(buildInvocable(task), setMembers, null);
                return true;
            }
            throw new IllegalStateException("Requested node not found in cluster");
        }
        throw new IllegalStateException("Cluster service is not available");
    }

    public Collection<Object> doSynchronousClusterTask(ClusterTask task, boolean includeLocalMember) {
        if (taskService != null) {
            Member current = taskService.getCluster().getLocalMember();
            Set setMembers = taskService.getInfo().getServiceMembers();
            if (!includeLocalMember) {
                // Don't send to the local instance.
                setMembers.remove(current);
            }

            // Execute the task.
            Map map = taskService.query(buildInvocable(task), setMembers);
            return map != null ? (Collection<Object>)map.values() : Collections.emptyList();
        }
        else {
            return Collections.emptyList();
        }
    }

    public Object doSynchronousClusterTask(ClusterTask task, byte[] nodeID) {
        if (taskService != null) {
            // Get members of the service
            Set setMembers = taskService.getInfo().getServiceMembers();
            // Remove all members except requested nodeID
            for (Iterator it=setMembers.iterator(); it.hasNext();) {
                Member member = (Member) it.next();
                if (!Arrays.equals(member.getUid().toByteArray(), nodeID)) {
                    it.remove();
                }
            }

            // Check that the requested member was found
            if (!setMembers.isEmpty()) {
                // Asynchronously execute the task.
                Map map = taskService.query(buildInvocable(task), setMembers);
                return map != null && !map.isEmpty() ? map.values().toArray()[0] : null;
            }
            throw new IllegalStateException("Requested node not found in cluster");
        }
        throw new IllegalStateException("Cluster service is not available");
    }

    public void updateCacheStats(Map<String, Cache> caches) {
        if (caches.size() > 0 && cluster != null) {
            // Create the cacheStats map if necessary.
            if (cacheStats == null) {
                cacheStats = (Map<String, Map<String, long[]>>)com.tangosol.net.CacheFactory.getCache("opt-$cacheStats");
            }
            String uid = cluster.getLocalMember().getUid().toString();
            Map<String, long[]> stats = new HashMap<String, long[]>();
            for (String cacheName : caches.keySet()) {
                Cache cache = caches.get(cacheName);
                // The following information is published:
                // current size, max size, num elements, cache
                // hits, cache misses.
                long [] info = new long[5];
                info[0] = cache.getCacheSize();
                info[1] = cache.getMaxCacheSize();
                info[2] = cache.size();
                info[3] = cache.getCacheHits();
                info[4] = cache.getCacheMisses();
                stats.put(cacheName, info);
            }
            // Publish message
            cacheStats.put(uid, stats);
        }
    }

	public String getPluginName() {
		return "clustering";
	}

	public ClusterNodeInfo getClusterNodeInfo(byte[] nodeID) {
        // Get members of the service
        Set setMembers = taskService.getInfo().getServiceMembers();
        Member member = null;
        // Find the member matching the requested nodeID
        for (Iterator it=setMembers.iterator(); it.hasNext();) {
            member = (Member) it.next();
            if (Arrays.equals(member.getUid().toByteArray(), nodeID)) {
                break;
            }
        }

        // Check that the requested member was found
        if (member != null) {
            return new CoherenceClusterNodeInfo(member);
        } else {
        	return null;
        }
	}

	private static Invocable buildInvocable(final ClusterTask task) {
        return new AbstractInvocable() {
            public void run() {
                task.run();
            }

            public Object getResult() {
                return task.getResult();
            }
        };
    }

    public Lock getLock(Object key, Cache cache) {
        if (cache instanceof CacheWrapper) {
            cache = ((CacheWrapper)cache).getWrappedCache();
        }
        return new CoherenceLock(key, (ClusteredCache) cache);
    }

    private static class CoherenceLock implements Lock {

        private Object key;
        private ClusteredCache cache;

        public CoherenceLock(Object key, ClusteredCache cache) {
            this.key = key;
            this.cache = cache;
        }

        public void lock() {
            cache.lock(key, -1);
        }

        public void lockInterruptibly() throws InterruptedException {
            cache.lock(key, -1);
        }

        public boolean tryLock() {
        	return cache.lock(key, 0);
        }

        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        	return cache.lock(key, unit.toMillis(time));
        }

        public void unlock() {
            cache.unlock(key);
        }

        public Condition newCondition() {
        	throw new UnsupportedOperationException();
        }
    }

    private static enum State {
        stopped,
        starting,
        started
    }
}



