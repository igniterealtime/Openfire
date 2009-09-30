/**
 * $RCSfile: $
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

package org.jivesoftware.openfire.spi;

import org.jivesoftware.openfire.RoutableChannelHandler;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.session.*;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.TaskEngine;
import org.xmpp.packet.JID;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Internal component used by the RoutingTable to keep references to routes hosted by this JVM. When
 * running in a cluster each cluster member will have its own RoutingTable containing an instance of
 * this class. Each LocalRoutingTable is responsible for storing routes to components, client sessions
 * and outgoing server sessions hosted by local cluster node.
 *
 * @author Gaston Dombiak
 */
class LocalRoutingTable {
    Map<String, RoutableChannelHandler> routes = new ConcurrentHashMap<String, RoutableChannelHandler>();

    /**
     * Adds a route of a local {@link RoutableChannelHandler}
     *
     * @param address the string representation of the JID associated to the route.
     * @param route the route hosted by this node.
     * @return true if the element was added or false if was already present.
     */
    boolean addRoute(String address, RoutableChannelHandler route) {
        return routes.put(address, route) != route;
    }

    /**
     * Returns the route hosted by this node that is associated to the specified address.
     *
     * @param address the string representation of the JID associated to the route.
     * @return the route hosted by this node that is associated to the specified address.
     */
    RoutableChannelHandler getRoute(String address) {
        return routes.get(address);
    }

    /**
     * Returns the client sessions that are connected to this JVM.
     *
     * @return the client sessions that are connected to this JVM.
     */
    Collection<LocalClientSession> getClientRoutes() {
        List<LocalClientSession> sessions = new ArrayList<LocalClientSession>();
        for (RoutableChannelHandler route : routes.values()) {
            if (route instanceof LocalClientSession) {
                sessions.add((LocalClientSession) route);
            }
        }
        return sessions;
    }

    /**
     * Returns the outgoing server sessions that are connected to this JVM.
     *
     * @return the outgoing server sessions that are connected to this JVM.
     */
    Collection<LocalOutgoingServerSession> getServerRoutes() {
        List<LocalOutgoingServerSession> sessions = new ArrayList<LocalOutgoingServerSession>();
        for (RoutableChannelHandler route : routes.values()) {
            if (route instanceof LocalOutgoingServerSession) {
                sessions.add((LocalOutgoingServerSession) route);
            }
        }
        return sessions;
    }

    /**
     * Returns the external component sessions that are connected to this JVM.
     *
     * @return the external component sessions that are connected to this JVM.
     */
    Collection<RoutableChannelHandler> getComponentRoute() {
        List<RoutableChannelHandler> sessions = new ArrayList<RoutableChannelHandler>();
        for (RoutableChannelHandler route : routes.values()) {
            if (!(route instanceof LocalOutgoingServerSession || route instanceof LocalClientSession)) {
                sessions.add(route);
            }
        }
        return sessions;
    }

    /**
     * Removes a route of a local {@link RoutableChannelHandler}
     *
     * @param address the string representation of the JID associated to the route.
     */
    void removeRoute(String address) {
        routes.remove(address);
    }

    public void start() {
        // Run through the server sessions every 3 minutes after a 3 minutes server startup delay (default values)
        int period = 3 * 60 * 1000;
        TaskEngine.getInstance().scheduleAtFixedRate(new ServerCleanupTask(), period, period);
    }

    public void stop() {
        try {
            // Send the close stream header to all connected connections
            for (RoutableChannelHandler route : routes.values()) {
                if (route instanceof LocalSession) {
                    LocalSession session = (LocalSession) route;
                    try {
                        // Notify connected client that the server is being shut down
                        session.getConnection().systemShutdown();
                    }
                    catch (Throwable t) {
                        // Ignore.
                    }
                }
            }
        }
        catch (Exception e) {
            // Ignore.
        }
    }

    public boolean isLocalRoute(JID jid) {
        return routes.containsKey(jid.toString());
    }

    /**
     * Task that closes idle server sessions.
     */
    private class ServerCleanupTask extends TimerTask {
        /**
         * Close outgoing server sessions that have been idle for a long time.
         */
        public void run() {
            // Do nothing if this feature is disabled
            int idleTime = SessionManager.getInstance().getServerSessionIdleTime();
            if (idleTime == -1) {
                return;
            }
            final long deadline = System.currentTimeMillis() - idleTime;
            for (RoutableChannelHandler route : routes.values()) {
                // Check outgoing server sessions
                if (route instanceof OutgoingServerSession) {
                    Session session = (Session) route;
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
}
