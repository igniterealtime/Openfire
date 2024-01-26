/*
 * Copyright (C) 2005-2008 Jive Software, 2016-2024 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.ConnectionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.container.PluginManagerListener;
import org.jivesoftware.openfire.http.HttpBindManager;
import org.jivesoftware.openfire.keystore.CertificateStore;
import org.jivesoftware.openfire.keystore.CertificateStoreManager;
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class ConnectionManagerImpl extends BasicModule implements ConnectionManager, CertificateEventListener, PropertyEventListener
{

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
                Connection.TLSPolicy.directTLS.name(), // force Direct TLS mode
                ConnectionSettings.Client.AUTH_PER_CLIENTCERT_POLICY,
                bindAddress,
                certificateStoreManager.getIdentityStoreConfiguration( ConnectionType.SOCKET_C2S ),
                certificateStoreManager.getTrustStoreConfiguration( ConnectionType.SOCKET_C2S ),
                ConnectionSettings.Client.COMPRESSION_SETTINGS
        );
        // BOSH / HTTP-bind
        boshListener = new ConnectionListener(
                ConnectionType.BOSH_C2S,
                HttpBindManager.HTTP_BIND_PORT.getKey(),
                HttpBindManager.HTTP_BIND_PORT.getDefaultValue(),
                HttpBindManager.HTTP_BIND_ENABLED.getKey(), // TODO this one property enables/disables both normal and Direct TLS port. Should be separated into two.
                HttpBindManager.HTTP_BIND_THREADS.getKey(),
                null,
                Connection.TLSPolicy.disabled.name(), // StartTLS over HTTP? Should use boshSslListener instead.
                HttpBindManager.HTTP_BIND_AUTH_PER_CLIENTCERT_POLICY.getKey(),
                bindAddress,
                certificateStoreManager.getIdentityStoreConfiguration( ConnectionType.BOSH_C2S ),
                certificateStoreManager.getTrustStoreConfiguration( ConnectionType.BOSH_C2S ),
                ConnectionSettings.Client.COMPRESSION_SETTINGS // Existing code re-used the generic client compression property. Should we have a BOSH-specific one?
        );
        boshSslListener = new ConnectionListener(
                ConnectionType.BOSH_C2S,
                HttpBindManager.HTTP_BIND_SECURE_PORT.getKey(),
                HttpBindManager.HTTP_BIND_SECURE_PORT.getDefaultValue(),
                HttpBindManager.HTTP_BIND_ENABLED.getKey(), // TODO this one property enables/disables both normal and Direct TLS port. Should be separated into two.
                HttpBindManager.HTTP_BIND_THREADS.getKey(),
                null,
                Connection.TLSPolicy.directTLS.name(),
                HttpBindManager.HTTP_BIND_AUTH_PER_CLIENTCERT_POLICY.getKey(),
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
            Connection.TLSPolicy.directTLS.name(), // force Direct TLS mode
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
                Connection.TLSPolicy.directTLS.name(), // force Direct TLS mode
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
                Connection.TLSPolicy.directTLS.name(), // force Direct TLS mode
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
                Connection.TLSPolicy.directTLS.name(),
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
    @Override
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
     * The #startInSslMode parameter is used to distinguish between listeners that expect to receive TLS encrypted data
     * immediately, as opposed to connections that initially accept plain text data (the latter are typically subject to
     * StartTLS for in-band encryption configuration). When for a particular connection type only one of these options
     * is implemented, the parameter value is ignored.
     *
     * @param type The connection type for which a listener is to be configured.
     * @param startInDirectTlsMode true when the listener to be configured is in Direct TLS mode, otherwise false.
     * @return The connection listener (never null).
     */
    @Override
    public ConnectionListener getListener( ConnectionType type, boolean startInDirectTlsMode )
    {
        switch ( type )
        {
            case SOCKET_C2S:
                if (startInDirectTlsMode) {
                    return clientSslListener;
                } else {
                    return clientListener;
                }

            case BOSH_C2S:
                if (startInDirectTlsMode) {
                    return boshSslListener;
                } else {
                    return boshListener;
                }
            case SOCKET_S2S:
                if (startInDirectTlsMode) {
                    return serverSslListener;
                } else {
                    return serverListener;
                }

            case COMPONENT:
                if (startInDirectTlsMode) {
                    return componentSslListener;
                } else {
                    return componentListener;
                }

            case CONNECTION_MANAGER:
                if (startInDirectTlsMode) {
                    return connectionManagerSslListener;
                } else {
                    return connectionManagerListener;
                }

            case WEBADMIN:
                if (startInDirectTlsMode) {
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
    @Override
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
     * Returns the connection acceptor for a particular connection type and configuration.
     *
     * @param type The connection type for which to return the acceptor
     * @param directTLS DirectTLS or StartTLS selector
     * @return The connection acceptor for the provided arguments.
     */
    public ConnectionAcceptor getConnectionAcceptor( ConnectionType type, boolean directTLS )
    {
        return getListener(type, directTLS).getConnectionAcceptor();
    }

    /**
     * Return if the configuration allows this listener to be enabled (but does not verify that the listener is
     * indeed active)
     *
     * The #startInSslMode parameter is used to distinguish between listeners that expect to receive TLS encrypted data
     * immediately, as opposed to connections that initially accept plain text data (the latter are typically subject to
     * StartTLS for in-band encryption configuration). When for a particular connection type only one of these options
     * is implemented, the parameter value is ignored.
     *
     * @param type The connection type for which a listener is to be configured.
     * @param startInDirectTlsMode true when the listener to be configured is in Direct TLS mode, otherwise false.
     * @return true if configuration allows this listener to be enabled, otherwise false.
     */
    @Override
    public boolean isEnabled( ConnectionType type, boolean startInDirectTlsMode )
    {
        return getListener( type, startInDirectTlsMode ).isEnabled();
    }

    /**
     * Enables or disables a connection listener. Does nothing if the particular listener is already in the requested
     * state.
     *
     * The #startInSslMode parameter is used to distinguish between listeners that expect to receive TLS encrypted data
     * immediately, as opposed to connections that initially accept plain text data (the latter are typically subject to
     * StartTLS for in-band encryption configuration). When for a particular connection type only one of these options
     * is implemented, the parameter value is ignored.
     *
     * @param type The connection type for which a listener is to be configured.
     * @param startInDirectTlsMode true when the listener to be configured is in Direct TLS mode, otherwise false.
     * @param enabled true if the listener is to be enabled, otherwise false.
     */
    @Override
    public void enable( ConnectionType type, boolean startInDirectTlsMode, boolean enabled )
    {
        getListener( type, startInDirectTlsMode ).enable( enabled );
    }

    /**
     * Retrieves the configured TCP port on which a listener accepts connections.
     *
     * @param type The connection type for which a listener is to be configured.
     * @param startInDirectTlsMode true when the listener to be configured is in DirectTLS mode, otherwise false.
     * @return a port number.
     */
    @Override
    public int getPort( ConnectionType type, boolean startInDirectTlsMode )
    {
        return getListener( type, startInDirectTlsMode ).getPort();
    }

    /**
     * Sets the TCP port on which a listener accepts connections.
     *
     * @param type The connection type for which a listener is to be configured.
     * @param startInDirectTlsMode true when the listener to be configured is in Direct TLS mode, otherwise false.
     * @param port a port number.
     */
    @Override
    public void setPort( ConnectionType type, boolean startInDirectTlsMode, int port )
    {
        getListener( type, startInDirectTlsMode ).setPort( port );
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
    public void start() {
        super.start();
        startListeners();
        CertificateManager.addListener(this);
    }

    @Override
    public void stop() {
        CertificateManager.removeListener(this);
        stopListeners();
        super.stop();
    }
}
