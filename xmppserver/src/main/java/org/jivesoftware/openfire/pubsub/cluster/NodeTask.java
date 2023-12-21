/*
 * Copyright (C) 2017-2022 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.pep.PEPServiceManager;
import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.openfire.pubsub.PubSubService;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Optional;

/**
 * A cluster task used work with a particular pubsub node.
 *
 * As with most cluster tasks, it is important to realize that these tasks are intended to interact only in a limited
 * way with the data on the cluster node where they're executed on. Generally speaking, the cluster node that initiates
 * the task takes responsibility for all of the 'heavy lifting', such as the persistence of data (in the backend data
 * storage/database) for the functionality that the cluster task relates to. The other cluster nodes typically need not
 * to repeat that effort. Specifically, the goal of executing cluster tasks on other cluster nodes should typically be
 * limited to updating in-memory state on the cluster node.
 */
public abstract class NodeTask implements ClusterTask<Void>
{
    /**
     * The unique identifier for the pubsub node that is the subject of the task.
     *
     * This value is a combination of the data that's captured in {@link #nodeId} and {@link #serviceId}, which are
     * primarily retained in the API for backwards compatibility reasons.
     *
     * @see Node#getUniqueIdentifier()
     */
    protected transient Node.UniqueIdentifier uniqueNodeIdentifier;

    /**
     * The node identifier, unique in context of the service, for the pubsub node that is the subject of the task.
     *
     * The value of this field is combined with that of {@link #serviceId} in {@link #uniqueNodeIdentifier}. To retain
     * (serialization) compatibility with older versions, the nodeId field is retained. It is, however, advisable to
     * make use of {@link #uniqueNodeIdentifier} instead, as that is guaranteed to provide a system-wide unique value
     * (whereas the nodeId value is unique only context of the pubsub service).
     *
     * @see Node#getNodeID()
     */
    protected String nodeId;

    /**
     * The service identifier for the pubsub node that is the subject of the task.
     */
    protected String serviceId;

    /**
     * This no-argument constructor is provided for serialization purposes. It should generally not be used otherwise.
     */
    protected NodeTask()
    {
    }

    /**
     * Constructs a new task for a specific pubsub node.
     *
     * @param node The pubsub node that this task operates on.
     */
    protected NodeTask(@Nonnull Node node)
    {
        uniqueNodeIdentifier = node.getUniqueIdentifier();
        nodeId = node.getUniqueIdentifier().getNodeId();
        serviceId = node.getUniqueIdentifier().getServiceIdentifier().getServiceId();
    }

    /**
     * Returns the unique identifier of the pubsub node that this task aims to update.
     *
     * Usage of this method, that provides a system-wide unique value, should generally be preferred over the use of
     * {@link #getNodeId()}, that returns a value that is unique only context of the pubsub service.
     *
     * @return A unique node identifier.
     * @see Node#getUniqueIdentifier()
     */
    public Node.UniqueIdentifier getUniqueNodeIdentifier() {
        return uniqueNodeIdentifier;
    }

    /**
     * The node identifier, unique in context of the service, for the pubsub node that is the subject of the task.
     *
     * It is advisable to make use of {@link #getUniqueNodeIdentifier()} instead of this method, as that is guaranteed
     * to provide a system-wide unique value (whereas the nodeId value is unique only context of the pubsub service).
     *
     * @return a node identifier
     * @see #getUniqueNodeIdentifier()
     * @see Node#getNodeID()
     */
    public String getNodeId()
    {
        return nodeId;
    }

    /**
     * Finds the pubsub node that is the subject of this task.
     *
     * Note that null, instead of a pubsub node instance, might be returned when the pubsub service is not currently
     * loaded in-memory on the cluster node that the task is executing on (although there is no guarantee that when this
     * method returns a non-null pubsub service, it was previously not loaded in-memory)! The rationale for this is that
     * this cluster tasks does not need to operate on data that is not in memory, as such operations are the
     * responsibility of the cluster node that initiates the cluster task.
     *
     * @return A pubsub node
     */
    @Nonnull
    public Optional<Node> getNodeIfLoaded()
    {
        final Optional<PubSubService> svc = getServiceIfLoaded();

        return svc.map(pubSubService -> pubSubService.getNode(nodeId));
    }

    /**
     * Finds the pubsub service for the pubsub node that is the subject of this task.
     *
     * Note that null, instead of a pubsub service instance, might be returned when the pubsub service is not currently
     * loaded in-memory on the cluster node that the task is executing on (although there is no guarantee that when this
     * method returns a non-null pubsub service, it was previously not loaded in-memory)! The rationale for this is that
     * this cluster tasks does not need to operate on data that is not in memory, as such operations are the
     * responsibility of the cluster node that initiates the cluster task.
     *
     * @return A pubsub service
     */
    @Nonnull
    public Optional<PubSubService> getServiceIfLoaded()
    {
        if (XMPPServer.getInstance().getPubSubModule().getServiceID().equals(serviceId)) {
            return Optional.of(XMPPServer.getInstance().getPubSubModule());
        }
        else
        {
            PEPServiceManager serviceMgr = XMPPServer.getInstance().getIQPEPHandler().getServiceManager();
            JID service = new JID( serviceId );
            return serviceMgr.hasCachedService(service) ? Optional.of(serviceMgr.getPEPService(service)) : Optional.empty();
        }
    }

    @Override
    public Void getResult()
    {
        return null;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException
    {
        ExternalizableUtil.getInstance().writeSafeUTF(out, nodeId);
        ExternalizableUtil.getInstance().writeSafeUTF(out, serviceId);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
    {
        nodeId = ExternalizableUtil.getInstance().readSafeUTF(in);
        serviceId = ExternalizableUtil.getInstance().readSafeUTF(in);
        uniqueNodeIdentifier = new Node.UniqueIdentifier( serviceId, nodeId );
    }
}
