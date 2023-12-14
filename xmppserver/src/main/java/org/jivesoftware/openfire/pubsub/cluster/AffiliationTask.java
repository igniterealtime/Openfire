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
import org.jivesoftware.openfire.pubsub.NodeAffiliate;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Optional;

/**
 * Updates the affiliation of a particular entity, for a particular pubsub node.
 *
 * Note that this task aims to update in-memory state only: it will not apply affiliation changes to persistent data
 * storage (it is assumed that the cluster node where the task originated takes responsibility for that). As a result,
 * this task might not apply changes if the node that is the subject of this task is currently not loaded in-memory of
 * the cluster node on which this task operates.
 */
public class AffiliationTask extends NodeTask
{
    private static final Logger log = LoggerFactory.getLogger(AffiliationTask.class);

    /**
     * Address of the entity that needs an update to the affiliation with a pubsub node.
     */
    private JID jid;

    /**
     * The new pubsub node affiliation of an entity.
     */
    private NodeAffiliate.Affiliation affiliation;

    /**
     * This no-argument constructor is provided for serialization purposes. It should generally not be used otherwise.
     */
    public AffiliationTask()
    {
    }

    /**
     * Constructs a new task that updates the affiliation of a particular entity with a specific pubsub node.
     *
     * @param node The pubsub node that this task relates to.
     * @param jid The address of the entity that has an affiliation with the pubsub node.
     * @param affiliation The affiliation that the entity has with the pubsub node.
     */
    public AffiliationTask(@Nonnull Node node, @Nonnull JID jid, @Nonnull NodeAffiliate.Affiliation affiliation)
    {
        super(node);
        this.jid = jid;
        this.affiliation = affiliation;
    }

    /**
     * Provides the address of the entity that has a new affiliation with a pubsub node.
     *
     * @return the address of an entity.
     */
    public JID getJID()
    {
        return jid;
    }

    /**
     * The new affiliation with a pubsub node.
     *
     * @return a pubsub node affiliation.
     */
    public NodeAffiliate.Affiliation getAffiliation()
    {
        return affiliation;
    }
    
    @Override
    public void run() {
        // Note: this implementation should apply changes in-memory state only. It explicitly needs not update
        // persisted data storage, as this can be expected to be done by the cluster node that issued this task.
        // Applying such changes in this task would, at best, needlessly require resources.
        log.debug("[TASK] New affiliation : {}", toString());

        final Optional<Node> optNode = getNodeIfLoaded();

        // This will only occur if a PEP service is not loaded on this particular cluster node. We can safely do nothing
        // in this case since any changes that might have been applied here will also have been applied to the database
        // by the cluster node where this task originated, meaning that those changes get loaded from the database when
        // the pubsub node is retrieved from the database in the future (OF-2077)
        if (!optNode.isPresent()) {
            return;
        }

        final Node node = optNode.get();

        // Create a new affiliate if the entity is not an affiliate yet.
        NodeAffiliate affiliate = node.getAffiliate(jid);
        if (affiliate == null) {
            // No existing affiliate: create a new one.
            affiliate = new NodeAffiliate(node, jid);
            node.addAffiliate(affiliate);
        }

        affiliate.setAffiliation(affiliation);
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException
    {
        super.writeExternal(out);
        ExternalizableUtil.getInstance().writeSerializable(out, jid);
        ExternalizableUtil.getInstance().writeSerializable(out, affiliation);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
    {
        super.readExternal(in);
        jid = (JID) ExternalizableUtil.getInstance().readSerializable(in);
        affiliation = (NodeAffiliate.Affiliation) ExternalizableUtil.getInstance().readSerializable(in);
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + " [(service=" + serviceId + "), (nodeId=" + nodeId + 
                "), (JID=" + jid + "),(affiliation=" + affiliation + ")]";
    }
}
