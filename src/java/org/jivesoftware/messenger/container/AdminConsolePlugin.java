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
import org.mortbay.http.SunJsseListener;
import org.mortbay.http.HttpListener;
import org.mortbay.util.InetAddrPort;

/**
 * The admin console plugin. It starts a Jetty instance on the configured
 * port and loads the admin console web application.
 *
 * @author Matt Tucker
 */
public class AdminConsolePlugin implements Plugin {

    private static Server jetty = null;
    private int port;
    private int securePort;

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

    public void initializePlugin(PluginManager manager, File pluginDir) {
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

            // Configure HTTP socket secureListener
            port = JiveGlobals.getXMLProperty("adminConsole.port", 9090);
            String domain = JiveGlobals.getProperty("xmpp.domain");

            HttpListener httpListener = jetty.addListener(new InetAddrPort(domain, port));

            SunJsseListener secureListener = new SunJsseListener();
            boolean secureStarted = false;
            try {

                // Get the keystore location. The default location is security/keystore
                String keyStoreLocation = JiveGlobals.getProperty("xmpp.socket.ssl.keystore",
                    "resources" + File.separator + "security" + File.separator + "keystore");
                keyStoreLocation = JiveGlobals.getMessengerHome() + File.separator + keyStoreLocation;

                // Get the keystore password. The default password is "changeit".
                String keypass = JiveGlobals.getProperty("xmpp.socket.ssl.keypass", "changeit");
                keypass = keypass.trim();

                // Get the truststore location; default at security/truststore
                String trustStoreLocation = JiveGlobals.getProperty("xmpp.socket.ssl.truststore",
                        "resources" + File.separator + "security" + File.separator + "truststore");
                trustStoreLocation = JiveGlobals.getMessengerHome() + File.separator + trustStoreLocation;

                // Get the truststore passwprd; default is "changeit".
                String trustpass = JiveGlobals.getProperty("xmpp.socket.ssl.trustpass", "changeit");
                trustpass = trustpass.trim();

                secureListener.setHost(domain);
                secureListener.setKeystore(keyStoreLocation);
                secureListener.setKeyPassword(keypass);
                secureListener.setPassword(keypass);
                securePort = JiveGlobals.getXMLProperty("adminConsole.securePort", 9091);
                secureListener.setPort(securePort);

                jetty.addListener(secureListener);
                secureStarted = true;
            }
            catch (Exception e) {
                Log.error(e);
            }

            // Add web-app
            WebApplicationContext webAppContext = jetty.addWebApplication("/",
                    pluginDir.getAbsoluteFile() + File.separator + "webapp");
            webAppContext.setWelcomeFiles(new String[]{"index.jsp"});

            jetty.start();

            Log.info("Started admin console on port: " + port);
            if (!secureStarted) {
                System.out.println("Admin console listening at http://" +
                        XMPPServer.getInstance().getServerInfo().getName() + ":" + port);
            }
            else {
                Log.info("Started secure admin console on port: " + securePort);
                System.out.println("Admin console listening at:");
                System.out.println("  http://" +
                        httpListener.getHost() + ":" + port);
                System.out.println("  https://" +
                         secureListener.getHost()+ ":" + securePort);
            }
        }
        catch (Exception e) {
            Log.error("Trouble initializing admin console", e);
        }
    }

    public void destroyPlugin() {
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