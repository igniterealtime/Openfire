/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.group;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;

import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.*;

/**
 * The JDBC group provider allows you to use an external database to define the make up of groups.
 * It is best used with the JDBCAuthProvider to provide integration between your external system and
 * Wildfire.  All data is treated as read-only so any set operations will result in an exception.
 *
 * To enable this provider, set the following in the XML configuration file:
 *
 * <pre>
 * &lt;provider&gt;
 *     &lt;group&gt;
 *         &lt;className&gt;org.jivesoftware.wildfire.group.JDBCGroupProvider&lt;/className&gt;
 *     &lt;/group&gt;
 * &lt;/provider&gt;
 * </pre>
 *
 * Then you need to set your driver, connection string and SQL statements:
 *
 * <pre>
 * &lt;jdbcGroupProvider&gt;
 *      &lt;jdbcDriver&gt;
 *          &lt;className&gt;com.mysql.jdbc.Driver&lt;/className&gt;
 *      &lt;/jdbcDrivec&gt;
 *      &lt;jdbcConnString&gt;jdbc:mysql:://localhost/dbname?user=username&amp;amp;password=secret&lt;/jdbcConnString&gt;
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

    private String jdbcConnString;

    private String groupCountSQL;
    private String descriptionSQL;
    private String allGroupsSQL;
    private String userGroupsSQL;
    private String loadMembersSQL;
    private String loadAdminsSQL;

    /**
     * Constructor of the JDBCGroupProvider class.
     */
    public JDBCGroupProvider() {
        // Load the JDBC driver
        String jdbcDriver = JiveGlobals.getXMLProperty("jdbcGroupProvider.jdbcDriver.className");
        try {
            Class.forName(jdbcDriver).newInstance();
        }
        catch (Exception e) {
            Log.error("Unable to load JDBC driver: " + jdbcDriver, e);
            return;
        }

        // grab our conn string and SQL statements
        jdbcConnString = JiveGlobals.getXMLProperty("jdbcGroupProvider.jdbcConnString");
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

        Connection conn = null;
        PreparedStatement pstmt = null;
        try {
            conn = DriverManager.getConnection(jdbcConnString);
            pstmt = conn.prepareStatement(descriptionSQL);
            pstmt.setString(1, name);
            ResultSet rs = pstmt.executeQuery();
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
        Collection<JID> members = getMembers(name, false);
        Collection<JID> administrators = getMembers(name, true);
        return new Group(name, description, members, administrators);
    }

    private Collection<JID> getMembers(String groupName, boolean adminsOnly) {
        List<JID> members = new ArrayList<JID>();
        Connection conn = null;
        PreparedStatement pstmt = null;
        try {
            conn = DriverManager.getConnection(jdbcConnString);
            if (adminsOnly) {
                if (loadAdminsSQL == null) {
                    return members;
                }
                pstmt = conn.prepareStatement(loadAdminsSQL);
            }
            else {
                pstmt = conn.prepareStatement(loadMembersSQL);
            }

            pstmt.setString(1, groupName);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String user = rs.getString(1);
                if (user != null) {
                    JID userJID = new JID(user);
                    members.add(userJID);
                }
            }
            rs.close();
        }
        catch (SQLException e) {
            Log.error(e);
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
        Connection conn = null;
        PreparedStatement pstmt = null;
        try {
            conn = DriverManager.getConnection(jdbcConnString);
            pstmt = conn.prepareStatement(groupCountSQL);
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
        return count;
    }

    public Collection<Group> getGroups() {
        List<String> groupNames = new ArrayList<String>();
        Connection conn = null;
        PreparedStatement pstmt = null;
        try {
            conn = DriverManager.getConnection(jdbcConnString);
            pstmt = conn.prepareStatement(allGroupsSQL);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                groupNames.add(rs.getString(1));
            }
            rs.close();
        }
        catch (SQLException e) {
            Log.error(e);
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
        List<Group> groups = new ArrayList<Group>(groupNames.size());
        for (String groupName : groupNames) {
            try {
                groups.add(getGroup(groupName));
            }
            catch (GroupNotFoundException e) {
                Log.error(e);
            }
        }
        return groups;
    }

    public Collection<Group> getGroups(Set<String> groupNames) {
        List<Group> groups = new ArrayList<Group>(groupNames.size());
        for (String groupName : groupNames) {
            try {
                groups.add(getGroup(groupName));
            }
            catch (GroupNotFoundException e) {
                Log.error(e);
            }
        }
        return groups;
    }

    public Collection<Group> getGroups(int start, int num) {
        List<String> groupNames = new ArrayList<String>();
        Connection conn = null;
        PreparedStatement pstmt = null;
        try {
            conn = DriverManager.getConnection(jdbcConnString);
            pstmt = DbConnectionManager.createScrollablePreparedStatement(conn, allGroupsSQL);
            ResultSet rs = pstmt.executeQuery();
            DbConnectionManager.scrollResultSet(rs, start);
            int count = 0;
            while (rs.next() && count < num) {
                groupNames.add(rs.getString(1));
                count++;
            }
            rs.close();
        }
        catch (SQLException e) {
            Log.error(e);
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
        List<Group> groups = new ArrayList<Group>(groupNames.size());
        for (String groupName : groupNames) {
            try {
                groups.add(getGroup(groupName));
            }
            catch (GroupNotFoundException e) {
                Log.error(e);
            }
        }
        return groups;
    }

    public Collection<Group> getGroups(JID user) {
        List<String> groupNames = new ArrayList<String>();
        Connection conn = null;
        PreparedStatement pstmt = null;
        try {
            conn = DriverManager.getConnection(jdbcConnString);
            pstmt = conn.prepareStatement(userGroupsSQL);
            pstmt.setString(1, user.toString());
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                groupNames.add(rs.getString(1));
            }
            rs.close();
        }
        catch (SQLException e) {
            Log.error(e);
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
        List<Group> groups = new ArrayList<Group>(groupNames.size());
        for (String groupName : groupNames) {
            try {
                groups.add(getGroup(groupName));
            }
            catch (GroupNotFoundException e) {
                Log.error(e);
            }
        }
        return groups;
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
            throws UnsupportedOperationException {
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
            throws UnsupportedOperationException {
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
            throws UnsupportedOperationException {
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
}