/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.user.spi;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.LongList;
import org.jivesoftware.messenger.user.UserIDProvider;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.jivesoftware.database.DbConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * <p>Implements default Jive user id lookup out of the jiveUserID table.</p>
 * <p>The implementation currently hard codes the default domain since messenger only supports one domain
 * (default domain means to use the Messenger domain name).</p>
 *
 * @author Iain Shiegoka
 */
public class DbUserIDProvider implements UserIDProvider {

    /**
     * <p>Object type for users.</p>
     */
    public static final int USER_TYPE = 0;
    /**
     * <p>Object type for chatbots.</p>
     */
    public static final int CHATBOT_TYPE = 1;
    /**
     * <p>The default domain id - the messenger domain.</p>
     */
    public static final long DEFAULT_DOMAIN = 1;

    private static final String GET_USERID = "SELECT objectID FROM jiveUserID WHERE username=? AND domainID=? AND objectType=?";
    private static final String GET_USERNAME = "SELECT username FROM jiveUserID WHERE objectID=? AND domainID=? AND objectType=?";

    public String getUsername(long id) throws UserNotFoundException {
        String name = null;
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GET_USERNAME);
            pstmt.setLong(1, id);
            pstmt.setLong(2, DEFAULT_DOMAIN);
            pstmt.setLong(3, USER_TYPE);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                name = rs.getString(1);
            }
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        finally {
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
            try {
                if (con != null) {
                    con.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
        if (name == null) {
            throw new UserNotFoundException();
        }
        return name;
    }

    public long getUserID(String username) throws UserNotFoundException {
        long id = -1;
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GET_USERID);
            pstmt.setString(1, username);
            pstmt.setLong(2, DEFAULT_DOMAIN);
            pstmt.setLong(3, USER_TYPE);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                id = rs.getLong(1);
            }
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        finally {
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
            try {
                if (con != null) {
                    con.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
        if (id == -1) {
            throw new UserNotFoundException();
        }
        return id;
    }

    private static final String USER_COUNT = "SELECT count(*) FROM jiveUser";

    private static final String ALL_USERS =
            "SELECT userID from jiveUser";

    public int getUserCount() {
        int count = 0;
        Connection con = null;
        PreparedStatement pstmt = null;

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(USER_COUNT);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                count = rs.getInt(1);
            }
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
            try {
                if (con != null) {
                    con.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
        return count;
    }

    public LongList getUserIDs() {
        LongList users = new LongList(500);
        Connection con = null;
        PreparedStatement pstmt = null;

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ALL_USERS);
            ResultSet rs = pstmt.executeQuery();
            // Set the fetch size. This will prevent some JDBC drivers from trying
            // to load the entire result set into memory.
            DbConnectionManager.setFetchSize(rs, 500);
            while (rs.next()) {
                users.add(rs.getLong(1));
            }
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
            try {
                if (con != null) {
                    con.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
        return users;
    }

    public LongList getUserIDs(int startIndex, int numResults) {
        LongList users = new LongList();
        Connection con = null;
        PreparedStatement pstmt = null;

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ALL_USERS);
            ResultSet rs = pstmt.executeQuery();
            DbConnectionManager.setFetchSize(rs, startIndex + numResults);
            // Move to start of index
            for (int i = 0; i < startIndex; i++) {
                rs.next();
            }
            // Now read in desired number of results (or stop if we run out of results).
            for (int i = 0; i < numResults; i++) {
                if (rs.next()) {
                    users.add(rs.getLong(1));
                }
                else {
                    break;
                }
            }
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
            try {
                if (con != null) {
                    con.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
        return users;
    }
}
