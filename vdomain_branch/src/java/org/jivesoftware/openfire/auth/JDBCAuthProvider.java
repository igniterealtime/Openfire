/**
 * $Revision: 1116 $
 * $Date: 2005-03-10 20:18:08 -0300 (Thu, 10 Mar 2005) $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.auth;

import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.openfire.user.*;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.database.DbConnectionManager;

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
 *
 * To enable this provider, set the following in the XML configuration file:
 *
 * <pre>
 * &lt;provider&gt;
 *     &lt;auth&gt;
 *         &lt;className&gt;org.jivesoftware.openfire.auth.JDBCAuthProvider&lt;/className&gt;
 *     &lt;/auth&gt;
 * &lt;/provider&gt;
 * </pre>
 *
 * You'll also need to set your JDBC driver, connection string, and SQL statements:
 *
 * <pre>
 * &lt;jdbcProvider&gt;
 *     &lt;driver&gt;com.mysql.jdbc.Driver&lt;/driver&gt;
 *     &lt;connectionString&gt;jdbc:mysql://localhost/dbname?user=username&amp;password=secret&lt;/connectionString&gt;
 * &lt;/jdbcProvider&gt;
 *
 * &lt;jdbcAuthProvider&gt;
 *      &lt;passwordSQL&gt;SELECT password FROM user_account WHERE username=?&lt;/passwordSQL&gt;
 *      &lt;passwordType&gt;plain&lt;/passwordType&gt;
 * &lt;/jdbcAuthProvider&gt;</pre>
 *
 * The passwordType setting tells Openfire how the password is stored. Setting the value
 * is optional (when not set, it defaults to "plain"). The valid values are:<ul>
 *      <li>{@link PasswordType#plain plain}
 *      <li>{@link PasswordType#md5 md5}
 *      <li>{@link PasswordType#sha1 sha1}
 *  </ul>
 *
 * @author David Snopek
 */
public class JDBCAuthProvider implements AuthProvider {

    private String connectionString;

    private String passwordSQL;
    private PasswordType passwordType;

    /**
     * Constructs a new JDBC authentication provider.
     */
    public JDBCAuthProvider() {
        // Load the JDBC driver and connection string.
        String jdbcDriver = JiveGlobals.getXMLProperty("jdbcProvider.driver");
        try {
            Class.forName(jdbcDriver).newInstance();
        }
        catch (Exception e) {
            Log.error("Unable to load JDBC driver: " + jdbcDriver, e);
            return;
        }
        connectionString = JiveGlobals.getXMLProperty("jdbcProvider.connectionString");

        // Load SQL statements.
        passwordSQL = JiveGlobals.getXMLProperty("jdbcAuthProvider.passwordSQL");
        passwordType = PasswordType.plain;
        try {
            passwordType = PasswordType.valueOf(
                    JiveGlobals.getXMLProperty("jdbcAuthProvider.passwordType", "plain"));
        }
        catch (IllegalArgumentException iae) {
            Log.error(iae);
        }
    }

    public void authenticate(String username, String password) throws UnauthorizedException {
        if (username == null || password == null) {
            throw new UnauthorizedException();
        }
        username = username.trim().toLowerCase();
        if (username.contains("@")) {
            // Check that the specified domain matches the server's domain
            int index = username.indexOf("@");
            String domain = username.substring(index + 1);
            if (domain.equals(XMPPServer.getInstance().getServerInfo().getName())) {
                username = username.substring(0, index);
            } else {
                // Unknown domain. Return authentication failed.
                throw new UnauthorizedException();
            }
        }
        String userPassword;
        try {
            userPassword = getPasswordValue(username);
        }
        catch (UserNotFoundException unfe) {
            throw new UnauthorizedException();
        }
        // If the user's password doesn't match the password passed in, authentication
        // should fail.
        if (passwordType == PasswordType.md5) {
            password = StringUtils.hash(password, "MD5");
        }
        else if (passwordType == PasswordType.sha1) {
            password = StringUtils.hash(password, "SHA-1");
        }
        if (!password.equals(userPassword)) {
            throw new UnauthorizedException();
        }

        // Got this far, so the user must be authorized.
        createUser(username);
    }

    public void authenticate(String username, String token, String digest)
            throws UnauthorizedException
    {
        if (passwordType != PasswordType.plain) {
            throw new UnsupportedOperationException("Digest authentication not supported for "
                    + "password type " + passwordType);
        }
        if (username == null || token == null || digest == null) {
            throw new UnauthorizedException();
        }
        username = username.trim().toLowerCase();
        if (username.contains("@")) {
            // Check that the specified domain matches the server's domain
            int index = username.indexOf("@");
            String domain = username.substring(index + 1);
            if (domain.equals(XMPPServer.getInstance().getServerInfo().getName())) {
                username = username.substring(0, index);
            } else {
                // Unknown domain. Return authentication failed.
                throw new UnauthorizedException();
            }
        }
        String password;
        try {
            password = getPasswordValue(username);
        }
        catch (UserNotFoundException unfe) {
            throw new UnauthorizedException();
        }
        String anticipatedDigest = AuthFactory.createDigest(token, password);
        if (!digest.equalsIgnoreCase(anticipatedDigest)) {
            throw new UnauthorizedException();
        }

        // Got this far, so the user must be authorized.
        createUser(username);
    }

    public boolean isPlainSupported() {
        // If the auth SQL is defined, plain text authentication is supported.
        return (passwordSQL != null);
    }

    public boolean isDigestSupported() {
        // The auth SQL must be defined and the password type is supported.
        return (passwordSQL != null && passwordType == PasswordType.plain);
    }

    public String getPassword(String username) throws UserNotFoundException,
            UnsupportedOperationException
    {

        if (!supportsPasswordRetrieval()) {
            throw new UnsupportedOperationException();
        }
        if (username.contains("@")) {
            // Check that the specified domain matches the server's domain
            int index = username.indexOf("@");
            String domain = username.substring(index + 1);
            if (domain.equals(XMPPServer.getInstance().getServerInfo().getName())) {
                username = username.substring(0, index);
            }
        } else {
            // Unknown domain. Return authentication failed.
            throw new UserNotFoundException();
        }
        return getPasswordValue(username);
    }

    public void setPassword(String username, String password)
            throws UserNotFoundException, UnsupportedOperationException
    {
        throw new UnsupportedOperationException();
    }

    public boolean supportsPasswordRetrieval() {
        return (passwordSQL != null && passwordType == PasswordType.plain);
    }

    /**
     * Returns the value of the password field. It will be in plain text or hashed
     * format, depending on the password type.
     *
     * @return the password value.
     * @throws UserNotFoundException if the given user could not be loaded.
     */
    private String getPasswordValue(String username) throws UserNotFoundException {
        String password = null;
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        if (username.contains("@")) {
            // Check that the specified domain matches the server's domain
            int index = username.indexOf("@");
            String domain = username.substring(index + 1);
            if (domain.equals(XMPPServer.getInstance().getServerInfo().getName())) {
                username = username.substring(0, index);
            } else {
                // Unknown domain.
                throw new UserNotFoundException();
            }
        }
        try {
            con = DriverManager.getConnection(connectionString);
            pstmt = con.prepareStatement(passwordSQL);
            pstmt.setString(1, username);

            rs = pstmt.executeQuery();

            // If the query had no results, the username and password
            // did not match a user record. Therefore, throw an exception.
            if (!rs.next()) {
                throw new UserNotFoundException();
            }
            password = rs.getString(1);
        }
        catch (SQLException e) {
            Log.error("Exception in JDBCAuthProvider", e);
            throw new UserNotFoundException();
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return password;
    }

    /**
     * Indicates how the password is stored.
     */
    @SuppressWarnings({"UnnecessarySemicolon"})  // Support for QDox Parser
    public enum PasswordType {

        /**
         * The password is stored as plain text.
         */
        plain,

        /**
         * The password is stored as a hex-encoded MD5 hash.
         */
        md5,

        /**
         * The password is stored as a hex-encoded SHA-1 hash.
         */
        sha1;
    }

    /**
     * Checks to see if the user exists; if not, a new user is created.
     *
     * @param username the username.
     */
    private static void createUser(String username) {
        // See if the user exists in the database. If not, automatically create them.
        UserManager userManager = UserManager.getInstance();
        try {
            userManager.getUser(username);
        }
        catch (UserNotFoundException unfe) {
            try {
                Log.debug("Automatically creating new user account for " + username);
                UserManager.getUserProvider().createUser(username, StringUtils.randomString(8),
                        null, null);
            }
            catch (UserAlreadyExistsException uaee) {
                // Ignore.
            }
        }
    }
}
