/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire;

import org.apache.commons.logging.LogConfigurationException;
import org.apache.commons.logging.LogFactory;
import org.jivesoftware.util.*;
import org.jivesoftware.wildfire.net.SSLConfig;
import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.mortbay.jetty.security.SslSocketConnector;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHandler;
import org.mortbay.jetty.servlet.ServletHolder;
import org.mortbay.jetty.servlet.ServletMapping;
import org.mortbay.log.Logger;

import javax.net.ssl.SSLServerSocketFactory;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Manages the instances of Jetty which provide the admin console funtionality and the HTTP binding
 * functionality.
 *
 * @author Alexander Wenckus
 */
public class HttpServerManager {

    private static final HttpServerManager instance = new HttpServerManager();

    public static final String ADMIN_CONSOLE_PORT = "adminConsole.port";

    public static final int ADMIN_CONSOLE_PORT_DEFAULT = 9090;

    public static final String ADMIN_CONOSLE_SECURE_PORT = "adminConsole.securePort";

    public static final int ADMIN_CONSOLE_SECURE_PORT_DEFAULT = 9091;

    public static final String HTTP_BIND_ENABLED = "httpbind.enabled";

    public static final boolean HTTP_BIND_ENABLED_DEFAULT = true;

    public static final String HTTP_BIND_PORT = "httpbind.port.plain";

    public static final int HTTP_BIND_PORT_DEFAULT = 8080;

    public static final String HTTP_BIND_SECURE_PORT = "httpbind.port.secure";

    public static final int HTTP_BIND_SECURE_PORT_DEFAULT = 8483;


    /**
     * Returns an HTTP server manager instance (singleton).
     *
     * @return an HTTP server manager instance.
     */
    public static HttpServerManager getInstance() {
        return instance;
    }

    private int bindPort;
    private int adminPort;
    private int bindSecurePort;
    private int adminSecurePort;
    private Server adminServer;
    private Server httpBindServer;
    private Context adminConsoleContext;
    private Map<ServletHolder, String> httpBindContexts;
    private CertificateEventListener certificateListener;
    private boolean restartNeeded = false;

    /**
     * Constructs a new HTTP server manager.
     */
    private HttpServerManager() {
        PropertyEventDispatcher.addListener(new HttpServerPropertyListener());

        // Configure Jetty logging to a more reasonable default.
        System.setProperty("org.mortbay.log.class",
                "org.jivesoftware.wildfire.HttpServerManager$JettyLog");
        // JSP 2.0 uses commons-logging, so also override that implementation.
        System.setProperty("org.apache.commons.logging.LogFactory",
                "org.jivesoftware.wildfire.HttpServerManager$CommonsLogFactory");
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
     * Sets up the parameters for the HTTP binding service.
     *
     * @param contexts a collection of servlets utilized by the HTTP binding service.
     */
    public void setHttpBindContext(Map<ServletHolder,String> contexts) {
        this.httpBindContexts = contexts;
    }

    /**
     * Starts any neccesary Jetty instances. If the admin console and http-binding are running on
     * seperate ports then two jetty instances are started, if not then only one is started. The
     * proper contexts are then added to the Jetty servers.
     */
    public void startup() {
        restartNeeded = false;
        // Add listener for certificate events
        certificateListener = new CertificateListener();
        CertificateManager.addListener(certificateListener);

        if (httpBindContexts != null && isHttpBindServiceEnabled()) {
            bindPort = JiveGlobals.getIntProperty(HTTP_BIND_PORT, ADMIN_CONSOLE_PORT_DEFAULT);
            bindSecurePort = JiveGlobals.getIntProperty(HTTP_BIND_SECURE_PORT,
                    ADMIN_CONSOLE_SECURE_PORT_DEFAULT);
            startHttpBindServer(bindPort, bindSecurePort);
        }
        if (adminConsoleContext != null) {
            createAdminConsoleServer();
        }

        addContexts();

        if (httpBindServer != null) {
            try {
                httpBindServer.start();
            }
            catch (Exception e) {
                Log.error("Could not start HTTP bind server", e);
            }
        }
        if (adminServer != null && adminServer != httpBindServer) {
            try {
                adminServer.start();
            }
            catch (Exception e) {
                Log.error("Could not start admin conosle server", e);
            }
        }
    }

    /**
     * Shuts down any Jetty servers that are running the admin console and HTTP binding service.
     */
    public void shutdown() {
        // Remove listener for certificate events
        if (certificateListener != null) {
            CertificateManager.removeListener(certificateListener);
        }

        try {
            if (httpBindServer != null && httpBindServer.isRunning()) {
                httpBindServer.stop();
            }
            if (httpBindContexts != null) {
                for(ServletHolder httpBindContext : httpBindContexts.keySet()) {
                    httpBindContext.stop();
                }
            }
        }
        catch (Exception e) {
            Log.error("Error stopping HTTP bind server", e);
        }
        httpBindServer = null;
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
     * Returns true if the HTTP binding server is currently enabled.
     *
     * @return true if the HTTP binding server is currently enabled.
     */
    public boolean isHttpBindEnabled() {
        return httpBindServer != null && httpBindServer.isRunning();
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
     * Returns true if the HTTP binding service is running on a seperate server than the admin
     * console.
     *
     * @return true if the HTTP binding service is running on a seperate server than the admin
     *         console.
     */
    public boolean isSeperateHttpBindServerConfigured() {
        return (httpBindServer != null && httpBindServer != adminServer) || (httpBindServer == null
                && (getAdminUnsecurePort() != JiveGlobals.getIntProperty(HTTP_BIND_PORT, 9090)
                || getAdminSecurePort()
                != JiveGlobals.getIntProperty(HTTP_BIND_SECURE_PORT, 9091)));
    }

    public void setHttpBindEnabled(boolean isEnabled) {
        JiveGlobals.setProperty(HTTP_BIND_ENABLED, String.valueOf(isEnabled));
    }

    /**
     * Set the ports on which the HTTP binding service will be running.
     *
     * @param unsecurePort the unsecured connection port which clients can connect to.
     * @param securePort the secured connection port which clients can connect to.
     */
    public void setHttpBindPorts(int unsecurePort, int securePort) {
        changeHttpBindPorts(unsecurePort, securePort);
        bindPort = unsecurePort;
        bindSecurePort = securePort;
        if (unsecurePort != getAdminUnsecurePort()) {
            JiveGlobals.setProperty(HTTP_BIND_PORT, String.valueOf(unsecurePort));
        }
        else {
            JiveGlobals.deleteProperty(HTTP_BIND_PORT);
        }
        if (securePort != getAdminSecurePort()) {
            JiveGlobals.setProperty(HTTP_BIND_SECURE_PORT, String.valueOf(securePort));
        }
        else {
            JiveGlobals.deleteProperty(HTTP_BIND_SECURE_PORT);
        }
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

    /**
     * Starts an HTTP Bind server on the specified port and secure port.
     *
     * @param port the port to start the normal (unsecured) HTTP Bind service on.
     * @param securePort the port to start the TLS (secure) HTTP Bind service on.
     */
    private void startHttpBindServer(int port, int securePort) {
        httpBindServer = new Server();
        Connector httpConnector = createConnector(port);
        Connector httpsConnector = createSSLConnector(securePort);
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
    }

    private void createAdminConsoleServer() {
        adminPort = JiveGlobals.getXMLProperty(ADMIN_CONSOLE_PORT, ADMIN_CONSOLE_PORT_DEFAULT);
        adminSecurePort = JiveGlobals.getXMLProperty(ADMIN_CONOSLE_SECURE_PORT,
                ADMIN_CONSOLE_SECURE_PORT_DEFAULT);
        boolean loadConnectors = true;
        if (httpBindServer != null) {
            // Admin console and HTTP binding running on the same ports
            if (adminPort == bindPort && adminSecurePort == bindSecurePort) {
                adminServer = httpBindServer;
                loadConnectors = false;
            }
            // Only one of the two ports is shared between the servers, we will start them both
            // on the Admin console ports and log a warning saying that you can't do that.
            else if (checkPorts(new int[]{adminPort, adminSecurePort},
                    new int[]{bindPort, bindSecurePort}))
            {
                Log.warn("HTTP bind ports must be either the same or distinct from admin console" +
                        " ports.");
                httpBindServer = null;
                httpBindServer = new Server();
                adminServer = httpBindServer;
            }
            // The admin server ports are distinct from the HTTP ports so we can start the admin
            // console seperatly
            else {
                adminServer = new Server();
            }
        }
        else {
            adminServer = new Server();
        }

        if (loadConnectors) {
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

    private boolean checkPorts(int[] httpBindPorts, int[] adminConsolePorts) {
        return httpBindPorts[0] == adminConsolePorts[0] || httpBindPorts[0] == adminConsolePorts[0]
                || httpBindPorts[1] == adminConsolePorts[0]
                || httpBindPorts[1] == adminConsolePorts[1];
    }

    private boolean isHttpBindServiceEnabled() {
        return JiveGlobals.getBooleanProperty(HTTP_BIND_ENABLED, HTTP_BIND_ENABLED_DEFAULT);
    }

    private void addContexts() {
        if (httpBindServer == adminServer && httpBindServer != null) {
            for (Map.Entry<ServletHolder, String> httpBindContext : httpBindContexts.entrySet()) {
                adminConsoleContext.addServlet(httpBindContext.getKey(),
                        httpBindContext.getValue());
            }
            if (adminServer.getHandler() == null) {
                adminServer.addHandler(adminConsoleContext);
            }
            return;
        }
        if (adminServer != null) {
            if (adminServer.getHandler() != null) {
                removeHttpBindServlet(adminConsoleContext);
            }
            else {
                adminServer.addHandler(adminConsoleContext);
            }
        }
        if (httpBindServer != null) {
            ServletHandler servletHandler = new ServletHandler();
            for (Map.Entry<ServletHolder, String> httpBindContext : httpBindContexts.entrySet()) {
                servletHandler.addServletWithMapping(httpBindContext.getKey(),
                        httpBindContext.getValue());
            }
            httpBindServer.addHandler(servletHandler);
        }
    }

    /**
     * Removes the HTTP-bind servlet from the Admin console servlet mappings.
     *
     * @param adminConsoleContext The admin console context.
     */
    private void removeHttpBindServlet(Context adminConsoleContext) {
        ServletHandler handler = adminConsoleContext.getServletHandler();
        ServletMapping[] servletMappings = handler.getServletMappings();
        List<ServletMapping> toAdd = new ArrayList<ServletMapping>();
        List<String> servletNames = new ArrayList<String>();
        for(ServletHolder holder : httpBindContexts.keySet()) {
            servletNames.add(holder.getName());
        }
        for (ServletMapping mapping : servletMappings) {
            if (servletNames.contains(mapping.getServletName())) {
                continue;
            }
            toAdd.add(mapping);
        }

        List<ServletHolder> toAddServlets = new ArrayList<ServletHolder>();
        for(ServletHolder holder : handler.getServlets()) {
            toAddServlets.add(holder);
        }
        toAddServlets.removeAll(httpBindContexts.keySet());

        handler.setServletMappings(toAdd.toArray(new ServletMapping[toAdd.size()]));
        handler.setServlets(toAddServlets.toArray(new ServletHolder[toAddServlets.size()]));
    }

    private Connector createConnector(int port) {
        if (port > 0) {
            SelectChannelConnector connector = new SelectChannelConnector();
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

    private void changeHttpBindPorts(int unsecurePort, int securePort) {
        if (unsecurePort < 0 && securePort < 0) {
            throw new IllegalArgumentException("At least one port must be greater than zero.");
        }
        if (unsecurePort == securePort) {
            throw new IllegalArgumentException("Ports must be distinct.");
        }
        int adminPort = JiveGlobals.getXMLProperty(ADMIN_CONSOLE_PORT, ADMIN_CONSOLE_PORT_DEFAULT);
        int adminSecurePort = JiveGlobals.getXMLProperty(ADMIN_CONOSLE_SECURE_PORT,
                ADMIN_CONSOLE_SECURE_PORT_DEFAULT);
        // Check to see if any of the ports are the same.
        if (checkPorts(new int[]{unsecurePort, securePort},
                new int[]{adminPort, adminSecurePort}))
        {
            if (unsecurePort != adminPort || securePort != adminSecurePort) {
                Log.warn("HTTP bind ports must be either the same or distinct from admin console" +
                        " ports, HTTP binding will run on the admin console ports.");
            }
            if (httpBindServer == adminServer) {
                return;
            }
            if (httpBindServer != null) {
                try {
                    httpBindServer.stop();
                }
                catch (Exception e) {
                    Log.error("Error stopping HTTP bind service", e);
                }
                httpBindServer = null;
            }
            httpBindServer = adminServer;
            addContexts();
            return;
        }

        if (httpBindServer != adminServer) {
            try {
                httpBindServer.stop();
            }
            catch (Exception e) {
                Log.error("Error stopping HTTP bind service", e);
            }
        }
        startHttpBindServer(unsecurePort, securePort);
        addContexts();
        try {
            httpBindServer.start();
        }
        catch (Exception e) {
            Log.error("Error starting HTTP bind service", e);
        }
    }

    private void doEnableHttpBind(boolean shouldEnable) {
        if (shouldEnable && httpBindServer == null) {
            changeHttpBindPorts(JiveGlobals.getIntProperty(HTTP_BIND_PORT,
                    ADMIN_CONSOLE_PORT_DEFAULT), JiveGlobals.getIntProperty(HTTP_BIND_SECURE_PORT,
                    ADMIN_CONSOLE_SECURE_PORT_DEFAULT));
        }
        else if (!shouldEnable && httpBindServer != null) {
            if (httpBindServer != adminServer) {
                try {
                    httpBindServer.stop();
                }
                catch (Exception e) {
                    Log.error("Error stopping HTTP bind service", e);
                }
                httpBindServer = null;
            }
            else {
                removeHttpBindServlet(adminConsoleContext);
                httpBindServer = null;
            }
        }
    }

    private void setUnsecureHttpBindPort(int value) {
        if (value == bindPort) {
            return;
        }
        try {
            changeHttpBindPorts(value, JiveGlobals.getIntProperty(HTTP_BIND_SECURE_PORT,
                    ADMIN_CONSOLE_SECURE_PORT_DEFAULT));
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
                    ADMIN_CONSOLE_PORT_DEFAULT), value);
            bindSecurePort = value;
        }
        catch (Exception ex) {
            Log.error("Error setting HTTP bind ports", ex);
        }
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

    /**
     * Listens for changes to Jive properties that affect the HTTP server manager.
     */
    private class HttpServerPropertyListener implements PropertyEventListener {

        public void propertySet(String property, Map params) {
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

        public void propertyDeleted(String property, Map params) {
            if (property.equalsIgnoreCase(HTTP_BIND_ENABLED)) {
                doEnableHttpBind(HTTP_BIND_ENABLED_DEFAULT);
            }
            else if (property.equalsIgnoreCase(HTTP_BIND_PORT)) {
                setUnsecureHttpBindPort(ADMIN_CONSOLE_PORT_DEFAULT);
            }
            else if (property.equalsIgnoreCase(HTTP_BIND_SECURE_PORT)) {
                setSecureHttpBindPort(ADMIN_CONSOLE_SECURE_PORT_DEFAULT);
            }
        }

        public void xmlPropertySet(String property, Map params) {
        }

        public void xmlPropertyDeleted(String property, Map params) {
        }
    }

    private class JiveSslConnector extends SslSocketConnector {

        @Override
        protected SSLServerSocketFactory createFactory() throws Exception {
            return SSLConfig.getServerSocketFactory();
        }
    }

    /**
     * A Logger implementation to override the default Jetty logging behavior. All log statements
     * are written to the Wildfire logs. Info level logging is sent to debug.
     */
    public static class JettyLog implements Logger {

        /**
         * Only enable Jetty debug logging if it's specifically enabled. Otherwise, Jetty debug logs
         * pollute the Wildfire debug log with too much data.
         */
        private boolean debugEnabled = JiveGlobals.getBooleanProperty("jetty.debugEnabled");

        public boolean isDebugEnabled() {
            return debugEnabled && Log.isDebugEnabled();
        }

        public void setDebugEnabled(boolean b) {
            // Do nothing.
        }

        public void info(String string, Object object, Object object1) {
            // Send info log messages to debug because they are generally not useful.
            Log.debug(string);
        }

        public void debug(String string, Throwable throwable) {
            Log.debug(string, throwable);
        }

        public void debug(String string, Object object, Object object1) {
            Log.debug(string);
        }

        public void warn(String string, Object object, Object object1) {
            Log.warn(string);
        }

        public void warn(String string, Throwable throwable) {
            Log.warn(string, throwable);
        }

        public Logger getLogger(String string) {
            return new JettyLog();
        }
    }

    /**
     * A LogFactory implementation to override the default commons-logging behavior. All log
     * statements are written to the Wildfire logs. Info level logging is sent to debug.
     */
    public static class CommonsLogFactory extends LogFactory {

        private org.apache.commons.logging.Log log;

        public CommonsLogFactory() {
            log = new org.apache.commons.logging.Log() {

                public boolean isDebugEnabled() {
                    return Log.isDebugEnabled();
                }

                public boolean isErrorEnabled() {
                    return Log.isErrorEnabled();
                }

                public boolean isFatalEnabled() {
                    return Log.isErrorEnabled();
                }

                public boolean isInfoEnabled() {
                    return Log.isInfoEnabled();
                }

                public boolean isTraceEnabled() {
                    return Log.isDebugEnabled();
                }

                public boolean isWarnEnabled() {
                    return Log.isWarnEnabled();
                }

                public void trace(Object object) {
                    // Ignore.
                }

                public void trace(Object object, Throwable throwable) {
                    // Ignore.
                }

                public void debug(Object object) {
                    Log.debug(object.toString());
                }

                public void debug(Object object, Throwable throwable) {
                    Log.debug(object.toString(), throwable);
                }

                public void info(Object object) {
                    // Send info log messages to debug because they are generally not useful.
                    Log.debug(object.toString());
                }

                public void info(Object object, Throwable throwable) {
                    // Send info log messages to debug because they are generally not useful.
                    Log.debug(object.toString(), throwable);
                }

                public void warn(Object object) {
                    Log.warn(object.toString());
                }

                public void warn(Object object, Throwable throwable) {
                    Log.warn(object.toString(), throwable);
                }

                public void error(Object object) {
                    Log.error(object.toString());
                }

                public void error(Object object, Throwable throwable) {
                    Log.error(object.toString(), throwable);
                }

                public void fatal(Object object) {
                    Log.error(object.toString());
                }

                public void fatal(Object object, Throwable throwable) {
                    Log.error(object.toString(), throwable);
                }
            };
        }

        public Object getAttribute(String string) {
            return null;
        }

        public String[] getAttributeNames() {
            return new String[0];
        }

        public org.apache.commons.logging.Log getInstance(Class aClass)
                throws LogConfigurationException
        {
            return log;
        }

        public org.apache.commons.logging.Log getInstance(String string)
                throws LogConfigurationException
        {
            return log;
        }

        public void release() {

        }

        public void removeAttribute(String string) {

        }

        public void setAttribute(String string, Object object) {

        }
    }
}