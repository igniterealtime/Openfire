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
import org.jivesoftware.messenger.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import java.io.File;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.WebApplicationContext;
import org.mortbay.log.OutputStreamLogSink;

/**
 * @author Derek DeMoro
 */
public class JettyStarter {

    private Server jetty = null;
    private WebApplicationContext webAppContext = null;
    private String port = null;

    /**
     * Create a jetty starter
     */
    public JettyStarter() {
        try {
            String home = JiveGlobals.getMessengerHome();

            // Configure logging to a file, creating log dir if needed
            File logDir = new File(home, "logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            File logFile = new File(logDir, "embedded-web.log");
            OutputStreamLogSink logSink = new OutputStreamLogSink(logFile.toString());
            logSink.start();

            jetty = new Server();

            // Configure HTTP socket listener
            port = JiveGlobals.getXMLProperty("embedded-web.port");
            if(port == null){
                port = "9090";
            }
            jetty.addListener(port);

            // Add web-app
            webAppContext = jetty.addWebApplication("/", "C:\\repository\\messenger\\target\\release\\jive_messenger_2_0_0\\webapp");
            start();
        }
        catch (Exception e) {
            Log.error("Trouble initializing Jetty", e);
        }
    }

    public void start() {
        try {
            jetty.start();
            webAppContext.setWelcomeFiles(new String[]{"index.jsp", "index.html"});
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
        }
        catch (InterruptedException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
    }

    public void destroy() {
    }

    public static void main(String args[]){
        new JettyStarter();
    }
}