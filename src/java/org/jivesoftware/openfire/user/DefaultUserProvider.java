/**
 * $RCSfile: DefaultUserProvider.java,v $
 * $Revision: 3116 $
 * $Date: 2005-11-24 06:25:00 -0300 (Thu, 24 Nov 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.user;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

/**
 * Default implementation of the UserProvider interface, which reads and writes data
 * from the <tt>ofUser</tt> database table.<p>
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

	private static final Logger Log = LoggerFactory.getLogger(DefaultUserProvider.class);

    private static final String LOAD_USER =
            "SELECT name, email, creationDate, modificationDate FROM ofUser WHERE username=?";
    private static final String USER_COUNT =
            "SELECT count(*) FROM ofUser";
    private static final String ALL_USERS =
            "SELECT username FROM ofUser ORDER BY username";
    private static final String INSERT_USER =
            "INSERT INTO ofUser (username,plainPassword,encryptedPassword,name,email,creationDate,modificationDate) " +
            "VALUES (?,?,?,?,?,?,?)";
    private static final String DELETE_USER_PROPS =
            "DELETE FROM ofUserProp WHERE username=?";
    private static final String DELETE_USER =
            "DELETE FROM ofUser WHERE username=?";
    private static final String UPDATE_NAME =
            "UPDATE ofUser SET name=? WHERE username=?";
    private static final String UPDATE_EMAIL =
            "UPDATE ofUser SET email=? WHERE username=?";
    private static final String UPDATE_CREATION_DATE =
            "UPDATE ofUser SET creationDate=? WHERE username=?";
    private static final String UPDATE_MODIFICATION_DATE =
            "UPDATE ofUser SET modificationDate=? WHERE username=?";
    private static final boolean IS_READ_ONLY = false;

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
                if (name == null || name.matches("\\s*")) {
                    pstmt.setNull(4, Types.VARCHAR);
                }
                else {
                    pstmt.setString(4, name);
                }
                if (email == null || email.matches("\\s*")) {
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
        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;
        try {
            // Delete all of the users's extended properties
            con = DbConnectionManager.getTransactionConnection();
            pstmt = con.prepareStatement(DELETE_USER_PROPS);
            pstmt.setString(1, username);
            pstmt.execute();
            DbConnectionManager.fastcloseStmt(pstmt);

            // Delete the actual user entry
            pstmt = con.prepareStatement(DELETE_USER);
            pstmt.setString(1, username);
            pstmt.execute();
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
            abortTransaction = true;
        }
        finally {
            DbConnectionManager.closeStatement(pstmt);
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
            Log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return count;
    }

    public Collection<User> getUsers() {
        Collection<String> usernames = getUsernames(0, Integer.MAX_VALUE);
        return new UserCollection(usernames.toArray(new String[usernames.size()]));
    }

    public Collection<String> getUsernames() {
        return getUsernames(0, Integer.MAX_VALUE);
    }

    private Collection<String> getUsernames(int startIndex, int numResults) {
        List<String> usernames = new ArrayList<String>(500);
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            if ((startIndex==0) && (numResults==Integer.MAX_VALUE))
            {
                pstmt = con.prepareStatement(ALL_USERS);
                // Set the fetch size. This will prevent some JDBC drivers from trying
                // to load the entire result set into memory.
                DbConnectionManager.setFetchSize(pstmt, 500);
                rs = pstmt.executeQuery();
                while (rs.next()) {
                    usernames.add(rs.getString(1));
                }
            }
            else {
                pstmt = DbConnectionManager.createScrollablePreparedStatement(con, ALL_USERS);
                DbConnectionManager.limitRowsAndFetchSize(pstmt, startIndex, numResults);
                rs = pstmt.executeQuery();
                DbConnectionManager.scrollResultSet(rs, startIndex);
                int count = 0;
                while (rs.next() && count < numResults) {
                    usernames.add(rs.getString(1));
                    count++;
                }
            }
            if (Log.isDebugEnabled()) {
                   Log.debug("Results: " + usernames.size());
                   LogResults(usernames);
            }
        }
        catch (SQLException e) {
            Log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return usernames;
    }

    public Collection<User> getUsers(int startIndex, int numResults) {
        Collection<String> usernames = getUsernames(startIndex, numResults);
        return new UserCollection(usernames.toArray(new String[usernames.size()]));
    }

    public void setName(String username, String name) throws UserNotFoundException {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_NAME);
            if (name == null || name.matches("\\s*")) {
            	pstmt.setNull(1, Types.VARCHAR);
            } 
            else {
            	pstmt.setString(1, name);
            }
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
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_EMAIL);
            if (email == null || email.matches("\\s*")) {
            	pstmt.setNull(1, Types.VARCHAR);
            } 
            else {
            	pstmt.setString(1, email);
            }
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

    public Collection<User> findUsers(Set<String> fields, String query) throws UnsupportedOperationException {
        return findUsers(fields, query, 0, Integer.MAX_VALUE);
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
        PreparedStatement pstmt = null;
        int queries=0;
        ResultSet rs = null;
        try {
            StringBuilder sql = new StringBuilder(90);
            sql.append("SELECT username FROM ofUser WHERE");
            boolean first = true;
            if (fields.contains("Username")) {
                sql.append(" username LIKE ?");
                queries++;
                first = false;
            }
            if (fields.contains("Name")) {
                if (!first) {
                    sql.append(" AND");
                }
                sql.append(" name LIKE ?");
                queries++;
                first = false;
            }
            if (fields.contains("Email")) {
                if (!first) {
                    sql.append(" AND");
                }
                sql.append(" email LIKE ?");
                queries++;
            }
            con = DbConnectionManager.getConnection();
            if ((startIndex==0) && (numResults==Integer.MAX_VALUE))
            {
                pstmt = con.prepareStatement(sql.toString());
                for (int i=1; i<=queries; i++)
                {
                    pstmt.setString(i, query);
                }
                rs = pstmt.executeQuery();
                while (rs.next()) {
                    usernames.add(rs.getString(1));
                }
            } else {
                pstmt = DbConnectionManager.createScrollablePreparedStatement(con, sql.toString());
                DbConnectionManager.limitRowsAndFetchSize(pstmt, startIndex, numResults);
                for (int i=1; i<=queries; i++)
                {
                	pstmt.setString(i, query);
                }
                rs = pstmt.executeQuery();
                // Scroll to the start index.
                DbConnectionManager.scrollResultSet(rs, startIndex);
                int count = 0;
                while (rs.next() && count < numResults) {
                    usernames.add(rs.getString(1));
                    count++;
               }
            }
            if (Log.isDebugEnabled())
            {
                Log.debug("Results: " + usernames.size());
                LogResults(usernames);
            }
        }
        catch (SQLException e) {
            Log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return new UserCollection(usernames.toArray(new String[usernames.size()]));
    }

    public boolean isReadOnly() {
        return IS_READ_ONLY;
    }

    public boolean isNameRequired() {
        return false;
    }

    public boolean isEmailRequired() {
        return false;
    }
    /**
     * Make sure that Log.isDebugEnabled()==true before calling this method.
     * Twenty elements will be logged in every log line, so for 81-100 elements
     * five log lines will be generated
     * @param listElements a list of Strings which will be logged 
     */
    private void LogResults(List<String> listElements) {
        String callingMethod = Thread.currentThread().getStackTrace()[3].getMethodName();
        StringBuilder sb = new StringBuilder(256);
        int count = 0;
        for (String element : listElements)
        {
            if (count > 20)
            {
                Log.debug(callingMethod + " results: " + sb.toString());
                sb.delete(0, sb.length());
                count = 0;
            }
            sb.append(element).append(",");
            count++;
        }
        sb.append(".");
        Log.debug(callingMethod + " results: " + sb.toString());
    }
}
