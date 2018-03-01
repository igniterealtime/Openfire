/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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
package org.jivesoftware.openfire.security;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.database.SequenceManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The default security audit provider stores the logs in a ofSecurityAuditLog table.
 *
 * @author Daniel Henninger
 */
public class DefaultSecurityAuditProvider implements SecurityAuditProvider {

    private static final Logger Log = LoggerFactory.getLogger(DefaultSecurityAuditProvider.class);

    private static final String LOG_ENTRY =
            "INSERT INTO ofSecurityAuditLog(msgID,username,entryStamp,summary,node,details) VALUES(?,?,?,?,?,?)";
    private static final String GET_EVENTS =
            "SELECT msgID,username,entryStamp,summary,node,details FROM ofSecurityAuditLog";
    private static final String GET_EVENT =
            "SELECT msgID,username,entryStamp,summary,node,details FROM ofSecurityAuditLog WHERE msgID=?";
    private static final String GET_EVENT_COUNT =
            "SELECT COUNT(msgID) FROM ofSecurityAuditLog";

    /**
     * Constructs a new DefaultSecurityAuditProvider
     */
    public DefaultSecurityAuditProvider() {

    }

    /**
     * The default provider logs events into a ofSecurityAuditLog table in the database.
     * @see org.jivesoftware.openfire.security.SecurityAuditProvider#logEvent(String, String, String)
     */
    @Override
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
            pstmt.setString(4, StringUtils.abbreviate(summary, 250));
            pstmt.setString(5, XMPPServer.getInstance().getServerInfo().getHostname());
            pstmt.setString(6, details);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.warn("Error trying to insert a new row in ofSecurityAuditLog: ", e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /**
     * The default provider retrieves events from a ofSecurityAuditLog table in the database.
     * @see org.jivesoftware.openfire.security.SecurityAuditProvider#getEvents(String, Integer, Integer, java.util.Date, java.util.Date)
     */
    @Override
    public List<SecurityAuditEvent> getEvents(String username, Integer skipEvents, Integer numEvents, Date startTime, Date endTime) {
        List<SecurityAuditEvent> events = new ArrayList<>();
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
            if (skipEvents != null) {
                DbConnectionManager.scrollResultSet(rs, skipEvents);
            }
            if (numEvents != null) {
                DbConnectionManager.setFetchSize(rs, numEvents);
            }
            
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
            Log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return events;
    }

    /**
     * The default provider retrieves events from a ofSecurityAuditLog table in the database.
     * @see org.jivesoftware.openfire.security.SecurityAuditProvider#getEvent(Integer)
     */
    @Override
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
     * The default provider counts the number of entries in the ofSecurityAuditLog table.
     * @see org.jivesoftware.openfire.security.SecurityAuditProvider#getEventCount()
     */
    @Override
    public Integer getEventCount() {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Integer cnt = 0;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GET_EVENT_COUNT);
            rs = pstmt.executeQuery();
            cnt = rs.getInt(1);
        }
        catch (Exception e) {
            // Hrm.  That should not occur.
            Log.error("Error while looking up number of security audit events: ", e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        return cnt;
    }

    /**
     * The default provider writes logs into a local Openfire database.
     * @see org.jivesoftware.openfire.security.SecurityAuditProvider#isWriteOnly()
     */
    @Override
    public boolean isWriteOnly() {
        return false;
    }

    /**
     * The default provider uses Openfire's own audit log viewer.
     * @see org.jivesoftware.openfire.security.SecurityAuditProvider#getAuditURL()
     */
    @Override
    public String getAuditURL() {
        return null;
    }

    /**
     * The default provider logs user events.
     * @see org.jivesoftware.openfire.security.SecurityAuditProvider#blockUserEvents()
     */
    @Override
    public boolean blockUserEvents() {
        return false;
    }

    /**
     * The default provider logs group events.
     * @see org.jivesoftware.openfire.security.SecurityAuditProvider#blockGroupEvents()
     */
    @Override
    public boolean blockGroupEvents() {
        return false;
    }

}
