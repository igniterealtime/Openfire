package org.jivesoftware.openfire.pubsub.cluster;

import org.jivesoftware.openfire.pubsub.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Removes a newly deleted node from memory across the cluster.
 *
 * @author Tom Evans
 *
 */
public class RemoveNodeTask extends NodeTask
{
    private static final Logger log = LoggerFactory.getLogger(RemoveNodeTask.class);

    public RemoveNodeTask()
    {
    }

    public RemoveNodeTask(Node node)
    {
        super(node);
    }

    public void run()
    {
		log.debug("[TASK] Removing node - nodeID: {}", getNodeId());
        getService().removeNode(getNodeId());
    }

}
