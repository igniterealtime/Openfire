/**
 * $RCSfile$
 * $Revision: 32902 $
 * $Date: 2006-08-04 11:11:39 -0700 (Fri, 04 Aug 2006) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.xmpp.workgroup.routing;

import org.jivesoftware.xmpp.workgroup.RequestQueue;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.request.UserRequest;
import org.jivesoftware.xmpp.workgroup.spi.routers.WordMatchRouter;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.NotFoundException;
import org.xmpp.component.ComponentManagerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Provides a registration and event processing for all <code>RequestRouter</code>s.
 *
 * @author Derek DeMoro
 */
public final class RoutingManager {

    private static final String ADD_ROUTING_RULE =
            "INSERT INTO fpRouteRule (workgroupID, queueID, rulePosition, query) VALUES (?,?,?,?)";
    private static final String DELETE_ROUTING_RULE =
            "DELETE FROM fpRouteRule WHERE workgroupID=? AND rulePosition=?";
    private static final String LOAD_RULES =
            "SELECT queueID, rulePosition, query FROM fpRouteRule WHERE workgroupID=?";
    private static final String UPDATE_RULE_POSITION =
            "UPDATE fpRouteRule SET rulePosition=? WHERE workgroupID=? AND rulePosition=?";

    private static RoutingManager singleton = new RoutingManager();

    /**
     * Returns the singleton instance of <CODE>RoutingManager</CODE>,
     * creating it if necessary.
     * <p/>
     *
     * @return the singleton instance of <Code>RoutingManager</CODE>
     */
    public static RoutingManager getInstance() {
        return singleton;
    }

    public static void shutdown() {
        singleton = null;
    }

    private RoutingManager() {

    }

    /**
     * Returns the best {@link RequestQueue} in the specified {@link Workgroup} that could
     * handle the specified {@link UserRequest}.
     *
     * @param workgroup the workgroup where a queue will be searched.
     * @param request the user request to be handled.
     * @return the best RequestQueue in the specified Workgroup.
     */
    public RequestQueue getBestQueue(Workgroup workgroup, UserRequest request) {
        WordMatchRouter router = new WordMatchRouter();
        for (RoutingRule rule : getRoutingRules(workgroup)) {
            String query = rule.getQuery();

            boolean handled = router.checkForHits(request.getMetaData(), query);
            if (handled) {
                // Retrieve queue and route to it.
                try {
                    return workgroup.getRequestQueue(rule.getQueueID());
                }
                catch (NotFoundException e) {
                    Log.error(e);
                }
            }
        }

        List<RequestQueue> availableRequestQueues = new ArrayList<RequestQueue>();
        // Route to best queue based on availability.
        for (RequestQueue requestQueue : workgroup.getRequestQueues()) {
            // Skip queues that do not have agents at the moment
            if (requestQueue != null && requestQueue.isOpened()) {
                availableRequestQueues.add(requestQueue);
            }
        }

        Collections.sort(availableRequestQueues, queueComparator);
        return availableRequestQueues.get(0);
    }

    /**
     * Routes a request to all registers globalRouters within the Live Assistant Server.
     *
     * @param workgroup the workgroup the request came in on.
     * @param request   the actual request.
     */
    public void routeRequest(Workgroup workgroup, UserRequest request) {
        getBestQueue(workgroup, request).addRequest(request);
    }

    /**
     * Adds a new Routing Rule to the database.
     *
     * @param workgroup the <code>Workgroup</code> the routing rule belongs to.
     * @param queueID the id of the queue.
     * @param position the position of the routing rule.
     * @param query the lucense query
     */
    public void addRoutingRule(Workgroup workgroup, long queueID, int position, String query) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ADD_ROUTING_RULE);

            pstmt.setLong(1, workgroup.getID());
            pstmt.setLong(2, queueID);
            pstmt.setInt(3, position);
            pstmt.setString(4, query);

            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /**
     * Updates a Routing rule to a new position.
     *
     * @param workgroup the workgroup the rule belongs to.
     * @param position the current position.
     * @param newPosition the new position.
     */
    public void updateRoutingRule(Workgroup workgroup, int position, int newPosition) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_RULE_POSITION);

            pstmt.setInt(1, newPosition);
            pstmt.setLong(2, workgroup.getID());
            pstmt.setInt(3, position);

            pstmt.execute();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /**
     * Removes a RoutingRule from the database.
     *
     * @param workgroup the workgroup the routing rule belongs to.
     * @param position the position of the routing rule.
     */
    public void removeRoutingRule(Workgroup workgroup, int position) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_ROUTING_RULE);

            pstmt.setLong(1, workgroup.getID());
            pstmt.setInt(2, position);

            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /**
     * Returns all RoutingRules belong to the given workgroup.
     *
     * @param workgroup the workgroup.
     * @return a Collection of all RoutingRules sorted by position.
     */
    public Collection<RoutingRule> getRoutingRules(Workgroup workgroup) {
        final List<RoutingRule> rules = new ArrayList<RoutingRule>();

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_RULES);
            pstmt.setLong(1, workgroup.getID());
            rs = pstmt.executeQuery();
            while (rs.next()) {
                long queueID = rs.getLong("queueID");
                int position = rs.getInt("rulePosition");
                String query = rs.getString("query");

                RoutingRule rule = new RoutingRule(queueID, position, query);
                rules.add(rule);
            }
        }
        catch (Exception ex) {
            Log.error(ex);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        // Sort by position.
        Collections.sort(rules, positionComparator);

        return rules;
    }

    private final Comparator<RoutingRule> positionComparator = new Comparator<RoutingRule>() {
        public int compare(RoutingRule rule1, RoutingRule rule2) {
            int int1 = rule1.getPosition();
            int int2 = rule2.getPosition();

            if (int1 == int2) {
                return 0;
            }

            if (int1 > int2) {
                return 1;
            }

            if (int1 < int2) {
                return -1;
            }

            return 0;
        }
    };

    private final Comparator<RequestQueue> queueComparator = new Comparator<RequestQueue>() {
        public int compare(RequestQueue queue1, RequestQueue queue2) {
            int int1 = queue1.getTotalRequestCount();
            int int2 = queue2.getTotalRequestCount();

            if (int1 == int2) {
                return 0;
            }

            if (int1 > int2) {
                return 1;
            }

            if (int1 < int2) {
                return -1;
            }

            return 0;
        }
    };
}
