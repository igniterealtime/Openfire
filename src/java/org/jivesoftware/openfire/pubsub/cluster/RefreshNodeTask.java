package org.jivesoftware.openfire.pubsub.cluster;

import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.openfire.pubsub.PubSubPersistenceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forces the node to be refreshed from the database. This will load a node from
 * the database and then add it to the service. If the node already existed it
 * will be replaced, thereby refreshing it from persistence.
 * 
 * @author Robin Collier
 * 
 */
public class RefreshNodeTask extends NodeTask
{
    private static final Logger log = LoggerFactory.getLogger(RefreshNodeTask.class);

	public RefreshNodeTask()
	{
	}

	public RefreshNodeTask(Node node)
	{
		super(node);
	}

	@Override
	public void run()
	{
		log.debug("[TASK] Refreshing node - nodeID: {}", getNodeId());
		PubSubPersistenceManager.loadNode(getService(), getNodeId());
	}

}
