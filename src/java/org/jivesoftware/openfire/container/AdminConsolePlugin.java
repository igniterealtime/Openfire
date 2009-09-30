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

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.net.SSLConfig;
import org.jivesoftware.util.*;
import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.ContextHandlerCollection;
import org.mortbay.jetty.handler.DefaultHandler;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.mortbay.jetty.security.SslSelectChannelConnector;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.webapp.WebAppContext;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * The admin console plugin. It starts a Jetty instance on the configured
 * port and loads the admin console web application.
 *
 * @author Matt Tucker
 */
public class AdminConsolePlugin implements Plugin {

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
        
        // Configure Jetty logging to a more reasonable default.
        System.setProperty("org.mortbay.log.class", "org.jivesoftware.util.log.util.JettyLog");
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

        adminPort = JiveGlobals.getXMLProperty("adminConsole.port", 9090);
        adminSecurePort = JiveGlobals.getXMLProperty("adminConsole.securePort", 9091);
        adminServer = new Server();
        // Do not send Jetty info in HTTP headers
        adminServer.setSendServerVersion(false);

        // Create connector for http traffic if it's enabled.
        if (adminPort > 0) {
            Connector httpConnector = new SelectChannelConnector();
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
                         
                JiveSslConnector httpsConnector = new JiveSslConnector();
                String bindInterface = getBindInterface();
                httpsConnector.setHost(bindInterface);
                httpsConnector.setPort(adminSecurePort);

                httpsConnector.setTrustPassword(SSLConfig.gets2sTrustPassword());
                httpsConnector.setTruststoreType(SSLConfig.getStoreType());
                httpsConnector.setTruststore(SSLConfig.gets2sTruststoreLocation());
                httpsConnector.setNeedClientAuth(false);
                httpsConnector.setWantClientAuth(false);

                httpsConnector.setKeyPassword(SSLConfig.getKeyPassword());
                httpsConnector.setKeystoreType(SSLConfig.getStoreType());
                httpsConnector.setKeystore(SSLConfig.getKeystoreLocation());
                adminServer.addConnector(httpsConnector);

                sslEnabled = true;
            }
        }
        catch (Exception e) {
            Log.error(e);
        }

        // Make sure that at least one connector was registered.
        if (adminServer.getConnectors() == null || adminServer.getConnectors().length == 0) {
            adminServer = null;
            // Log warning.
            log(LocaleUtils.getLocalizedString("admin.console.warning"));
            return;
        }

        adminServer.setHandlers(new Handler[] { contexts, new DefaultHandler() });

        try {
            adminServer.start();
        }
        catch (Exception e) {
            Log.error("Could not start admin conosle server", e);
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
            Log.error(e);
        }
    }

    private void createWebAppContext() {
        Context context;
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
        boolean isPlainStarted = false;
        boolean isSecureStarted = false;
        for (Connector connector : adminServer.getConnectors()) {
            if (connector.getPort() == adminPort) {
                isPlainStarted = true;
            }
            else if (connector.getPort() == adminSecurePort) {
                isSecureStarted = true;
            }
        }

        if (isPlainStarted && isSecureStarted) {
            log(listening + ":" + System.getProperty("line.separator") +
                    "  http://" + XMPPServer.getInstance().getServerInfo().getXMPPDomain() + ":" +
                    adminPort + System.getProperty("line.separator") +
                    "  https://" + XMPPServer.getInstance().getServerInfo().getXMPPDomain() + ":" +
                    adminSecurePort);
        }
        else if (isSecureStarted) {
            log(listening + " https://" +
                    XMPPServer.getInstance().getServerInfo().getXMPPDomain() + ":" + adminSecurePort);
        }
        else if (isPlainStarted) {
            log(listening + " http://" +
                    XMPPServer.getInstance().getServerInfo().getXMPPDomain() + ":" + adminPort);
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

    private class JiveSslConnector extends SslSelectChannelConnector {

        @Override
        protected SSLContext createSSLContext() throws Exception {
            return SSLConfig.getSSLContext();
        }
    }
}
