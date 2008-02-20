/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package org.jivesoftware.openfire.security;

import org.jivesoftware.database.SequenceManager;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.Log;
import org.jivesoftware.openfire.XMPPServer;

import java.util.List;
import java.util.Date;
import java.util.ArrayList;
import java.sql.*;

/**
 * The default security audit provider stores the logs in a jiveSecurityAuditLog table.
 *
 * @author Daniel Henninger
 */
public class DefaultSecurityAuditProvider implements SecurityAuditProvider {

    private static final String LOG_ENTRY =
            "INSERT INTO jiveSecurityAuditLog VALUES(?,?,?,?,?,?)";
    private static final String GET_EVENTS =
            "SELECT msgID,username,entryStamp,summary,node,details FROM jiveSecurityAuditLog";
    private static final String GET_EVENT =
            "SELECT msgID,username,entryStamp,summary,node,details FROM jiveSecurityAuditLog WHERE msgID=?";

    /**
     * Constructs a new DefaultSecurityAuditProvider
     */
    public DefaultSecurityAuditProvider() {

    }

    /**
     * The default provider logs events into a jiveSecurityAuditLog table in the database.
     * @see org.jivesoftware.openfire.security.SecurityAuditProvider#logEvent(String, String, String)
     */
    public void logEvent(String username, String summary, String details) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            long msgID = SequenceManager.nextID(JiveConstants.SECURITY_AUDIT);
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOG_ENTRY);
            pstmt.setLong(1, msgID);
            pstmt.setString(2, username);
            pstmt.setLong(3, new Date().getTime());
            pstmt.setString(4, summary);
            pstmt.setString(5, XMPPServer.getInstance().getServerInfo().getHostname());
            pstmt.setString(6, details);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.warn("Error trying to insert a new row in jiveSecurityAuditLog: ", e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /**
     * The default provider retrieves events from a jiveSecurityAuditLog table in the database.
     * @see org.jivesoftware.openfire.security.SecurityAuditProvider#getEvents(String, Integer, Integer, java.util.Date, java.util.Date)
     */
    public List<SecurityAuditEvent> getEvents(String username, Integer skipEvents, Integer numEvents, Date startTime, Date endTime) {
        List<SecurityAuditEvent> events = new ArrayList<SecurityAuditEvent>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        String sql = GET_EVENTS;
        boolean addedOne = false;
        if (username != null) {
            sql += " WHERE username = ?";
            addedOne = true;
        }
        if (startTime != null) {
            if (!addedOne) {
                sql += " WHERE";
            }
            else {
                sql += " AND";
            }
            sql += " entryStamp >= ?";
            addedOne = true;
        }
        if (endTime != null) {
            if (!addedOne) {
                sql += " WHERE";
            }
            else {
                sql += " AND";
            }
            sql += " entryStamp <= ?";
        }
        sql += " ORDER BY entryStamp DESC";
        try {
            con = DbConnectionManager.getConnection();
            pstmt = DbConnectionManager.createScrollablePreparedStatement(con, sql);
            if (skipEvents != null) {
                DbConnectionManager.scrollResultSet(rs, skipEvents);
            }
            if (numEvents != null) {
                DbConnectionManager.setFetchSize(rs, numEvents);
            }
            int i = 1;
            if (username != null) {
                pstmt.setString(i, username);
                i++;
            }
            if (startTime != null) {
                pstmt.setLong(i, startTime.getTime());
                i++;
            }
            if (endTime != null) {
                pstmt.setLong(i, endTime.getTime());
            }
            rs = pstmt.executeQuery();
            int count = 0;
            while (rs.next() && count < numEvents) {
                SecurityAuditEvent event = new SecurityAuditEvent();
                event.setMsgID(rs.getLong(1));
                event.setUsername(rs.getString(2));
                event.setEventStamp(new Date(rs.getLong(3)));
                event.setSummary(rs.getString(4));
                event.setNode(rs.getString(5));
                event.setDetails(rs.getString(6));
                events.add(event);
                count++;
            }
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return events;
    }

    /**
     * The default provider retrieves events from a jiveSecurityAuditLog table in the database.
     * @see org.jivesoftware.openfire.security.SecurityAuditProvider#getEvent(Integer)
     */
    public SecurityAuditEvent getEvent(Integer msgID) throws EventNotFoundException {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        SecurityAuditEvent event = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GET_EVENT);
            pstmt.setLong(1, msgID);
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new EventNotFoundException();
            }
            event = new SecurityAuditEvent();
            event.setMsgID(rs.getLong(1));
            event.setUsername(rs.getString(2));
            event.setEventStamp(new Date(rs.getLong(3)));
            event.setSummary(rs.getString(4));
            event.setNode(rs.getString(5));
            event.setDetails(rs.getString(6));
        }
        catch (Exception e) {
            throw new EventNotFoundException();
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        return event;
    }

    /**
     * The default provider writes logs into a local Openfire database.
     * @see org.jivesoftware.openfire.security.SecurityAuditProvider#isWriteOnly()
     */
    public boolean isWriteOnly() {
        return false;
    }

    /**
     * The default provider uses Openfire's own audit log viewer.
     * @see org.jivesoftware.openfire.security.SecurityAuditProvider#getAuditURL()
     */
    public String getAuditURL() {
        return null;
    }

    /**
     * The default provider logs user events.
     * @see org.jivesoftware.openfire.security.SecurityAuditProvider#blockUserEvents()
     */
    public boolean blockUserEvents() {
        return false;
    }

    /**
     * The default provider logs group events.
     * @see org.jivesoftware.openfire.security.SecurityAuditProvider#blockGroupEvents()
     */
    public boolean blockGroupEvents() {
        return false;
    }

}
