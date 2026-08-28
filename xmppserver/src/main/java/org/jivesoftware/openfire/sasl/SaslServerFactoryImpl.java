/*
 * Copyright (C) 2004-2008 Jive Software, 2017-2026 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.openfire.fast.FastTokenManager;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.LocalIncomingServerSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
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
        allMechanisms.add( new Mechanism( ScramSha1SaslServer.MECHANISM_NAME, false, false ) );
        allMechanisms.add( new Mechanism( ScramSha1SaslServer.MECHANISM_NAME + "-PLUS", false, false ) );
        allMechanisms.add( new Mechanism( ScramSha256SaslServer.MECHANISM_NAME, false, false ) );
        allMechanisms.add( new Mechanism( ScramSha256SaslServer.MECHANISM_NAME + "-PLUS", false, false ) );
        allMechanisms.add( new Mechanism( ScramSha512SaslServer.MECHANISM_NAME, false, false ) );
        allMechanisms.add( new Mechanism( ScramSha512SaslServer.MECHANISM_NAME + "-PLUS", false, false ) );
        allMechanisms.add( new Mechanism( "JIVE-SHAREDSECRET", true, false ) );
        allMechanisms.add( new Mechanism( "EXTERNAL", false, false ) );
        // HT-* mechanisms (original HT draft): all hash × channel-binding combinations
        allMechanisms.add( new Mechanism( FastTokenManager.HT_SHA_256_NONE, false, false ) );
        allMechanisms.add( new Mechanism( FastTokenManager.HT_SHA_256_UNIQ, false, false ) );
        allMechanisms.add( new Mechanism( FastTokenManager.HT_SHA_256_ENDP, false, false ) );
        allMechanisms.add( new Mechanism( FastTokenManager.HT_SHA_256_EXPR, false, false ) );
        allMechanisms.add( new Mechanism( FastTokenManager.HT_SHA_512_NONE, false, false ) );
        allMechanisms.add( new Mechanism( FastTokenManager.HT_SHA_512_UNIQ, false, false ) );
        allMechanisms.add( new Mechanism( FastTokenManager.HT_SHA_512_ENDP, false, false ) );
        allMechanisms.add( new Mechanism( FastTokenManager.HT_SHA_512_EXPR, false, false ) );
        // HT2-* mechanisms (draft-ietf-kitten-sasl-ht): all hash × channel-binding combinations
        allMechanisms.add( new Mechanism( FastTokenManager.HT2_SHA_256_NONE, false, false ) );
        allMechanisms.add( new Mechanism( FastTokenManager.HT2_SHA_256_UNIQ, false, false ) );
        allMechanisms.add( new Mechanism( FastTokenManager.HT2_SHA_256_ENDP, false, false ) );
        allMechanisms.add( new Mechanism( FastTokenManager.HT2_SHA_256_EXPR, false, false ) );
        allMechanisms.add( new Mechanism( FastTokenManager.HT2_SHA_512_NONE, false, false ) );
        allMechanisms.add( new Mechanism( FastTokenManager.HT2_SHA_512_UNIQ, false, false ) );
        allMechanisms.add( new Mechanism( FastTokenManager.HT2_SHA_512_ENDP, false, false ) );
        allMechanisms.add( new Mechanism( FastTokenManager.HT2_SHA_512_EXPR, false, false ) );
    }

    @Override
    public SaslServer createSaslServer(String mechanism, String protocol, String serverName, Map<String, ?> props, CallbackHandler cbh) throws SaslException
    {
        if ( !Arrays.asList( getMechanismNames( props )).contains( mechanism ) )
        {
            Log.debug( "This implementation is unable to create a SaslServer instance for the {} mechanism using the provided properties.", mechanism );
            return null;
        }

        final Optional<Set<String>> advertisedSASLMechanisms = extractAdvertisedSASLMechanisms(props);
        final Optional<Set<String>> advertisedChannelBindingTypes = extractAdvertisedChannelBindingTypes(props);
        if (mechanism.toUpperCase().startsWith("SCRAM-SHA-"))
        {
            // These checks are the basis for the 'orElseThrow' statements in the switch/case below. Note that they
            // test whether anything was recorded at all, not whether any types were advertised: a session that was
            // legitimately offered no channel-binding types has an empty set recorded, which is a present Optional.
            if (advertisedSASLMechanisms.isEmpty()) {
                Log.debug("Unable to instantiate {} SaslServer: Provided properties do not contain a set of SASL mechanism names that was advertised to the client.", mechanism);
                return null;
            }
            if (advertisedChannelBindingTypes.isEmpty()) {
                Log.debug("Unable to instantiate {} SaslServer: Provided properties do not contain a set of channel-binding types that was advertised to the client.", mechanism);
                return null;
            }
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

            case ScramSha1SaslServer.MECHANISM_NAME:
                return new ScramSha1SaslServer(false, props, advertisedSASLMechanisms.orElseThrow(), advertisedChannelBindingTypes.orElseThrow());

            case ScramSha1SaslServer.MECHANISM_NAME + "-PLUS":
                return new ScramSha1SaslServer(true, props, advertisedSASLMechanisms.orElseThrow(), advertisedChannelBindingTypes.orElseThrow());

            case ScramSha256SaslServer.MECHANISM_NAME:
                return new ScramSha256SaslServer(false, props, advertisedSASLMechanisms.orElseThrow(), advertisedChannelBindingTypes.orElseThrow());

            case ScramSha256SaslServer.MECHANISM_NAME + "-PLUS":
                return new ScramSha256SaslServer(true, props, advertisedSASLMechanisms.orElseThrow(), advertisedChannelBindingTypes.orElseThrow());

            case ScramSha512SaslServer.MECHANISM_NAME:
                return new ScramSha512SaslServer(false, props, advertisedSASLMechanisms.orElseThrow(), advertisedChannelBindingTypes.orElseThrow());

            case ScramSha512SaslServer.MECHANISM_NAME + "-PLUS":
                return new ScramSha512SaslServer(true, props, advertisedSASLMechanisms.orElseThrow(), advertisedChannelBindingTypes.orElseThrow());

            case "ANONYMOUS":
                final Object sessionValue = props == null ? null : props.get( LocalSession.class.getCanonicalName() );
                if ( !(sessionValue instanceof LocalSession session) )
                {
                    Log.debug( "Unable to instantiate {} SaslServer: Provided properties do not contain a LocalSession instance.", mechanism );
                    return null;
                }
                else
                {
                    return new AnonymousSaslServer(session);
                }

            case "EXTERNAL":
                if ( props == null  )
                {
                    Log.debug( "Unable to instantiate {} SaslServer: Provided properties do not contain a LocalSession instance.", mechanism );
                    return null;
                }
                else
                {
                    final Object sessionVal = props.get( LocalSession.class.getCanonicalName() );
                    if ( !(sessionVal instanceof LocalSession session) )
                    {
                        Log.debug( "Unable to instantiate {} SaslServer: Provided properties do not contain a LocalSession instance.", mechanism );
                        return null;
                    }
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

            case FastTokenManager.HT_SHA_256_NONE:
            case FastTokenManager.HT_SHA_256_UNIQ:
            case FastTokenManager.HT_SHA_256_ENDP:
            case FastTokenManager.HT_SHA_256_EXPR:
            case FastTokenManager.HT_SHA_512_NONE:
            case FastTokenManager.HT_SHA_512_UNIQ:
            case FastTokenManager.HT_SHA_512_ENDP:
            case FastTokenManager.HT_SHA_512_EXPR:
                return new HtSaslServer( mechanism, props );

            case FastTokenManager.HT2_SHA_256_NONE:
            case FastTokenManager.HT2_SHA_256_UNIQ:
            case FastTokenManager.HT2_SHA_256_ENDP:
            case FastTokenManager.HT2_SHA_256_EXPR:
            case FastTokenManager.HT2_SHA_512_NONE:
            case FastTokenManager.HT2_SHA_512_UNIQ:
            case FastTokenManager.HT2_SHA_512_ENDP:
            case FastTokenManager.HT2_SHA_512_EXPR:
                return new Ht2SaslServer( mechanism, props );

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

    /**
     * Extracts the set of SASL mechanism names that was advertised to the peer, from the session data of the
     * LocalSession instance that is expected to be stored in the provided properties.
     *
     * Returns an empty Optional when the properties contain no LocalSession, or when no mechanisms have been
     * recorded as advertised for that session. Note that this is distinct from a session that was advertised an
     * empty set of mechanisms, which yields a present Optional holding an empty set.
     *
     * @param props the property map
     * @return the SASL mechanism names advertised to the peer, if known
     * @see SASLAuthentication#getAdvertisedSASLMechanisms(LocalSession)
     */
    private static Optional<Set<String>> extractAdvertisedSASLMechanisms(Map<String, ?> props)
    {
        final Object sessionValue = props == null ? null : props.get( LocalSession.class.getCanonicalName() );
        if (!(sessionValue instanceof LocalSession session)) {
            Log.trace("Provided properties do not contain a LocalSession instance.");
            return Optional.empty();
        } else {
            return SASLAuthentication.getAdvertisedSASLMechanisms(session);
        }
    }

    /**
     * Extracts the set of channel-binding types that was advertised to the peer, from the session data of the
     * LocalSession instance that is expected to be stored in the provided properties.
     *
     * Returns an empty Optional when the properties contain no LocalSession, or when no channel-binding types have
     * been recorded as advertised for that session. Note that this is distinct from a session that was advertised
     * no channel-binding types at all, which yields a present Optional holding an empty set; that is the normal
     * case for a session that was offered no channel-binding-capable mechanism.
     *
     * @param props the property map
     * @return the channel-binding types advertised to the peer, if known
     * @see SASLAuthentication#getAdvertisedChannelBindingTypes(LocalSession)
     */
    private static Optional<Set<String>> extractAdvertisedChannelBindingTypes(Map<String, ?> props)
    {
        final Object sessionValue = props == null ? null : props.get( LocalSession.class.getCanonicalName() );
        if (!(sessionValue instanceof LocalSession session)) {
            Log.trace("Provided properties do not contain a LocalSession instance.");
            return Optional.empty();
        } else {
            return SASLAuthentication.getAdvertisedChannelBindingTypes(session);
        }
    }
}
