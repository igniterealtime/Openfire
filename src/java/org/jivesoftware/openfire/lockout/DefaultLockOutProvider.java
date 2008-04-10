/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package org.jivesoftware.openfire.lockout;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;

import java.sql.*;
import java.util.Date;

/**
 * The DefaultLockOutProvider works with the ofUserFlag table to maintain a list of disabled/locked out
 * accounts., and as the name implies, is the default LockOutProvider implementation.
 *
 * @author Daniel Henninger
 */
public class DefaultLockOutProvider implements LockOutProvider {

    private static final String FLAG_ID = "lockout";
    private static final String DELETE_FLAG =
            "DELETE FROM ofUserFlag WHERE username=? AND name='"+FLAG_ID+"'";
    private static final String ADD_FLAG =
            "INSERT INTO ofUserFlag VALUES(?,'"+FLAG_ID+"',?,?)";
    private static final String RETRIEVE_FLAG =
            "SELECT name,startTime,endTime FROM ofUserFlag WHERE username=? AND name='"+FLAG_ID+"'";

    /**
     * Constructs a new DefaultLockOutProvider
     */
    public DefaultLockOutProvider() {

    }

    /**
     * Default provider retrieves disabled status from ofUserFlag table.
     * @see org.jivesoftware.openfire.lockout.LockOutProvider#getDisabledStatus(String)
     */
    public LockOutFlag getDisabledStatus(String username) {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        LockOutFlag ret = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(RETRIEVE_FLAG);
            pstmt.setString(1, username);
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                return null;
            }
            Date startTime = null;
            if (rs.getString(2) != null) {
                startTime = new Date(Long.parseLong(rs.getString(2).trim()));
            }
            Date endTime = null;
            if (rs.getString(3) != null) {
                endTime = new Date(Long.parseLong(rs.getString(3).trim()));
            }

            ret = new LockOutFlag(username, startTime, endTime);
        }
        catch (Exception e) {
            Log.error("Error loading lockout information from DB", e);
            return null;
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        return ret;
    }

    /**
     * Default provider deletes existing flag, if it exists, and adds new described flag in ofUserFlag table.
     * @see org.jivesoftware.openfire.lockout.LockOutProvider#setDisabledStatus(LockOutFlag)
     */
    public void setDisabledStatus(LockOutFlag flag) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_FLAG);
            pstmt.setString(1, flag.getUsername());
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            // Nothing to do.
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ADD_FLAG);
            pstmt.setString(1, flag.getUsername());
            if (flag.getStartTime() != null) {
                pstmt.setString(2, StringUtils.dateToMillis(flag.getStartTime()));
            }
            else {
                pstmt.setNull(2, Types.VARCHAR);
            }
            if (flag.getEndTime() != null) {
                pstmt.setString(3, StringUtils.dateToMillis(flag.getEndTime()));
            }
            else {
                pstmt.setNull(3, Types.VARCHAR);
            }
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            // Nothing to do.
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /**
     * Default provider deletes existing flag from ofUserFlag table.
     * @see org.jivesoftware.openfire.lockout.LockOutProvider#unsetDisabledStatus(String)
     */
    public void unsetDisabledStatus(String username) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_FLAG);
            pstmt.setString(1, username);
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            // Nothing to do.
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /**
     * Default provider allows editing of disabled status.
     * @see org.jivesoftware.openfire.lockout.LockOutProvider#isReadOnly()
     */
    public boolean isReadOnly() {
        return false;
    }

    /**
     * Default provider allows delayed start to disabled status.
     * @see org.jivesoftware.openfire.lockout.LockOutProvider#isDelayedStartSupported()
     */
    public boolean isDelayedStartSupported() {
        return true;
    }

    /**
     * Default provider allows timeout of disabled status.
     * @see org.jivesoftware.openfire.lockout.LockOutProvider#isTimeoutSupported()
     */
    public boolean isTimeoutSupported() {
        return true;
    }

    /**
     * Default provider should be cached.
     * @see org.jivesoftware.openfire.lockout.LockOutProvider#shouldNotBeCached()
     */
    public boolean shouldNotBeCached() {
        return false;
    }

}
