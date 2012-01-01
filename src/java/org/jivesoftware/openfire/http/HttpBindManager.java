/**
 * $RCSfile$
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

package org.jivesoftware.openfire.http;

import java.io.File;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.net.SSLConfig;
import org.jivesoftware.util.CertificateEventListener;
import org.jivesoftware.util.CertificateManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public final class HttpBindManager {

	private static final Logger Log = LoggerFactory.getLogger(HttpBindManager.class);

    public static final String HTTP_BIND_ENABLED = "httpbind.enabled";

    public static final boolean HTTP_BIND_ENABLED_DEFAULT = true;

    public static final String HTTP_BIND_PORT = "httpbind.port.plain";

    public static final int HTTP_BIND_PORT_DEFAULT = 7070;

    public static final String HTTP_BIND_SECURE_PORT = "httpbind.port.secure";

    public static final int HTTP_BIND_SECURE_PORT_DEFAULT = 7443;

    private static HttpBindManager instance = new HttpBindManager();

    private Server httpBindServer;

    private int bindPort;

    private int bindSecurePort;

    private Connector httpConnector;
    private Connector httpsConnector;

    private CertificateListener certificateListener;

    private HttpSessionManager httpSessionManager;

    private ContextHandlerCollection contexts;

    public static HttpBindManager getInstance() {
        return instance;
    }

    private HttpBindManager() {
        // JSP 2.0 uses commons-logging, so also override that implementation.
        System.setProperty("org.apache.commons.logging.LogFactory", "org.jivesoftware.util.log.util.CommonsLogFactory");

        PropertyEventDispatcher.addListener(new HttpServerPropertyListener());
        this.httpSessionManager = new HttpSessionManager();
        contexts = new ContextHandlerCollection();
    }

    public void start() {
        certificateListener = new CertificateListener();
        CertificateManager.addListener(certificateListener);

        if (!isHttpBindServiceEnabled()) {
            return;
        }
        bindPort = getHttpBindUnsecurePort();
        bindSecurePort = getHttpBindSecurePort();
        configureHttpBindServer(bindPort, bindSecurePort);

        try {
            httpBindServer.start();
        }
        catch (Exception e) {
            Log.error("Error starting HTTP bind service", e);
        }
    }

    public void stop() {
        CertificateManager.removeListener(certificateListener);

        if (httpBindServer != null) {
            try {
                httpBindServer.stop();
            }
            catch (Exception e) {
                Log.error("Error stoping HTTP bind service", e);
            }
        }
    }

    public HttpSessionManager getSessionManager() {
        return httpSessionManager;
    }

    private boolean isHttpBindServiceEnabled() {
        return JiveGlobals.getBooleanProperty(HTTP_BIND_ENABLED, HTTP_BIND_ENABLED_DEFAULT);
    }

    private void createConnector(int port) {
        httpConnector = null;
        if (port > 0) {
            SelectChannelConnector connector = new SelectChannelConnector();
            // Listen on a specific network interface if it has been set.
            connector.setHost(getBindInterface());
            connector.setPort(port);
            httpConnector = connector;
        }
    }

    private void createSSLConnector(int securePort) {
        httpsConnector = null;
        try {
            if (securePort > 0 && CertificateManager.isRSACertificate(SSLConfig.getKeyStore(), "*")) {
                if (!CertificateManager.isRSACertificate(SSLConfig.getKeyStore(),
                        XMPPServer.getInstance().getServerInfo().getXMPPDomain())) {
                    Log.warn("HTTP binding: Using RSA certificates but they are not valid for " +
                            "the hosted domain");
                }

                SslSelectChannelConnector sslConnector = new SslSelectChannelConnector();
                sslConnector.setHost(getBindInterface());
                sslConnector.setPort(securePort);

                sslConnector.setTrustPassword(SSLConfig.getc2sTrustPassword());
                sslConnector.setTruststoreType(SSLConfig.getStoreType());
                sslConnector.setTruststore(SSLConfig.getc2sTruststoreLocation());

                // Set policy for checking client certificates
                String certPol = JiveGlobals.getProperty("xmpp.client.cert.policy", "disabled");
                if(certPol.equals("needed")) {                    
                    sslConnector.setNeedClientAuth(true);
                    sslConnector.setWantClientAuth(true);
                } else if(certPol.equals("wanted")) {
                    sslConnector.setNeedClientAuth(false);
                    sslConnector.setWantClientAuth(true);
                } else {
                    sslConnector.setNeedClientAuth(false);
                    sslConnector.setWantClientAuth(false);
                }
                
                sslConnector.setKeyPassword(SSLConfig.getKeyPassword());
                sslConnector.setKeystoreType(SSLConfig.getStoreType());
                sslConnector.setKeystore(SSLConfig.getKeystoreLocation());
                httpsConnector = sslConnector;
            }
        }
        catch (Exception e) {
            Log.error("Error creating SSL connector for Http bind", e);
        }
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
    public boolean isHttpBindActive() {
        return httpConnector != null && httpConnector.isRunning();
    }

    /**
     * Returns true if a listener on the HTTPS binding port is running.
     *
     * @return true if a listener on the HTTPS binding port is running.
     */
    public boolean isHttpsBindActive() {
        return httpsConnector != null && httpsConnector.isRunning();
    }

    public String getHttpBindUnsecureAddress() {
        return "http://" + XMPPServer.getInstance().getServerInfo().getXMPPDomain() + ":" +
                bindPort + "/http-bind/";
    }

    public String getHttpBindSecureAddress() {
        return "https://" + XMPPServer.getInstance().getServerInfo().getXMPPDomain() + ":" +
                bindSecurePort + "/http-bind/";
    }

    public String getJavaScriptUrl() {
        return "http://" + XMPPServer.getInstance().getServerInfo().getXMPPDomain() + ":" +
                bindPort + "/scripts/";
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
        changeHttpBindPorts(unsecurePort, securePort);
        bindPort = unsecurePort;
        bindSecurePort = securePort;
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

    private synchronized void changeHttpBindPorts(int unsecurePort, int securePort)
            throws Exception {
        if (unsecurePort < 0 && securePort < 0) {
            throw new IllegalArgumentException("At least one port must be greater than zero.");
        }
        if (unsecurePort == securePort) {
            throw new IllegalArgumentException("Ports must be distinct.");
        }

        if (httpBindServer != null) {
            try {
                httpBindServer.stop();
            }
            catch (Exception e) {
                Log.error("Error stopping http bind server", e);
            }
        }

        configureHttpBindServer(unsecurePort, securePort);
        httpBindServer.start();
    }

    /**
     * Starts an HTTP Bind server on the specified port and secure port.
     *
     * @param port the port to start the normal (unsecured) HTTP Bind service on.
     * @param securePort the port to start the TLS (secure) HTTP Bind service on.
     */
    private synchronized void configureHttpBindServer(int port, int securePort) {
        httpBindServer = new Server();
        final QueuedThreadPool tp = new QueuedThreadPool(254);
        tp.setName("Jetty-QTP-BOSH");
        httpBindServer.setThreadPool(tp);
        
        createConnector(port);
        createSSLConnector(securePort);
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

        createBoshHandler(contexts, "/http-bind");
        createCrossDomainHandler(contexts, "/");
        loadStaticDirectory(contexts);

        HandlerCollection collection = new HandlerCollection();
        httpBindServer.setHandler(collection);
        collection.setHandlers(new Handler[] { contexts, new DefaultHandler() });
    }

    private void createBoshHandler(ContextHandlerCollection contexts, String boshPath)
    {
        ServletContextHandler context = new ServletContextHandler(contexts, boshPath, ServletContextHandler.SESSIONS);
        context.addServlet(new ServletHolder(new HttpBindServlet()),"/*");
    }

    private void createCrossDomainHandler(ContextHandlerCollection contexts, String crossPath)
    {
        ServletContextHandler context = new ServletContextHandler(contexts, crossPath, ServletContextHandler.SESSIONS);
        context.addServlet(new ServletHolder(new HttpBindServlet()),"/crossdomain.xml");
    }

    private void loadStaticDirectory(ContextHandlerCollection contexts) {
        File spankDirectory = new File(JiveGlobals.getHomeDirectory() + File.separator
                + "resources" + File.separator + "spank");
        if (spankDirectory.exists()) {
            if (spankDirectory.canRead()) {
                WebAppContext context = new WebAppContext(contexts, spankDirectory.getPath(), "/");
                context.setWelcomeFiles(new String[]{"index.html"});
            }
            else {
                Log.warn("Openfire cannot read the directory: " + spankDirectory);
            }
        }
    }

    public ContextHandlerCollection getContexts() {
        return contexts;
    }

    private void doEnableHttpBind(boolean shouldEnable) {
        if (shouldEnable && httpBindServer == null) {
            try {
                changeHttpBindPorts(JiveGlobals.getIntProperty(HTTP_BIND_PORT,
                        HTTP_BIND_PORT_DEFAULT), JiveGlobals.getIntProperty(HTTP_BIND_SECURE_PORT,
                        HTTP_BIND_SECURE_PORT_DEFAULT));
            }
            catch (Exception e) {
                Log.error("Error configuring HTTP binding ports", e);
            }
        }
        else if (!shouldEnable && httpBindServer != null) {
            try {
                httpBindServer.stop();
            }
            catch (Exception e) {
                Log.error("Error stopping HTTP bind service", e);
            }
            httpBindServer = null;
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

    private void setUnsecureHttpBindPort(int value) {
        if (value == bindPort) {
            return;
        }
        try {
            changeHttpBindPorts(value, JiveGlobals.getIntProperty(HTTP_BIND_SECURE_PORT,
                    HTTP_BIND_SECURE_PORT_DEFAULT));
            bindPort = value;
        }
        catch (Exception ex) {
            Log.error("Error setting HTTP bind ports", ex);
        }
    }

    private void setSecureHttpBindPort(int value) {
        if (value == bindSecurePort) {
            return;
        }
        try {
            changeHttpBindPorts(JiveGlobals.getIntProperty(HTTP_BIND_PORT,
                    HTTP_BIND_PORT_DEFAULT), value);
            bindSecurePort = value;
        }
        catch (Exception ex) {
            Log.error("Error setting HTTP bind ports", ex);
        }
    }

    private synchronized void restartServer() {
        if (httpBindServer != null) {
            try {
                httpBindServer.stop();
            }
            catch (Exception e) {
                Log.error("Error stopping http bind server", e);
            }

            configureHttpBindServer(getHttpBindUnsecurePort(), getHttpBindSecurePort());
        }
    }

    /** Listens for changes to Jive properties that affect the HTTP server manager. */
    private class HttpServerPropertyListener implements PropertyEventListener {

        public void propertySet(String property, Map<String, Object> params) {
            if (property.equalsIgnoreCase(HTTP_BIND_ENABLED)) {
                doEnableHttpBind(Boolean.valueOf(params.get("value").toString()));
            }
            else if (property.equalsIgnoreCase(HTTP_BIND_PORT)) {
                int value;
                try {
                    value = Integer.valueOf(params.get("value").toString());
                }
                catch (NumberFormatException ne) {
                    JiveGlobals.deleteProperty(HTTP_BIND_PORT);
                    return;
                }
                setUnsecureHttpBindPort(value);
            }
            else if (property.equalsIgnoreCase(HTTP_BIND_SECURE_PORT)) {
                int value;
                try {
                    value = Integer.valueOf(params.get("value").toString());
                }
                catch (NumberFormatException ne) {
                    JiveGlobals.deleteProperty(HTTP_BIND_SECURE_PORT);
                    return;
                }
                setSecureHttpBindPort(value);
            }
        }

        public void propertyDeleted(String property, Map<String, Object> params) {
            if (property.equalsIgnoreCase(HTTP_BIND_ENABLED)) {
                doEnableHttpBind(HTTP_BIND_ENABLED_DEFAULT);
            }
            else if (property.equalsIgnoreCase(HTTP_BIND_PORT)) {
                setUnsecureHttpBindPort(HTTP_BIND_PORT_DEFAULT);
            }
            else if (property.equalsIgnoreCase(HTTP_BIND_SECURE_PORT)) {
                setSecureHttpBindPort(HTTP_BIND_SECURE_PORT_DEFAULT);
            }
        }

        public void xmlPropertySet(String property, Map<String, Object> params) {
        }

        public void xmlPropertyDeleted(String property, Map<String, Object> params) {
        }
    }

    private class CertificateListener implements CertificateEventListener {

        public void certificateCreated(KeyStore keyStore, String alias, X509Certificate cert) {
            // If new certificate is RSA then (re)start the HTTPS service
            if ("RSA".equals(cert.getPublicKey().getAlgorithm())) {
                restartServer();
            }
        }

        public void certificateDeleted(KeyStore keyStore, String alias) {
            restartServer();
        }

        public void certificateSigned(KeyStore keyStore, String alias,
                                      List<X509Certificate> certificates) {
            // If new certificate is RSA then (re)start the HTTPS service
            if ("RSA".equals(certificates.get(0).getPublicKey().getAlgorithm())) {
                restartServer();
            }
        }
    }
}
