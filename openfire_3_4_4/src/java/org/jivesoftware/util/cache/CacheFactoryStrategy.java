/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2005 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.util.cache;

import org.jivesoftware.openfire.cluster.ClusterNodeInfo;

import java.util.Collection;
import java.util.Map;

/**
 * Implementation of CacheFactory that relies on the specific clustering solution.
 *
 * @author Gaston Dombiak
 */
public interface CacheFactoryStrategy {

    /**
     * Returns true if the cluster has been started. When running in local
     * mode a true value should be returned.<p>
     *
     * An error should be logged when the cluster fails to be started.
     *
     * @return true if the cluster has been started.
     */
    boolean startCluster();

    /**
     * Stops the cluster. When not running in a cluster this request will be ignored.
     */
    void stopCluster();

    /**
     * Creates a new cache for the cache name specified. The created cache is
     * already configured. Different implementations could store the cache
     * configuration in different ways. It is recommended to store the cache
     * configuration in an external file so it is easier for customers to change
     * the default configuration.
     *
     * @param name name of the cache to create.
     * @return newly created and configured cache.
     */
    Cache createCache(String name);

    /**
     * Destroys the supplied cache.
     *
     * @param cache the cache to destroy.
     */
    void destroyCache(Cache cache);

    /**
     * Returns true if this node is the maste node of the cluster. When not running
     * in cluster mode a value of true should be returned.
     *
     * @return true if this node is the maste node of the cluster.
     */
    boolean isSeniorClusterMember();

    /**
     * Returns basic information about the current members of the cluster or an empty
     * collection if not running in a cluster.
     *
     * @return information about the current members of the cluster or an empty
     *         collection if not running in a cluster.
     */
    Collection<ClusterNodeInfo> getClusterNodesInfo();

    /**
     * Returns the maximum number of cluster members allowed. A value of 0 will
     * be returned when clustering is not allowed.
     *
     * @return the maximum number of cluster members allowed or 0 if clustering is not allowed.
     */
    int getMaxClusterNodes();
    
    /**
     * Returns a byte[] that uniquely identifies this senior cluster member or <tt>null</tt>
     * when not in a cluster.
     *
     * @return a byte[] that uniquely identifies this senior cluster member or null when not in a cluster.
     */
    byte[] getSeniorClusterMemberID();

    /**
     * Returns a byte[] that uniquely identifies this member within the cluster or <tt>null</tt>
     * when not in a cluster.
     *
     * @return a byte[] that uniquely identifies this member within the cluster or null when not in a cluster.
     */
    byte[] getClusterMemberID();

    /**
     * Invokes a task on other cluster members in an asynchronous fashion. The task will not be
     * executed on the local cluster member. If clustering is not enabled, this method
     * will do nothing.
     *
     * @param task the task to be invoked on all other cluster members.
     */
    void doClusterTask(final ClusterTask task);


    /**
     * Invokes a task on other the specified cluster member in an asynchronous fashion. If clustering is not
     * enabled, this method will do nothing.
     *
     * @param task the task to be invoked on the specified cluster member.
     * @param nodeID the byte array that identifies the target cluster member.
     * @return false if not in a cluster or specified cluster node was not found.
     */
    boolean doClusterTask(ClusterTask task, byte[] nodeID);

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
    Collection<Object> doSynchronousClusterTask(ClusterTask task, boolean includeLocalMember);

    /**
     * Invokes a task on a given cluster member synchronously and returns the result of
     * the remote operation. If clustering is not enabled, this method will return null.
     *
     * @param task        the ClusterTask object to be invoked on a given cluster member.
     * @param nodeID      the byte array that identifies the target cluster member.
     * @return result of remote operation or null if operation failed or operation returned null.
     * @throws IllegalStateException if requested node was not found.
     */
    Object doSynchronousClusterTask(ClusterTask task, byte[] nodeID);

    /**
     * Updates the statistics of the specified caches and publishes them into
     * a cache for statistics. The statistics cache is already known to the application
     * but this could change in the future (?). When not in cluster mode then
     * do nothing.<p>
     *
     * The statistics cache must contain a long array of 5 positions for each cache
     * with the following content:
     * <ol>
     *  <li>cache.getCacheSize()</li>
     *  <li>cache.getMaxCacheSize()</li>
     *  <li>cache.size()</li>
     *  <li>cache.getCacheHits()</li>
     *  <li>cache.getCacheMisses()</li>
     * </ol>
     *
     * @param caches caches to get their stats and publish them in a statistics cache.
     */
    void updateCacheStats(Map<String, Cache> caches);

    /**
     * Locks the specified key in the locking map. The map should be clusterable
     * thus locking a key is visible to the cluster. When not in cluster mode
     * the lock is only visible to this JVM.
     *
     * @param key the key to lock.
     * @param timeout number of milliseconds to wait to obtain the lock. -1 means wait forever.
     */
    void lockKey(Object key, long timeout);

    /**
     * Unlocks the specified key in the locking map. The map should be clusterable
     * thus locking a key is visible to the cluster. When not in cluster mode
     * the lock is only visible to this JVM.
     *
     * @param key the key to unlock.
     */
    void unlockKey(Object key);
}
