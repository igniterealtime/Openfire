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
import org.jivesoftware.database.SequenceManager;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.UserAccountProvider;
import org.jivesoftware.messenger.user.UserAlreadyExistsException;
import org.jivesoftware.database.DbConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Date;

public class DbUserAccountProvider implements UserAccountProvider {

    private static final String INSERT_USERID = "INSERT INTO jiveUserID (username,domainID,objectType,objectID) VALUES (?,?,?,?)";
    private static final String INSERT_USER =
            "INSERT INTO jiveUser (userID, password, name, nameVisible," +
            "email, emailVisible, creationDate, modificationDate) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    public long createUser(String username, String password, String email)
            throws UnauthorizedException, UserAlreadyExistsException {
        if (email == null || email.length() == 0) {
            email = " ";
        }
        long id = -1;
        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;
        try {

            // Reserve the name in the jiveUserID
            id = SequenceManager.nextID(JiveConstants.USER);
            con = DbConnectionManager.getTransactionConnection();
            pstmt = con.prepareStatement(INSERT_USERID);
            pstmt.setString(1, username);
            pstmt.setLong(2, DbUserIDProvider.DEFAULT_DOMAIN);
            pstmt.setLong(3, DbUserIDProvider.USER_TYPE);
            pstmt.setLong(4, id);
            pstmt.execute();
            // Add the user record in jiveUser
            pstmt = con.prepareStatement(INSERT_USER);
            pstmt.setLong(1, id);
            pstmt.setString(2, password);
            pstmt.setString(3, "");
            pstmt.setInt(4, 1); // name visible
            pstmt.setString(5, email);
            pstmt.setInt(6, 0); // email visible
            Date now = new Date();
            pstmt.setString(7, StringUtils.dateToMillis(now));
            pstmt.setString(8, StringUtils.dateToMillis(now));
            pstmt.execute();
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            abortTransaction = true;
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
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
        }
        if (id == -1) {
            throw new UnauthorizedException("Failed to create user, please " +
                    "check Jive error logs for details");
        }
        return id;
    }

    private static final String DELETE_USER_ID =
            "DELETE FROM jiveUserID WHERE objectID=? AND objectType=? and domainID=?";
    private static final String DELETE_USER_GROUPS =
            "DELETE FROM jiveGroupUser WHERE userID=?";
    private static final String DELETE_USER_PROPS =
            "DELETE FROM jiveUserProp WHERE userID=?";
    private static final String DELETE_VCARD_PROPS =
            "DELETE FROM jiveVCard WHERE userID=?";
    private static final String DELETE_USER =
            "DELETE FROM jiveUser WHERE userID=?";

    public void deleteUser(long userID) throws UnauthorizedException {

        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;

        try {
            con = DbConnectionManager.getTransactionConnection();
            // Remove user from all groups
            pstmt = con.prepareStatement(DELETE_USER_GROUPS);
            pstmt.setLong(1, userID);
            pstmt.execute();
            // Delete all of the users's extended properties
            pstmt = con.prepareStatement(DELETE_USER_PROPS);
            pstmt.setLong(1, userID);
            pstmt.execute();
            // Delete all of the users's vcard properties
            pstmt = con.prepareStatement(DELETE_VCARD_PROPS);
            pstmt.setLong(1, userID);
            pstmt.execute();
            // Delete the actual user entry
            pstmt = con.prepareStatement(DELETE_USER);
            pstmt.setLong(1, userID);
            pstmt.execute();
            // Delete the actual user ID entry
            pstmt = con.prepareStatement(DELETE_USER_ID);
            pstmt.setLong(1, userID);
            pstmt.setInt(2, DbUserIDProvider.USER_TYPE);
            pstmt.setLong(3, DbUserIDProvider.DEFAULT_DOMAIN);
            pstmt.execute();
        }
        catch (Exception e) {
            Log.error(e);
            abortTransaction = true;
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
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
        }
    }
}
