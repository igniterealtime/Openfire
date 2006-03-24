/**
 * $Revision: 1116 $
 * $Date: 2005-03-10 20:18:08 -0300 (Thu, 10 Mar 2005) $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.auth;

import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.wildfire.auth.*;
import org.jivesoftware.wildfire.user.*;

import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * The JDBC auth provider allows you to authenticate users against any database
 * that you can connect to with JDBC. It can be used along with the
 * {@link HybridAuthProvider hybrid} auth provider, so that you can also have
 * XMPP-only users that won't pollute your external data.<p>
 * <p/>
 * To enable this provider, set the following in the XML configuration file:
 * <p/>
 * <pre>
 * &lt;provider&gt;
 *     &lt;auth&gt;
 *         &lt;className&gt;org.jivesoftware.wildfire.auth.JDBCAuthProvider&lt;/className&gt;
 *     &lt;/auth&gt;
 * &lt;/provider&gt;
 * </pre>
 * <p/>
 * You'll also need to set your JDBC driver, connection string, and SQL statements:
 * <p/>
 * <pre>
 * &lt;jdbcAuthProvider&gt;
 *      &lt;jdbcDriver&gt;
 *          &lt;className&gt;com.mysql.jdbc.Driver&lt;/className&gt;
 *      &lt;/jdbcDrivec&gt;
 *      &lt;jdbcConnString&gt;jdbc:mysql:://localhost/dbname?user=username&amp;amp;password=secret&lt;/jdbcConnString&gt;
 *      &lt;authorizeSQL&gt;SELECT username FROM user_account WHERE username=? AND password=?&lt;/authorizeSQL&gt;
 *      &lt;passwordSQL&gt;SELECT password FROM user_account WHERE username=?&lt;passwordSQL&gt;
 * &lt;/jdbcAuthProvider&gt;
 * </pre>
 *
 * @author David Snopek
 */
public class JDBCAuthProvider implements AuthProvider {

    private String jdbcConnString;
    private String authSQL;
    private String passSQL;

    /**
     * Constructs a new JDBC authentication provider.
     */
    public JDBCAuthProvider() {
        // Load the JDBC driver
        String jdbcDriver = JiveGlobals.getXMLProperty("jdbcAuthProvider.jdbcDriver.className");
        try {
            Class.forName(jdbcDriver).newInstance();
        }
        catch (Exception e) {
            Log.error("Unable to load JDBC driver: " + jdbcDriver, e);
            return;
        }

        // Grab connection string and SQL statements
        jdbcConnString = JiveGlobals.getXMLProperty("jdbcAuthProvider.jdbcConnString");
        authSQL = JiveGlobals.getXMLProperty("jdbcAuthProvider.authorizeSQL");
        passSQL = JiveGlobals.getXMLProperty("jdbcAuthProvider.passwordSQL");
    }

    public void authenticate(String username, String password) throws UnauthorizedException {
        if (username == null || password == null) {
            throw new UnauthorizedException();
        }
        username = username.trim().toLowerCase();
        Connection conn = null;
        PreparedStatement pstmt = null;
        try {
            conn = DriverManager.getConnection(jdbcConnString);
            pstmt = conn.prepareStatement(authSQL);
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            ResultSet rs = pstmt.executeQuery();
            // If the query has no results, the username and password
            // did not match a user record. Therefore, throw an exception.
            if (!rs.next()) {
                throw new UnauthorizedException();
            }
            rs.close();
        }
        catch (SQLException e) {
            Log.error("Exception in JDBCAuthProvider", e);
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
                if (conn != null) {
                    conn.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
        }

        // Got this far, so the user must be authorized.
        createUser(username);
    }

    public void authenticate(String username, String token, String digest)
            throws UnauthorizedException {
        if (username == null || token == null || digest == null) {
            throw new UnauthorizedException();
        }
        username = username.trim().toLowerCase();
        Connection conn = null;
        PreparedStatement pstmt = null;
        try {
            conn = DriverManager.getConnection(jdbcConnString);
            pstmt = conn.prepareStatement(passSQL);
            pstmt.setString(1, username);

            ResultSet rs = pstmt.executeQuery();

            // If the query had no results, the username and password
            // did not match a user record. Therefore, throw an exception.
            if (!rs.next()) {
                throw new UnauthorizedException();
            }
            String pass = rs.getString(1);
            String anticipatedDigest = AuthFactory.createDigest(token, pass);
            if (!digest.equalsIgnoreCase(anticipatedDigest)) {
                throw new UnauthorizedException();
            }
            rs.close();
        }
        catch (SQLException e) {
            Log.error("Exception in JDBCAuthProvider", e);
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
                if (conn != null) {
                    conn.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
        }

        // Got this far, so the user must be authorized.
        createUser(username);
    }

    public boolean isPlainSupported() {
        return true;
    }

    public boolean isDigestSupported() {
        return true;
    }

    private static void createUser(String username) {
        // See if the user exists in the database. If not, automatically create them.
        UserManager userManager = UserManager.getInstance();
        try {
            userManager.getUser(username);
        }
        catch (UserNotFoundException unfe) {
            try {
                Log.debug("Automatically creating new user account for " + username);
                UserProvider provider = UserManager.getUserProvider();
                UserManager.getUserProvider().createUser(username, StringUtils.randomString(8),
                        null, null);
            }
            catch (UserAlreadyExistsException uaee) {
                // Ignore.
            }
        }
    }
}
