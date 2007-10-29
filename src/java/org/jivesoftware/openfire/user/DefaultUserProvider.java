/**
 * $RCSfile: DefaultUserProvider.java,v $
 * $Revision: 3116 $
 * $Date: 2005-11-24 06:25:00 -0300 (Thu, 24 Nov 2005) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.user;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;
import org.xmpp.packet.JID;

import java.sql.*;
import java.util.*;
import java.util.Date;

/**
 * Default implementation of the UserProvider interface, which reads and writes data
 * from the <tt>jiveUser</tt> database table.<p>
 *
 * Passwords can be stored as plain text, or encrypted using Blowfish. The
 * encryption/decryption key is stored as the Openfire property <tt>passwordKey</tt>,
 * which is automatically created on first-time use. It's critical that the password key
 * not be changed once created, or existing passwords will be lost. By default
 * passwords will be stored encrypted. Plain-text password storage can be enabled
 * by setting the Openfire property <tt>user.usePlainPassword</tt> to <tt>true</tt>.
 *
 * @author Matt Tucker
 */
public class DefaultUserProvider implements UserProvider {

    private static final String LOAD_USER =
            "SELECT name, email, creationDate, modificationDate FROM jiveUser WHERE username=?";
    private static final String USER_COUNT =
            "SELECT count(*) FROM jiveUser";
    private static final String ALL_USERS =
            "SELECT username FROM jiveUser ORDER BY username";
    private static final String INSERT_USER =
            "INSERT INTO jiveUser (username,plainPassword,encryptedPassword,name,email,creationDate,modificationDate) " +
            "VALUES (?,?,?,?,?,?,?)";
    private static final String DELETE_USER_PROPS =
            "DELETE FROM jiveUserProp WHERE username=?";
    private static final String DELETE_USER =
            "DELETE FROM jiveUser WHERE username=?";
    private static final String UPDATE_NAME =
            "UPDATE jiveUser SET name=? WHERE username=?";
    private static final String UPDATE_EMAIL =
            "UPDATE jiveUser SET email=? WHERE username=?";
    private static final String UPDATE_CREATION_DATE =
            "UPDATE jiveUser SET creationDate=? WHERE username=?";
    private static final String UPDATE_MODIFICATION_DATE =
            "UPDATE jiveUser SET modificationDate=? WHERE username=?";

    public User loadUser(String username) throws UserNotFoundException {
        if(username.contains("@")) {
            if (!XMPPServer.getInstance().isLocal(new JID(username))) {
                throw new UserNotFoundException("Cannot load user of remote server: " + username);
            }
            username = username.substring(0,username.lastIndexOf("@"));
        }
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_USER);
            pstmt.setString(1, username);
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new UserNotFoundException();
            }
            String name = rs.getString(1);
            String email = rs.getString(2);
            Date creationDate = new Date(Long.parseLong(rs.getString(3).trim()));
            Date modificationDate = new Date(Long.parseLong(rs.getString(4).trim()));

            return new User(username, name, email, creationDate, modificationDate);
        }
        catch (Exception e) {
            throw new UserNotFoundException(e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    public User createUser(String username, String password, String name, String email)
            throws UserAlreadyExistsException
    {
        if (isReadOnly()) {
            // Reject the operation since the provider is read-only
            throw new UnsupportedOperationException();
        }
        try {
            loadUser(username);
            // The user already exists since no exception, so:
            throw new UserAlreadyExistsException("Username " + username + " already exists");
        }
        catch (UserNotFoundException unfe) {
            // The user doesn't already exist so we can create a new user

            // Determine if the password should be stored as plain text or encrypted.
            boolean usePlainPassword = JiveGlobals.getBooleanProperty("user.usePlainPassword");
            String encryptedPassword = null;
            if (!usePlainPassword) {
                try {
                    encryptedPassword = AuthFactory.encryptPassword(password);
                    // Set password to null so that it's inserted that way.
                    password = null;
                }
                catch (UnsupportedOperationException uoe) {
                    // Encrypting the password may have failed if in setup mode. Therefore,
                    // use the plain password.
                }
            }

            Date now = new Date();
            Connection con = null;
            PreparedStatement pstmt = null;
            try {
                con = DbConnectionManager.getConnection();
                pstmt = con.prepareStatement(INSERT_USER);
                pstmt.setString(1, username);
                if (password == null) {
                    pstmt.setNull(2, Types.VARCHAR);
                }
                else {
                    pstmt.setString(2, password);
                }
                if (encryptedPassword == null) {
                    pstmt.setNull(3, Types.VARCHAR);
                }
                else {
                    pstmt.setString(3, encryptedPassword);
                }
                if (name == null) {
                    pstmt.setNull(4, Types.VARCHAR);
                }
                else {
                    pstmt.setString(4, name);
                }
                if (email == null) {
                    pstmt.setNull(5, Types.VARCHAR);
                }
                else {
                    pstmt.setString(5, email);
                }
                pstmt.setString(6, StringUtils.dateToMillis(now));
                pstmt.setString(7, StringUtils.dateToMillis(now));
                pstmt.execute();
            }
            catch (Exception e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
            finally {
                DbConnectionManager.closeConnection(pstmt, con);
            }
            return new User(username, name, email, now, now);
        }
    }

    public void deleteUser(String username) {
        if (isReadOnly()) {
            // Reject the operation since the provider is read-only
            throw new UnsupportedOperationException();
        }
        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;
        try {
            // Delete all of the users's extended properties
            con = DbConnectionManager.getTransactionConnection();
            pstmt = con.prepareStatement(DELETE_USER_PROPS);
            pstmt.setString(1, username);
            pstmt.execute();
            pstmt.close();
            // Delete the actual user entry
            pstmt = con.prepareStatement(DELETE_USER);
            pstmt.setString(1, username);
            pstmt.execute();
        }
        catch (Exception e) {
            Log.error(e);
            abortTransaction = true;
        }
        finally {
            DbConnectionManager.closeTransactionConnection(pstmt, con, abortTransaction);
        }
    }

    public int getUserCount() {
        int count = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(USER_COUNT);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                count = rs.getInt(1);
            }
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return count;
    }

    public Collection<User> getUsers() {
        Collection<String> usernames = getUsernames();
        return new UserCollection(usernames.toArray(new String[usernames.size()]));
    }

    public Collection<String> getUsernames() {
        List<String> usernames = new ArrayList<String>(500);
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ALL_USERS);
            rs = pstmt.executeQuery();
            // Set the fetch size. This will prevent some JDBC drivers from trying
            // to load the entire result set into memory.
            DbConnectionManager.setFetchSize(rs, 500);
            while (rs.next()) {
                usernames.add(rs.getString(1));
            }
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return usernames;
    }

    public Collection<User> getUsers(int startIndex, int numResults) {
        List<String> usernames = new ArrayList<String>(numResults);
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = DbConnectionManager.createScrollablePreparedStatement(con, ALL_USERS);
            rs = pstmt.executeQuery();
            DbConnectionManager.setFetchSize(rs, startIndex + numResults);
            DbConnectionManager.scrollResultSet(rs, startIndex);
            int count = 0;
            while (rs.next() && count < numResults) {
                usernames.add(rs.getString(1));
                count++;
            }
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return new UserCollection(usernames.toArray(new String[usernames.size()]));
    }

    public void setName(String username, String name) throws UserNotFoundException {
        if (isReadOnly()) {
            // Reject the operation since the provider is read-only
            throw new UnsupportedOperationException();
        }
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_NAME);
            pstmt.setString(1, name);
            pstmt.setString(2, username);
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            throw new UserNotFoundException(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    public void setEmail(String username, String email) throws UserNotFoundException {
        if (isReadOnly()) {
            // Reject the operation since the provider is read-only
            throw new UnsupportedOperationException();
        }
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_EMAIL);
            pstmt.setString(1, email);
            pstmt.setString(2, username);
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            throw new UserNotFoundException(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    public void setCreationDate(String username, Date creationDate) throws UserNotFoundException {
        if (isReadOnly()) {
            // Reject the operation since the provider is read-only
            throw new UnsupportedOperationException();
        }
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_CREATION_DATE);
            pstmt.setString(1, StringUtils.dateToMillis(creationDate));
            pstmt.setString(2, username);
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            throw new UserNotFoundException(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    public void setModificationDate(String username, Date modificationDate) throws UserNotFoundException {
        if (isReadOnly()) {
            // Reject the operation since the provider is read-only
            throw new UnsupportedOperationException();
        }
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_MODIFICATION_DATE);
            pstmt.setString(1, StringUtils.dateToMillis(modificationDate));
            pstmt.setString(2, username);
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            throw new UserNotFoundException(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    public Set<String> getSearchFields() throws UnsupportedOperationException {
        return new LinkedHashSet<String>(Arrays.asList("Username", "Name", "Email"));
    }

    public Collection<User> findUsers(Set<String> fields, String query)
            throws UnsupportedOperationException
    {
        if (fields.isEmpty()) {
            return Collections.emptyList();
        }
        if (!getSearchFields().containsAll(fields)) {
            throw new IllegalArgumentException("Search fields " + fields + " are not valid.");
        }
        if (query == null || "".equals(query)) {
            return Collections.emptyList();
        }
        // SQL LIKE queries don't map directly into a keyword/wildcard search like we want.
        // Therefore, we do a best approximiation by replacing '*' with '%' and then
        // surrounding the whole query with two '%'. This will return more data than desired,
        // but is better than returning less data than desired.
        query = "%" + query.replace('*', '%') + "%";
        if (query.endsWith("%%")) {
            query = query.substring(0, query.length()-1);
        }

        List<String> usernames = new ArrayList<String>(50);
        Connection con = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            stmt = con.createStatement();
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT username FROM jiveUser WHERE");
            boolean first = true;
            if (fields.contains("Username")) {
                sql.append(" username LIKE '").append(StringUtils.escapeForSQL(query)).append("'");
                first = false;
            }
            if (fields.contains("Name")) {
                if (!first) {
                    sql.append(" AND");
                }
                sql.append(" name LIKE '").append(StringUtils.escapeForSQL(query)).append("'");
                first = false;
            }
            if (fields.contains("Email")) {
                if (!first) {
                    sql.append(" AND");
                }
                sql.append(" email LIKE '").append(StringUtils.escapeForSQL(query)).append("'");
            }
            rs = stmt.executeQuery(sql.toString());
            while (rs.next()) {
                usernames.add(rs.getString(1));
            }
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, stmt, con);
        }
        return new UserCollection(usernames.toArray(new String[usernames.size()]));
    }

    public Collection<User> findUsers(Set<String> fields, String query, int startIndex,
            int numResults) throws UnsupportedOperationException
    {
        if (fields.isEmpty()) {
            return Collections.emptyList();
        }
        if (!getSearchFields().containsAll(fields)) {
            throw new IllegalArgumentException("Search fields " + fields + " are not valid.");
        }
        if (query == null || "".equals(query)) {
            return Collections.emptyList();
        }
        // SQL LIKE queries don't map directly into a keyword/wildcard search like we want.
        // Therefore, we do a best approximiation by replacing '*' with '%' and then
        // surrounding the whole query with two '%'. This will return more data than desired,
        // but is better than returning less data than desired.
        query = "%" + query.replace('*', '%') + "%";
        if (query.endsWith("%%")) {
            query = query.substring(0, query.length()-1);
        }

        List<String> usernames = new ArrayList<String>(50);
        Connection con = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            stmt = con.createStatement();
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT username FROM jiveUser WHERE");
            boolean first = true;
            if (fields.contains("Username")) {
                sql.append(" username LIKE '").append(StringUtils.escapeForSQL(query)).append("'");
                first = false;
            }
            if (fields.contains("Name")) {
                if (!first) {
                    sql.append(" AND");
                }
                sql.append(" name LIKE '").append(StringUtils.escapeForSQL(query)).append("'");
                first = false;
            }
            if (fields.contains("Email")) {
                if (!first) {
                    sql.append(" AND");
                }
                sql.append(" email LIKE '").append(StringUtils.escapeForSQL(query)).append("'");
            }
            rs = stmt.executeQuery(sql.toString());
            // Scroll to the start index.
            DbConnectionManager.scrollResultSet(rs, startIndex);
            int count = 0;
            while (rs.next() && count < numResults) {
                usernames.add(rs.getString(1));
                count++;
            }
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, stmt, con);
        }
        return new UserCollection(usernames.toArray(new String[usernames.size()]));
    }

    public boolean isReadOnly() {
        return false;
    }
}
