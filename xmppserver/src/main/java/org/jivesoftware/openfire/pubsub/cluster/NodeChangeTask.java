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

package org.jivesoftware.openfire.pubsub.cluster;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;

import javax.annotation.Nonnull;

/**
 * Base class of clustering tasks for pubsub. It simply stores/retrieves the node.
 *
 * This implementation should not be used (and is retained for backwards compatibility only).
 *
 * This class is only safe when used for a singleton pubsub service, as instantiated through
 * {@link org.jivesoftware.openfire.pubsub.PubSubModule}
 *
 * Unlike other cluster tasks, this task will forcefully (re)load the node from backend storage on every cluster node
 * where the task is executed. This can add significant overhead, and should be avoided if possible.
 *
 * @author Robin Collier
 * @deprecated Use a task that uses unique pubsub node identifiers, and that does not forcefully load from database, such as @{link {@link NodeTask}
 */
@Deprecated // TODO Remove this implementation in Openfire 4.7 or later.
public abstract class NodeChangeTask implements ClusterTask<Void>
{
    /**
     * The node identifier, unique in context of the service, for the pubsub node that is the subject of the task.
     *
     * @see Node#getNodeID()
     */
    private String nodeId;

    /**
     * The node that is the subject of this task.
     */
    transient private Node node;

    /**
     * This no-argument constructor is provided for serialization purposes. It should generally not be used otherwise.
     */
    public NodeChangeTask()
    {
    }

    /**
     * Constructs a new task for a pubsub node.
     *
     * Note that the provided value should refer to a node that exists in the singleton pubsub service, as instantiated
     * through {@link org.jivesoftware.openfire.pubsub.PubSubModule}
     *
     * @param nodeId the (service-specific) node identifier for the pubsub node that is the subject of the task.
     */
    public NodeChangeTask(@Nonnull final String nodeId)
    {
        this.nodeId = nodeId;
    }

    /**
     * Constructs a new task for a pubsub node.
     *
     * Note that the provided value should be a node that exists in the singleton pubsub service, as instantiated
     * through {@link org.jivesoftware.openfire.pubsub.PubSubModule}
     *
     * @param node the pubsub node that is the subject of the task.
     */
    public NodeChangeTask(@Nonnull final Node node)
    {
        this.node = node;
        nodeId = node.getUniqueIdentifier().getNodeId();
    }

    /**
     * Finds the pubsub node that is the subject of this task.
     *
     * Unlike what is common practise for cluster tasks, this method will return a pubsub node even when it was
     * previously not loaded in memory. Hence, this method can introduce significant overhead when used in a cluster.
     *
     * It is advisable to use {@link NodeTask} instead.
     *
     * @return A pubsub node
     */
    public Node getNode()
    {
        if (node == null) {
            node = XMPPServer.getInstance().getPubSubModule().getNode(nodeId);
        }
        return node;
    }

    /**
     * The node identifier, unique in context of the service, for the pubsub node that is the subject of the task.
     *
     * @return a node identifier
     * @see Node#getNodeID()
     */
    public String getNodeId()
    {
        return nodeId;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException
    {
        ExternalizableUtil.getInstance().writeSafeUTF(out, nodeId);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
    {
        nodeId = ExternalizableUtil.getInstance().readSafeUTF(in);
    }
}
