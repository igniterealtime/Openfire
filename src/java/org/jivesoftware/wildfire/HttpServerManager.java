/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006-2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire;

import org.jivesoftware.util.*;
import org.jivesoftware.wildfire.net.SSLConfig;
import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.mortbay.jetty.security.SslSocketConnector;
import org.mortbay.jetty.servlet.Context;
import javax.net.ssl.SSLServerSocketFactory;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Manages the instances of Jetty which provides the admin console funtionality.
 *
 * @author Alexander Wenckus
 */
public class HttpServerManager {

    private static final HttpServerManager instance = new HttpServerManager();

    public static final String ADMIN_CONSOLE_PORT = "adminConsole.port";

    public static final int ADMIN_CONSOLE_PORT_DEFAULT = 9090;

    public static final String ADMIN_CONOSLE_SECURE_PORT = "adminConsole.securePort";

    public static final int ADMIN_CONSOLE_SECURE_PORT_DEFAULT = 9091;

    /**
     * Returns an HTTP server manager instance (singleton).
     *
     * @return an HTTP server manager instance.
     */
    public static HttpServerManager getInstance() {
        return instance;
    }

    private int adminPort;
    private int adminSecurePort;
    private Server adminServer;
    private Context adminConsoleContext;
    private CertificateEventListener certificateListener;
    private boolean restartNeeded = false;

    /**
     * Constructs a new HTTP server manager.
     */
    private HttpServerManager() {
        // Configure Jetty logging to a more reasonable default.
        System.setProperty("org.mortbay.log.class", "org.jivesoftware.util.log.util.JettyLog");
        // JSP 2.0 uses commons-logging, so also override that implementation.
        System.setProperty("org.apache.commons.logging.LogFactory",
                "org.jivesoftware.util.log.util.CommonsLogFactory");
    }

    /**
     * Sets the Jetty context which provides the functionality for the admin console.
     *
     * @param context the web-app context which provides functionality for the admin console.
     */
    public void setAdminConsoleContext(Context context) {
        this.adminConsoleContext = context;
    }

    /**
     * Starts the Jetty instance.
     */
    public void startup() {
        restartNeeded = false;
        // Add listener for certificate events
        certificateListener = new CertificateListener();
        CertificateManager.addListener(certificateListener);

        if (adminConsoleContext != null) {
            createAdminConsoleServer();
        }

        if (adminServer != null) {
            adminServer.addHandler(adminConsoleContext);
        }

        if (adminServer != null) {
            try {
                adminServer.start();
            }
            catch (Exception e) {
                Log.error("Could not start admin conosle server", e);
            }
        }
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
            if (adminConsoleContext != null && adminConsoleContext.isRunning()) {
                adminConsoleContext.stop();
            }
        }
        catch (Exception e) {
            Log.error("Error stopping admin console server", e);
        }
        adminServer = null;
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
     * Returns the non-SSL port on which the admin console is currently operating.
     *
     * @return the non-SSL port on which the admin console is currently operating.
     */
    public int getAdminUnsecurePort() {
        return JiveGlobals.getXMLProperty(ADMIN_CONSOLE_PORT, ADMIN_CONSOLE_PORT_DEFAULT);
    }

    /**
     * Returns the SSL port on which the admin console is current operating.
     *
     * @return the SSL port on which the admin console is current operating.
     */
    public int getAdminSecurePort() {
        return JiveGlobals.getXMLProperty(ADMIN_CONOSLE_SECURE_PORT,
                ADMIN_CONSOLE_SECURE_PORT_DEFAULT);
    }

    private void createAdminConsoleServer() {
        adminPort = JiveGlobals.getXMLProperty(ADMIN_CONSOLE_PORT, ADMIN_CONSOLE_PORT_DEFAULT);
        adminSecurePort = JiveGlobals.getXMLProperty(ADMIN_CONOSLE_SECURE_PORT,
                ADMIN_CONSOLE_SECURE_PORT_DEFAULT);
        adminServer = new Server();

        Connector httpConnector = createConnector(adminPort);
        Connector httpsConnector = createSSLConnector(adminSecurePort);
        if (httpConnector == null && httpsConnector == null) {
            adminServer = null;
            // Log warning.
            log(LocaleUtils.getLocalizedString("admin.console.warning"));
            return;
        }
        if (httpConnector != null) {
            adminServer.addConnector(httpConnector);
        }
        if (httpsConnector != null) {
            adminServer.addConnector(httpsConnector);
        }

        logAdminConsolePorts();
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
                    "  http://" + XMPPServer.getInstance().getServerInfo().getName() + ":" +
                    adminPort + System.getProperty("line.separator") +
                    "  https://" + XMPPServer.getInstance().getServerInfo().getName() + ":" +
                    adminSecurePort);
        }
        else if (isSecureStarted) {
            log(listening + " https://" +
                    XMPPServer.getInstance().getServerInfo().getName() + ":" + adminSecurePort);
        }
        else if (isPlainStarted) {
            log(listening + " http://" +
                    XMPPServer.getInstance().getServerInfo().getName() + ":" + adminPort);
        }
    }

    private Connector createConnector(int port) {
        if (port > 0) {
            SelectChannelConnector connector = new SelectChannelConnector();
            // Listen on a specific network interface if it has been set.
            String interfaceName = JiveGlobals.getXMLProperty("network.interface");
            String bindInterface = null;
            if (interfaceName != null) {
                if (interfaceName.trim().length() > 0) {
                    bindInterface = interfaceName;
                }
            }
            connector.setHost(bindInterface);
            connector.setPort(port);
            return connector;
        }
        return null;
    }

    private Connector createSSLConnector(int securePort) {
        try {
            if (securePort > 0 && CertificateManager.isRSACertificate(SSLConfig.getKeyStore(),
                    XMPPServer.getInstance().getServerInfo().getName())) {
                SslSocketConnector sslConnector = new JiveSslConnector();
                String interfaceName = JiveGlobals.getXMLProperty("network.interface");
                String bindInterface = null;
                if (interfaceName != null) {
                    if (interfaceName.trim().length() > 0) {
                        bindInterface = interfaceName;
                    }
                }
                sslConnector.setHost(bindInterface);
                sslConnector.setPort(securePort);

                sslConnector.setTrustPassword(SSLConfig.getTrustPassword());
                sslConnector.setTruststoreType(SSLConfig.getStoreType());
                sslConnector.setTruststore(SSLConfig.getTruststoreLocation());
                sslConnector.setNeedClientAuth(false);
                sslConnector.setWantClientAuth(false);

                sslConnector.setKeyPassword(SSLConfig.getKeyPassword());
                sslConnector.setKeystoreType(SSLConfig.getStoreType());
                sslConnector.setKeystore(SSLConfig.getKeystoreLocation());
                return sslConnector;
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
        return null;
    }

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

        /*private void stopSSLService() throws Exception {
            if (adminServer != null && adminSSLConnector != null) {
                adminSSLConnector.stop();
                adminServer.removeConnector(adminSSLConnector);
                adminSSLConnector = null;
            }
            // HTTP binding SSL service
            if (httpBindServer != null && httpBindServer != adminServer &&
                    bindSSLConnector != null) {
                bindSSLConnector.stop();
                httpBindServer.removeConnector(bindSSLConnector);
                bindSSLConnector = null;
            }
        }

        private void startSSLService() throws Exception {
            if (adminServer != null && adminSecurePort > 0) {
                adminSSLConnector = createSSLConnector(adminSecurePort);
                adminServer.addConnector(adminSSLConnector);
                adminServer.setHandlers(adminServer.getHandlers());
                adminSSLConnector.start();
                /*adminServer.stop();
                adminServer.start();
            }
            // HTTP binding SSL service
            if (httpBindServer != null && httpBindServer != adminServer && bindSecurePort > 0) {
                bindSSLConnector = createSSLConnector(bindSecurePort);
                httpBindServer.addConnector(bindSSLConnector);
                adminServer.setHandlers(adminServer.getHandlers());
                bindSSLConnector.start();
                /*httpBindServer.stop();
                httpBindServer.start();             }
        }*/
    }

    private class JiveSslConnector extends SslSocketConnector {

        @Override
        protected SSLServerSocketFactory createFactory() throws Exception {
            return SSLConfig.getServerSocketFactory();
        }
    }

}