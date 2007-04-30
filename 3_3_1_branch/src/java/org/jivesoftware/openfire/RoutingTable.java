/**
 * $RCSfile: RoutingTable.java,v $
 * $Revision: 3138 $
 * $Date: 2005-12-01 02:13:26 -0300 (Thu, 01 Dec 2005) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire;

import org.xmpp.packet.JID;

import java.util.List;

/**
 * <p>Maintains server-wide knowledge of routes to any node.</p>
 * <p>Routes are only concerned with node addresses. Destinations are
 * packet handlers (typically of the three following types):</p>
 * <ul>
 * <li>Session - A local or remote session belonging to the server's domain.
 * Remote sessions may be possible in clustered servers.</li>
 * <li>Chatbot - A chatbot which will have various packets routed to it.</li>
 * <li>Transport - A transport for foreign server domains. Foreign domains
 * may be hosted in the same server JVM (e.g. virutal hosted servers, groupchat
 * servers, etc).</li>
 * </ul>
 * <p>In almost all cases, the caller should not be concerned with what
 * handler is associated with a given node. Simply obtain the packet handler
 * and deliver the packet to the node, leaving the details up to the handler.</p>
 * <p/>
 * <p>Routes are matched using the stringprep rules given in the XMPP specification.
 * Wildcard routes for a particular name or resource is indicated by a null. E.g.
 * routing to any address at server.com should set the name to null, the host to
 * 'server.com' and the resource to null. A route to the best resource for user@server.com
 * should indicate that route with a null resource component of the XMPPAddress. Session
 * managers should add a route for both the generic user@server.com as well as
 * user@server.com/resource routes (knowing that one is an alias for the other
 * is the responsibility of the session or session manager).</p>
 * <p/>
 * <p>In order to accomodate broadcasts, you can also do partial matches by querying
 * all 'child' nodes of a particular node. The routing table contains a forest of
 * node trees. The node tree is arranged in the following heirarchy:</p>
 * <ul>
 * <li>forest - All nodes in the routing table. An XMPP address with host, name, and resource set
 * to null will match all nodes stored in the routing table. Use with extreme caution as the
 * routing table may contain hundreds of thousands of entries and iterators will be produced using
 * a copy of the table for iteration safety.</li>
 * <li>domain root - The root of each node tree is the server domain. An XMPP address
 * containing just a host entry, and null in the name and resource fields will match
 * the domain root. The children will contain both the root entry (if there is one) and
 * all entries with the same host name.</li>
 * <li>user branches - The root's immediate children are the user branches. An
 * XMPP address containing just a hast and name entry, and null in the resource field
 * will match a particular user branch. The children will contain both the user branch
 * (if there is one) and all entries with the same host and name, ignoring resources.
 * This is the most useful for conducting user broadcasts. Note that if the user
 * branch is located on a foreign server, the only route returned will the server-to-server
 * transport.</li>
 * <li>resource leaves - Each user branch can have zero or more resource leaves. A partial
 * match on an XMPP address with values in host, name, and resource fields will be equivalent
 * to the exact match calls since only one route can ever be registered for a particular. See
 * getBestRoute() if you'd like to search for both the resource leaf route, as well as a valid user
 * branch for that node if no leaf exists.</li>
 * </ul>
 * <p/>
 * <p>Note: it is important that any component or action affecting routes
 * update the routing table immediately.</p>
 *
 * @author Iain Shigeoka
 */
public interface RoutingTable {

    /**
     * <p>Add a route to the routing table.</p>
     * <p>A single access method allows you to add any of the acceptable
     * route to the table. It is expected that routes are added and removed
     * on a relatively rare occassion so routing tables should be optimized
     * for lookup speed.</p>
     *
     * @param node        The route's destination node
     * @param destination The destination object for this route
     */
    void addRoute(JID node, RoutableChannelHandler destination);

    /**
     * <p>Obtain a route to a packet handler for the given node.</p>
     * <p>If a route doesn't exist, the method returns null.</p>
     *
     * @param node The address we want a route to
     * @return The handler corresponding to the route, or null indicating no route exists
     */
    RoutableChannelHandler getRoute(JID node);

    /**
     * <p>Obtain all child routes for the given node.</p>
     * <p>See the class documentation for the matching algorithm of child routes for
     * any given node. If a route doesn't exist, the method returns an empty iterator (not null).</p>
     *
     * @param node The address we want a route to
     * @return An iterator over all applicable routes
     */
    List<ChannelHandler> getRoutes(JID node);

    /**
     * <p>Obtain a route to a handler at the given node falling back to a user branch if no resource leaf exists.</p>
     * <p>Matching differs slightly from getRoute() which does matching according
     * to the general matching algorithm described in the class notes. This method
     * searches using the standard matching rules, and if that does not find a
     * match and the address name component is not null, or empty, searches again
     * with the resource set to null (wild card). This is essentially a convenience
     * for falling back to the best route to a user node when a specific resource
     * is not available.</p>
     * <p>For example, consider we're searching for a route to user@server.com/work.
     * There is no route to that resource but a session is available at
     * user@server.com/home. The routing table will contain entries for user@server.com
     * and user@server.com/home. getBestLocalRoute() will first do a search for
     * user@server.com/work and not find a match. It will then do another search
     * on user@server.com and find the alias for the session user@server.com/home
     * (the alias must be maintained by the session manager for the highest priority
     * resource for any given user). In most cases, the caller doesn't care as long
     * as they get a legitimate route to the user, so this behavior is 'better' than
     * the exact matching used in getLocalRoute().</p>
     * <p>However, it is important to note that sometimes you don't want the best route
     * to a node. In the previous example, if the packet is an error packet, it is
     * probably only relevant to the sending session. If a route to that particular
     * session can't be found, the error should not be sent to another session logged
     * into the account.</p>
     * <p/>
     * <p>If a route doesn't exist, the method returns null.</p>
     *
     * @param node The address we want a route to
     * @return The Session corresponding to the route, or null indicating no route exists
     */
    ChannelHandler getBestRoute(JID node);

    /**
     * <p>Remove a route from the routing table.</p>
     * <p>If a route doesn't exist, the method returns null.</p>
     *
     * @param node The address we want a route to
     * @return The destination object previously registered under the given address, or null if none existed
     */
    ChannelHandler removeRoute(JID node);
}
