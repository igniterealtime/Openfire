package org.jivesoftware.openfire.sasl;

import org.jivesoftware.openfire.session.LocalIncomingServerSession;

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
    public byte[] evaluateResponse( byte[] response ) throws SaslException
    {
        if ( isComplete() )
        {
            throw new IllegalStateException( "Authentication exchange already completed." );
        }

        if ( response == null || response.length == 0 )
        {
            // No hostname was provided so send a challenge to get it
            return new byte[ 0 ];
        }

        complete = true;

        final String requestedId = new String( response, StandardCharsets.UTF_8 );
        final String defaultIdentity = session.getDefaultIdentity();
        if ( !requestedId.equals( defaultIdentity ) )
        {
            throw new SaslException( "From '" + requestedId + "' does not equal authzid '" + defaultIdentity + "'" );
        }

        authorizationID = requestedId;
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
