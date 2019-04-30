package org.jivesoftware.openfire.sasl;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.StringUtils;

import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import java.nio.charset.StandardCharsets;
import java.util.StringTokenizer;

/**
 * Implementation of a proprietary Jive Software SASL mechanism that is based on a shared secret. Successful
 * authentication will result in an anonymous authorization.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class JiveSharedSecretSaslServer implements SaslServer
{
    public static final String NAME = "JIVE-SHAREDSECRET";

    private boolean complete = false;

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
            // No info was provided so send a challenge to get it.
            return new byte[ 0 ];
        }

        complete = true;

        // Parse data and obtain username & password.
        final StringTokenizer tokens = new StringTokenizer( new String( response, StandardCharsets.UTF_8 ), "\0" );
        tokens.nextToken();
        final String secretDigest = tokens.nextToken();

        if ( authenticateSharedSecret( secretDigest ) )
        {
            return null; // Success!
        }
        else
        {
            // Otherwise, authentication failed.
            throw new SaslException( "Authentication failed" );
        }
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
    }

    /**
     * Returns true if the supplied digest matches the shared secret value. The digest must be an MD5 hash of the secret
     * key, encoded as hex. This value is supplied by clients attempting shared secret authentication.
     *
     * @param digest the MD5 hash of the secret key, encoded as hex.
     * @return true if authentication succeeds.
     */
    public static boolean authenticateSharedSecret( String digest )
    {
        if ( !isSharedSecretAllowed() )
        {
            return false;
        }

        return StringUtils.hash( getSharedSecret() ).equals( digest );
    }

    /**
     * Returns true if shared secret authentication is enabled. Shared secret authentication creates an anonymous
     * session, but requires that the authenticating entity know a shared secret key. The client sends a digest of the
     * secret key, which is compared against a digest of the local shared key.
     *
     * @return true if shared secret authentication is enabled.
     */
    public static boolean isSharedSecretAllowed()
    {
        return JiveGlobals.getBooleanProperty( "xmpp.auth.sharedSecretEnabled" );
    }

    /**
     * Returns the shared secret value, or {@code null} if shared secret authentication is disabled. If this is the
     * first time the shared secret value has been requested (and  shared secret auth is enabled), the key will be
     * randomly generated and stored in the property {@code xmpp.auth.sharedSecret}.
     *
     * @return the shared secret value.
     */
    public static String getSharedSecret()
    {
        if ( !isSharedSecretAllowed() )
        {
            return null;
        }

        String sharedSecret = JiveGlobals.getProperty( "xmpp.auth.sharedSecret" );
        if ( sharedSecret == null )
        {
            sharedSecret = StringUtils.randomString( 8 );
            JiveGlobals.setProperty( "xmpp.auth.sharedSecret", sharedSecret );
        }
        return sharedSecret;
    }

    /**
     * Sets whether shared secret authentication is enabled. Shared secret authentication creates an anonymous session,
     * but requires that the authenticating entity know a shared secret key. The client sends a digest of the secret
     * key, which is compared against a digest of the local shared key.
     *
     * @param sharedSecretAllowed true if shared secret authentication should be enabled.
     */
    public static void setSharedSecretAllowed( boolean sharedSecretAllowed )
    {
        JiveGlobals.setProperty( "xmpp.auth.sharedSecretEnabled", sharedSecretAllowed ? "true" : "false" );
    }
}
