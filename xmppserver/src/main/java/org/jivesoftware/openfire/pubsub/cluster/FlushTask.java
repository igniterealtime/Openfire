/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2020 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.pubsub.cluster;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.pubsub.CachingPubsubPersistenceProvider;
import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.openfire.pubsub.PubSubPersistenceProvider;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A cluster task used to instruct other cluster nodes that they must flush pending changes to pubsub nodes to the
 * persistent data storage.
 *
 * This task can be used to flush all pending changes, or the pending changes related to a specific node only.
 */
public class FlushTask implements ClusterTask<Void>
{
    /**
     * The unique identifier for the pubsub node that is the subject of the task, in case the task is specific to one
     * pubsub node. When this task should apply to all nodes, this value will be null.
     *
     * @see Node#getUniqueIdentifier()
     */
    @Nullable
	private Node.UniqueIdentifier uniqueIdentifier;

    /**
     * Instantiates a flush task for a specific node.
     *
     * @param uniqueIdentifier The identifier of the node to flush.
     */
	public FlushTask(@Nonnull final Node.UniqueIdentifier uniqueIdentifier )
	{
		this.uniqueIdentifier = uniqueIdentifier;
	}

    /**
     * Instantiates a flush task for a system-wide flush of pending changes.
     */
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
