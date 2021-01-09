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

import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.openfire.pubsub.NodeAffiliate;
import org.jivesoftware.openfire.pubsub.NodeSubscription;
import org.jivesoftware.openfire.pubsub.PubSubService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * A cluster task used to create a pubsub node subscription (a relation between an entity an a pubsub node).
 *
 * Note that this task aims to update in-memory state only: it will not apply affiliation changes to persistent data
 * storage (it is assumed that the cluster node where the task originated takes responsibility for that). As a result,
 * this task might not apply changes if the node that is the subject of this task is currently not loaded in-memory of
 * the cluster node on which this task operates.
 */
public class NewSubscriptionTask extends SubscriptionTask
{
    private static final Logger log = LoggerFactory.getLogger(NewSubscriptionTask.class);

    /**
     * This no-argument constructor is provided for serialization purposes. It should generally not be used otherwise.
     */
    public NewSubscriptionTask()
    {
    }

    /**
     * Constructs a new task that creates a subscription to a pubsub node.
     *
     * @param subscription The to-be-created subscription
     */
    public NewSubscriptionTask(@Nonnull final NodeSubscription subscription)
    {
        super(subscription);
    }

    @Override
    public void run()
    {
        // Note: this implementation should apply changes in-memory state only. It explicitly needs not update
        // persisted data storage, as this can be expected to be done by the cluster node that issued this task.
        // Applying such changes in this task would, at best, needlessly require resources.
        log.debug("[TASK] New subscription : {}", toString());

        final Optional<PubSubService> optService = getServiceIfLoaded();
        final Optional<Node> optNode = getNodeIfLoaded();
        final Optional<NodeSubscription> optSubscription = getSubscriptionIfLoaded();

        // This will only occur if a PEP service is not loaded on this particular cluster node. We can safely do nothing
        // in this case since any changes that might have been applied here will also have been applied to the database
        // by the cluster node where this task originated, meaning that those changes get loaded from the database when
        // the pubsub node is retrieved from the database in the future (OF-2077)
        if (!optService.isPresent() || !optNode.isPresent() || !optSubscription.isPresent()) {
            return;
        }

        final PubSubService service = optService.get();
        final Node node = optNode.get();
        final NodeSubscription subscription = optSubscription.get();

        if (node.getAffiliate(getOwner()) == null)
        {
            // add the missing 'none' affiliation
            NodeAffiliate affiliate = new NodeAffiliate(node, getOwner());
            affiliate.setAffiliation(NodeAffiliate.Affiliation.none);
            node.addAffiliate(affiliate);
        }
        node.addSubscription(subscription);

        if (node.isPresenceBasedDelivery() && node.getSubscriptions(subscription.getOwner()).size() == 1)
        {
            if (subscription.getPresenceStates().isEmpty())
            {
                // Subscribe to the owner's presence since the node is only
                // sending events to online subscribers and this is the first
                // subscription of the user and the subscription is not
                // filtering notifications based on presence show values.
                service.presenceSubscriptionRequired(node, getOwner());
            }
        }
    }
}
