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

import org.jivesoftware.util.Log;
import org.jivesoftware.openfire.ChannelHandler;
import org.jivesoftware.openfire.RoutableChannelHandler;
import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.server.OutgoingSessionPromise;
import org.jivesoftware.openfire.session.ClientSession;
import org.xmpp.packet.JID;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>Uses simple Maps for table storage.</p>
 * <p>Leaves in the tree are indicated by a PacketHandler, while branches are stored in Maps.
 * Traverse the tree according to an XMPPAddress' fields (host -> name -> resource) and when you
 * hit a PacketHandler, you have found the handler for that node and all sub-nodes. </p>
 *
 * @author Iain Shigeoka
 */
public class RoutingTableImpl extends BasicModule implements RoutingTable {

    /**
     * We need a three level tree built of hashtables: host -> name -> resource
     */
    private Map routes = new ConcurrentHashMap();

    private String serverName;
    private InternalComponentManager componentManager;

    public RoutingTableImpl() {
        super("Routing table");
    }

    public void addRoute(JID node, RoutableChannelHandler destination) {

        String nodeJID = node.getNode() == null ? "" : node.getNode();
        String resourceJID = node.getResource() == null ? "" : node.getResource();

        if (destination instanceof ClientSession) {
            Object nameRoutes = routes.get(node.getDomain());
            if (nameRoutes == null) {
                // No route to the requested domain. Create a new entry in the table
                synchronized (node.getDomain().intern()) {
                    // Check again if a route exists now that we have a lock
                    nameRoutes = routes.get(node.getDomain());
                    if (nameRoutes == null) {
                        // Still nothing so create a new entry in the map for domain
                        nameRoutes = new ConcurrentHashMap();
                        routes.put(node.getDomain(), nameRoutes);
                    }
                }
            }
            // Check if there is something associated with the node of the JID
            Object resourceRoutes = ((Map) nameRoutes).get(nodeJID);
            if (resourceRoutes == null) {
                // Nothing was found so create a new entry for this node (a.k.a. user)
                synchronized (nodeJID.intern()) {
                    resourceRoutes = ((Map) nameRoutes).get(nodeJID);
                    if (resourceRoutes == null) {
                        resourceRoutes = new ConcurrentHashMap();
                        ((Map) nameRoutes).put(nodeJID, resourceRoutes);
                    }
                }
            }
            // Add the connected resource to the node's Map
            ((Map) resourceRoutes).put(resourceJID, destination);
        }
        else {
            routes.put(node.getDomain(), destination);
        }
    }

    public RoutableChannelHandler getRoute(JID node) {
        if (node == null) {
            return null;
        }
        return getRoute(node.toString(), node.getNode() == null ? "" : node.getNode(),
                node.getDomain(), node.getResource() == null ? "" : node.getResource());
    }

    private RoutableChannelHandler getRoute(String jid, String node, String domain,
            String resource) {
        RoutableChannelHandler route = null;

        // Check if the address belongs to a remote server
        if (!serverName.equals(domain) && routes.get(domain) == null &&
                componentManager.getComponent(domain) == null) {
            // Return a promise of a remote session. This object will queue packets pending
            // to be sent to remote servers
            return OutgoingSessionPromise.getInstance();
        }

        try {
            Object nameRoutes = routes.get(domain);
            if (nameRoutes instanceof ChannelHandler) {
                route = (RoutableChannelHandler) nameRoutes;
            }
            else if (nameRoutes != null) {
                Object resourceRoutes = ((Map) nameRoutes).get(node);
                if (resourceRoutes instanceof ChannelHandler) {
                    route = (RoutableChannelHandler) resourceRoutes;
                }
                else if (resourceRoutes != null) {
                    route = (RoutableChannelHandler) ((Map) resourceRoutes).get(resource);
                }
                else {
                    route = null;
                }
            }
        }
        catch (Exception e) {
            if (Log.isDebugEnabled()) {
                Log.debug("Route not found for JID: " + jid, e);
            }
        }

        return route;
    }

    public List<ChannelHandler> getRoutes(JID node) {
        // Check if the address belongs to a remote server
        if (!serverName.equals(node.getDomain()) && routes.get(node.getDomain()) == null &&
                componentManager.getComponent(node) == null) {
            // Return a promise of a remote session. This object will queue packets pending
            // to be sent to remote servers
            List<ChannelHandler> list = new ArrayList<ChannelHandler>();
            list.add(OutgoingSessionPromise.getInstance());
            return list;
        }

        LinkedList list = null;
        Object nameRoutes = routes.get(node.getDomain());
        if (nameRoutes != null) {
            if (nameRoutes instanceof ChannelHandler) {
                list = new LinkedList();
                list.add(nameRoutes);
            }
            else if (node.getNode() == null) {
                list = new LinkedList();
                getRoutes(list, (Map) nameRoutes);
            }
            else {
                Object resourceRoutes = ((Map) nameRoutes).get(node.getNode());
                if (resourceRoutes != null) {
                    if (resourceRoutes instanceof ChannelHandler) {
                        list = new LinkedList();
                        list.add(resourceRoutes);
                    }
                    else if (node.getResource() == null || node.getResource().length() == 0) {
                        list = new LinkedList();
                        getRoutes(list, (Map) resourceRoutes);
                    }
                    else {
                        Object entry = ((Map) resourceRoutes).get(node.getResource());
                        if (entry != null) {
                            list = new LinkedList();
                            list.add(entry);
                        }
                    }
                }
            }
        }
        if (list == null) {
            return Collections.emptyList();
        }
        else {
            return list;
        }
    }

    /**
     * Recursive method to iterate through the given table (and any embedded map)
     * and stuff non-Map values into the given list.<p>
     *
     * There should be no recursion problems since the routing table is at most 3 levels deep.
     *
     * @param list  The list to stuff entries into
     * @param table The hashtable who's values should be entered into the list
     */
    private void getRoutes(LinkedList list, Map table) {
        Iterator entryIter = table.values().iterator();
        while (entryIter.hasNext()) {
            Object entry = entryIter.next();
            if (entry instanceof ConcurrentHashMap) {
                getRoutes(list, (Map)entry);
            }
            else {
                // Do not include the same entry many times. This could be the case when the same 
                // session is associated with the bareJID and with a given resource
                if (!list.contains(entry)) {
                    list.add(entry);
                }
            }
        }
    }

    public ChannelHandler getBestRoute(JID node) {
        ChannelHandler route = getRoute(node);
        if (route == null) {
            // Try looking for a route based on the bare JID
            String nodeJID = node.getNode() == null ? "" : node.getNode();
            route = getRoute(node.toBareJID(), nodeJID, node.getDomain(), "");
        }
        return route;
    }

    public ChannelHandler removeRoute(JID node) {

        ChannelHandler route = null;
        String nodeJID = node.getNode() == null ? "" : node.getNode();
        String resourceJID = node.getResource() == null ? "" : node.getResource();

        try {
            Object nameRoutes = routes.get(node.getDomain());
            if (nameRoutes instanceof ConcurrentHashMap) {
                Object resourceRoutes = ((Map) nameRoutes).get(nodeJID);
                if (resourceRoutes instanceof ConcurrentHashMap) {
                    // Remove the requested resource for this user
                    route = (ChannelHandler) ((Map) resourceRoutes).remove(resourceJID);
                    if (((Map) resourceRoutes).isEmpty()) {
                        ((Map) nameRoutes).remove(nodeJID);
                        if (((Map) nameRoutes).isEmpty()) {
                            routes.remove(node.getDomain());
                        }
                    }
                }
                else {
                    // Remove the unique route to this node
                    ((Map) nameRoutes).remove(nodeJID);
                }
            }
            else if (nameRoutes != null) {
                // The retrieved route points to a RoutableChannelHandler
                if (("".equals(nodeJID) && "".equals(resourceJID)) ||
                        ((RoutableChannelHandler) nameRoutes).getAddress().equals(node)) {
                    // Remove the route to this domain
                    routes.remove(node.getDomain());
                }
            }
        }
        catch (Exception e) {
            Log.error("Error removing route", e);
        }
        return route;
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);
        serverName = server.getServerInfo().getName();
    }

    public void start() throws IllegalStateException {
        super.start();
        componentManager = InternalComponentManager.getInstance();
    }
}