/*
 * Copyright (C) 2018-2023 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.session.LocalIncomingServerSession;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import java.nio.charset.StandardCharsets;

/**
 * Implementation of the SASL EXTERNAL mechanism with PKIX to be used for server-to-server connections.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @see <a href="http://tools.ietf.org/html/rfc6125">RFC 6125</a>
 * @see <a href="http://xmpp.org/extensions/xep-0178.html">XEP 0178</a>
 */
public class ExternalServerSaslServer implements SaslServer
{
    private static final Logger Log = LoggerFactory.getLogger(ExternalServerSaslServer.class);

    /**
     * This property controls if the inbound connection is required to provide an authorization identity in the SASL
     * EXTERNAL handshake (as part of an `auth` element). In older XMPP specifications, it was not required to have a
     * `from` attribute on the stream, making the authzid a required part of the handshake.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2639">Issue OF-2639</a>
     */
    public static final SystemProperty<Boolean> PROPERTY_SASL_EXTERNAL_SERVER_REQUIRE_AUTHZID = SystemProperty.Builder
        .ofType( Boolean.class )
        .setKey( "xmpp.auth.sasl.external.server.require-authzid" )
        .setDefaultValue( false )
        .setDynamic( true )
        .build();

    public static final String NAME = "EXTERNAL";

    private boolean complete = false;

    private String authorizationID = null;

    private LocalIncomingServerSession session;

    public ExternalServerSaslServer( LocalIncomingServerSession session ) throws SaslException
    {
        this.session = session;
    }

    @Override
    public String getMechanismName()
    {
        return NAME;
    }

    @Override
    public byte[] evaluateResponse( @Nonnull final byte[] response ) throws SaslException
    {
        if ( isComplete() )
        {
            throw new IllegalStateException( "Authentication exchange already completed." );
        }

        // The value as sent to us in the 'from' attribute of the stream element sent by the remote server.
        final String defaultIdentity = session.getDefaultIdentity();

        // RFC 6120 Section 4.7.1:
        //    "Because a server is a "public entity" on the XMPP network, it MUST include the 'from' attribute after the
        //     confidentiality and integrity of the stream are protected via TLS or an equivalent security layer."
        //
        // When doing SASL EXTERNAL, TLS must already have been negotiated, which means that the 'from' attribute must have been set.
        if (defaultIdentity == null || defaultIdentity.isEmpty()) {
            throw new SaslFailureException(Failure.NOT_AUTHORIZED, "Peer does not provide 'from' attribute value on stream.");
        }

        final String requestedId;
        if (response.length == 0 && session.getSessionData(SASLAuthentication.SASL_LAST_RESPONSE_WAS_PROVIDED_BUT_EMPTY) == null) {
            if (PROPERTY_SASL_EXTERNAL_SERVER_REQUIRE_AUTHZID.getValue()) {
                // No initial response. Send a challenge to get one, per RFC 4422 appendix-A.
                return new byte[0];
            } else {
                requestedId = defaultIdentity;
            }
        }
        else
        {
            requestedId = new String( response, StandardCharsets.UTF_8 );
        }

        complete = true;

        Log.trace("Completing handshake with '{}' using authzid value: '{}'", defaultIdentity, requestedId);

        // Added for backwards compatibility. Not required by XMPP, but versions of Openfire prior to 4.8.0 did require the authzid to be present.
        if (SASLAuthentication.EXTERNAL_S2S_REQUIRE_AUTHZID.getValue() && requestedId.isEmpty()) {
            throw new SaslFailureException(Failure.INVALID_AUTHZID, "Peer does not provide authzid, which is required by configuration.");
        }

        // When an authorization identity is provided, make sure that it matches the 'from' value from the session stream.
        if (!requestedId.isEmpty() && !requestedId.equals(defaultIdentity)) {
            throw new SaslFailureException(Failure.INVALID_AUTHZID, "Stream 'from' attribute value '" + defaultIdentity + "' does not equal SASL authzid '" + requestedId + "'");
        }

        if (!SASLAuthentication.verifyCertificates(session.getConnection().getPeerCertificates(), defaultIdentity, true)) {
            throw new SaslFailureException(Failure.NOT_AUTHORIZED, "Server-to-Server certificate verification failed.");
        }

        authorizationID = defaultIdentity;

        Log.trace("Successfully authenticated '{}'", authorizationID);
        return null; // Success!
    }

    @Override
    public boolean isComplete()
    {
        return complete;
    }

    @Override
    public String getAuthorizationID()
    {
        if ( !isComplete() )
        {
            throw new IllegalStateException( "Authentication exchange not completed." );
        }

        return authorizationID;
    }

    @Override
    public byte[] unwrap( byte[] incoming, int offset, int len ) throws SaslException
    {
        if ( !isComplete() )
        {
            throw new IllegalStateException( "Authentication exchange not completed." );
        }

        throw new IllegalStateException( "SASL Mechanism '" + getMechanismName() + " does not support integrity nor privacy." );
    }

    @Override
    public byte[] wrap( byte[] outgoing, int offset, int len ) throws SaslException
    {
        if ( !isComplete() )
        {
            throw new IllegalStateException( "Authentication exchange not completed." );
        }

        throw new IllegalStateException( "SASL Mechanism '" + getMechanismName() + " does not support integrity nor privacy." );
    }

    @Override
    public Object getNegotiatedProperty( String propName )
    {
        if ( !isComplete() )
        {
            throw new IllegalStateException( "Authentication exchange not completed." );
        }

        if ( propName.equals( Sasl.QOP ) )
        {
            return "auth";
        }
        else
        {
            return null;
        }
    }

    @Override
    public void dispose() throws SaslException
    {
        complete = false;
        authorizationID = null;
        session = null;
    }
}
