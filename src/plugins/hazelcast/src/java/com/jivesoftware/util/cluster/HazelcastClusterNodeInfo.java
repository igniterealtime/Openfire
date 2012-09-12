/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2009 Jive Software. All rights reserved.
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

package com.jivesoftware.util.cluster;

import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.cluster.ClusterNodeInfo;
import org.jivesoftware.openfire.cluster.NodeID;

import com.hazelcast.core.Member;

/**
 * Cluster Node information as provided by Hazelcast.
 *
 * @author Tom Evans
 * @author Gaston Dombiak
 */
public class HazelcastClusterNodeInfo implements ClusterNodeInfo {

    private String hostname;
    private NodeID nodeID;
    private long joinedTime;
    private boolean seniorMember;

    public HazelcastClusterNodeInfo(Member member) {
        this(member, System.currentTimeMillis());
    }

    public HazelcastClusterNodeInfo(Member member, Long joinedTime) {
        hostname = member.getInetSocketAddress().getHostName();
        nodeID = NodeID.getInstance(member.getUuid().getBytes());
        this.joinedTime = joinedTime;
        seniorMember = ClusterManager.getSeniorClusterMember().equals(member.getUuid().getBytes());
    }

    public String getHostName() {
        return hostname;
    }

    public NodeID getNodeID() {
        return nodeID;
    }

    public long getJoinedTime() {
        return joinedTime;
    }

    public boolean isSeniorMember() {
        return seniorMember;
    }
}
