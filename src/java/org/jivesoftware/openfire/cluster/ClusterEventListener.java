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
     * Notification event indication that this JVM is now part of a cluster. The
     * {@link org.jivesoftware.openfire.XMPPServer#getNodeID()} will now return
     * a new value.<p>
     *
     * When joining the cluster as the senior cluster member the {@link #markedAsSeniorClusterMember()}
     * event will be sent right after this event.
     */
    void joinedCluster();

    /**
     * Notification event indicating that this JVM is no longer part of the cluster. This could
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
     * {@link #leftCluster()} and {@link #joinedCluster()} events.
     */
    void markedAsSeniorClusterMember();
}
