/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2020 Ignite Realtime Foundation. All rights reserved.
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
import org.jivesoftware.openfire.pubsub.PubSubService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Removes a newly deleted node from memory across the cluster.
 *
 * Note that this task aims to update in-memory state only: it will not apply affiliation changes to persistent data
 * storage (it is assumed that the cluster node where the task originated takes responsibility for that). As a result,
 * this task might not apply changes if the node that is the subject of this task is currently not loaded in-memory of
 * the cluster node on which this task operates.
 *
 * @author Tom Evans
 */
public class RemoveNodeTask extends NodeTask
{
    private static final Logger log = LoggerFactory.getLogger(RemoveNodeTask.class);

    /**
     * This no-argument constructor is provided for serialization purposes. It should generally not be used otherwise.
     */
    public RemoveNodeTask()
    {
    }

    /**
     * Constructs a new task that removes a specific node from a pubsub node.
     *
     * @param node The pubsub node that this task relates to.
     */
    public RemoveNodeTask(@Nonnull final Node node)
    {
        super(node);
    }

    @Override
    public void run()
    {
        log.debug("[TASK] Removing node - nodeID: {}", getNodeId());

        final Optional<PubSubService> optService = getServiceIfLoaded();

        // This will only occur if a PEP service is not loaded on this particular cluster node. We can safely do nothing
        // in this case since any changes that might have been applied here will also have been applied to the database
        // by the cluster node where this task originated, meaning that those changes get loaded from the database when
        // the pubsub node is retrieved from the database in the future (OF-2077)
        if (!optService.isPresent()) {
            return;
        }

        optService.get().removeNode(getNodeId());
    }
}
