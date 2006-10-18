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

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.net.SSLConfig;
import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;
import org.mortbay.jetty.servlet.ServletHandler;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.mortbay.jetty.security.SslSocketConnector;

import javax.net.ssl.SSLServerSocketFactory;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;

/**
 * Manages the instances of Jetty which provide the admin console funtionality and the http binding
 * functionality.
 *
 * @author Alexander Wenckus
 */
public class HttpServerManager {

    private static final HttpServerManager instance = new HttpServerManager();

    public static final String ADMIN_CONSOLE_PORT = "adminConsole.port";

    public static final String ADMIN_CONOSLE_SECURE_PORT = "adminConsole.securePort";

    public static final String HTTP_BIND_PORT = "httpbind.port.plain";

    public static final String HTTP_BIND_SECURE_PORT = "httpbind.port.secure";
    private String httpBindPath;

    public static HttpServerManager getInstance() {
        return instance;
    }

    private int port;
    private int securePort;
    private Server adminServer;
    private Server httpBindServer;
    private Context adminConsoleContext;
    private ServletHolder httpBindContext;

    private HttpServerManager() {
    }

    public void setAdminConsoleContext(Context context) {
        this.adminConsoleContext = context;
    }

    public void setHttpBindContext(ServletHolder context, String httpBindPath) {
        this.httpBindContext = context;
        this.httpBindPath = httpBindPath;
    }

    private void createHttpBindServer() {
        port = JiveGlobals.getIntProperty(HTTP_BIND_PORT, 9090);
        securePort = JiveGlobals.getIntProperty(HTTP_BIND_SECURE_PORT, 9091);

        httpBindServer = new Server();
        Collection<Connector> connectors = createAdminConsoleConnectors(port, securePort);
        if(connectors.size() == 0) {
            httpBindServer = null;
            return;
        }
        for (Connector connector : connectors) {
            httpBindServer.addConnector(connector);
        }
    }

    private void createAdminConsoleServer() {
        int port = JiveGlobals.getXMLProperty(ADMIN_CONSOLE_PORT, 9090);
        int securePort = JiveGlobals.getXMLProperty(ADMIN_CONOSLE_SECURE_PORT, 9091);
        boolean loadConnectors = true;
        if(httpBindServer != null) {
            if(port == this.port && securePort == this.securePort) {
                adminServer = httpBindServer;
                loadConnectors = false;
            }
            else if(port == this.port || port == this.securePort
                    || securePort == this.port || securePort == this.securePort) {
                Log.warn("Http bind ports must be either the same or distinct from admin console" +
                        " ports.");
                httpBindServer = null;
                httpBindServer = new Server();
                adminServer = httpBindServer;
            }
            else {
                adminServer = new Server();
            }
        }
        else {
            adminServer = new Server();
        }

        if (loadConnectors) {
            Collection<Connector> connectors = createAdminConsoleConnectors(port, securePort);
            if(connectors.size() == 0) {
                adminServer = null;
                return;
            }

            for (Connector connector : connectors) {
                adminServer.addConnector(connector);
            }
        }
    }

    public void startup() {
        if(httpBindContext != null) {
            createHttpBindServer();
        }
        if(adminConsoleContext != null) {
            createAdminConsoleServer();
        }

        addContexts();

        if(httpBindServer != null) {
            try {
                httpBindServer.start();
            }
            catch (Exception e) {
                Log.error("Could not start http bind server", e);
            }
        }
        if(adminServer != null && adminServer != httpBindServer) {
            try {
                adminServer.start();
            }
            catch (Exception e) {
                Log.error("Could not start admin conosle server", e);
            }
        }
    }

    private void addContexts() {
        if(httpBindServer == adminServer && httpBindServer != null) {
            adminConsoleContext.addServlet(httpBindContext, httpBindPath);
            adminServer.addHandler(adminConsoleContext);
            return;
        }
        if(httpBindServer != null) {
            ServletHandler servletHandler = new ServletHandler();
            servletHandler.addServletWithMapping(httpBindContext, httpBindPath);
            httpBindServer.addHandler(servletHandler);
        }
        if(adminServer != null) {
            adminServer.addHandler(adminConsoleContext);
            // TODO remove the http bind servlet
        }
    }

    public void shutdown() {
        if(httpBindServer != null) {
            try {
                httpBindServer.stop();
            }
            catch (Exception e) {
                Log.error("Error stopping http bind server", e);
            }
            httpBindServer = null;
        }
        if(adminServer != null && adminServer != httpBindServer) {
            try {
                adminServer.stop();
            }
            catch (Exception e) {
                Log.error("Error stopping admin console server", e);
            }
            adminServer = null;
        }
    }

    private Collection<Connector> createAdminConsoleConnectors(int port, int securePort) {
        List<Connector> connectorList = new ArrayList<Connector>();

        if (port > 0) {
            SelectChannelConnector connector = new SelectChannelConnector();
            connector.setPort(port);
            connectorList.add(connector);
        }

        try {
            if (securePort > 0) {
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
                connectorList.add(sslConnector);
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
        return connectorList;
    }

    private class JiveSslConnector extends SslSocketConnector {

        @Override
        protected SSLServerSocketFactory createFactory() throws Exception {
            return SSLConfig.getServerSocketFactory();
        }
    }
}
