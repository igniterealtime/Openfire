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
import org.jivesoftware.wildfire.XMPPServer;
import org.mortbay.http.HttpContext;
import org.mortbay.http.HttpListener;
import org.mortbay.http.SunJsseListener;
import org.mortbay.jetty.Server;
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
    private int port;
    private int securePort;

    private File pluginDir;
    private HttpContext context = null;
    private HttpListener plainListener = null;
    private HttpListener secureListener = null;

    /**
     * Create a jetty module.
     */
    public AdminConsolePlugin() {
    }

    public void restartListeners() {
        try {
            String restarting = LocaleUtils.getLocalizedString("admin.console.restarting");
            System.out.println(restarting);
            Log.info(restarting);

            jetty.stop();
            if (plainListener != null) {
                jetty.removeListener(plainListener);
                plainListener = null;
            }
            if (secureListener != null) {
                jetty.removeListener(secureListener);
                secureListener = null;
            }
            jetty.removeContext(context);
            loadListeners();

            // Add web-app. Check to see if we're in development mode. If so, we don't
            // add the normal web-app location, but the web-app in the project directory.
            if (Boolean.getBoolean("developmentMode")) {
                System.out.println(LocaleUtils.getLocalizedString("admin.console.devmode"));
                context = jetty.addWebApplication("/",
                    pluginDir.getParentFile().getParentFile().getParent() + File.separator + "src" +
                            File.separator + "web");
            }
            else {
                context = jetty.addWebApplication("/",
                    pluginDir.getAbsoluteFile() + File.separator + "webapp");
            }
            context.setWelcomeFiles(new String[]{"index.jsp"});

            jetty.start();

            printListenerMessages();
        }
        catch (Exception e) {
            Log.error(e);
        }
    }

    private void loadListeners() throws Exception {
        // Configure HTTP socket listener. Setting the interface property to a
        // non null value will imply that the Jetty server will only
        // accept connect requests to that IP address.
        String interfaceName = JiveGlobals.getXMLProperty("network.interface");
        port = JiveGlobals.getXMLProperty("adminConsole.port", 9090);
        InetAddrPort address = new InetAddrPort(interfaceName, port);
        if (port > 0) {
            plainListener = jetty.addListener(address);
        }

        try {
            securePort = JiveGlobals.getXMLProperty("adminConsole.securePort", 9091);
            if (securePort > 0) {
                SunJsseListener listener = new SunJsseListener();
                // Get the keystore location. The default location is security/keystore
                String keyStoreLocation = JiveGlobals.getProperty("xmpp.socket.ssl.keystore",
                        "resources" + File.separator + "security" + File.separator + "keystore");
                // The location is relative to the home directory of the application.
                keyStoreLocation = JiveGlobals.getHomeDirectory() + File.separator + keyStoreLocation;

                // Get the keystore password. The default password is "changeit".
                String keypass = JiveGlobals.getProperty("xmpp.socket.ssl.keypass", "changeit");
                keypass = keypass.trim();

                listener.setKeystore(keyStoreLocation);
                listener.setKeyPassword(keypass);
                listener.setPassword(keypass);

                listener.setHost(interfaceName);
                listener.setPort(securePort);

                secureListener = jetty.addListener(listener);
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
    }

    public void initializePlugin(PluginManager manager, File pluginDir) {
        this.pluginDir = pluginDir;
        try {
            // Configure logging to a file, creating log dir if needed
            System.setProperty("org.apache.commons.logging.LogFactory", "org.mortbay.log.Factory");
            File logDir = null;
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
            File logFile = new File(logDir, "admin-console.log");
            OutputStreamLogSink logSink = new OutputStreamLogSink(logFile.toString());
            logSink.start();
            // In some cases, commons-logging settings can be stomped by other
            // libraries in the classpath. Make sure that hasn't happened before
            // setting configuration.
            Object logImpl = Factory.getFactory().getInstance("");
            if (logImpl instanceof LogImpl) {
                LogImpl log = (LogImpl)logImpl;
                // Ignore INFO logs unless debugging turned on.
                if (Log.isDebugEnabled() &&
                        JiveGlobals.getBooleanProperty("jetty.debug.enabled", true)) {
                    log.setVerbose(1);
                }
                else {
                    log.setVerbose(-1);
                }
                log.add(logSink);
            }

            jetty = new Server();

            loadListeners();

            // Add web-app. Check to see if we're in development mode. If so, we don't
            // add the normal web-app location, but the web-app in the project directory.
            if (Boolean.getBoolean("developmentMode")) {
                System.out.println(LocaleUtils.getLocalizedString("admin.console.devmode"));
                context = jetty.addWebApplication("/",
                    pluginDir.getParentFile().getParentFile().getParent() + File.separator + "src" +
                            File.separator + "web");
            }
            else {
                context = jetty.addWebApplication("/",
                    pluginDir.getAbsoluteFile() + File.separator + "webapp");
            }
            context.setWelcomeFiles(new String[]{"index.jsp"});

            jetty.start();

            printListenerMessages();
        }
        catch (Exception e) {
            System.err.println("Error starting admin console: " + e.getMessage()); 
            Log.error("Trouble initializing admin console", e);
        }
    }

    public void destroyPlugin() {
        plainListener = null;
        secureListener = null;
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

    /**
     * Writes out startup messages for the listeners.
     */
    private void printListenerMessages() {
        String warning = LocaleUtils.getLocalizedString("admin.console.warning");
        String listening = LocaleUtils.getLocalizedString("admin.console.listening");

        if (plainListener == null && secureListener == null) {
            Log.info(warning);
            System.out.println(warning);
        }
        else if (plainListener == null) {
            Log.info(listening + " https://" +
                    XMPPServer.getInstance().getServerInfo().getName() + ":" + securePort);
            System.out.println(listening + " https://" +
                    XMPPServer.getInstance().getServerInfo().getName() + ":" + securePort);
        }
        else if (secureListener == null) {
            Log.info(listening + " http://" +
                    XMPPServer.getInstance().getServerInfo().getName() + ":" + port);
            System.out.println(listening + " http://" +
                    XMPPServer.getInstance().getServerInfo().getName() + ":" + port);
        }
        else {
            String msg = listening + ":" + System.getProperty("line.separator") +
                    "  http://" + XMPPServer.getInstance().getServerInfo().getName() + ":" +
                    port + System.getProperty("line.separator") +
                    "  https://" + XMPPServer.getInstance().getServerInfo().getName() + ":" +
                    securePort;
            Log.info(msg);
            System.out.println(msg);
        }
    }
}