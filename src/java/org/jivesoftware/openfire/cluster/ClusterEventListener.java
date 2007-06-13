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
     *
     * @param oldNodeID nodeID used by this JVM before joining the cluster.
     */
    void joinedCluster(byte[] oldNodeID);

    /**
     * Notification event indicating that this JVM is about to leave the cluster. This could
     * happen when disabling clustering support, removing the enterprise plugin that provides
     * clustering support or even shutdown the server.<p>
     *
     * At this point the CacheFactory is still holding clustered caches. That means that
     * modifications to the caches will be reflected in the cluster.<p>
     *
     * Use {@link org.jivesoftware.openfire.XMPPServer#isShuttingDown()} to figure out if the
     * server is being shutdown.
     */
    void leavingCluster();

    /**
     * Notification event indicating that this JVM is no longer part of the cluster. This could
     * happen when disabling clustering support, removing the enterprise plugin that provides
     * clustering support or connection to cluster got lost. If connection to cluster was lost
     * then this event will not be predated by the {@link #leavingCluster()} event.<p>
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
     * Notification event indicating that this JVM is now the senior cluster member. This
     * could either happen when initially joining the cluster or when the senior cluster
     * member node left the cluster and this JVM was marked as the new senior cluster member.<p>
     *
     * Moreover, in the case of a "split brain" scenario (ie. separated cluster islands) each
     * island will have its own senior cluster member. However, when the islands meet again there
     * could only be one senior cluster member so one of the senior cluster members will stop playing
     * that role. When that happens the JVM no longer playing that role will receive the
     * {@link #leftCluster()} and {@link #joinedCluster(byte[])} events.
     */
    void markedAsSeniorClusterMember();
}
