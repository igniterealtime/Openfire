/**
 * $RCSfile: ConnectionManagerImpl.java,v $
 * $Revision: $
 * $Date: $
 *
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

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.buffer.SimpleBufferAllocator;
import org.apache.mina.core.service.IoService;
import org.apache.mina.core.service.IoServiceListener;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.executor.ExecutorFilter;
import org.apache.mina.filter.ssl.SslFilter;
import org.apache.mina.integration.jmx.IoServiceMBean;
import org.apache.mina.integration.jmx.IoSessionMBean;
import org.apache.mina.transport.socket.SocketSessionConfig;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.jivesoftware.openfire.ConnectionManager;
import org.jivesoftware.openfire.JMXManager;
import org.jivesoftware.openfire.PacketDeliverer;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.ServerPort;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.container.PluginManagerListener;
import org.jivesoftware.openfire.http.HttpBindManager;
import org.jivesoftware.openfire.net.SSLConfig;
import org.jivesoftware.openfire.net.ServerSocketReader;
import org.jivesoftware.openfire.net.SocketAcceptThread;
import org.jivesoftware.openfire.net.SocketConnection;
import org.jivesoftware.openfire.net.SocketReader;
import org.jivesoftware.openfire.net.SocketSendingTracker;
import org.jivesoftware.openfire.net.StalledSessionsFilter;
import org.jivesoftware.openfire.nio.ClientConnectionHandler;
import org.jivesoftware.openfire.nio.ComponentConnectionHandler;
import org.jivesoftware.openfire.nio.MultiplexerConnectionHandler;
import org.jivesoftware.openfire.nio.XMPPCodecFactory;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectionManagerImpl extends BasicModule implements ConnectionManager, CertificateEventListener, PropertyEventListener {

	private static final int MB = 1024 * 1024;

    public static final String EXECUTOR_FILTER_NAME = "threadModel";
    public static final String TLS_FILTER_NAME = "tls";
    public static final String COMPRESSION_FILTER_NAME = "compression";
    public static final String XMPP_CODEC_FILTER_NAME = "xmpp";
    public static final String CAPACITY_FILTER_NAME = "outCap";

    private static final String CLIENT_SOCKET_ACCEPTOR_NAME = "client";
    private static final String CLIENT_SSL_SOCKET_ACCEPTOR_NAME = "client_ssl";
    private static final String COMPONENT_SOCKET_ACCEPTOR_NAME = "component";
    private static final String MULTIPLEXER_SOCKET_ACCEPTOR_NAME = "multiplexer";

    private static final Logger Log = LoggerFactory.getLogger(ConnectionManagerImpl.class);

    private NioSocketAcceptor socketAcceptor;
    private NioSocketAcceptor sslSocketAcceptor;
    private NioSocketAcceptor componentAcceptor;
    private SocketAcceptThread serverSocketThread;
    private NioSocketAcceptor multiplexerSocketAcceptor;
    private ArrayList<ServerPort> ports;

    private SessionManager sessionManager;
    private PacketDeliverer deliverer;
    private PacketRouter router;
    private RoutingTable routingTable;
    private String serverName;
    private String localIPAddress = null;

    // Used to know if the sockets have been started
    private boolean isSocketStarted = false;

    public ConnectionManagerImpl() {
        super("Connection Manager");
        ports = new ArrayList<ServerPort>(4);
    }

    private synchronized void createListeners() {
        if (isSocketStarted || sessionManager == null || deliverer == null || router == null || serverName == null) {
            return;
        }
        // Create the port listener for s2s communication
        createServerListener(localIPAddress);
        // Create the port listener for Connections Multiplexers
        createConnectionManagerListener();
        // Create the port listener for external components
        createComponentListener();
        // Create the port listener for clients
        createClientListeners();
        // Create the port listener for secured clients
        createClientSSLListeners();
    }

    private synchronized void startListeners() {
        if (isSocketStarted || sessionManager == null || deliverer == null || router == null || serverName == null) {
            return;
        }

        // Check if plugins have been loaded
        PluginManager pluginManager = XMPPServer.getInstance().getPluginManager();
        if (!pluginManager.isExecuted()) {
            pluginManager.addPluginManagerListener(new PluginManagerListener() {
                public void pluginsMonitored() {
                    // Stop listening for plugin events
                    XMPPServer.getInstance().getPluginManager().removePluginManagerListener(this);
                    // Start listeners
                    startListeners();
                }
            });
            return;
        }

        isSocketStarted = true;

        // Setup port info
        try {
            localIPAddress = InetAddress.getLocalHost().getHostAddress();
        }
        catch (UnknownHostException e) {
            if (localIPAddress == null) {
                localIPAddress = "Unknown";
            }
        }
        // Start the port listener for s2s communication
        startServerListener();
        // Start the port listener for Connections Multiplexers
        startConnectionManagerListener(localIPAddress);
        // Start the port listener for external components
        startComponentListener();
        // Start the port listener for clients
        startClientListeners(localIPAddress);
        // Start the port listener for secured clients
        startClientSSLListeners(localIPAddress);
        // Start the HTTP client listener
        startHTTPBindListeners();
    }

    private void createServerListener(String localIPAddress) {
        // Start servers socket unless it's been disabled.
        if (isServerListenerEnabled()) {
            int port = getServerListenerPort();
            try {
                serverSocketThread = new SocketAcceptThread(this, new ServerPort(port, serverName,
                        localIPAddress, false, null, ServerPort.Type.server));
                ports.add(serverSocketThread.getServerPort());
                serverSocketThread.setDaemon(true);
                serverSocketThread.setPriority(Thread.MAX_PRIORITY);
            }
            catch (Exception e) {
                System.err.println("Error creating server listener on port " + port + ": " +
                        e.getMessage());
                Log.error(LocaleUtils.getLocalizedString("admin.error.socket-setup"), e);
            }
        }
    }

    private void startServerListener() {
        // Start servers socket unless it's been disabled.
        if (isServerListenerEnabled()) {
            int port = getServerListenerPort();
            try {
                serverSocketThread.start();

                List<String> params = new ArrayList<String>();
                params.add(Integer.toString(serverSocketThread.getPort()));
                Log.info(LocaleUtils.getLocalizedString("startup.server", params));
            }
            catch (Exception e) {
                System.err.println("Error starting server listener on port " + port + ": " +
                        e.getMessage());
                Log.error(LocaleUtils.getLocalizedString("admin.error.socket-setup"), e);
            }
        }
    }

    private void stopServerListener() {
        if (serverSocketThread != null) {
            serverSocketThread.shutdown();
            ports.remove(serverSocketThread.getServerPort());
            serverSocketThread = null;
        }
    }

    private void createConnectionManagerListener() {
        // Start multiplexers socket unless it's been disabled.
        if (isConnectionManagerListenerEnabled()) {
            // Create SocketAcceptor with correct number of processors
            multiplexerSocketAcceptor = buildSocketAcceptor(MULTIPLEXER_SOCKET_ACCEPTOR_NAME);
            // Customize Executor that will be used by processors to process incoming stanzas
            int maxPoolSize = JiveGlobals.getIntProperty("xmpp.multiplex.processing.threads", 16);
            ExecutorFilter executorFilter = new ExecutorFilter(getCorePoolSize(maxPoolSize), maxPoolSize, 60, TimeUnit.SECONDS);
            ThreadPoolExecutor eventExecutor = (ThreadPoolExecutor)executorFilter.getExecutor();
            ThreadFactory threadFactory = eventExecutor.getThreadFactory();
            threadFactory = new DelegatingThreadFactory("Multiplexer-Thread-", threadFactory);
            eventExecutor.setThreadFactory(threadFactory);
            multiplexerSocketAcceptor.getFilterChain().addFirst(EXECUTOR_FILTER_NAME, executorFilter);
            // Add the XMPP codec filter
            multiplexerSocketAcceptor.getFilterChain().addAfter(EXECUTOR_FILTER_NAME, XMPP_CODEC_FILTER_NAME, new ProtocolCodecFilter(new XMPPCodecFactory()));

        }
    }

    private void startConnectionManagerListener(String localIPAddress) {
        // Start multiplexers socket unless it's been disabled.
        if (isConnectionManagerListenerEnabled()) {
            int port = getConnectionManagerListenerPort();

            try {
                // Listen on a specific network interface if it has been set.
                String interfaceName = JiveGlobals.getXMLProperty("network.interface");
                InetAddress bindInterface = null;
                if (interfaceName != null) {
                    if (interfaceName.trim().length() > 0) {
                        bindInterface = InetAddress.getByName(interfaceName);
                    }
                }
                // Start accepting connections
                multiplexerSocketAcceptor.setHandler(new MultiplexerConnectionHandler(serverName));
                multiplexerSocketAcceptor.bind(new InetSocketAddress(bindInterface, port));

                ports.add(new ServerPort(port, serverName, localIPAddress, false, null, ServerPort.Type.connectionManager));

                List<String> params = new ArrayList<String>();
                params.add(Integer.toString(port));
                Log.info(LocaleUtils.getLocalizedString("startup.multiplexer", params));
            }
            catch (Exception e) {
                System.err.println("Error starting multiplexer listener on port " + port + ": " +
                        e.getMessage());
                Log.error(LocaleUtils.getLocalizedString("admin.error.socket-setup"), e);
            }
        }
    }

    private void stopConnectionManagerListener() {
        if (multiplexerSocketAcceptor != null) {
            multiplexerSocketAcceptor.unbind();
            for (ServerPort port : ports) {
                if (port.isConnectionManagerPort()) {
                    ports.remove(port);
                    break;
                }
            }
            multiplexerSocketAcceptor = null;
        }
    }

    private void createComponentListener() {
        // Start components socket unless it's been disabled.
        if (isComponentListenerEnabled() && componentAcceptor == null) {
            // Create SocketAcceptor with correct number of processors
            componentAcceptor = buildSocketAcceptor(COMPONENT_SOCKET_ACCEPTOR_NAME);
            int maxPoolSize = JiveGlobals.getIntProperty("xmpp.component.processing.threads", 16);
            ExecutorFilter executorFilter = new ExecutorFilter(getCorePoolSize(maxPoolSize), maxPoolSize, 60, TimeUnit.SECONDS);
            ThreadPoolExecutor eventExecutor = (ThreadPoolExecutor)executorFilter.getExecutor();
            ThreadFactory threadFactory = eventExecutor.getThreadFactory();
            threadFactory = new DelegatingThreadFactory("Component-Thread-", threadFactory);
            eventExecutor.setThreadFactory(threadFactory);
            componentAcceptor.getFilterChain().addFirst(EXECUTOR_FILTER_NAME, executorFilter);
            // Add the XMPP codec filter
            componentAcceptor.getFilterChain().addAfter(EXECUTOR_FILTER_NAME, XMPP_CODEC_FILTER_NAME, new ProtocolCodecFilter(new XMPPCodecFactory()));
        }
    }

    private void startComponentListener() {
        // Start components socket unless it's been disabled.
        if (isComponentListenerEnabled() && componentAcceptor != null &&
                componentAcceptor.getManagedSessionCount() == 0) {
            int port = getComponentListenerPort();
            try {
                // Listen on a specific network interface if it has been set.
                String interfaceName = JiveGlobals.getXMLProperty("network.interface");
                InetAddress bindInterface = null;
                if (interfaceName != null) {
                    if (interfaceName.trim().length() > 0) {
                        bindInterface = InetAddress.getByName(interfaceName);
                    }
                }
                // Start accepting connections
                componentAcceptor.setHandler(new ComponentConnectionHandler(serverName));
                componentAcceptor.bind(new InetSocketAddress(bindInterface, port));

                ports.add(new ServerPort(port, serverName, localIPAddress, false, null, ServerPort.Type.component));

                List<String> params = new ArrayList<String>();
                params.add(Integer.toString(port));
                Log.info(LocaleUtils.getLocalizedString("startup.component", params));
            }
            catch (Exception e) {
                System.err.println("Error starting component listener on port " + port + ": " +
                        e.getMessage());
                Log.error(LocaleUtils.getLocalizedString("admin.error.socket-setup"), e);
            }
        }
    }

    private void stopComponentListener() {
        if (componentAcceptor != null) {
            componentAcceptor.unbind();
            for (ServerPort port : ports) {
                if (port.isComponentPort()) {
                    ports.remove(port);
                    break;
                }
            }
            componentAcceptor = null;
        }
    }

    private void createClientListeners() {
        // Start clients plain socket unless it's been disabled.
        if (isClientListenerEnabled()) {
            // Create SocketAcceptor with correct number of processors
            socketAcceptor = buildSocketAcceptor(CLIENT_SOCKET_ACCEPTOR_NAME);
            // Customize Executor that will be used by processors to process incoming stanzas
            int maxPoolSize = JiveGlobals.getIntProperty(ConnectionSettings.Client.MAX_THREADS, 16);
            ExecutorFilter executorFilter = new ExecutorFilter(getCorePoolSize(maxPoolSize), maxPoolSize, 60, TimeUnit.SECONDS);
            ThreadPoolExecutor eventExecutor = (ThreadPoolExecutor)executorFilter.getExecutor();
            ThreadFactory threadFactory = eventExecutor.getThreadFactory();
            threadFactory = new DelegatingThreadFactory("C2S-Thread-", threadFactory);
            eventExecutor.setThreadFactory(threadFactory);

            // Add the XMPP codec filter
            socketAcceptor.getFilterChain().addFirst(EXECUTOR_FILTER_NAME, executorFilter);
            socketAcceptor.getFilterChain().addAfter(EXECUTOR_FILTER_NAME, XMPP_CODEC_FILTER_NAME, new ProtocolCodecFilter(new XMPPCodecFactory()));
            // Kill sessions whose outgoing queues keep growing and fail to send traffic
            socketAcceptor.getFilterChain().addAfter(XMPP_CODEC_FILTER_NAME, CAPACITY_FILTER_NAME, new StalledSessionsFilter());
            // Throttle sessions who send data too fast
            int maxBufferSize = JiveGlobals.getIntProperty(ConnectionSettings.Client.MAX_READ_BUFFER, 10 * MB);
            socketAcceptor.getSessionConfig().setMaxReadBufferSize(maxBufferSize);
	        Log.debug("Throttling read buffer for connections from socketAcceptor={} to max={} bytes",
	                  socketAcceptor, maxBufferSize);
        }
    }

    private void startClientListeners(String localIPAddress) {
        // Start clients plain socket unless it's been disabled.
        if (isClientListenerEnabled()) {
            int port = getClientListenerPort();
            try {
                // Listen on a specific network interface if it has been set.
                String interfaceName = JiveGlobals.getXMLProperty("network.interface");
                InetAddress bindInterface = null;
                if (interfaceName != null) {
                    if (interfaceName.trim().length() > 0) {
                        bindInterface = InetAddress.getByName(interfaceName);
                    }
                }
                // Start accepting connections
                socketAcceptor.setHandler(new ClientConnectionHandler(serverName));
                socketAcceptor.bind(new InetSocketAddress(bindInterface, port));

                ports.add(new ServerPort(port, serverName, localIPAddress, false, null, ServerPort.Type.client));

                List<String> params = new ArrayList<String>();
                params.add(Integer.toString(port));
                Log.info(LocaleUtils.getLocalizedString("startup.plain", params));
            }
            catch (Exception e) {
                System.err.println("Error starting XMPP listener on port " + port + ": " +
                        e.getMessage());
                Log.error(LocaleUtils.getLocalizedString("admin.error.socket-setup"), e);
            }
        }
    }

    private void stopClientListeners() {
        if (socketAcceptor != null) {
            socketAcceptor.unbind();
            for (ServerPort port : ports) {
                if (port.isClientPort() && !port.isSecure()) {
                    ports.remove(port);
                    break;
                }
            }
            socketAcceptor = null;
        }
    }

    private void createClientSSLListeners() {
        // Start clients SSL unless it's been disabled.
        if (isClientSSLListenerEnabled()) {
            int port = getClientSSLListenerPort();
            String algorithm = JiveGlobals.getProperty(ConnectionSettings.Client.TLS_ALGORITHM, "TLS");
            try {
                // Customize Executor that will be used by processors to process incoming stanzas
                int maxPoolSize = JiveGlobals.getIntProperty(ConnectionSettings.Client.MAX_THREADS_SSL, 16);
                ExecutorFilter executorFilter = new ExecutorFilter(getCorePoolSize(maxPoolSize), maxPoolSize, 60, TimeUnit.SECONDS);
                ThreadPoolExecutor eventExecutor = (ThreadPoolExecutor)executorFilter.getExecutor();
                ThreadFactory threadFactory = eventExecutor.getThreadFactory();
                threadFactory = new DelegatingThreadFactory("LegacySSL-Thread-", threadFactory);
                eventExecutor.setThreadFactory(threadFactory);
                
                // Create SocketAcceptor with correct number of processors
                sslSocketAcceptor = buildSocketAcceptor(CLIENT_SSL_SOCKET_ACCEPTOR_NAME);
                sslSocketAcceptor.getFilterChain().addFirst(EXECUTOR_FILTER_NAME, executorFilter);

                // Add the XMPP codec filter
                sslSocketAcceptor.getFilterChain().addAfter(EXECUTOR_FILTER_NAME, XMPP_CODEC_FILTER_NAME, new ProtocolCodecFilter(new XMPPCodecFactory()));
                // Kill sessions whose outgoing queues keep growing and fail to send traffic
                sslSocketAcceptor.getFilterChain().addAfter(XMPP_CODEC_FILTER_NAME, CAPACITY_FILTER_NAME, new StalledSessionsFilter());
                
				// Throttle sessions who send data too fast
				int maxBufferSize = JiveGlobals.getIntProperty(ConnectionSettings.Client.MAX_READ_BUFFER_SSL, 10 * MB);
				sslSocketAcceptor.getSessionConfig().setMaxReadBufferSize(maxBufferSize);
		        Log.debug("Throttling read buffer for connections from sslSocketAcceptor={} to max={} bytes",
		                  sslSocketAcceptor, maxBufferSize);

                // Add the SSL filter now since sockets are "borned" encrypted in the old ssl method
                SSLContext sslContext = SSLContext.getInstance(algorithm);
                KeyManagerFactory keyFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                keyFactory.init(SSLConfig.getKeyStore(), SSLConfig.getKeyPassword().toCharArray());
                TrustManagerFactory trustFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustFactory.init(SSLConfig.getc2sTrustStore());

                sslContext.init(keyFactory.getKeyManagers(),
                        trustFactory.getTrustManagers(),
                        new java.security.SecureRandom());

                SslFilter sslFilter = new SslFilter(sslContext);
                if (JiveGlobals.getProperty(ConnectionSettings.Client.AUTH_PER_CLIENTCERT_POLICY,"disabled").equals("needed")) {
                    sslFilter.setNeedClientAuth(true);
                }
                else if(JiveGlobals.getProperty(ConnectionSettings.Client.AUTH_PER_CLIENTCERT_POLICY,"disabled").equals("wanted")) {
                    sslFilter.setWantClientAuth(true);
                }
                sslSocketAcceptor.getFilterChain().addAfter(EXECUTOR_FILTER_NAME, TLS_FILTER_NAME, sslFilter);

            }
            catch (Exception e) {
                System.err.println("Error starting SSL XMPP listener on port " + port + ": " +
                        e.getMessage());
                Log.error(LocaleUtils.getLocalizedString("admin.error.ssl"), e);
            }
        }
    }

    private void startClientSSLListeners(String localIPAddress) {
        // Start clients SSL unless it's been disabled.
        if (isClientSSLListenerEnabled()) {
            int port = getClientSSLListenerPort();
            try {
                // Listen on a specific network interface if it has been set.
                String interfaceName = JiveGlobals.getXMLProperty("network.interface");
                InetAddress bindInterface = null;
                if (interfaceName != null) {
                    if (interfaceName.trim().length() > 0) {
                        bindInterface = InetAddress.getByName(interfaceName);
                    }
                }
                // Start accepting connections
                sslSocketAcceptor.setHandler(new ClientConnectionHandler(serverName));
                sslSocketAcceptor.bind(new InetSocketAddress(bindInterface, port));

                ports.add(new ServerPort(port, serverName, localIPAddress, true, null, ServerPort.Type.client));

                List<String> params = new ArrayList<String>();
                params.add(Integer.toString(port));
                Log.info(LocaleUtils.getLocalizedString("startup.ssl", params));
            }
            catch (Exception e) {
                System.err.println("Error starting SSL XMPP listener on port " + port + ": " +
                        e.getMessage());
                Log.error(LocaleUtils.getLocalizedString("admin.error.ssl"), e);
            }
        }
    }

    private void stopClientSSLListeners() {
        if (sslSocketAcceptor != null) {
            sslSocketAcceptor.unbind();
            for (ServerPort port : ports) {
                if (port.isClientPort() && port.isSecure()) {
                    ports.remove(port);
                    break;
                }
            }
            sslSocketAcceptor = null;
        }
    }

    private void restartClientSSLListeners() {
        if (!isSocketStarted) {
            return;
        }
        // Setup port info
        try {
            localIPAddress = InetAddress.getLocalHost().getHostAddress();
        }
        catch (UnknownHostException e) {
            if (localIPAddress == null) {
                localIPAddress = "Unknown";
            }
        }
        stopClientSSLListeners();
        createClientSSLListeners();
        startClientSSLListeners(localIPAddress);
    }

    public Collection<ServerPort> getPorts() {
        return ports;
    }

    public SocketReader createSocketReader(Socket sock, boolean isSecure, ServerPort serverPort,
            boolean useBlockingMode) throws IOException {
        if (serverPort.isServerPort()) {
            SocketConnection conn = new SocketConnection(deliverer, sock, isSecure);
            return new ServerSocketReader(router, routingTable, serverName, sock, conn,
                    useBlockingMode);
        }
        return null;
    }

    private void startHTTPBindListeners() {
        HttpBindManager.getInstance().start();
    }

    @Override
	public void initialize(XMPPServer server) {
        super.initialize(server);
        serverName = server.getServerInfo().getXMPPDomain();
        router = server.getPacketRouter();
        routingTable = server.getRoutingTable();
        deliverer = server.getPacketDeliverer();
        sessionManager = server.getSessionManager();
        // Check if we need to configure MINA to use Direct or Heap Buffers
        // Note: It has been reported that heap buffers are 50% faster than direct buffers
        if (JiveGlobals.getBooleanProperty("xmpp.socket.heapBuffer", true)) {
            IoBuffer.setUseDirectBuffer(false);
            IoBuffer.setAllocator(new SimpleBufferAllocator());
        }
    }

    public void enableClientListener(boolean enabled) {
        if (enabled == isClientListenerEnabled()) {
            // Ignore new setting
            return;
        }
        if (enabled) {
            JiveGlobals.setProperty(ConnectionSettings.Client.SOCKET_ACTIVE, "true");
            // Start the port listener for clients
            createClientListeners();
            startClientListeners(localIPAddress);
        }
        else {
            JiveGlobals.setProperty(ConnectionSettings.Client.SOCKET_ACTIVE, "false");
            // Stop the port listener for clients
            stopClientListeners();
        }
    }

    public boolean isClientListenerEnabled() {
        return JiveGlobals.getBooleanProperty(ConnectionSettings.Client.SOCKET_ACTIVE, true);
    }

    public void enableClientSSLListener(boolean enabled) {
        if (enabled == isClientSSLListenerEnabled()) {
            // Ignore new setting
            return;
        }
        if (enabled) {
            JiveGlobals.setProperty(ConnectionSettings.Client.ENABLE_OLD_SSLPORT, "true");
            // Start the port listener for secured clients
            createClientSSLListeners();
            startClientSSLListeners(localIPAddress);
        }
        else {
            JiveGlobals.setProperty(ConnectionSettings.Client.ENABLE_OLD_SSLPORT, "false");
            // Stop the port listener for secured clients
            stopClientSSLListeners();
        }
    }

    public boolean isClientSSLListenerEnabled() {
        try {
            return JiveGlobals.getBooleanProperty(ConnectionSettings.Client.ENABLE_OLD_SSLPORT, false) && SSLConfig.getKeyStore().size() > 0;
        } catch (KeyStoreException e) {
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    public void enableComponentListener(boolean enabled) {
        if (enabled == isComponentListenerEnabled()) {
            // Ignore new setting
            return;
        }
        if (enabled) {
            JiveGlobals.setProperty(ConnectionSettings.Component.SOCKET_ACTIVE, "true");
            // Start the port listener for external components
            createComponentListener();
            startComponentListener();
        }
        else {
            JiveGlobals.setProperty(ConnectionSettings.Component.SOCKET_ACTIVE, "false");
            // Stop the port listener for external components
            stopComponentListener();
        }
    }

    public boolean isComponentListenerEnabled() {
        return JiveGlobals.getBooleanProperty(ConnectionSettings.Component.SOCKET_ACTIVE, false);
    }

    public void enableServerListener(boolean enabled) {
        if (enabled == isServerListenerEnabled()) {
            // Ignore new setting
            return;
        }
        if (enabled) {
            JiveGlobals.setProperty(ConnectionSettings.Server.SOCKET_ACTIVE, "true");
            // Start the port listener for s2s communication
            createServerListener(localIPAddress);
            startServerListener();
        }
        else {
            JiveGlobals.setProperty(ConnectionSettings.Server.SOCKET_ACTIVE, "false");
            // Stop the port listener for s2s communication
            stopServerListener();
        }
    }

    public boolean isServerListenerEnabled() {
        return JiveGlobals.getBooleanProperty(ConnectionSettings.Server.SOCKET_ACTIVE, true);
    }

    public void enableConnectionManagerListener(boolean enabled) {
        if (enabled == isConnectionManagerListenerEnabled()) {
            // Ignore new setting
            return;
        }
        if (enabled) {
            JiveGlobals.setProperty(ConnectionSettings.Multiplex.SOCKET_ACTIVE, "true");
            // Start the port listener for s2s communication
            createConnectionManagerListener();
            startConnectionManagerListener(localIPAddress);
        }
        else {
            JiveGlobals.setProperty(ConnectionSettings.Multiplex.SOCKET_ACTIVE, "false");
            // Stop the port listener for s2s communication
            stopConnectionManagerListener();
        }
    }

    public boolean isConnectionManagerListenerEnabled() {
        return JiveGlobals.getBooleanProperty(ConnectionSettings.Multiplex.SOCKET_ACTIVE, false);
    }

    public void setClientListenerPort(int port) {
        if (port == getClientListenerPort()) {
            // Ignore new setting
            return;
        }
        JiveGlobals.setProperty(ConnectionSettings.Client.PORT, String.valueOf(port));
        // Stop the port listener for clients
        stopClientListeners();
        if (isClientListenerEnabled()) {
            // Start the port listener for clients
            createClientListeners();
            startClientListeners(localIPAddress);
        }
    }

    public NioSocketAcceptor getSocketAcceptor() {
        return socketAcceptor;
    }

    public int getClientListenerPort() {
        return JiveGlobals.getIntProperty(ConnectionSettings.Client.PORT, DEFAULT_PORT);
    }

    public NioSocketAcceptor getSSLSocketAcceptor() {
        return sslSocketAcceptor;
    }

    public void setClientSSLListenerPort(int port) {
        if (port == getClientSSLListenerPort()) {
            // Ignore new setting
            return;
        }
        JiveGlobals.setProperty(ConnectionSettings.Client.OLD_SSLPORT, String.valueOf(port));
        // Stop the port listener for secured clients
        stopClientSSLListeners();
        if (isClientSSLListenerEnabled()) {
            // Start the port listener for secured clients
            createClientSSLListeners();
            startClientSSLListeners(localIPAddress);
        }
    }

    public int getClientSSLListenerPort() {
        return JiveGlobals.getIntProperty(ConnectionSettings.Client.OLD_SSLPORT, DEFAULT_SSL_PORT);
    }

    public void setComponentListenerPort(int port) {
        if (port == getComponentListenerPort()) {
            // Ignore new setting
            return;
        }
        JiveGlobals.setProperty(ConnectionSettings.Component.PORT, String.valueOf(port));
        // Stop the port listener for external components
        stopComponentListener();
        if (isComponentListenerEnabled()) {
            // Start the port listener for external components
            createComponentListener();
            startComponentListener();
        }
    }

    public NioSocketAcceptor getComponentAcceptor() {
        return componentAcceptor;
    }

    public int getComponentListenerPort() {
        return JiveGlobals.getIntProperty(ConnectionSettings.Component.PORT, DEFAULT_COMPONENT_PORT);
    }

    public void setServerListenerPort(int port) {
        if (port == getServerListenerPort()) {
            // Ignore new setting
            return;
        }
        JiveGlobals.setProperty(ConnectionSettings.Server.PORT, String.valueOf(port));
        // Stop the port listener for s2s communication
        stopServerListener();
        if (isServerListenerEnabled()) {
            // Start the port listener for s2s communication
            createServerListener(localIPAddress);
            startServerListener();
        }
    }

    public int getServerListenerPort() {
        return JiveGlobals.getIntProperty(ConnectionSettings.Server.PORT, DEFAULT_SERVER_PORT);
    }

    public NioSocketAcceptor getMultiplexerSocketAcceptor() {
        return multiplexerSocketAcceptor;
    }

    public void setConnectionManagerListenerPort(int port) {
        if (port == getConnectionManagerListenerPort()) {
            // Ignore new setting
            return;
        }
        JiveGlobals.setProperty(ConnectionSettings.Multiplex.PORT, String.valueOf(port));
        // Stop the port listener for connection managers
        stopConnectionManagerListener();
        if (isConnectionManagerListenerEnabled()) {
            // Start the port listener for connection managers
            createConnectionManagerListener();
            startConnectionManagerListener(localIPAddress);
        }
    }

    public int getConnectionManagerListenerPort() {
        return JiveGlobals.getIntProperty(ConnectionSettings.Multiplex.PORT, DEFAULT_MULTIPLEX_PORT);
    }

    // #####################################################################
    // Certificates events
    // #####################################################################

    public void certificateCreated(KeyStore keyStore, String alias, X509Certificate cert) {
        restartClientSSLListeners();
    }

    public void certificateDeleted(KeyStore keyStore, String alias) {
        restartClientSSLListeners();
    }

    public void certificateSigned(KeyStore keyStore, String alias, List<X509Certificate> certificates) {
        restartClientSSLListeners();
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
        Log.debug( "Processing property value change for '"+property +"'." );

        if ("xmpp.client.cert.policy".equalsIgnoreCase( property )) {
            restartClientSSLListeners();
        }
    }

    private NioSocketAcceptor buildSocketAcceptor(String name) {
        NioSocketAcceptor socketAcceptor;
        // Create SocketAcceptor with correct number of processors
        int processorCount = JiveGlobals.getIntProperty("xmpp.processor.count", Runtime.getRuntime().availableProcessors());
        socketAcceptor = new NioSocketAcceptor(processorCount);
        // Set that it will be possible to bind a socket if there is a connection in the timeout state
        socketAcceptor.setReuseAddress(true);
        // Set the listen backlog (queue) length. Default is 50.
        socketAcceptor.setBacklog(JiveGlobals.getIntProperty("xmpp.socket.backlog", 50));

        // Set default (low level) settings for new socket connections
        SocketSessionConfig socketSessionConfig = socketAcceptor.getSessionConfig();
        //socketSessionConfig.setKeepAlive();
        int receiveBuffer = JiveGlobals.getIntProperty("xmpp.socket.buffer.receive", -1);
        if (receiveBuffer > 0 ) {
            socketSessionConfig.setReceiveBufferSize(receiveBuffer);
        }
        int sendBuffer = JiveGlobals.getIntProperty("xmpp.socket.buffer.send", -1);
        if (sendBuffer > 0 ) {
            socketSessionConfig.setSendBufferSize(sendBuffer);
        }
        int linger = JiveGlobals.getIntProperty("xmpp.socket.linger", -1);
        if (linger > 0 ) {
            socketSessionConfig.setSoLinger(linger);
        }
        socketSessionConfig.setTcpNoDelay(
                JiveGlobals.getBooleanProperty("xmpp.socket.tcp-nodelay", socketSessionConfig.isTcpNoDelay()));
        if (JMXManager.isEnabled()) {
        	configureJMX(socketAcceptor, name);
        }
        return socketAcceptor;
    }

    private void configureJMX(NioSocketAcceptor acceptor, String suffix) {
        final String prefix = IoServiceMBean.class.getPackage().getName();
        // monitor the IoService
        try {
            IoServiceMBean mbean = new IoServiceMBean(acceptor);
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();  
            ObjectName name = new ObjectName(prefix + ":type=SocketAcceptor,name=" + suffix);
            mbs.registerMBean( mbean, name );
//        	mbean.startCollectingStats(JiveGlobals.getIntProperty("xmpp.socket.jmx.interval", 60000));
    	} catch (JMException ex) {
    		Log.warn("Failed to register MINA acceptor mbean (JMX): " + ex);
    	}
    	// optionally register IoSession mbeans (one per session)
    	if (JiveGlobals.getBooleanProperty("xmpp.socket.jmx.sessions", false)) {
	    	acceptor.addListener(new IoServiceListener() {
	    	    public void sessionCreated(IoSession session) {
	    	        try {
                        IoSessionMBean mbean = new IoSessionMBean(session);
	    	            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();  
	    	            ObjectName name = new ObjectName(prefix + ":type=IoSession,name=" + 
	    	            					session.getRemoteAddress().toString().replace(':', '/'));
	    	            mbs.registerMBean(mbean, name);
	    	        } catch(JMException ex) {
	    	            Log.warn("Failed to register MINA session mbean (JMX): " + ex);
	    	        }      
	    	    }
	    	    public void sessionDestroyed(IoSession session) {
	    	        try {
	    	            ObjectName name = new ObjectName(prefix + ":type=IoSession,name=" + 
	    	            					session.getRemoteAddress().toString().replace(':', '/'));
	    	            ManagementFactory.getPlatformMBeanServer().unregisterMBean(name);
	    	        } catch(JMException ex) {
	    	            Log.warn("Failed to unregister MINA session mbean (JMX): " + ex);
	    	        }      
	    	    }
                public void serviceActivated(IoService service) throws Exception { }
                public void serviceDeactivated(IoService service) throws Exception { }
                public void serviceIdle(IoService service, IdleStatus idleStatus) throws Exception { }
	    	});
    	}
    }
    
	private int getCorePoolSize(int maxPoolSize) {
		return (maxPoolSize/4)+1;
	}

	// #####################################################################
    // Module management
    // #####################################################################

    @Override
	public void start() {
        super.start();
        createListeners();
        startListeners();
        SocketSendingTracker.getInstance().start();
        CertificateManager.addListener(this);
    }

    @Override
	public void stop() {
        super.stop();
        stopClientListeners();
        stopClientSSLListeners();
        stopComponentListener();
        stopConnectionManagerListener();
        stopServerListener();
        HttpBindManager.getInstance().stop();
        SocketSendingTracker.getInstance().shutdown();
        CertificateManager.removeListener(this);
        serverName = null;
    }

    private static class DelegatingThreadFactory implements ThreadFactory {
        private final AtomicInteger threadId;
        private final ThreadFactory originalThreadFactory;
        private String threadNamePrefix;

        public DelegatingThreadFactory(String threadNamePrefix, ThreadFactory originalThreadFactory) {
            this.originalThreadFactory = originalThreadFactory;
            threadId = new AtomicInteger(0);
            this.threadNamePrefix = threadNamePrefix;
        }

        public Thread newThread(Runnable runnable)
        {
            Thread t = originalThreadFactory.newThread(runnable);
            t.setName(threadNamePrefix + threadId.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }
}
