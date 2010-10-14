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

package org.jivesoftware.openfire;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.LocalComponentSession;
import org.jivesoftware.openfire.session.LocalConnectionMultiplexerSession;
import org.jivesoftware.openfire.session.LocalIncomingServerSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.TaskEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A LocalSessionManager keeps track of sessions that are connected to this JVM and for
 * which there is no route. That is, sessions that are added to the routing table are
 * not going to be stored by this manager.<p>
 *
 * For external component sessions, incoming server sessions and connection manager
 * sessions there is never going to be a route so they are only kept here. Client
 * sessions before they authenticate are kept in this manager but once authenticated
 * they are removed since a new route is created for authenticated client sessions.<p>
 *
 * Sessions stored in this manager are not accessible from other cluster nodes. However,
 * sessions for which there is a route in the routing table can be accessed from other
 * cluster nodes. The only exception to this rule are the sessions of external components.
 * External component sessions are kept in this manager but all components (internal and
 * external) create a route in the routing table for the service they provide. That means
 * that services of components are accessible from other cluster nodes and when the
 * component is an external component then its session will be used to deliver packets
 * through the external component's session. 
 *
 * @author Gaston Dombiak
 */
class LocalSessionManager {
	
	private static final Logger Log = LoggerFactory.getLogger(LocalSessionManager.class);

    /**
     * Map that holds sessions that has been created but haven't been authenticated yet. The Map
     * will hold client sessions.
     */
    private Map<String, LocalClientSession> preAuthenticatedSessions = new ConcurrentHashMap<String, LocalClientSession>();

    /**
     * The sessions contained in this List are component sessions. For each connected component
     * this Map will keep the component's session.
     */
    private List<LocalComponentSession> componentsSessions = new CopyOnWriteArrayList<LocalComponentSession>();

    /**
     * Map of connection multiplexer sessions grouped by connection managers. Each connection
     * manager may have many connections to the server (i.e. connection pool). All connections
     * originated from the same connection manager are grouped as a single entry in the map.
     * Once all connections have been closed users that were logged using the connection manager
     * will become unavailable.
     */
    private Map<String, LocalConnectionMultiplexerSession> connnectionManagerSessions =
            new ConcurrentHashMap<String, LocalConnectionMultiplexerSession>();

    /**
     * The sessions contained in this Map are server sessions originated by a remote server. These
     * sessions can only receive packets from the remote server but are not capable of sending
     * packets to the remote server. Sessions will be added to this collecion only after they were
     * authenticated.
     * Key: streamID, Value: the IncomingServerSession associated to the streamID.
     */
    private final Map<String, LocalIncomingServerSession> incomingServerSessions =
            new ConcurrentHashMap<String, LocalIncomingServerSession>();


    public Map<String, LocalClientSession> getPreAuthenticatedSessions() {
        return preAuthenticatedSessions;
    }

    public List<LocalComponentSession> getComponentsSessions() {
        return componentsSessions;
    }

    public Map<String, LocalConnectionMultiplexerSession> getConnnectionManagerSessions() {
        return connnectionManagerSessions;
    }

    public LocalIncomingServerSession getIncomingServerSession(String streamID) {
        return incomingServerSessions.get(streamID);
    }

    public Collection<LocalIncomingServerSession> getIncomingServerSessions() {
        return incomingServerSessions.values();
    }

    public void addIncomingServerSessions(String streamID, LocalIncomingServerSession  session) {
        incomingServerSessions.put(streamID, session);
    }

    public void removeIncomingServerSessions(String streamID) {
        incomingServerSessions.remove(streamID);
    }

    public void start() {
        // Run through the server sessions every 3 minutes after a 3 minutes server startup delay (default values)
        int period = 3 * 60 * 1000;
        TaskEngine.getInstance().scheduleAtFixedRate(new ServerCleanupTask(), period, period);
    }

    public void stop() {
        try {
            // Send the close stream header to all connected connections
            Set<LocalSession> sessions = new HashSet<LocalSession>();
            sessions.addAll(preAuthenticatedSessions.values());
            sessions.addAll(componentsSessions);
            for (LocalIncomingServerSession incomingSession : incomingServerSessions.values()) {
                sessions.add(incomingSession);
            }
            for (LocalConnectionMultiplexerSession multiplexer : connnectionManagerSessions.values()) {
                sessions.add(multiplexer);
            }

            for (LocalSession session : sessions) {
                try {
                    // Notify connected client that the server is being shut down
                    session.getConnection().systemShutdown();
                }
                catch (Throwable t) {
                    // Ignore.
                }
            }
        }
        catch (Exception e) {
            // Ignore.
        }
    }

    /**
     * Task that closes idle server sessions.
     */
    private class ServerCleanupTask extends TimerTask {
        /**
         * Close incoming server sessions that have been idle for a long time.
         */
        @Override
		public void run() {
            // Do nothing if this feature is disabled
            int idleTime = SessionManager.getInstance().getServerSessionIdleTime();
            if (idleTime == -1) {
                return;
            }
            final long deadline = System.currentTimeMillis() - idleTime;
            for (LocalIncomingServerSession session : incomingServerSessions.values()) {
                try {
                    if (session.getLastActiveDate().getTime() < deadline) {
                        session.close();
                    }
                }
                catch (Throwable e) {
                    Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                }
            }
        }
    }
}
