/*
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
