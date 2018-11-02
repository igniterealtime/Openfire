package org.tiki.tikitoken;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslServerFactory;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A SaslServerFactory implementation that is used to instantiate TikiToken-based SaslServer instances.
 *
 * This implementation makes use of a Tiki server for token validation.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class TikiTokenSaslServerFactory implements SaslServerFactory
{
    private static final Logger Log = LoggerFactory.getLogger( TikiTokenSaslServerFactory.class );

    public TikiTokenSaslServerFactory()
    {
    }

    public SaslServer createSaslServer( String mechanism, String protocol, String serverName, Map<String, ?> props, CallbackHandler cbh ) throws SaslException
    {
        // Do not return an instance when the provided properties contain Policy settings that disallow our implementations.
        final Set<String> mechanismNames = getMechanismNamesSet( props );

        if ( mechanismNames.contains( mechanism ) && mechanism.equalsIgnoreCase( TikiTokenSaslServer.MECHANISM_NAME ) )
        {
            Log.debug( "Instantiating a new TikiTokenSaslServer instance." );
            return new TikiTokenSaslServer();
        }

        Log.debug( "Unable to instantiate a SaslServer instance that matches the requested properties." );
        return null;
    }

    public String[] getMechanismNames( Map<String, ?> props )
    {
        final Set<String> result = getMechanismNamesSet( props );
        return result.toArray( new String[ result.size() ] );
    }

    protected final Set<String> getMechanismNamesSet( Map<String, ?> props )
    {
        final Set<String> supportedMechanisms = new HashSet<String>();
        supportedMechanisms.add( TikiTokenSaslServer.MECHANISM_NAME );

        if ( props != null )
        {
            for ( Map.Entry<String, ?> prop : props.entrySet() )
            {
                if ( !( prop.getValue() instanceof String ) )
                {
                    continue;
                }

                final String name = prop.getKey();
                final String value = (String) prop.getValue();

                if ( Sasl.POLICY_NOPLAINTEXT.equalsIgnoreCase( name ) && "true".equalsIgnoreCase( value ) )
                {
                    Log.info( "Removing '{}' mechanism, as the provided properties define a NOPLAINTEXT policy.", TikiTokenSaslServer.MECHANISM_NAME );
                    supportedMechanisms.remove( TikiTokenSaslServer.MECHANISM_NAME );
                }

                // TODO Determine if other policies are relevant.
            }
        }
        return supportedMechanisms;
    }
}
