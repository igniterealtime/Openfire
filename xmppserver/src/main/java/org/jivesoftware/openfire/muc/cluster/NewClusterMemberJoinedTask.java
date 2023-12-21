/*
 * Copyright (C) 2021 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.muc.cluster;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Task that is used by a newly joined cluster node to inform other cluster nodes that it has joined the cluster.
 * The task triggers the joinedCluster event for the newly joined node.
 *
 * @author Emiel van der Herberg
 */
public class NewClusterMemberJoinedTask implements ClusterTask<Void> {
    private static final Logger Log = LoggerFactory.getLogger(NewClusterMemberJoinedTask.class);

    private NodeID originator;

    public NewClusterMemberJoinedTask() {
        this.originator = XMPPServer.getInstance().getNodeID();
    }

    public NodeID getOriginator() {
        return originator;
    }

    @Override
    public Void getResult() {
        return null;
    }

    @Override
    public void run() {
        Log.debug("Node {} informed us that it has joined the cluster. Firing joined cluster event for that node.", originator);
        ClusterManager.fireJoinedCluster(originator.toByteArray(), true);
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        final ExternalizableUtil externalizableUtil = ExternalizableUtil.getInstance();
        externalizableUtil.writeSerializable(out, originator);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        final ExternalizableUtil externalizableUtil = ExternalizableUtil.getInstance();
        originator = (NodeID) externalizableUtil.readSerializable(in);
    }
}
