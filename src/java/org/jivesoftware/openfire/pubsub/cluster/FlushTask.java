package org.jivesoftware.openfire.pubsub.cluster;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.jivesoftware.openfire.pubsub.PubSubPersistenceManager;
import org.jivesoftware.util.cache.ClusterTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class FlushTask implements ClusterTask
{
	private static final Logger log = LoggerFactory.getLogger(FlushTask.class);

	public FlushTask()
	{
	}

	@Override
	public void run()
	{
		log.debug("[TASK] Flush pubsub");
        PubSubPersistenceManager.flushItems(false); // just this member
	}

	@Override
	public Object getResult()
	{
		return null;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException
	{
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
	{
	}

}
