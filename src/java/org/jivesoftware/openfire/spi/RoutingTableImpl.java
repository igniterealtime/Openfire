/**
 * $RCSfile: RoutingTableImpl.java,v $
 * $Revision: 3138 $
 * $Date: 2005-12-01 02:13:26 -0300 (Thu, 01 Dec 2005) $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.spi;

import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.server.OutgoingSessionPromise;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.jivesoftware.util.lock.LockManager;
import org.xmpp.packet.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.Lock;

/**
 * Routing table that stores routes to client sessions, outgoing server sessions
 * and components. As soon as a user authenticates with the server its client session
 * will be added to the routing table. Whenever the client session becomes available
 * or unavailable the routing table will be updated too.<p>
 *
 * When running inside of a cluster the routing table will also keep references to routes
 * hosted in other cluster nodes. A {@link RemotePacketRouter} will be use to route packets
 * to routes hosted in other cluster nodes.<p>
 *
 * Failure to route a packet will end up sending {@link IQRouter#routingFailed(org.xmpp.packet.Packet)},
 * {@link MessageRouter#routingFailed(org.xmpp.packet.Packet)} or
 * {@link PresenceRouter#routingFailed(org.xmpp.packet.Packet)} depending on the packet type
 * that tried to be sent.
 *
 * @author Gaston Dombiak
 */
public class RoutingTableImpl extends BasicModule implements RoutingTable {

    /**
     * Cache (unlimited, never expire) that holds outgoing sessions to remote servers from this server.
     */
    private Cache<String, byte[]> serversCache;
    /**
     * Cache (unlimited, never expire) that holds sessions of external components connected to the server.
     */
    private Cache<String, byte[]> componentsCache;
    /**
     * Cache (unlimited, never expire) that holds sessions of user that have authenticated with the server.
     */
    private Cache<String, ClientRoute> usersCache;
    /**
     * Cache (unlimited, never expire) that holds sessions of anoymous user that have authenticated with the server.
     */
    private Cache<String, ClientRoute> anonymousUsersCache;
    /**
     * Cache (unlimited, never expire) that holds list of connected resources of authenticated users
     * (includes anonymous). Key: bare jid, Value: List of full JIDs.
     */
    private Cache<String, List<String>> usersSessions;

    private String serverName;
    private XMPPServer server;
    private LocalRoutingTable localRoutingTable;
    private RemotePacketRouter remotePacketRouter;
    private IQRouter iqRouter;
    private MessageRouter messageRouter;
    private PresenceRouter presenceRouter;

    public RoutingTableImpl() {
        super("Routing table");
        serversCache = CacheFactory.createCache("Routing Servers Cache");
        componentsCache = CacheFactory.createCache("Routing Components Cache");
        usersCache = CacheFactory.createCache("Routing Users Cache");
        anonymousUsersCache = CacheFactory.createCache("Routing AnonymousUsers Cache");
        usersSessions = CacheFactory.createCache("Routing User Sessions");
        localRoutingTable = new LocalRoutingTable();
    }

    public void addServerRoute(JID route, RoutableChannelHandler destination) {
        String address = destination.getAddress().getDomain();
        localRoutingTable.addRoute(address, destination);
        serversCache.put(address, server.getNodeID());
    }

    public void addComponentRoute(JID route, RoutableChannelHandler destination) {
        String address = destination.getAddress().getDomain();
        localRoutingTable.addRoute(address, destination);
        componentsCache.put(address, server.getNodeID());
    }

    public void addClientRoute(JID route, ClientSession destination) {
        String address = destination.getAddress().toString();
        boolean available = destination.getPresence().isAvailable();
        localRoutingTable.addRoute(address, destination);
        if (destination.getAuthToken().isAnonymous()) {
            anonymousUsersCache.put(address, new ClientRoute(server.getNodeID(), available));
            // Add the session to the list of user sessions
            if (route.getResource() != null && !available) {
                Lock lock = LockManager.getLock(route.toBareJID());
                try {
                    lock.lock();
                    usersSessions.put(route.toBareJID(), Arrays.asList(route.toString()));
                }
                finally {
                    lock.unlock();
                }
            }
        }
        else {
            usersCache.put(address, new ClientRoute(server.getNodeID(), available));
            // Add the session to the list of user sessions
            if (route.getResource() != null && !available) {
                Lock lock = LockManager.getLock(route.toBareJID());
                try {
                    lock.lock();
                    List<String> jids = usersSessions.get(route.toBareJID());
                    if (jids == null) {
                        jids = new ArrayList<String>();
                    }
                    jids.add(route.toString());
                    usersSessions.put(route.toBareJID(), jids);
                }
                finally {
                    lock.unlock();
                }
            }
        }
    }

    public void broadcastPacket(Message packet, boolean onlyLocal) {
        // Send the message to client sessions connected to this JVM
        for(RoutableChannelHandler session : localRoutingTable.getClientRoutes()) {
            try {
                session.process(packet);
            } catch (UnauthorizedException e) {
                // Should never happen
            }
        }

        // Check if we need to broadcast the message to client sessions connected to remote cluter nodes
        if (!onlyLocal && remotePacketRouter != null) {
            remotePacketRouter.broadcastPacket(packet);
        }
    }

    public void routePacket(JID jid, Packet packet) throws UnauthorizedException, PacketException {
        boolean routed = false;
        JID address = packet.getTo();
        if (address == null) {
            throw new PacketException("To address cannot be null.");
        }

        if (serverName.equals(jid.getDomain())) {
            boolean onlyAvailable = true;
            if (packet instanceof IQ) {
                onlyAvailable = packet.getFrom() != null;
            }
            else if (packet instanceof Message) {
                onlyAvailable = true;
            }
            else if (packet instanceof Presence) {
                onlyAvailable = true;
            }

            // Packet sent to local user
            ClientRoute clientRoute = usersCache.get(jid.toString());
            if (clientRoute == null) {
                clientRoute = anonymousUsersCache.get(jid.toString());
            }
            if (clientRoute != null) {
                if (onlyAvailable && !clientRoute.isAvailable()) {
                    // Packet should only be sent to available sessions and the route is not available
                    routed = false;
                }
                else {
                    if (clientRoute.getNodeID() == server.getNodeID()) {
                        // This is a route to a local user hosted in this node
                        localRoutingTable.getRoute(jid.toString()).process(packet);
                        routed = true;
                    }
                    else {
                        // This is a route to a local user hosted in other node
                        if (remotePacketRouter != null) {
                            routed = remotePacketRouter.routePacket(clientRoute.getNodeID(), jid, packet);
                        }
                    }
                }
            }
        }
        else if (jid.getDomain().contains(serverName)) {
            // Packet sent to component hosted in this server
            byte[] nodeID = componentsCache.get(jid.getDomain());
            if (nodeID != null) {
                if (nodeID == server.getNodeID()) {
                    // This is a route to a local component hosted in this node
                    localRoutingTable.getRoute(jid.getDomain()).process(packet);
                    routed = true;
                }
                else {
                    // This is a route to a local component hosted in other node
                    if (remotePacketRouter != null) {
                        routed = remotePacketRouter.routePacket(nodeID, jid, packet);
                    }
                }
            }
        }
        else {
            // Packet sent to remote server
            byte[] nodeID = serversCache.get(jid.getDomain());
            if (nodeID != null) {
                if (nodeID == server.getNodeID()) {
                    // This is a route to a remote server connected from this node
                    localRoutingTable.getRoute(jid.getDomain()).process(packet);
                    routed = true;
                }
                else {
                    // This is a route to a remote server connected from other node
                    if (remotePacketRouter != null) {
                        routed = remotePacketRouter.routePacket(nodeID, jid, packet);
                    }
                }
            }
            else {
                // Return a promise of a remote session. This object will queue packets pending
                // to be sent to remote servers
                // TODO Make sure that creating outgoing connections is thread-safe across cluster nodes 
                OutgoingSessionPromise.getInstance().process(packet);
                routed = true;
            }
        }

        if (!routed) {
            if (Log.isDebugEnabled()) {
                Log.debug("Failed to route packet to JID: " + jid + " packet: " + packet);
            }
            if (packet instanceof IQ) {
                iqRouter.routingFailed(packet);
            }
            else if (packet instanceof Message) {
                messageRouter.routingFailed(packet);
            }
            else if (packet instanceof Presence) {
                presenceRouter.routingFailed(packet);
            }
        }
    }

    public boolean hasClientRoute(JID jid) {
        return usersCache.get(jid.toString()) != null || anonymousUsersCache.get(jid.toString()) != null;
    }

    public boolean hasServerRoute(JID jid) {
        return serversCache.get(jid.getDomain()) != null;
    }

    public boolean hasComponentRoute(JID jid) {
        return componentsCache.get(jid.getDomain()) != null;
    }

    public List<JID> getRoutes(JID route) {
        // TODO Refactor API to be able to get c2s sessions available only/all
        List<JID> jids = new ArrayList<JID>();
        if (serverName.equals(route.getDomain())) {
            // Address belongs to local user
            if (route.getResource() != null) {
                // Address is a full JID of a user
                ClientRoute clientRoute = usersCache.get(route.toString());
                if (clientRoute == null) {
                    clientRoute = anonymousUsersCache.get(route.toString());
                }
                if (clientRoute != null && clientRoute.isAvailable()) {
                    jids.add(route);
                }
            }
            else {
                // Address is a bare JID so return all AVAILABLE resources of user
                List<String> sessions = usersSessions.get(route.toBareJID());
                if (sessions != null) {
                    // Select only available sessions
                    for (String jid : sessions) {
                        ClientRoute clientRoute = usersCache.get(jid);
                        if (clientRoute == null) {
                            clientRoute = anonymousUsersCache.get(jid);
                        }
                        if (clientRoute != null && clientRoute.isAvailable()) {
                            jids.add(new JID(jid));
                        }
                    }
                }
            }
        }
        else if (route.getDomain().contains(serverName)) {
            // Packet sent to component hosted in this server
            byte[] nodeID = componentsCache.get(route.getDomain());
            if (nodeID != null) {
                jids.add(route);
            }
        }
        else {
            // Packet sent to remote server
            jids.add(route);
        }
        return jids;
    }

    public boolean removeClientRoute(JID route) {
        boolean anonymous = false;
        String address = route.toString();
        ClientRoute clientRoute = usersCache.remove(address);
        if (clientRoute == null) {
            clientRoute = anonymousUsersCache.remove(address);
            anonymous = true;
        }
        if (clientRoute != null && route.getResource() != null) {
            Lock lock = LockManager.getLock(route.toBareJID());
            try {
                lock.lock();
                if (anonymous) {
                    usersSessions.remove(route.toBareJID());
                }
                else {
                    List<String> jids = usersSessions.get(route.toBareJID());
                    if (jids != null) {
                        jids.remove(route.toString());
                        if (!jids.isEmpty()) {
                            usersSessions.put(route.toBareJID(), jids);
                        }
                        else {
                            usersSessions.remove(route.toBareJID());
                        }
                    }
                }
            }
            finally {
                lock.unlock();
            }
        }
        localRoutingTable.removeRoute(address);
        return clientRoute != null;
    }

    public boolean removeServerRoute(JID route) {
        String address = route.getDomain();
        boolean removed = serversCache.remove(address) != null;
        localRoutingTable.removeRoute(address);
        return removed;
    }

    public boolean removeComponentRoute(JID route) {
        String address = route.getDomain();
        boolean removed = componentsCache.remove(address) != null;
        localRoutingTable.removeRoute(address);
        return removed;
    }

    public void setRemotePacketRouter(RemotePacketRouter remotePacketRouter) {
        this.remotePacketRouter = remotePacketRouter;
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);
        this.server = server;
        serverName = server.getServerInfo().getName();
        iqRouter = server.getIQRouter();
        messageRouter = server.getMessageRouter();
        presenceRouter = server.getPresenceRouter();
    }

    public void start() throws IllegalStateException {
        super.start();
    }
}
