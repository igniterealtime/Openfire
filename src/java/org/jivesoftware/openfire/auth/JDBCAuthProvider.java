/**
 * $Revision: 1116 $
 * $Date: 2005-03-10 20:18:08 -0300 (Thu, 10 Mar 2005) $
 *
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

package org.jivesoftware.openfire.auth;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The JDBC auth provider allows you to authenticate users against any database
 * that you can connect to with JDBC. It can be used along with the
 * {@link HybridAuthProvider hybrid} auth provider, so that you can also have
 * XMPP-only users that won't pollute your external data.<p>
 *
 * To enable this provider, set the following in the system properties:
 * <ul>
 * <li><tt>provider.auth.className = org.jivesoftware.openfire.auth.JDBCAuthProvider</tt></li>
 * </ul>
 *
 * You'll also need to set your JDBC driver, connection string, and SQL statements:
 *
 * <ul>
 * <li><tt>jdbcProvider.driver = com.mysql.jdbc.Driver</tt></li>
 * <li><tt>jdbcProvider.connectionString = jdbc:mysql://localhost/dbname?user=username&amp;password=secret</tt></li>
 * <li><tt>jdbcAuthProvider.passwordSQL = SELECT password FROM user_account WHERE username=?</tt></li>
 * <li><tt>jdbcAuthProvider.passwordType = plain</tt></li>
 * <li><tt>jdbcAuthProvider.allowUpdate = true</tt></li>
 * <li><tt>jdbcAuthProvider.setPasswordSQL = UPDATE user_account SET password=? WHERE username=?</tt></li>
 * </ul>
 *
 * In order to use the configured JDBC connection provider do not use a JDBC
 * connection string, set the following property
 *
 * <ul>
 * <li><tt>jdbcAuthProvider.useConnectionProvider = true</tt></li>
 * </ul>
 *
 * The passwordType setting tells Openfire how the password is stored. Setting the value
 * is optional (when not set, it defaults to "plain"). The valid values are:<ul>
 *      <li>{@link PasswordType#plain plain}
 *      <li>{@link PasswordType#md5 md5}
 *      <li>{@link PasswordType#sha1 sha1}
 *      <li>{@link PasswordType#sha256 sha256}
 *      <li>{@link PasswordType#sha512 sha512}
 *  </ul>
 *
 * @author David Snopek
 */
public class JDBCAuthProvider implements AuthProvider {

	private static final Logger Log = LoggerFactory.getLogger(JDBCAuthProvider.class);

    private String connectionString;

    private String passwordSQL;
    private String setPasswordSQL;
    private PasswordType passwordType;
    private boolean allowUpdate;
    private boolean useConnectionProvider;

    /**
     * Constructs a new JDBC authentication provider.
     */
    public JDBCAuthProvider() {
        // Convert XML based provider setup to Database based
        JiveGlobals.migrateProperty("jdbcProvider.driver");
        JiveGlobals.migrateProperty("jdbcProvider.connectionString");
        JiveGlobals.migrateProperty("jdbcAuthProvider.passwordSQL");
        JiveGlobals.migrateProperty("jdbcAuthProvider.passwordType");
        JiveGlobals.migrateProperty("jdbcAuthProvider.setPasswordSQL");
        JiveGlobals.migrateProperty("jdbcAuthProvider.allowUpdate");

        useConnectionProvider = JiveGlobals.getBooleanProperty("jdbcAuthProvider.useConnectionProvider");

        if (!useConnectionProvider) {
            // Load the JDBC driver and connection string.
            String jdbcDriver = JiveGlobals.getProperty("jdbcProvider.driver");
            try {
               Class.forName(jdbcDriver).newInstance();
            }
            catch (Exception e) {
                Log.error("Unable to load JDBC driver: " + jdbcDriver, e);
                return;
            }
            connectionString = JiveGlobals.getProperty("jdbcProvider.connectionString");
        }

        // Load SQL statements.
        passwordSQL = JiveGlobals.getProperty("jdbcAuthProvider.passwordSQL");
        setPasswordSQL = JiveGlobals.getProperty("jdbcAuthProvider.setPasswordSQL");

        allowUpdate = JiveGlobals.getBooleanProperty("jdbcAuthProvider.allowUpdate",false);

        passwordType = PasswordType.plain;
        try {
            passwordType = PasswordType.valueOf(
                    JiveGlobals.getProperty("jdbcAuthProvider.passwordType", "plain"));
        }
        catch (IllegalArgumentException iae) {
            Log.error(iae.getMessage(), iae);
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
            if (domain.equals(XMPPServer.getInstance().getServerInfo().getXMPPDomain())) {
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
        else if (passwordType == PasswordType.sha256) {
            password = StringUtils.hash(password, "SHA-256");
        }
        else if (passwordType == PasswordType.sha512) {
            password = StringUtils.hash(password, "SHA-512");
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
            if (domain.equals(XMPPServer.getInstance().getServerInfo().getXMPPDomain())) {
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
            if (domain.equals(XMPPServer.getInstance().getServerInfo().getXMPPDomain())) {
                username = username.substring(0, index);
            } else {
                // Unknown domain.
                throw new UserNotFoundException();
            }
        }
        return getPasswordValue(username);
    }

    public void setPassword(String username, String password)
            throws UserNotFoundException, UnsupportedOperationException
    {
        if (allowUpdate && setPasswordSQL != null) {
            setPasswordValue(username, password);
        } else { 
            throw new UnsupportedOperationException();
        }
    }

    public boolean supportsPasswordRetrieval() {
        return (passwordSQL != null && passwordType == PasswordType.plain);
    }

    private Connection getConnection() throws SQLException {
        if (useConnectionProvider)
            return DbConnectionManager.getConnection();
        return DriverManager.getConnection(connectionString);
    }

    /**
     * Returns the value of the password field. It will be in plain text or hashed
     * format, depending on the password type.
     *
     * @param username user to retrieve the password field for
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
            if (domain.equals(XMPPServer.getInstance().getServerInfo().getXMPPDomain())) {
                username = username.substring(0, index);
            } else {
                // Unknown domain.
                throw new UserNotFoundException();
            }
        }
        try {
            con = getConnection();
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

    private void setPasswordValue(String username, String password) throws UserNotFoundException {
        Connection con = null;
        PreparedStatement pstmt = null;
        if (username.contains("@")) {
            // Check that the specified domain matches the server's domain
            int index = username.indexOf("@");
            String domain = username.substring(index + 1);
            if (domain.equals(XMPPServer.getInstance().getServerInfo().getXMPPDomain())) {
                username = username.substring(0, index);
            } else {
                // Unknown domain.
                throw new UserNotFoundException();
            }
        }
        try {
            con = getConnection();
            pstmt = con.prepareStatement(setPasswordSQL);
            pstmt.setString(2, username);
            if (passwordType == PasswordType.md5) {
                password = StringUtils.hash(password, "MD5");
            }
            else if (passwordType == PasswordType.sha1) {
                password = StringUtils.hash(password, "SHA-1");
            }
            else if (passwordType == PasswordType.sha256) {
                password = StringUtils.hash(password, "SHA-256");
            }
            else if (passwordType == PasswordType.sha512) {
                password = StringUtils.hash(password, "SHA-512");
            }
            pstmt.setString(1, password);
            pstmt.executeQuery();
        }
        catch (SQLException e) {
            Log.error("Exception in JDBCAuthProvider", e);
            throw new UserNotFoundException();
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        
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
        sha1,
        
        /**
         * The password is stored as a hex-encoded SHA-256 hash.
         */
        sha256,
              
        /**
          * The password is stored as a hex-encoded SHA-512 hash.
          */
        sha512;
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
                Log.debug("JDBCAuthProvider: Automatically creating new user account for " + username);
                UserManager.getUserProvider().createUser(username, StringUtils.randomString(8),
                        null, null);
            }
            catch (UserAlreadyExistsException uaee) {
                // Ignore.
            }
        }
    }
}
