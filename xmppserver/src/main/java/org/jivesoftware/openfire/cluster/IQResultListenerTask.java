/*
 * Copyright (C) 2020 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.cluster;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.XMLWriter;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.StringWriter;

public class IQResultListenerTask implements ClusterTask<Void>
{
    private static Logger Log = LoggerFactory.getLogger( IQResultListenerTask.class );

    private IQ packet;

    public IQResultListenerTask()
    {
    }

    public IQResultListenerTask( IQ packet )
    {
        this.packet = packet;
    }

    @Override
    public Void getResult()
    {
        return null;
    }

    @Override
    public void run()
    {
        if ( packet != null )
        {
            XMPPServer.getInstance().getIQRouter().route( packet );
        }
    }

    @Override
    public void writeExternal( ObjectOutput out ) throws IOException
    {
        StringWriter sw = new StringWriter();
        XMLWriter writer = new XMLWriter( sw, OutputFormat.createCompactFormat() );

        try
        {
            writer.write( packet.getElement() );
        }
        catch ( Exception e )
        {
            Log.warn( "Unable to serialize packet {}", packet, e );
        }

        ExternalizableUtil.getInstance().writeSafeUTF( out, sw.toString() );
    }

    @Override
    public void readExternal( ObjectInput in ) throws IOException, ClassNotFoundException
    {
        final String xml = ExternalizableUtil.getInstance().readSafeUTF( in );
        try
        {
            final Element el = DocumentHelper.parseText( xml ).getRootElement();
            packet = new IQ( el );
        }
        catch ( Exception e )
        {
            Log.warn( "Unable to deserialize string '{}'", xml, e );
        }
    }
}
