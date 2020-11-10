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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

/**
 * Removes a newly deleted node from memory across the cluster.
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
        getService().removeNode(getNodeId());
    }
}
