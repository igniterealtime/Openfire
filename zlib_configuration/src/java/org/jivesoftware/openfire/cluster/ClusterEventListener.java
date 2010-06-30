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

/**
 * Listener for cluster events. Use {@link ClusterManager#addListener(ClusterEventListener)}
 * to add new listeners.
 *
 * @author Gaston Dombiak
 */
public interface ClusterEventListener {

    /**
     * Notification event indicating that this JVM is now part of a cluster. At this point the
     * {@link org.jivesoftware.openfire.XMPPServer#getNodeID()} holds the new nodeID value.<p>
     *
     * When joining the cluster as the senior cluster member the {@link #markedAsSeniorClusterMember()}
     * event will be sent right after this event.<p>
     *
     * At this point the CacheFactory holds clustered caches. That means that modifications
     * to the caches will be reflected in the cluster. The clustered caches were just
     * obtained from the cluster and no local cached data was automatically moved.<p>
     */
    void joinedCluster();

    /**
     * Notification event indicating that another JVM is now part of a cluster.<p>
     *
     * At this point the CacheFactory of the new node holds clustered caches. That means
     * that modifications to the caches of this JVM will be reflected in the cluster and
     * in particular in the new node.
     *
     * @param nodeID ID of the node that joined the cluster.
     */
    void joinedCluster(byte[] nodeID);

    /**
     * Notification event indicating that this JVM is no longer part of the cluster. This could
     * happen when disabling clustering support, removing the enterprise plugin that provides
     * clustering support or connection to cluster got lost.<p>
     *
     * Moreover, if we were in a "split brain" scenario (ie. separated cluster islands) and the
     * island were this JVM belonged was marked as "old" then all nodes of that island will
     * get the <tt>left cluster event</tt> and <tt>joined cluster events</tt>. That means that
     * caches will be reset and thus will need to be repopulated again with fresh data from this JVM.
     * This also includes the case where this JVM was the senior cluster member and when the islands
     * met again then this JVM stopped being the senior member.<p>
     *
     * At this point the CacheFactory holds local caches. That means that modifications to
     * the caches will only affect this JVM.
     */
    void leftCluster();

    /**
     * Notification event indicating that another JVM is no longer part of the cluster. This could
     * happen when disabling clustering support, removing the enterprise plugin that provides
     * clustering support or connection to cluster got lost.<p>
     *
     * Moreover, if we were in a "split brain" scenario (ie. separated cluster islands) and the
     * island were the other JVM belonged was marked as "old" then all nodes of that island will
     * get the <tt>left cluster event</tt> and <tt>joined cluster events</tt>. That means that
     * caches will be reset and thus will need to be repopulated again with fresh data from this JVM.
     * This also includes the case where the other JVM was the senior cluster member and when the islands
     * met again then the other JVM stopped being the senior member.<p>
     *
     * At this point the CacheFactory of the leaving node holds local caches. That means that modifications to
     * the caches of this JVM will not affect the leaving node but other cluster members.
     *
     * @param nodeID ID of the node that is left the cluster.
     */
    void leftCluster(byte[] nodeID);

    /**
     * Notification event indicating that this JVM is now the senior cluster member. This
     * could either happen when initially joining the cluster or when the senior cluster
     * member node left the cluster and this JVM was marked as the new senior cluster member.<p>
     *
     * Moreover, in the case of a "split brain" scenario (ie. separated cluster islands) each
     * island will have its own senior cluster member. However, when the islands meet again there
     * could only be one senior cluster member so one of the senior cluster members will stop playing
     * that role. When that happens the JVM no longer playing that role will receive the
     * {@link #leftCluster()} and {@link #joinedCluster()} events.
     */
    void markedAsSeniorClusterMember();
}
