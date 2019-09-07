package org.jivesoftware.openfire.spi;

import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.ServerPort;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.keystore.CertificateStoreConfiguration;
import org.jivesoftware.openfire.keystore.IdentityStore;
import org.jivesoftware.openfire.net.SocketConnection;
import org.jivesoftware.util.CertificateManager;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * As a server, Openfire accepts connection requests from other network entities. The exact functionality is subject to
 * configuration details (eg: TCP port on which connections are accepted, TLS policy that is applied, etc). An instance
 * of this class is used to manage this configuration for one type of connection (on one TCP port), and is responsible
 * for managing the lifecycle of the entity that implements the acceptance of new connections.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
// TODO most getters in this class assume that the ConnectionAcceptor property value match the property values of JiveGlobals. This should be the case, but should be asserted.
public class ConnectionListener
{
    private Logger Log;

    // Connection characteristics
    private final ConnectionType type;
    private final int defaultPort;
    private final InetAddress bindAddress; // if null, represents any local address (typically 0.0.0.0 or ::0)
    private CertificateStoreConfiguration identityStoreConfiguration;
    private CertificateStoreConfiguration trustStoreConfiguration;

    // Name of properties used to configure the acceptor.
    private final String tcpPortPropertyName;

    /**
     * Name of property that toggles availability. 'null' indicates that the listener should always be enabled (and
     * cannot be turned on/off).
     */
    private final String isEnabledPropertyName;

    /**
     * Name of property that configures the maximum threads that can currently be processing IO related to this
     * listener. 'null' indicates that a default number should be used.
     */
    private final String maxPoolSizePropertyName; // Max threads

    /**
     * Name of property that configures the maximum amount (in bytes) of IO data can be cached, pending processing.
     * 'nll' indicates that the cache size is unbounded. Unbounded caches should be used for high-volume and/or trusted
     * connections only (if at all).
     */
    private final String maxReadBufferPropertyName; // Max buffer size

    /**
     * Name of property that configures the TLS policy that's applicable to this listener. Instead of a property name,
     * the name of a {@link org.jivesoftware.openfire.Connection.TLSPolicy} can be used to indicate that this listener
     * is 'hard-coded' in this state (configuration changes will cause exceptions to be thrown).
     */
    private final String tlsPolicyPropertyName;

    /**
     * Name of property that configures the policy regarding compression (eg: ZLIB) that's applicable to this listener.
     * 'null' causes an implementation default to be used.
     */
    private final String compressionPolicyPropertyName;

    /**
     * Name of property that configures the policy regarding mutual authentication that's applicable to this listener.
     * 'null' indicates that this policy cannot be configured and 'disabled' should be used as a default.
     */
    private final String clientAuthPolicyPropertyName;

    // The entity that performs the acceptance of new (socket) connections.
    private ConnectionAcceptor connectionAcceptor;


    ConnectionListener getConnectionListener( ConnectionType type ) {
        ConnectionManagerImpl connectionManager = ((ConnectionManagerImpl) XMPPServer.getInstance().getConnectionManager());
        return connectionManager.getListener( type, getTLSPolicy().equals( Connection.TLSPolicy.legacyMode ) );
    }

    /**
     * Instantiates a new connection listener.
     *
     * @param type the connection type
     * @param tcpPortPropertyName the name of the system property holding the TCP port
     * @param defaultPort the default port number if the property is not set
     * @param isEnabledPropertyName Property name (of a boolean) that toggles availability. Null to indicate that this listener is 'always on'
     * @param maxPoolSizePropertyName Property name (of an int) that defines maximum IO processing threads. Null causes an unconfigurable default amount to be used.
     * @param maxReadBufferPropertyName Property name (of an int) that defines maximum amount (in bytes) of IO data can be cached, pending processing. Null to indicate boundless caches.
     * @param tlsPolicyPropertyName Property name (of a string) that defines the applicable TLS Policy. Or, the value {@link org.jivesoftware.openfire.Connection.TLSPolicy} to indicate unconfigurable TLS Policy. Cannot be null.
     * @param clientAuthPolicyPropertyName Property name (of an string) that defines maximum IO processing threads. Null causes a unconfigurabel value of 'wanted' to be used.
     * @param bindAddress the address to bind to
     * @param identityStoreConfiguration the certificates the server identify as
     * @param trustStoreConfiguration the certificates the server trusts
     * @param compressionPolicyPropertyName the name of the system property indicating if compression is enabled or not
     */
    public ConnectionListener( ConnectionType type, String tcpPortPropertyName, int defaultPort, String isEnabledPropertyName, String maxPoolSizePropertyName, String maxReadBufferPropertyName, String tlsPolicyPropertyName, String clientAuthPolicyPropertyName, InetAddress bindAddress, CertificateStoreConfiguration identityStoreConfiguration, CertificateStoreConfiguration trustStoreConfiguration, String compressionPolicyPropertyName )
    {
        this.type = type;
        this.tcpPortPropertyName = tcpPortPropertyName;
        this.defaultPort = defaultPort;
        this.isEnabledPropertyName = isEnabledPropertyName;
        this.maxPoolSizePropertyName = maxPoolSizePropertyName;
        this.maxReadBufferPropertyName = maxReadBufferPropertyName;
        this.tlsPolicyPropertyName = tlsPolicyPropertyName;
        this.clientAuthPolicyPropertyName = clientAuthPolicyPropertyName;
        this.bindAddress = bindAddress;
        this.identityStoreConfiguration = identityStoreConfiguration;
        this.trustStoreConfiguration = trustStoreConfiguration;
        this.compressionPolicyPropertyName = compressionPolicyPropertyName;

        // A listener cannot be changed into or from legacy mode. That fact is safe to use in the name of the logger..
        final String name = getType().toString().toLowerCase() + ( getTLSPolicy().equals( Connection.TLSPolicy.legacyMode ) ? "-legacyMode" : "" );
        this.Log = LoggerFactory.getLogger( ConnectionListener.class.getName() + "[" + name + "]" );
    }

    /**
     * Return if the configuration allows this listener to be enabled (but does not verify that the listener is
     * indeed active).
     *
     * @return true if configuration allows this listener to be enabled, otherwise false.
     */
    public boolean isEnabled()
    {
        // Not providing a property name indicates that availability cannot be toggled. The listener is 'always on'.
        if (isEnabledPropertyName == null )
        {
            return true;
        }
        // TODO if this is an SSL connection, legacy code required the existence of at least one certificate in the identity store in addition to the property value (although no such requirement is enforced for a TLS connection that might or might not be elevated to encrypted).
        return JiveGlobals.getBooleanProperty( isEnabledPropertyName, true );
    }

    /**
     * Activates or deactivates the listener, and changes the configuration accordingly. This configuration change is
     * persisted. An invocation of this method has no effect if the listener is already in the provided state.
     * @param enable to enable or disable the listener
     */
    public synchronized void enable( boolean enable )
    {
        // Not providing a property name indicates that availability cannot be toggled. The listener is 'always on'.
        if ( isEnabledPropertyName == null && !enable )
        {
            throw new IllegalArgumentException( "This listener cannot be disabled!" );
        }

        final boolean isRunning = connectionAcceptor != null;
        if ( enable == isRunning )
        {
            // This is likely to be caused by a cadence of property changes and harmless / safe to ignore.
            Log.debug( "Ignoring enable({}): listener already in this state.", enable );
            return;
        }

            JiveGlobals.setProperty( isEnabledPropertyName, Boolean.toString( enable ) );
        restart();
    }

    /**
     * Attempts to start the connection acceptor, creating a new instance when needed.
     *
     * An invocation of this method does not change the configuration for this connection. As a result, an acceptor will
     * <em>not</em> be started when the listener is not enabled (in such cases, an invocation of this method has no
     * effect).
     *
     * In order to start this listener and persist this as the desired state for this connection, use #enable(true).
     *
     * This method should not be called when an acceptor has already been started (instead, {@link #restart()} should be
     * used to explicitly define the need to stop a previous connection). The current implementation of this method will
     * stop a pre-existing acceptor, but only when it is currently not serving connections. When the acceptor is not
     * idle, this method has no effect. This behavior might change in the future.
     */
    public synchronized void start()
    {
        // TODO Start all connection types here, by supplying more connection acceptors other than a MINA-based one.
        switch ( getType() )
        {
            case BOSH_C2S:
            case WEBADMIN:
                Log.debug( "Not starting a (MINA-based) connection acceptor, as connections of type " + getType() + " depend on another IO technology.");
                return;

            default:
        }

        if ( !isEnabled() )
        {
            Log.debug( "Not starting: disabled by configuration." );
            return;
        }

        if ( connectionAcceptor != null )
        {
            // This might indicate an illegal state. Legacy code allows for this, so we won't throw a runtime exception (for now).
            if ( !connectionAcceptor.isIdle() )
            {
                Log.warn( "Unable to start: it appears to have already been started (and it is currently serving connections)! To restart, first stop this listener explicitly." );
                return;
            }
            else
            {
                Log.warn( "Stopping (in order to restart) an instance that has already been started, but is idle. This start would have failed if the listener was not idle. The implementation should have called stop() or restart() first, to ensure a clean restart!" );
                connectionAcceptor.stop();
            }
        }

        Log.debug( "Starting..." );
        if ( getType() == ConnectionType.SOCKET_S2S )
        {
            connectionAcceptor = new LegacyConnectionAcceptor( generateConnectionConfiguration() );
        }
        else
        {
            connectionAcceptor = new MINAConnectionAcceptor( generateConnectionConfiguration() );
        }

        connectionAcceptor.start();
        Log.info( "Started." );
    }

    /**
     * Generates an immutable ConnectionConfiguration based on the current state.
     *
     * @return an immutable configuration, never null.
     */
    public ConnectionConfiguration generateConnectionConfiguration()
    {
        final int defaultMaxPoolSize = 16;
        final int maxThreadPoolSize;
        if ( maxPoolSizePropertyName == null )
        {
            maxThreadPoolSize = defaultMaxPoolSize;
        }
        else
        {
            maxThreadPoolSize = JiveGlobals.getIntProperty( maxPoolSizePropertyName, defaultMaxPoolSize );
        }

        final int maxBufferSize;
        if ( maxReadBufferPropertyName != null )
        {
            maxBufferSize = JiveGlobals.getIntProperty( maxReadBufferPropertyName, 10 * 1024 * 1024 );
        }
        else
        {
            maxBufferSize = -1; // No upper bound.
        }

        // Take the current state of this instance, and create a new configuration.
        return new ConnectionConfiguration(
                getType(),
                isEnabled(),
                maxThreadPoolSize,
                maxBufferSize,
                getClientAuth(),
                getBindAddress(),
                getPort(),
                getTLSPolicy(),
                identityStoreConfiguration,
                trustStoreConfiguration,
                acceptSelfSignedCertificates(),
                verifyCertificateValidity(),
                getEncryptionProtocols(),
                getEncryptionCipherSuites(),
                getCompressionPolicy()
        );
    }

    /**
     * Attempts to stop the connection acceptor. If the connection acceptor has not been started, an invocation of this
     * method has no effect.
     *
     * An invocation of this method does not change the configuration for this connection. As a result, the acceptor for
     * this connection can be restarted when this ConnectionListener instance is replaced.
     *
     * In order to stop this listener (and persist this as the desired state for this connection, use #enable(false).
     */
    protected synchronized void stop()
    {
        if ( connectionAcceptor == null )
        {
            Log.debug( "Not stopping: it hasn't been started." );
            return;
        }

        Log.debug( "Stopping..." );
        try
        {
            connectionAcceptor.stop();
        }
        finally
        {
            connectionAcceptor = null;
        }
        Log.info( "Stopped." );
    }

    /**
     * Starts or restarts this instance (typically used to put into effect a configuration change).
     *
     * A connection that was started, but is disabled by configuration will be stopped but not restarted by an
     * invocation of this method.
     */
    public synchronized void restart()
    {
        Log.debug( "Restarting..." );
        try
        {
            if ( connectionAcceptor != null )
            {
                stop();
            }
        }
        finally
        {
            start(); // won't actually start anything if not enabled.
        }
        Log.info( "Done restarting..." );
    }

    /**
     * Reconfigures the acceptor without breaking existing connections. Note that not all configuration changes
     * can be applied. These changes will be applied after a restart.
     */
    public synchronized void reloadConfiguration()
    {
        if ( connectionAcceptor == null )
        {
            return; // There's no point in reloading config of a stopped instance. Config will be reloaded when started.
        }

        Log.debug( "Reconfiguring..." );
        connectionAcceptor.reconfigure( generateConnectionConfiguration() );
        Log.info( "Reconfigured." );
    }

    /**
     * Returns the MINA-specific socket acceptor that is managed by the instance.
     *
     * @return A socket acceptor, or null when this listener is disabled or not based on a MINA implementation.
     */
    // TODO see if we can avoid exposing MINA internals.
    public NioSocketAcceptor getSocketAcceptor()
    {
        if ( connectionAcceptor == null || !(connectionAcceptor instanceof MINAConnectionAcceptor) )
        {
            return null;
        }

        return ((MINAConnectionAcceptor)connectionAcceptor).getSocketAcceptor();
    }

    /**
     * Returns the network address on which connections are accepted when this listener is enabled.
     *
     * This method can return null, which indicates that connections are accepted on any local address (typically
     * 0.0.0.0 or ::0).
     *
     * @return A network address or null.
     */
    public InetAddress getBindAddress()
    {
        return bindAddress;
    }

    /**
     * Returns the type of connection that is accepted by this listener.
     *
     * @return A connection type (never null).
     */
    public ConnectionType getType()
    {
        return type;
    }


    /**
     * The TCP port number on which connections will be accepted when this listener is enabled.
     *
     * @return A port number.
     */
    public int getPort()
    {
        if ( tcpPortPropertyName != null )
        {
            return JiveGlobals.getIntProperty( tcpPortPropertyName, defaultPort );
        }
        else
        {
            return defaultPort;
        }
    }

    /**
     * Changes the TCP port on which connections are accepted, This configuration change is persisted.
     *
     * If the listener is currently enabled, this configuration change will be applied immediately (which will cause a
     * restart of the underlying connection acceptor).
     *
     * An invocation of this method has no effect if the new port value is equal to the existing value.
     *
     * @param port A port number.
     */
    public void setPort( int port )
    {
        final long oldPort = getPort();
        if (port == oldPort ) {
            Log.debug( "Ignoring port change request (to '{}'): listener already in this state.", port );
            return;
        }

        Log.debug( "Changing port from '{}' to '{}'.", oldPort, port );
        if ( tcpPortPropertyName != null )
        {
            JiveGlobals.setProperty( tcpPortPropertyName, String.valueOf( port ) );
        }
        restart();
    }

    public Connection.ClientAuth getClientAuth()
    {
        Connection.ClientAuth clientAuth;
        if ( clientAuthPolicyPropertyName == null )
        {
            clientAuth = getDefaultClientAuth();
        }
        else
        {
            final String value = JiveGlobals.getProperty( clientAuthPolicyPropertyName, getDefaultClientAuth().name() );
            try
            {
                clientAuth = Connection.ClientAuth.valueOf( value );
            }
            catch ( IllegalArgumentException e )
            {
                Log.error( "Error parsing property value of '{}' into a valid ClientAuth. Offending value: '{}'.", value, clientAuthPolicyPropertyName, e );
                clientAuth = getDefaultClientAuth();
            }
        }
        return clientAuth;
    }

    protected Connection.ClientAuth getDefaultClientAuth()
    {
        try
        {
            final IdentityStore identityStore = XMPPServer.getInstance().getCertificateStoreManager().getIdentityStore( getType() );
            final boolean hasSignedCert = identityStore.getAllCertificates()
                .values()
                .stream()
                .anyMatch( certificate -> !CertificateManager.isSelfSignedCertificate( certificate ) && !CertificateManager.isSigningRequestPending( certificate ) );

            if ( hasSignedCert && Arrays.asList( ConnectionType.SOCKET_S2S ).contains( getType() ) ) {
                return Connection.ClientAuth.wanted;
            } else {
                return Connection.ClientAuth.disabled;
            }
        } catch ( Exception e ) {
            Log.info( "An unexpected exception occurred while calculating the default client auth setting for connection type {}.", getType(), e );
            return Connection.ClientAuth.disabled;
        }
    }

    public void setClientAuth( Connection.ClientAuth clientAuth )
    {
        final Connection.ClientAuth oldValue = getClientAuth();
        if ( oldValue.equals( clientAuth ) )
        {
            Log.debug( "Ignoring client auth configuration change request (to '{}'): listener already in this state.", clientAuth );
            return;
        }
        Log.debug( "Changing client auth configuration from '{}' to '{}'.", oldValue, clientAuth );
        JiveGlobals.setProperty( clientAuthPolicyPropertyName, clientAuth.toString() );
        restart();
    }

    /**
     * Returns the applicable TLS policy, but only when it is hardcoded (and inconfigurable).
     * @return a policy or null.
     */
    private Connection.TLSPolicy getHardcodedTLSPolicy()
    {
        try
        {
            return Connection.TLSPolicy.valueOf( tlsPolicyPropertyName );
        } catch ( IllegalArgumentException ex ) {
            // Not hardcoded!
            return null;
        }
    }

    /**
     * Returns whether TLS is mandatory, optional, disabled or mandatory immediately for new connections. When TLS is
     * mandatory connections are required to be encrypted or otherwise will be closed.
     *
     * When TLS is disabled connections are not allowed to be (or become) encrypted. In this case, connections will be
     * closed when encryption is attempted.
     *
     * @return An encryption policy, never null.
     */
    public Connection.TLSPolicy getTLSPolicy()
    {

        final Connection.TLSPolicy hardcoded = getHardcodedTLSPolicy();
        if ( hardcoded != null )
        {
            return hardcoded;
        }
        else
        {
            final String policyName = JiveGlobals.getProperty( tlsPolicyPropertyName, Connection.TLSPolicy.optional.toString() );
            Connection.TLSPolicy tlsPolicy;
            try
            {
                tlsPolicy = Connection.TLSPolicy.valueOf(policyName);
            }
            catch ( IllegalArgumentException e )
            {
                Log.error( "Error parsing property value of '{}' into a valid TLS_POLICY. Offending value: '{}'.", policyName, tlsPolicyPropertyName, e );
                tlsPolicy = Connection.TLSPolicy.optional;
            }
            return tlsPolicy;
        }
    }

    /**
     * Sets whether TLS is mandatory, optional, disabled or mandatory immediately for new connections. When TLS is
     * mandatory connections are required to be encrypted or otherwise will be closed. This configuration change is
     * persisted.
     *
     * If the listener is currently enabled, this configuration change will be applied immediately (which will cause a
     * restart of the underlying connection acceptor).
     *
     * When TLS is disabled connections are not allowed to be (or become) encrypted. In this case, connections will be
     * closed when encryption is attempted.
     *
     * This method disallows changing the policy from or into legacy mode. Such a change is logged but otherwise
     * ignored.
     *
     * An invocation of this method has no effect if the new policy value is equal to the existing value.
     *
     * @param policy an encryption policy (not null).
     */
    public void setTLSPolicy( SocketConnection.TLSPolicy policy )
    {
        final Connection.TLSPolicy oldPolicy = getTLSPolicy();
        if ( oldPolicy.equals( policy ) )
        {
            Log.debug( "Ignoring TLS Policy change request (to '{}'): listener already in this state.", policy );
            return;
        }

        final Connection.TLSPolicy hardcoded = getHardcodedTLSPolicy();
        if ( hardcoded != null )
        {
            throw new IllegalArgumentException( "The TLS Policy for this listener is hardcoded (to '"+hardcoded+"'). It cannot be changed." );
        }

        if ( Connection.TLSPolicy.legacyMode.equals( policy ) )
        {
            Log.warn( "Ignoring TLS Policy change request (to '{}'): You cannot reconfigure an existing connection (from '{}') into legacy mode!", policy, oldPolicy );
            return;
        }

        if ( Connection.TLSPolicy.legacyMode.equals( oldPolicy ) )
        {
            Log.warn( "Ignoring TLS Policy change request (to '{}'): You cannot reconfigure an existing connection that is in legacy mode!", policy );
            return;
        }

        Log.debug( "Changing TLS Policy from '{}' to '{}'.", oldPolicy, policy );
        JiveGlobals.setProperty( tlsPolicyPropertyName, policy.toString() );
        restart();
    }

    /**
     * Returns whether compression is optional or disabled for new connections.
     *
     * @return A compression policy (never null)
     */
    public Connection.CompressionPolicy getCompressionPolicy()
    {
        // Depending on the connection type, define a good default value.
        final Connection.CompressionPolicy defaultPolicy;
        switch ( getType() )
        {
            // More likely to have good bandwidth. Compression on high-volume data gobbles CPU.
            case COMPONENT:
            case CONNECTION_MANAGER:
            case SOCKET_S2S:
                defaultPolicy = Connection.CompressionPolicy.disabled;
                break;

            // At least *offer* compression functionality.
            case SOCKET_C2S:
            case BOSH_C2S:
            case WEBADMIN:
            default:
                defaultPolicy = Connection.CompressionPolicy.optional;
                break;
        }

        if ( compressionPolicyPropertyName == null )
        {
            return defaultPolicy;
        }
        else
        {
            final String policyName = JiveGlobals.getProperty( compressionPolicyPropertyName, defaultPolicy.toString() );
            try
            {
                return Connection.CompressionPolicy.valueOf( policyName );
            }
            catch ( IllegalArgumentException e )
            {
                Log.error( "Error parsing property value of '{}' into a valid Compression Policy. Offending value: '{}'.", tlsPolicyPropertyName, policyName, e );
                return defaultPolicy;
            }
        }
    }

    /**
     * Sets whether compression is optional or disabled for new connections. This configuration change is persisted.
     *
     * If the listener is currently enabled, this configuration change will be applied immediately (which will cause a
     * restart of the underlying connection acceptor).
     *
     * An invocation of this method has no effect if the new policy value is equal to the existing value.
     *
     * @param policy a compression policy (not null).
     */
    public void setCompressionPolicy( Connection.CompressionPolicy policy )
    {
        final Connection.CompressionPolicy oldPolicy = getCompressionPolicy();
        if ( oldPolicy.equals( policy ) )
        {
            Log.debug( "Ignoring Compression Policy change request (to '{}'): listener already in this state.", policy );
            return;
        }

        Log.debug( "Changing Compression Policy from '{}' to '{}'.", oldPolicy, policy );
        JiveGlobals.setProperty( compressionPolicyPropertyName, policy.toString() );
        restart();
    }

    /**
     * Returns the configuration for the identity store that identifies this instance of Openfire to the peer
     * on connections created by this listener.
     *
     * @return The configuration of the identity store (not null)
     */
    public CertificateStoreConfiguration getIdentityStoreConfiguration() {
        return this.identityStoreConfiguration;
    }

    /**
     * Replaces the configuration for the identity store that identifies this instance of Openfire to the peer
     * on connections created by this listener.
     *
     * If the listener is currently enabled, this configuration change will be applied immediately (which will cause a
     * restart of the underlying connection acceptor).
     *
     * @param configuration The identity store configuration (not null)
     */
    public void setIdentityStoreConfiguration( CertificateStoreConfiguration configuration )
    {
        if ( this.identityStoreConfiguration.equals( configuration ) )
        {
            Log.debug( "Ignoring identity store configuration change request (to '{}'): listener already in this state.", configuration );
            return;
        }
        Log.debug( "Changing identity store configuration  from '{}' to '{}'.", this.identityStoreConfiguration, configuration );
        this.identityStoreConfiguration = configuration;
        restart();
    }

    /**
     * Returns the configuration for the trust store that is used to identify/trust peers on connections created by this
     * listener.
     *
     * @return The configuration of the identity store (not null)
     */
    public CertificateStoreConfiguration getTrustStoreConfiguration() {
        return this.trustStoreConfiguration;
    }

    /**
     * Replaces the configuration for the trust store that is used to identify/trust peers on connections created by
     * this listener.
     *
     * If the listener is currently enabled, this configuration change will be applied immediately (which will cause a
     * restart of the underlying connection acceptor).
     *
     * @param configuration The configuration of the identity store (not null)
     */
    public void setTrustStoreConfiguration( CertificateStoreConfiguration configuration )
    {
        if ( this.trustStoreConfiguration.equals( configuration ) )
        {
            Log.debug( "Ignoring trust store configuration change request (to '{}'): listener already in this state.", configuration );
            return;
        }
        Log.debug( "Changing trust store configuration  from '{}' to '{}'.", this.trustStoreConfiguration, configuration );
        this.trustStoreConfiguration = configuration;
        restart();
    }

    /**
     * A boolean that indicates if self-signed peer certificates can be used to establish an encrypted connection.
     *
     * @return true when self-signed certificates are accepted, otherwise false.
     */
    public boolean acceptSelfSignedCertificates()
    {
        // TODO these are new properties! Deprecate (migrate?) all existing 'accept-selfsigned properties' (Eg: org.jivesoftware.openfire.session.ConnectionSettings.Server.TLS_ACCEPT_SELFSIGNED_CERTS )
        final String propertyName = type.getPrefix() + "certificate.accept-selfsigned";
        final boolean defaultValue = false;

        if ( type.getFallback() == null )
        {
            return JiveGlobals.getBooleanProperty( propertyName, defaultValue );
        }
        else
        {
            return JiveGlobals.getBooleanProperty( propertyName, getConnectionListener( type.getFallback() ).acceptSelfSignedCertificates() );
        }
    }

    /**
     * Configuresif self-signed peer certificates can be used to establish an encrypted connection.
     *
     * @param accept true when self-signed certificates are accepted, otherwise false.
     */
    public void setAcceptSelfSignedCertificates( boolean accept )
    {
        final boolean oldValue = verifyCertificateValidity();

        // Always set the property explicitly even if it appears the equal to the old value (the old value might be a fallback value).
        JiveGlobals.setProperty( type.getPrefix() + "certificate.accept-selfsigned", Boolean.toString( accept ) );

        if ( oldValue == accept )
        {
            Log.debug( "Ignoring self-signed certificate acceptance policy change request (to '{}'): listener already in this state.", accept );
            return;
        }

        Log.debug( "Changing self-signed certificate acceptance policy from '{}' to '{}'.", oldValue, accept );
        restart();
    }

    /**
     * A boolean that indicates if the current validity of certificates (based on their 'notBefore' and 'notAfter'
     * property values) is used when they are used to establish an encrypted connection..
     *
     * @return true when certificates are required to be valid to establish a secured connection, otherwise false.
     */
    public boolean verifyCertificateValidity()
    {
        // TODO these are new properties! Deprecate (migrate?) all existing 'verify / verify-validity properties' (Eg: org.jivesoftware.openfire.session.ConnectionSettings.Server.TLS_CERTIFICATE_VERIFY_VALIDITY )
        final String propertyName = type.getPrefix() + "certificate.verify.validity";
        final boolean defaultValue = true;

        if ( type.getFallback() == null )
        {
            return JiveGlobals.getBooleanProperty( propertyName, defaultValue );
        }
        else
        {
            return JiveGlobals.getBooleanProperty( propertyName, getConnectionListener( type.getFallback() ).acceptSelfSignedCertificates() );
        }
    }

    /**
     * Configures if the current validity of certificates (based on their 'notBefore' and 'notAfter' property values) is
     * used when they are used to establish an encrypted connection..
     *
     * @param verify true when certificates are required to be valid to establish a secured connection, otherwise false.
     */
    public void setVerifyCertificateValidity( boolean verify )
    {
        final boolean oldValue = verifyCertificateValidity();

        // Always set the property explicitly even if it appears the equal to the old value (the old value might be a fallback value).
        JiveGlobals.setProperty( type.getPrefix() + "certificate.verify.validity", Boolean.toString( verify ) );

        if ( oldValue == verify )
        {
            Log.debug( "Ignoring certificate validity verification configuration change request (to '{}'): listener already in this state.", verify );
            return;
        }

        Log.debug( "Changing certificate validity verification configuration from '{}' to '{}'.", oldValue, verify );
        restart();
    }

    /**
     * A collection of protocol names that can be used for encryption of connections.
     *
     * When non-empty, the list is intended to specify those protocols (from a larger collection of implementation-
     * supported protocols) that can be used to establish encryption.
     *
     * The order over which values are iterated in the result is equal to the order of values in the comma-separated
     * configuration string. This can, but is not guaranteed to, indicate preference.
     *
     * @return An (ordered) set of protocols, never null but possibly empty.
     */
    public Set<String> getEncryptionProtocols()
    {
        final Set<String> result = new LinkedHashSet<>();
        final String csv = getEncryptionProtocolsCommaSeparated();
        if ( csv.isEmpty() ) {
            try {
                result.addAll( EncryptionArtifactFactory.getDefaultProtocols() );
            } catch ( Exception ex ) {
                Log.error( "An error occurred while obtaining the default encryption protocol setting.", ex );
            }
        } else {
            result.addAll( Arrays.asList( csv.split( "\\s*,\\s*" ) ) );
        }

        // OF-1118: Do not return protocols that are not supported by the implementation.
        try {
            result.retainAll( EncryptionArtifactFactory.getSupportedProtocols() );
        } catch ( Exception ex ) {
            Log.error( "An error occurred while obtaining the supported encryption protocols.", ex );
        }

        return result;
    }

    protected String getEncryptionProtocolsCommaSeparated()
    {
        final String propertyName = type.getPrefix() + "protocols";
        final String defaultValue = "";

        if ( type.getFallback() == null )
        {
            return JiveGlobals.getProperty( propertyName, defaultValue ).trim();
        }
        else
        {
            return JiveGlobals.getProperty( propertyName, getConnectionListener( type.getFallback() ).getEncryptionProtocolsCommaSeparated() ).trim();
        }
    }

    /**
     * Defines the collection of protocols (by name) that can be used for encryption of connections.
     *
     * When non-empty, the list is intended to specify those protocols (from a larger collection of implementation-
     * supported protocols) that can be used to establish encryption. An empty list will cause an implementation
     * default to be used.
     *
     * The order over which values are presented can, but is not guaranteed to, indicate preference.
     *
     * @param protocols An (ordered) set of protocol names, can be null.
     */
    public void setEncryptionProtocols( Set<String> protocols ) {
        if ( protocols == null ) {
            setEncryptionProtocols( new String[0] );
        } else {
            setEncryptionProtocols( protocols.toArray( new String[ protocols.size() ] ) );
        }
    }

    /**
     * Defines the collection of protocols (by name) that can be used for encryption of connections.
     *
     * When non-empty, the list is intended to specify those protocols (from a larger collection of implementation-
     * supported protocols) that can be used to establish encryption. An empty list will cause an implementation
     * default to be used.
     *
     * The order over which values are presented can, but is not guaranteed to, indicate preference.
     *
     * @param protocols An array of protocol names, can be null.
     */
    public void setEncryptionProtocols( String[] protocols )
    {
        if ( protocols == null) {
            protocols = new String[0];
        }
        final String oldValue = getEncryptionProtocolsCommaSeparated();

        // Always set the property explicitly even if it appears the equal to the old value (the old value might be a fallback value).
        final StringBuilder csv = new StringBuilder();
        for( String protocol : protocols )
        {
            csv.append( protocol );
            csv.append( ',' );
        }
        final String newValue = csv.length() > 0 ? csv.substring( 0, csv.length() - 1 ) : "";
        JiveGlobals.setProperty( type.getPrefix() + "protocols", newValue );

        if ( oldValue.equals( newValue ) )
        {
            Log.debug( "Ignoring protocol configuration change request (to '{}'): listener already in this state.", newValue );
            return;
        }

        Log.debug( "Changing protocol configuration from '{}' to '{}'.", oldValue, newValue );
        restart();
    }

    /**
     * A collection of cipher suite names that can be used for encryption of connections.
     *
     * When non-empty, the list is intended to specify those cipher suites (from a larger collection of implementation-
     * supported cipher suites) that can be used to establish encryption.
     *
     * The order over which values are iterated in the result is equal to the order of values in the comma-separated
     * configuration string. This can, but is not guaranteed to, indicate preference.
     *
     * @return An (ordered) set of cipher suite names, never null but possibly empty.
     */
    public Set<String> getEncryptionCipherSuites()
    {
        final Set<String> result = new LinkedHashSet<>();
        final String csv = getEncryptionCipherSuitesCommaSeparated();
        if ( csv.isEmpty() ) {
            try {
                result.addAll( EncryptionArtifactFactory.getDefaultCipherSuites() );
            } catch ( Exception ex ) {
                Log.error( "An error occurred while obtaining the default encryption cipher suite setting.", ex );
            }
        } else {
            result.addAll( Arrays.asList( csv.split( "\\s*,\\s*" ) ) );
        }

        // OF-1118: Do not return cipher suites that are not supported by the implementation.
        try {
            result.retainAll( EncryptionArtifactFactory.getSupportedCipherSuites() );
        } catch ( Exception ex ) {
            Log.warn( "An error occurred while obtaining the supported encryption cipher suites.", ex );
        }

        return result;
    }

    protected String getEncryptionCipherSuitesCommaSeparated()
    {
        final String propertyName = type.getPrefix() + "ciphersuites";
        final String defaultValue = "";

        if ( type.getFallback() == null )
        {
            return JiveGlobals.getProperty( propertyName, defaultValue );
        }
        else
        {
            return JiveGlobals.getProperty( propertyName, getConnectionListener( type.getFallback() ).getEncryptionCipherSuitesCommaSeparated() );
        }
    }

    /**
     * Defines the collection of cipher suite (by name) that can be used for encryption of connections.
     *
     * When non-empty, the list is intended to specify those cipher suites (from a larger collection of implementation-
     * supported cipher suites) that can be used to establish encryption. An empty list will cause an implementation
     * default to be used.
     *
     * The order over which values are presented can, but is not guaranteed to, indicate preference.
     *
     * @param cipherSuites An (ordered) set of cipher suite names, can be null.
     */
    public void setEncryptionCipherSuites( Set<String> cipherSuites ) {
        if ( cipherSuites == null ) {
            setEncryptionCipherSuites( new String[0] );
        } else {
            setEncryptionCipherSuites( cipherSuites.toArray( new String[ cipherSuites.size() ] ) );
        }
    }

    /**
     * Defines the collection of cipher suite (by name) that can be used for encryption of connections.
     *
     * When non-empty, the list is intended to specify those cipher suites (from a larger collection of implementation-
     * supported cipher suites) that can be used to establish encryption. An empty list will cause an implementation
     * default to be used.
     *
     * The order over which values are presented can, but is not guaranteed to, indicate preference.
     *
     * @param cipherSuites An array of cipher suite names, can be null.
     */
    public void setEncryptionCipherSuites( String[] cipherSuites )
    {
        if ( cipherSuites == null) {
            cipherSuites = new String[0];
        }
        final String oldValue = getEncryptionCipherSuitesCommaSeparated();

        // Always set the property explicitly even if it appears the equal to the old value (the old value might be a fallback value).
        final StringBuilder csv = new StringBuilder();
        for( String cipherSuite : cipherSuites )
        {
            csv.append( cipherSuite );
            csv.append( ',' );
        }
        final String newValue = csv.length() > 0 ? csv.substring( 0, csv.length() - 1 ) : "";
        JiveGlobals.setProperty( type.getPrefix() + "ciphersuites", newValue );

        if ( oldValue.equals( newValue ) )
        {
            Log.debug( "Ignoring cipher suite configuration change request (to '{}'): listener already in this state.", newValue );
            return;
        }

        Log.debug( "Changing cipher suite configuration from '{}' to '{}'.", oldValue, newValue );
        restart();
    }

    /**
     * Constructs and returns a ServerPort instance that reflects the state of this listener.
     *
     * @return A ServerPort instance, or null when the listener is not enabled.
     * @deprecated To obtain the state of this instance, use corresponding getters instead.
     */
    @Deprecated
    public ServerPort getServerPort()
    {
        if ( connectionAcceptor == null )
        {
            return null;
        }

        final int port = getPort();
        final String name = getBindAddress().getHostName();
        final String address = getBindAddress().getHostAddress();
        final boolean isSecure = getTLSPolicy() != Connection.TLSPolicy.disabled;
        final String algorithm = null;

        switch ( type ) {
            case SOCKET_C2S:
                return new ServerPort( port, name, address, isSecure, algorithm, ServerPort.Type.client );
            case SOCKET_S2S:
                return new ServerPort( port, name, address, isSecure, algorithm, ServerPort.Type.server );
            case COMPONENT:
                return new ServerPort( port, name, address, isSecure, algorithm, ServerPort.Type.component );
            case CONNECTION_MANAGER:
                return new ServerPort( port, name, address, isSecure, algorithm, ServerPort.Type.connectionManager );
            default:
                throw new IllegalStateException( "Unrecognized type: " + type );
        }
    }
    @Override
    public String toString()
    {
        final String name = getType().toString().toLowerCase() + ( getTLSPolicy().equals( Connection.TLSPolicy.legacyMode ) ? "-legacyMode" : "" );
        return "ConnectionListener{" +
                "name=" + name +
                '}';
    }

}
