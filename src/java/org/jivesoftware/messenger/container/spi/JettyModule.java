/*
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.container.spi;

import org.jivesoftware.messenger.container.*;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import java.io.File;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.WebApplicationContext;
import org.mortbay.log.OutputStreamLogSink;

/**
 * A simple wrapper that allows Jetty to run inside the Messenger
 * container. Jetty settings are extracted from the ModuleContext.
 * The Jetty module is primarily designed to host the JSP web
 * administration interface to the server when running in standalone
 * mode without an external servlet container.
 *
 * @author Iain Shigeoka
 */
public class JettyModule implements Module {

    private Server jetty = null;
    private WebApplicationContext webAppContext = null;
    private Container serverContainer = null;
    private ServiceRegistration reg = null;
    private String port = null;

    /**
     * Create a jetty module.
     */
    public JettyModule() {
    }

    public String getName() {
        return "Embedded Webserver";
    }

    public void initialize(ModuleContext context, Container container) {
        try {
            // Configure logging to a file, creating log dir if needed
            File logDir = context.getLogDirectory();
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            File logFile = new File(logDir, "embedded-web.log");
            OutputStreamLogSink logSink = new OutputStreamLogSink(logFile.toString());
            logSink.start();

            jetty = new Server();

            // Configure HTTP socket listener
            port = context.getProperty("embedded-web.port");
            jetty.addListener(port);
            this.serverContainer = container;

            // Add web-app
            webAppContext = jetty.addWebApplication("/",
                    context.getHomeDirectory() + "/webapp/");
        }
        catch (Exception e) {
            Log.error("Trouble initializing Jetty", e);
        }
    }

    public void start() {
        try {
            jetty.start();
            webAppContext.setWelcomeFiles(new String[]{"index.jsp", "index.html"});
            ServiceItem serverItem = new ServiceItem(null, this, null);
            reg = serverContainer.getServiceLookup().register(serverItem);
            Log.info("Started embedded web server on port: " + port);
        }
        catch (Exception e) {
            Log.error("Trouble starting Jetty", e);
            stop();
        }
    }

    public void stop() {
        try {
            if (jetty != null) {
                jetty.stop();
                jetty = null;
            }
            if (reg != null) {
                reg.cancel();
                reg = null;
            }
        }
        catch (InterruptedException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
    }

    public void destroy() {
    }
}