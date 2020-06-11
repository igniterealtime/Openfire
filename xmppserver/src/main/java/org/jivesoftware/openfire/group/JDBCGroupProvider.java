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

import org.apache.commons.lang3.StringUtils;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.database.ExternalDbConnectionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PersistableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import java.sql.*;
import java.util.*;

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
 * Then you need to set the <b>driver properties</b>. Check the documentation of the class
 * {@link org.jivesoftware.database.ExternalDbConnectionProperties}
 * to see what properties you <b>must</b> set. <br />
 * <br />
 * The properties for the SQL statements are:
 * <ul>
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
 * You can also define Group properties to be used from the external database. Below are the default
 * requests as an example:
 * <ul>
 *     <li>{@code jdbcGroupPropertyProvider.grouplistContainersSQL = SELECT groupName FROM ofGroupProp WHERE name='sharedRoster.groupList' AND propValue LIKE ?}</li>
 *     <li>{@code jdbcGroupPropertyProvider.publicGroupsSQL = SELECT groupName FROM ofGroupProp WHERE name='sharedRoster.showInRoster' AND propValue='everybody'}</li>
 *     <li>{@code jdbcGroupPropertyProvider.groupsForPropSQL = SELECT groupName FROM ofGroupProp WHERE name=? AND propValue=?}</li>
 *     <li>{@code jdbcGroupPropertyProvider.loadSharedGroupsSQL = SELECT groupName FROM ofGroupProp WHERE name='sharedRoster.showInRoster' AND propValue IS NOT NULL AND propValue <> 'nobody'}</li>
 *     <li>{@code jdbcGroupPropertyProvider.loadPropertiesSQL = SELECT name, propValue FROM ofGroupProp WHERE groupName=?}</li>
 *     <li>{@code jdbcGroupPropertyProvider.deletePropertySQL = "DELETE FROM ofGroupProp WHERE groupName=? AND name=?"}</li>
 *     <li>{@code jdbcGroupPropertyProvider.deleteAllPropertiesSQL = "DELETE FROM ofGroupProp WHERE groupName=?"}</li>
 *     <li>{@code jdbcGroupPropertyProvider.updatePropertySQL = "UPDATE ofGroupProp SET propValue=? WHERE name=? AND groupName=?"}</li>
 *     <li>{@code jdbcGroupPropertyProvider.insertPropertySQL = "INSERT INTO ofGroupProp (groupName, name, propValue) VALUES (?, ?, ?)"}</li>
 * </ul>
 *
 * If you want to set Group Properties to be read-only, set the following property to true:
 * <ul>
 *     <li>{@code jdbcGroupPropertyProvider.groupPropertyReadonly = true}</li></li>
 * </ul>
 *
 * @author David Snopek
 */
public class JDBCGroupProvider extends AbstractGroupProvider {

    private static final Logger Log = LoggerFactory.getLogger(JDBCGroupProvider.class);

    private final String groupCountSQL;
    private final String descriptionSQL;
    private final String allGroupsSQL;
    private final String userGroupsSQL;
    private final String loadMembersSQL;
    private final String loadAdminsSQL;

    private final boolean useConnectionProvider;

    // Keys to use for the SQL properties to manipulate Group Properties
    private static final String KEY_GRPLIST_CONTAINER = "jdbcGroupPropertyProvider.grouplistContainersSQL";
    private static final String KEY_PUB_GROUP = "jdbcGroupPropertyProvider.publicGroupsSQL";
    private static final String KEY_GROUPS_FOR_PROP = "jdbcGroupPropertyProvider.groupsForPropSQL";
    private static final String KEY_LOAD_SHARED_GROUPS = "jdbcGroupPropertyProvider.loadSharedGroupsSQL";
    private static final String KEY_LOAD_PROPERTIES = "jdbcGroupPropertyProvider.loadPropertiesSQL";
    private static final String KEY_DEL_PROP = "jdbcGroupPropertyProvider.deletePropertySQL";
    private static final String KEY_DEL_ALL_PROP = "jdbcGroupPropertyProvider.deleteAllPropertiesSQL";
    private static final String KEY_UPDATE_PROP = "jdbcGroupPropertyProvider.updatePropertySQL";
    private static final String KEY_INSERT_PROP = "jdbcGroupPropertyProvider.insertPropertySQL";
    private static final String KEY_GROUP_PROP_RO = "jdbcGroupPropertyProvider.groupPropertyReadonly";
    private final String grouplistContainersSQL;
    private final String publicGroupsSQL;
    private final String groupsForPropSQL;
    private final String loadSharedGroupsSQL;
    private final String loadPropertiesSQL;
    private final String deletePropertySQL;
    private final String deleteAllPropertiesSQL;
    private final String updatePropertySQL;
    private final String insertPropertySQL;
    private final boolean groupPropReadonly;

    private final XMPPServer server = XMPPServer.getInstance();

    // Connections to the external database
    private ExternalDbConnectionManager exDb;

    /**
     * Constructor of the JDBCGroupProvider class.
     */
    public JDBCGroupProvider() {
        // Convert XML based provider setup to Database based
        JiveGlobals.migrateProperty("jdbcGroupProvider.groupCountSQL");
        JiveGlobals.migrateProperty("jdbcGroupProvider.allGroupsSQL");
        JiveGlobals.migrateProperty("jdbcGroupProvider.userGroupsSQL");
        JiveGlobals.migrateProperty("jdbcGroupProvider.descriptionSQL");
        JiveGlobals.migrateProperty("jdbcGroupProvider.loadMembersSQL");
        JiveGlobals.migrateProperty("jdbcGroupProvider.loadAdminsSQL");

        JiveGlobals.migrateProperty(KEY_GRPLIST_CONTAINER);
        JiveGlobals.migrateProperty(KEY_PUB_GROUP);
        JiveGlobals.migrateProperty(KEY_GROUPS_FOR_PROP);
        JiveGlobals.migrateProperty(KEY_LOAD_SHARED_GROUPS);
        JiveGlobals.migrateProperty(KEY_LOAD_PROPERTIES);
        JiveGlobals.migrateProperty(KEY_DEL_PROP);
        JiveGlobals.migrateProperty(KEY_DEL_ALL_PROP);
        JiveGlobals.migrateProperty(KEY_UPDATE_PROP);
        JiveGlobals.migrateProperty(KEY_INSERT_PROP);
        JiveGlobals.migrateProperty(KEY_GROUP_PROP_RO);

        useConnectionProvider = JiveGlobals.getBooleanProperty("jdbcGroupProvider.useConnectionProvider");

        if (!useConnectionProvider) {
            exDb = ExternalDbConnectionManager.getInstance();
        }

        // Load SQL statements
        groupCountSQL = JiveGlobals.getProperty("jdbcGroupProvider.groupCountSQL");
        allGroupsSQL = JiveGlobals.getProperty("jdbcGroupProvider.allGroupsSQL");
        userGroupsSQL = JiveGlobals.getProperty("jdbcGroupProvider.userGroupsSQL");
        descriptionSQL = JiveGlobals.getProperty("jdbcGroupProvider.descriptionSQL");
        loadMembersSQL = JiveGlobals.getProperty("jdbcGroupProvider.loadMembersSQL");
        loadAdminsSQL = JiveGlobals.getProperty("jdbcGroupProvider.loadAdminsSQL");

        // If any of those is blank, then the methods implementation will rely on the default method from
        // the super-class
        grouplistContainersSQL = JiveGlobals.getProperty(KEY_GRPLIST_CONTAINER);
        publicGroupsSQL = JiveGlobals.getProperty(KEY_PUB_GROUP);
        groupsForPropSQL = JiveGlobals.getProperty(KEY_GROUPS_FOR_PROP);
        loadSharedGroupsSQL = JiveGlobals.getProperty(KEY_LOAD_SHARED_GROUPS);
        loadPropertiesSQL = JiveGlobals.getProperty(KEY_LOAD_PROPERTIES);

        // If any of those is blank, then we have to set it to its default value. See class DefaultGroupPropertyMap.java
        deletePropertySQL = JiveGlobals.getProperty(KEY_DEL_PROP, DELETE_PROPERTY);
        deleteAllPropertiesSQL = JiveGlobals.getProperty(KEY_DEL_ALL_PROP, DELETE_ALL_PROPERTIES);
        updatePropertySQL = JiveGlobals.getProperty(KEY_UPDATE_PROP, UPDATE_PROPERTY);
        insertPropertySQL = JiveGlobals.getProperty(KEY_INSERT_PROP, INSERT_PROPERTY);

        // Check if the group properties has been manually set to read-only
        groupPropReadonly = JiveGlobals.getBooleanProperty(KEY_GROUP_PROP_RO, false);
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
        if (useConnectionProvider) {
            return DbConnectionManager.getConnection();
        } else {
            return exDb.getConnection();
        }
    }

    /**
     * In this implementation, the group properties are expected to be writable to the backend unless the server property
     * "jdbcGroupPropertyProvider.groupPropertyReadonly" has been explicitly set to 'true'.
     * @return return false or true if "jdbcGroupPropertyProvider.groupPropertyReadonly" is true
     */
    @Override
    public boolean arePropertiesReadOnly() {
        return groupPropReadonly;
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

    @Override
    public Collection<String> getVisibleGroupNames(String userGroup) {
        // If no SQL was defined for this method we stick with the default implementation
        if (StringUtils.isBlank(grouplistContainersSQL)) {
            return super.getVisibleGroupNames(userGroup);
        }

        Set<String> groupNames = new HashSet<>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = getConnection();
            pstmt = con.prepareStatement(grouplistContainersSQL);
            pstmt.setString(1, "%" + userGroup + "%");
            rs = pstmt.executeQuery();
            while (rs.next()) {
                groupNames.add(rs.getString(1));
            }
        }
        catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return groupNames;
    }

    @Override
    public Collection<String> getPublicSharedGroupNames() {
        // If no SQL was defined for this method we stick with the default implementation
        if (StringUtils.isBlank(publicGroupsSQL)) {
            return super.getPublicSharedGroupNames();
        }

        Set<String> groupNames = new HashSet<>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = getConnection();
            pstmt = con.prepareStatement(publicGroupsSQL);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                groupNames.add(rs.getString(1));
            }
        }
        catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return groupNames;
    }

    @Override
    public Collection<String> search(String key, String value) {
        // If no SQL was defined for this method we stick with the default implementation
        if (StringUtils.isBlank(groupsForPropSQL)) {
            return super.search(key, value);
        }

        Set<String> groupNames = new HashSet<>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = getConnection();
            pstmt = con.prepareStatement(groupsForPropSQL);
            pstmt.setString(1, key);
            pstmt.setString(2, value);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                groupNames.add(rs.getString(1));
            }
        }
        catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return groupNames;
    }


    @Override
    public Collection<String> getSharedGroupNames() {
        // If no SQL was defined for this method we stick with the default implementation
        if (StringUtils.isBlank(loadSharedGroupsSQL)) {
            return super.getSharedGroupNames();
        }

        Collection<String> groupNames = new HashSet<>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = getConnection();
            pstmt = con.prepareStatement(loadSharedGroupsSQL);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                groupNames.add(rs.getString(1));
            }
        }
        catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return groupNames;
    }

    /**
     * Returns a custom {@link Map} that updates the database whenever
     * a property value is added, changed, or deleted.
     *
     * @param group The target group
     * @return The properties for the given group
     */
    @Override
    public PersistableMap<String,String> loadProperties(Group group) {
        // If no SQL was defined for this method we stick with the default implementation
        if (StringUtils.isBlank(loadPropertiesSQL)) {
            return super.loadProperties(group);
        }

        // custom map implementation persists group property changes
        // whenever one of the standard mutator methods are called
        String name = group.getName();
        PersistableMap<String,String> result;
        if (useConnectionProvider) {
            /*  the 'connectionProvider' var indicates the legacy behaviour where we were using the connectionProvider
                of the main Openfire's db to retrieve group properties */
            result = new DefaultGroupPropertyMap<>(group, deletePropertySQL, deleteAllPropertiesSQL, updatePropertySQL,
                insertPropertySQL, groupPropReadonly);
        } else {
            /*  The new behaviour OF-2036 is to use the custom connectionProvider to an externalDatabase */
            result = new DefaultGroupPropertyMap<>(group, true, deletePropertySQL, deleteAllPropertiesSQL,
                updatePropertySQL, insertPropertySQL, groupPropReadonly);
        }
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = getConnection();
            pstmt = con.prepareStatement(loadPropertiesSQL);
            pstmt.setString(1, name);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                String key = rs.getString(1);
                String value = rs.getString(2);
                if (key != null) {
                    if (value == null) {
                        result.remove(key);
                        Log.warn("Deleted null property " + key + " for group: " + name);
                    } else {
                        result.put(key, value, false); // skip persistence during load
                    }
                }
                else { // should not happen, but ...
                    Log.warn("Ignoring null property key for group: " + name);
                }
            }
        }
        catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return result;
    }

}
