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

import org.jivesoftware.messenger.XMPPServer;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.mortbay.http.SunJsseListener;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.WebApplicationContext;
import org.mortbay.log.Factory;
import org.mortbay.log.LogImpl;
import org.mortbay.log.OutputStreamLogSink;
import org.mortbay.util.InetAddrPort;

import java.io.File;

/**
 * The admin console plugin. It starts a Jetty instance on the configured
 * port and loads the admin console web application.
 *
 * @author Matt Tucker
 */
public class AdminConsolePlugin implements Plugin {

    private static Server jetty = null;
    private String interfaceName;
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
            System.setProperty("org.apache.commons.logging.LogFactory", "org.mortbay.log.Factory");
            File logDir = new File(JiveGlobals.getHomeDirectory(), "logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            File logFile = new File(logDir, "admin-console.log");
            OutputStreamLogSink logSink = new OutputStreamLogSink(logFile.toString());
            logSink.start();
            LogImpl log = (LogImpl) Factory.getFactory().getInstance("");
            // Ignore INFO logs.
            log.setVerbose(-1);
            log.add(logSink);

            jetty = new Server();

            // Configure HTTP socket listener
            boolean plainStarted = false;
            // Setting this property to a not null value will imply that the Jetty server will only
            // accept connect requests to that IP address
            interfaceName = JiveGlobals.getXMLProperty("adminConsole.inteface");
            port = JiveGlobals.getXMLProperty("adminConsole.port", 9090);
            InetAddrPort address = new InetAddrPort(interfaceName, port);
            if (port > 0) {
                jetty.addListener(address);
                plainStarted = true;
            }

            boolean secureStarted = false;
            try {
                securePort = JiveGlobals.getXMLProperty("adminConsole.securePort", 9091);
                if (securePort > 0) {
                    SunJsseListener listener = new SunJsseListener();
                    // Get the keystore location. The default location is security/keystore
                    String keyStoreLocation = JiveGlobals.getProperty("xmpp.socket.ssl.keystore",
                            "resources" + File.separator + "security" + File.separator + "keystore");
                    keyStoreLocation = JiveGlobals.getHomeDirectory() + File.separator + keyStoreLocation;

                    // Get the keystore password. The default password is "changeit".
                    String keypass = JiveGlobals.getProperty("xmpp.socket.ssl.keypass", "changeit");
                    keypass = keypass.trim();

                    // Get the truststore location; default at security/truststore
                    String trustStoreLocation = JiveGlobals.getProperty("xmpp.socket.ssl.truststore",
                            "resources" + File.separator + "security" + File.separator + "truststore");
                    trustStoreLocation = JiveGlobals.getHomeDirectory() + File.separator +
                            trustStoreLocation;

                    // Get the truststore passwprd; default is "changeit".
                    String trustpass = JiveGlobals.getProperty("xmpp.socket.ssl.trustpass", "changeit");
                    trustpass = trustpass.trim();

                    listener.setKeystore(keyStoreLocation);
                    listener.setKeyPassword(keypass);
                    listener.setPassword(keypass);

                    listener.setHost(interfaceName);
                    listener.setPort(securePort);

                    jetty.addListener(listener);
                    secureStarted = true;
                }
            }
            catch (Exception e) {
                Log.error(e);
            }

            // Add web-app
            WebApplicationContext webAppContext = jetty.addWebApplication("/",
                    pluginDir.getAbsoluteFile() + File.separator + "webapp");
            webAppContext.setWelcomeFiles(new String[]{"index.jsp"});

            jetty.start();

            if (!plainStarted && !secureStarted) {
                Log.info("Warning: admin console not started due to configuration settings.");
                System.out.println("Warning: admin console not started due to configuration settings.");
            }
            else if (!plainStarted && secureStarted) {
                Log.info("Admin console listening at https://" +
                        XMPPServer.getInstance().getServerInfo().getName() + ":" + securePort);
                System.out.println("Admin console listening at https://" +
                        XMPPServer.getInstance().getServerInfo().getName() + ":" + securePort);
            }
            else if (!secureStarted && plainStarted) {
                Log.info("Admin console listening at http://" +
                        XMPPServer.getInstance().getServerInfo().getName() + ":" + port);
                System.out.println("Admin console listening at http://" +
                        XMPPServer.getInstance().getServerInfo().getName() + ":" + port);
            }
            else {
                String msg = "Admin console listening at:\n" +
                        "  http://" + XMPPServer.getInstance().getServerInfo().getName() + ":" +
                        port + "\n" +
                        "  https://" + XMPPServer.getInstance().getServerInfo().getName() + ":" +
                        securePort;
                Log.info(msg);
                System.out.println(msg);
            }
        }
        catch (Exception e) {
            System.err.println("Error starting admin console: " + e.getMessage()); 
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