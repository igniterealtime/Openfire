package org.tiki.tikitoken;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import java.nio.charset.StandardCharsets;
import java.util.StringTokenizer;

/**
 * A SaslServer implementation of the TikiToken mechanism.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class TikiTokenSaslServer implements SaslServer
{
    private final static Logger Log = LoggerFactory.getLogger( TikiTokenSaslServer.class );

    /**
     * The mechanism name of this implementation: "TIKITOKEN"
     */
    public static final String MECHANISM_NAME = "TIKITOKEN";

    private enum State {

        /** Initial state. Has not evaluated any response yet. */
        PRE_INITIAL_RESPONSE,

        /** Has evaluated an initial response, but has not yet completed. */
        POST_INITIAL_RESPONSE,

        /** Done (authentication succeeded or failed). */
        COMPLETED
    }

    private String authorizationID = null;

    private State state = State.PRE_INITIAL_RESPONSE;

    public TikiTokenSaslServer()
    {}

    /**
     * Returns the mechanism name of this SASL server: "TIKITOKEN".
     *
     * @return A non-null string representing the mechanism name: TIKITOKEN
     */
    public String getMechanismName()
    {
        return MECHANISM_NAME;
    }

    public byte[] evaluateResponse( byte[] response ) throws SaslException
    {
        Log.trace( "Evaluating new response..." );

        if( isComplete() )
        {
            throw new IllegalStateException( "TIKITOKEN authentication was already completed." );
        }

        Log.trace( "Current state: {}", state );
        switch ( state )
        {
            case POST_INITIAL_RESPONSE:

                if ( response.length == 0 )
                {
                    state = State.COMPLETED;
                    throw new SaslException( "The TIKITOKEN SASL mechanism expects response data in either the initial or second client response. Neither had any data." );
                }

                // Intended fall-through.
            case PRE_INITIAL_RESPONSE:

                if ( response.length == 0 )
                {
                    // No data in the initial response. Ask for data by responding with a success.
                    state = State.POST_INITIAL_RESPONSE;
                    return null;
                }
                else
                {
                    // We have data: no further responses are expected.
                    state = State.COMPLETED;

                    Log.trace( "Parsing data from client response..." );
                    final String data = new String( response, StandardCharsets.UTF_8);
                    final StringTokenizer tokens = new StringTokenizer( data, "\0");
                    if ( tokens.countTokens() != 2 )
                    {
                        throw new SaslException( "Exactly two NUL (U+0000) character-separated values are expected (a username, followed by a Tiki access token). Instead " +  tokens.countTokens() + " were found." );
                    }

                    final String username = tokens.nextToken();
                    final String tikiToken = tokens.nextToken();

                    Log.trace( "Parsed data from client response for user '{}'. Verifying Tiki token...", username );
                    final TikiTokenQuery query = new TikiTokenQuery( username, tikiToken );
                    if ( !query.isValid() )
                    {
                        throw new SaslException( "Tiki token based authentication failed for: " + username );
                    }

                    Log.debug( "Authentication successful for user '{}'!", username );
                    authorizationID = username;
                    return null;
                }

            default:
                throw new IllegalStateException( "Instance is in an unrecognized state (please report this incident as a bug in class: " + this.getClass().getCanonicalName() + "). Unrecognized value: " + state );
        }
    }

    public boolean isComplete()
    {
        return state == State.COMPLETED;
    }

    public String getAuthorizationID()
    {
        if( !isComplete() )
        {
            throw new IllegalStateException( "TIKITOKEN authentication has not completed." );
        }

        return authorizationID;
    }

    public Object getNegotiatedProperty( String propName )
    {
        if( !isComplete() )
        {
            throw new IllegalStateException( "TIKITOKEN authentication has not completed." );
        }

        if ( Sasl.QOP.equals( propName ) )
        {
            return "auth";
        }
        return null;
    }

    public void dispose() throws SaslException
    {
        state = null;
        authorizationID = null;
    }

    public byte[] unwrap( byte[] incoming, int offset, int len ) throws SaslException
    {
        if( !isComplete() )
        {
            throw new IllegalStateException( "TIKITOKEN authentication has not completed." );
        }

        throw new IllegalStateException( "TIKITOKEN supports neither integrity nor privacy." );
    }

    public byte[] wrap( byte[] outgoing, int offset, int len ) throws SaslException
    {
        if( !isComplete() )
        {
            throw new IllegalStateException( "TIKITOKEN authentication has not completed." );
        }

        throw new IllegalStateException( "TIKITOKEN supports neither integrity nor privacy." );
    }
}
