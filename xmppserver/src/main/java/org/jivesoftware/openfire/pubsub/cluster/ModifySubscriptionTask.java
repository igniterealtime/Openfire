package org.jivesoftware.openfire.pubsub.cluster;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.pubsub.NodeSubscription;
import org.jivesoftware.openfire.pubsub.PubSubPersistenceProviderManager;
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

    @Override
    public void run()
    {
        log.debug("[TASK] Modify subscription : {}", toString());
        XMPPServer.getInstance().getPubSubModule().getPersistenceProvider().loadSubscription( getService(), getNode(), getSubscriptionId());
    }
}
