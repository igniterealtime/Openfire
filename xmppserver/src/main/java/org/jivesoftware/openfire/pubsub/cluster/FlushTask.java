package org.jivesoftware.openfire.pubsub.cluster;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

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
		final PubSubPersistenceProvider provider = PubSubPersistenceProviderManager.getInstance().getProvider();
		if ( provider instanceof CachingPubsubPersistenceProvider )
		{
			if ( uniqueIdentifier != null ) {
				((CachingPubsubPersistenceProvider) provider).flushPendingItems( uniqueIdentifier, false ); // just this member
			} else {
				((CachingPubsubPersistenceProvider) provider).flushPendingItems( false ); // just this member
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
			ExternalizableUtil.getInstance().writeSafeUTF( out, uniqueIdentifier.getServiceId() );
			ExternalizableUtil.getInstance().writeSafeUTF( out, uniqueIdentifier.getNodeId() );
		}
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
    {
		if ( ExternalizableUtil.getInstance().readBoolean( in ) ) {
			String serviceId = ExternalizableUtil.getInstance().readSafeUTF( in );
			String nodeId = ExternalizableUtil.getInstance().readSafeUTF( in );
			uniqueIdentifier = new Node.UniqueIdentifier( serviceId, nodeId );
		} else {
			this.uniqueIdentifier = null;
        }
	}
}
