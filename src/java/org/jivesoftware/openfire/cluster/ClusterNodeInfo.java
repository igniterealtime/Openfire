/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.cluster;

/**
 * Basic information about a cluster node.
 *
 * @author Gaston Dombiak
 */
public interface ClusterNodeInfo {

    /**
     * Returns the hostname where the cluster node is running.
     *
     * @return the hostname where the cluster node is running.
     */
    String getHostName();

    /**
     * Returns the ID that uniquely identifies this node.
     *
     * @return the ID that uniquely identifies this node.
     */
    NodeID getNodeID();

    /**
     * Return the date/time value (in cluster time) that the Member joined.
     *
     * @return the date/time value (in cluster time) that the Member joined.
     */
    long getJoinedTime();

    /**
     * Returns true if this member is the senior member in the cluster.
     *
     * @return true if this cluster member is the senior.
     */
    boolean isSeniorMember();
}
