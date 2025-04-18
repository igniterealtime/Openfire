/*
 * Copyright (C) 2004-2008 Jive Software, 2017-2025 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.openfire.session.*;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Maintains knowledge of routes to any XMPP entity.
 * <p>
 * Routes closely relate to sessions (as managed by the {@link SessionManager}). Typically, a session can be routed to
 * almost immediately after it has been established. <em>Client</em> Sessions are somewhat special, in that they can be
 * routed to only when they have sent Initial Presence (a variant exists for Clients Sessions to be routable only for
 * specific entities, when the session has sent those entities Directed Presence).
 * <p>
 * Generally speaking, every Route will have an associated Session, but not every Session may have an associated Route.
 * <p>
 * As Routes are closely related to sessions, a route is represented by a Session instance. A session will normally
 * always be obtainable from a SessionManager, but will only be obtainable from a RoutingTable when the session is
 * 'routable'.
 * <p>
 * The information maintained by a routing table is entirely transient and does not need to be preserved between
 * server restarts.
 * <p>
 * Note: it is important that any component or action affecting routes update the routing table immediately.
 *
 * @author Iain Shigeoka
 * @see SessionManager
 */
public interface RoutingTable {

    /**
     * Adds a route to the routing table for the specified outgoing server session, or replaces a pre-existing one.
     * <p>
     * When running inside a cluster, this method <em>must</em> be invoked on the cluster node that is actually holding
     * the physical connection to the remote server. Additionally, replacing a pre-existing server session can only
     * occur on the same cluster node as the one that was holding the original session. A runtime exception is thrown
     * when another cluster node attempts to replace the session.
     *
     * @param route the address associated to the route.
     * @param destination the outgoing server session.
     */
    void addServerRoute(DomainPair route, LocalOutgoingServerSession destination);

    /**
     * Removes a route from the routing table for the specified outgoing server session.
     * <p>
     * When running inside a cluster this message <em>must</em> be sent from the cluster node that is actually holding
     * the physical connection to the remote server.
     * <p>
     * Returns true if a route to an outgoing server has been successfully removed.
     *
     * @param route the route to remove.
     * @return true if the route was successfully removed.
     */
    boolean removeServerRoute(DomainPair route);

    /**
     * Adds a route to the routing table for the specified internal or external component.
     * <p>
     * When running inside a cluster this method <em>must</em> be invoked from the cluster node that is actually hosting
     * the component.
     * <p>
     * An internal component may be installed in or an external component may be connected to one, some, or all cluster
     * nodes. The routing table will keep track of all nodes hosting the component.
     *
     * @param route the address associated to the route.
     * @param destination the component.
     */
    void addComponentRoute(JID route, RoutableChannelHandler destination);

    /**
     * Removes a route from the routing table for the specified internal or external component.
     * <p>
     * When running inside a cluster this message <em>must</em> be sent from the cluster node that is actually hosting
     * the component.
     * <p>
     * Returns true if a route of a component has been successfully removed.
     *
     * @param route the route to remove.
     * @return true if a route of a component has been successfully removed.
     */
    boolean removeComponentRoute(JID route);

    /**
     * Adds a route to the routing table for the specified client session.
     * <p>
     * The client session will be added as soon as the user has finished authenticating with the server. Moreover, when
     * the user becomes available or unavailable then the routing table will get updated again.
     * <p>
     * When running inside a cluster this method <em>must</em> be invoked from the cluster node that is actually holding
     * the client session.
     *
     * @param route the address (a full JID) associated to the route.
     * @param destination the client session.
     */
    // FIXME: "Moreover, when the user becomes available or unavailable then the routing table will get updated again."
    //        This appears to be true: this method gets invoked when a session is added, but also when it becomes
    //        available and unavailable. Determine if this is needed, and if so, document why.
    void addClientRoute(JID route, LocalClientSession destination);

    /**
     * Removes a route from the routing table for the specified client session.
     * <p>
     * When running inside a cluster this message <em>must</em> be sent from the cluster node that is actually hosting
     * the client session.
     * <p>
     * Returns true if a route of a client session has been successfully removed.
     *
     * @param route the route to remove.
     * @return true if a route of a client session has been successfully removed.
     */
    boolean removeClientRoute(JID route);

    /**
     * Routes a stanza to the specified address. The stanza destination can be any XMPP entity, including a user on the
     * local XMPP domain, a component, or a remote XMPP domain.
     * <p>
     * When routing a stanza to a remote XMPP domain, then a new outgoing server connection will be created to a server
     * belonging to the target domain, if no preexisting outgoing server connection to that domain is available. If an
     * existing outgoing connection already exists then it will be used for delivering the stanza. Moreover, when
     * running inside a cluster the node that has the actual outgoing connection will be requested to deliver the
     * stanza.
     * <p>
     * Stanzas routed to components will only be sent if an internal or external component for the target address is
     * available to the server. Unlike with servers, a new connection will <em>not</em> be established when a component
     * is not available. When running inside a cluster the node that is hosting the component will be requested to
     * deliver the stanza. Components available in the local cluster node are preferred. When a component is not
     * available locally, the first cluster node found hosting the component will be used.
     * <p>
     * Stanzas routed to users of the local domain will be delivered if the user is connected to the XMPP domain.
     * Depending on the stanza type and the sender of the stanza, only available or all user sessions can be considered
     * for delivery of the stanza. For instance, {@link org.xmpp.packet.Message Messages} and
     * {@link org.xmpp.packet.Presence Presences} are only sent to available client sessions, whilst
     * {@link org.xmpp.packet.IQ IQs} originated from the XMPP domain can be sent to available or unavailable
     * sessions. When running inside a cluster the node that is hosting the user session will be requested to deliver
     * the requested stanza.
     *
     * @param jid the recipient of the stanza to route.
     * @param packet the stanza to route.
     * @throws PacketException thrown if the stanza is malformed (results in the sender's session being shutdown).
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
     * @return true if a registered user or anonymous user with the specified full JID is currently logged in.
     */
    boolean hasClientRoute(JID jid);

    /**
     * Returns true if an anonymous user with the specified full JID is currently logged in.
     * When running inside a cluster a true value will be returned as long as the
     * user is connected to any cluster node.
     *
     * @param jid the full JID of the anonymous user.
     * @return true if an anonymous user with the specified full JID is currently logged.
     * @deprecated Replaced by {@link SessionManager#isAnonymousClientSession(JID)}
     */
    @Deprecated(forRemoval = true, since = "5.0.0") // Remove in or after Openfire 5.1.0
    boolean isAnonymousRoute(JID jid);

    /**
     * Returns true if the specified address belongs to a route that is hosted by this JVM.
     * When running inside a cluster each cluster node will host routes to local resources.
     * A false value could either mean that the route is not hosted by this JVM but other
     * cluster node or that there is no route to the specified address. Use
     * {@link XMPPServer#isLocal(org.xmpp.packet.JID)} to figure out if the address
     * belongs to tge domain hosted by this server.
     *
     * @param jid the address of the route.
     * @return true if the specified address belongs to a route that is hosted by this JVM.
     */
    boolean isLocalRoute(JID jid);

    /**
     * Returns true if an outgoing server session exists to the specified remote server.
     * The JID can be a full JID or a bare JID since only the domain of the specified
     * address will be used to look up the route.
     * <p>
     * When running inside a cluster the look-up will be performed in all cluster nodes.
     * As long as any node has a connection to the remote server, a true value will be
     * returned.
     *
     * @param pair DomainPair that specifies the local/remote server address.
     * @return true if an outgoing server session exists to the specified remote server.
     */
    boolean hasServerRoute(DomainPair pair);

    /**
     * Returns true if an internal or external component exists that is hosting the specified address.
     * The JID can be a full JID or a bare JID since only the domain of the specified
     * address will be used to look up the route.
     * <p>
     * When running inside a cluster the look-up will be performed in all cluster nodes.
     * As long as any node is hosting the component, a true value will be returned.
     *
     * @param jid JID that specifies the component address.
     * @return true if an internal or external component is hosting the specified address.
     */
    boolean hasComponentRoute(JID jid);

    /**
     * Returns the client session associated to the specified XMPP address or {@code null} if none was found.
     * <p>
     * When running inside a cluster and a remote node is hosting the client session then a session surrogate will be
     * returned.
     *
     * @param jid the address of the session.
     * @return the client session associated to the specified XMPP address or null if none was found.
     */
    ClientSession getClientRoute(JID jid);

    /**
     * Returns the client sessions associated to the specified XMPP address or an empty collection
     * if none were found.
     * <p>
     * When running inside a cluster and a remote node is hosting the client session then a session surrogate will be
     * returned.
     * <p>
     * When the provided address is a full JID, at most one session is returned. If a bare JID is provided, then all
     * sessions associated to the user will be returned.
     *
     * @param jid the address of the session.
     * @return the client session associated to the specified XMPP address or null if none was found.
     */
    Set<ClientSession> getClientRoutes(JID jid);

    /**
     * Returns collection of client sessions authenticated with the server.
     * <p>
     * When running inside a cluster the returned sessions will include sessions connected to the local cluster node.
     * Optionally, sessions on other cluster nodes can be included in the result, too.
     *
     * TODO Prevent usage of this method and change original requirement to avoid having to load all sessions.
     * TODO This may not scale when hosting millions of sessions.
     *
     * @param onlyLocal true if only client sessions connected to the local cluster node are to be returned.
     * @return collection of client sessions authenticated with the server.
     */
    Collection<ClientSession> getClientsRoutes(boolean onlyLocal);

    /**
     * Returns the outgoing server session associated to the specified XMPP address or {@code null} if none was found.
     * <p>
     * When running inside a cluster and a remote node is hosting the session then a session surrogate will be returned.
     *
     * @param pair DomainPair that specifies the local/remote server address.
     * @return the outgoing server session associated to the specified XMPP address or null if none was found.
     */
    OutgoingServerSession getServerRoute(DomainPair pair);

    /**
     * Returns a collection with the hostnames of the remote servers that currently may receive
     * packets sent from this server.
     *
     * @return a collection with the hostnames of the remote servers that currently may receive
     *         packets sent from this server.
     */
    Collection<String> getServerHostnames();

    Collection<DomainPair> getServerRoutes();

    /**
     * Returns the number of outgoing server sessions hosted in the local cluster node.
     * <p>
     * When running inside a cluster you will need to get this value for each cluster node to learn the total number
     * of outgoing server sessions.
     *
     * @return the number of outgoing server sessions hosted in this JVM.
     */
    int getServerSessionsCount();

    /**
     * Returns domains (e.g. mycomponent.example.org) of all components.
     * <p>
     * When running in a cluster, domains of components running in any node will be returned.
     *
     * @return domains of all components.
     */
    Collection<String> getComponentsDomains();

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
     * any case, only JIDs of {@code available} client sessions are returned. However,
     * there is an exception with directed presences. Unavailable routes may be returned
     * if and only if the owner of the route sent a directed presence to the requester
     * thus becoming available to the requester. If requester is {@code null} then only
     * available resources are considered.<p>
     *
     * When asking for routes to components a single element will be returned in the answer
     * only if an internal or external component is found for the specified route address.
     * If no component was found then an empty collection will be returned.
     *
     * @param route The address we want a route to.
     * @param requester The address of the entity requesting the routes or null if we don't
     * care about directed presences.
     * @return list of routes associated to the specified route address.
     */
    List<JID> getRoutes(JID route, JID requester);

    /**
     * Sets the {@link RemotePacketRouter} to use for delivering packets to entities hosted
     * in remote nodes of the cluster.
     *
     * @param remotePacketRouter the RemotePacketRouter to use for delivering packets to entities hosted
     *        in remote nodes of the cluster.
     */
    void setRemotePacketRouter(RemotePacketRouter remotePacketRouter);

    /**
     * Returns the {@link RemotePacketRouter} to use for delivering packets to entities hosted
     * in remote nodes of the cluster or {@code null} if none was set.
     *
     * @return the RemotePacketRouter to use for delivering packets to entities hosted
     *        in remote nodes of the cluster.
     */
    RemotePacketRouter getRemotePacketRouter();

    /**
     * Broadcasts the specified message to connected client sessions to the local node or
     * across the cluster. Both available and unavailable client sessions will receive the message.
     *
     * @param packet the message to broadcast.
     * @param onlyLocal true if only client sessions connect to the local JVM will get the message.
     */
    void broadcastPacket(Message packet, boolean onlyLocal);
}
