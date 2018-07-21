/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.net;

import org.jivesoftware.openfire.ServerPort;

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
 * @deprecated Old, pre NIO / MINA code. Should not be used as NIO offers better performance
 */
@Deprecated
public class SocketAcceptThread extends Thread {

    /**
     * Holds information about the port on which the server will listen for connections.
     */
    private final int tcpPort;
    private InetAddress bindInterface;
    private final boolean directTLS;

    private SocketAcceptingMode acceptingMode;

    public SocketAcceptThread( int tcpPort, InetAddress bindInterface, boolean directTLS )
            throws IOException {
        super("Socket Listener at port " + tcpPort + ( directTLS ? " (direct TLS)" : ""));
        this.tcpPort = tcpPort;
        this.bindInterface = bindInterface;
        this.directTLS = directTLS;

        // Set the blocking reading mode to use
        acceptingMode = new BlockingAcceptingMode(tcpPort, bindInterface, directTLS);
    }

    /**
     * Retrieve the port this server socket is bound to.
     *
     * @return the port the socket is bound to.
     */
    public int getPort() {
        return tcpPort;
    }

    /**
     * Returns information about the port on which the server is listening for connections.
     *
     * @return information about the port on which the server is listening for connections.
     */
    public ServerPort getServerPort() {
        return new ServerPort(tcpPort, null, bindInterface.getHostName(), directTLS, null, ServerPort.Type.server);
    }

    /**
     * Returns if the port expects sockets to be encrypted immediately (direct
     * TLS).
     *
     * @return true when direct TLS is expected, otherwise false.
     */
    public boolean isDirectTLS() {
        return directTLS;
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
    @Override
    public void run() {
        acceptingMode.run();
        // We stopped accepting new connections so close the listener
        shutdown();
    }
}
