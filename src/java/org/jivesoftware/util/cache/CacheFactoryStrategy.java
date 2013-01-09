/**
 * $RCSfile$
 * $Revision: 3144 $
 * $Date: 2005-12-01 14:20:11 -0300 (Thu, 01 Dec 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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
package org.jivesoftware.util.cache;

import org.jivesoftware.openfire.cluster.ClusterNodeInfo;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.locks.Lock;

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
     * Returns an existing lock on the specified key or creates a new one if none was found. This
     * operation is thread safe. The supplied cache may or may not be used depending whether
     * the server is running on cluster mode or not. When not running as part of a cluster then
     * the lock will be unrelated to the cache and will only be visible in this JVM.
     *
     * @param key the object that defines the visibility or scope of the lock.
     * @param cache the cache used for holding the lock.
     * @return an existing lock on the specified key or creates a new one if none was found.
     */
    Lock getLock(Object key, Cache cache);
    
    /**
     * Get the plugin name corresponding to this clustering implementation
     * 
     * @return the plugin name for this clustering implementation
     */
    String getPluginName();

    /**
     * Returns the node info for the given cluster node
     * @param nodeID The target cluster node
     * @return The info for the cluster node, or null if not found
     */
	ClusterNodeInfo getClusterNodeInfo(byte[] nodeID);
}
