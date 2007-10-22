/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.group;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * The JDBC group provider allows you to use an external database to define the make up of groups.
 * It is best used with the JDBCAuthProvider to provide integration between your external system and
 * Openfire.  All data is treated as read-only so any set operations will result in an exception.
 *
 * To enable this provider, set the following in the XML configuration file:
 *
 * <pre>
 * &lt;provider&gt;
 *     &lt;group&gt;
 *         &lt;className&gt;org.jivesoftware.openfire.group.JDBCGroupProvider&lt;/className&gt;
 *     &lt;/group&gt;
 * &lt;/provider&gt;
 * </pre>
 *
 * Then you need to set your driver, connection string and SQL statements:
 *
 * <pre>
 * &lt;jdbcProvider&gt;
 *     &lt;driver&gt;com.mysql.jdbc.Driver&lt;/driver&gt;
 *     &lt;connectionString&gt;jdbc:mysql://localhost/dbname?user=username&amp;password=secret&lt;/connectionString&gt;
 * &lt;/jdbcProvider&gt;
 *
 * &lt;jdbcGroupProvider&gt;
 *      &lt;groupCountSQL&gt;SELECT count(*) FROM myGroups&lt;/groupCountSQL&gt;
 *      &lt;allGroupsSQL&gt;SELECT groupName FROM myGroups&lt;/allGroupsSQL&gt;
 *      &lt;userGroupsSQL&gt;SELECT groupName FORM myGroupUsers WHERE
 * username=?&lt;/userGroupsSQL&gt;
 *      &lt;descriptionSQL&gt;SELECT groupDescription FROM myGroups WHERE
 * groupName=?&lt;/descriptionSQL&gt;
 *      &lt;loadMembersSQL&gt;SELECT username FORM myGroupUsers WHERE groupName=? AND
 * isAdmin='N'&lt;/loadMembersSQL&gt;
 *      &lt;loadAdminsSQL&gt;SELECT username FORM myGroupUsers WHERE groupName=? AND
 * isAdmin='Y'&lt;/loadAdminsSQL&gt;
 * &lt;/jdbcGroupProvider&gt;
 * </pre>
 *
 * @author David Snopek
 */
public class JDBCGroupProvider implements GroupProvider {

    private String connectionString;

    private String groupCountSQL;
    private String descriptionSQL;
    private String allGroupsSQL;
    private String userGroupsSQL;
    private String loadMembersSQL;
    private String loadAdminsSQL;

    private XMPPServer server = XMPPServer.getInstance();  

    /**
     * Constructor of the JDBCGroupProvider class.
     */
    public JDBCGroupProvider() {
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

        // Load SQL statements
        groupCountSQL = JiveGlobals.getXMLProperty("jdbcGroupProvider.groupCountSQL");
        allGroupsSQL = JiveGlobals.getXMLProperty("jdbcGroupProvider.allGroupsSQL");
        userGroupsSQL = JiveGlobals.getXMLProperty("jdbcGroupProvider.userGroupsSQL");
        descriptionSQL = JiveGlobals.getXMLProperty("jdbcGroupProvider.descriptionSQL");
        loadMembersSQL = JiveGlobals.getXMLProperty("jdbcGroupProvider.loadMembersSQL");
        loadAdminsSQL = JiveGlobals.getXMLProperty("jdbcGroupProvider.loadAdminsSQL");
    }

    /**
     * Always throws an UnsupportedOperationException because JDBC groups are read-only.
     *
     * @param name the name of the group to create.
     * @throws UnsupportedOperationException when called.
     */
    public Group createGroup(String name) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Always throws an UnsupportedOperationException because JDBC groups are read-only.
     *
     * @param name the name of the group to delete
     * @throws UnsupportedOperationException when called.
     */
    public void deleteGroup(String name) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    public Group getGroup(String name) throws GroupNotFoundException {
        String description = null;

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DriverManager.getConnection(connectionString);
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
            Log.error(e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        Collection<JID> members = getMembers(name, false);
        Collection<JID> administrators = getMembers(name, true);
        return new Group(name, description, members, administrators);
    }

    private Collection<JID> getMembers(String groupName, boolean adminsOnly) {
        List<JID> members = new ArrayList<JID>();

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DriverManager.getConnection(connectionString);
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
                        userJID = new JID(user);
                    }
                    else {
                        userJID = server.createJID(user, null); 
                    }
                    members.add(userJID);
                }
            }
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return members;
    }

    /**
     * Always throws an UnsupportedOperationException because JDBC groups are read-only.
     *
     * @param oldName the current name of the group.
     * @param newName the desired new name of the group.
     * @throws UnsupportedOperationException when called.
     */
    public void setName(String oldName, String newName) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Always throws an UnsupportedOperationException because JDBC groups are read-only.
     *
     * @param name the group name.
     * @param description the group description.
     * @throws UnsupportedOperationException when called.
     */
    public void setDescription(String name, String description)
            throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    public int getGroupCount() {
        int count = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DriverManager.getConnection(connectionString);
            pstmt = con.prepareStatement(groupCountSQL);
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

    public Collection<String> getGroupNames() {
        List<String> groupNames = new ArrayList<String>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DriverManager.getConnection(connectionString);
            pstmt = con.prepareStatement(allGroupsSQL);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                groupNames.add(rs.getString(1));
            }
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return groupNames;
    }

    public Collection<String> getGroupNames(int start, int num) {
        List<String> groupNames = new ArrayList<String>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DriverManager.getConnection(connectionString);
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
            Log.error(e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return groupNames;
    }

    public Collection<String> getGroupNames(JID user) {
        List<String> groupNames = new ArrayList<String>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DriverManager.getConnection(connectionString);
            pstmt = con.prepareStatement(userGroupsSQL);
            pstmt.setString(1, server.isLocal(user) ? user.getNode() : user.toString());
            rs = pstmt.executeQuery();
            while (rs.next()) {
                groupNames.add(rs.getString(1));
            }
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return groupNames;
    }

    /**
     * Always throws an UnsupportedOperationException because JDBC groups are read-only.
     *
     * @param groupName name of a group.
     * @param user the JID of the user to add
     * @param administrator true if is an administrator.
     * @throws UnsupportedOperationException when called.
     */
    public void addMember(String groupName, JID user, boolean administrator)
            throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Always throws an UnsupportedOperationException because JDBC groups are read-only.
     *
     * @param groupName the naame of a group.
     * @param user the JID of the user with new privileges
     * @param administrator true if is an administrator.
     * @throws UnsupportedOperationException when called.
     */
    public void updateMember(String groupName, JID user, boolean administrator)
            throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Always throws an UnsupportedOperationException because JDBC groups are read-only.
     *
     * @param groupName the name of a group.
     * @param user the JID of the user to delete.
     * @throws UnsupportedOperationException when called.
     */
    public void deleteMember(String groupName, JID user)
            throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Always returns true because JDBC groups are read-only.
     *
     * @return true because all JDBC functions are read-only.
     */
    public boolean isReadOnly() {
        return true;
    }

    public Collection<String> search(String query) {
        return Collections.emptyList();
    }

    public Collection<String> search(String query, int startIndex, int numResults) {
        return Collections.emptyList();
    }

    public boolean isSearchSupported() {
        return false;
    }
}