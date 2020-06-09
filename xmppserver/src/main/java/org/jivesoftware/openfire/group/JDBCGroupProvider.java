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

package org.jivesoftware.openfire.group;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

/**
 * The JDBC group provider allows you to use an external database to define the make up of groups.
 * It is best used with the JDBCAuthProvider to provide integration between your external system and
 * Openfire.  All data is treated as read-only so any set operations will result in an exception.
 *
 * To enable this provider, set the following in the system properties:
 *
 * <ul>
 * <li>{@code provider.group.className = org.jivesoftware.openfire.group.JDBCGroupProvider}</li>
 * </ul>
 *
 * Then you need to set your driver, connection string and SQL statements:
 *
 * <ul>
 * <li>{@code jdbcProvider.driver = com.mysql.jdbc.Driver}</li>
 * <li>{@code jdbcProvider.connectionString = jdbc:mysql://localhost/dbname?user=username&amp;password=secret}</li>
 * <li>{@code jdbcGroupProvider.groupCountSQL = SELECT count(*) FROM myGroups}</li>
 * <li>{@code jdbcGroupProvider.allGroupsSQL = SELECT groupName FROM myGroups}</li>
 * <li>{@code jdbcGroupProvider.userGroupsSQL = SELECT groupName FORM myGroupUsers WHERE username=?}</li>
 * <li>{@code jdbcGroupProvider.descriptionSQL = SELECT groupDescription FROM myGroups WHERE groupName=?}</li>
 * <li>{@code jdbcGroupProvider.loadMembersSQL = SELECT username FORM myGroupUsers WHERE groupName=? AND isAdmin='N'}</li>
 * <li>{@code jdbcGroupProvider.loadAdminsSQL = SELECT username FORM myGroupUsers WHERE groupName=? AND isAdmin='Y'}</li>
 * </ul>
 *
 * In order to use the configured JDBC connection provider do not use a JDBC
 * connection string, set the following property
 *
 * <ul>
 * <li>{@code jdbcGroupProvider.useConnectionProvider = true}</li>
 * </ul>
 *
 * @author David Snopek
 */
public class JDBCGroupProvider extends AbstractGroupProvider {

    private static final Logger Log = LoggerFactory.getLogger(JDBCGroupProvider.class);

    private String connectionString;

    private String groupCountSQL;
    private String descriptionSQL;
    private String allGroupsSQL;
    private String userGroupsSQL;
    private String loadMembersSQL;
    private String loadAdminsSQL;
    private boolean useConnectionProvider;

    private XMPPServer server = XMPPServer.getInstance();  

    /**
     * Constructor of the JDBCGroupProvider class.
     */
    public JDBCGroupProvider() {
        // Convert XML based provider setup to Database based
        JiveGlobals.migrateProperty("jdbcProvider.driver");
        JiveGlobals.migrateProperty("jdbcProvider.connectionString");
        JiveGlobals.migrateProperty("jdbcGroupProvider.groupCountSQL");
        JiveGlobals.migrateProperty("jdbcGroupProvider.allGroupsSQL");
        JiveGlobals.migrateProperty("jdbcGroupProvider.userGroupsSQL");
        JiveGlobals.migrateProperty("jdbcGroupProvider.descriptionSQL");
        JiveGlobals.migrateProperty("jdbcGroupProvider.loadMembersSQL");
        JiveGlobals.migrateProperty("jdbcGroupProvider.loadAdminsSQL");

        useConnectionProvider = JiveGlobals.getBooleanProperty("jdbcGroupProvider.useConnectionProvider");

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

        // Load SQL statements
        groupCountSQL = JiveGlobals.getProperty("jdbcGroupProvider.groupCountSQL");
        allGroupsSQL = JiveGlobals.getProperty("jdbcGroupProvider.allGroupsSQL");
        userGroupsSQL = JiveGlobals.getProperty("jdbcGroupProvider.userGroupsSQL");
        descriptionSQL = JiveGlobals.getProperty("jdbcGroupProvider.descriptionSQL");
        loadMembersSQL = JiveGlobals.getProperty("jdbcGroupProvider.loadMembersSQL");
        loadAdminsSQL = JiveGlobals.getProperty("jdbcGroupProvider.loadAdminsSQL");
    }

    /**
     * XMPP disallows some characters in identifiers, requiring them to be escaped.
     *
     * This implementation assumes that the database returns properly escaped identifiers,
     * but can apply escaping by setting the value of the 'jdbcGroupProvider.isEscaped'
     * property to 'false'.
     *
     * @return 'false' if this implementation needs to escape database content before processing.
     */
    protected boolean assumePersistedDataIsEscaped()
    {
        return JiveGlobals.getBooleanProperty( "jdbcGroupProvider.isEscaped", true );
    }

    private Connection getConnection() throws SQLException {
        if (useConnectionProvider)
            return DbConnectionManager.getConnection();
        return DriverManager.getConnection(connectionString);
    }

    @Override
    public Group getGroup(String name) throws GroupNotFoundException {
        String description = null;

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = getConnection();
            pstmt = con.prepareStatement(descriptionSQL);
            pstmt.setString(1, name);
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new GroupNotFoundException("Group with name "
                        + name + " not found.");
            }
            description = rs.getString(1);
        }
        catch (SQLException e) {
            Log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        Collection<JID> members = getMembers(name, false);
        Collection<JID> administrators = getMembers(name, true);
        return new Group(name, description, members, administrators);
    }

    private Collection<JID> getMembers(String groupName, boolean adminsOnly) {
        List<JID> members = new ArrayList<>();

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = getConnection();
            if (adminsOnly) {
                if (loadAdminsSQL == null) {
                    return members;
                }
                pstmt = con.prepareStatement(loadAdminsSQL);
            }
            else {
                pstmt = con.prepareStatement(loadMembersSQL);
            }

            pstmt.setString(1, groupName);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                String user = rs.getString(1);
                if (user != null) {
                    JID userJID;
                    if (user.contains("@")) {
                        if ( !assumePersistedDataIsEscaped()) {
                            // OF-1837: When the database does not hold escaped data, escape values before processing them further.
                            final int splitIndex = user.lastIndexOf( "@" );
                            final String node = user.substring( 0, splitIndex );
                            userJID = new JID( JID.escapeNode( node ) + user.substring( splitIndex ) );
                        } else {
                            userJID = new JID(user);
                        }
                    }
                    else {
                        // OF-1837: When the database does not hold escaped data, escape values before processing them further.
                        final String processedNode = assumePersistedDataIsEscaped() ? user : JID.escapeNode( user );
                        userJID = server.createJID(processedNode, null);
                    }
                    members.add(userJID);
                }
            }
        }
        catch (SQLException e) {
            Log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return members;
    }

    @Override
    public int getGroupCount() {
        int count = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = getConnection();
            pstmt = con.prepareStatement(groupCountSQL);
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
    public Collection<String> getGroupNames() {
        List<String> groupNames = new ArrayList<>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = getConnection();
            pstmt = con.prepareStatement(allGroupsSQL);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                groupNames.add(rs.getString(1));
            }
        }
        catch (SQLException e) {
            Log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return groupNames;
    }

    @Override
    public Collection<String> getGroupNames(int start, int num) {
        List<String> groupNames = new ArrayList<>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = getConnection();
            pstmt = DbConnectionManager.createScrollablePreparedStatement(con, allGroupsSQL);
            rs = pstmt.executeQuery();
            DbConnectionManager.scrollResultSet(rs, start);
            int count = 0;
            while (rs.next() && count < num) {
                groupNames.add(rs.getString(1));
                count++;
            }
        }
        catch (SQLException e) {
            Log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return groupNames;
    }

    @Override
    public Collection<String> getGroupNames(JID user) {
        List<String> groupNames = new ArrayList<>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            // OF-1837: When the database does not hold escaped data, our query should use unescaped values in the 'where' clause.
            final String queryValue;
            if ( server.isLocal(user) ) {
                queryValue = assumePersistedDataIsEscaped() ? user.getNode() : JID.unescapeNode( user.getNode() );
            } else {
                String value = user.toString();
                final int splitIndex = value.lastIndexOf( "@" );
                final String node = value.substring( 0, splitIndex );
                final String processedNode = assumePersistedDataIsEscaped() ? node : JID.unescapeNode( node );
                queryValue = processedNode + value.substring( splitIndex );
            }
            con = getConnection();
            pstmt = con.prepareStatement(userGroupsSQL);
            pstmt.setString(1, queryValue);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                groupNames.add(rs.getString(1));
            }
        }
        catch (SQLException e) {
            Log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return groupNames;
    }
}
