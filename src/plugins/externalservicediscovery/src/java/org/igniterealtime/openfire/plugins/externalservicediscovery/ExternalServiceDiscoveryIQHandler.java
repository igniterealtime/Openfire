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
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.jivesoftware.openfire.handler.IQHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

/**
 * An IQ handler that implements XEP-0215: External Service Discovery.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 * @see <a href="https://xmpp.org/extensions/xep-0215.html">XEP-0215: External Service Discovery</a>
 */
public abstract class ExternalServiceDiscoveryIQHandler extends IQHandler implements ServerFeaturesProvider
{
    private static final Logger Log = LoggerFactory.getLogger( ExternalServiceDiscoveryIQHandler.class );

    public ExternalServiceDiscoveryIQHandler()
    {
        super( "XEP-0215: External Service Discovery" );
    }

    @Override
    public Iterator<String> getFeatures()
    {
        return Collections.singleton( getInfo().getNamespace() ).iterator();
    }

    @Override
    public IQ handleIQ( IQ packet ) throws UnauthorizedException
    {
        if ( packet.isResponse() )
        {
            Log.debug( "Silently ignoring IQ response: {}", packet );
            return null;
        }

        if ( IQ.Type.set == packet.getType() )
        {
            Log.info( "Responding with an error to an IQ request of type 'set': {}", packet );
            final IQ response = IQ.createResultIQ( packet );
            response.setError( PacketError.Condition.service_unavailable );
            return response;
        }

        final IQ response;
        switch ( packet.getChildElement().getName() )
        {
            case "services":
                response = handleServices( packet );
                break;

            case "credentials":
                response = handleCredentials( packet );
                break;

            default:
                Log.info( "Responding with an error to an IQ request for which the element name escaped by namespace is not understood: {}", packet );
                response = IQ.createResultIQ( packet );
                response.setError( PacketError.Condition.service_unavailable );
        }
        Log.info( "Responding with {} to request {}", response.toXML(), packet.toXML() );
        return response;
    }

    /**
     * Handles requests for services.
     *
     * @param request the request (cannot be null).
     * @return An answer (never null).
     */
    protected IQ handleServices( IQ request )
    {
        final Map<Service, Credentials> services;
        final ServiceManager serviceManager = ServiceManager.getInstance();
        final String requestedType = request.getChildElement().attributeValue( "type" );
        if ( requestedType == null || requestedType.isEmpty() )
        {
            Log.debug( "Handling request for all services." );
            services = serviceManager.getServicesFor( request.getFrom() );
        }
        else
        {
            Log.debug( "Handling request for services of a specific type: {}.", requestedType );
            services = serviceManager.getServicesFor( request.getFrom(), requestedType );
        }

        // Formulate response.
        final IQ response = IQ.createResultIQ( request );
        final Element childElement = response.setChildElement( request.getChildElement().getName(), request.getChildElement().getNamespaceURI() );
        if ( requestedType != null && !requestedType.isEmpty() )
        {
            childElement.addAttribute( "type", requestedType );
        }

        for ( final Map.Entry<Service, Credentials> entry : services.entrySet() )
        {
            addServiceXml( childElement, entry.getKey(), null, entry.getValue() );
        }

        return response;
    }

    /**
     * Handles requests for credentials
     *
     * @param request the request (cannot be null).
     * @return An answer (never null).
     */
    protected IQ handleCredentials( IQ request )
    {
        final Element requestedService = request.getChildElement().element( "service" );
        if ( requestedService == null )
        {
            Log.debug( "Responding with an error to a request for credentials that did not specify any service: {}", request );
            final IQ response = IQ.createResultIQ( request );
            response.setError( PacketError.Condition.bad_request );
            return response;
        }

        final String requestedHost = requestedService.attributeValue( "host" );
        final String requestedType = requestedService.attributeValue( "type" );
        final Integer requestedPort;
        if ( requestedService.attribute( "port" ) != null )
        {
            try
            {
                requestedPort = Integer.parseInt( requestedService.attributeValue( "port" ) );
            }
            catch ( NumberFormatException ex )
            {
                Log.debug( "Responding with an error to a request for credentials that specified a malformed port number for a service: {}", request, ex );
                final IQ response = IQ.createResultIQ( request );
                response.setError( PacketError.Condition.bad_request );
                return response;
            }
        }
        else
        {
            requestedPort = null;
        }

        if ( requestedHost == null || requestedHost.isEmpty() || requestedType == null || requestedType.isEmpty() )
        {
            Log.debug( "Responding with an error to a request for credentials that did not specify any service: {}", request );
            final IQ response = IQ.createResultIQ( request );
            response.setError( PacketError.Condition.bad_request );
            return response;
        }

        final ServiceManager serviceManager = ServiceManager.getInstance();
        final Map<Service, Credentials> services;
        if ( requestedPort == null )
        {
            Log.debug( "Handling request for credentials by {} for the {} service: {}", request.getFrom(), requestedType, requestedHost );
            services = serviceManager.getServicesFor( request.getFrom(), requestedHost, requestedType );
        }
        else
        {
            Log.debug( "Handling request for credentials by {} for the {} service: {}:{}", request.getFrom(), requestedType, requestedHost, requestedPort);
            services = serviceManager.getServicesFor( request.getFrom(), requestedHost, requestedType, requestedPort );
        }

        // Formulate response.
        final IQ response = IQ.createResultIQ( request );
        final Element childElement = response.setChildElement( "credentials", request.getChildElement().getNamespaceURI() );
        for ( final Map.Entry<Service, Credentials> service : services.entrySet() )
        {
            addServiceXml( childElement, service.getKey(), null, service.getValue() );
        }

        return response;
    }

    abstract void addServiceXml( Element parent, Service service, final Service.Action action, final Credentials credentials );
}
