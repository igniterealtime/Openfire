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

package org.jivesoftware.openfire.multiplex;

import java.net.UnknownHostException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.event.SessionEventDispatcher;
import org.jivesoftware.openfire.event.SessionEventListener;
import org.jivesoftware.openfire.session.ConnectionMultiplexerSession;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.TaskEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A ConnectionMultiplexerManager is responsible for keeping track of the connected
 * Connection Managers and the sessions that were established with the Connection
 * Managers. Moreover, a ConnectionMultiplexerManager is able to create, get and close
 * client sessions based on Connection requests.
 *
 * @author Gaston Dombiak
 */
public class ConnectionMultiplexerManager implements SessionEventListener {

    private static final Logger Log = LoggerFactory.getLogger(ConnectionMultiplexerManager.class);

    private static final ConnectionMultiplexerManager instance = new ConnectionMultiplexerManager();

    /**
     * Pseudo-random number generator object for use with getMultiplexerSession(String).
     */
    private static Random randGen = new Random();

    static {
        // Add the unique instance of this class as a session listener. We need to react
        // when sessions are closed so we can clean up the registry of client sessions.
        SessionEventDispatcher.addListener(instance);
    }

    /**
     * Map that keeps track of connection managers and hosted connections.
     * Key: stream ID; Value: Domain of connection manager hosting connection
     */
    private Map<StreamID, String> streamIDs = new ConcurrentHashMap<>();
    /**
     * Map that keeps track of connection managers and hosted sessions.
     * Key: Domain of connection manager; Value: Map with Key: stream ID; Value: Client session
     */
    private Map<String, Map<StreamID, LocalClientSession>> sessionsByManager =
            new ConcurrentHashMap<>();

    private SessionManager sessionManager;

    /**
     * Returns the unique instance of this class.
     *
     * @return the unique instance of this class.
     */
    public static ConnectionMultiplexerManager getInstance() {
        return instance;
    }

    /**
     * Returns the default secret key that connection managers should present while trying to
     * establish a new connection.
     *
     * @return the default secret key that connection managers should present while trying to
     *         establish a new connection.
     */
    public static String getDefaultSecret() {
        return JiveGlobals.getProperty("xmpp.multiplex.defaultSecret");
    }

    /**
     * Sets the default secret key that connection managers should present while trying to
     * establish a new connection.
     *
     * @param defaultSecret the default secret key that connection managers should present
     *        while trying to establish a new connection.
     */
    public static void setDefaultSecret(String defaultSecret) {
        JiveGlobals.setProperty("xmpp.multiplex.defaultSecret", defaultSecret);
    }

    private ConnectionMultiplexerManager() {
        sessionManager = XMPPServer.getInstance().getSessionManager();
        // Start thread that will send heartbeats to Connection Managers every 30 seconds
        // to keep connections open.
        TimerTask heartbeatTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    for (ConnectionMultiplexerSession session : sessionManager.getConnectionMultiplexerSessions()) {
                        session.deliverRawText(" ");
                    }
                }
                catch(Exception e) {
                    Log.error(e.getMessage(), e);
                }
            }
        };
        TaskEngine.getInstance().schedule(heartbeatTask, 30*JiveConstants.SECOND, 30*JiveConstants.SECOND);
    }

    /**
     * Creates a new client session that was established to the specified connection manager.
     * The new session will not be findable through its stream ID.
     *
     * @param connectionManagerDomain the connection manager that is handling the connection
     *        of the session.
     * @param streamID the stream ID created by the connection manager for the new session.
     * @param hostName the address's hostname of the client or null if using old connection manager.
     * @param hostAddress the textual representation of the address of the client or null if using old CM.
     * @return true if a session was created or false if the client should disconnect.
     */
    public boolean createClientSession(String connectionManagerDomain, StreamID streamID, String hostName, String hostAddress) {
        Connection connection = new ClientSessionConnection(connectionManagerDomain, hostName, hostAddress);
        // Check if client is allowed to connect from the specified IP address. Ignore the checking if connection
        // manager is old version and is not passing client's address
        byte[] address = null;
        try {
            address = connection.getAddress();
        } catch (UnknownHostException e) {
            // Ignore
        }
        if (address == null || LocalClientSession.isAllowed(connection)) {
            LocalClientSession session =
                    SessionManager.getInstance().createClientSession(connection, streamID);
            // Register that this streamID belongs to the specified connection manager
            streamIDs.put(streamID, connectionManagerDomain);
            // Register which sessions are being hosted by the speicifed connection manager
            Map<StreamID, LocalClientSession> sessions = sessionsByManager.get(connectionManagerDomain);
            if (sessions == null) {
                synchronized (connectionManagerDomain.intern()) {
                    sessions = sessionsByManager.get(connectionManagerDomain);
                    if (sessions == null) {
                        sessions = new ConcurrentHashMap<>();
                        sessionsByManager.put(connectionManagerDomain, sessions);
                    }
                }
            }
            sessions.put(streamID, session);
            return true;
        }
        return false;
    }

    /**
     * Closes an existing client session that was established through a connection manager.
     *
     * @param connectionManagerDomain the connection manager that is handling the connection
     *        of the session.
     * @param streamID the stream ID created by the connection manager for the session.
     */
    public void closeClientSession(String connectionManagerDomain, StreamID streamID) {
        Map<StreamID, LocalClientSession> sessions = sessionsByManager.get(connectionManagerDomain);
        if (sessions != null) {
            Session session = sessions.remove(streamID);
            if (session != null) {
                // Close the session
                session.close();
            }
        }
    }

    /**
     * A connection manager has become available. Clients can now connect to the server through
     * the connection manager.
     *
     * @param connectionManagerName the connection manager that has become available.
     */
    public void multiplexerAvailable(String connectionManagerName) {
        // Add a new entry in the list of available managers. Here is where we are going to store
        // which clients were connected through which connection manager
        Map<StreamID, LocalClientSession> sessions = sessionsByManager.get(connectionManagerName);
        if (sessions == null) {
            synchronized (connectionManagerName.intern()) {
                sessions = sessionsByManager.get(connectionManagerName);
                if (sessions == null) {
                    sessions = new ConcurrentHashMap<>();
                    sessionsByManager.put(connectionManagerName, sessions);
                }
            }
        }
    }

    /**
     * A connection manager has gone unavailable. Close client sessions that were established
     * to the specified connection manager.
     *
     * @param connectionManagerName the connection manager that is no longer available.
     */
    public void multiplexerUnavailable(String connectionManagerName) {
        // Remove the connection manager and the hosted sessions
        Map<StreamID, LocalClientSession> sessions = sessionsByManager.remove(connectionManagerName);
        if (sessions != null) {
            for (StreamID streamID : sessions.keySet()) {
                // Remove inverse track of connection manager hosting streamIDs
                streamIDs.remove(streamID);
                // Close the session
                sessions.get(streamID).close();
            }
        }
    }

    /**
     * Returns the ClientSession with the specified stream ID that is being hosted by the
     * specified connection manager.
     *
     * @param connectionManagerDomain the connection manager that is handling the connection
     *        of the session.
     * @param streamID the stream ID created by the connection manager for the session.
     * @return the ClientSession with the specified stream ID.
     */
    public LocalClientSession getClientSession(String connectionManagerDomain, StreamID streamID) {
        Map<StreamID, LocalClientSession> sessions = sessionsByManager.get(connectionManagerDomain);
        if (sessions != null) {
            return sessions.get(streamID);
        }
        return null;
    }

    /**
     * Returns a {@link ConnectionMultiplexerSession} for the specified connection manager
     * domain or <tt>null</tt> if none was found. If a StreamID is passed in, the same connection
     * will always be used for that StreamID. Otherwise, if the connection manager has many
     * connections established with the server then one of them will be selected randomly.
     *
     * @param connectionManagerDomain the domain of the connection manager to get a session.
     * @param streamID if provided, the same connection will always be used for a given streamID
     * @return a session to the specified connection manager domain or null if none was found.
     */
    public ConnectionMultiplexerSession getMultiplexerSession(String connectionManagerDomain,StreamID streamID) {
        List<ConnectionMultiplexerSession> sessions =
                sessionManager.getConnectionMultiplexerSessions(connectionManagerDomain);
        if (sessions.isEmpty()) {
            return null;
        }
        else if (sessions.size() == 1) {
            return sessions.get(0);
        }
        else if (streamID != null) {
            // Always use the same connection for a given streamID
            int connectionIndex = Math.abs(streamID.hashCode()) % sessions.size();
            return sessions.get(connectionIndex);
        } else {
            // Pick a random session so we can distribute traffic evenly
            return sessions.get(randGen.nextInt(sessions.size()));
        }
    }

    /**
     * Returns a {@link ConnectionMultiplexerSession} for the specified connection manager
     * domain or <tt>null</tt> if none was found. In case the connection manager has many
     * connections established with the server then one of them will be selected randomly.
     *
     * @param connectionManagerDomain the domain of the connection manager to get a session.
     * @return a session to the specified connection manager domain or null if none was found.
     */
    public ConnectionMultiplexerSession getMultiplexerSession(String connectionManagerDomain) {
        return getMultiplexerSession(connectionManagerDomain,null);
    }


    /**
     * Returns the names of the connected connection managers to this server.
     *
     * @return the names of the connected connection managers to this server.
     */
    public Collection<String> getMultiplexers() {
        return sessionsByManager.keySet();
    }

    /**
     * Returns the number of connected clients to a specific connection manager.
     *
     * @param managerName the name of the connection manager.
     * @return the number of connected clients to a specific connection manager.
     */
    public int getNumConnectedClients(String managerName) {
        Map<StreamID, LocalClientSession> clients = sessionsByManager.get(managerName);
        if (clients == null) {
            return 0;
        }
        else {
            return clients.size();
        }
    }

    @Override
    public void anonymousSessionCreated(Session session) {
        // Do nothing.
    }

    @Override
    public void anonymousSessionDestroyed(Session session) {
        removeSession(session);
    }

    @Override
    public void sessionCreated(Session session) {
        // Do nothing.
    }

    @Override
    public void sessionDestroyed(Session session) {
        removeSession(session);
    }

    @Override
    public void resourceBound(Session session) {
        // Do nothing.
    }

    private void removeSession(Session session) {
        // Remove trace indicating that a connection manager is hosting a connection
        StreamID streamID = session.getStreamID();
        String connectionManagerDomain = streamIDs.remove(streamID);
        // Remove trace indicating that a connection manager is hosting a session
        if (connectionManagerDomain != null) {
            Map<StreamID, LocalClientSession> sessions = sessionsByManager.get(connectionManagerDomain);
            if (sessions != null) {
                sessions.remove(streamID);
            }
        }
    }
}
