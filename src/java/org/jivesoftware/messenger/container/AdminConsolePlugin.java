/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.container;

import org.jivesoftware.messenger.JiveGlobals;
import org.jivesoftware.messenger.XMPPServer;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import java.io.File;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.WebApplicationContext;
import org.mortbay.log.*;

/**
 * The admin console plugin. It starts a Jetty instance on the configured
 * port and loads the admin console web application.
 *
 * @author Matt Tucker
 */
public class AdminConsolePlugin implements Plugin {

    private static Server jetty = null;
    private String port = null;

    /**
     * Create a jetty module.
     */
    public AdminConsolePlugin() {
    }

    public String getName() {
        return "Admin Console";
    }

    public String getDescription() {
        return "Web-based admin console for Jive Messenger.";
    }

    public String getAuthor() {
        return "Jive Software";
    }

    public String getVersion() {
        return "2.0";
    }

    public void initialize(PluginManager manager, File pluginDir) {
        try {
            // Configure logging to a file, creating log dir if needed
            System.setProperty("org.apache.commons.logging.LogFactory","org.mortbay.log.Factory");
            File logDir = new File(JiveGlobals.getMessengerHome(), "logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            File logFile = new File(logDir, "admin-console.log");
            OutputStreamLogSink logSink = new OutputStreamLogSink(logFile.toString());
            logSink.start();
            LogImpl log = (LogImpl)Factory.getFactory().getInstance("");
            // Ignore INFO logs.
            log.setVerbose(-1);
            log.add(logSink);

            jetty = new Server();

            // Configure HTTP socket listener
            port = JiveGlobals.getXMLProperty("adminConsole.port", "9090");
            jetty.addListener(port);

            // Add web-app
            WebApplicationContext webAppContext = jetty.addWebApplication("/",
                    pluginDir.getAbsoluteFile() + File.separator + "webapp");
            webAppContext.setWelcomeFiles(new String[]{"index.jsp"});

            jetty.start();

            Log.info("Started admin console on port: " + port);
            System.out.println("Admin console listening at http://" +
                    XMPPServer.getInstance().getServerInfo().getName() + ":" + port);
        }
        catch (Exception e) {
            Log.error("Trouble initializing admin console", e);
        }
    }

    public void destroy() {
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

    /**
     * Returns the Jetty instance started by this plugin.
     *
     * @return the Jetty server instance.
     */
    public static Server getJettyServer() {
        return jetty;
    }
}