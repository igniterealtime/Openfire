/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire;

import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.http.FlashCrossDomainServlet;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.mortbay.jetty.Connector;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.ContextHandler;
import org.mortbay.jetty.handler.ContextHandlerCollection;
import org.mortbay.jetty.handler.DefaultHandler;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.mortbay.jetty.servlet.ServletHandler;

/**
 * Sets up the "legacy" flash cross domain servlet, served off port 5229.
 * 
 * @author Daniel Henninger
 *
 */
public class FlashCrossDomainHandler extends BasicModule {

	private Server crossDomainServer;
    private Connector crossDomainConnector;
    private ContextHandlerCollection contexts;
    private int servletPort = 5229;

    public FlashCrossDomainHandler() {
        super("Flash CrossDomain Handler");
        
        // Configure Jetty logging to a more reasonable default.
        System.setProperty("org.mortbay.log.class", "org.jivesoftware.util.log.util.JettyLog");
        // JSP 2.0 uses commons-logging, so also override that implementation.
        System.setProperty("org.apache.commons.logging.LogFactory", "org.jivesoftware.util.log.util.CommonsLogFactory");
        
        contexts = new ContextHandlerCollection();
    }
    
    public Integer getPort() {
    	if (crossDomainConnector != null) {
			return crossDomainConnector.getLocalPort();
    	}
    	else {
    		return null;
    	}
    }

    public void start() {
        configureCrossDomainServer(servletPort);

        try {
            crossDomainServer.start();
        }
        catch (Exception e) {
            Log.error("Error starting cross domain service", e);
        }
    }

    public void stop() {
        if (crossDomainServer != null) {
            try {
                crossDomainServer.stop();
            }
            catch (Exception e) {
                Log.error("Error stoping cross domain service", e);
            }
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

    private void createConnector(int port) {
        crossDomainConnector = null;
        if (port > 0) {
            SelectChannelConnector connector = new SelectChannelConnector();
            // Listen on a specific network interface if it has been set.
            connector.setHost(getBindInterface());
            connector.setPort(port);
            crossDomainConnector = connector;
        }
    }
    
    private synchronized void configureCrossDomainServer(int port) {
        crossDomainServer = new Server();
        createConnector(port);
        if (crossDomainConnector == null) {
            crossDomainServer = null;
            return;
        }
        else {
            crossDomainServer.addConnector(crossDomainConnector);
        }

        createCrossDomainHandler(contexts, "/");

        crossDomainServer.setHandlers(new Handler[]{contexts, new DefaultHandler()});
    }

    private void createCrossDomainHandler(ContextHandlerCollection contexts, String crossPath) {
        ServletHandler handler = new ServletHandler();
        handler.addServletWithMapping(FlashCrossDomainServlet.class, "/crossdomain.xml");

        ContextHandler crossContextHandler = new ContextHandler(contexts, crossPath);
        crossContextHandler.setHandler(handler);
    }

}
