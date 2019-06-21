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
    public byte[] evaluateResponse( byte[] response ) throws SaslException
    {
        if ( isComplete() )
        {
            throw new IllegalStateException( "Authentication exchange already completed." );
        }

        // There will be no further steps. Either authentication succeeds or fails, but in any case, we're done.
        complete = true;

        final Connection connection = session.getConnection();
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

        // Process client identities / principals.
        final ArrayList<String> principals = new ArrayList<>();
        principals.addAll( CertificateManager.getClientIdentities( trusted ) );
        String principal;
        switch ( principals.size() )
        {
            case 0:
                principal = "";
                break;

            default:
                Log.debug( "More than one principal found, using the first one." );
                // intended fall-through;
            case 1:
                principal = principals.get( 0 );
                break;
        }

        // Process requested user name.
        String username;
        if ( response != null && response.length > 0 )
        {
            username = new String( response, StandardCharsets.UTF_8 );
            if( PROPERTY_SASL_EXTERNAL_CLIENT_SUPPRESS_MATCHING_REALMNAME.getValue() && username.contains("@") ) {
                String userUser = username.substring(0,username.lastIndexOf("@"));
                String userRealm = username.substring((username.lastIndexOf("@")+1));
                if ( XMPPServer.getInstance().getServerInfo().getXMPPDomain().equals( userRealm ) ) {
                    username = userUser;
                }
            }
        }
        else
        {
            username = null;
        }

        if ( username == null || username.length() == 0 )
        {
            // No username was provided, according to XEP-0178 we need to:
            //    * attempt to get it from the cert first
            //    * have the server assign one

            // There shouldn't be more than a few principals in here. One ideally. We set principal to the first one in
            // the list to have a sane default. If this list is empty, then the cert had no identity at all, which will
            // cause an authorization failure.
            for ( String princ : principals )
            {
                final String mappedUsername = AuthorizationManager.map( princ );
                if ( !mappedUsername.equals( princ ) )
                {
                    username = mappedUsername;
                    principal = princ;
                    break;
                }
            }

            if ( username == null || username.length() == 0 )
            {
                // Still no username.  Punt.
                username = principal;
            }
            Log.debug( "No username requested, using: {}", username );
        }

        // Its possible that either/both username and principal are null here. The providers should not allow a null authorization
        if ( AuthorizationManager.authorize( username, principal ) )
        {
            Log.debug( "Principal {} authorized to username {}", principal, username );
            authorizationID = username;
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
