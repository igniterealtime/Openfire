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
package org.jivesoftware.wildfire.http;

import org.mortbay.jetty.Server;
import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.security.SslSocketConnector;
import org.mortbay.jetty.servlet.ServletHolder;
import org.mortbay.jetty.servlet.ServletHandler;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.net.SSLConfig;

import javax.net.ssl.SSLServerSocketFactory;

/**
 * Manages connections to the server which use the HTTP Bind protocol specified in <a
 * href="http://www.xmpp.org/extensions/xep-0124.html">XEP-0124</a>. The manager maps a servlet to
 * an embedded servlet container using the ports provided in the constructor.
 *
 * @author Alexander Wenckus
 */
public class HttpBindManager {
    private int plainPort;
    private int sslPort;
    private Server server;
    private String serverName;

    public HttpBindManager(String serverName, int plainPort, int sslPort) {
        this.plainPort = plainPort;
        this.sslPort = sslPort;
        this.server = new Server();
        this.serverName = serverName;
    }

    /**
     * Starts the HTTP Bind service.
     *
     * @throws Exception if there is an error starting up the server.
     */
    public void startup() throws Exception {
        for(Connector connector : createConnectors()) {
            server.addConnector(connector);
        }
        server.addHandler(createServletHandler());

        server.start();
    }

    private Handler createServletHandler() {
        ServletHolder servletHolder = new ServletHolder(
                new HttpBindServlet(new HttpSessionManager(serverName)));
        ServletHandler servletHandler = new ServletHandler();
        servletHandler.addServletWithMapping(servletHolder, "/");
        return servletHandler;
    }

    private Connector[] createConnectors() {
        SelectChannelConnector connector = new SelectChannelConnector();
        connector.setPort(plainPort);

        if (sslPort > 0) {
            try {
                SslSocketConnector secureConnector = new JiveSslConnector();
                secureConnector.setPort(sslPort);

                secureConnector.setTrustPassword(SSLConfig.getTrustPassword());
                secureConnector.setTruststoreType(SSLConfig.getStoreType());
                secureConnector.setTruststore(SSLConfig.getTruststoreLocation());
                secureConnector.setNeedClientAuth(false);
                secureConnector.setWantClientAuth(false);

                secureConnector.setKeyPassword(SSLConfig.getKeyPassword());
                secureConnector.setKeystoreType(SSLConfig.getStoreType());
                secureConnector.setKeystore(SSLConfig.getKeystoreLocation());
                
                return new Connector[]{connector, secureConnector};
            }
            catch (Exception ex) {
                Log.error("Error establishing SSL connector for HTTP Bind", ex);
            }
        }

        return new Connector[]{connector};
    }

    /**
     * Shutdown the HTTP Bind service, freeing any related resources.
     *
     * @throws Exception if there is an error shutting down the service.
     */
    public void shutdown() throws Exception {
        server.stop();
    }

    private class JiveSslConnector extends SslSocketConnector {

        @Override
        protected SSLServerSocketFactory createFactory() throws Exception {
            return SSLConfig.getServerSocketFactory();
        }
    }
}
