/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2005 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.util.cache;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.XMPPServerListener;
import org.jivesoftware.openfire.cluster.ClusterEventListener;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.cluster.ClusterNodeInfo;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginClassLoader;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.util.InitializationException;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates Cache objects. The returned caches will either be local or clustered
 * depending on the clustering enabled setting and a user's license.<p>
 * <p/>
 * When clustered caching is turned on, cache usage statistics for all caches
 * that have been created are periodically published to the clustered cache
 * named "opt-$cacheStats".
 *
 * @src.include false
 */
public class CacheFactory {

    public static String LOCAL_CACHE_PROPERTY_NAME = "cache.clustering.local.class";
    public static String CLUSTERED_CACHE_PROPERTY_NAME = "cache.clustering.clustered.class";

    private static boolean clusteringStarted = false;

    /**
     * Storage for all caches that get created.
     */
    private static Map<String, Cache> caches = new ConcurrentHashMap<String, Cache>();

    private static String localCacheFactoryClass;
    private static String clusteredCacheFactoryClass;
    private static CacheFactoryStrategy cacheFactoryStrategy;
    private static Thread statsThread;

    static {
        localCacheFactoryClass = JiveGlobals.getProperty(LOCAL_CACHE_PROPERTY_NAME,
                "org.jivesoftware.util.cache.DefaultLocalCacheStrategy");
        clusteredCacheFactoryClass = JiveGlobals.getProperty(CLUSTERED_CACHE_PROPERTY_NAME,
                "com.jivesoftware.util.cache.CoherenceClusteredCacheFactory");
    }

    private CacheFactory() {
    }

    /**
     * Returns an array of all caches in the system.
     * @return an array of all caches in the system.
     */
    public static Cache[] getAllCaches() {
        List<Cache> values = new ArrayList<Cache>();
        for (Cache cache : caches.values()) {
            values.add(cache);
        }
        return values.toArray(new Cache[values.size()]);
    }


    /**
     * Returns the named cache, creating it as necessary.
     *
     * @param name         the name of the cache to create.
     * @return the named cache, creating it as necessary.
     */
    @SuppressWarnings("unchecked")
    public static synchronized <T extends Cache> T createCache(String name) {
        T cache = (T) caches.get(name);
        if (cache != null) {
            return cache;
        }

        cache = (T) cacheFactoryStrategy.createCache(name);

        return wrapCache(cache, name);
    }

    public static void lockKey(Object key, long timeout) {
        cacheFactoryStrategy.lockKey(key, timeout);
    }

    public static void unlockKey(Object key) {
        cacheFactoryStrategy.unlockKey(key);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Cache> T wrapCache(T cache, String name) {
        cache = (T) new CacheWrapper(cache);
        cache.setName(name);

        caches.put(name, cache);
        return cache;
    }

    /**
     * Returns true if this node is currently a member of a cluster. The last step of application
     * initialization is to join a cluster, so this method returns false during most of application startup.
     *
     * @return true if this node is currently a member of a cluster.
     */
    public static boolean isClusteringStarted() {
        return clusteringStarted;
    }

    /**
     * Returns a byte[] that uniquely identifies this member within the cluster or <tt>null</tt>
     * when not in a cluster.
     *
     * @return a byte[] that uniquely identifies this member within the cluster or null when not in a cluster.
     */
    public static byte[] getClusterMemberID() {
        return cacheFactoryStrategy.getClusterMemberID();
    }

    public synchronized static void clearCaches() {
        for (String cacheName : caches.keySet()) {
            Cache cache = caches.get(cacheName);
            cache.clear();
        }
    }

    /**
     * Returns a byte[] that uniquely identifies this senior cluster member or <tt>null</tt>
     * when not in a cluster.
     *
     * @return a byte[] that uniquely identifies this senior cluster member or null when not in a cluster.
     */
    public static byte[] getSeniorClusterMemberID() {
        return cacheFactoryStrategy.getSeniorClusterMemberID();
    }

    /**
     * Returns true if this member is the senior member in the cluster. If clustering
     * is not enabled, this method will also return true. This test is useful for
     * tasks that should only be run on a single member in a cluster.
     *
     * @return true if this cluster member is the senior or if clustering is not enabled.
     */
    public static boolean isSeniorClusterMember() {
        return cacheFactoryStrategy.isSeniorClusterMember();
    }

    /**
     * Returns basic information about the current members of the cluster or an empty
     * collection if not running in a cluster.
     *
     * @return information about the current members of the cluster or an empty
     *         collection if not running in a cluster.
     */
    public static Collection<ClusterNodeInfo> getClusterNodesInfo() {
        return cacheFactoryStrategy.getClusterNodesInfo();
    }

    /**
     * Returns the maximum number of cluster members allowed. A value of 0 will
     * be returned when clustering is not allowed.
     *
     * @return the maximum number of cluster members allowed or 0 if clustering is not allowed.
     */
    public static int getMaxClusterNodes() {
        return cacheFactoryStrategy.getMaxClusterNodes();
    }
    /**
     * Invokes a task on other cluster members in an asynchronous fashion. The task will not be
     * executed on the local cluster member. If clustering is not enabled, this method
     * will do nothing.
     *
     * @param task the task to be invoked on all other cluster members.
     */
    public static void doClusterTask(final ClusterTask task) {
        cacheFactoryStrategy.doClusterTask(task);
    }

    /**
     * Invokes a task on a given cluster member in an asynchronous fashion. If clustering is not enabled,
     * this method will do nothing.
     *
     * @param task the task to be invoked on the specified cluster member.
     * @param nodeID the byte array that identifies the target cluster member.
     * @throws IllegalStateException if requested node was not found or not running in a cluster. 
     */
    public static void doClusterTask(final ClusterTask task, byte[] nodeID) {
        cacheFactoryStrategy.doClusterTask(task, nodeID);
    }

    /**
     * Invokes a task on other cluster members synchronously and returns the result as a Collection
     * (method will not return until the task has been executed on each cluster member).
     * The task will not be executed on the local cluster member. If clustering is not enabled,
     * this method will return an empty collection.
     *
     * @param task               the ClusterTask object to be invoked on all other cluster members.
     * @param includeLocalMember true to run the task on the local member, false otherwise
     * @return collection with the result of the execution.
     */
    public static Collection<Object> doSynchronousClusterTask(ClusterTask task, boolean includeLocalMember) {
        return cacheFactoryStrategy.doSynchronousClusterTask(task, includeLocalMember);
    }

    /**
     * Invokes a task on a given cluster member synchronously and returns the result of
     * the remote operation. If clustering is not enabled, this method will return null.
     *
     * @param task        the ClusterTask object to be invoked on a given cluster member.
     * @param nodeID      the byte array that identifies the target cluster member.
     * @return result of remote operation or null if operation failed or operation returned null.
     * @throws IllegalStateException if requested node was not found or not running in a cluster.
     */
    public static Object doSynchronousClusterTask(ClusterTask task, byte[] nodeID) {
        return cacheFactoryStrategy.doSynchronousClusterTask(task, nodeID);
    }

    public static synchronized void initialize() throws InitializationException {
        try {
            cacheFactoryStrategy = (CacheFactoryStrategy) Class
                        .forName(localCacheFactoryClass).newInstance();
        }
        catch (InstantiationException e) {
             throw new InitializationException(e);
        }
        catch (IllegalAccessException e) {
             throw new InitializationException(e);
        }
        catch (ClassNotFoundException e) {
            throw new InitializationException(e);
        }
    }

    private static ClassLoader getClusteredCacheStrategyClassLoader(String pluginName) {
        PluginManager pluginManager = XMPPServer.getInstance().getPluginManager();
        Plugin enterprisePlugin = pluginManager.getPlugin(pluginName);
        PluginClassLoader pluginLoader = pluginManager.getPluginClassloader(enterprisePlugin);
        if (pluginLoader != null) {
            return pluginLoader;
        }
        else {
            Log.warn("Unable to find PluginClassloader for plugin: " + pluginName + " in CacheFactory.");
            return Thread.currentThread().getContextClassLoader();
        }
    }

    public static void startClustering() {
        clusteringStarted = false;
        try {
            cacheFactoryStrategy = (CacheFactoryStrategy) Class.forName(clusteredCacheFactoryClass, true,
                    getClusteredCacheStrategyClassLoader("enterprise"))
                    .newInstance();
            clusteringStarted = cacheFactoryStrategy.startCluster();
        }
        catch (Exception e) {
            Log.error("Unable to start clustering - continuing in local mode", e);
        }
        if (!clusteringStarted) {
            // Revert to local cache factory if cluster fails to start
            try {
                cacheFactoryStrategy = (CacheFactoryStrategy) Class.forName(localCacheFactoryClass).newInstance();
            } catch (Exception e) {
                Log.error("Fatal error - Failed to join the cluster and failed to use local cache", e);
            }
        }
        else {
            if (statsThread == null) {
                // Start a timing thread with 1 second of accuracy.
                statsThread = new Thread("Cache Stats") {
                    private volatile boolean destroyed = false;

                    public void run() {
                        XMPPServer.getInstance().addServerListener(new XMPPServerListener() {
                            public void serverStarted() {}

                            public void serverStopping() {
                                destroyed = true;
                            }
                        });
                        ClusterManager.addListener(new ClusterEventListener() {
                            public void joinedCluster() {}

                            public void joinedCluster(byte[] nodeID) {}

                            public void leftCluster() {
                                destroyed = true;
                                ClusterManager.removeListener(this);
                            }

                            public void leftCluster(byte[] nodeID) {}

                            public void markedAsSeniorClusterMember() {}
                        });

                        // Run the timer indefinitely.
                        while (!destroyed && ClusterManager.isClusteringEnabled()) {
                            // Publish cache stats for this cluster node (assuming clustering is
                            // enabled and there are stats to publish).
                            try {
                                cacheFactoryStrategy.updateCacheStats(caches);
                            }
                            catch (Exception e) {
                                Log.error(e);
                            }
                            try {
                                // Sleep 10 seconds.
                                sleep(10000);
                            }
                            catch (InterruptedException ie) {
                                // Ignore.
                            }
                        }
                        statsThread = null;
                        Log.debug("Cache stats thread terminated.");
                    }
                };
                statsThread.setDaemon(true);
                statsThread.start();
            }
        }
    }

    public static void stopClustering() {
        try {
            // Stop the cluster
            cacheFactoryStrategy.stopCluster();
            // Set the strategy to local
            cacheFactoryStrategy = (CacheFactoryStrategy) Class.forName(localCacheFactoryClass)
                    .newInstance();

            clusteringStarted = false;
        }
        catch (Exception e) {
            Log.error("Unable to stop clustering - continuing in clustered mode", e);
        }
    }

    /**
     * Notification message indicating that this JVM has joined a cluster.
     */
    public static void joinedCluster() {
        // Loop through local caches and switch them to clustered cache (migrate content)
        for (Cache cache : getAllCaches()) {
            CacheWrapper cacheWrapper = ((CacheWrapper) cache);
            Cache clusteredCache = cacheFactoryStrategy.createCache(cacheWrapper.getName());
            cacheWrapper.setWrappedCache(clusteredCache);
        }
    }

    /**
     * Notification message indicating that this JVM has left the cluster.
     */
    public static void leftCluster() {
        // Loop through clustered caches and change them to local caches (migrate content)
        try {
            cacheFactoryStrategy = (CacheFactoryStrategy) Class.forName(localCacheFactoryClass).newInstance();

            for (Cache cache : getAllCaches()) {
                CacheWrapper cacheWrapper = ((CacheWrapper) cache);
                Cache standaloneCache = cacheFactoryStrategy.createCache(cacheWrapper.getName());
                cacheWrapper.setWrappedCache(standaloneCache);
            }
        } catch (Exception e) {
            Log.error("Error reverting caches to local caches", e);
        }
    }
}