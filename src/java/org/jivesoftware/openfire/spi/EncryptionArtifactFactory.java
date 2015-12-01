package org.jivesoftware.openfire.spi;

import org.apache.mina.filter.ssl.SslFilter;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.jivesoftware.openfire.keystore.OpenfireX509TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.security.*;
import java.util.ArrayList;
import java.util.Set;

/**
 * Instances of this class will be able to generate various encryption-related artifacts based on a specific connection
 * configuration.
 *
 * This implementation intends to centralize the implementation for generating the artifacts produced, which in earlier
 * versions of the code-base was scattered (and duplicated) over various connection-type-specific implementations.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class EncryptionArtifactFactory
{
    private final Logger Log = LoggerFactory.getLogger( EncryptionArtifactFactory.class );

    private final ConnectionConfiguration configuration;

    // lazy loaded factory objects. These re-usable objects should be lazy loaded, preventing initialization in situations where they're never going to be used.
    private transient KeyManagerFactory keyManagerFactory;
    private transient SslContextFactory sslContextFactory;

    /**
     * Creates a new instance of the factory.
     *
     * @param configuration the configuration for which this factory generates artifacts (cannot be null).
     */
    public EncryptionArtifactFactory( ConnectionConfiguration configuration )
    {
        if ( configuration == null ) {
            throw new IllegalArgumentException( "Argument 'configuration' cannot be null" );
        }
        this.configuration = configuration;
    }

    /**
     * Generates KeyManager instances suitable for connections that are created based on a particular configuration.
     *
     * @return KeyManagers applicable to a connection that is established using the provided configuration.
     */
    public synchronized KeyManager[] getKeyManagers() throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException
    {
        try
        {
            if ( keyManagerFactory == null )
            {
                keyManagerFactory = KeyManagerFactory.getInstance( KeyManagerFactory.getDefaultAlgorithm() );
                keyManagerFactory.init( configuration.getIdentityStore().getStore(), configuration.getIdentityStoreConfiguration().getPassword() );
            }

            return keyManagerFactory.getKeyManagers();
        }
        catch ( UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException | RuntimeException ex )
        {
            // Allow initialization to restart upon next iteration.
            keyManagerFactory = null;
            throw ex;
        }
    }

    /**
     * Generates KeyManager instances suitable for connections that are created based on a particular configuration.
     *
     * @return TrustManagers applicable to a connection that is established using the provided configuration.
     */
    public synchronized TrustManager[] getTrustManagers() throws KeyStoreException, NoSuchAlgorithmException
    {
        return new TrustManager[] {
                new OpenfireX509TrustManager( configuration.getTrustStore().getStore(), configuration.isAcceptSelfSignedCertificates(), configuration.isVerifyCertificateValidity() )
        };
    }

    /**
     * Generates a new, initialized SSLContext instance that is suitable for connections that are created based on a
     * particular configuration.
     *
     * @return TrustManagers applicable to a connection that is established using the provided configuration.
     */
    public synchronized SSLContext getSSLContext() throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException
    {
        final SSLContext sslContext = SSLContext.getInstance( "TLSv1" );
        sslContext.init( getKeyManagers(), getTrustManagers(), new SecureRandom() );
        return sslContext;
    }

    /**
     * A utility method that implements the shared functionality of getClientModeSSLEngine and getServerModeSSLEngine.
     *
     * This method is used to initialize and pre-configure an instance of SSLEngine for a particular connection type.
     * The returned value lacks further configuration. In most cases, developers will want to use getClientModeSSLEngine
     * or getServerModeSSLEngine instead of this method.
     *
     * @return A new pre-configured SSLEngine instance (never null).
     */
    private SSLEngine createSSLEngine() throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException
    {
        final SSLContext sslContext = getSSLContext();

        final SSLEngine sslEngine = sslContext.createSSLEngine();

        // Configure protocol support.
        final Set<String> protocolsEnabled = configuration.getEncryptionProtocolsEnabled();
        if ( !protocolsEnabled.isEmpty() )
        {
            // When an explicit list of enabled protocols is defined, use only those.
            sslEngine.setEnabledProtocols( protocolsEnabled.toArray( new String[ protocolsEnabled.size() ] ) );
        }
        else
        {
            // Otherwise, use all supported protocols (except for the ones that are explicitly disabled).
            final Set<String> disabled = configuration.getEncryptionProtocolsDisabled();
            final ArrayList<String> supported = new ArrayList<>();
            for ( final String candidate : sslEngine.getSupportedProtocols() )
            {
                if ( !disabled.contains( candidate ) )
                {
                    supported.add( candidate );
                }
            }

            sslEngine.setEnabledProtocols( supported.toArray( new String[ supported.size()] ) );
        }

        // Configure cipher suite support.
        final Set<String> cipherSuitesEnabled = configuration.getCipherSuitesEnabled();
        if ( !cipherSuitesEnabled.isEmpty() )
        {
            // When an explicit list of enabled protocols is defined, use only those.
            sslEngine.setEnabledCipherSuites( cipherSuitesEnabled.toArray( new String[ cipherSuitesEnabled.size() ] ) );
        }
        else
        {
            // Otherwise, use all supported cipher suites (except for the ones that are explicitly disabled).
            final Set<String> disabled = configuration.getCipherSuitesDisabled();
            final ArrayList<String> supported = new ArrayList<>();
            for ( final String candidate : sslEngine.getSupportedCipherSuites() )
            {
                if ( !disabled.contains( candidate ) )
                {
                    supported.add( candidate );
                }
            }

            sslEngine.setEnabledCipherSuites( supported.toArray( new String[ supported.size() ] ) );
        }

        return sslEngine;
    }

    /**
     * Creates a new SSL Engine that is configured to use server mode when handshaking.
     *
     * For Openfire, an engine is of this mode used for most purposes (as Openfire is a server by nature).
     *
     * @return A new, initialized SSLEngine instance (never null).
     */
    public SSLEngine createServerModeSSLEngine() throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException
    {
        final SSLEngine sslEngine = createSSLEngine( );
        sslEngine.setUseClientMode( false );

        switch ( configuration.getClientAuth() )
        {
            case needed:
                sslEngine.setNeedClientAuth( true );
                break;

            case wanted:
                sslEngine.setWantClientAuth( true );
                break;

            case disabled:
                sslEngine.setWantClientAuth( false );
                break;
        }

        return sslEngine;
    }

    /**
     * Creates an SSL Engine that is configured to use client mode when handshaking.
     *
     * For Openfire, an engine of this mode is typically used when the server tries to connect to another server.
     *
     * @return An initialized SSLEngine instance (never null).
     */
    public SSLEngine createClientModeSSLEngine() throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException
    {
        final SSLEngine sslEngine = createSSLEngine(  );
        sslEngine.setUseClientMode( true );

        return sslEngine;
    }

    public synchronized SslContextFactory getSslContextFactory()
    {
        if ( sslContextFactory != null )
        {
            return sslContextFactory;
        }

        Log.info( "Creating new SslContextFactory instance" );
        try
        {
            sslContextFactory = new SslContextFactory();

            sslContextFactory.setTrustStore( configuration.getTrustStore().getStore() );
            sslContextFactory.setTrustStorePassword( new String( configuration.getTrustStore().getConfiguration().getPassword() ) );

            sslContextFactory.setKeyStore( configuration.getIdentityStore().getStore() );
            sslContextFactory.setKeyStorePassword( new String( configuration.getIdentityStore().getConfiguration().getPassword() ) );

            // Configure protocol support
            if ( configuration.getEncryptionProtocolsEnabled() != null && !configuration.getEncryptionProtocolsEnabled().isEmpty() )
            {
                sslContextFactory.setIncludeProtocols( configuration.getEncryptionProtocolsEnabled().toArray( new String[ configuration.getEncryptionProtocolsEnabled().size() ] ) );
            }
            sslContextFactory.setExcludeProtocols( configuration.getEncryptionProtocolsDisabled().toArray( new String[ configuration.getEncryptionProtocolsDisabled().size() ] ) );

            // Configure cipher suite support.
            if ( configuration.getCipherSuitesEnabled() != null && !configuration.getCipherSuitesEnabled().isEmpty() )
            {
                sslContextFactory.setIncludeCipherSuites( configuration.getCipherSuitesEnabled().toArray( new String[ configuration.getCipherSuitesEnabled().size() ] ) );
            }
            sslContextFactory.setExcludeCipherSuites( configuration.getCipherSuitesDisabled().toArray( new String[ configuration.getCipherSuitesDisabled().size() ] ) );

            //Set policy for checking client certificates
            switch ( configuration.getClientAuth() )
            {
                case disabled:
                    sslContextFactory.setNeedClientAuth( false );
                    sslContextFactory.setWantClientAuth( false );
                    break;
                case wanted:
                    sslContextFactory.setNeedClientAuth( false );
                    sslContextFactory.setWantClientAuth( true );
                    break;
                case needed:
                    sslContextFactory.setNeedClientAuth( true );
                    break;
            }
            return sslContextFactory;
        }
        catch ( RuntimeException ex )
        {
            // Allow initialization to restart upon next iteration.
            sslContextFactory = null;
            throw ex;
        }
    }

    /**
     * Creates an Apache MINA SslFilter that is configured to use server mode when handshaking.
     *
     * For Openfire, an engine is of this mode used for most purposes (as Openfire is a server by nature).
     *
     * Instead of an SSLContext or SSLEngine, Apache MINA uses an SslFilter instance. It is generally not needed to
     * create both SSLContext/SSLEngine as well as SslFilter instances.
     *
     * @return An initialized SslFilter instance (never null)
     */
    public SslFilter createServerModeSslFilter() throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException
    {
        final SSLContext sslContext = getSSLContext();
        final SSLEngine sslEngine = createServerModeSSLEngine();

        return createSslFilter( sslContext, sslEngine );
    }

    /**
     * Creates an Apache MINA SslFilter that is configured to use client mode when handshaking.
     *
     * For Openfire, a filter of this mode is typically used when the server tries to connect to another server.
     *
     * Instead of an SSLContext or SSLEngine, Apache MINA uses an SslFilter instance. It is generally not needed to
     * create both SSLContext/SSLEngine as well as SslFilter instances.
     *
     * @return An initialized SslFilter instance (never null)
     */
    public SslFilter createClientModeSslFilter() throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException
    {
        final SSLContext sslContext = getSSLContext();
        final SSLEngine sslEngine = createClientModeSSLEngine();

        return createSslFilter( sslContext, sslEngine );
    }

    /**
     * A utility method that implements the shared functionality of getServerModeSslFilter and getClientModeSslFilter.
     *
     * This method is used to initialize and configure an instance of SslFilter for a particular pre-configured
     * SSLContext and SSLEngine. In most cases, developers will want to use getServerModeSslFilter or
     * getClientModeSslFilter instead of this method.
     *
     * @param sslContext a pre-configured SSL Context instance (cannot be null).
     * @param sslEngine a pre-configured SSL Engine instance (cannot be null).
     * @return A SslFilter instance (never null).
     */
    private static SslFilter createSslFilter( SSLContext sslContext, SSLEngine sslEngine ) {
        final SslFilter filter = new SslFilter( sslContext );

        // Copy configuration from the SSL Engine into the filter.
        filter.setUseClientMode( sslEngine.getUseClientMode() );
        filter.setEnabledProtocols( sslEngine.getEnabledProtocols() );
        filter.setEnabledCipherSuites( sslEngine.getEnabledCipherSuites() );

        // Note that the setters for 'need' and 'want' influence each-other. Invoke only one of them!
        if ( sslEngine.getNeedClientAuth() )
        {
            filter.setNeedClientAuth( true );
        }
        else if ( sslEngine.getWantClientAuth() )
        {
            filter.setWantClientAuth( true );
        }
        return filter;
    }
}
