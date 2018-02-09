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

import org.dom4j.Element;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

/**
 * An IQ handler that implements XEP-0215: External Service Discovery, Version 0.6 (2014-02-27)
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 * @see <a href="https://xmpp.org/extensions/xep-0215.html">XEP-0215: External Service Discovery</a>
 */
public class ExternalServiceDiscoveryIQHandlerV1 extends ExternalServiceDiscoveryIQHandler
{
    private static final Logger Log = LoggerFactory.getLogger( ExternalServiceDiscoveryIQHandlerV1.class );

    private final static IQHandlerInfo INFO = new IQHandlerInfo( "services", "urn:xmpp:extdisco:1" );

    @Override
    public IQHandlerInfo getInfo()
    {
        return INFO;
    }

    @Override
    public void addServiceXml( Element parent, Service service, final Service.Action action, final Credentials credentials )
    {
        final Element result = parent.addElement( "service" );

        if ( credentials != null )
        {
            if ( credentials.getUsername() != null )
            {
                result.addAttribute( "username", credentials.getUsername() );
            }


            if ( credentials.getPassword() != null )
            {
                result.addAttribute( "password", credentials.getPassword() );
            }
        }

        if ( service.getHost() != null )
        {
            result.addAttribute( "host", service.getHost() );
        }

        if ( service.getName() != null )
        {
            result.addAttribute( "name", service.getName() );
        }

        if ( service.getPort() != null )
        {
            result.addAttribute( "port", Integer.toString( service.getPort() ) );
        }

        if ( service.getTransport() != null )
        {
            result.addAttribute( "transport", service.getTransport() );
        }

        if ( service.getType() != null )
        {
            result.addAttribute( "type", service.getType() );
        }
    }
}
