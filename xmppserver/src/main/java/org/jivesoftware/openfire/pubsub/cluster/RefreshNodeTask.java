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

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.pubsub.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

/**
 * Forces the node to be refreshed from the database. This will load a node from
 * the database and then add it to the service. If the node already existed it
 * will be replaced, thereby refreshing it from persistence.
 *
 * Unlike other cluster tasks, this task will forcefully (re)load the node from backend storage on every cluster node
 * where the task is executed. This can add significant overhead, and should be avoided if possible.
 *
 * @author Robin Collier
 */
public class RefreshNodeTask extends NodeTask
{
    private static final Logger log = LoggerFactory.getLogger(RefreshNodeTask.class);

    /**
     * This no-argument constructor is provided for serialization purposes. It should generally not be used otherwise.
     */
    public RefreshNodeTask()
    {
    }

    /**
     * Constructs a new task that refreshes a specific pubsub node.
     *
     * @param node The pubsub node that this task relates to.
     */
    public RefreshNodeTask(@Nonnull final Node node)
    {
        super(node);
    }

    @Override
    public void run()
    {
        log.debug("[TASK] Refreshing node - nodeID: {}", getNodeId());
        XMPPServer.getInstance().getPubSubModule().getPersistenceProvider().loadNode(getService(), getUniqueNodeIdentifier());
    }
}
