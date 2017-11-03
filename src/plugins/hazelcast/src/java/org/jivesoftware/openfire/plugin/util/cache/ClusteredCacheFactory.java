/*
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

package org.jivesoftware.openfire.plugin.util.cache;

import com.hazelcast.config.ClasspathXmlConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.MaxSizeConfig;
import com.hazelcast.core.Cluster;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.Member;
import org.jivesoftware.openfire.JMXManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.ClusterNodeInfo;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.openfire.plugin.session.RemoteSessionLocator;
import org.jivesoftware.openfire.plugin.util.cluster.ClusterPacketRouter;
import org.jivesoftware.openfire.plugin.util.cluster.HazelcastClusterNodeInfo;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.jivesoftware.util.cache.CacheFactoryStrategy;
import org.jivesoftware.util.cache.CacheWrapper;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.jivesoftware.util.cache.ExternalizableUtilStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * CacheFactory implementation to use when using Hazelcast in cluster mode.
 *
 * @author Tom Evans
 * @author Gaston Dombiak
 */
public class ClusteredCacheFactory implements CacheFactoryStrategy {

    public static final String HAZELCAST_EXECUTOR_SERVICE_NAME =
    		JiveGlobals.getProperty("hazelcast.executor.service.name", "openfire::cluster::executor");
    private static final long MAX_CLUSTER_EXECUTION_TIME = 
    		JiveGlobals.getLongProperty("hazelcast.max.execution.seconds", 30);
    private static final long CLUSTER_STARTUP_RETRY_TIME = 
    		JiveGlobals.getLongProperty("hazelcast.startup.retry.seconds", 10);
    private static final long CLUSTER_STARTUP_RETRY_COUNT = 
    		JiveGlobals.getLongProperty("hazelcast.startup.retry.count", 1);
    private static final String HAZELCAST_CONFIG_FILE = 
    		JiveGlobals.getProperty("hazelcast.config.xml.filename", "hazelcast-cache-config.xml");
    private static final boolean HAZELCAST_JMX_ENABLED = 
    		JiveGlobals.getBooleanProperty("hazelcast.config.jmx.enabled", false);

	private static Logger logger = LoggerFactory.getLogger(ClusteredCacheFactory.class);

    /**
     * Keep serialization strategy the server was using before we set our strategy. We will
     * restore old strategy when plugin is unloaded.
     */
    private ExternalizableUtilStrategy serializationStrategy;

    /**
     * Storage for cache statistics
     */
    private static Map<String, Map<String, long[]>> cacheStats;

    private static HazelcastInstance hazelcast = null;
    private static Cluster cluster = null;
    private ClusterListener clusterListener;
    private String lifecycleListener;
    private String membershipListener;

    /**
     * Keeps that running state. Initial state is stopped.
     */
    private State state = State.stopped;

    public boolean startCluster() {
        state = State.starting;
        
        // Set the serialization strategy to use for transmitting objects between node clusters
        serializationStrategy = ExternalizableUtil.getInstance().getStrategy();
        ExternalizableUtil.getInstance().setStrategy(new ClusterExternalizableUtil());
        // Set session locator to use when in a cluster
        XMPPServer.getInstance().setRemoteSessionLocator(new RemoteSessionLocator());
        // Set packet router to use to deliver packets to remote cluster nodes
        XMPPServer.getInstance().getRoutingTable().setRemotePacketRouter(new ClusterPacketRouter());

        ClassLoader oldLoader = null;
        // Store previous class loader (in case we change it)
        oldLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader loader = new ClusterClassLoader();
        Thread.currentThread().setContextClassLoader(loader);
        int retry = 0;
        do {
            try {
	            Config config = new ClasspathXmlConfig(HAZELCAST_CONFIG_FILE);
	            config.setInstanceName("openfire");
                config.setClassLoader(loader);
	            if (JMXManager.isEnabled() && HAZELCAST_JMX_ENABLED) {
	            	config.setProperty("hazelcast.jmx", "true");
	            	config.setProperty("hazelcast.jmx.detailed", "true");
	            }
	        	hazelcast = Hazelcast.newHazelcastInstance(config);
	            cluster = hazelcast.getCluster();
	
	            // Update the running state of the cluster
	            state = cluster != null ? State.started : State.stopped;
	
	            // Set the ID of this cluster node
	            XMPPServer.getInstance().setNodeID(NodeID.getInstance(getClusterMemberID()));
	            // CacheFactory is now using clustered caches. We can add our listeners.
	            clusterListener = new ClusterListener(cluster);
	            lifecycleListener = hazelcast.getLifecycleService().addLifecycleListener(clusterListener);
	            membershipListener = cluster.addMembershipListener(clusterListener);
	            break;
	        } catch (Exception e) {
	            if (retry < CLUSTER_STARTUP_RETRY_COUNT) {
	            	logger.warn("Failed to start clustering (" +  e.getMessage() + "); " +
	            			"will retry in " + CLUSTER_STARTUP_RETRY_TIME + " seconds");
	                try { Thread.sleep(CLUSTER_STARTUP_RETRY_TIME*1000); }
	                catch (InterruptedException ie) { /* ignore */ }
	            } else {
	            	logger.error("Unable to start clustering - continuing in local mode", e);
		            state = State.stopped;
	            }
	        }
    	} while (retry++ < CLUSTER_STARTUP_RETRY_COUNT);
        
        if (oldLoader != null) {
            // Restore previous class loader
            Thread.currentThread().setContextClassLoader(oldLoader);
        }
        return cluster != null;
    }

    public void stopCluster() {
        // Stop the cache services.
        cacheStats = null;
        // Update the running state of the cluster
        state = State.stopped;
        // Stop the cluster
        Hazelcast.shutdownAll();
        cluster = null;
        if (clusterListener != null) {
	        // Wait until the server has updated its internal state
	        while (!clusterListener.isDone()) {
	            try {
	                Thread.sleep(100);
	            } catch (InterruptedException e) {
	                // Ignore
	            }
	        }
	        hazelcast.getLifecycleService().removeLifecycleListener(lifecycleListener);
	        cluster.removeMembershipListener(membershipListener);
	        lifecycleListener = null;
	        membershipListener = null;
	        clusterListener = null;
        }
        // Reset the node ID
        XMPPServer.getInstance().setNodeID(null);

        // Reset packet router to use to deliver packets to remote cluster nodes
        XMPPServer.getInstance().getRoutingTable().setRemotePacketRouter(null);
        // Reset the session locator to use
        XMPPServer.getInstance().setRemoteSessionLocator(null);
        // Set the old serialization strategy was using before clustering was loaded
        ExternalizableUtil.getInstance().setStrategy(serializationStrategy);
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
        // Determine the time to live. Note that in Hazelcast 0 means "forever", not -1
        final long openfireLifetimeInMilliseconds = CacheFactory.getMaxCacheLifetime(name);
        final int hazelcastLifetimeInSeconds = openfireLifetimeInMilliseconds < 0 ? 0 : (int) (openfireLifetimeInMilliseconds / 1000);
        // Determine the max cache size. Note that in Hazelcast the max cache size must be positive
        final long openfireMaxCacheSize = CacheFactory.getMaxCacheSize(name);
        final int hazelcastMaxCacheSize = openfireMaxCacheSize < 0 ? Integer.MAX_VALUE : (int) openfireMaxCacheSize;
        final MapConfig mapConfig = hazelcast.getConfig().getMapConfig(name);
        mapConfig.setTimeToLiveSeconds(hazelcastLifetimeInSeconds);
        mapConfig.setMaxSizeConfig(new MaxSizeConfig(hazelcastMaxCacheSize, MaxSizeConfig.MaxSizePolicy.USED_HEAP_SIZE));
        return new ClusteredCache(name, hazelcast.getMap(name), hazelcastLifetimeInSeconds);
    }

    public void destroyCache(Cache cache) {
        if (cache instanceof CacheWrapper) {
            cache = ((CacheWrapper)cache).getWrappedCache();
        }

        ClusteredCache clustered = (ClusteredCache)cache;
        clustered.destroy();
    }

    public boolean isSeniorClusterMember() {
    	if (cluster == null) { return false; }
    	// first cluster member is the oldest
    	Iterator<Member> members = cluster.getMembers().iterator();
        return members.next().getUuid().equals(cluster.getLocalMember().getUuid());
    }

    public Collection<ClusterNodeInfo> getClusterNodesInfo() {
    	return clusterListener == null ? Collections.EMPTY_LIST : clusterListener.getClusterNodesInfo();
    }

    public int getMaxClusterNodes() {
        // No longer depends on license code so just return a big number
        return 10000;
    }

    public byte[] getSeniorClusterMemberID() {
        if (cluster != null && !cluster.getMembers().isEmpty()) {
        	Member oldest = cluster.getMembers().iterator().next();
            return StringUtils.getBytes(oldest.getUuid());
        }
        else {
            return null;
        }
    }

    public byte[] getClusterMemberID() {
        if (cluster != null) {
            return StringUtils.getBytes(cluster.getLocalMember().getUuid());
        }
        else {
            return null;
        }
    }

    /**
     * Gets the pseudo-synchronized time from the cluster. While the cluster members may
     * have varying system times, this method is expected to return a timestamp that is
     * synchronized (or nearly so; best effort) across the cluster.
     * 
     * @return Synchronized time for all cluster members
     */
    public long getClusterTime() {
    	return cluster == null ? System.currentTimeMillis() : cluster.getClusterTime();
    }

    /*
     * Execute the given task on the other (non-local) cluster members.
     * Note that this method does not provide the result set for the given
     * task, as the task is run asynchronously across the cluster.
     */
    public void doClusterTask(final ClusterTask task) {
    	if (cluster == null) { return; }
        Set<Member> members = new HashSet<Member>();
        Member current = cluster.getLocalMember();
        for(Member member : cluster.getMembers()) {
        	if (!member.getUuid().equals(current.getUuid())) {
        		members.add(member);
        	}
        }
        if (members.size() > 0) {
	        // Asynchronously execute the task on the other cluster members
    		logger.debug("Executing asynchronous MultiTask: " + task.getClass().getName());
	        hazelcast.getExecutorService(HAZELCAST_EXECUTOR_SERVICE_NAME).submitToMembers(
        		new CallableTask<Object>(task), members);
        } else {
       		logger.warn("No cluster members selected for cluster task " + task.getClass().getName());
        }
    }

    /*
     * Execute the given task on the given cluster member.
     * Note that this method does not provide the result set for the given
     * task, as the task is run asynchronously across the cluster.
     */
    public void doClusterTask(final ClusterTask task, byte[] nodeID) {
    	if (cluster == null) { return; }
    	Member member = getMember(nodeID);
        // Check that the requested member was found
        if (member != null) {
            // Asynchronously execute the task on the target member
    		logger.debug("Executing asynchronous DistributedTask: " + task.getClass().getName());
	        hazelcast.getExecutorService(HAZELCAST_EXECUTOR_SERVICE_NAME).submitToMember(
	        		new CallableTask<Object>(task), member);
        } else {
        	String msg = MessageFormat.format("Requested node {0} not found in cluster", StringUtils.getString(nodeID));
	        logger.warn(msg);
        	throw new IllegalArgumentException(msg);
		}
    }

    /*
     * Execute the given task on the designated cluster members.
     * Note that this method blocks for up to MAX_CLUSTER_EXECUTION_TIME
     * (seconds) per member until the task is run on all members.
     */
    public Collection<Object> doSynchronousClusterTask(ClusterTask task, boolean includeLocalMember) {
        if (cluster == null) { return Collections.emptyList(); }
        Set<Member> members = new HashSet<Member>();
        Member current = cluster.getLocalMember();
        for(Member member : cluster.getMembers()) {
        	if (includeLocalMember || (!member.getUuid().equals(current.getUuid()))) {
        		members.add(member);
        	}
        }
        Collection<Object> result = new ArrayList<Object>();
        if (members.size() > 0) {
	        // Asynchronously execute the task on the other cluster members
        	try {
        		logger.debug("Executing MultiTask: " + task.getClass().getName());
        		Map<Member, Future<Object>> futures = hazelcast.getExecutorService(HAZELCAST_EXECUTOR_SERVICE_NAME)
        			.submitToMembers(new CallableTask<Object>(task), members);
        		long nanosLeft = TimeUnit.SECONDS.toNanos(MAX_CLUSTER_EXECUTION_TIME*members.size());
        		for (Future<Object> future : futures.values()) {
        			long start = System.nanoTime();
        			result.add(future.get(nanosLeft, TimeUnit.NANOSECONDS));
        			nanosLeft = nanosLeft - (System.nanoTime() - start);
        		}
        	} catch (TimeoutException te) {
        		logger.error("Failed to execute cluster task within " + MAX_CLUSTER_EXECUTION_TIME + " seconds", te);
        	} catch (Exception e) {
        		logger.error("Failed to execute cluster task", e);
        	}
        } else {
       		logger.warn("No cluster members selected for cluster task " + task.getClass().getName());
        }
        return result;
    }

    /*
     * Execute the given task on the designated cluster member.
     * Note that this method blocks for up to MAX_CLUSTER_EXECUTION_TIME
     * (seconds) until the task is run on the given member.
     */
    public Object doSynchronousClusterTask(ClusterTask task, byte[] nodeID) {
    	if (cluster == null) { return null; }
    	Member member = getMember(nodeID);
    	Object result = null;
        // Check that the requested member was found
        if (member != null) {
            // Asynchronously execute the task on the target member
    		logger.debug("Executing DistributedTask: " + task.getClass().getName());
            try { 
            	Future<Object> future = hazelcast.getExecutorService(HAZELCAST_EXECUTOR_SERVICE_NAME)
            			.submitToMember(new CallableTask<Object>(task), member);
    			result = future.get(MAX_CLUSTER_EXECUTION_TIME, TimeUnit.SECONDS);
        		logger.debug("DistributedTask result: " + (result == null ? "null" : result));
        	} catch (TimeoutException te) {
        		logger.error("Failed to execute cluster task within " + MAX_CLUSTER_EXECUTION_TIME + " seconds", te);
        	} catch (Exception e) {
        		logger.error("Failed to execute cluster task", e);
        	}
        } else {
        	String msg = MessageFormat.format("Requested node {0} not found in cluster", StringUtils.getString(nodeID));
	        logger.warn(msg);
        	throw new IllegalArgumentException(msg);
        }
        return result;
    }
    
    public ClusterNodeInfo getClusterNodeInfo(byte[] nodeID) {
    	if (cluster == null) { return null; }
    	ClusterNodeInfo result = null;
    	Member member = getMember(nodeID);
    	if (member != null) {
    		result = new HazelcastClusterNodeInfo(member, cluster.getClusterTime());
    	}
    	return result;
    }
    
    private Member getMember(byte[] nodeID) {
    	Member result = null;
    	for(Member member: cluster.getMembers()) {
       		if (Arrays.equals(StringUtils.getBytes(member.getUuid()), nodeID)) {
    			result = member;
    			break;
    		}
    	}
    	return result;
    }

    public void updateCacheStats(Map<String, Cache> caches) {
        if (caches.size() > 0 && cluster != null) {
            // Create the cacheStats map if necessary.
            if (cacheStats == null) {
                cacheStats = hazelcast.getMap("opt-$cacheStats");
            }
            String uid = cluster.getLocalMember().getUuid();
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
		return "hazelcast";
	}

	public Lock getLock(Object key, Cache cache) {
        if (cache instanceof CacheWrapper) {
            cache = ((CacheWrapper)cache).getWrappedCache();
        }
        return new ClusterLock(key, (ClusteredCache) cache);
    }

    private static class ClusterLock implements Lock {

        private Object key;
        private ClusteredCache cache;

        public ClusterLock(Object key, ClusteredCache cache) {
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

    private static class CallableTask<V> implements Callable<V>, Serializable {
    	private ClusterTask<V> task;
    	
    	public CallableTask(ClusterTask<V> task) {
    		this.task = task;
    	}

        public V call() {
            task.run();
            logger.debug("CallableTask[" + task.getClass().getName() + "] result: " + task.getResult());
            return task.getResult();
        }
    }
    
    private static enum State {
        stopped,
        starting,
        started
    }
}



