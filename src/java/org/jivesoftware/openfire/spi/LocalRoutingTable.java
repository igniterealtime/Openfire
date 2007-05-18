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

import java.util.Map;
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
     * Removes a route of a local {@link RoutableChannelHandler}
     *
     * @param address the string representation of the JID associated to the route.
     */
    void removeRoute(String address) {
        routes.remove(address);
    }

}
