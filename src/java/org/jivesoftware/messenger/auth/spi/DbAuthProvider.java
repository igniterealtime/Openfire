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

package org.jivesoftware.messenger.auth.spi;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.NodePrep;
import org.jivesoftware.messenger.auth.AuthFactory;
import org.jivesoftware.messenger.auth.AuthProvider;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.jivesoftware.messenger.user.spi.DbUserIDProvider;
import org.jivesoftware.database.DbConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Implements the default Jive authenticator implementation. It makes an
 * SQL query to the Jive user table to see if the supplied username and password
 * match a user record. If they do, the appropriate user ID is returned.
 * If no matching User record is found an UnauthorizedException is
 * thrown.<p>
 *
 * Because each call to authenticate() makes a database
 * connection, the results of authentication should be cached whenever possible. When
 * using a servlet or JSP skins, a good method is to cache a token in the
 * session named AuthFactory.SESSION_AUTHORIZATION. The default
 * AuthFactory.createAuthorization(HttpServletRequest request,
 * HttpServletResponse response) method automatically handles this logic.<p>
 *
 * If you wish to integrate Jive Forums with your own authentication or
 * single sign-on system, you'll need  your own implementation of the
 * AuthProvider interface. See that interface for further details.<p>
 *
 * This implementation relies on the DbUserIDProvider to map usernames to IDs in
 * order to lookup the password in the jiveUser table. This relationship is not required
 * (authentication providers may be entirely independent of user management.
 *
 * @author Matt Tucker
 * @author Iain Shigeoka
 */
public class DbAuthProvider implements AuthProvider {

    /**
     * DATABASE QUERIES *
     */
    private static final String AUTHORIZE =
            "SELECT userID FROM jiveUser WHERE userID=? AND password=?";

    private static final String SELECT_PASSWORD =
            "SELECT password FROM jiveUser WHERE userID=?";
    private static final String UPDATE_PASSWORD =
            "UPDATE jiveUser set password=? WHERE userID=?";

    /**
     * <p>Used to map usernames to ids.</p>
     */
    private DbUserIDProvider idProvider = new DbUserIDProvider();

    /**
     * <p>Implementation requires the password to be stored in the user table in plain text.</p>
     * <p>You can store the password in the database in another format and modify this method
     * to check the given plain-text password (e.g. if the passwords are stored as MD5 hashes,
     * MD5 hash the password and compare that with the database).</p>
     *
     * @param username the username to create an AuthToken with.
     * @param password the password to create an AuthToken with.
     * @throws UnauthorizedException if the username and password do not match any existing user.
     */
    public void authenticate(String username, String password) throws UnauthorizedException {
        if (username == null || password == null) {
            throw new UnauthorizedException();
        }
        username = NodePrep.prep(username);
        long userID = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            userID = idProvider.getUserID(username);
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(AUTHORIZE);
            pstmt.setLong(1, userID);
            pstmt.setString(2, password);

            ResultSet rs = pstmt.executeQuery();

            // If the query had no results, the username and password
            // did not match a user record. Therefore, throw an exception.
            if (!rs.next()) {
                throw new UnauthorizedException();
            }
        }
        catch (SQLException e) {
            Log.error("Exception in DbAuthProvider", e);
            throw new UnauthorizedException();
        }
        catch (UserNotFoundException e) {
            throw new UnauthorizedException();
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
        // Got this far, so the user must be authorized.
    }

    /**
     * Implementation requires the password to be stored in the user table in plain text.<p>
     *
     * There is no way to support digest authentication without the plain text password in the
     * database. If you can't support plain-text passwords in the backend, then always throw
     * UnauthorizedException immediately and be sure to return false in isDigestSupported().
     *
     * @param username The username of the user to check
     * @param token    The unique token (XMPP stream id) used to generate the digest
     * @param digest   The digest to be checked
     * @throws UnauthorizedException If the login attempt failed
     */
    public void authenticate(String username, String token, String digest) throws UnauthorizedException {
        if (username == null || token == null || digest == null) {
            throw new UnauthorizedException();
        }
        username = NodePrep.prep(username);
        long userID = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            userID = idProvider.getUserID(username);
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SELECT_PASSWORD);
            pstmt.setLong(1, userID);

            ResultSet rs = pstmt.executeQuery();

            // If the query had no results, the username and password
            // did not match a user record. Therefore, throw an exception.
            if (!rs.next()) {
                throw new UnauthorizedException();
            }
            String pass = rs.getString(1);
            String anticipatedDigest = AuthFactory.createTokenPasswordDigest(token, pass);
            if (!digest.equalsIgnoreCase(anticipatedDigest)) {
                throw new UnauthorizedException();
            }
        }
        catch (SQLException e) {
            Log.error("Exception in DbAuthProvider", e);
            throw new UnauthorizedException();
        }
        catch (UserNotFoundException e) {
            throw new UnauthorizedException();
        }
        finally {
            try {
                if (pstmt != null) pstmt.close();
            }
            catch (Exception e) {
                Log.error(e);
            }
            try {
                if (con != null) con.close();
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
        // Got this far, so the user must be authorized.
    }

    /**
     * Update the password for the given user. If you don't want people to change their
     * password through Messenger just throw UnauthorizedException.
     *
     * @param username The username of the user who's password is changing
     * @param password The new password for the user
     * @throws UnauthorizedException If the password is invalid or the provider doesn't allow password updates
     * @throws UserNotFoundException If the given user could not be located
     */
    public void updatePassword(String username, String password) throws UserNotFoundException, UnauthorizedException {
        if (username == null || password == null) {
            throw new UnauthorizedException();
        }
        username = NodePrep.prep(username);
        long userID = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            userID = idProvider.getUserID(username);
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_PASSWORD);
            pstmt.setString(1, password);
            pstmt.setLong(2, userID);

            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error("Exception in DbAuthProvider", e);
            throw new UnauthorizedException();
        }
        finally {
            try {
                if (pstmt != null) pstmt.close();
            }
            catch (Exception e) {
                Log.error(e);
            }
            try {
                if (con != null) con.close();
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
    }

    public boolean isPlainSupported() {
        return true;
    }

    public boolean isDigestSupported() {
        return true;
    }
}