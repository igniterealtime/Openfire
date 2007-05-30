/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.spi;

import org.jivesoftware.openfire.RoutableChannelHandler;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.session.OutgoingServerSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.TaskEngine;

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
     */
    void addRoute(String address, RoutableChannelHandler route) {
        routes.put(address, route);
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
    Collection<ClientSession> getClientRoutes() {
        List<ClientSession> sessions = new ArrayList<ClientSession>();
        for (RoutableChannelHandler route : routes.values()) {
            if (route instanceof ClientSession) {
                sessions.add((ClientSession) route);
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
