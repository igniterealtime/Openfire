/**
 * $Revision: 3034 $
 * $Date: 2005-11-04 21:02:33 -0300 (Fri, 04 Nov 2005) $
 *
 * Copyright (C) 2004-2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.container;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.HttpServerManager;
import org.mortbay.jetty.webapp.WebAppContext;
import java.io.File;

/**
 * The admin console plugin. It starts a Jetty instance on the configured
 * port and loads the admin console web application.
 *
 * @author Matt Tucker
 */
public class AdminConsolePlugin implements Plugin {

    private File pluginDir;
    private HttpServerManager serverManager;

    /**
     * Create a jetty module.
     */
    public AdminConsolePlugin() {
        serverManager = HttpServerManager.getInstance();
    }

    public void initializePlugin(PluginManager manager, File pluginDir) {
        this.pluginDir = pluginDir;
        try {
            File logDir;
            String logDirectory = JiveGlobals.getXMLProperty("log.directory");
            // Check if the "log.directory" was defined
            if (logDirectory != null) {
                // Remove last separator character (if any)
                if (!logDirectory.endsWith(File.separator)) {
                    logDirectory = logDirectory + File.separator;
                }
                logDir = new File(logDirectory);
            }
            else {
                // Create log file in the default directory
                logDir = new File(JiveGlobals.getHomeDirectory(), "logs");
            }
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            
            serverManager.setAdminConsoleContext(createWebAppContext());
        }
        catch (Exception e) {
            System.err.println("Error starting admin console: " + e.getMessage()); 
            Log.error("Trouble initializing admin console", e);
        }
    }

    private WebAppContext createWebAppContext() {
        WebAppContext context;
        // Add web-app. Check to see if we're in development mode. If so, we don't
        // add the normal web-app location, but the web-app in the project directory.
        if (Boolean.getBoolean("developmentMode")) {
            System.out.println(LocaleUtils.getLocalizedString("admin.console.devmode"));
            context = new WebAppContext(
                    pluginDir.getParentFile().getParentFile().getParent() + File.separator +
                            "src" + File.separator + "web", "/");
        }
        else {
            context = new WebAppContext(pluginDir.getAbsoluteFile() + File.separator + "webapp",
                    "/");
        }
        context.setWelcomeFiles(new String[]{"index.jsp"});
        return context;
    }

    public void destroyPlugin() {
    }
}