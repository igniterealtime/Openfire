/*
 * Copyright (C) 2017-2023 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthorizationManager;
import org.jivesoftware.openfire.keystore.TrustStore;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.util.CertificateManager;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;

/**
 * Implementation of the SASL EXTERNAL mechanism with PKIX to be used for client-to-server connections.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @see <a href="http://tools.ietf.org/html/rfc6125">RFC 6125</a>
 * @see <a href="http://xmpp.org/extensions/xep-0178.html">XEP 0178</a>
 */
public class ExternalClientSaslServer implements SaslServer
{
    public static final SystemProperty<Boolean> PROPERTY_SASL_EXTERNAL_CLIENT_SUPPRESS_MATCHING_REALMNAME = SystemProperty.Builder
        .ofType( Boolean.class )
        .setKey( "xmpp.auth.sasl.external.client.suppress-matching-realmname" )
        .setDefaultValue( true )
        .setDynamic( true )
        .build();

    public static final Logger Log = LoggerFactory.getLogger( ExternalClientSaslServer.class );

    public static final String NAME = "EXTERNAL";

    private boolean complete = false;

    private String authorizationID = null;

    private LocalClientSession session;

    public ExternalClientSaslServer( LocalClientSession session ) throws SaslException
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

        if (response.length == 0 && session.getSessionData(SASLAuthentication.SASL_LAST_RESPONSE_WAS_PROVIDED_BUT_EMPTY) != null) {
            // No initial response. Send a challenge to get one, per RFC 4422 appendix-A.
            return new byte[ 0 ];
        }

        // There will be no further steps. Either authentication succeeds or fails, but in any case, we're done.
        complete = true;

        final Connection connection = session.getConnection();
        assert connection != null; // While the peer is performing a SASL negotiation, the connection can't be null.
        Certificate[] peerCertificates = connection.getPeerCertificates();
        if ( peerCertificates == null || peerCertificates.length < 1 )
        {
            throw new SaslException( "No peer certificates." );
        }

        final X509Certificate trusted;
        if ( SASLAuthentication.SKIP_PEER_CERT_REVALIDATION_CLIENT.getValue() ) {
            // Trust that the peer certificate has been validated when TLS got established.
            trusted = (X509Certificate) peerCertificates[0];
        } else {
            // Re-evaluate the validity of the peer certificate.
            final TrustStore trustStore = connection.getConfiguration().getTrustStore();
            trusted = trustStore.getEndEntityCertificate( peerCertificates );
        }

        if ( trusted == null )
        {
            throw new SaslException( "Certificate chain of peer is not trusted." );
        }

        // Process client authentication identities / principals.
        final ArrayList<String> authenticationIdentities = new ArrayList<>();
        authenticationIdentities.addAll( CertificateManager.getClientIdentities( trusted ) );
        String authenticationIdentity;
        switch ( authenticationIdentities.size() )
        {
            case 0:
                authenticationIdentity = "";
                break;

            default:
                Log.debug( "More than one authentication identity found, using the first one." );
                // intended fall-through;
            case 1:
                authenticationIdentity = authenticationIdentities.get( 0 );
                break;
        }

        // Process requested username to act as.
        String authorizationIdentity;
        if ( response.length > 0 )
        {
            authorizationIdentity = new String( response, StandardCharsets.UTF_8 );
            if( PROPERTY_SASL_EXTERNAL_CLIENT_SUPPRESS_MATCHING_REALMNAME.getValue() && authorizationIdentity.contains("@") ) {
                String authzUser = authorizationIdentity.substring(0,authorizationIdentity.lastIndexOf("@"));
                String authzRealm = authorizationIdentity.substring((authorizationIdentity.lastIndexOf("@")+1));
                if ( XMPPServer.getInstance().getServerInfo().getXMPPDomain().equals( authzRealm ) ) {
                    authorizationIdentity = authzUser;
                }
            }
        }
        else
        {
            authorizationIdentity = null;
        }

        if ( authorizationIdentity == null || authorizationIdentity.length() == 0 )
        {
            // No authorization identity was provided, according to XEP-0178 we need to:
            //    * attempt to get it from the cert first
            //    * have the server assign one

            // There shouldn't be more than a few authentication identities in here. One ideally. We set authcid to the
            // first one in the list to have a sane default. If this list is empty, then the cert had no identity at all, which will
            // cause an authorization failure.
            for ( String authcid : authenticationIdentities )
            {
                final String mappedUsername = AuthorizationManager.map( authcid );
                if ( !mappedUsername.equals( authcid ) )
                {
                    authorizationIdentity = mappedUsername;
                    authenticationIdentity = authcid;
                    break;
                }
            }

            if ( authorizationIdentity == null || authorizationIdentity.length() == 0 )
            {
                // Still no username to act as.  Punt.
                authorizationIdentity = authenticationIdentity;
            }
            Log.debug( "No username requested, using: {}", authorizationIdentity );
        }

        // It's possible that either/both authzid and authcid are null here. The providers should not allow a null authorization.
        if ( AuthorizationManager.authorize( authorizationIdentity, authenticationIdentity ) )
        {
            Log.debug( "Authcid {} authorized to authzid (username) {}", authenticationIdentity, authorizationIdentity );
            authorizationID = authorizationIdentity;
            return null; // Success!
        }

        throw new SaslException();
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
