/*
 * Copyright (C) 2004-2008 Jive Software, 2016-2023 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire.container;

import org.apache.jasper.servlet.JasperInitializer;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.SimpleInstanceManager;
import org.eclipse.jetty.annotations.AnnotationConfiguration;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.plus.annotation.ContainerInitializer;
import org.eclipse.jetty.plus.webapp.EnvConfiguration;
import org.eclipse.jetty.plus.webapp.PlusConfiguration;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.*;
import org.jivesoftware.admin.AdminContentSecurityPolicyFilter;
import org.jivesoftware.openfire.ConnectionManager;
import org.jivesoftware.admin.AuthCheckFilter;
import org.jivesoftware.openfire.JMXManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.http.HttpBindContentSecurityPolicyFilter;
import org.jivesoftware.openfire.keystore.CertificateStore;
import org.jivesoftware.openfire.keystore.IdentityStore;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.openfire.spi.ConnectionType;
import org.jivesoftware.openfire.spi.EncryptionArtifactFactory;
import org.jivesoftware.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimerTask;

/**
 * The admin console plugin. It starts a Jetty instance on the configured
 * port and loads the admin console web application.
 *
 * @author Matt Tucker
 */
public class AdminConsolePlugin implements Plugin {

    private static final Logger Log = LoggerFactory.getLogger(AdminConsolePlugin.class);

    /**
     * Enable / Disable parsing a 'X-Forwarded-For' style HTTP header of HTTP requests.
     */
    public static final SystemProperty<Boolean> ADMIN_CONSOLE_FORWARDED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("adminConsole.forwarded.enabled")
        .setDynamic(false)
        .setDefaultValue(false)
        .addListener(enabled -> ((AdminConsolePlugin) XMPPServer.getInstance().getPluginManager().getPlugin("admin")).restartNeeded = true)
        .build();

    /**
     * The HTTP header name for 'forwarded for'
     */
    public static final SystemProperty<String> ADMIN_CONSOLE_FORWARDED_FOR = SystemProperty.Builder.ofType(String.class)
        .setKey("adminConsole.forwarded.for.header")
        .setDynamic(false)
        .setDefaultValue(HttpHeader.X_FORWARDED_FOR.toString())
        .addListener(enabled -> ((AdminConsolePlugin) XMPPServer.getInstance().getPluginManager().getPlugin("admin")).restartNeeded = true)
        .build();

    /**
     * The HTTP header name for 'forwarded server'.
     */
    public static final SystemProperty<String> ADMIN_CONSOLE_FORWARDED_SERVER = SystemProperty.Builder.ofType(String.class)
        .setKey("adminConsole.forwarded.server.header")
        .setDynamic(false)
        .setDefaultValue(HttpHeader.X_FORWARDED_SERVER.toString())
        .addListener(enabled -> ((AdminConsolePlugin) XMPPServer.getInstance().getPluginManager().getPlugin("admin")).restartNeeded = true)
        .build();

    /**
     * The HTTP header name for 'forwarded hosts'.
     */
    public static final SystemProperty<String> ADMIN_CONSOLE_FORWARDED_HOST = SystemProperty.Builder.ofType(String.class)
        .setKey("adminConsole.forwarded.host.header")
        .setDynamic(false)
        .setDefaultValue(HttpHeader.X_FORWARDED_HOST.toString())
        .addListener(enabled -> ((AdminConsolePlugin) XMPPServer.getInstance().getPluginManager().getPlugin("admin")).restartNeeded = true)
        .build();

    /**
     * Sets a forced valued for the host header.
     */
    public static final SystemProperty<String> ADMIN_CONSOLE_FORWARDED_HOST_NAME = SystemProperty.Builder.ofType(String.class)
        .setKey("adminConsole.forwarded.host.name")
        .setDynamic(false)
        .setDefaultValue(null)
        .addListener(enabled -> ((AdminConsolePlugin) XMPPServer.getInstance().getPluginManager().getPlugin("admin")).restartNeeded = true)
        .build();

    /**
     * Enable / Disable adding a 'Content-Security-Policy' HTTP header to the response to requests made against the admin console.
     */
    public static final SystemProperty<Boolean> ADMIN_CONSOLE_CONTENT_SECURITY_POLICY_ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("adminConsole.CSP.enabled")
        .setDynamic(true)
        .setDefaultValue(true)
        .build();

    /**
     * The header value when adding a 'Content-Security-Policy' HTTP header to the response to requests made against the admin console.
     */
    public static final SystemProperty<String> ADMIN_CONSOLE_CONTENT_SECURITY_POLICY_RESPONSEVALUE = SystemProperty.Builder.ofType(String.class)
        .setKey("adminConsole.CSP.responsevalue")
        .setDynamic(true)
        .setDefaultValue("default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; base-uri 'self'; form-action 'self'; img-src 'self' igniterealtime.org;")
        .build();

    /**
     * Random secret used by JVM to allow SSO. Only other cluster nodes can use this secret
     * as a way to integrate the admin consoles of each cluster node.
     */
    public final static String secret = StringUtils.randomString(64);

    private int adminPort;
    private int adminSecurePort;
    private Server adminServer;
    private ContextHandlerCollection contexts;
    private CertificateEventListener certificateListener;
    private boolean restartNeeded = false;
    private boolean autoRestartEnabled = true;
    private TimerTask reenableTask = null;
    private boolean sslEnabled = false;

    private File pluginDir;

    /**
     * Create a Jetty module.
     */
    public AdminConsolePlugin() {
    }

    /**
     * Starts the Jetty instance.
     */
    protected void startup() {

        deleteLegacyWebInfLibFolder();

        restartNeeded = false;

        // Add listener for certificate events
        certificateListener = new CertificateListener();
        CertificateManager.addListener(certificateListener);

        // the number of threads allocated to each connector/port
        int serverThreads = JiveGlobals.getXMLProperty("adminConsole.serverThreads", 2);

        adminPort = JiveGlobals.getXMLProperty("adminConsole.port", 9090);
        adminSecurePort = JiveGlobals.getXMLProperty("adminConsole.securePort", 9091);

        final QueuedThreadPool tp = new QueuedThreadPool();
        tp.setName("Jetty-QTP-AdminConsole");

        adminServer = new Server(tp);

        if (JMXManager.isEnabled()) {
            JMXManager jmx = JMXManager.getInstance();
            adminServer.addBean(jmx.getContainer());
        }

        // Create connector for http traffic if it's enabled.
        if (adminPort > 0) {
            final HttpConfiguration httpConfig = new HttpConfiguration();

            // Do not send Jetty info in HTTP headers
            httpConfig.setSendServerVersion( false );
            configureProxiedConnector(httpConfig);

            final ServerConnector httpConnector = new ServerConnector(adminServer, null, null, null, -1, serverThreads, new HttpConnectionFactory(httpConfig));

            // Listen on a specific network interface if it has been set.
            String bindInterface = getBindInterface();
            httpConnector.setHost(bindInterface);
            httpConnector.setPort(adminPort);
            adminServer.addConnector(httpConnector);
        }

        // Create a connector for https traffic if it's enabled.
        sslEnabled = false;
        try {
            IdentityStore identityStore = null;
            if (XMPPServer.getInstance().getCertificateStoreManager() == null){
                Log.warn( "Admin console: CertificateStoreManager has not been initialized yet. HTTPS will be unavailable." );
            } else {
                identityStore = XMPPServer.getInstance().getCertificateStoreManager().getIdentityStore( ConnectionType.WEBADMIN );
            }
            if (identityStore != null && adminSecurePort > 0 )
            {
                if ( identityStore.getAllCertificates().isEmpty() )
                {
                    Log.warn( "Admin console: Identity store does not have any certificates. HTTPS will be unavailable." );
                }
                else
                {
                    if ( !identityStore.containsDomainCertificate() )
                    {
                        Log.warn( "Admin console: Using certificates but they are not valid for the hosted domain" );
                    }

                    final ConnectionManager connectionManager = XMPPServer.getInstance().getConnectionManager();
                    final ConnectionConfiguration configuration = connectionManager.getListener( ConnectionType.WEBADMIN, true ).generateConnectionConfiguration();
                    final SslContextFactory.Server sslContextFactory = new EncryptionArtifactFactory( configuration ).getSslContextFactory();

                    final HttpConfiguration httpsConfig = new HttpConfiguration();
                    httpsConfig.setSendServerVersion( false );
                    httpsConfig.setSecureScheme( "https" );
                    httpsConfig.setSecurePort( adminSecurePort );
                    SecureRequestCustomizer secureRequestCustomizer = new SecureRequestCustomizer();
                    secureRequestCustomizer.setSniHostCheck(sslContextFactory.isSniRequired());
                    httpsConfig.addCustomizer( secureRequestCustomizer );
                    configureProxiedConnector(httpsConfig);

                    final HttpConnectionFactory httpConnectionFactory = new HttpConnectionFactory( httpsConfig );
                    final SslConnectionFactory sslConnectionFactory = new SslConnectionFactory( sslContextFactory, org.eclipse.jetty.http.HttpVersion.HTTP_1_1.toString() );

                    final ServerConnector httpsConnector = new ServerConnector( adminServer, null, null, null, -1, serverThreads, sslConnectionFactory, httpConnectionFactory );
                    final String bindInterface = getBindInterface();
                    httpsConnector.setHost(bindInterface);
                    httpsConnector.setPort(adminSecurePort);
                    adminServer.addConnector(httpsConnector);

                    sslEnabled = true;
                }
            }
        }
        catch ( Exception e )
        {
            Log.error( "An exception occurred while trying to make available the admin console via HTTPS.", e );
        }

        // Make sure that at least one connector was registered.
        if (adminServer.getConnectors() == null || adminServer.getConnectors().length == 0) {
            adminServer = null;
            // Log warning.
            log(LocaleUtils.getLocalizedString("admin.console.warning"));
            return;
        }

        createWebAppContext();

        HandlerCollection collection = new HandlerCollection();
        adminServer.setHandler(collection);
        collection.setHandlers(new Handler[] { contexts, new DefaultHandler() });

        try {
            adminServer.start(); // excludes initialised

            if(XMPPServer.getInstance().isSetupMode()) {
                AuthCheckFilter.loadSetupExcludes();
            }

            // Log the ports that the admin server is listening on.
            logAdminConsolePorts();
        }
        catch (Exception e) {
            Log.error("Could not start admin console server", e);
        }
    }

    private void deleteLegacyWebInfLibFolder() {
        /*
        See https://igniterealtime.atlassian.net/projects/OF/issues/OF-1647 - with the migration from Ant to Maven, Openfire
        needs less JAR files scattered around the file system. When upgrading from before 4.3.0, the old file are not
        removed by the installer, so this method attempts to remove them.
         */
        final Path libFolder = Paths.get(pluginDir.getAbsoluteFile().toString(), "webapp", "WEB-INF", "lib");
        if (!Files.exists(libFolder) || !Files.isDirectory(libFolder)) {
            // Nothing to do
            return;
        }

        final int maxAttempts = 10;
        int currentAttempt = 1;
        do {
            int backupSuffix = 1;
            String backupFileName;
            do {
                backupFileName = "lib.backup-" + backupSuffix;
                backupSuffix++;
            } while (Files.exists(libFolder.resolveSibling(backupFileName)));

            Log.warn("Renaming legacy admin WEB-INF/lib folder to {}. Attempt #{} {}", backupFileName, currentAttempt, libFolder);

            currentAttempt++;
            try {
                Files.move(libFolder, libFolder.resolveSibling(backupFileName));
            } catch (final IOException e) {
               Log.warn("Exception attempting to delete folder, will retry shortly", e);
            }
            if(Files.exists(libFolder)) {
                try {
                    Thread.sleep(1000);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.warn("Interrupted whilst sleeping - aborting attempt to rename lib folder", e);
                }
            }
        } while (Files.exists(libFolder) && currentAttempt <= maxAttempts && !Thread.currentThread().isInterrupted());

        if (!Files.exists(libFolder)) {
            // We succeeded, so continue
            return;
        }

        // The old lib folder still exists, will have to be deleted manully
        final String message = "The folder " + libFolder + " must be manually renamed or deleted before Openfire can start. Shutting down.";
        // Log this everywhere so it's impossible (?) to miss
        Log.debug(message);
        Log.info(message);
        Log.warn(message);
        Log.error(message);
        System.out.println(message);
        XMPPServer.getInstance().stop();
        throw new IllegalStateException(message);
    }

    /**
     * Shuts down the Jetty server.
     * */
    protected void shutdown() {
        // Remove listener for certificate events
        if (certificateListener != null) {
            CertificateManager.removeListener(certificateListener);
        }
        try {
            if (adminServer != null && adminServer.isRunning()) {
                adminServer.stop();
            }
        }
        catch (Exception e) {
            Log.error("Error stopping admin console server", e);
        }

        if (contexts != null ) {
            try {
                contexts.stop();
                contexts.destroy();
            } catch ( Exception e ) {
                Log.error("Error stopping admin console server", e);
            }
        }
        adminServer = null;
        contexts = null;
    }

    @Override
    public void initializePlugin(PluginManager manager, File pluginDir) {
        this.pluginDir = pluginDir;

        startup();
    }

    @Override
    public void destroyPlugin() {
        shutdown();
    }

    /**
     * Returns true if the Jetty server needs to be restarted. This is usually required when
     * certificates are added, deleted or modified or when server ports were modified.
     *
     * @return true if the Jetty server needs to be restarted.
     */
    public boolean isRestartNeeded() {
        return restartNeeded;
    }

    private void configureProxiedConnector(HttpConfiguration httpConfig) {
        // Check to see if we are deployed behind a proxy
        // Refer to http://eclipse.org/jetty/documentation/current/configuring-connectors.html
        if (ADMIN_CONSOLE_FORWARDED.getValue()) {
            ForwardedRequestCustomizer customizer = new ForwardedRequestCustomizer();
            // default: "X-Forwarded-For"
            String forwardedForHeader = ADMIN_CONSOLE_FORWARDED_FOR.getValue();
            if (forwardedForHeader != null) {
                customizer.setForwardedForHeader(forwardedForHeader);
            }
            // default: "X-Forwarded-Server"
            String forwardedServerHeader = ADMIN_CONSOLE_FORWARDED_SERVER.getValue();
            if (forwardedServerHeader != null) {
                customizer.setForwardedServerHeader(forwardedServerHeader);
            }
            // default: "X-Forwarded-Host"
            String forwardedHostHeader = ADMIN_CONSOLE_FORWARDED_HOST.getValue();
            if (forwardedHostHeader != null) {
                customizer.setForwardedHostHeader(forwardedHostHeader);
            }
            // default: none
            String hostName = ADMIN_CONSOLE_FORWARDED_HOST_NAME.getValue();
            if (hostName != null) {
                customizer.setHostHeader(hostName);
            }

            httpConfig.addCustomizer(customizer);
        }
    }

    /**
     * Returns {@code null} if the admin console will be available in all network interfaces of this machine
     * or a String representing the only interface where the admin console will be available.
     *
     * @return String representing the only interface where the admin console will be available or null if it
     * will be available in all interfaces.
     */
    public String getBindInterface() {
        String adminInterfaceName = JiveGlobals.getXMLProperty("adminConsole.interface");
        String globalInterfaceName = JiveGlobals.getXMLProperty("network.interface");
        String bindInterface = null;
        if (adminInterfaceName != null && adminInterfaceName.trim().length() > 0) {
            bindInterface = adminInterfaceName;
        }
        else if (globalInterfaceName != null && globalInterfaceName.trim().length() > 0) {
            bindInterface = globalInterfaceName;
         }
        return bindInterface;
    }

    /**
     * Returns the non-TLS port on which the admin console is currently operating.
     *
     * @return the non-TLS port on which the admin console is currently operating.
     */
    public int getAdminUnsecurePort() {
        return adminPort;
    }

    /**
     * Returns the TLS port on which the admin console is current operating.
     *
     * @return the TLS port on which the admin console is current operating.
     */
    public int getAdminSecurePort() {
        if (!sslEnabled) {
            return 0;
        }
        return adminSecurePort;
    }

    /**
     * Returns the collection of Jetty contexts used in the admin console. A root context "/"
     * is where the admin console lives. Additional contexts can be added dynamically for
     * other web applications that should be run as part of the admin console server
     * process. The following pseudo code demonstrates how to do this:
     *
     * <pre>
     *   ContextHandlerCollection contexts = ((AdminConsolePlugin)pluginManager.getPlugin("admin")).getContexts();
     *   context = new WebAppContext(SOME_DIRECTORY, "/CONTEXT_NAME");
     *   contexts.addHandler(context);
     *   context.setWelcomeFiles(new String[]{"index.jsp"});
     *   context.start();
     * </pre>
     *
     * @return the Jetty handlers.
     */
    public ContextHandlerCollection getContexts() {
        return contexts;
    }

    /**
     * Restart the admin console (and it's HTTP server) without restarting the plugin.
     */
    public void restart() {
        try {
            shutdown();
            startup();
        }
        catch (Exception e) {
            Log.error("An exception occurred while restarting the admin console:", e);
        }
    }

    private void createWebAppContext() {

        contexts = new ContextHandlerCollection();

        WebAppContext context = new WebAppContext(contexts, pluginDir.getAbsoluteFile() + File.separator + "webapp", "/");

        // Ensure the JSP engine is initialized correctly (in order to be able to cope with Tomcat/Jasper precompiled JSPs).
        final List<ContainerInitializer> initializers = new ArrayList<>();
        initializers.add(new ContainerInitializer(new JasperInitializer(), null));
        context.setAttribute("org.eclipse.jetty.containerInitializers", initializers);
        context.setClassLoader(Thread.currentThread().getContextClassLoader());
        context.setAttribute(InstanceManager.class.getName(), new SimpleInstanceManager());
        context.setConfigurations(new Configuration[]{
            new AnnotationConfiguration(),
            new WebInfConfiguration(),
            new WebXmlConfiguration(),
            new MetaInfConfiguration(),
            new FragmentConfiguration(),
            new EnvConfiguration(),
            new PlusConfiguration(),
            new JettyWebXmlConfiguration()
        });
        final URL classes = getClass().getProtectionDomain().getCodeSource().getLocation();
        context.getMetaData().setWebInfClassesResources(Collections.singletonList(Resource.newResource(classes)));

        // Add CSP headers for all HTTP responses (errors, etc.)
        context.addFilter(AdminContentSecurityPolicyFilter.class, "/*", null);

        // The index.html includes a redirect to the index.jsp and doesn't bypass
        // the context security when in development mode
        context.setWelcomeFiles(new String[]{"index.html"});
    }

    private void log(String string) {
       Log.info(string);
       System.out.println(string);
    }

    private void logAdminConsolePorts() {
        // Log what ports the admin console is running on.
        String listening = LocaleUtils.getLocalizedString("admin.console.listening");
        String hostname = getBindInterface() == null ?
                XMPPServer.getInstance().getServerInfo().getXMPPDomain() :
                getBindInterface();
        boolean isPlainStarted = false;
        boolean isEncryptedStarted = false;

        for (Connector connector : adminServer.getConnectors()) {
            if (((ServerConnector) connector).getPort() == adminPort) {
                isPlainStarted = true;
            }
            else if (((ServerConnector) connector).getPort() == adminSecurePort) {
                isEncryptedStarted = true;
            }

        }

        if (isPlainStarted && isEncryptedStarted) {
            log(listening + ":" + System.getProperty("line.separator") +
                    "  http://" + hostname + ":" +
                    adminPort + System.getProperty("line.separator") +
                    "  https://" + hostname + ":" +
                    adminSecurePort);
        }
        else if (isEncryptedStarted) {
            log(listening + " https://" + hostname + ":" + adminSecurePort);
        }
        else if (isPlainStarted) {
            log(listening + " http://" + hostname + ":" + adminPort);
        }
    }

    /**
     * Temporarily disables auto-restarting of the plugin's webserver when certificate changes are detected.
     *
     * @param pause The duration for which certificate changes are ignored.
     */
    public synchronized void pauseAutoRestartEnabled(final Duration pause) {
        setAutoRestartEnabled(false);

        if (reenableTask != null) {
            TaskEngine.getInstance().cancelScheduledTask(reenableTask);
        }
        reenableTask = new TimerTask() {
            @Override
            public void run() {
                 setAutoRestartEnabled(true);
            }
        };

        TaskEngine.getInstance().schedule(reenableTask, pause);
    }

    /**
     * Controls if the webserver is automatically reloaded when a certificate change is detected. It is useful to disable
     * this when the certificates are being updated through the admin console, as that would cause the administrative
     * user to be logged out while working on the certificate stores.
     *
     * @param autoRestartEnabled 'true' if the plugin's webserver should be automatically reloaded when certificate changes are detected.
     */
    private void setAutoRestartEnabled(final boolean autoRestartEnabled) {
        Log.info("Setting auto-restart enabled to {}", autoRestartEnabled);
        this.autoRestartEnabled = autoRestartEnabled;
    }

    private boolean isAutoRestartEnabled() {
        return autoRestartEnabled;
    }

    /**
     * Listens for security certificates being created and destroyed so we can track when the
     * admin console needs to be restarted.
     */
    private class CertificateListener implements CertificateEventListener {

        @Override
        public void storeContentChanged( CertificateStore store )
        {
            if (autoRestartEnabled) {
                Log.info("Automatically restarting plugin. Certificate changes detected.");
                restart();
            } else {
                restartNeeded = true;
                Log.info("Certificate changes detected. Plugin needs a restart for changed to be applied.");
            }
        }
    }
}
