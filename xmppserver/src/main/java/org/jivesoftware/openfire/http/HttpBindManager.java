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

package org.jivesoftware.openfire.http;

import java.io.File;
import java.util.*;

import org.apache.jasper.servlet.JasperInitializer;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.SimpleInstanceManager;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.plus.annotation.ContainerInitializer;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.WebAppLoaderFix;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.JMXManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.keystore.CertificateStore;
import org.jivesoftware.openfire.keystore.IdentityStore;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.openfire.spi.ConnectionManagerImpl;
import org.jivesoftware.openfire.spi.ConnectionType;
import org.jivesoftware.openfire.spi.EncryptionArtifactFactory;
import org.jivesoftware.openfire.websocket.OpenfireWebSocketServlet;
import org.jivesoftware.util.CertificateEventListener;
import org.jivesoftware.util.CertificateManager;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;
import org.jivesoftware.util.TaskEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responsible for making available BOSH (functionality to the outside world, using an embedded web server.
 */
public final class HttpBindManager implements CertificateEventListener, PropertyEventListener {

    private static final Logger Log = LoggerFactory.getLogger(HttpBindManager.class);

    public static final String HTTP_BIND_ENABLED = "httpbind.enabled";

    public static final boolean HTTP_BIND_ENABLED_DEFAULT = true;

    public static final String HTTP_BIND_PORT = "httpbind.port.plain";

    public static final int HTTP_BIND_PORT_DEFAULT = 7070;

    public static final String HTTP_BIND_SECURE_PORT = "httpbind.port.secure";

    public static final int HTTP_BIND_SECURE_PORT_DEFAULT = 7443;

    public static final String HTTP_BIND_THREADS = "httpbind.client.processing.threads";

    public static final String HTTP_BIND_AUTH_PER_CLIENTCERT_POLICY = "httpbind.client.cert.policy";

    public static final int HTTP_BIND_THREADS_DEFAULT = 200;

    private static final String HTTP_BIND_FORWARDED = "httpbind.forwarded.enabled";

    private static final String HTTP_BIND_FORWARDED_FOR = "httpbind.forwarded.for.header";

    private static final String HTTP_BIND_FORWARDED_SERVER = "httpbind.forwarded.server.header";

    private static final String HTTP_BIND_FORWARDED_HOST = "httpbind.forwarded.host.header";

    private static final String HTTP_BIND_FORWARDED_HOST_NAME = "httpbind.forwarded.host.name";

    // http binding CORS default properties

    public static final String HTTP_BIND_CORS_ENABLED = "httpbind.CORS.enabled";

    public static final boolean HTTP_BIND_CORS_ENABLED_DEFAULT = true;

    public static final String HTTP_BIND_CORS_ALLOW_ORIGIN = "httpbind.CORS.domains";

    public static final String HTTP_BIND_CORS_ALLOW_ORIGIN_DEFAULT = "*";

    public static final String HTTP_BIND_CORS_ALLOW_METHODS_DEFAULT = "PROPFIND, PROPPATCH, COPY, MOVE, DELETE, MKCOL, LOCK, UNLOCK, PUT, GETLIB, VERSION-CONTROL, CHECKIN, CHECKOUT, UNCHECKOUT, REPORT, UPDATE, CANCELUPLOAD, HEAD, OPTIONS, GET, POST";

    public static final String HTTP_BIND_CORS_ALLOW_HEADERS_DEFAULT = "Overwrite, Destination, Content-Type, Depth, User-Agent, X-File-Size, X-Requested-With, If-Modified-Since, X-File-Name, Cache-Control";

    public static final String HTTP_BIND_CORS_MAX_AGE_DEFAULT = "86400";

    public static final String HTTP_BIND_REQUEST_HEADER_SIZE = "httpbind.request.header.size";

    public static final int HTTP_BIND_REQUEST_HEADER_SIZE_DEFAULT = 32768;

    public static Map<String, Boolean> HTTP_BIND_ALLOWED_ORIGINS = new HashMap<>();

    private static HttpBindManager instance = new HttpBindManager();

    private Server httpBindServer;

    private final HttpSessionManager httpSessionManager;

    /**
     * An ordered collection of all handlers (that is created to include the #extensionHandlers).
     *
     * A reference to this collection is maintained outside of the Jetty server implementation ({@link #httpBindServer})
     * as its lifecycle differs from that server: the server is recreated upon configuration changes, while the
     * collection of handlers need not be.
     *
     * This collection is ordered, which ensures that:
     * <ul>
     *     <li>The handlers providing BOSH functionality are tried first.</li>
     *     <li>Any handlers that are registered by external sources ({@link #extensionHandlers}) are tried in between.</li>
     *     <li>The 'catch-all' handler that maps to static content is tried last.</li>
     * </ul>
     *
     * This collection should be regarded as immutable. When handlers are to be added/removed dynamically, this should
     * occur in {@link #extensionHandlers}, to which a reference is stored in this list by the constructor of this class.
     */
    private final HandlerList handlerList = new HandlerList();

    /**
     * Contains all Jetty handlers that are added as an extension.
     *
     * This collection is mutable. Handlers can be added and removed at runtime.
     */
    private final HandlerCollection extensionHandlers = new HandlerCollection( true );

    /**
     * A task that, periodically, updates the 'last modified' date of all files in the Jetty 'tmp' directories. This
     * prevents operating systems from removing files that are deemed unused.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-1534">OF-1534</a>
     */
    private TempFileToucherTask tempFileToucherTask;

    public static HttpBindManager getInstance() {
        return instance;
    }

    private HttpBindManager() {
        JiveGlobals.migrateProperty(HTTP_BIND_ENABLED);
        JiveGlobals.migrateProperty(HTTP_BIND_PORT);
        JiveGlobals.migrateProperty(HTTP_BIND_SECURE_PORT);
        JiveGlobals.migrateProperty(HTTP_BIND_THREADS);
        JiveGlobals.migrateProperty(HTTP_BIND_FORWARDED);
        JiveGlobals.migrateProperty(HTTP_BIND_FORWARDED_FOR);
        JiveGlobals.migrateProperty(HTTP_BIND_FORWARDED_SERVER);
        JiveGlobals.migrateProperty(HTTP_BIND_FORWARDED_HOST);
        JiveGlobals.migrateProperty(HTTP_BIND_FORWARDED_HOST_NAME);
        JiveGlobals.migrateProperty(HTTP_BIND_CORS_ENABLED);
        JiveGlobals.migrateProperty(HTTP_BIND_CORS_ALLOW_ORIGIN);
        JiveGlobals.migrateProperty(HTTP_BIND_REQUEST_HEADER_SIZE);

        PropertyEventDispatcher.addListener( this );
        this.httpSessionManager = new HttpSessionManager();

        // setup the cache for the allowed origins
        this.setupAllowedOriginsMap();

        // Setup the default handlers. Order is important here. First, evaluate if the 'standard' handlers can be used to fulfill requests.
        this.handlerList.addHandler( createBoshHandler() );
        this.handlerList.addHandler( createWebsocketHandler() );
        this.handlerList.addHandler( createCrossDomainHandler() );

        // When standard handling does not apply, see if any of the handlers in the extension pool of handlers applies to the request.
        this.handlerList.addHandler( this.extensionHandlers );

        // When everything else fails, use the static content handler. This one should be last, as it is mapping to the root context.
        // This means that it will catch everything and prevent the invocation of later handlers.
        final Handler staticContentHandler = createStaticContentHandler();
        if ( staticContentHandler != null )
        {
            this.handlerList.addHandler( staticContentHandler );
        }
    }

    public void start() {

        if (!isHttpBindServiceEnabled()) {
            return;
        }

        // this is the number of threads allocated to each connector/port
        final int processingThreads = JiveGlobals.getIntProperty(HTTP_BIND_THREADS, HTTP_BIND_THREADS_DEFAULT);

        final QueuedThreadPool tp = new QueuedThreadPool(processingThreads);
        tp.setName("Jetty-QTP-BOSH");

        httpBindServer = new Server(tp);
        if (JMXManager.isEnabled()) {
            JMXManager jmx = JMXManager.getInstance();
            httpBindServer.addBean(jmx.getContainer());
        }

        final Connector httpConnector = createConnector( httpBindServer );
        final Connector httpsConnector = createSSLConnector( httpBindServer);

        if (httpConnector == null && httpsConnector == null) {
            httpBindServer = null;
            return;
        }
        if (httpConnector != null) {
            httpBindServer.addConnector(httpConnector);
        }
        if (httpsConnector != null) {
            httpBindServer.addConnector(httpsConnector);
        }

        httpBindServer.setHandler( handlerList );

        try {
            httpBindServer.start();

            if (handlerList.getHandlers() != null) {
                Arrays.stream(handlerList.getHandlers()).forEach(handler -> {
                    try {
                        handler.start();
                    } catch (Exception e) {
                        Log.warn("An exception occurred while trying to start handler: {}", handler, e);
                    }
                });
            }
            handlerList.start();

            if ( extensionHandlers.getHandlers() != null ) {
                Arrays.stream(extensionHandlers.getHandlers()).forEach(handler -> {
                    try {
                        handler.start();
                    } catch (Exception e) {
                        Log.warn("An exception occurred while trying to start extension handler: {}", handler, e);
                    }
                });
            }
            extensionHandlers.start();

            CertificateManager.addListener(this);

            Log.info("HTTP bind service started");
        }
        catch (Exception e) {
            Log.error("Error starting HTTP bind service", e);
        }

        if ( JiveGlobals.getBooleanProperty( "jetty.temp-file-toucher.enabled", true ) ) {
            tempFileToucherTask = new TempFileToucherTask( httpBindServer );
            final long period = JiveGlobals.getLongProperty( "jetty.temp-file-toucher.period", JiveConstants.DAY );
            TaskEngine.getInstance().schedule( tempFileToucherTask, period, period );
        }
    }

    public void stop() {
        CertificateManager.removeListener(this);

        if ( tempFileToucherTask != null ) {
            TaskEngine.getInstance().cancelScheduledTask( tempFileToucherTask );
            tempFileToucherTask = null;
        }

        if (httpBindServer != null) {
            try {
                if ( extensionHandlers.getHandlers() != null ) {
                    Arrays.stream(extensionHandlers.getHandlers()).forEach(handler -> {
                        try {
                            handler.stop();
                        } catch (Exception e) {
                            Log.warn("An exception occurred while trying to stop extension handler: {}", handler, e);
                        }
                    });
                }
                extensionHandlers.stop();

                if ( handlerList.getHandlers() != null ) {
                    Arrays.stream(handlerList.getHandlers()).forEach(handler -> {
                        try {
                            handler.stop();
                        } catch (Exception e) {
                            Log.warn("An exception occurred while trying to stop handler: {}", handler, e);
                        }
                    });
                }
                handlerList.stop();

                httpBindServer.stop();
                Log.info("HTTP bind service stopped");
            }
            catch (Exception e) {
                Log.error("Error stopping HTTP bind service", e);
            }
            httpBindServer = null;
        }
    }

    public HttpSessionManager getSessionManager() {
        return httpSessionManager;
    }

    private boolean isHttpBindServiceEnabled() {
        return JiveGlobals.getBooleanProperty(HTTP_BIND_ENABLED, HTTP_BIND_ENABLED_DEFAULT);
    }

    private Connector createConnector( final Server httpBindServer ) {
        final int port = getHttpBindUnsecurePort();
        if (port > 0) {
            HttpConfiguration httpConfig = new HttpConfiguration();
            httpConfig.setSendServerVersion( false );
            configureProxiedConnector(httpConfig);
            ServerConnector connector = new ServerConnector(httpBindServer, new HttpConnectionFactory(httpConfig));

            // Listen on a specific network interface if it has been set.
            connector.setHost(getBindInterface());
            connector.setPort(port);
            return connector;
        }
        else
        {
            return null;
        }
    }

    private Connector createSSLConnector( final Server httpBindServer ) {
        final int securePort = getHttpBindSecurePort();
        try {
            final IdentityStore identityStore = XMPPServer.getInstance().getCertificateStoreManager().getIdentityStore( ConnectionType.BOSH_C2S );

            if (securePort > 0 && identityStore.getStore().aliases().hasMoreElements() ) {
                if ( !identityStore.containsDomainCertificate( ) ) {
                    Log.warn("HTTP binding: Using certificates but they are not valid for the hosted domain");
                }

                final ConnectionManagerImpl connectionManager = ((ConnectionManagerImpl) XMPPServer.getInstance().getConnectionManager());
                final ConnectionConfiguration configuration = connectionManager.getListener( ConnectionType.BOSH_C2S, true ).generateConnectionConfiguration();
                final SslContextFactory sslContextFactory = new EncryptionArtifactFactory(configuration).getSslContextFactory();

                final HttpConfiguration httpsConfig = new HttpConfiguration();
                httpsConfig.setSecureScheme("https");
                httpsConfig.setSecurePort(securePort);
                configureProxiedConnector(httpsConfig);
                httpsConfig.addCustomizer(new SecureRequestCustomizer());
                httpsConfig.setSendServerVersion( false );

                final ServerConnector sslConnector = new ServerConnector(httpBindServer, new SslConnectionFactory(sslContextFactory, "http/1.1"), new HttpConnectionFactory(httpsConfig));
                sslConnector.setHost(getBindInterface());
                sslConnector.setPort(securePort);
                return sslConnector;
            }
        }
        catch (Exception e) {
            Log.error("Error creating SSL connector for Http bind", e);
        }

        return null;
    }

    private void configureProxiedConnector(HttpConfiguration httpConfig) {
        // Check to see if we are deployed behind a proxy
        // Refer to http://eclipse.org/jetty/documentation/current/configuring-connectors.html
        if (isXFFEnabled()) {
            ForwardedRequestCustomizer customizer = new ForwardedRequestCustomizer();
            // default: "X-Forwarded-For"
            String forwardedForHeader = getXFFHeader();
            if (forwardedForHeader != null) {
                customizer.setForwardedForHeader(forwardedForHeader);
            }
            // default: "X-Forwarded-Server"
            String forwardedServerHeader = getXFFServerHeader();
            if (forwardedServerHeader != null) {
                customizer.setForwardedServerHeader(forwardedServerHeader);
            }
            // default: "X-Forwarded-Host"
            String forwardedHostHeader = getXFFHostHeader();
            if (forwardedHostHeader != null) {
                customizer.setForwardedHostHeader(forwardedHostHeader);
            }
            // default: none
            String hostName = getXFFHostName();
            if (hostName != null) {
                customizer.setHostHeader(hostName);
            }

            httpConfig.addCustomizer(customizer);
        }
        httpConfig.setRequestHeaderSize(JiveGlobals.getIntProperty(HTTP_BIND_REQUEST_HEADER_SIZE, HTTP_BIND_REQUEST_HEADER_SIZE_DEFAULT));
   }

    private String getBindInterface() {
        String interfaceName = JiveGlobals.getXMLProperty("network.interface");
        String bindInterface = null;
        if (interfaceName != null) {
            if (interfaceName.trim().length() > 0) {
                bindInterface = interfaceName;
            }
        }
        return bindInterface;
    }

    /**
     * Returns true if the HTTP binding server is currently enabled.
     *
     * @return true if the HTTP binding server is currently enabled.
     */
    public boolean isHttpBindEnabled() {
        return httpBindServer != null && httpBindServer.isRunning();
    }

    /**
     * Returns true if a listener on the HTTP binding port is running.
     *
     * @return true if a listener on the HTTP binding port is running.
     */
    public boolean isHttpBindActive()
    {
        if ( isHttpBindEnabled() )
        {
            final int configuredPort = getHttpBindUnsecurePort();
            for ( final Connector connector : httpBindServer.getConnectors() )
            {
                if ( !( connector instanceof ServerConnector ) )
                {
                    continue;
                }
                final int activePort = ( (ServerConnector) connector ).getLocalPort();

                if ( activePort == configuredPort )
                {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns true if a listener on the HTTPS binding port is running.
     *
     * @return true if a listener on the HTTPS binding port is running.
     */
    public boolean isHttpsBindActive()
    {
        if ( isHttpBindEnabled() )
        {
            final int configuredPort = getHttpBindSecurePort();
            for ( final Connector connector : httpBindServer.getConnectors() )
            {
                if ( !( connector instanceof ServerConnector ) )
                {
                    continue;
                }
                final int activePort = ( (ServerConnector) connector ).getLocalPort();

                if ( activePort == configuredPort )
                {
                    return true;
                }
            }
        }
        return false;
    }

    public String getHttpBindUnsecureAddress() {
        return "http://" + XMPPServer.getInstance().getServerInfo().getHostname() + ":" + getHttpBindUnsecurePort() + "/http-bind/";
    }

    public String getHttpBindSecureAddress() {
        return "https://" + XMPPServer.getInstance().getServerInfo().getHostname() + ":" + getHttpBindSecurePort() + "/http-bind/";
    }

    public String getJavaScriptUrl() {
        return "http://" + XMPPServer.getInstance().getServerInfo().getHostname() + ":" + getHttpBindUnsecurePort() + "/scripts/";
    }

    // http binding CORS support start

    private void setupAllowedOriginsMap() {
        final String originString = getCORSAllowOrigin();
        if (!originString.equals(HTTP_BIND_CORS_ALLOW_ORIGIN_DEFAULT)) {
            String[] origins = originString.split(",");
            // reset the cache
            HTTP_BIND_ALLOWED_ORIGINS.clear();
            for (String str : origins) {
                HTTP_BIND_ALLOWED_ORIGINS.put(str, true);
            }
        }
    }

    public boolean isCORSEnabled() {
        return JiveGlobals.getBooleanProperty(HTTP_BIND_CORS_ENABLED, HTTP_BIND_CORS_ENABLED_DEFAULT);
    }

    public void setCORSEnabled(Boolean value) {
        if (value != null)
            JiveGlobals.setProperty(HTTP_BIND_CORS_ENABLED, String.valueOf(value));
    }

    public String getCORSAllowOrigin() {
        return JiveGlobals.getProperty(HTTP_BIND_CORS_ALLOW_ORIGIN , HTTP_BIND_CORS_ALLOW_ORIGIN_DEFAULT);
    }

    public void setCORSAllowOrigin(String origins) {
        if (origins == null || origins.trim().length() == 0)
             origins = HTTP_BIND_CORS_ALLOW_ORIGIN_DEFAULT;
        else {
            origins = origins.replaceAll("\\s+", "");
        }
        JiveGlobals.setProperty(HTTP_BIND_CORS_ALLOW_ORIGIN, origins);
        setupAllowedOriginsMap();
    }

    public boolean isAllOriginsAllowed() {
        return HTTP_BIND_CORS_ALLOW_ORIGIN_DEFAULT.equals( getCORSAllowOrigin() );
    }

    public boolean isThisOriginAllowed(String origin) {
        return isAllOriginsAllowed() || HTTP_BIND_ALLOWED_ORIGINS.get(origin) != null;
    }

    // http binding CORS support end

    public boolean isXFFEnabled() {
        return JiveGlobals.getBooleanProperty(HTTP_BIND_FORWARDED, false);
    }

    public void setXFFEnabled(boolean enabled) {
        JiveGlobals.setProperty(HTTP_BIND_FORWARDED, String.valueOf(enabled));
    }

    public String getXFFHeader() {
        return JiveGlobals.getProperty(HTTP_BIND_FORWARDED_FOR);
    }

    public void setXFFHeader(String header) {
        if (header == null || header.trim().length() == 0) {
            JiveGlobals.deleteProperty(HTTP_BIND_FORWARDED_FOR);
        } else {
            JiveGlobals.setProperty(HTTP_BIND_FORWARDED_FOR, header);
        }
    }

    public String getXFFServerHeader() {
        return JiveGlobals.getProperty(HTTP_BIND_FORWARDED_SERVER);
    }

    public void setXFFServerHeader(String header) {
        if (header == null || header.trim().length() == 0) {
            JiveGlobals.deleteProperty(HTTP_BIND_FORWARDED_SERVER);
        } else {
            JiveGlobals.setProperty(HTTP_BIND_FORWARDED_SERVER, header);
        }
    }

    public String getXFFHostHeader() {
        return JiveGlobals.getProperty(HTTP_BIND_FORWARDED_HOST);
    }

    public void setXFFHostHeader(String header) {
        if (header == null || header.trim().length() == 0) {
            JiveGlobals.deleteProperty(HTTP_BIND_FORWARDED_HOST);
        } else {
            JiveGlobals.setProperty(HTTP_BIND_FORWARDED_HOST, header);
        }
    }

    public String getXFFHostName() {
        return JiveGlobals.getProperty(HTTP_BIND_FORWARDED_HOST_NAME);
    }

    public void setXFFHostName(String name) {
        if (name == null || name.trim().length() == 0) {
            JiveGlobals.deleteProperty(HTTP_BIND_FORWARDED_HOST_NAME);
        } else {
            JiveGlobals.setProperty(HTTP_BIND_FORWARDED_HOST_NAME, name);
        }
    }

    public void setHttpBindEnabled(boolean isEnabled) {
        JiveGlobals.setProperty(HTTP_BIND_ENABLED, String.valueOf(isEnabled));
    }

    /**
     * Set the ports on which the HTTP binding service will be running.
     *
     * @param unsecurePort the unsecured connection port which clients can connect to.
     * @param securePort the secured connection port which clients can connect to.
     * @throws Exception when there is an error configuring the HTTP binding ports.
     */
    public void setHttpBindPorts(int unsecurePort, int securePort) throws Exception {
        if (unsecurePort != HTTP_BIND_PORT_DEFAULT) {
            JiveGlobals.setProperty(HTTP_BIND_PORT, String.valueOf(unsecurePort));
        }
        else {
            JiveGlobals.deleteProperty(HTTP_BIND_PORT);
        }
        if (securePort != HTTP_BIND_SECURE_PORT_DEFAULT) {
            JiveGlobals.setProperty(HTTP_BIND_SECURE_PORT, String.valueOf(securePort));
        }
        else {
            JiveGlobals.deleteProperty(HTTP_BIND_SECURE_PORT);
        }
    }

    /**
     * Creates a Jetty context handler that can be used to expose BOSH (HTTP-Bind) functionality.
     *
     * Note that an invocation of this method will not register the handler (and thus make the related functionality
     * available to the end user). Instead, the created handler is returned by this method, and will need to be
     * registered with the embedded Jetty webserver by the caller.
     *
     * @return A Jetty context handler (never null).
     */
    protected Handler createBoshHandler()
    {
        final int options;
        if(isHttpCompressionEnabled()) {
            options = ServletContextHandler.SESSIONS | ServletContextHandler.GZIP;
        } else {
            options = ServletContextHandler.SESSIONS;
        }
        final ServletContextHandler context = new ServletContextHandler( null, "/http-bind", options );

        // Ensure the JSP engine is initialized correctly (in order to be able to cope with Tomcat/Jasper precompiled JSPs).
        final List<ContainerInitializer> initializers = new ArrayList<>();
        initializers.add( new ContainerInitializer( new JasperInitializer(), null ) );
        context.setAttribute( "org.eclipse.jetty.containerInitializers", initializers );
        context.setAttribute( InstanceManager.class.getName(), new SimpleInstanceManager() );

        // Generic configuration of the context.
        context.setAllowNullPathInfo( true );

        // Add the functionality-providers.
        context.addServlet( new ServletHolder( new HttpBindServlet() ), "/*" );

        // Add compression filter when needed.
        if (isHttpCompressionEnabled()) {
            final GzipHandler gzipHandler = context.getGzipHandler();
            gzipHandler.addIncludedPaths("/*");
            gzipHandler.addIncludedMethods(HttpMethod.POST.asString());
        }

        return context;
    }

    /**
     * Creates a Jetty context handler that can be used to expose Websocket functionality.
     *
     * Note that an invocation of this method will not register the handler (and thus make the related functionality
     * available to the end user). Instead, the created handler is returned by this method, and will need to be
     * registered with the embedded Jetty webserver by the caller.
     *
     * @return A Jetty context handler (never null).
     */
    protected Handler createWebsocketHandler()
    {
        final ServletContextHandler context = new ServletContextHandler( null, "/ws", ServletContextHandler.SESSIONS );
        context.setAllowNullPathInfo(true);
        // Add the functionality-providers.
        context.addServlet( new ServletHolder( new OpenfireWebSocketServlet() ), "/*" );

        return context;
    }

    // NOTE: enabled by default
    private boolean isHttpCompressionEnabled() {
        final ConnectionManagerImpl connectionManager = ((ConnectionManagerImpl) XMPPServer.getInstance().getConnectionManager());
        final ConnectionConfiguration configuration = connectionManager.getListener( ConnectionType.BOSH_C2S, true ).generateConnectionConfiguration();
        return configuration.getCompressionPolicy() == null || configuration.getCompressionPolicy().equals( Connection.CompressionPolicy.optional );
    }

    /**
     * Creates a Jetty context handler that can be used to expose the cross-domain functionality as implemented by
     * {@link FlashCrossDomainServlet}.
     *
     * Note that an invocation of this method will not register the handler (and thus make the related functionality
     * available to the end user). Instead, the created handler is returned by this method, and will need to be
     * registered with the embedded Jetty webserver by the caller.
     *
     * @return A Jetty context handler (never null).
     */
    protected Handler createCrossDomainHandler()
    {
        final ServletContextHandler context = new ServletContextHandler( null, "/crossdomain.xml", ServletContextHandler.SESSIONS );

        // Ensure the JSP engine is initialized correctly (in order to be able to cope with Tomcat/Jasper precompiled JSPs).
        final List<ContainerInitializer> initializers = new ArrayList<>();
        initializers.add( new ContainerInitializer( new JasperInitializer(), null ) );
        context.setAttribute( "org.eclipse.jetty.containerInitializers", initializers );
        context.setAttribute( InstanceManager.class.getName(), new SimpleInstanceManager() );

        // Generic configuration of the context.
        context.setAllowNullPathInfo( true );

        // Add the functionality-providers.
        context.addServlet( new ServletHolder( new FlashCrossDomainServlet() ), "" );

        return context;
    }

    /**
     * Creates a Jetty context handler that can be used to expose static files.
     *
     * Note that an invocation of this method will not register the handler (and thus make the related functionality
     * available to the end user). Instead, the created handler is returned by this method, and will need to be
     * registered with the embedded Jetty webserver by the caller.
     *
     * @return A Jetty context handler, or null when the static content could not be accessed.
     */
    protected Handler createStaticContentHandler()
    {
        final File spankDirectory = new File( JiveGlobals.getHomeDirectory() + File.separator + "resources" + File.separator + "spank" );
        if ( spankDirectory.exists() )
        {
            if ( spankDirectory.canRead() )
            {
                final WebAppContext context = new WebAppContext( null, spankDirectory.getPath(), "/" );
                context.setWelcomeFiles( new String[] { "index.html" } );

                return context;
            }
            else
            {
                Log.warn( "Openfire cannot read the directory: " + spankDirectory );
            }
        }
        return null;
    }

    /**
     * Adds a Jetty handler to be added to the embedded web server that is used to expose BOSH (HTTP-bind)
     * functionality.
     *
     * @param handler The handler (cannot be null).
     */
    public void addJettyHandler( Handler handler )
    {
        if ( handler == null )
        {
            throw new IllegalArgumentException( "Argument 'handler' cannot be null." );
        }

        extensionHandlers.addHandler( handler );

        if ( !handler.isStarted() && extensionHandlers.isStarted() )
        {
            try
            {
                handler.start();
            }
            catch ( Exception e )
            {
                Log.warn( "Unable to start handler {}", handler, e );
            }
        }
    }

    /**
     * Removes a Jetty handler to be added to the embedded web server that is used to expose BOSH (HTTP-bind)
     * functionality.
     *
     * Removing a handler, even when null, or non-existing, might have side-effects as introduced by the Jetty
     * implementation. At the time of writing, Jetty will re
     *
     * @param handler The handler (should not be null).
     */
    public void removeJettyHandler( Handler handler )
    {
        if (handler instanceof WebAppContext) {
            // A work-around of the Jetty bug described at https://github.com/eclipse/jetty.project/issues/1425
            // NOTE: According to some comments on WebAppLoaderFix, this may stop working on Java 9.
            // Hopefully the Jetty team will have fixed the underlying bug by then
            WebAppLoaderFix.checkAndClose(((WebAppContext) handler).getClassLoader());
        }
        extensionHandlers.removeHandler( handler );
        if ( handler.isStarted() )
        {
            try
            {
                handler.stop();
            }
            catch ( Exception e )
            {
                Log.warn( "Unable to stop the handler that was removed: {}", handler, e );
            }
        }

    }

    private void doEnableHttpBind(boolean shouldEnable) {
        if (shouldEnable && httpBindServer == null) {
            start();
        }
        else if (!shouldEnable && httpBindServer != null) {
            stop();
        }
    }

    /**
     * Returns the HTTP binding port which does not use SSL.
     *
     * @return the HTTP binding port which does not use SSL.
     */
    public int getHttpBindUnsecurePort() {
        return JiveGlobals.getIntProperty(HTTP_BIND_PORT, HTTP_BIND_PORT_DEFAULT);
    }

    /**
     * Returns the HTTP binding port which uses SSL.
     *
     * @return the HTTP binding port which uses SSL.
     */
    public int getHttpBindSecurePort() {
        return JiveGlobals.getIntProperty(HTTP_BIND_SECURE_PORT, HTTP_BIND_SECURE_PORT_DEFAULT);
    }

    /**
     * Returns true if script syntax is enabled. Script syntax allows BOSH to be used in
     * environments where clients may be restricted to using a particular server. Instead of using
     * standard HTTP Post requests to transmit data,  HTTP Get requests are used.
     *
     * @return true if script syntax is enabled.
     * @see <a href="http://www.xmpp.org/extensions/xep-0124.html#script">BOSH: Alternative Script
     * Syntax</a>
     */
    public boolean isScriptSyntaxEnabled() {
        return JiveGlobals.getBooleanProperty("xmpp.httpbind.scriptSyntax.enabled", false);
    }

    /**
     * Enables or disables script syntax.
     *
     * @param isEnabled true to enable script syntax and false to disable it.
     * @see #isScriptSyntaxEnabled()
     * @see <a href="http://www.xmpp.org/extensions/xep-0124.html#script">BOSH: Alternative Script
     * Syntax</a>
     */
    public void setScriptSyntaxEnabled(boolean isEnabled) {
        final String property = "xmpp.httpbind.scriptSyntax.enabled";
        if(!isEnabled) {
            JiveGlobals.deleteProperty(property);
        }
        else {
            JiveGlobals.setProperty(property, String.valueOf(isEnabled));
        }
    }

    private synchronized void restartServer() {
        stop();
        start();
    }

    @Override
    public void propertySet(String property, Map<String, Object> params) {
        if (property.equalsIgnoreCase(HTTP_BIND_ENABLED)) {
            doEnableHttpBind(Boolean.valueOf(params.get("value").toString()));
        }
        else if (property.equalsIgnoreCase(HTTP_BIND_PORT)) {
            try {
                Integer.valueOf(params.get("value").toString());
            }
            catch (NumberFormatException ne) {
                JiveGlobals.deleteProperty(HTTP_BIND_PORT);
                return;
            }
            restartServer();
        }
        else if (property.equalsIgnoreCase(HTTP_BIND_SECURE_PORT)) {
            try {
                Integer.valueOf(params.get("value").toString());
            }
            catch (NumberFormatException ne) {
                JiveGlobals.deleteProperty(HTTP_BIND_SECURE_PORT);
                return;
            }
            restartServer();
        }
        else if (HTTP_BIND_AUTH_PER_CLIENTCERT_POLICY.equalsIgnoreCase( property )) {
            restartServer();
        }
    }

    @Override
    public void propertyDeleted(String property, Map<String, Object> params) {
        if (property.equalsIgnoreCase(HTTP_BIND_ENABLED)) {
            doEnableHttpBind(HTTP_BIND_ENABLED_DEFAULT);
        }
        else if (property.equalsIgnoreCase(HTTP_BIND_PORT)) {
            restartServer();
        }
        else if (property.equalsIgnoreCase(HTTP_BIND_SECURE_PORT)) {
            restartServer();
        }
        else if (HTTP_BIND_AUTH_PER_CLIENTCERT_POLICY.equalsIgnoreCase( property )) {
            restartServer();
        }
    }

    @Override
    public void xmlPropertySet(String property, Map<String, Object> params) {
    }

    @Override
    public void xmlPropertyDeleted(String property, Map<String, Object> params) {
    }

    @Override
    public void storeContentChanged( CertificateStore store )
    {
        restartServer();
    }
}
