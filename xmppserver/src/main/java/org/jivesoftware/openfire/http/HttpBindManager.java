/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2023 Ignite Realtime Foundation. All rights reserved.
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

import org.apache.jasper.servlet.JasperInitializer;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.SimpleInstanceManager;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.plus.annotation.ContainerInitializer;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.ConnectionManager;
import org.jivesoftware.openfire.JMXManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.keystore.CertificateStore;
import org.jivesoftware.openfire.keystore.IdentityStore;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.openfire.spi.ConnectionType;
import org.jivesoftware.openfire.spi.EncryptionArtifactFactory;
import org.jivesoftware.openfire.websocket.OpenfireWebSocketServlet;
import org.jivesoftware.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.DispatcherType;
import java.io.File;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Responsible for making available BOSH (functionality to the outside world, using an embedded web server.
 */
public final class HttpBindManager implements CertificateEventListener {

    private static final Logger Log = LoggerFactory.getLogger(HttpBindManager.class);

    /**
     * Enable / disable logging of BOSH requests and responses.
     */
    public static final SystemProperty<Boolean> LOG_HTTPBIND_ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("log.httpbind.enabled")
        .setDynamic(true)
        .setDefaultValue(false)
        .build();

    /**
     * Enable / disable BOSH (HTTP Binding) functionality.
     */
    public static final SystemProperty<Boolean> HTTP_BIND_ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("httpbind.enabled")
        .setDynamic(true)
        .setDefaultValue(true)
        .addListener(HttpBindManager::restart)
        .build();

    /**
     * TCP port on which the non-encrypted (HTTP) BOSH endpoint is exposed.
     */
    public static final SystemProperty<Integer> HTTP_BIND_PORT = SystemProperty.Builder.ofType(Integer.class)
        .setKey("httpbind.port.plain")
        .setDynamic(true)
        .setDefaultValue(7070)
        .addListener(HttpBindManager::restart)
        .build();

    /**
     * TCP port on which the encrypted (HTTPS) BOSH endpoint is exposed.
     */
    public static final SystemProperty<Integer> HTTP_BIND_SECURE_PORT = SystemProperty.Builder.ofType(Integer.class)
        .setKey("httpbind.port.secure")
        .setDynamic(true)
        .setDefaultValue(7443)
        .addListener(HttpBindManager::restart)
        .build();

    /**
     * Minimum amount of threads in the thread pool to perform the network IO related to BOSH traffic.
     *
     * Note: Apart from the network-IO threads configured in this property, the server also uses a thread pool for
     * processing the inbound data (as configured in ({@link HttpSessionManager#MAX_POOL_SIZE}). BOSH
     * installations expecting heavy loads may want to allocate additional threads to this worker pool to ensure timely
     * processing of data
     */
    public static final SystemProperty<Integer> HTTP_BIND_THREADS_MIN = SystemProperty.Builder.ofType(Integer.class)
        .setKey("httpbind.client.processing.threads-min")
        .setDynamic(false) // TODO This can easily be made dynamic with <tt>.addListener(HttpBindManager.getInstance()::restartServer)</tt>. Existing implementation was not dynamic. Should it?
        .setDefaultValue(8)
        .setMinValue(1)
        .build();

    /**
     * Maximum amount of threads in the thread pool to perform the network IO related to BOSH traffic.
     *
     * Note: Apart from the network-IO threads configured in this property, the server also uses a thread pool for
     * processing the inbound data (as configured in ({@link HttpSessionManager#MAX_POOL_SIZE}). BOSH
     * installations expecting heavy loads may want to allocate additional threads to this worker pool to ensure timely
     * processing of data
     */
    public static final SystemProperty<Integer> HTTP_BIND_THREADS = SystemProperty.Builder.ofType(Integer.class)
        .setKey("httpbind.client.processing.threads")
        .setDynamic(false) // TODO This can easily be made dynamic with <tt>.addListener(HttpBindManager.getInstance()::restartServer)</tt>. Existing implementation was not dynamic. Should it?
        .setDefaultValue(200)
        .setMinValue(1)
        .build();

    /**
     * Amount of time after which idle, surplus threads are removed from the thread pool to perform the network IO
     * related to BOSH traffic.
     *
     * Note: Apart from the network-IO threads configured in this property, the server also uses a thread pool for
     * processing the inbound data (as configured in ({@link HttpSessionManager#INACTIVITY_TIMEOUT}). BOSH
     * installations expecting heavy loads may want to allocate additional threads to this worker pool to ensure timely
     * processing of data
     */
    public static final SystemProperty<Duration> HTTP_BIND_THREADS_TIMEOUT = SystemProperty.Builder.ofType(Duration.class)
        .setKey("httpbind.client.processing.threads-timeout")
        .setDynamic(false) // TODO This can easily be made dynamic with <tt>.addListener(HttpBindManager.getInstance()::restartServer)</tt>. Existing implementation was not dynamic. Should it?
        .setDefaultValue(Duration.ofSeconds(60))
        .setChronoUnit(ChronoUnit.MILLIS)
        .setMaxValue(Duration.ofMillis(Integer.MAX_VALUE)) // Jetty takes an int value, not a long.
        .build();

    /**
     * The TLS 'mutual authentication' policy that is applied to the BOSH endpoint.
     */
    // Ideally, this would be a property of the Connection.ClientAuth enum, but we need to be able to set a 'null' default value (as that will cause a dynamic default to be calculated). Using the dynamic default calculation in this SystemProperty does not work, as it throws NullPointerExceptions during server startup.
    public static final SystemProperty<String> HTTP_BIND_AUTH_PER_CLIENTCERT_POLICY = SystemProperty.Builder.ofType(String.class)
        .setKey("httpbind.client.cert.policy")
        .setDynamic(true)
        .setDefaultValue(null)
        .addListener(HttpBindManager::restart)
        .build();

    /**
     * Enable / Disable parsing a 'X-Forwarded-For' style HTTP header of BOSH requests.
     */
    public static final SystemProperty<Boolean> HTTP_BIND_FORWARDED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("httpbind.forwarded.enabled")
        .setDynamic(false) // TODO This can easily be made dynamic with <tt>.addListener(HttpBindManager.getInstance()::restartServer)</tt>. Existing implementation was not dynamic. Should it?
        .setDefaultValue(false)
        .build();

    /**
     * The HTTP header name for 'forwarded for'
     */
    public static final SystemProperty<String> HTTP_BIND_FORWARDED_FOR = SystemProperty.Builder.ofType(String.class)
        .setKey("httpbind.forwarded.for.header")
        .setDynamic(false) // TODO This can easily be made dynamic with <tt>.addListener(HttpBindManager.getInstance()::restartServer)</tt>. Existing implementation was not dynamic. Should it?
        .setDefaultValue(HttpHeader.X_FORWARDED_FOR.toString())
        .build();

    /**
     * The HTTP header name for 'forwarded server'.
     */
    public static final SystemProperty<String> HTTP_BIND_FORWARDED_SERVER = SystemProperty.Builder.ofType(String.class)
        .setKey("httpbind.forwarded.server.header")
        .setDynamic(false) // TODO This can easily be made dynamic with <tt>.addListener(HttpBindManager.getInstance()::restartServer)</tt>. Existing implementation was not dynamic. Should it?
        .setDefaultValue(HttpHeader.X_FORWARDED_SERVER.toString())
        .build();

    /**
     * The HTTP header name for 'forwarded hosts'.
     */
    public static final SystemProperty<String> HTTP_BIND_FORWARDED_HOST = SystemProperty.Builder.ofType(String.class)
        .setKey("httpbind.forwarded.host.header")
        .setDynamic(false) // TODO This can easily be made dynamic with <tt>.addListener(HttpBindManager.getInstance()::restartServer)</tt>. Existing implementation was not dynamic. Should it?
        .setDefaultValue(HttpHeader.X_FORWARDED_HOST.toString())
        .build();

    /**
     * Sets a forced valued for the host header.
     */
    public static final SystemProperty<String> HTTP_BIND_FORWARDED_HOST_NAME = SystemProperty.Builder.ofType(String.class)
        .setKey("httpbind.forwarded.host.name")
        .setDynamic(false) // TODO This can easily be made dynamic with <tt>.addListener(HttpBindManager.getInstance()::restartServer)</tt>. Existing implementation was not dynamic. Should it?
        .setDefaultValue(null)
        .build();

    // http binding CORS default properties

    /**
     * Enable / Disable support for Cross-Origin Resource Sharing (CORS) headers in the BOSH endpoint.
     */
    public static final SystemProperty<Boolean> HTTP_BIND_CORS_ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("httpbind.CORS.enabled")
        .setDynamic(false)  // TODO This can easily be made dynamic with <tt>.addListener(HttpBindManager.getInstance()::restartServer)</tt>. Existing implementation was not dynamic. Should it?
        .setDefaultValue(true)
        .build();

    /**
     * The Cross-Origin Resource Sharing (CORS) header value that represents the 'allow all orgins' state.
     */
    public static final String HTTP_BIND_CORS_ALLOW_ORIGIN_ALL = "*";

    /**
     * The domain names that are accepted as values for the CORS 'Origin' header in the BOSH endpoint.
     */
    public static final SystemProperty<Set<String>> HTTP_BIND_ALLOWED_ORIGINS = SystemProperty.Builder.ofType(Set.class)
        .setKey("httpbind.CORS.domains")
        .setDynamic(true)
        .setDefaultValue(Collections.singleton(HTTP_BIND_CORS_ALLOW_ORIGIN_ALL))
        .buildSet(String.class);

    /**
     * Enable / Disable adding a 'Content-Security-Policy' HTTP header to the response to requests made against the BOSH endpoint.
     */
    public static final SystemProperty<Boolean> HTTP_BIND_CONTENT_SECURITY_POLICY_ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("httpbind.CSP.enabled")
        .setDynamic(true)
        .setDefaultValue(true)
        .build();

    /**
     * The header value when adding a 'Content-Security-Policy' HTTP header to the response to requests made against the BOSH endpoint.
     */
    public static final SystemProperty<String> HTTP_BIND_CONTENT_SECURITY_POLICY_RESPONSEVALUE = SystemProperty.Builder.ofType(String.class)
        .setKey("httpbind.CSP.responsevalue")
        .setDynamic(true)
        .setDefaultValue("default-src 'none'; style-src 'self' 'unsafe-inline'; connect-src 'self'; base-uri 'self'; form-action 'none';")
        .build();

    /**
     * The HTTP methods that are accepted in the BOSH endpoint.
     */
    public static final SystemProperty<Set<String>> HTTP_BIND_CORS_ALLOW_METHODS = SystemProperty.Builder.ofType(Set.class)
        .setKey("httpbind.CORS.methods")
        .setDynamic(true)
        .setDefaultValue(new HashSet<>(Arrays.asList("PROPFIND", "PROPPATCH", "COPY", "MOVE", "DELETE", "MKCOL", "LOCK", "UNLOCK", "PUT", "GETLIB", "VERSION-CONTROL", "CHECKIN", "CHECKOUT", "UNCHECKOUT", "REPORT", "UPDATE", "CANCELUPLOAD", "HEAD", "OPTIONS", "GET", "POST")) )
        .buildSet(String.class);

    /**
     * The name of HTTP headers that are accepted in requests to the BOSH endpoint.
     */
    public static final SystemProperty<Set<String>> HTTP_BIND_CORS_ALLOW_HEADERS = SystemProperty.Builder.ofType(Set.class)
        .setKey("httpbind.CORS.headers")
        .setDynamic(true)
        .setDefaultValue(new HashSet<>(Arrays.asList("Overwrite", "Destination", "Content-Type", "Depth", "User-Agent", "X-File-Size", "X-Requested-With", "If-Modified-Since", "X-File-Name", "Cache-Control")) )
        .buildSet(String.class);

    /**
     * How long the results of a preflight request (that is the information contained in the Access-Control-Allow-Methods and Access-Control-Allow-Headers headers) can be cached.
     */
    public static final SystemProperty<Duration> HTTP_BIND_CORS_MAX_AGE = SystemProperty.Builder.ofType(Duration.class)
        .setKey("httpbind.CORS.max_age")
        .setDynamic(true)
        .setDefaultValue(Duration.ofDays(1))
        .setChronoUnit(ChronoUnit.SECONDS)
        .build();

    /**
     * the maximum size in bytes of request headers in the BOSH endpoint. Larger headers will allow for more and/or
     * larger cookies plus larger form content encoded in a URL. However, larger headers consume more memory and can
     * make a server more vulnerable to denial of service attacks.
     */
    public static final SystemProperty<Integer> HTTP_BIND_REQUEST_HEADER_SIZE = SystemProperty.Builder.ofType(Integer.class)
        .setKey("httpbind.request.header.size")
        .setDynamic(true)
        .setDefaultValue(32768)
        .build();

    private static HttpBindManager instance;

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

    public static synchronized HttpBindManager getInstance() {
        if (instance == null) {
            instance = new HttpBindManager();
        }
        return instance;
    }

    private HttpBindManager() {

        this.httpSessionManager = new HttpSessionManager();

        // Setup the default handlers. Order is important here. First, evaluate if the 'standard' handlers can be used to fulfill requests.
        this.handlerList.addHandler( createBoshHandler() );
        this.handlerList.addHandler( createWebsocketHandler() );

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

        if (!HTTP_BIND_ENABLED.getValue()) {
            return;
        }

        final QueuedThreadPool tp = new QueuedThreadPool(HTTP_BIND_THREADS.getValue(), HTTP_BIND_THREADS_MIN.getValue(), (int) HTTP_BIND_THREADS_TIMEOUT.getValue().toMillis());
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
            final Duration period = Duration.ofMillis(JiveGlobals.getLongProperty( "jetty.temp-file-toucher.period", Duration.ofDays(1).toMillis() ));
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

    private Connector createConnector( final Server httpBindServer ) {
        final int port = HTTP_BIND_PORT.getValue();
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
        final int securePort = HTTP_BIND_SECURE_PORT.getValue();
        try {
            final IdentityStore identityStore = XMPPServer.getInstance().getCertificateStoreManager().getIdentityStore( ConnectionType.BOSH_C2S );

            if (securePort > 0 && identityStore.getStore().aliases().hasMoreElements() ) {
                if ( !identityStore.containsDomainCertificate( ) ) {
                    Log.warn("HTTP binding: Using certificates but they are not valid for the hosted domain");
                }

                final ConnectionManager connectionManager = XMPPServer.getInstance().getConnectionManager();
                final ConnectionConfiguration configuration = connectionManager.getListener( ConnectionType.BOSH_C2S, true ).generateConnectionConfiguration();
                final SslContextFactory.Server sslContextFactory = new EncryptionArtifactFactory(configuration).getSslContextFactory();

                final HttpConfiguration httpsConfig = new HttpConfiguration();
                httpsConfig.setSecureScheme("https");
                httpsConfig.setSecurePort(securePort);
                SecureRequestCustomizer secureRequestCustomizer = new SecureRequestCustomizer();
                secureRequestCustomizer.setSniHostCheck(sslContextFactory.isSniRequired());
                httpsConfig.addCustomizer( secureRequestCustomizer );
                configureProxiedConnector(httpsConfig);
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
        if (HTTP_BIND_FORWARDED.getValue()) {
            ForwardedRequestCustomizer customizer = new ForwardedRequestCustomizer();
            // default: "X-Forwarded-For"
            String forwardedForHeader = HTTP_BIND_FORWARDED_FOR.getValue();
            if (forwardedForHeader != null) {
                customizer.setForwardedForHeader(forwardedForHeader);
            }
            // default: "X-Forwarded-Server"
            String forwardedServerHeader = HTTP_BIND_FORWARDED_SERVER.getValue();
            if (forwardedServerHeader != null) {
                customizer.setForwardedServerHeader(forwardedServerHeader);
            }
            // default: "X-Forwarded-Host"
            String forwardedHostHeader = HTTP_BIND_FORWARDED_HOST.getValue();
            if (forwardedHostHeader != null) {
                customizer.setForwardedHostHeader(forwardedHostHeader);
            }
            // default: none
            String hostName = HTTP_BIND_FORWARDED_HOST_NAME.getValue();
            if (hostName != null) {
                customizer.setHostHeader(hostName);
            }

            httpConfig.addCustomizer(customizer);
        }
        httpConfig.setRequestHeaderSize(HTTP_BIND_REQUEST_HEADER_SIZE.getValue());
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
            final int configuredPort = HTTP_BIND_PORT.getValue();
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
            final int configuredPort = HTTP_BIND_SECURE_PORT.getValue();
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

    public String getWebsocketUnsecureAddress() {
        return "ws://" + XMPPServer.getInstance().getServerInfo().getHostname() + ":" + HTTP_BIND_PORT.getValue() + "/ws/";
    }

    public String getWebsocketSecureAddress() {
        return "wss://" + XMPPServer.getInstance().getServerInfo().getHostname() + ":" + HTTP_BIND_SECURE_PORT.getValue() + "/ws/";
    }

    public String getHttpBindUnsecureAddress() {
        return "http://" + XMPPServer.getInstance().getServerInfo().getHostname() + ":" + HTTP_BIND_PORT.getValue() + "/http-bind/";
    }

    public String getHttpBindSecureAddress() {
        return "https://" + XMPPServer.getInstance().getServerInfo().getHostname() + ":" + HTTP_BIND_SECURE_PORT.getValue() + "/http-bind/";
    }

    public String getJavaScriptUrl() {
        return "http://" + XMPPServer.getInstance().getServerInfo().getHostname() + ":" + HTTP_BIND_PORT.getValue() + "/scripts/";
    }

    public boolean isAllOriginsAllowed() {
        return HTTP_BIND_ALLOWED_ORIGINS.getValue().contains(HTTP_BIND_CORS_ALLOW_ORIGIN_ALL);
    }

    public boolean isThisOriginAllowed(String origin) {
        return isAllOriginsAllowed() || HTTP_BIND_ALLOWED_ORIGINS.getValue().stream().anyMatch(o -> o.equalsIgnoreCase(origin));
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
        final int options = ServletContextHandler.SESSIONS;
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
            final GzipHandler gzipHandler = new GzipHandler();
            gzipHandler.addIncludedPaths("/*");
            gzipHandler.addIncludedMethods(HttpMethod.POST.asString());
            context.insertHandler(gzipHandler);
        }

        // Add CSP headers for all HTTP responses (errors, etc.)
        context.addFilter(HttpBindContentSecurityPolicyFilter.class, "/*", null);

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
        JettyWebSocketServletContainerInitializer.configure(context, null);

        // Add CSP headers for all HTTP responses (errors, etc.)
        context.addFilter(HttpBindContentSecurityPolicyFilter.class, "/*", null);

        return context;
    }

    // NOTE: enabled by default
    private boolean isHttpCompressionEnabled() {
        final ConnectionManager connectionManager = XMPPServer.getInstance().getConnectionManager();
        final ConnectionConfiguration configuration = connectionManager.getListener( ConnectionType.BOSH_C2S, true ).generateConnectionConfiguration();
        return configuration.getCompressionPolicy() == null || configuration.getCompressionPolicy().equals( Connection.CompressionPolicy.optional );
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
        final File spankDirectory = new File( JiveGlobals.getHomePath() + File.separator + "resources" + File.separator + "spank" );
        if ( spankDirectory.exists() )
        {
            if ( spankDirectory.canRead() )
            {
                final WebAppContext context = new WebAppContext( null, spankDirectory.getPath(), "/" );
                context.setWelcomeFiles( new String[] { "index.html" } );

                // Add CSP headers for all HTTP responses (errors, etc.)
                context.addFilter(HttpBindContentSecurityPolicyFilter.class, "/*", EnumSet.allOf(DispatcherType.class));

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

    /**
     * Static reference for {@link #restartServer()} that can be used as a listener of a {@link SystemProperty}.
     * The provided argument is ignored.
     */
    public static void restart(Object ignored) {
        if (getInstance() != null) {
            getInstance().restartServer();
        }
    }

    private synchronized void restartServer() {
        stop();
        start();
    }

    @Override
    public void storeContentChanged( CertificateStore store )
    {
        restartServer();
    }
}
