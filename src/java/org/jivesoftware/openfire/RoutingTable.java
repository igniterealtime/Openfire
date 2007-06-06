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

import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.LocalOutgoingServerSession;
import org.jivesoftware.openfire.session.OutgoingServerSession;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import java.util.Collection;
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
     * Adds a route to the routing table for the specified outoing server session. When running
     * inside of a cluster this message <tt>must</tt> be sent from the cluster node that is
     * actually holding the physical connectoin to the remote server.
     *
     * @param route the address associated to the route.
     * @param destination the outgoing server session.
     */
    void addServerRoute(JID route, LocalOutgoingServerSession destination);

    /**
     * Adds a route to the routing table for the specified internal or external component. <p>
     *
     * When running inside of a cluster this message <tt>must</tt> be sent from the cluster
     * node that is actually hosting the component. The component may be available in all
     * or some of cluster nodes. The routing table will keep track of all nodes hosting
     * the component. 
     *
     * @param route the address associated to the route.
     * @param destination the component.
     */
    void addComponentRoute(JID route, RoutableChannelHandler destination);

    /**
     * Adds a route to the routing table for the specified client session. The client
     * session will be added as soon as the user has finished authenticating with the server.
     * Moreover, when the user becomes available or unavailable then the routing table will
     * get updated again. When running inside of a cluster this message <tt>must</tt> be sent
     * from the cluster node that is actually holding the client session.
     *
     * @param route the address associated to the route.
     * @param destination the client session.
     */
    void addClientRoute(JID route, LocalClientSession destination);

    /**
     * Routes a packet to the specified address. The packet destination can be a
     * user on the local server, a component, or a foreign server.<p>
     *
     * When routing a packet to a remote server then a new outgoing connection
     * will be created to the remote server if none was found and the packet
     * will be delivered. If an existing outgoing connection already exists then
     * it will be used for delivering the packet. Moreover, when runing inside of a cluster
     * the node that has the actual outgoing connection will be requested to deliver
     * the requested packet.<p>
     *
     * Packets routed to components will only be sent if the internal or external
     * component is connected to the server. Moreover, when runing inside of a cluster
     * the node that is hosting the component will be requested to deliver the requested
     * packet. It will be first checked if the component is available in this JVM and if not
     * then the first cluster node found hosting the component will be used.<p>
     *
     * Packets routed to users will be delivered if the user is connected to the server. Depending
     * on the packet type and the sender of the packet only available or all user sessions could
     * be considered. For instance, {@link org.xmpp.packet.Message Messages} and
     * {@link org.xmpp.packet.Presence Presences} are only sent to available client sessions whilst
     * {@link org.xmpp.packet.IQ IQs} originated to the server can be sent to available or unavailable
     * sessions. When runing inside of a cluster the node that is hosting the user session will be
     * requested to deliver the requested packet.<p>
     *
     * @param jid the receipient of the packet to route.
     * @param packet the packet to route.
     * @throws PacketException thrown if the packet is malformed (results in the sender's
     *      session being shutdown).
     */
    void routePacket(JID jid, Packet packet) throws PacketException;

    /**
     * Returns true if a registered user or anonymous user with the specified full JID is
     * currently logged. When running inside of a cluster a true value will be returned
     * as long as the user is connected to any cluster node.
     *
     * // TODO Should we care about available or not available????
     *
     * @param jid the full JID of the user.
     * @return true if a registered user or anonymous user with the specified full JID is
     * currently logged.
     */
    boolean hasClientRoute(JID jid);

    /**
     * Returns true if an anonymous user with the specified full JID is currently logged.
     * When running inside of a cluster a true value will be returned as long as the
     * user is connected to any cluster node.
     *
     * @param jid the full JID of the anonymous user.
     * @return true if an anonymous user with the specified full JID is currently logged.
     */
    boolean isAnonymousRoute(JID jid);

    /**
     * Returns true if an outgoing server session exists to the specified remote server.
     * The JID can be a full JID or a bare JID since only the domain of the specified
     * address will be used to look up the route.<p>
     *
     * When running inside of a cluster the look up will be done in all the cluster. So
     * as long as a node has a connection to the remote server a true value will be
     * returned.
     *
     * @param jid JID that specifies the remote server address.
     * @return true if an outgoing server session exists to the specified remote server.
     */
    boolean hasServerRoute(JID jid);

    /**
     * Returns true if an internal or external component is hosting the specified address.
     * The JID can be a full JID or a bare JID since only the domain of the specified
     * address will be used to look up the route.<p>
     *
     * When running inside of a cluster the look up will be done in all the cluster. So
     * as long as a node is hosting the component  a true value will be returned.
     *
     * @param jid JID that specifies the component address.
     * @return true if an internal or external component is hosting the specified address.
     */
    boolean hasComponentRoute(JID jid);

    /**
     * Returns the client session associated to the specified XMPP address or <tt>null</tt>
     * if none was found. When running inside of a cluster and a remote node is hosting
     * the client session then a session surrage will be returned.
     *
     * @param jid the address of the session.
     * @return the client session associated to the specified XMPP address or null if none was found.
     */
    ClientSession getClientRoute(JID jid);

    /**
     * Returns collection of client sessions authenticated with the server. When running inside
     * of a cluster the returned sessions will include sessions connected to this JVM and also
     * other cluster nodes.
     *
     * TODO Prevent usage of this message and change original requirement to avoid having to load all sessions.
     * TODO This may not scale when hosting millions of sessions.
     *
     * @return collection of client sessions authenticated with the server.
     */
    Collection<ClientSession> getClientsRoutes();

    /**
     * Returns the outgoing server session associated to the specified XMPP address or <tt>null</tt>
     * if none was found. When running inside of a cluster and a remote node is hosting
     * the session then a session surrage will be returned.
     *
     * @param jid the address of the session.
     * @return the outgoing server session associated to the specified XMPP address or null if none was found.
     */
    OutgoingServerSession getServerRoute(JID jid);

    /**
     * Returns a collection with the hostnames of the remote servers that currently may receive
     * packets sent from this server.
     *
     * @return a collection with the hostnames of the remote servers that currently may receive
     *         packets sent from this server.
     */
    Collection<String> getServerHostnames();

    /**
     * Returns the list of routes associated to the specified route address. When asking
     * for routes to a remote server then the requested JID will be included as the only
     * value of the returned collection. It is indifferent if an outgoing session to the
     * specified remote server exists or not.<p>
     *
     * When asking for routes to client sessions the specified route address could either
     * be a full JID of a bare JID. In the case of a full JID, a single element will be
     * included in the answer in case the specified full JID exists or an empty collection
     * if the full JID does not exist. Moreover, when passing a bare JID a list of full
     * JIDs will be returned for each available resource associated to the bare JID. In
     * any case, only JIDs of <tt>available</tt> client sessions are returned.<p>
     *
     * When asking for routes to components a single element will be returned in the answer
     * only if an internal or external component is found for the specified route address.
     * If no component was found then an empty collection will be returned.
     *
     * @param route The address we want a route to.
     * @return list of routes associated to the specified route address.
     */
    List<JID> getRoutes(JID route);

    /**
     * Returns true if a route of a client session has been successfully removed. When running
     * inside of a cluster this message <tt>must</tt> be sent from the cluster node that is
     * actually hosting the client session.
     *
     * @param route the route to remove.
     * @return true if a route of a client session has been successfully removed.
     */
    boolean removeClientRoute(JID route);

    /**
     * Returns true if a route to an outoing server has been successfully removed. When running
     * inside of a cluster this message <tt>must</tt> be sent from the cluster node that is
     * actually holding the physical connectoin to the remote server. 
     *
     * @param route the route to remove.
     * @return true if the route was successfully removed.
     */
    boolean removeServerRoute(JID route);

    /**
     * Returns true if a route of a component has been successfully removed. Both internal
     * and external components have a route in the table. When running inside of a cluster
     * this message <tt>must</tt> be sent from the cluster node that is actually hosting the
     * component.
     *
     * @param route the route to remove.
     * @return true if a route of a component has been successfully removed.
     */
    boolean removeComponentRoute(JID route);

    /**
     * Sets the {@link RemotePacketRouter} to use for deliverying packets to entities hosted
     * in remote nodes of the cluster.
     *
     * @param remotePacketRouter the RemotePacketRouter to use for deliverying packets to entities hosted
     *        in remote nodes of the cluster.
     */
    void setRemotePacketRouter(RemotePacketRouter remotePacketRouter);

    /**
     * Returns the {@link RemotePacketRouter} to use for deliverying packets to entities hosted
     * in remote nodes of the cluster or <tt>null</tt> if none was set.
     *
     * @return the RemotePacketRouter to use for deliverying packets to entities hosted
     *        in remote nodes of the cluster.
     */
    RemotePacketRouter getRemotePacketRouter();

    /**
     * Broadcasts the specified message to connected client sessions to the local node or
     * across the cluster. Both available and unavailable client sessions will receive the message.
     *
     * @param packet the message to broadcast.
     * @param onlyLocal true if only client sessions connecte to the local JVM will get the message.
     */
    void broadcastPacket(Message packet, boolean onlyLocal);
}
