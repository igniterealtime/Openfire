/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.spi;

import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.container.TrackInfo;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.audit.AuditManager;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.net.SSLSocketAcceptThread;
import org.jivesoftware.messenger.net.SocketAcceptThread;
import org.jivesoftware.messenger.net.SocketConnection;
import org.jivesoftware.messenger.net.SocketReadThread;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.xml.stream.XMLStreamException;

public class ConnectionManagerImpl extends BasicModule implements ConnectionManager {

    private SocketAcceptThread socketThread;
    private SSLSocketAcceptThread sslSocketThread;
    private ArrayList ports;

    public ConnectionManagerImpl() {
        super("Connection Manager");
        ports = new ArrayList(2);
    }

    private void createSocket() {

        if (!isStarted ||
                isSocketStarted ||
                auditManager == null ||
                sessionManager == null ||
                deliverer == null ||
                router == null ||
                serverName == null ||
                packetFactory == null) {
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

        // Now start up the acceptor (and associated read selector)
          if ("true".equals(JiveGlobals.getProperty("xmpp.socket.ssl.active"))) {
            try {
                sslSocketThread = new SSLSocketAcceptThread(this);
                String algorithm =
                        JiveGlobals.getProperty("xmpp.socket.ssl.algorithm");
                if ("".equals(algorithm) || algorithm == null) {
                    algorithm = "TLS";
                }
                ports.add(new ServerPortImpl(sslSocketThread.getPort(),
                        serverName,
                        localIPAddress,
                        true,
                        algorithm));
                sslSocketThread.setDaemon(true);
                sslSocketThread.start();

                List params = new ArrayList();
                params.add(Integer.toString(sslSocketThread.getPort()));
                Log.info(LocaleUtils.getLocalizedString("startup.ssl", params));
            }
            catch (Exception e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error.ssl"), e);
            }
        }
        else {
            socketThread = new SocketAcceptThread(this);
            ports.add(new ServerPortImpl(socketThread.getPort(),
                    serverName, localIPAddress, false, null));
            socketThread.setDaemon(true);
            socketThread.start();

            List params = new ArrayList();
            params.add(Integer.toString(socketThread.getPort()));
            Log.info(LocaleUtils.getLocalizedString("startup.plain", params));
        }
    }

    public Iterator getPorts() {
        return ports.iterator();
    }

    public AuditManager auditManager;
    public SessionManager sessionManager;
    public PacketDeliverer deliverer;
    public PacketRouter router;
    private String serverName;
    public XMPPServer server;
    public PacketFactory packetFactory;

    public void addSocket(Socket sock, boolean isSecure) throws XMLStreamException {
        try {
            // the order of these calls is critical (stupid huh?)
            Connection conn = new SocketConnection(deliverer,
                    auditManager.getAuditor(),
                    sock,
                    isSecure);
            Session session = sessionManager.createSession(conn);
            SocketReadThread reader = new SocketReadThread(router,
                    packetFactory, serverName, auditManager.getAuditor(),
                    sock, session);
            reader.setDaemon(true);
            reader.start();
        }
        catch (UnauthorizedException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        catch (IOException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
    }

    public TrackInfo getTrackInfo() {
        TrackInfo trackInfo = new TrackInfo();
        trackInfo.getTrackerClasses().put(SessionManager.class, "sessionManager");
        trackInfo.getTrackerClasses().put(XMPPServer.class, "server");
        trackInfo.getTrackerClasses().put(PacketRouter.class, "router");
        trackInfo.getTrackerClasses().put(PacketDeliverer.class, "deliverer");
        trackInfo.getTrackerClasses().put(AuditManager.class, "auditManager");
        trackInfo.getTrackerClasses().put(PacketFactory.class, "packetFactory");
        return trackInfo;
    }

    public void serviceAdded(Object service) {
        if (service instanceof XMPPServer) {
            serverName = server.getServerInfo().getName();
        }
        createSocket();
    }

    // Used to know if the sockets can be started (the connection manager has been started)
    private boolean isStarted = false;
    // Used to know if the sockets have been started
    private boolean isSocketStarted = false;

    public void serviceRemoved(Object service) {
        if (server == null) {
            serverName = null;
        }
    }

    // #####################################################################
    // Module management
    // #####################################################################

    public void start() {
        super.start();
        isStarted = true;
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
    }
}
