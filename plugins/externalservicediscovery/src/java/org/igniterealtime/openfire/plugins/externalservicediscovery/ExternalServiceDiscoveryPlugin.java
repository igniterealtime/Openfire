/*
 * Copyright (C) 2017 Ignite Realtime Foundation. All rights reserved.
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
package org.igniterealtime.openfire.plugins.externalservicediscovery;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Iterator;

/**
 * An Openfire plugin that implements XEP-0215: External Service Discovery.
 *
 * This plugin allowS XMPP entities to discover services external to the XMPP network, such as STUN and TURN servers.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 * @see <a href="https://xmpp.org/extensions/xep-0215.html">XEP-0215: External Service Discovery</a>
 */
public class ExternalServiceDiscoveryPlugin implements Plugin
{
    private static final Logger Log = LoggerFactory.getLogger( ExternalServiceDiscoveryPlugin.class );

    private ExternalServiceDiscoveryIQHandlerV1 iqHandlerV1;
    private ExternalServiceDiscoveryIQHandlerV2 iqHandlerV2;

    @Override
    public void initializePlugin( PluginManager manager, File pluginDirectory )
    {
        Log.debug( "Registering IQ Handlers..." );
        iqHandlerV1 = new ExternalServiceDiscoveryIQHandlerV1();
        iqHandlerV2 = new ExternalServiceDiscoveryIQHandlerV2();
        XMPPServer.getInstance().getIQRouter().addHandler( iqHandlerV1 );
        XMPPServer.getInstance().getIQRouter().addHandler( iqHandlerV2 );

        Log.debug( "Registering Server Features..." );
        for ( final Iterator<String> it = iqHandlerV1.getFeatures(); it.hasNext(); )
        {
            XMPPServer.getInstance().getIQDiscoInfoHandler().addServerFeature( it.next() );
        }
        for ( final Iterator<String> it = iqHandlerV2.getFeatures(); it.hasNext(); )
        {
            XMPPServer.getInstance().getIQDiscoInfoHandler().addServerFeature( it.next() );
        }

        Log.debug( "Initialized." );
    }

    @Override
    public void destroyPlugin()
    {
        Log.debug( "Removing Server Features..." );
        for ( final Iterator<String> it = iqHandlerV2.getFeatures(); it.hasNext(); )
        {
            XMPPServer.getInstance().getIQDiscoInfoHandler().removeServerFeature( it.next() );
        }
        for ( final Iterator<String> it = iqHandlerV1.getFeatures(); it.hasNext(); )
        {
            XMPPServer.getInstance().getIQDiscoInfoHandler().removeServerFeature( it.next() );
        }

        if ( iqHandlerV2 != null )
        {
            Log.debug( "Removing IQ Handler..." );
            XMPPServer.getInstance().getIQRouter().removeHandler( iqHandlerV2 );
        }
        if ( iqHandlerV1 != null )
        {
            Log.debug( "Removing IQ Handler..." );
            XMPPServer.getInstance().getIQRouter().removeHandler( iqHandlerV1 );
        }

        Log.debug( "Destroyed." );
    }
}
