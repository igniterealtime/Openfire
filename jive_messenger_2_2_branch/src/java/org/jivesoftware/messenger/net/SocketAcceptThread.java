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

package org.jivesoftware.messenger.net;

import org.jivesoftware.messenger.ConnectionManager;
import org.jivesoftware.messenger.ServerPort;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Implements a network front end with a dedicated thread reading
 * each incoming socket.
 *
 * @author Iain Shigeoka
 */
public class SocketAcceptThread extends Thread {

    /**
     * The default XMPP port for clients.
     */
    public static final int DEFAULT_PORT = 5222;

    /**
     * The default XMPP port for external components.
     */
    public static final int DEFAULT_COMPONENT_PORT = 10015;

    /**
     * The default XMPP port for server2server communication.
     */
    public static final int DEFAULT_SERVER_PORT = 5269;

    /**
     * Holds information about the port on which the server will listen for connections.
     */
    private ServerPort serverPort;

    /**
     * Interface to bind to.
     */
    private InetAddress bindInterface;

    /**
     * True while this thread should continue running.
     */
    private boolean notTerminated = true;

    /**
     * socket that listens for connections.
     */
    ServerSocket serverSocket;

    private ConnectionManager connManager;

    public SocketAcceptThread(ConnectionManager connManager, ServerPort serverPort)
            throws IOException {
        super("Socket Listener at port " + serverPort.getPort());
        this.connManager = connManager;
        this.serverPort = serverPort;
        String interfaceName = serverPort.getInterfaceName();
        bindInterface = null;
        if (interfaceName != null) {
            if (interfaceName.trim().length() > 0) {
                bindInterface = InetAddress.getByName(interfaceName);
            }
        }
        serverSocket = new ServerSocket(serverPort.getPort(), -1, bindInterface);
    }

    /**
     * Retrieve the port this server socket is bound to.
     *
     * @return the port the socket is bound to.
     */
    public int getPort() {
        return serverPort.getPort();
    }

    /**
     * Returns information about the port on which the server is listening for connections.
     *
     * @return information about the port on which the server is listening for connections.
     */
    public ServerPort getServerPort() {
        return serverPort;
    }

    /**
     * Unblock the thread and force it to terminate.
     */
    public void shutdown() {
        notTerminated = false;

        try {
            ServerSocket sSock = serverSocket;
            serverSocket = null;
            if (sSock != null) {
                sSock.close();
            }
        }
        catch (IOException e) {
            // we don't care, no matter what, the socket should be dead
        }

    }

    /**
     * About as simple as it gets.  The thread spins around an accept
     * call getting sockets and handing them to the SocketManager.
     */
    public void run() {
        while (notTerminated) {
            try {
                Socket sock = serverSocket.accept();
                if (sock != null) {
                    Log.debug("Connect " + sock.toString());
                    connManager.addSocket(sock, false, serverPort);
                }
            }
            catch (IOException ie) {
                if (notTerminated) {
                    Log.error(LocaleUtils.getLocalizedString("admin.error.accept"),
                            ie);
                }
            }
            catch (Exception e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error.accept"), e);
            }
        }

        try {
            ServerSocket sSock = serverSocket;
            serverSocket = null;
            if (sSock != null) {
                sSock.close();
            }
        }
        catch (IOException e) {
            // we don't care, no matter what, the socket should be dead
        }
    }
}
