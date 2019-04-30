/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.spi;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.buffer.SimpleBufferAllocator;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.ConnectionManager;
import org.jivesoftware.openfire.ServerPort;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.container.PluginManagerListener;
import org.jivesoftware.openfire.http.HttpBindManager;
import org.jivesoftware.openfire.keystore.CertificateStore;
import org.jivesoftware.openfire.keystore.CertificateStoreManager;
import org.jivesoftware.openfire.net.SocketSendingTracker;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.util.CertificateEventListener;
import org.jivesoftware.util.CertificateManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

public class ConnectionManagerImpl extends BasicModule implements ConnectionManager, CertificateEventListener, PropertyEventListener
{
    public static final String EXECUTOR_FILTER_NAME = "threadModel";
    public static final String TLS_FILTER_NAME = "tls";
    public static final String COMPRESSION_FILTER_NAME = "compression";
    public static final String XMPP_CODEC_FILTER_NAME = "xmpp";
    public static final String CAPACITY_FILTER_NAME = "outCap";

    private static final Logger Log = LoggerFactory.getLogger(ConnectionManagerImpl.class);

    private final ConnectionListener clientListener;
    private final ConnectionListener clientSslListener;
    private final ConnectionListener boshListener;
    private final ConnectionListener boshSslListener;
    private final ConnectionListener serverListener;
    private final ConnectionListener serverSslListener;
    private final ConnectionListener componentListener;
    private final ConnectionListener componentSslListener;
    private final ConnectionListener connectionManagerListener; // Also known as 'multiplexer'
    private final ConnectionListener connectionManagerSslListener; // Also known as 'multiplexer'
    private final ConnectionListener webAdminListener;
    private final ConnectionListener webAdminSslListener;

    /**
     * Instantiates a new connection manager.
     * @throws IOException if the identity or trust stores could not be loaded
     */
    public ConnectionManagerImpl() throws IOException
    {
        super("Connection Manager");

        InetAddress bindAddress = null;
        InetAddress adminConsoleBindAddress = null;

        try
        {
            bindAddress = getListenAddress();
        }
        catch ( UnknownHostException e )
        {
            Log.warn( "Unable to resolve bind address: ", e );
        }

        try
        {
            adminConsoleBindAddress = getAdminConsoleListenAddress();
            if( adminConsoleBindAddress == null )
            {
                adminConsoleBindAddress = bindAddress;
            }
        }
        catch( UnknownHostException e )
        {
            Log.warn(  "Unable to resolve admin console bind address: ", e );
        }

        final CertificateStoreManager certificateStoreManager = XMPPServer.getInstance().getCertificateStoreManager();

        // client-to-server
        clientListener = new ConnectionListener(
                ConnectionType.SOCKET_C2S,
                ConnectionSettings.Client.PORT,
                DEFAULT_PORT,
                ConnectionSettings.Client.SOCKET_ACTIVE,
                ConnectionSettings.Client.MAX_THREADS,
                ConnectionSettings.Client.MAX_READ_BUFFER,
                ConnectionSettings.Client.TLS_POLICY,
                ConnectionSettings.Client.AUTH_PER_CLIENTCERT_POLICY,
                bindAddress,
                certificateStoreManager.getIdentityStoreConfiguration( ConnectionType.SOCKET_C2S ),
                certificateStoreManager.getTrustStoreConfiguration( ConnectionType.SOCKET_C2S ),
                ConnectionSettings.Client.COMPRESSION_SETTINGS
        );
        clientSslListener = new ConnectionListener(
                ConnectionType.SOCKET_C2S,
                ConnectionSettings.Client.OLD_SSLPORT,
                DEFAULT_SSL_PORT,
                ConnectionSettings.Client.ENABLE_OLD_SSLPORT_PROPERTY.getKey(),
                ConnectionSettings.Client.MAX_THREADS_SSL,
                ConnectionSettings.Client.MAX_READ_BUFFER_SSL,
                Connection.TLSPolicy.legacyMode.name(), // force legacy mode
                ConnectionSettings.Client.AUTH_PER_CLIENTCERT_POLICY,
                bindAddress,
                certificateStoreManager.getIdentityStoreConfiguration( ConnectionType.SOCKET_C2S ),
                certificateStoreManager.getTrustStoreConfiguration( ConnectionType.SOCKET_C2S ),
                ConnectionSettings.Client.COMPRESSION_SETTINGS
        );
        // BOSH / HTTP-bind
        boshListener = new ConnectionListener(
                ConnectionType.BOSH_C2S,
                HttpBindManager.HTTP_BIND_PORT,
                HttpBindManager.HTTP_BIND_PORT_DEFAULT,
                HttpBindManager.HTTP_BIND_ENABLED, // TODO this one property enables/disables both normal and legacymode port. Should be separated into two.
                HttpBindManager.HTTP_BIND_THREADS,
                null,
                Connection.TLSPolicy.disabled.name(), // StartTLS over HTTP? Should use boshSslListener instead.
                HttpBindManager.HTTP_BIND_AUTH_PER_CLIENTCERT_POLICY,
                bindAddress,
                certificateStoreManager.getIdentityStoreConfiguration( ConnectionType.BOSH_C2S ),
                certificateStoreManager.getTrustStoreConfiguration( ConnectionType.BOSH_C2S ),
                ConnectionSettings.Client.COMPRESSION_SETTINGS // Existing code re-used the generic client compression property. Should we have a BOSH-specific one?
        );
        boshSslListener = new ConnectionListener(
                ConnectionType.BOSH_C2S,
                HttpBindManager.HTTP_BIND_SECURE_PORT,
                HttpBindManager.HTTP_BIND_SECURE_PORT_DEFAULT,
                HttpBindManager.HTTP_BIND_ENABLED, // TODO this one property enables/disables both normal and legacymode port. Should be separated into two.
                HttpBindManager.HTTP_BIND_THREADS,
                null,
                Connection.TLSPolicy.legacyMode.name(),
                HttpBindManager.HTTP_BIND_AUTH_PER_CLIENTCERT_POLICY,
                bindAddress,
                certificateStoreManager.getIdentityStoreConfiguration( ConnectionType.BOSH_C2S ),
                certificateStoreManager.getTrustStoreConfiguration( ConnectionType.BOSH_C2S ),
                ConnectionSettings.Client.COMPRESSION_SETTINGS // Existing code re-used the generic client compression property. Should we have a BOSH-specific one?
        );
        // server-to-server (federation)
        serverListener = new ConnectionListener(
                ConnectionType.SOCKET_S2S,
                ConnectionSettings.Server.PORT,
                DEFAULT_SERVER_PORT,
                ConnectionSettings.Server.SOCKET_ACTIVE,
                "xmpp.server.processing.threads",
                null,
                ConnectionSettings.Server.TLS_POLICY,
                ConnectionSettings.Server.AUTH_PER_CLIENTCERT_POLICY,
                bindAddress,
                certificateStoreManager.getIdentityStoreConfiguration( ConnectionType.SOCKET_S2S ),
                certificateStoreManager.getTrustStoreConfiguration( ConnectionType.SOCKET_S2S ),
                ConnectionSettings.Server.COMPRESSION_SETTINGS
        );
        serverSslListener = new ConnectionListener(
            ConnectionType.SOCKET_S2S,
            ConnectionSettings.Server.OLD_SSLPORT,
            DEFAULT_SERVER_SSL_PORT,
            ConnectionSettings.Server.ENABLE_OLD_SSLPORT,
            "xmpp.server.processing.threads",
            null,
            Connection.TLSPolicy.legacyMode.name(), // force legacy mode
            ConnectionSettings.Server.AUTH_PER_CLIENTCERT_POLICY,
            bindAddress,
            certificateStoreManager.getIdentityStoreConfiguration( ConnectionType.SOCKET_S2S ),
            certificateStoreManager.getTrustStoreConfiguration( ConnectionType.SOCKET_S2S ),
            ConnectionSettings.Server.COMPRESSION_SETTINGS
        );

        // external components (XEP 0114)
        componentListener = new ConnectionListener(
                ConnectionType.COMPONENT,
                ConnectionSettings.Component.PORT,
                DEFAULT_COMPONENT_PORT,
                ConnectionSettings.Component.SOCKET_ACTIVE,
                ConnectionSettings.Component.MAX_THREADS,
                null,
                ConnectionSettings.Component.TLS_POLICY,
                ConnectionSettings.Component.AUTH_PER_CLIENTCERT_POLICY,
                bindAddress,
                certificateStoreManager.getIdentityStoreConfiguration( ConnectionType.COMPONENT ),
                certificateStoreManager.getTrustStoreConfiguration( ConnectionType.COMPONENT ),
                ConnectionSettings.Component.COMPRESSION_SETTINGS
        );
        componentSslListener = new ConnectionListener(
                ConnectionType.COMPONENT,
                ConnectionSettings.Component.OLD_SSLPORT,
                DEFAULT_COMPONENT_SSL_PORT,
                ConnectionSettings.Component.ENABLE_OLD_SSLPORT_PROPERTY.getKey(),
                ConnectionSettings.Component.MAX_THREADS_SSL,
                null,
                Connection.TLSPolicy.legacyMode.name(), // force legacy mode
                ConnectionSettings.Component.AUTH_PER_CLIENTCERT_POLICY,
                bindAddress,
                certificateStoreManager.getIdentityStoreConfiguration( ConnectionType.COMPONENT ),
                certificateStoreManager.getTrustStoreConfiguration( ConnectionType.COMPONENT ),
                ConnectionSettings.Component.COMPRESSION_SETTINGS
        );

        // Multiplexers (our propertietary connection manager implementation)
        connectionManagerListener = new ConnectionListener(
                ConnectionType.CONNECTION_MANAGER,
                ConnectionSettings.Multiplex.PORT,
                DEFAULT_MULTIPLEX_PORT,
                ConnectionSettings.Multiplex.SOCKET_ACTIVE,
                ConnectionSettings.Multiplex.MAX_THREADS,
                null,
                ConnectionSettings.Multiplex.TLS_POLICY,
                ConnectionSettings.Multiplex.AUTH_PER_CLIENTCERT_POLICY,
                bindAddress,
                certificateStoreManager.getIdentityStoreConfiguration( ConnectionType.CONNECTION_MANAGER ),
                certificateStoreManager.getTrustStoreConfiguration( ConnectionType.CONNECTION_MANAGER ),
                ConnectionSettings.Multiplex.COMPRESSION_SETTINGS
        );
        connectionManagerSslListener = new ConnectionListener(
                ConnectionType.CONNECTION_MANAGER,
                ConnectionSettings.Multiplex.OLD_SSLPORT,
                DEFAULT_MULTIPLEX_SSL_PORT,
                ConnectionSettings.Multiplex.ENABLE_OLD_SSLPORT,
                ConnectionSettings.Multiplex.MAX_THREADS_SSL,
                null,
                Connection.TLSPolicy.legacyMode.name(), // force legacy mode
                ConnectionSettings.Multiplex.AUTH_PER_CLIENTCERT_POLICY,
                bindAddress,
                certificateStoreManager.getIdentityStoreConfiguration( ConnectionType.CONNECTION_MANAGER ),
                certificateStoreManager.getTrustStoreConfiguration( ConnectionType.CONNECTION_MANAGER ),
                ConnectionSettings.Multiplex.COMPRESSION_SETTINGS
        );

        // Admin console (the Openfire web-admin) // TODO these use the XML properties instead of normal properties!
        webAdminListener = new ConnectionListener(
                ConnectionType.WEBADMIN,
                "adminConsole.port",
                9090,
                null,
                "adminConsole.serverThreads",
                null,
                Connection.TLSPolicy.disabled.name(), // StartTLS over HTTP? Should use webAdminSslListener instead.
                null,
                adminConsoleBindAddress,
                certificateStoreManager.getIdentityStoreConfiguration( ConnectionType.WEBADMIN ),
                certificateStoreManager.getTrustStoreConfiguration( ConnectionType.WEBADMIN ),
                null // Should we have compression on the admin console?
        );

        webAdminSslListener = new ConnectionListener(
                ConnectionType.WEBADMIN,
                "adminConsole.securePort",
                9091,
                null,
                "adminConsole.serverThreads",
                null,
                Connection.TLSPolicy.legacyMode.name(),
                null,
                adminConsoleBindAddress,
                certificateStoreManager.getIdentityStoreConfiguration( ConnectionType.WEBADMIN ),
                certificateStoreManager.getTrustStoreConfiguration( ConnectionType.WEBADMIN ),
                null // Should we have compression on the admin console?
        );

    }

    /**
     * Starts all listeners. This ensures that all those that are enabled will start accept connections.
     */
    private synchronized void startListeners()
    {
        // Check if plugins have been loaded
        Log.debug( "Received a request to start listeners. Have plugins been loaded?" );
        PluginManager pluginManager = XMPPServer.getInstance().getPluginManager();
        if ( !pluginManager.isExecuted() )
        {
            Log.debug( "Plugins not yet loaded. Waiting for plugins to be loaded..." );
            pluginManager.addPluginManagerListener( new PluginManagerListener()
            {
                public void pluginsMonitored()
                {
                    Log.debug( "Received plugin monitor event! Plugins should now be loaded." );
                    // Stop listening for plugin events
                    XMPPServer.getInstance().getPluginManager().removePluginManagerListener( this );
                    // Start listeners
                    startListeners();
                }
            } );
            return;
        }

        Log.debug( "Starting listeners..." );
        for ( final ConnectionListener listener : getListeners() )
        {
            try
            {
                listener.start();
                Log.debug( "Started '{}' (port {}) listener.", listener.getType(), listener.getPort() );
            }
            catch ( RuntimeException ex )
            {
                Log.error( "An exception occurred while starting listener " + listener, ex );
            }
        }

        // Start the HTTP client listener.
        try
        {
            HttpBindManager.getInstance().start();
            Log.debug( "Started HTTP client listener." );
        }
        catch ( RuntimeException ex )
        {
            Log.error( "An exception occurred while starting HTTP Bind listener ", ex );
        }
    }

    /**
     * Stops all listeners. This ensures no listener will accept new connections.
     */
    private synchronized void stopListeners()
    {
        for ( final ConnectionListener listener : getListeners() )
        {
            // TODO determine by purpose exactly what needs and what need not be restarted.
            try
            {
                listener.stop();
            }
            catch ( RuntimeException ex )
            {
                Log.error( "An exception occurred while stopping listener " + listener, ex );
            }
        }

        // Stop the HTTP client listener.
        try
        {
            HttpBindManager.getInstance().stop();
        }
        catch ( RuntimeException ex )
        {
            Log.error( "An exception occurred while stopping HTTP Bind listener ", ex );
        }
    }

    /**
     * Returns the specific network interface on which Openfire is configured to listen, or null when no such preference
     * has been configured.
     *
     * @return A network interface or null.
     * @throws UnknownHostException When the configured network name cannot be resolved.
     */
    public InetAddress getListenAddress() throws UnknownHostException
    {
        String interfaceName = JiveGlobals.getXMLProperty( "network.interface" );
        InetAddress bindInterface = null;
        if (interfaceName != null) {
            if (interfaceName.trim().length() > 0) {
                bindInterface = InetAddress.getByName(interfaceName);
            }
        }
        return bindInterface;
    }

    /**
     * Returns the specific network interface on which the Openfire administration
     * console should be configured to listen, or null when no such preference
     * has been configured.
     *
     * @return A network interface or null.
     * @throws UnknownHostException When the configured network name cannot be resolved.
     */
    public InetAddress getAdminConsoleListenAddress() throws UnknownHostException
    {
        String acInterfaceName = JiveGlobals.getXMLProperty( "adminConsole.interface" );
        InetAddress acBindInterface = null;
        if (acInterfaceName != null) {
            if (acInterfaceName.trim().length() > 0) {
                acBindInterface = InetAddress.getByName(acInterfaceName);
            }
        }
        return acBindInterface;
    }

    /**
     * Returns all connection listeners.
     *
     * @return All connection listeners (never null).
     */
    public Set<ConnectionListener> getListeners() {
        final Set<ConnectionListener> listeners = new LinkedHashSet<>();
        listeners.add( clientListener );
        listeners.add( clientSslListener );
        listeners.add( boshListener );
        listeners.add( boshSslListener );
        listeners.add( serverListener );
        listeners.add( serverSslListener );
        listeners.add( componentListener );
        listeners.add( componentSslListener );
        listeners.add( connectionManagerListener );
        listeners.add( connectionManagerSslListener );
        listeners.add( webAdminListener );
        listeners.add( webAdminSslListener );
        return listeners;
    }

    /**
     * Returns a connection listener.
     *
     * The #startInSslMode parameter is used to distinguish between listeners that expect to receive SSL encrypted data
     * immediately, as opposed to connections that initially accept plain text data (the latter are typically subject to
     * StartTLS for in-band encryption configuration). When for a particular connection type only one of these options
     * is implemented, the parameter value is ignored.
     *
     * @param type The connection type for which a listener is to be configured.
     * @param startInSslMode true when the listener to be configured is in legacy SSL mode, otherwise false.
     * @return The connection listener (never null).
     */
    public ConnectionListener getListener( ConnectionType type, boolean startInSslMode )
    {
        switch ( type )
        {
            case SOCKET_C2S:
                if (startInSslMode) {
                    return clientSslListener;
                } else {
                    return clientListener;
                }

            case BOSH_C2S:
                if (startInSslMode) {
                    return boshSslListener;
                } else {
                    return boshListener;
                }
            case SOCKET_S2S:
                if (startInSslMode) {
                    return serverSslListener;
                } else {
                    return serverListener;
                }

            case COMPONENT:
                if (startInSslMode) {
                    return componentSslListener;
                } else {
                    return componentListener;
                }

            case CONNECTION_MANAGER:
                if (startInSslMode) {
                    return connectionManagerSslListener;
                } else {
                    return connectionManagerListener;
                }

            case WEBADMIN:
                if (startInSslMode) {
                    return webAdminSslListener;
                } else {
                    return webAdminListener;
                }
            default:
                throw new IllegalStateException( "Unknown connection type: "+ type );
        }
    }

    /**
     * Returns al connection listeners for the provided type.
     *
     * @param type The connection type for which a listener is to be configured.
     * @return The connection listener (never null).
     */
    public Set<ConnectionListener> getListeners( ConnectionType type )
    {
        final Set<ConnectionListener> result = new HashSet<>();
        switch ( type )
        {
            case SOCKET_C2S:
                result.add( clientListener );
                result.add( clientSslListener );
                break;

            case BOSH_C2S:
                result.add( boshListener );
                result.add( boshSslListener );
                break;

            case SOCKET_S2S:
                result.add( serverListener );
                result.add( serverSslListener );
                break;

            case COMPONENT:
                result.add( componentListener );
                result.add( componentSslListener );
                break;

            case CONNECTION_MANAGER:
                result.add( connectionManagerListener );
                result.add( connectionManagerSslListener );
                break;

            case WEBADMIN:
                result.add( webAdminListener );
                result.add( webAdminSslListener );
                break;

            default:
                throw new IllegalStateException( "Unknown connection type: "+ type );
        }

        return result;
    }

    /**
     * Return if the configuration allows this listener to be enabled (but does not verify that the listener is
     * indeed active)
     *
     * The #startInSslMode parameter is used to distinguish between listeners that expect to receive SSL encrypted data
     * immediately, as opposed to connections that initially accept plain text data (the latter are typically subject to
     * StartTLS for in-band encryption configuration). When for a particular connection type only one of these options
     * is implemented, the parameter value is ignored.
     *
     * @param type The connection type for which a listener is to be configured.
     * @param startInSslMode true when the listener to be configured is in legacy SSL mode, otherwise false.
     * @return true if configuration allows this listener to be enabled, otherwise false.
     */
    public boolean isEnabled( ConnectionType type, boolean startInSslMode )
    {
        return getListener( type, startInSslMode ).isEnabled();
    }

    /**
     * Enables or disables a connection listener. Does nothing if the particular listener is already in the requested
     * state.
     *
     * The #startInSslMode parameter is used to distinguish between listeners that expect to receive SSL encrypted data
     * immediately, as opposed to connections that initially accept plain text data (the latter are typically subject to
     * StartTLS for in-band encryption configuration). When for a particular connection type only one of these options
     * is implemented, the parameter value is ignored.
     *
     * @param type The connection type for which a listener is to be configured.
     * @param startInSslMode true when the listener to be configured is in legacy SSL mode, otherwise false.
     * @param enabled true if the listener is to be enabled, otherwise false.
     */
    public void enable( ConnectionType type, boolean startInSslMode, boolean enabled )
    {
        getListener( type, startInSslMode ).enable( enabled );
    }

    /**
     * Retrieves the configured TCP port on which a listener accepts connections.
     *
     * @param type The connection type for which a listener is to be configured.
     * @param startInSslMode true when the listener to be configured is in legacy SSL mode, otherwise false.
     * @return a port number.
     */
    public int getPort( ConnectionType type, boolean startInSslMode )
    {
        return getListener( type, startInSslMode ).getPort();
    }

    /**
     * Sets the TCP port on which a listener accepts connections.
     *
     * @param type The connection type for which a listener is to be configured.
     * @param startInSslMode true when the listener to be configured is in legacy SSL mode, otherwise false.
     * @param port a port number.
     */
    public void setPort( ConnectionType type, boolean startInSslMode, int port )
    {
        getListener( type, startInSslMode ).setPort( port );
    }

    // TODO see if we can avoid exposing MINA internals.
    public NioSocketAcceptor getSocketAcceptor( ConnectionType type, boolean startInSslMode )
    {
        return getListener( type, startInSslMode ).getSocketAcceptor();
    }

    // #####################################################################
    // Certificates events
    // #####################################################################

    @Override
    public void storeContentChanged( CertificateStore store )
    {
        // Note that all non-SSL listeners can be using TLS - these also need to be restarted.
        for ( final ConnectionListener listener : getListeners() )
        {
            if ( listener.getIdentityStoreConfiguration().equals( store.getConfiguration() ) || listener.getTrustStoreConfiguration().equals( store.getConfiguration() ) )
            {
                try
                {
                    listener.reloadConfiguration();
                }
                catch ( RuntimeException ex )
                {
                    Log.error( "An exception occurred while reloading listener " + listener + ". The reason for the reload was a certificate store change.", ex );
                }
            }
        }
    }

    // #####################################################################
    // Property events
    // #####################################################################
    @Override
    public void propertySet( String property, Map<String, Object> params ) {
        processPropertyValueChange( property, params );
    }

    @Override
    public void propertyDeleted( String property, Map<String, Object> params ) {
        processPropertyValueChange( property, params );
    }

    @Override
    public void xmlPropertySet( String property, Map<String, Object> params ) {
        processPropertyValueChange( property, params );
    }

    @Override
    public void xmlPropertyDeleted( String property, Map<String, Object> params ) {
        processPropertyValueChange( property, params );
    }

    private void processPropertyValueChange( String property, Map<String, Object> params ) {
        Log.debug( "Processing property value change for '"+property +"'. Params: " + params );

        // TODO there are more properties on which a restart is required (and this also applies to other listeners)!
        if ("xmpp.client.cert.policy".equalsIgnoreCase( property )) {
            clientSslListener.restart();
        }
    }

    // #####################################################################
    // Module management
    // #####################################################################

    @Override
    public void initialize(XMPPServer server) {
        super.initialize(server);

        // Check if we need to configure MINA to use Direct or Heap Buffers
        // Note: It has been reported that heap buffers are 50% faster than direct buffers
        if (JiveGlobals.getBooleanProperty("xmpp.socket.heapBuffer", true)) {
            IoBuffer.setUseDirectBuffer(false);
            IoBuffer.setAllocator(new SimpleBufferAllocator());
        }
    }

    @Override
    public void start() {
        super.start();
        startListeners();
        SocketSendingTracker.getInstance().start();
        CertificateManager.addListener(this);
    }

    @Override
    public void stop() {
        CertificateManager.removeListener(this);
        SocketSendingTracker.getInstance().shutdown();
        stopListeners();
        super.stop();
    }

    // #####################################################################
    // Deprecated delegation methods to individual listeners (as dictated by legacy API design).
    // #####################################################################

    // Client
    @Deprecated
    public void enableClientListener( boolean enabled )
    {
        enable( ConnectionType.SOCKET_C2S, false, enabled);
    }

    @Deprecated
    public boolean isClientListenerEnabled()
    {
        return isEnabled( ConnectionType.SOCKET_C2S, false );
    }

    @Deprecated
    public NioSocketAcceptor getSocketAcceptor()
    {
        return getSocketAcceptor( ConnectionType.SOCKET_C2S, false );
    }

    @Deprecated
    public void setClientListenerPort( int port )
    {
        setPort( ConnectionType.SOCKET_C2S, false, port );
    }

    @Deprecated
    public int getClientListenerPort()
    {
        return getPort( ConnectionType.SOCKET_C2S, false );
    }

    // Client in legacy mode
    @Deprecated
    public void enableClientSSLListener( boolean enabled )
    {
        enable( ConnectionType.SOCKET_C2S, true, enabled );
    }

    @Deprecated
    public boolean isClientSSLListenerEnabled()
    {
        return isEnabled( ConnectionType.SOCKET_C2S, true );
    }

    @Deprecated
    public NioSocketAcceptor getSSLSocketAcceptor()
    {
        return getSocketAcceptor( ConnectionType.SOCKET_C2S, true );
    }

    @Deprecated
    public void setClientSSLListenerPort( int port )
    {
        setPort( ConnectionType.SOCKET_C2S, true, port );
    }

    @Deprecated
    public int getClientSSLListenerPort()
    {
        return getPort( ConnectionType.SOCKET_C2S, true );
    }

    // Component
    @Deprecated
    public void enableComponentListener( boolean enabled )
    {
        enable( ConnectionType.COMPONENT, false, enabled );
    }

    @Deprecated
    public boolean isComponentListenerEnabled()
    {
        return isEnabled( ConnectionType.COMPONENT, false );
    }

    @Deprecated
    public NioSocketAcceptor getComponentAcceptor()
    {
        return getSocketAcceptor( ConnectionType.COMPONENT, false );
    }

    @Deprecated
    public void setComponentListenerPort( int port )
    {
        setPort( ConnectionType.COMPONENT, false, port );
    }

    @Deprecated
    public int getComponentListenerPort()
    {
        return getPort( ConnectionType.COMPONENT, false );
    }

    // Component in legacy mode
    @Deprecated
    public void enableComponentSslListener( boolean enabled )
    {
        enable( ConnectionType.COMPONENT, true, enabled );
    }

    @Deprecated
    public boolean isComponentSslListenerEnabled()
    {
        return isEnabled( ConnectionType.COMPONENT, true );
    }

    @Deprecated
    public NioSocketAcceptor getComponentSslAcceptor()
    {
        return getSocketAcceptor( ConnectionType.COMPONENT, true);
    }

    @Deprecated
    public void setComponentSslListenerPort( int port )
    {
        setPort( ConnectionType.COMPONENT, true, port );
    }

    @Deprecated
    public int getComponentSslListenerPort()
    {
        return getPort( ConnectionType.COMPONENT, true );
    }

    // Server
    @Deprecated
    public void enableServerListener( boolean enabled )
    {
        enable( ConnectionType.SOCKET_S2S, false, enabled );
    }

    @Deprecated
    public boolean isServerListenerEnabled()
    {
        return isEnabled( ConnectionType.SOCKET_S2S, false );
    }

    @Deprecated
    public NioSocketAcceptor getServerListenerSocketAcceptor()
    {
        return getSocketAcceptor( ConnectionType.SOCKET_S2S, false );
    }

    @Deprecated
    public void setServerListenerPort( int port )
    {
        setPort( ConnectionType.SOCKET_S2S, false, port );
    }

    @Deprecated
    public int getServerListenerPort()
    {
        return getPort( ConnectionType.SOCKET_S2S, false );
    }

    @Deprecated
    public void enableServerSslListener( boolean enabled )
    {
        enable( ConnectionType.SOCKET_S2S, true, enabled );
    }

    @Deprecated
    public boolean isServerSslListenerEnabled()
    {
        return isEnabled( ConnectionType.SOCKET_S2S, true );
    }

    @Deprecated
    public NioSocketAcceptor getServerSslListenerSocketAcceptor()
    {
        return getSocketAcceptor( ConnectionType.SOCKET_S2S, true );
    }

    @Deprecated
    public void setServerSslListenerPort( int port )
    {
        setPort( ConnectionType.SOCKET_S2S, true, port );
    }

    @Deprecated
    public int getServerSslListenerPort()
    {
        return getPort( ConnectionType.SOCKET_S2S, true );
    }

    // Connection Manager
    @Deprecated
    public void enableConnectionManagerListener( boolean enabled )
    {
        enable( ConnectionType.CONNECTION_MANAGER, false, enabled );
    }

    @Deprecated
    public boolean isConnectionManagerListenerEnabled()
    {
        return isEnabled( ConnectionType.CONNECTION_MANAGER, false );
    }

    /**
     * @deprecated Replaced by #getConnectionManagerSocketAcceptor
     * @return the socket acceptor
     */
    @Deprecated
    public NioSocketAcceptor getMultiplexerSocketAcceptor()
    {
        return getSocketAcceptor( ConnectionType.CONNECTION_MANAGER, false );
    }

    @Deprecated
    public NioSocketAcceptor getConnectionManagerSocketAcceptor()
    {
        return getSocketAcceptor( ConnectionType.CONNECTION_MANAGER, false );
    }

    @Deprecated
    public void setConnectionManagerListenerPort( int port )
    {
        setPort( ConnectionType.CONNECTION_MANAGER, false, port );
    }

    @Deprecated
    public int getConnectionManagerListenerPort()
    {
        return getPort( ConnectionType.CONNECTION_MANAGER, false );
    }

    // Connection Manager in legacy mode
    @Deprecated
    public void enableConnectionManagerSslListener( boolean enabled )
    {
        enable( ConnectionType.CONNECTION_MANAGER, true, enabled );
    }

    @Deprecated
    public boolean isConnectionManagerSslListenerEnabled()
    {
        return isEnabled( ConnectionType.CONNECTION_MANAGER, true );
    }

    @Deprecated
    public NioSocketAcceptor getConnectionManagerSslSocketAcceptor()
    {
        return getSocketAcceptor( ConnectionType.CONNECTION_MANAGER, true );
    }

    @Deprecated
    public void setConnectionManagerSslListenerPort( int port )
    {
        setPort( ConnectionType.CONNECTION_MANAGER, true, port );
    }

    @Deprecated
    public int getConnectionManagerSslListenerPort()
    {
        return getPort( ConnectionType.CONNECTION_MANAGER, true );
    }

    // #####################################################################
    // Other deprecated implementations.
    // #####################################################################

    /**
     * @deprecated use #getListeners
     */
    @Deprecated
    public Collection<ServerPort> getPorts() {
        final Set<ServerPort> result = new LinkedHashSet<>();
        for ( ConnectionListener listener : getListeners() )
        {
            if (listener.getServerPort() != null)
            {
                result.add( listener.getServerPort() );
            }
        }
        return result;
    }
}
