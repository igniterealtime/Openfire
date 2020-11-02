package org.jivesoftware.openfire.pubsub.cluster;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.pubsub.CachingPubsubPersistenceProvider;
import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.openfire.pubsub.PubSubPersistenceProvider;
import org.jivesoftware.openfire.pubsub.PubSubPersistenceProviderManager;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;


public class FlushTask implements ClusterTask<Void>
{
	private Node.UniqueIdentifier uniqueIdentifier;

	public FlushTask( Node.UniqueIdentifier uniqueIdentifier )
	{
		this.uniqueIdentifier = uniqueIdentifier;
	}

    public FlushTask()
    {
		this.uniqueIdentifier = null;
    }

    @Override
    public void run()
    {
		final PubSubPersistenceProvider provider = XMPPServer.getInstance().getPubSubModule().getPersistenceProvider();
		if ( provider instanceof CachingPubsubPersistenceProvider )
		{
			if ( uniqueIdentifier != null ) {
				((CachingPubsubPersistenceProvider) provider).flushPendingChanges(uniqueIdentifier, false ); // just this member
			} else {
				((CachingPubsubPersistenceProvider) provider).flushPendingChanges(false ); // just this member
			}
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
		ExternalizableUtil.getInstance().writeBoolean( out, uniqueIdentifier != null);
		if ( uniqueIdentifier != null )
		{
			ExternalizableUtil.getInstance().writeSerializable( out, uniqueIdentifier );
		}
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
    {
		if ( ExternalizableUtil.getInstance().readBoolean( in ) ) {
		    uniqueIdentifier = (Node.UniqueIdentifier) ExternalizableUtil.getInstance().readSerializable( in );
		} else {
			this.uniqueIdentifier = null;
        }
	}
}
