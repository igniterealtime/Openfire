package org.jivesoftware.openfire.sasl;

import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.util.SystemProperty;

/**
 * Implementation of the SASL ANONYMOUS mechanism.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @see <a href="http://tools.ietf.org/html/rfc4505">RFC 4505</a>
 * @see <a href="http://xmpp.org/extensions/xep-0175.html">XEP 0175</a>
 */
public class AnonymousSaslServer implements SaslServer
{
    public static final SystemProperty<Boolean> ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.auth.anonymous")
        .setDefaultValue(Boolean.FALSE)
        .setDynamic(Boolean.TRUE)
        .build();

    public static final String NAME = "ANONYMOUS";

    private boolean complete = false;

    private LocalSession session;

    public AnonymousSaslServer( LocalSession session )
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

        complete = true;

        // Verify server-wide policy.
        if (!ENABLED.getValue())
        {
            throw new SaslException( "Authentication failed" );
        }

        // Verify that client can connect from his IP address.
        final boolean forbidAccess = !LocalClientSession.isAllowedAnonymous( session.getConnection() );
        if ( forbidAccess )
        {
            throw new SaslException( "Authentication failed" );
        }

        // Just accept the authentication :)
        return null;
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

        return null; // Anonymous!
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
        session = null;
    }
}
