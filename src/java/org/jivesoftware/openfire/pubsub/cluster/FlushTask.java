package org.jivesoftware.openfire.pubsub.cluster;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.jivesoftware.openfire.pubsub.PubSubPersistenceManager;
import org.jivesoftware.util.cache.ClusterTask;


public class FlushTask implements ClusterTask
{
	public FlushTask()
	{
	}

	@Override
	public void run()
	{
        PubSubPersistenceManager.flushPendingItems(false); // just this member
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
