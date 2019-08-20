/*
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

package org.jivesoftware.openfire.user;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

/**
 * The JDBC user provider allows you to use an external database to define the users.
 * It is best used with the JDBCAuthProvider &amp; JDBCGroupProvider to provide integration
 * between your external system and Openfire. All data is treated as read-only so any
 * set operations will result in an exception.
 * <p>For the search facility, the SQL will be constructed from the SQL in the <i>search</i>
 * section below, as well as the <i>usernameField</i>, the <i>nameField</i> and the
 * <i>emailField</i>.</p>
 * <p>To enable this provider, set the following in the system properties:</p>
 * <ul>
 * <li>{@code provider.user.className = org.jivesoftware.openfire.user.JDBCUserProvider}</li>
 * </ul>
 * <p>
 * Then you need to set your driver, connection string and SQL statements:
 * </p>
 * <ul>
 * <li>{@code jdbcProvider.driver = com.mysql.jdbc.Driver}</li>
 * <li>{@code jdbcProvider.connectionString = jdbc:mysql://localhost/dbname?user=username&amp;password=secret}</li>
 * <li>{@code jdbcUserProvider.loadUserSQL = SELECT name,email FROM myUser WHERE user = ?}</li>
 * <li>{@code jdbcUserProvider.userCountSQL = SELECT COUNT(*) FROM myUser}</li>
 * <li>{@code jdbcUserProvider.allUsersSQL = SELECT user FROM myUser}</li>
 * <li>{@code jdbcUserProvider.searchSQL = SELECT user FROM myUser WHERE}</li>
 * <li>{@code jdbcUserProvider.usernameField = myUsernameField}</li>
 * <li>{@code jdbcUserProvider.nameField = myNameField}</li>
 * <li>{@code jdbcUserProvider.emailField = mymailField}</li>
 * </ul>
 *
 * In order to use the configured JDBC connection provider do not use a JDBC
 * connection string, set the following property
 *
 * <ul>
 * <li>{@code jdbcUserProvider.useConnectionProvider = true}</li>
 * </ul>
 *
 *
 * @author Huw Richards huw.richards@gmail.com
 */
public class JDBCUserProvider implements UserProvider {

    private static final Logger Log = LoggerFactory.getLogger(JDBCUserProvider.class);

    private String connectionString;

    private String loadUserSQL;
    private String userCountSQL;
    private String allUsersSQL;
    private String searchSQL;
    private String usernameField;
    private String nameField;
    private String emailField;
    private boolean useConnectionProvider;
    private static final boolean IS_READ_ONLY = true;

    /**
     * Constructs a new JDBC user provider.
     */
    public JDBCUserProvider() {
        // Convert XML based provider setup to Database based
        JiveGlobals.migrateProperty("jdbcProvider.driver");
        JiveGlobals.migrateProperty("jdbcProvider.connectionString");
        JiveGlobals.migrateProperty("jdbcUserProvider.loadUserSQL");
        JiveGlobals.migrateProperty("jdbcUserProvider.userCountSQL");
        JiveGlobals.migrateProperty("jdbcUserProvider.allUsersSQL");
        JiveGlobals.migrateProperty("jdbcUserProvider.searchSQL");
        JiveGlobals.migrateProperty("jdbcUserProvider.usernameField");
        JiveGlobals.migrateProperty("jdbcUserProvider.nameField");
        JiveGlobals.migrateProperty("jdbcUserProvider.emailField");

        useConnectionProvider = JiveGlobals.getBooleanProperty("jdbcUserProvider.useConnectionProvider");

            // Load the JDBC driver and connection string.
        if (!useConnectionProvider) {
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

        // Load database statements for user data.
        loadUserSQL = JiveGlobals.getProperty("jdbcUserProvider.loadUserSQL");
        userCountSQL = JiveGlobals.getProperty("jdbcUserProvider.userCountSQL");
        allUsersSQL = JiveGlobals.getProperty("jdbcUserProvider.allUsersSQL");
        searchSQL = JiveGlobals.getProperty("jdbcUserProvider.searchSQL");
        usernameField = JiveGlobals.getProperty("jdbcUserProvider.usernameField");
        nameField = JiveGlobals.getProperty("jdbcUserProvider.nameField");
        emailField = JiveGlobals.getProperty("jdbcUserProvider.emailField");
    }

    /**
     * XMPP disallows some characters in identifiers, requiring them to be escaped.
     *
     * This implementation assumes that the database returns properly escaped identifiers,
     * but can apply escaping by setting the value of the 'jdbcUserProvider.isEscaped'
     * property to 'false'.
     *
     * @return 'false' if this implementation needs to escape database content before processing.
     */
    protected boolean assumePersistedDataIsEscaped()
    {
        return JiveGlobals.getBooleanProperty( "jdbcUserProvider.isEscaped", true );
    }

    @Override
    public User loadUser(String username) throws UserNotFoundException {
        if(username.contains("@")) {
            if (!XMPPServer.getInstance().isLocal(new JID(username))) {
                throw new UserNotFoundException("Cannot load user of remote server: " + username);
            }
            username = username.substring(0,username.lastIndexOf("@"));
        }

        // OF-1837: When the database does not hold escaped data, our query should use unescaped values in the 'where' clause.
        final String queryValue = assumePersistedDataIsEscaped() ? username : JID.unescapeNode( username );

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = getConnection();
            pstmt = con.prepareStatement(loadUserSQL);
            pstmt.setString(1, queryValue);
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new UserNotFoundException();
            }
            String name = rs.getString(1);
            String email = rs.getString(2);
            return new User(username, name, email, new Date(), new Date());
        }
        catch (Exception e) {
            throw new UserNotFoundException(e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    @Override
    public User createUser(String username, String password, String name, String email)
            throws UserAlreadyExistsException {
        // Reject the operation since the provider is read-only
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteUser(String username) {
        // Reject the operation since the provider is read-only
        throw new UnsupportedOperationException();
    }

    @Override
    public int getUserCount() {
        int count = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = getConnection();
            pstmt = con.prepareStatement(userCountSQL);
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

    @Override
    public Collection<User> getUsers() {
        Collection<String> usernames = getUsernames(0, Integer.MAX_VALUE);
        return new UserCollection(usernames.toArray(new String[0]));
    }

    @Override
    public Collection<String> getUsernames() {
        return getUsernames(0, Integer.MAX_VALUE);
    }

    private Collection<String> getUsernames(int startIndex, int numResults) {
        List<String> usernames = new ArrayList<>(500);
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = getConnection();
            if ((startIndex==0) && (numResults==Integer.MAX_VALUE))
            {
                pstmt = con.prepareStatement(allUsersSQL);
                // Set the fetch size. This will prevent some JDBC drivers from trying
                // to load the entire result set into memory.
                DbConnectionManager.setFetchSize(pstmt, 500);
                rs = pstmt.executeQuery();
                while (rs.next()) {
                    // OF-1837: When the database does not hold escaped data, escape values before processing them further.
                    final String username;
                    if (assumePersistedDataIsEscaped()) {
                        username = rs.getString(1);
                    } else {
                        username = JID.escapeNode( rs.getString(1) );
                    }
                    usernames.add(username);
                }
            }
            else {
                pstmt = DbConnectionManager.createScrollablePreparedStatement(con, allUsersSQL);
                DbConnectionManager.limitRowsAndFetchSize(pstmt, startIndex, numResults);
                rs = pstmt.executeQuery();
                DbConnectionManager.scrollResultSet(rs, startIndex);
                int count = 0;
                while (rs.next() && count < numResults) {
                    // OF-1837: When the database does not hold escaped data, escape values before processing them further.
                    final String username;
                    if (assumePersistedDataIsEscaped()) {
                        username = rs.getString(1);
                    } else {
                        username = JID.escapeNode( rs.getString(1) );
                    }
                    usernames.add(username);
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

    @Override
    public Collection<User> getUsers(int startIndex, int numResults) {
        Collection<String> usernames = getUsernames(startIndex, numResults);
        return new UserCollection(usernames.toArray(new String[0]));
    }
    
    @Override
    public void setName(String username, String name) throws UserNotFoundException {
        // Reject the operation since the provider is read-only
        throw new UnsupportedOperationException();
    }

    @Override
    public void setEmail(String username, String email) throws UserNotFoundException {
        // Reject the operation since the provider is read-only
        throw new UnsupportedOperationException();
    }

    @Override
    public void setCreationDate(String username, Date creationDate) throws UserNotFoundException {
        // Reject the operation since the provider is read-only
        throw new UnsupportedOperationException();
    }

    @Override
    public void setModificationDate(String username, Date modificationDate) throws UserNotFoundException {
        // Reject the operation since the provider is read-only
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<String> getSearchFields() throws UnsupportedOperationException {
        if (searchSQL == null) {
            throw new UnsupportedOperationException();
        }
        return new LinkedHashSet<>(Arrays.asList("Username", "Name", "Email"));
    }

    @Override
    public Collection<User> findUsers(Set<String> fields, String query) throws UnsupportedOperationException {
        return findUsers(fields, query, 0, Integer.MAX_VALUE);
    }

    @Override
    public Collection<User> findUsers(Set<String> fields, String query, int startIndex,
            int numResults) throws UnsupportedOperationException
    {
        if (searchSQL == null) {
            throw new UnsupportedOperationException();
        }
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
            query = query.substring(0, query.length() - 1);
        }

        List<String> usernames = new ArrayList<>(50);
        Connection con = null;
        PreparedStatement pstmt = null;
        int queries=0;
        ResultSet rs = null;
        try {
            StringBuilder sql = new StringBuilder(90);
            sql.append(searchSQL);
            boolean first = true;
            if (fields.contains("Username")) {
                sql.append(' ');
                sql.append(usernameField);
                sql.append(" LIKE ?");
                queries++;
                first = false;
            }
            if (fields.contains("Name")) {
                if (!first) {
                    sql.append(" AND");
                }
                sql.append(' ');
                sql.append(nameField);
                sql.append(" LIKE ?");
                queries++;
                first = false;
            }
            if (fields.contains("Email")) {
                if (!first) {
                    sql.append(" AND");
                }
                sql.append(' ');
                sql.append(emailField);
                sql.append(" LIKE ?");
                queries++;
            }
            con = getConnection();
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
        return new UserCollection(usernames.toArray(new String[0]));
    }

    @Override
    public boolean isReadOnly() {
        return IS_READ_ONLY;
    }

    @Override
    public boolean isNameRequired() {
        return false;
    }

    @Override
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
            sb.append(element).append(',');
            count++;
        }
        sb.append('.');
        Log.debug(callingMethod + " results: " + sb.toString());
    }

    private Connection getConnection() throws SQLException {
        if (useConnectionProvider) {
            return DbConnectionManager.getConnection();
        } else
        {
            return DriverManager.getConnection(connectionString);
        }
    }
}
