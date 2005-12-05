/**
 * $RCSfile: ConnectionManagerImpl.java,v $
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.spi;

import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.net.*;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.JiveGlobals;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ConnectionManagerImpl extends BasicModule implements ConnectionManager {

    private SocketAcceptThread socketThread;
    private SSLSocketAcceptThread sslSocketThread;
    private SocketAcceptThread componentSocketThread;
    private SocketAcceptThread serverSocketThread;
    private ArrayList<ServerPort> ports;

    private SessionManager sessionManager;
    private PacketDeliverer deliverer;
    private PacketRouter router;
    private String serverName;
    private XMPPServer server;
    private String localIPAddress = null;

    // Used to know if the sockets can be started (the connection manager has been started)
    private boolean isStarted = false;
    // Used to know if the sockets have been started
    private boolean isSocketStarted = false;

    public ConnectionManagerImpl() {
        super("Connection Manager");
        ports = new ArrayList<ServerPort>(4);
    }

    private void createSocket() {
        if (!isStarted || isSocketStarted || sessionManager == null || deliverer == null ||
                router == null ||
                serverName == null)
        {
            return;
        }
        isSocketStarted = true;

        // Setup port info
        try {
            localIPAddress = InetAddress.getLocalHost().getHostAddress();
        }
        catch (UnknownHostException e) {
            if (localIPAddress == null) {
                localIPAddress = "Unknown";
            }
        }
        // Start the port listener for s2s communication
        startServerListener(localIPAddress);
        // Start the port listener for external components
        startComponentListener(localIPAddress);
        // Start the port listener for clients
        startClientListeners(localIPAddress);
        // Start the port listener for secured clients
        startClientSSLListeners(localIPAddress);
    }

    private void startServerListener(String localIPAddress) {
        // Start servers socket unless it's been disabled.
        if (isServerListenerEnabled()) {
            int port = getServerListenerPort();
            String interfaceName = JiveGlobals.getProperty("xmpp.server.socket.interface");
            ServerPort serverPort = new ServerPort(port, interfaceName, serverName, localIPAddress,
                    false, null, ServerPort.Type.server);
            try {
                serverSocketThread = new SocketAcceptThread(this, serverPort);
                ports.add(serverPort);
                serverSocketThread.setDaemon(true);
                serverSocketThread.start();

                List params = new ArrayList();
                params.add(Integer.toString(serverSocketThread.getPort()));
                Log.info(LocaleUtils.getLocalizedString("startup.server", params));
            }
            catch (Exception e) {
                System.err.println("Error starting server listener on port " + port + ": " +
                        e.getMessage());
                Log.error(LocaleUtils.getLocalizedString("admin.error.socket-setup"), e);
            }
        }
    }

    private void stopServerListener() {
        if (serverSocketThread != null) {
            serverSocketThread.shutdown();
            ports.remove(serverSocketThread.getServerPort());
            serverSocketThread = null;
        }
    }

    private void startComponentListener(String localIPAddress) {
        // Start components socket unless it's been disabled.
        if (isComponentListenerEnabled()) {
            int port = getComponentListenerPort();
            String interfaceName = JiveGlobals.getProperty("xmpp.component.socket.interface");
            ServerPort serverPort = new ServerPort(port, interfaceName, serverName, localIPAddress,
                    false, null, ServerPort.Type.component);
            try {
                componentSocketThread = new SocketAcceptThread(this, serverPort);
                ports.add(serverPort);
                componentSocketThread.setDaemon(true);
                componentSocketThread.start();

                List params = new ArrayList();
                params.add(Integer.toString(componentSocketThread.getPort()));
                Log.info(LocaleUtils.getLocalizedString("startup.component", params));
            }
            catch (Exception e) {
                System.err.println("Error starting component listener on port " + port + ": " +
                        e.getMessage());
                Log.error(LocaleUtils.getLocalizedString("admin.error.socket-setup"), e);
            }
        }
    }

    private void stopComponentListener() {
        if (componentSocketThread != null) {
            componentSocketThread.shutdown();
            ports.remove(componentSocketThread.getServerPort());
            componentSocketThread = null;
        }
    }

    private void startClientListeners(String localIPAddress) {
        // Start clients plain socket unless it's been disabled.
        if (isClientListenerEnabled()) {
            int port = getClientListenerPort();
            String interfaceName = JiveGlobals.getProperty("xmpp.socket.plain.interface");
            ServerPort serverPort = new ServerPort(port, interfaceName, serverName, localIPAddress,
                    false, null, ServerPort.Type.client);
            try {
                socketThread = new SocketAcceptThread(this, serverPort);
                ports.add(serverPort);
                socketThread.setDaemon(true);
                socketThread.start();

                List params = new ArrayList();
                params.add(Integer.toString(socketThread.getPort()));
                Log.info(LocaleUtils.getLocalizedString("startup.plain", params));
            }
            catch (Exception e) {
                System.err.println("Error starting XMPP listener on port " + port + ": " +
                        e.getMessage());
                Log.error(LocaleUtils.getLocalizedString("admin.error.socket-setup"), e);
            }
        }
    }

    private void stopClientListeners() {
        if (socketThread != null) {
            socketThread.shutdown();
            ports.remove(socketThread.getServerPort());
            socketThread = null;
        }
    }

    private void startClientSSLListeners(String localIPAddress) {
        // Start clients SSL unless it's been disabled.
        if (isClientSSLListenerEnabled()) {
            int port = getClientSSLListenerPort();
            String interfaceName = JiveGlobals.getProperty("xmpp.socket.ssl.interface");
            String algorithm = JiveGlobals.getProperty("xmpp.socket.ssl.algorithm");
            if ("".equals(algorithm) || algorithm == null) {
                algorithm = "TLS";
            }
            ServerPort serverPort = new ServerPort(port, interfaceName, serverName, localIPAddress,
                    true, algorithm, ServerPort.Type.client);
            try {
                sslSocketThread = new SSLSocketAcceptThread(this, serverPort);
                ports.add(serverPort);
                sslSocketThread.setDaemon(true);
                sslSocketThread.start();

                List params = new ArrayList();
                params.add(Integer.toString(sslSocketThread.getPort()));
                Log.info(LocaleUtils.getLocalizedString("startup.ssl", params));
            }
            catch (Exception e) {
                System.err.println("Error starting SSL XMPP listener on port " + port + ": " +
                        e.getMessage());
                Log.error(LocaleUtils.getLocalizedString("admin.error.ssl"), e);
            }
        }
    }

    private void stopClientSSLListeners() {
        if (sslSocketThread != null) {
            sslSocketThread.shutdown();
            ports.remove(sslSocketThread.getServerPort());
            sslSocketThread = null;
        }
    }

    public Iterator<ServerPort> getPorts() {
        return ports.iterator();
    }

    public void addSocket(Socket sock, boolean isSecure, ServerPort serverPort)  {
        try {
            // the order of these calls is critical (stupid huh?)
            SocketConnection conn = new SocketConnection(deliverer, sock, isSecure);
            SocketReader reader = null;
            String threadName = null;
            if (serverPort.isClientPort()) {
                reader = new ClientSocketReader(router, serverName, sock, conn);
                threadName = "Client SR - " + reader.hashCode();
            }
            else if (serverPort.isComponentPort()) {
                reader = new ComponentSocketReader(router, serverName, sock, conn);
                threadName = "Component SR - " + reader.hashCode();
            }
            else {
                reader = new ServerSocketReader(router, serverName, sock, conn);
                threadName = "Server SR - " + reader.hashCode();
            }
            Thread thread = new Thread(reader, threadName);
            thread.setDaemon(true);
            thread.start();
        }
        catch (IOException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);
        this.server = server;
        router = server.getPacketRouter();
        deliverer = server.getPacketDeliverer();
        sessionManager = server.getSessionManager();
    }

    public void enableClientListener(boolean enabled) {
        if (enabled == isClientListenerEnabled()) {
            // Ignore new setting
            return;
        }
        if (enabled) {
            JiveGlobals.setProperty("xmpp.socket.plain.active", "true");
            // Start the port listener for clients
            startClientListeners(localIPAddress);
        }
        else {
            JiveGlobals.setProperty("xmpp.socket.plain.active", "false");
            // Stop the port listener for clients
            stopClientListeners();
        }
    }

    public boolean isClientListenerEnabled() {
        return JiveGlobals.getBooleanProperty("xmpp.socket.plain.active", true);
    }

    public void enableClientSSLListener(boolean enabled) {
        if (enabled == isClientSSLListenerEnabled()) {
            // Ignore new setting
            return;
        }
        if (enabled) {
            JiveGlobals.setProperty("xmpp.socket.ssl.active", "true");
            // Start the port listener for secured clients
            startClientSSLListeners(localIPAddress);
        }
        else {
            JiveGlobals.setProperty("xmpp.socket.ssl.active", "false");
            // Stop the port listener for secured clients
            stopClientSSLListeners();
        }
    }

    public boolean isClientSSLListenerEnabled() {
        return JiveGlobals.getBooleanProperty("xmpp.socket.ssl.active", true);
    }

    public void enableComponentListener(boolean enabled) {
        if (enabled == isComponentListenerEnabled()) {
            // Ignore new setting
            return;
        }
        if (enabled) {
            JiveGlobals.setProperty("xmpp.component.socket.active", "true");
            // Start the port listener for external components
            startComponentListener(localIPAddress);
        }
        else {
            JiveGlobals.setProperty("xmpp.component.socket.active", "false");
            // Stop the port listener for external components
            stopComponentListener();
        }
    }

    public boolean isComponentListenerEnabled() {
        return JiveGlobals.getBooleanProperty("xmpp.component.socket.active", false);
    }

    public void enableServerListener(boolean enabled) {
        if (enabled == isServerListenerEnabled()) {
            // Ignore new setting
            return;
        }
        if (enabled) {
            JiveGlobals.setProperty("xmpp.server.socket.active", "true");
            // Start the port listener for s2s communication
            startServerListener(localIPAddress);
        }
        else {
            JiveGlobals.setProperty("xmpp.server.socket.active", "false");
            // Stop the port listener for s2s communication
            stopServerListener();
        }
    }

    public boolean isServerListenerEnabled() {
        return JiveGlobals.getBooleanProperty("xmpp.server.socket.active", true);
    }

    public void setClientListenerPort(int port) {
        if (port == getClientListenerPort()) {
            // Ignore new setting
            return;
        }
        JiveGlobals.setProperty("xmpp.socket.plain.port", String.valueOf(port));
        // Stop the port listener for clients
        stopClientListeners();
        if (isClientListenerEnabled()) {
            // Start the port listener for clients
            startClientListeners(localIPAddress);
        }
    }

    public int getClientListenerPort() {
        return JiveGlobals.getIntProperty("xmpp.socket.plain.port",
                SocketAcceptThread.DEFAULT_PORT);
    }

    public void setClientSSLListenerPort(int port) {
        if (port == getClientSSLListenerPort()) {
            // Ignore new setting
            return;
        }
        JiveGlobals.setProperty("xmpp.socket.ssl.port", String.valueOf(port));
        // Stop the port listener for secured clients
        stopClientSSLListeners();
        if (isClientSSLListenerEnabled()) {
            // Start the port listener for secured clients
            startClientSSLListeners(localIPAddress);
        }
    }

    public int getClientSSLListenerPort() {
        return JiveGlobals.getIntProperty("xmpp.socket.ssl.port",
                SSLSocketAcceptThread.DEFAULT_PORT);
    }

    public void setComponentListenerPort(int port) {
        if (port == getComponentListenerPort()) {
            // Ignore new setting
            return;
        }
        JiveGlobals.setProperty("xmpp.component.socket.port", String.valueOf(port));
        // Stop the port listener for external components
        stopComponentListener();
        if (isComponentListenerEnabled()) {
            // Start the port listener for external components
            startComponentListener(localIPAddress);
        }
    }

    public int getComponentListenerPort() {
        return JiveGlobals.getIntProperty("xmpp.component.socket.port",
                SocketAcceptThread.DEFAULT_COMPONENT_PORT);
    }

    public void setServerListenerPort(int port) {
        if (port == getServerListenerPort()) {
            // Ignore new setting
            return;
        }
        JiveGlobals.setProperty("xmpp.server.socket.port", String.valueOf(port));
        // Stop the port listener for s2s communication
        stopServerListener();
        if (isServerListenerEnabled()) {
            // Start the port listener for s2s communication
            startServerListener(localIPAddress);
        }
    }

    public int getServerListenerPort() {
        return JiveGlobals.getIntProperty("xmpp.server.socket.port",
                SocketAcceptThread.DEFAULT_SERVER_PORT);
    }

    // #####################################################################
    // Module management
    // #####################################################################

    public void start() {
        super.start();
        isStarted = true;
        serverName = server.getServerInfo().getName();
        createSocket();
        SocketSendingTracker.getInstance().start();
    }

    public void stop() {
        super.stop();
        stopClientListeners();
        stopClientSSLListeners();
        stopComponentListener();
        stopServerListener();
        SocketSendingTracker.getInstance().shutdown();
        serverName = null;
    }
}
