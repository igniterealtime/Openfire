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

import org.jivesoftware.openfire.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Abstract class for {@link BlockingAcceptingMode}.
 *
 * @author Gaston Dombiak
 * @deprecated Old, pre NIO / MINA code. Should not be used as NIO offers better performance
 */
@Deprecated
abstract class SocketAcceptingMode {

    /**
     * True while this thread should continue running.
     */
    protected boolean notTerminated = true;

    /**
     * socket that listens for connections.
     */
    protected ServerSocket serverSocket;

    /**
     * true if data is to be encrypted directly (as opposed to StartTLS).
     */
    protected final boolean directTLS;

    protected SocketAcceptingMode(boolean directTLS) {
        this.directTLS = directTLS;
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

    public abstract void run();

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

    public SocketReader createServerSocketReader(Socket sock, boolean isSecure, boolean useBlockingMode) throws IOException {
        final XMPPServer server = XMPPServer.getInstance();
        final String serverName = server.getServerInfo().getXMPPDomain();
        final PacketRouter router = server.getPacketRouter();
        final RoutingTable routingTable = server.getRoutingTable();
        final PacketDeliverer deliverer = server.getPacketDeliverer();
        final SocketConnection conn = new SocketConnection(deliverer, sock, isSecure);
        if (directTLS) {
            conn.startTLS( false, directTLS );
        }
        return new ServerSocketReader(router, routingTable, serverName, sock, conn, useBlockingMode, directTLS);
    }
}
