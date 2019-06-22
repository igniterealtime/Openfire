package org.jivesoftware.openfire.pubsub.cluster;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.jivesoftware.openfire.pubsub.PubSubPersistenceManager;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;


public class FlushTask implements ClusterTask<Void>
{
	private String nodeId;

	public FlushTask( String nodeId )
	{
		this.nodeId = nodeId;
	}

    public FlushTask()
    {
		this.nodeId = null;
    }

    @Override
    public void run()
    {
		if ( nodeId != null ) {
			PubSubPersistenceManager.flushPendingItems(nodeId, false); // just this member
		} else {
        PubSubPersistenceManager.flushPendingItems(false); // just this member
    }
	}

    @Override
    public Void getResult()
    {
        return null;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException
    {
		ExternalizableUtil.getInstance().writeBoolean( out, nodeId != null);
		ExternalizableUtil.getInstance().writeSafeUTF( out, nodeId );
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
    {
		if ( ExternalizableUtil.getInstance().readBoolean( in ) ) {
			this.nodeId = ExternalizableUtil.getInstance().readSafeUTF( in );
		} else {
			this.nodeId = null;
    }
	}
}
