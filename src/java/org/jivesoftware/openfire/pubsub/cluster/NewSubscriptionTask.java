package org.jivesoftware.openfire.pubsub.cluster;

import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.openfire.pubsub.NodeAffiliate;
import org.jivesoftware.openfire.pubsub.NodeSubscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

public class NewSubscriptionTask extends SubscriptionTask
{
    private static final Logger log = LoggerFactory.getLogger(NewSubscriptionTask.class);

	public NewSubscriptionTask()
	{

	}

	public NewSubscriptionTask(NodeSubscription subscription)
	{
		super(subscription);
	}

	public void run()
	{
		log.debug("[TASK] New subscription : {}", toString());

		Node node = getNode();

		// This will only occur if a PEP service is not loaded.  We can safely do nothing in this 
		// case since any changes will get loaded from the db when it is loaded.
		if (node == null)
			return;

		if (node.getAffiliate(getOwner()) == null)
		{
			// add the missing 'none' affiliation
            NodeAffiliate affiliate = new NodeAffiliate(node, getOwner());
            affiliate.setAffiliation(NodeAffiliate.Affiliation.none);
            node.addAffiliate(affiliate);
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
	}
}
