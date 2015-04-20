/**
 * $Revision: 3034 $
 * $Date: 2005-11-04 21:02:33 -0300 (Fri, 04 Nov 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

import java.io.File;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.List;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.spdy.server.http.HTTPSPDYServerConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;
import org.jivesoftware.openfire.JMXManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.net.SSLConfig;
import org.jivesoftware.util.CertificateEventListener;
import org.jivesoftware.util.CertificateManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The admin console plugin. It starts a Jetty instance on the configured
 * port and loads the admin console web application.
 *
 * @author Matt Tucker
 */
public class AdminConsolePlugin implements Plugin {

    private static final Logger Log = LoggerFactory.getLogger(AdminConsolePlugin.class);

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
    private boolean sslEnabled = false;

    private File pluginDir;

    /**
     * Create a Jetty module.
     */
    public AdminConsolePlugin() {
        contexts = new ContextHandlerCollection();
        
        // JSP 2.0 uses commons-logging, so also override that implementation.
        System.setProperty("org.apache.commons.logging.LogFactory", "org.jivesoftware.util.log.util.CommonsLogFactory");
    }

    /**
     * Starts the Jetty instance.
     */
    public void startup() {
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

        ServerConnector httpConnector = null;
		ServerConnector httpsConnector = null;
		HttpConfiguration httpConfig = null;

        // Create connector for http traffic if it's enabled.
        if (adminPort > 0) {
			httpConfig = new HttpConfiguration();

        	// Do not send Jetty info in HTTP headers
			httpConfig.setSendServerVersion( false );
            httpConnector = new ServerConnector(adminServer, null, null, null, -1, serverThreads, 
            		new HttpConnectionFactory(httpConfig));

            // Listen on a specific network interface if it has been set.
            String bindInterface = getBindInterface();
            httpConnector.setHost(bindInterface);
            httpConnector.setPort(adminPort);
            adminServer.addConnector(httpConnector);
        }

        // Create a connector for https traffic if it's enabled.
        sslEnabled = false;
        try {
            if (adminSecurePort > 0 && CertificateManager.isRSACertificate(SSLConfig.getKeyStore(), "*"))
            {
                if (!CertificateManager.isRSACertificate(SSLConfig.getKeyStore(),
                        XMPPServer.getInstance().getServerInfo().getXMPPDomain())) {
                    Log.warn("Admin console: Using RSA certificates but they are not valid for the hosted domain");
                }

                final SslContextFactory sslContextFactory = new SslContextFactory();
                sslContextFactory.addExcludeProtocols("SSLv3");
                sslContextFactory.setTrustStorePassword(SSLConfig.gets2sTrustPassword());
                sslContextFactory.setTrustStoreType(SSLConfig.getStoreType());
                sslContextFactory.setKeyStorePath(SSLConfig.getKeystoreLocation());
                sslContextFactory.setNeedClientAuth(false);
                sslContextFactory.setWantClientAuth(false);
                sslContextFactory.setKeyStorePassword(SSLConfig.getKeyPassword());
                sslContextFactory.setKeyStoreType(SSLConfig.getStoreType());

				if ("npn".equals(JiveGlobals.getXMLProperty("spdy.protocol", "")))
				{
					httpsConnector = new HTTPSPDYServerConnector(adminServer, sslContextFactory);

				} else {
					HttpConfiguration httpsConfig = new HttpConfiguration();
					httpsConfig.setSendServerVersion( false );
					httpsConfig.setSecureScheme("https");
					httpsConfig.setSecurePort(adminSecurePort);
					httpsConfig.addCustomizer(new SecureRequestCustomizer());

					HttpConnectionFactory httpConnectionFactory = new HttpConnectionFactory(httpsConfig);
					SslConnectionFactory sslConnectionFactory = new SslConnectionFactory(sslContextFactory, org.eclipse.jetty.http.HttpVersion.HTTP_1_1.toString());

                	httpsConnector = new ServerConnector(adminServer, null, null, null, -1, serverThreads, 
                			sslConnectionFactory, httpConnectionFactory);
				}

                String bindInterface = getBindInterface();
                httpsConnector.setHost(bindInterface);
                httpsConnector.setPort(adminSecurePort);
                adminServer.addConnector(httpsConnector);

                sslEnabled = true;
            }
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
        }

        // Make sure that at least one connector was registered.
        if (adminServer.getConnectors() == null || adminServer.getConnectors().length == 0) {
            adminServer = null;
            // Log warning.
            log(LocaleUtils.getLocalizedString("admin.console.warning"));
            return;
        }

        HandlerCollection collection = new HandlerCollection();
        adminServer.setHandler(collection);
        collection.setHandlers(new Handler[] { contexts, new DefaultHandler() });

        try {
            adminServer.start();
        }
        catch (Exception e) {
            Log.error("Could not start admin console server", e);
        }

        // Log the ports that the admin server is listening on.
        logAdminConsolePorts();
    }

	/**
     * Shuts down the Jetty server.
     * */
    public void shutdown() {
        // Remove listener for certificate events
        if (certificateListener != null) {
            CertificateManager.removeListener(certificateListener);
        }
        //noinspection ConstantConditions
        try {
            if (adminServer != null && adminServer.isRunning()) {
                adminServer.stop();
            }
        }
        catch (Exception e) {
            Log.error("Error stopping admin console server", e);
        }
        adminServer = null;
    }

    public void initializePlugin(PluginManager manager, File pluginDir) {
        this.pluginDir = pluginDir;

        createWebAppContext();

        startup();
    }

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

    /**
     * Returns <tt>null</tt> if the admin console will be available in all network interfaces of this machine
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
     * Returns the non-SSL port on which the admin console is currently operating.
     *
     * @return the non-SSL port on which the admin console is currently operating.
     */
    public int getAdminUnsecurePort() {
        return adminPort;
    }

    /**
     * Returns the SSL port on which the admin console is current operating.
     *
     * @return the SSL port on which the admin console is current operating.
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

    public void restart() {
        try {
            adminServer.stop();
            adminServer.start();
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
    }

    private void createWebAppContext() {
        ServletContextHandler context;
        // Add web-app. Check to see if we're in development mode. If so, we don't
        // add the normal web-app location, but the web-app in the project directory.
        if (Boolean.getBoolean("developmentMode")) {
            System.out.println(LocaleUtils.getLocalizedString("admin.console.devmode"));
            context = new WebAppContext(contexts, pluginDir.getParentFile().getParentFile().getParentFile().getParent() +
                     File.separator + "src" + File.separator + "web", "/");
        }
        else {
            context = new WebAppContext(contexts, pluginDir.getAbsoluteFile() + File.separator + "webapp",
                    "/");
        }
        context.setWelcomeFiles(new String[]{"index.jsp"});
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
        boolean isSecureStarted = false;
        boolean isSPDY = false;

        for (Connector connector : adminServer.getConnectors()) {
            if (((ServerConnector) connector).getPort() == adminPort) {
                isPlainStarted = true;
            }
            else if (((ServerConnector) connector).getPort() == adminSecurePort) {
                isSecureStarted = true;
            }

           	if (connector instanceof HTTPSPDYServerConnector) {
				isSPDY = true;
			}
        }

        if (isPlainStarted && isSecureStarted) {
            log(listening + ":" + System.getProperty("line.separator") +
                    "  http://" + hostname + ":" +
                    adminPort + System.getProperty("line.separator") +
                    "  https://" + hostname + ":" +
                    adminSecurePort + (isSPDY ? " (SPDY)" : ""));
        }
        else if (isSecureStarted) {
            log(listening + " https://" + hostname + ":" + adminSecurePort + (isSPDY ? " (SPDY)" : ""));
        }
        else if (isPlainStarted) {
            log(listening + " http://" + hostname + ":" + adminPort);
        }
    }

    /**
     * Listens for security certificates being created and destroyed so we can track when the
     * admin console needs to be restarted.
     */
    private class CertificateListener implements CertificateEventListener {

        public void certificateCreated(KeyStore keyStore, String alias, X509Certificate cert) {
            // If new certificate is RSA then (re)start the HTTPS service
            if ("RSA".equals(cert.getPublicKey().getAlgorithm())) {
                restartNeeded = true;
            }
        }

        public void certificateDeleted(KeyStore keyStore, String alias) {
            restartNeeded = true;
        }

        public void certificateSigned(KeyStore keyStore, String alias,
                                      List<X509Certificate> certificates) {
            // If new certificate is RSA then (re)start the HTTPS service
            if ("RSA".equals(certificates.get(0).getPublicKey().getAlgorithm())) {
                restartNeeded = true;
            }
        }
    }
}
