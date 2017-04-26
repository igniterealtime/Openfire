/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.sasl;

import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.LocalIncomingServerSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslServerFactory;

/**
 * Server Factory for supported mechanisms.
 *
 * @author Jay Kline
 */

public class SaslServerFactoryImpl implements SaslServerFactory
{
    private final static Logger Log = LoggerFactory.getLogger( SaslServerFactoryImpl.class );

    /**
     * All mechanisms provided by this factory.
     */
    private final Set<Mechanism> allMechanisms;

    public SaslServerFactoryImpl()
    {
        allMechanisms = new HashSet<>();
        allMechanisms.add( new Mechanism( "ANONYMOUS", true, true ) );
        allMechanisms.add( new Mechanism( "PLAIN", false, true ) );
        allMechanisms.add( new Mechanism( "SCRAM-SHA-1", false, false ) );
        allMechanisms.add( new Mechanism( "JIVE-SHAREDSECRET", true, false ) );
        allMechanisms.add( new Mechanism( "EXTERNAL", false, false ) );
    }

    @Override
    public SaslServer createSaslServer(String mechanism, String protocol, String serverName, Map<String, ?> props, CallbackHandler cbh) throws SaslException
    {
        if ( !Arrays.asList( getMechanismNames( props )).contains( mechanism ) )
        {
            Log.debug( "This implementation is unable to create a SaslServer instance for the {} mechanism using the provided properties.", mechanism );
            return null;
        }

        switch ( mechanism.toUpperCase() )
        {
            case "PLAIN":
                if ( cbh == null )
                {
                    Log.debug( "Unable to instantiate {} SaslServer: A callbackHandler with support for Password, Name, and AuthorizeCallback required.", mechanism );
                    return null;
                }
                return new SaslServerPlainImpl( protocol, serverName, props, cbh );

            case "SCRAM-SHA-1":
                return new ScramSha1SaslServer();

            case "ANONYMOUS":
                if ( !props.containsKey( LocalSession.class.getCanonicalName() ) )
                {
                    Log.debug( "Unable to instantiate {} SaslServer: Provided properties do not contain a LocalSession instance.", mechanism );
                    return null;
                }
                else
                {
                    final LocalSession session = (LocalSession) props.get( LocalSession.class.getCanonicalName() );
                    return new AnonymousSaslServer( session );
                }

            case "EXTERNAL":
                if ( !props.containsKey( LocalSession.class.getCanonicalName() ) )
                {
                    Log.debug( "Unable to instantiate {} SaslServer: Provided properties do not contain a LocalSession instance.", mechanism );
                    return null;
                }
                else
                {
                    final Object session = props.get( LocalSession.class.getCanonicalName() );
                    if ( session instanceof LocalClientSession )
                    {
                        return new ExternalClientSaslServer( (LocalClientSession) session );
                    }
                    if ( session instanceof LocalIncomingServerSession )
                    {
                        return new ExternalServerSaslServer( (LocalIncomingServerSession) session );
                    }

                    Log.debug( "Unable to instantiate {} Sasl Server: Provided properties contains neither LocalClientSession nor LocalIncomingServerSession instance.", mechanism );
                    return null;
                }

            case JiveSharedSecretSaslServer.NAME:
                return new JiveSharedSecretSaslServer();

            default:
                throw new IllegalStateException(); // Fail fast - this should not be possible, as the first check in this method already verifies wether the mechanism is supported.
        }
    }

    @Override
    public String[] getMechanismNames( Map<String, ?> props )
    {
        final Set<String> result = new HashSet<>();

        for ( final Mechanism mechanism : allMechanisms )
        {
            if ( props != null )
            {
                if ( mechanism.allowsAnonymous && props.containsKey( Sasl.POLICY_NOANONYMOUS ) && Boolean.parseBoolean( (String) props.get( Sasl.POLICY_NOANONYMOUS ) ) )
                {
                    // Do not include a mechanism that allows anonymous authentication when the 'no anonymous' policy is set.
                    continue;
                }

                if ( mechanism.isPlaintext && props.containsKey( Sasl.POLICY_NOPLAINTEXT ) && Boolean.parseBoolean( (String) props.get( Sasl.POLICY_NOPLAINTEXT ) ) )
                {
                    // Do not include a mechanism that is susceptible to simple plain passive attacks when the 'no plaintext' policy is set.
                    continue;
                }
            }

            // Mechanism passed all filters. It should be part of the result.
            result.add( mechanism.name );
        }

        return result.toArray( new String[ result.size() ] );
    }

    private static class Mechanism
    {
        final String name;
        final boolean allowsAnonymous;
        final boolean isPlaintext;

        private Mechanism( String name, boolean allowsAnonymous, boolean isPlaintext )
        {
            this.name = name;
            this.allowsAnonymous = allowsAnonymous;
            this.isPlaintext = isPlaintext;
        }
    }
}
