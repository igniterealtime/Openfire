/*
 * Copyright (C) 2018 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire.archive;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.time.Duration;
import java.time.Instant;

/**
 * A task that retrieves a time estimation on the time it takes for data to have been written to persistent storage.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class GetArchiveWriteETATask implements ClusterTask<Duration>
{
    private Instant instant;
    private String id;
    private Duration result;

    public GetArchiveWriteETATask() {}

    public GetArchiveWriteETATask( Instant instant, String id )
    {
        this.instant = instant;
        this.id = id;
    }

    @Override
    public void run()
    {
        final ArchiveManager manager = XMPPServer.getInstance().getArchiveManager();
        result = manager.availabilityETAOnLocalNode( id, instant );
    }

    @Override
    public Duration getResult()
    {
        return result;
    }

    @Override
    public void writeExternal( ObjectOutput out ) throws IOException
    {
        ExternalizableUtil.getInstance().writeSerializable( out, instant );
        ExternalizableUtil.getInstance().writeSafeUTF( out, id );
    }

    @Override
    public void readExternal( ObjectInput in ) throws IOException, ClassNotFoundException
    {
        instant = (Instant) ExternalizableUtil.getInstance().readSerializable( in );
        id = ExternalizableUtil.getInstance().readSafeUTF( in );
    }
}
