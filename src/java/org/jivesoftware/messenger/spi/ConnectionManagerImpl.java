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

package org.jivesoftware.messenger.spi;

import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.net.SSLSocketAcceptThread;
import org.jivesoftware.messenger.net.SocketAcceptThread;
import org.jivesoftware.messenger.net.SocketConnection;
import org.jivesoftware.messenger.net.SocketReadThread;
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
    private ArrayList<ServerPort> ports;

    private SessionManager sessionManager;
    private PacketDeliverer deliverer;
    private PacketRouter router;
    private String serverName;
    private XMPPServer server;

    public ConnectionManagerImpl() {
        super("Connection Manager");
        ports = new ArrayList<ServerPort>(2);
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
        String localIPAddress = null;
        try {
            localIPAddress = InetAddress.getLocalHost().getHostAddress();
        }
        catch (UnknownHostException e) {
            if (localIPAddress == null) {
                localIPAddress = "Unknown";
            }
        }
        // Start plain socket unless it's been disabled.
        if (JiveGlobals.getBooleanProperty("xmpp.socket.plain.active", true)) {
            try {
                socketThread = new SocketAcceptThread(this);
                ports.add(new ServerPort(socketThread.getPort(),
                        serverName, localIPAddress, false, null));
                socketThread.setDaemon(true);
                socketThread.start();

                List params = new ArrayList();
                params.add(Integer.toString(socketThread.getPort()));
                Log.info(LocaleUtils.getLocalizedString("startup.plain", params));
            }
            catch (Exception e) {
                System.err.println("Error starting XMPP listener on port " +
                        JiveGlobals.getIntProperty("xmpp.socket.plain.port", SocketAcceptThread.DEFAULT_PORT) +
                        ": " + e.getMessage());
                Log.error(LocaleUtils.getLocalizedString("admin.error.socket-setup"), e);
            }
        }
        // Start SSL unless it's been disabled.
        if (JiveGlobals.getBooleanProperty("xmpp.socket.ssl.active", true)) {
            try {
                sslSocketThread = new SSLSocketAcceptThread(this);
                String algorithm = JiveGlobals.getProperty("xmpp.socket.ssl.algorithm");
                if ("".equals(algorithm) || algorithm == null) {
                    algorithm = "TLS";
                }
                ports.add(new ServerPort(sslSocketThread.getPort(), serverName,
                        localIPAddress, true, algorithm));
                sslSocketThread.setDaemon(true);
                sslSocketThread.start();

                List params = new ArrayList();
                params.add(Integer.toString(sslSocketThread.getPort()));
                Log.info(LocaleUtils.getLocalizedString("startup.ssl", params));
            }
            catch (Exception e) {
                System.err.println("Error starting SSL XMPP listener on port " +
                        JiveGlobals.getIntProperty("xmpp.socket.ssl.port", SSLSocketAcceptThread.DEFAULT_PORT) +
                        ": " + e.getMessage());
                Log.error(LocaleUtils.getLocalizedString("admin.error.ssl"), e);
            }
        }
    }

    public Iterator<ServerPort> getPorts() {
        return ports.iterator();
    }

    public void addSocket(Socket sock, boolean isSecure)  {
        try {
            // the order of these calls is critical (stupid huh?)
            SocketConnection conn = new SocketConnection(deliverer, sock, isSecure);
            SocketReadThread reader = new SocketReadThread(router, serverName, sock, conn);
            reader.setDaemon(true);
            reader.start();
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

    // Used to know if the sockets can be started (the connection manager has been started)
    private boolean isStarted = false;
    // Used to know if the sockets have been started
    private boolean isSocketStarted = false;

    // #####################################################################
    // Module management
    // #####################################################################

    public void start() {
        super.start();
        isStarted = true;
        serverName = server.getServerInfo().getName();
        createSocket();
    }

    public void stop() {
        super.stop();
        if (socketThread != null) {
            socketThread.shutdown();
            socketThread = null;
        }
        if (sslSocketThread != null) {
            sslSocketThread.shutdown();
            sslSocketThread = null;
        }
        serverName = null;
    }
}
