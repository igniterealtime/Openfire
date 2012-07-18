package org.jivesoftware.openfire.pubsub.cluster;

import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.openfire.pubsub.NodeSubscription;
import org.jivesoftware.openfire.pubsub.PubSubPersistenceManager;

public class NewSubscriptionTask extends SubscriptionTask
{
	public NewSubscriptionTask()
	{

	}

	public NewSubscriptionTask(NodeSubscription subscription)
	{
		super(subscription);
	}

	@Override
	public void run()
	{
		System.out.println("Running NewSubscriptionTask: " + toString());

		Node node = getNode();

		// This will only occur if a PEP service is not loaded.  We can safely do nothing in this 
		// case since any changes will get loaded from the db when it is loaded.
		if (node == null)
			return;

		if (node.getAffiliate(getOwner()) == null)
		{
			node.addNoneAffiliation(getOwner());
		}
		node.addSubscription(getSubscription());

		if (node.isPresenceBasedDelivery() && node.getSubscriptions(getSubscription().getOwner()).size() == 1)
		{
			if (getSubscription().getPresenceStates().isEmpty())
			{
				// Subscribe to the owner's presence since the node is only
				// sending events to online subscribers and this is the first
				// subscription of the user and the subscription is not
				// filtering notifications based on presence show values.
				getService().presenceSubscriptionRequired(getNode(), getOwner());
			}
		}
		// We have to flush so the originating node can do a get last item.
		PubSubPersistenceManager.flushItems();
	}
}
