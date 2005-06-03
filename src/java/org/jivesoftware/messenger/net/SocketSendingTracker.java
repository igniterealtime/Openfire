package org.jivesoftware.messenger.net;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;

import java.io.IOException;
import java.net.Socket;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
     * Map that holds the sockets that are currently sending information together with the date
     * when the sending operation started.
     */
    private Map<Socket, Date> sockets = new ConcurrentHashMap<Socket, Date>();

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
     * Register that the specified socket has started sending information. The registration will
     * include the timestamp when the sending operation started so that if after several minutes
     * it hasn't finished then the socket will be closed.
     *
     * @param socket the socket that started sending data.
     */
    public void socketStartedSending(Socket socket) {
        sockets.put(socket, new Date());
    }

    /**
     * Register that the specified socket has finished sending information. The socket will
     * be removed from the tracking list.
     *
     * @param socket the socket that finished sending data.
     */
    public void socketFinishedSending(Socket socket) {
        sockets.remove(socket);
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
        for (Socket socket : sockets.keySet()) {
            Date startDate = sockets.get(socket);
            if (startDate != null &&
                    System.currentTimeMillis() - startDate.getTime() >
                    JiveGlobals.getIntProperty("xmpp.session.sending-limit", 60000)) {
                // Check that the sending operation is still active
                if (sockets.get(socket) != null) {
                    // Close the socket
                    try {
                        Log.debug("Closing socket: " + socket + " that started sending data at: " +
                                startDate);
                        socket.close();
                    }
                    catch (IOException e) {
                        Log.error("Error closing socket", e);
                    }
                    finally {
                        // Remove tracking on this socket
                        sockets.remove(socket);
                    }
                }
            }

        }
    }
}
