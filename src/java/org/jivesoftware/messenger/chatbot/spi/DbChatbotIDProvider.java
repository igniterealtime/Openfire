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

package org.jivesoftware.messenger.chatbot.spi;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.LongList;
import org.jivesoftware.messenger.chatbot.ChatbotIDProvider;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.jivesoftware.messenger.user.spi.DbUserIDProvider;
import org.jivesoftware.database.DbConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

// TODO: this class is a copy of user id provider except for objecttype. Need to create a base
// class and just parameterize the object type.

public class DbChatbotIDProvider implements ChatbotIDProvider {

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
            pstmt.setLong(2, DbUserIDProvider.DEFAULT_DOMAIN);
            pstmt.setLong(3, DbUserIDProvider.CHATBOT_TYPE);
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

    public long getChatbotID(String username) throws UserNotFoundException {
        long id = -1;
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GET_USERID);
            pstmt.setString(1, username);
            pstmt.setLong(2, DbUserIDProvider.DEFAULT_DOMAIN);
            pstmt.setLong(3, DbUserIDProvider.CHATBOT_TYPE);
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

    private static final String USER_COUNT = "SELECT count(*) FROM jiveUserID WHERE objectType=? AND domainID=?";

    public int getChatbotCount() {
        int count = 0;
        Connection con = null;
        PreparedStatement pstmt = null;

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(USER_COUNT);
            pstmt.setInt(1, DbUserIDProvider.CHATBOT_TYPE);
            pstmt.setLong(2, DbUserIDProvider.DEFAULT_DOMAIN);
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

    private static final String ALL_USERS =
            "SELECT objectID from jiveUserID WHERE objectType=? AND domainID=?";

    public LongList getChatbotIDs() {
        LongList users = new LongList(500);
        Connection con = null;
        PreparedStatement pstmt = null;

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ALL_USERS);
            pstmt.setInt(1, DbUserIDProvider.CHATBOT_TYPE);
            pstmt.setLong(2, DbUserIDProvider.DEFAULT_DOMAIN);
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

    public LongList getChatbotIDs(int startIndex, int numResults) {
        LongList users = new LongList();
        Connection con = null;
        PreparedStatement pstmt = null;

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ALL_USERS);
            pstmt.setInt(1, DbUserIDProvider.CHATBOT_TYPE);
            pstmt.setLong(2, DbUserIDProvider.DEFAULT_DOMAIN);
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
