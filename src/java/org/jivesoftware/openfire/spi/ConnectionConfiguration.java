package org.jivesoftware.openfire.spi;

import org.apache.mina.filter.ssl.SslFilter;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.keystore.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.net.InetAddress;
import java.security.*;
import java.util.*;

/**
 * Configuration for a socket connection.
 *
 * Instances of this class are thread-safe, with the exception of the internal state of the #bindAddress property.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class ConnectionConfiguration
{
    private final Logger Log;
    private final boolean enabled;
    private final ConnectionType type;
    private final int maxThreadPoolSize;
    private final int maxBufferSize;
    private final Connection.ClientAuth clientAuth;
    private final InetAddress bindAddress;
    private final int port;
    private final Connection.TLSPolicy tlsPolicy;
    private final CertificateStoreConfiguration identityStoreConfiguration;
    private final CertificateStoreConfiguration trustStoreConfiguration;
    private final boolean acceptSelfSignedCertificates;
    private final boolean verifyCertificateValidity;
    private final Set<String> encryptionProtocolsEnabled;
    private final Set<String> encryptionProtocolsDisabled;
    private final Set<String> cipherSuitesEnabled;
    private final Set<String> cipherSuitesDisabled;

    // derived
    private final IdentityStore identityStore;
    private final TrustStore trustStore;

    // derived & lazy loaded factory objects. These re-usable objects should be lazy loaded, preventing initialization in configurations where they're never going to be used.
    private transient KeyManagerFactory keyManagerFactory;
    private transient SSLContext sslContext;
    private transient SslContextFactory sslContextFactory;


    public synchronized KeyManager[] getKeyManagers() throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException
    {
        try
        {
            if ( keyManagerFactory == null )
            {
                keyManagerFactory = KeyManagerFactory.getInstance( KeyManagerFactory.getDefaultAlgorithm() );
                keyManagerFactory.init( getIdentityStore().getStore(), identityStoreConfiguration.getPassword() );
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

    public synchronized TrustManager[] getTrustManagers() throws KeyStoreException, NoSuchAlgorithmException
    {
        return new TrustManager[] { new OpenfireX509TrustManager( trustStore.getStore(), isAcceptSelfSignedCertificates(), isVerifyCertificateValidity() ) };
    }

    public synchronized SSLContext getSSLContext( ) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException
    {
        if ( sslContext == null )
        {
            sslContext = SSLContext.getInstance( "TLSv1" );
            try
            {
                sslContext.init( getKeyManagers(), getTrustManagers(), new SecureRandom() );
            }
            catch ( UnrecoverableKeyException | RuntimeException ex )
            {
                // Allow initialization to restart upon next iteration.
                sslContext = null;
                throw ex;
            }
        }
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
    private SSLEngine createSSLEngine( ) throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException
    {
        final SSLContext sslContext = getSSLContext();

        final SSLEngine sslEngine = sslContext.createSSLEngine();

        // Configure protocol support.
        final Set<String> protocolsEnabled = getEncryptionProtocolsEnabled();
        if ( !protocolsEnabled.isEmpty() )
        {
            // When an explicit list of enabled protocols is defined, use only those.
            sslEngine.setEnabledProtocols( protocolsEnabled.toArray( new String[ protocolsEnabled.size() ] ) );
        }
        else
        {
            // Otherwise, use all supported protocols (except for the ones that are explicitly disabled).
            final Set<String> disabled = getEncryptionProtocolsDisabled();
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
        final Set<String> cipherSuitesEnabled = getCipherSuitesEnabled();
        if ( !cipherSuitesEnabled.isEmpty() )
        {
            // When an explicit list of enabled protocols is defined, use only those.
            sslEngine.setEnabledCipherSuites( cipherSuitesEnabled.toArray( new String[ cipherSuitesEnabled.size() ] ) );
        }
        else
        {
            // Otherwise, use all supported cipher suites (except for the ones that are explicitly disabled).
            final Set<String> disabled = getCipherSuitesDisabled();
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

        // TODO: Set policy for checking client certificates

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
        final SSLEngine sslEngine = createSSLEngine();
        sslEngine.setUseClientMode( false );

        switch ( getClientAuth() )
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
    public SSLEngine createClientModeSSLEngine( ) throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException
    {
        final SSLEngine sslEngine = createSSLEngine();
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

            sslContextFactory.setTrustStore( trustStore.getStore() );
            sslContextFactory.setTrustStorePassword( new String( trustStoreConfiguration.getPassword() ) );

            sslContextFactory.setKeyStore( identityStore.getStore() );
            sslContextFactory.setKeyStorePassword( new String( identityStoreConfiguration.getPassword() ) );

            // Configure protocol support
            if ( getEncryptionProtocolsEnabled() != null && !getEncryptionProtocolsEnabled().isEmpty() )
            {
                sslContextFactory.setIncludeProtocols( getEncryptionProtocolsEnabled().toArray( new String[ getEncryptionProtocolsEnabled().size() ] ) );
            }
            sslContextFactory.setExcludeProtocols( getEncryptionProtocolsDisabled().toArray( new String[ getEncryptionProtocolsDisabled().size() ] ) );

            // Configure cipher suite support.
            if ( getCipherSuitesEnabled() != null && !getCipherSuitesEnabled().isEmpty() )
            {
                sslContextFactory.setIncludeCipherSuites( getCipherSuitesEnabled().toArray( new String[ getCipherSuitesEnabled().size() ] ) );
            }
            sslContextFactory.setExcludeCipherSuites( getCipherSuitesDisabled().toArray( new String[ getCipherSuitesDisabled().size() ] ) );

            //Set policy for checking client certificates
            switch ( clientAuth )
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


    /**
     * @param type
     * @param enabled
     * @param maxThreadPoolSize The maximum number of threads that are to be used to processing network activity. Must be equal to or larger than one.
     * @param maxBufferSize The maximum amount of bytes of the read buffer that I/O processor allocates per each read, or a non-positive value to configure no maximum.
     * @param clientAuth specification if peers should be authenticated ('mutual authentication') (cannot be null).
     * @param bindAddress The network address on which connections are accepted, or null when any local address can be used.
     * @param port The TCP port number on which connections are accepted (must be a valid TCP port number).
     * @param tlsPolicy The TLS policy that is applied to connections (cannot be null).
     */
    // TODO input validation
    public ConnectionConfiguration( ConnectionType type, boolean enabled, int maxThreadPoolSize, int maxBufferSize, Connection.ClientAuth clientAuth, InetAddress bindAddress, int port, Connection.TLSPolicy tlsPolicy, CertificateStoreConfiguration identityStoreConfiguration, CertificateStoreConfiguration trustStoreConfiguration, boolean acceptSelfSignedCertificates, boolean verifyCertificateValidity, Set<String> encryptionProtocolsEnabled, Set<String> encryptionProtocolsDisabled, Set<String> cipherSuitesEnabled, Set<String> cipherSuitesDisabled )
    {
        if ( maxThreadPoolSize <= 0 ) {
            throw new IllegalArgumentException( "Argument 'maxThreadPoolSize' must be equal to or greater than one." );
        }
        if ( clientAuth == null ) {
            throw new IllegalArgumentException( "Argument 'clientAuth' cannot be null." );
        }

        this.enabled = enabled;
        this.tlsPolicy = tlsPolicy;
        this.type = type;
        this.maxThreadPoolSize = maxThreadPoolSize;
        this.maxBufferSize = maxBufferSize;
        this.clientAuth = clientAuth;
        this.bindAddress = bindAddress;
        this.port = port;
        this.identityStoreConfiguration = identityStoreConfiguration;
        this.trustStoreConfiguration = trustStoreConfiguration;
        this.acceptSelfSignedCertificates = acceptSelfSignedCertificates;
        this.verifyCertificateValidity = verifyCertificateValidity;

        // Remove all disabled protocols from the enabled ones.
        final Set<String> protocolsEnabled = new HashSet<>();
        protocolsEnabled.addAll( encryptionProtocolsEnabled );
        protocolsEnabled.removeAll( encryptionProtocolsDisabled );
        this.encryptionProtocolsEnabled = Collections.unmodifiableSet( protocolsEnabled );
        this.encryptionProtocolsDisabled = Collections.unmodifiableSet( encryptionProtocolsDisabled );

        // Remove all disabled suites from the enabled ones.
        final Set<String> suitesEnabled = new HashSet<>();
        suitesEnabled.addAll( cipherSuitesEnabled );
        suitesEnabled.removeAll( cipherSuitesDisabled );
        this.cipherSuitesEnabled = Collections.unmodifiableSet( suitesEnabled );
        this.cipherSuitesDisabled = Collections.unmodifiableSet( cipherSuitesDisabled );

        this.identityStore = CertificateStoreManager.getIdentityStore( type );
        this.trustStore = CertificateStoreManager.getTrustStore( type );

        this.Log = LoggerFactory.getLogger( this.getClass().getName() + "["+port+"-"+type+"]" );
    }

    public Connection.TLSPolicy getTlsPolicy()
    {
        return tlsPolicy;
    }

    public ConnectionType getType()
    {
        return type;
    }

    public int getMaxThreadPoolSize()
    {
        return maxThreadPoolSize;
    }

    public int getMaxBufferSize()
    {
        return maxBufferSize;
    }

    public Connection.ClientAuth getClientAuth()
    {
        return clientAuth;
    }

    public InetAddress getBindAddress()
    {
        return bindAddress;
    }

    public int getPort()
    {
        return port;
    }

    public CertificateStoreConfiguration getIdentityStoreConfiguration()
    {
        return identityStoreConfiguration;
    }

    public CertificateStoreConfiguration getTrustStoreConfiguration()
    {
        return trustStoreConfiguration;
    }

    /**
     * A boolean that indicates if self-signed peer certificates can be used to establish an encrypted connection.
     *
     * @return true when self-signed certificates are accepted, otherwise false.
     */
    public boolean isAcceptSelfSignedCertificates()
    {
        return acceptSelfSignedCertificates;
    }

    /**
     * A boolean that indicates if the current validity of certificates (based on their 'notBefore' and 'notAfter'
     * property values) is used when they are used to establish an encrypted connection..
     *
     * @return true when certificates are required to be valid to establish a secured connection, otherwise false.
     */
    public boolean isVerifyCertificateValidity()
    {
        return verifyCertificateValidity;
    }

    /**
     * A collection of protocol names that can be used for encryption of connections.
     *
     * When non-empty, the list is intended to specify those protocols (from a larger collection of implementation-
     * supported protocols) that can be used to establish encryption.
     *
     * Values returned by {@link #getEncryptionProtocolsDisabled()} are not included in the result of this method.
     *
     * The order over which values are iterated in the result is equal to the order of values in the comma-separated
     * configuration string. This can, but is not guaranteed to, indicate preference.
     *
     * @return An (ordered) set of protocols, never null but possibly empty.
     */
    public Set<String> getEncryptionProtocolsEnabled()
    {
        return encryptionProtocolsEnabled;
    }

    /**
     * A collection of protocols that must not be used for encryption of connections.
     *
     * When non-empty, the list is intended to specify those protocols (from a larger collection of implementation-
     * supported protocols) that must not be used to establish encryption.
     *
     * The order over which values are iterated in the result is equal to the order of values in the comma-separated
     * configuration string.
     *
     * @return An (ordered) set of protocols, never null but possibly empty.
     */
    public Set<String> getEncryptionProtocolsDisabled()
    {
        return encryptionProtocolsDisabled;
    }

    /**
     * A collection of cipher suite names that can be used for encryption of connections.
     *
     * When non-empty, the list is intended to specify those cipher suites (from a larger collection of implementation-
     * supported cipher suties) that can be used to establish encryption.
     *
     * Values returned by {@link #getCipherSuitesDisabled()} are not included in the result of this method.
     *
     * The order over which values are iterated in the result is equal to the order of values in the comma-separated
     * configuration string. This can, but is not guaranteed to, indicate preference.
     *
     * @return An (ordered) set of cipher suites, never null but possibly empty.
     */
    public Set<String> getCipherSuitesEnabled()
    {
        return cipherSuitesEnabled;
    }

    /**
     * A collection of cipher suites that must not be used for encryption of connections.
     *
     * When non-empty, the list is intended to specify those cipher suites (from a larger collection of implementation-
     * supported cipher suites) that must not be used to establish encryption.
     *
     * The order over which values are iterated in the result is equal to the order of values in the comma-separated
     * configuration string.
     *
     * @return An (ordered) set of cipher suites, never null but possibly empty.
     */
    public Set<String> getCipherSuitesDisabled()
    {
        return cipherSuitesDisabled;
    }

    public IdentityStore getIdentityStore()
    {
        return identityStore;
    }

    public TrustStore getTrustStore()
    {
        return trustStore;
    }

    public boolean isEnabled()
    {
        return enabled;
    }
}
