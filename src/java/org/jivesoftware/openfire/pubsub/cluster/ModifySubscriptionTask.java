package org.jivesoftware.openfire.pubsub.cluster;

import org.jivesoftware.openfire.pubsub.NodeSubscription;
import org.jivesoftware.openfire.pubsub.PubSubPersistenceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModifySubscriptionTask extends SubscriptionTask
{
    private static final Logger log = LoggerFactory.getLogger(ModifySubscriptionTask.class);

	public ModifySubscriptionTask()
	{

	}

	public ModifySubscriptionTask(NodeSubscription subscription)
	{
		super(subscription);
	}

	public void run()
	{
		log.debug("[TASK] Modify subscription : {}", toString());
		PubSubPersistenceManager.loadSubscription(getService(), getNode(), getSubscriptionId());
	}
}
