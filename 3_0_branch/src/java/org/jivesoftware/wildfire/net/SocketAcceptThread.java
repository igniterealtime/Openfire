/**
 * $RCSfile$
 * $Revision: 1583 $
 * $Date: 2005-07-03 17:55:39 -0300 (Sun, 03 Jul 2005) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.net;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.wildfire.ConnectionManager;
import org.jivesoftware.wildfire.ServerPort;

import java.io.IOException;
import java.net.InetAddress;

/**
 * Implements a network front end with a dedicated thread reading
 * each incoming socket. Blocking and non-blocking modes are supported.
 * By default blocking mode is used. Use the <i>xmpp.socket.blocking</i>
 * system property to change the blocking mode. Restart the server after making
 * changes to the system property.
 *
 * @author Gaston Dombiak
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
     * The default XMPP port for connection multiplex.
     */
    public static final int DEFAULT_MULTIPLEX_PORT = 5262;

    /**
     * Holds information about the port on which the server will listen for connections.
     */
    private ServerPort serverPort;

    private SocketAcceptingMode acceptingMode;

    public SocketAcceptThread(ConnectionManager connManager, ServerPort serverPort)
            throws IOException {
        super("Socket Listener at port " + serverPort.getPort());
        this.serverPort = serverPort;
        // Listen on a specific network interface if it has been set.
        String interfaceName = JiveGlobals.getXMLProperty("network.interface");
        InetAddress bindInterface = null;
        if (interfaceName != null) {
            if (interfaceName.trim().length() > 0) {
                bindInterface = InetAddress.getByName(interfaceName);
            }
        }
        // Set the blocking reading mode to use
        boolean useBlockingMode = JiveGlobals.getBooleanProperty("xmpp.socket.blocking", true);
        if (useBlockingMode) {
            acceptingMode = new BlockingAcceptingMode(connManager, serverPort, bindInterface);
        }
        else {
            acceptingMode = new NonBlockingAcceptingMode(connManager, serverPort, bindInterface);
        }
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
        acceptingMode.shutdown();
    }

    /**
     * About as simple as it gets.  The thread spins around an accept
     * call getting sockets and handing them to the SocketManager.
     */
    public void run() {
        acceptingMode.run();
        // We stopped accepting new connections so close the listener
        shutdown();
    }
}
