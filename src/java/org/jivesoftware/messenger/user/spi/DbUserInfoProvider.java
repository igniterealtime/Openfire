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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.User;
import org.jivesoftware.messenger.user.UserInfo;
import org.jivesoftware.messenger.user.UserInfoProvider;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;

/**
 * Catabase implementation of the UserInfoProvider interface.
 *
 * @author Matt Tucker
 * @author Bruce Ritchie
 * @author Iain Shigeoka
 *
 * @see User
 */
public class DbUserInfoProvider implements UserInfoProvider {

    private static final String LOAD_USER_BY_USERNAME =
        "SELECT name, email, creationDate, modificationDate FROM jiveUser WHERE username=?";

    public UserInfo getInfo(String username) throws UserNotFoundException {
        UserInfo userInfo = null;
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_USER_BY_USERNAME);
            pstmt.setString(1, username);

            ResultSet rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new UserNotFoundException();
            }
            // We trim() the dates before trying to parse them because some
            // databases pad with extra characters when returning the data.
            userInfo = new UserInfo(username,
                    rs.getString(1), // name
                    rs.getString(2), // email
                    new java.util.Date(Long.parseLong(rs.getString(3).trim())), // creation date
                    new java.util.Date(Long.parseLong(rs.getString(4).trim()))); // modification date

        }
        catch (SQLException e) {
            throw new UserNotFoundException("Failed to read user " + username + " from database.", e);
        }
        catch (NumberFormatException nfe) {
            Log.error("WARNING: There was an error parsing the dates " +
                    "returned from the database. Ensure that they're being stored " +
                    "correctly.");
            throw new UserNotFoundException("User "
                    + username + " could not be loaded from the database.");
        }
        finally {
            try { if (pstmt != null) { pstmt.close(); } }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) { con.close(); } }
            catch (Exception e) { Log.error(e); }
        }
        return userInfo;
    }

    private static final String SAVE_USER =
            "UPDATE jiveUser SET name=?,email=?,creationDate=?,modificationDate=? " +
            "WHERE username=?";

    public void setInfo(String username, UserInfo info) throws UserNotFoundException, UnauthorizedException {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SAVE_USER);
            pstmt.setString(1, info.getName());
            pstmt.setString(2, info.getEmail());
            pstmt.setString(3, StringUtils.dateToMillis(info.getCreationDate()));
            pstmt.setString(4, StringUtils.dateToMillis(info.getModificationDate()));
            pstmt.setString(5, username);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            throw new UnauthorizedException();
        }
        finally {
            try { if (pstmt != null) { pstmt.close(); } }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) { con.close(); } }
            catch (Exception e) { Log.error(e); }
        }
    }
}
