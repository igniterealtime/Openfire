/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2022 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.openfire.pubsub.NodeSubscription;
import org.jivesoftware.openfire.pubsub.NodeSubscription.State;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Optional;

/**
 * A cluster task used work with a particular subscription (a relation between an entity an a pubsub node) of a pubsub node.
 *
 * Note that this task aims to update in-memory state only: it will not apply affiliation changes to persistent data
 * storage (it is assumed that the cluster node where the task originated takes responsibility for that). As a result,
 * this task might not apply changes if the node that is the subject of this task is currently not loaded in-memory of
 * the cluster node on which this task operates.
 */
public abstract class SubscriptionTask extends NodeTask
{
    /**
     * The ID that uniquely identifies the subscription of the user in the node.
     *
     * @see NodeSubscription#getID()
     */
    private String subId;

    /**
     * The address of the entity that owns this subscription.
     *
     * @see NodeSubscription#getOwner()
     */
    private JID owner;

    /**
     * The address that is going to receive the event notifications.
     *
     * @see NodeSubscription#getJID()
     */
    private JID subJid;

    /**
     * The state of the subscription.
     *
     * @see NodeSubscription#getState()
     */
    private NodeSubscription.State state;

    /**
     * This no-argument constructor is provided for serialization purposes. It should generally not be used otherwise.
     */
    public SubscriptionTask()
    {
    }

    public SubscriptionTask(@Nonnull final NodeSubscription subscription)
    {
        super(subscription.getNode());
        subId = subscription.getID();
        state = subscription.getState();
        owner = subscription.getOwner();
        subJid = subscription.getJID();
    }

    /**
     * Returns the ID that uniquely identifies the subscription of the user in the node.
     *
     * @return a unique node subscription identifier.
     * @see NodeSubscription#getID()
     */
    public String getSubscriptionId()
    {
        return subId;
    }

    /**
     * Returns the address of the entity that owns this subscription.
     *
     * @return The address of the owner of the subscription.
     * @see NodeSubscription#getOwner()
     */
    public JID getOwner()
    {
        return owner;
    }

    /**
     * Returns the address that is going to receive the event notifications.
     *
     * @return the address that will receive notifications.
     * @see NodeSubscription#getJID()
     */
    public JID getSubscriberJid()
    {
        return subJid;
    }

    /**
     * Returns the state of the subscription.
     *
     * @return subscription state
     * @see NodeSubscription#getState()
     */
    public NodeSubscription.State getState()
    {
        return state;
    }

    /**
     * Finds the pubsub node subscription that is the subject of this task.
     *
     * Note that null, instead of a pubsub node subscription instance, might be returned when the pubsub service is not
     * currently loaded in-memory on the cluster node that the task is executing on (although there is no guarantee that
     * when this method returns a non-null node subscription, it was previously not loaded in-memory)! The rationale for
     * this is that this cluster tasks does not need to operate on data that is not in memory, as such operations are the
     * responsibility of the cluster node that initiates the cluster task.
     *
     * @return a pubsub node subscription
     */
    @Nonnull
    public Optional<NodeSubscription> getSubscriptionIfLoaded()
    {
        final Optional<Node> node = getNodeIfLoaded();

        // When this cluster node does not have the pubsub node loaded in memory, no updates are needed (OF-2077).
        return node.map(value -> new NodeSubscription(value, owner, subJid, state, subId));
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException
    {
        super.writeExternal(out);
        ExternalizableUtil.getInstance().writeSafeUTF(out, subId);
        ExternalizableUtil.getInstance().writeSerializable(out, owner);
        ExternalizableUtil.getInstance().writeSerializable(out, subJid);
        ExternalizableUtil.getInstance().writeSerializable(out, state);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
    {
        super.readExternal(in);
        subId = ExternalizableUtil.getInstance().readSafeUTF(in);
        owner = (JID) ExternalizableUtil.getInstance().readSerializable(in);
        subJid = (JID) ExternalizableUtil.getInstance().readSerializable(in);
        state = (State) ExternalizableUtil.getInstance().readSerializable(in);
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + " [(service=" + serviceId + "), (nodeId=" + nodeId + "), (owner=" + owner
                + "),(subscriber=" + subJid + "),(state=" + state + "),(id=" + subId + ")]";
    }
}
