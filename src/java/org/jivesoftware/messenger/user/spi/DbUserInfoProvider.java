/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.user.spi;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.User;
import org.jivesoftware.messenger.user.UserInfo;
import org.jivesoftware.messenger.user.UserInfoProvider;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.jivesoftware.database.DbConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * <p>Database implementation of the UserInfoProvider interface.</p>
 *
 * @author Matt Tucker
 * @author Bruce Ritchie
 * @author Iain Shigeoka
 *
 * @see User
 */
public class DbUserInfoProvider implements UserInfoProvider {

    private static final String LOAD_USER_BY_ID =
            "SELECT name, nameVisible, email, emailVisible, " +
            "creationDate, modificationDate FROM jiveUser WHERE userID=?";

    public UserInfo getInfo(long id) throws UserNotFoundException {
        BasicUserInfo userInfo = null;
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_USER_BY_ID);
            pstmt.setLong(1, id);

            ResultSet rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new UserNotFoundException();
            }
            // We trim() the dates before trying to parse them because some
            // databases pad with extra characters when returning the data.
            userInfo = new BasicUserInfo(id,
                    rs.getString(1), // name
                    rs.getString(3), // email
                    rs.getInt(2) == 1, // name visible
                    rs.getInt(4) == 1, // email visible
                    new java.util.Date(Long.parseLong(rs.getString(5).trim())), // creation date
                    new java.util.Date(Long.parseLong(rs.getString(6).trim()))); // modification date

        }
        catch (SQLException e) {
            throw new UserNotFoundException("Failed to read user " + id + " from database.", e);
        }
        catch (NumberFormatException nfe) {
            Log.error("WARNING: There was an error parsing the dates " +
                    "returned from the database. Ensure that they're being stored " +
                    "correctly.");
            throw new UserNotFoundException("User with id "
                    + id + " could not be loaded from the database.");
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
        return userInfo;
    }

    private static final String SAVE_USER =
            "UPDATE jiveUser SET name=?, nameVisible=?, email=?," +
            "emailVisible=?, creationDate=?, modificationDate=? WHERE " +
            "userID=?";

    public void setInfo(long id, UserInfo info) throws UserNotFoundException, UnauthorizedException {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SAVE_USER);
            pstmt.setString(1, info.getName());
            pstmt.setInt(2, info.isNameVisible() ? 1 : 0);
            pstmt.setString(3, info.getEmail());
            pstmt.setInt(4, info.isEmailVisible() ? 1 : 0);
            pstmt.setString(5, StringUtils.dateToMillis(info.getCreationDate()));
            pstmt.setString(6, StringUtils.dateToMillis(info.getModificationDate()));
            pstmt.setLong(7, id);
            pstmt.execute();
        }
        catch (SQLException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            throw new UnauthorizedException();
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
    }
}
