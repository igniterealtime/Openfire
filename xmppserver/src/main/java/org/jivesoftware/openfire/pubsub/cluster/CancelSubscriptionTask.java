package org.jivesoftware.openfire.pubsub.cluster;

import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.openfire.pubsub.NodeSubscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CancelSubscriptionTask extends SubscriptionTask
{
    private static final Logger log = LoggerFactory.getLogger(CancelSubscriptionTask.class);

    public CancelSubscriptionTask()
    {
    }

    public CancelSubscriptionTask(NodeSubscription subscription)
    {
        super(subscription);
    }

    @Override
    public void run()
    {
        log.debug("[TASK] Cancel Subscription : {}", toString());

        Node node = getNode();
        
        // This will only occur if a PEP service is not loaded.  We can safely do nothing in this 
        // case since any changes will get loaded from the db when it is loaded.
        if (node == null)
            return;
        
        // This method will make a db call, but it will simply do nothing since
        // the record will already be deleted.
        node.cancelSubscription(getSubscription(), false);
    }
}
