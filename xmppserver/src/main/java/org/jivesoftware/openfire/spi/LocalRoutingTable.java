/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2025 Ignite Realtime Foundation. All rights reserved.
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
import org.jivesoftware.openfire.session.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Internal component used by the RoutingTable to keep references to routes hosted by this JVM. When
 * running in a cluster each cluster member will have its own RoutingTable containing an instance of
 * this class. Each LocalRoutingTable is responsible for storing routes to entities of a particular type
 * (such as components, client sessions or outgoing server sessions) hosted by local cluster node.
 *
 * @author Gaston Dombiak
 */
class LocalRoutingTable<R extends RoutableChannelHandler> {
    
    private static final Logger Log = LoggerFactory.getLogger(LocalRoutingTable.class);

    Map<DomainPair, R> routes = new ConcurrentHashMap<>();

    /**
     * Adds a route of a local {@link RoutableChannelHandler}
     *
     * @param pair DomainPair associated to the route.
     * @param route the route hosted by this node.
     * @return true if the element was added or false if was already present.
     */
    boolean addRoute(DomainPair pair, R route) {
        final boolean result = routes.put(pair, route) != route;
        Log.trace( "Route '{}' (for pair: '{}') {}", route.getAddress(), pair, result ? "added" : "not added (was already present)." );
        return result;
    }

    /**
     * Returns the route hosted by this node that is associated to the specified address.
     *
     * @param pair DomainPair associated to the route.
     * @return the route hosted by this node that is associated to the specified address.
     */
    R getRoute(DomainPair pair) {
        return routes.get(pair);
    }
    R getRoute(JID jid) {
        return routes.get(new DomainPair("", jid.toString()));
    }

    /**
     * Returns the sessions that are connected to this JVM.
     *
     * @return the sessions that are connected to this JVM.
     */
    Collection<R> getRoutes() {
        return new LinkedList<>(routes.values());
    }

    /**
     * Returns the amount routes that are connected to this JVM.
     *
     * @return a route count.
     */
    int size() {
        return routes.size();
    }

    /**
     * Removes a route of a local {@link RoutableChannelHandler}
     *
     * @param pair DomainPair associated to the route.
     */
    void removeRoute(DomainPair pair) {
        final RoutableChannelHandler removed = routes.remove(pair);
        Log.trace( "Remove local route '{}' (for pair: '{}') {}", removed == null ? "(null)" : removed.getAddress(), pair, removed != null ? "removed" : "not removed (was not present).");
    }

    public void start() {
    }

    public void stop() {
        try {
            // Send the close stream header to all connected connections
            for (RoutableChannelHandler route : routes.values()) {
                if (route instanceof LocalSession) {
                    LocalSession session = (LocalSession) route;
                    try {
                        // Notify connected client that the server is being shut down
                        if (session.getConnection() != null) { // Can occur if a session is 'detached'.
                            session.getConnection().systemShutdown();
                        }
                    }
                    catch (Throwable t) {
                        Log.debug("A throwable was thrown while trying to send the close stream header to a session.", t);
                    }
                }
            }
        }
        catch (Exception e) {
            Log.debug("An exception was thrown while trying to send the close stream header to a session.", e);
        }
    }

    public boolean isLocalRoute(DomainPair pair) {
        return routes.containsKey(pair);
    }
    public boolean isLocalRoute(JID jid) {
        return routes.containsKey(new DomainPair("", jid.toString()));
    }
}
