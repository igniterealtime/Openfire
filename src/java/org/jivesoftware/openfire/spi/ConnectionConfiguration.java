package org.jivesoftware.openfire.spi;

import org.apache.mina.filter.ssl.SslFilter;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.XMPPServer;
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
    private final Connection.CompressionPolicy compressionPolicy;

    // derived
    private final IdentityStore identityStore;
    private final TrustStore trustStore;

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
    public ConnectionConfiguration( ConnectionType type, boolean enabled, int maxThreadPoolSize, int maxBufferSize, Connection.ClientAuth clientAuth, InetAddress bindAddress, int port, Connection.TLSPolicy tlsPolicy, CertificateStoreConfiguration identityStoreConfiguration, CertificateStoreConfiguration trustStoreConfiguration, boolean acceptSelfSignedCertificates, boolean verifyCertificateValidity, Set<String> encryptionProtocolsEnabled, Set<String> encryptionProtocolsDisabled, Set<String> cipherSuitesEnabled, Set<String> cipherSuitesDisabled, Connection.CompressionPolicy compressionPolicy )
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

        this.compressionPolicy = compressionPolicy;

        final CertificateStoreManager certificateStoreManager = XMPPServer.getInstance().getCertificateStoreManager();
        this.identityStore = certificateStoreManager.getIdentityStore( type );
        this.trustStore = certificateStoreManager.getTrustStore( type );
    }

    public Connection.TLSPolicy getTlsPolicy()
    {
        return tlsPolicy;
    }

    public Connection.CompressionPolicy getCompressionPolicy()
    {
        return compressionPolicy;
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
