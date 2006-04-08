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

package org.jivesoftware.wildfire.user;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.*;
import org.jivesoftware.wildfire.vcard.VCardManager;

import java.sql.*;
import java.util.*;
import java.util.Date;

/**
 * Default implementation of the UserProvider interface, which reads and writes data
 * from the <tt>jiveUser</tt> database table.<p>
 *
 * Passwords can be stored as plain text, or encrypted using Blowfish. The
 * encryption/decryption key is stored as the Wildfire property <tt>passwordKey</tt>,
 * which is automatically created on first-time use. It's critical that the password key
 * not be changed once created, or existing passwords will be lost. By default
 * passwords will be stored encrypted. Plain-text password storage can be enabled
 * by setting the Wildfire property <tt>user.usePlainPassword</tt> to <tt>true</tt>.
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
            "INSERT INTO jiveUser (username,password,encryptedPassword,name,email,creationDate,modificationDate) " +
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
    private static final String LOAD_PASSWORD =
            "SELECT password,encryptedPassword FROM jiveUser WHERE username=?";
    private static final String UPDATE_PASSWORD =
            "UPDATE jiveUser SET password=?, encryptedPassword=? WHERE username=?";

    private static Blowfish cipher = null;

    private static synchronized Blowfish getCipher() {
        if (cipher != null) {
            return cipher;
        }
        // Get the password key, stored as a database property. Obviously,
        // protecting your database is critical for making the
        // encryption fully secure.
        String keyString;
        try {
            keyString = JiveGlobals.getProperty("passwordKey");
            if (keyString == null) {
                keyString = StringUtils.randomString(15);
                JiveGlobals.setProperty("passwordKey", keyString);
            }
            cipher = new Blowfish(keyString);
        }
        catch (Exception e) {
            Log.error(e);
        }
        return cipher;
    }

    public User loadUser(String username) throws UserNotFoundException {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_USER);
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new UserNotFoundException();
            }
            String name = rs.getString(1);
            String email = rs.getString(2);
            Date creationDate = new Date(Long.parseLong(rs.getString(3).trim()));
            Date modificationDate = new Date(Long.parseLong(rs.getString(4).trim()));
            rs.close();

            return new User(username, name, email, creationDate, modificationDate);
        }
        catch (Exception e) {
            throw new UserNotFoundException(e);
        }
        finally {
            try { if (pstmt != null) { pstmt.close(); } }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) { con.close(); } }
            catch (Exception e) { Log.error(e); }
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
                encryptedPassword = getCipher().encryptString(password);
                // Set password to null so that it's inserted that way.
                password = null;
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
                try { if (pstmt != null) { pstmt.close(); } }
                catch (Exception e) { Log.error(e); }
                try { if (con != null) { con.close(); } }
                catch (Exception e) { Log.error(e); }
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
            // Delete all of the users's vcard properties
            try {
                VCardManager.getInstance().deleteVCard(username);
            }
            catch (UnsupportedOperationException e) {
                // Ignore.
            }
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
            try { if (pstmt != null) { pstmt.close(); } }
            catch (Exception e) { Log.error(e); }
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
        }
    }

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
            rs.close();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try { if (pstmt != null) { pstmt.close(); } }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) { con.close(); } }
            catch (Exception e) { Log.error(e); }
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
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ALL_USERS);
            ResultSet rs = pstmt.executeQuery();
            // Set the fetch size. This will prevent some JDBC drivers from trying
            // to load the entire result set into memory.
            DbConnectionManager.setFetchSize(rs, 500);
            while (rs.next()) {
                usernames.add(rs.getString(1));
            }
            rs.close();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try { if (pstmt != null) { pstmt.close(); } }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) { con.close(); } }
            catch (Exception e) { Log.error(e); }
        }
        return usernames;
    }

    public Collection<User> getUsers(int startIndex, int numResults) {
        List<String> usernames = new ArrayList<String>(numResults);
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = DbConnectionManager.createScrollablePreparedStatement(con, ALL_USERS);
            ResultSet rs = pstmt.executeQuery();
            DbConnectionManager.setFetchSize(rs, startIndex + numResults);
            DbConnectionManager.scrollResultSet(rs, startIndex);
            int count = 0;
            while (rs.next() && count < numResults) {
                usernames.add(rs.getString(1));
                count++;
            }
            rs.close();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try { if (pstmt != null) { pstmt.close(); } }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) { con.close(); } }
            catch (Exception e) { Log.error(e); }
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
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
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
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
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
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
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
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
    }

    public String getPassword(String username) throws UserNotFoundException {
        if (!supportsPasswordRetrieval()) {
            // Reject the operation since the provider is read-only
            throw new UnsupportedOperationException();
        }
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_PASSWORD);
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new UserNotFoundException(username);
            }
            String plainText = rs.getString(1);
            String encrypted = rs.getString(2);
            if (encrypted != null) {
                return getCipher().decryptString(encrypted);
            }
            else {
                return plainText;
            }
        }
        catch (SQLException sqle) {
            throw new UserNotFoundException(sqle);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
    }

    public void setPassword(String username, String password) throws UserNotFoundException {
        if (isReadOnly()) {
            // Reject the operation since the provider is read-only
            throw new UnsupportedOperationException();
        }

        // Determine if the password should be stored as plain text or encrypted.
        boolean usePlainPassword = JiveGlobals.getBooleanProperty("user.usePlainPassword");
        String encryptedPassword = null;
        if (!usePlainPassword) {
            encryptedPassword = getCipher().encryptString(password);
            // Set password to null so that it's inserted that way.
            password = null;
        }

        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_PASSWORD);
            if (password == null) {
                pstmt.setNull(1, Types.VARCHAR);
            }
            else {
                pstmt.setString(1, password);
            }
            if (encryptedPassword == null) {
                pstmt.setNull(2, Types.VARCHAR);
            }
            else {
                pstmt.setString(2, encryptedPassword);
            }
            pstmt.setString(3, username);
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            throw new UserNotFoundException(sqle);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
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
            ResultSet rs = stmt.executeQuery(sql.toString());
            while (rs.next()) {
                usernames.add(rs.getString(1));
            }
            rs.close();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try { if (stmt != null) { stmt.close(); } }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) { con.close(); } }
            catch (Exception e) { Log.error(e); }
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
            ResultSet rs = stmt.executeQuery(sql.toString());
            // Scroll to the start index.
            DbConnectionManager.scrollResultSet(rs, startIndex);
            int count = 0;
            while (rs.next() && count < numResults) {
                usernames.add(rs.getString(1));
                count++;
            }
            rs.close();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try { if (stmt != null) { stmt.close(); } }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) { con.close(); } }
            catch (Exception e) { Log.error(e); }
        }
        return new UserCollection(usernames.toArray(new String[usernames.size()]));
    }

    public boolean isReadOnly() {
        return false;
    }

    public boolean supportsPasswordRetrieval() {
        return true;
    }
}