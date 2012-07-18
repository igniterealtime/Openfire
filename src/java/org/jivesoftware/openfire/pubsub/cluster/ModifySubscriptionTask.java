package org.jivesoftware.openfire.pubsub.cluster;

import org.jivesoftware.openfire.pubsub.NodeSubscription;
import org.jivesoftware.openfire.pubsub.PubSubPersistenceManager;

public class ModifySubscriptionTask extends SubscriptionTask
{
	public ModifySubscriptionTask()
	{

	}

	public ModifySubscriptionTask(NodeSubscription subscription)
	{
		super(subscription);
	}

	@Override
	public void run()
	{
		PubSubPersistenceManager.loadSubscription(getService(), getNode(), getSubscriptionId());
	}
}
