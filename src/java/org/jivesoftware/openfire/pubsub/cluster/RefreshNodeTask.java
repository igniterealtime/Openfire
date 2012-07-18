package org.jivesoftware.openfire.pubsub.cluster;

import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.openfire.pubsub.PubSubPersistenceManager;

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
	public RefreshNodeTask()
	{
	}

	public RefreshNodeTask(Node node)
	{
		super(node);
	}

	@Override
	public Object getResult()
	{
		return null;
	}

	@Override
	public void run()
	{
		System.out.println("Refreshing node task");
		PubSubPersistenceManager.loadNode(getService(), getNodeId());
	}

}
