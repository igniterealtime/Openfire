/**
 * Copyright (C) 2004-2009 Jive Software. All rights reserved.
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

/**
 * A SocketSendingTracker keeps track of all the sockets that are currently sending data and
 * checks the health of the sockets to detect hanged connections. If a sending operation takes
 * too much time (i.e. exceeds a time limit) then it is assumed that the connection has been
 * lost and for some reason the JVM has not been notified of the dead connection. Once a dead
 * connection has been detected it will be closed so that the thread that was writing to the
 * socket can resume. Resuming locked threads is important since otherwise a complete system halt
 * may occur.<p>
 *
 * The time limit to wait before considering a connection dead can be configured changing the
 * property <b>xmpp.session.sending-limit</b>. If the property was not defined then a default
 * time limit of 60 seconds will be assumed. This means that by default if a sending operation
 * takes longer than 60 seconds then the connection will be closed and the client disconnected.
 * Therefore, it is important to not set a very low time limit since active clients may be
 * incorrectly considered as dead clients.
 *
 * @author Gaston Dombiak
 */
public class SocketSendingTracker {


    private static SocketSendingTracker instance = new SocketSendingTracker();

    /**
     * Flag that indicates if the tracket should shutdown the tracking process.
     */
    private boolean shutdown = false;

    /**
     * Thread used for checking periodically the health of the sockets involved in sending
     * operations.
     */
    private Thread checkingThread;

    /**
     * Returns the unique instance of this class.
     *
     * @return the unique instance of this class.
     */
    public static SocketSendingTracker getInstance() {
        return instance;
    }

    /**
     * Hide the constructor so that only one instance of this class can exist.
     */
    private SocketSendingTracker() {
    }

    /**
     * Start up the daemon thread that will check for the health of the sockets that are
     * currently sending data.
     */
    public void start() {
        shutdown = false;
        checkingThread = new Thread("SocketSendingTracker") {
            public void run() {
                while (!shutdown) {
                    checkHealth();
                    synchronized (this) {
                        try {
                            wait(10000);
                        }
                        catch (InterruptedException e) {
                        }
                    }
                }
            }
        };
        checkingThread.setDaemon(true);
        checkingThread.start();
    }

    /**
     * Indicates that the checking thread should be stoped. The thread will be waked up
     * so that it can be stoped.
     */
    public void shutdown() {
        shutdown = true;
        // Use a wait/notify algorithm to ensure that the thread stops immediately if it
        // was waiting
        synchronized (checkingThread) {
            checkingThread.notify();
        }
    }

    /**
     * Checks if a socket has been trying to send data for a given amount of time. If it has
     * exceded a limit of time then the socket will be closed.<p>
     *
     * It is expected that sending operations will not take too much time so the checking will
     * be very fast since very few sockets will be present in the Map and most or all of them
     * will not exceed the time limit. Therefore, it is expected the overhead of this class to be
     * quite small.
     */
    private void checkHealth() {
        for (SocketConnection connection : SocketConnection.getInstances()) {
            connection.checkHealth();
        }
    }
}
