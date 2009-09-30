/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
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

import org.jivesoftware.openfire.ConnectionManager;
import org.jivesoftware.openfire.ServerPort;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * Abstract class for {@link BlockingAcceptingMode}.
 *
 * @author Gaston Dombiak
 */
abstract class SocketAcceptingMode {

    /**
     * True while this thread should continue running.
     */
    protected boolean notTerminated = true;

    /**
     * Holds information about the port on which the server will listen for connections.
     */
    protected ServerPort serverPort;

    /**
     * socket that listens for connections.
     */
    protected ServerSocket serverSocket;

    protected ConnectionManager connManager;

    protected SocketAcceptingMode(ConnectionManager connManager, ServerPort serverPort) {
        this.connManager = connManager;
        this.serverPort = serverPort;
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
}
