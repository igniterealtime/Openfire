/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.group;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.*;
import org.jivesoftware.messenger.user.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Collection;

/**
 * Database implementation of the GroupManager interface.
 *
 * @author Iain Shigeoka
 */
public class DefaultGroupProvider implements GroupProvider {

    private static final String INSERT_GROUP =
        "INSERT INTO jiveGroup (groupName, description) VALUES (?, ?)";
    private static final String SAVE_GROUP =
        "UPDATE jiveGroup SET description=? WHERE groupName=?";
    private static final String SET_GROUP_NAME_1 =
        "UPDATE jiveGroup SET groupName=? WHERE groupName=?";
    private static final String SET_GROUP_NAME_2 =
        "UPDATE jiveGroupProp SET groupName=? WHERE groupName=?";
    private static final String SET_GROUP_NAME_3 =
        "UPDATE jiveGroupUser SET groupName=? WHERE groupName=?";
    private static final String DELETE_GROUP_USERS =
        "DELETE FROM jiveGroupUser WHERE groupName=?";
    private static final String DELETE_GROUP =
        "DELETE FROM jiveGroup WHERE groupName=?";
    private static final String GROUP_COUNT = "SELECT count(*) FROM jiveGroup";
     private static final String LOAD_ADMINS =
        "SELECT username FROM jiveGroupUser WHERE administrator=1 AND groupName=?";
    private static final String LOAD_MEMBERS =
        "SELECT username FROM jiveGroupUser WHERE administrator=0 AND groupName=?";
    private static final String SELECT_GROUP_BY_NAME =
        "SELECT description FROM jiveGroup WHERE groupName=?";
    private static final String REMOVE_USER =
        "DELETE FROM jiveGroupUser WHERE groupName=? AND username=?";
    private static final String ADD_USER =
        "INSERT INTO jiveGroupUser (groupName, username, administrator) VALUES (?, ?, ?)";
    private static final String USER_GROUPS =
        "SELECT groupName FROM jiveGroupUser WHERE username=?";
    private static final String ALL_GROUPS = "SELECT groupName FROM jiveGroup ORDER BY groupName";

    public Group createGroup(String name) throws GroupAlreadyExistsException {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(INSERT_GROUP);
            pstmt.setString(1, name);
            pstmt.setString(2, "");
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
        Collection<String> members = getMembers(name, false);
        Collection<String> administrators = getMembers(name, true);
        return new Group(this, name, "", members, administrators);
    }

    public Group getGroup(String name) throws GroupNotFoundException {
        String description = null;

        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SELECT_GROUP_BY_NAME);
            pstmt.setString(1, name);
            ResultSet rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new GroupNotFoundException("Group with name "
                    + name + " not found.");
            }
            if (rs.next()) {
                description = rs.getString(1);
            }
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
        Collection<String> members = getMembers(name, false);
        Collection<String> administrators = getMembers(name, true);
        return new Group(this, name, description, members, administrators);
    }

    public void setDescription(String name, String description)
            throws GroupNotFoundException
    {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SAVE_GROUP);
            pstmt.setString(1, description);
            pstmt.setString(2, name);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e);
            throw new GroupNotFoundException();
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
    }

    public void setName(String oldName, String newName) throws UnsupportedOperationException,
            GroupAlreadyExistsException
    {
        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();
            pstmt = con.prepareStatement(SET_GROUP_NAME_1);
            pstmt.setString(1, newName);
            pstmt.setString(2, oldName);
            pstmt.executeUpdate();
            pstmt.close();
            pstmt = con.prepareStatement(SET_GROUP_NAME_2);
            pstmt.setString(1, newName);
            pstmt.setString(2, oldName);
            pstmt.executeUpdate();
            pstmt.close();
            pstmt = con.prepareStatement(SET_GROUP_NAME_3);
            pstmt.setString(1, newName);
            pstmt.setString(2, oldName);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e);
            abortTransaction = true;
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
        }
    }

    public void deleteGroup(String groupName) {
        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();
            // Remove all users in the group.
            pstmt = con.prepareStatement(DELETE_GROUP_USERS);
            pstmt.setString(1, groupName);
            pstmt.executeUpdate();
            pstmt.close();
            // Remove the group entry.
            pstmt = con.prepareStatement(DELETE_GROUP);
            pstmt.setString(1, groupName);
            pstmt.executeUpdate();
            pstmt.close();
        }
        catch (SQLException e) {
            Log.error(e);
            abortTransaction = true;
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
        }
    }

    public int getGroupCount() {
        int count = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GROUP_COUNT);
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
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
        return count;
    }

    public Collection<Group> getGroups() {
        List<String> groupNames = new ArrayList<String>();
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ALL_GROUPS);
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
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
        List<Group> groups = new ArrayList<Group>(groupNames.size());
        GroupManager manager = GroupManager.getInstance();
        for (String groupName : groupNames) {
            try {
                groups.add(manager.getGroup(groupName));
            }
            catch (GroupNotFoundException e) {
                Log.error(e);
            }
        }
        return groups;
    }

    public Collection<Group> getGroups(int startIndex, int numResults) {
        List<String> groupNames = new ArrayList<String>();
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ALL_GROUPS);
            ResultSet rs = pstmt.executeQuery();
            DbConnectionManager.scrollResultSet(rs, startIndex);
            int count = 0;
            while (rs.next() && count < numResults) {
                groupNames.add(rs.getString(1));
                count++;
            }
            rs.close();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
        List<Group> groups = new ArrayList<Group>(groupNames.size());
        GroupManager manager = GroupManager.getInstance();
        for (String groupName : groupNames) {
            try {
                groups.add(manager.getGroup(groupName));
            }
            catch (GroupNotFoundException e) {
                Log.error(e);
            }
        }
        return groups;
    }

    public Collection<Group> getGroups(User user) {
        List<String> groupNames = new ArrayList<String>();
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(USER_GROUPS);
            pstmt.setString(1, user.getUsername());
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
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
        List<Group> groups = new ArrayList<Group>(groupNames.size());
        GroupManager manager = GroupManager.getInstance();
        for (String groupName : groupNames) {
            try {
                groups.add(manager.getGroup(groupName));
            }
            catch (GroupNotFoundException e) {
                Log.error(e);
            }
        }
        return groups;
    }

    public void addMember(String groupName, String username, boolean administrator) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ADD_USER);
            pstmt.setString(1, groupName);
            pstmt.setString(2, username);
            pstmt.setInt(3, administrator ? 1 : 0);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
    }

    public void deleteMember(String groupName, String username) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(REMOVE_USER);
            pstmt.setString(1, groupName);
            pstmt.setString(2, username);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
    }

    private Collection<String> getMembers(String groupName, boolean adminsOnly) {
        List<String> members = new ArrayList<String>();
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            if (adminsOnly) {
                pstmt = con.prepareStatement(LOAD_MEMBERS);
            }
            else {
                pstmt = con.prepareStatement(LOAD_ADMINS);
            }
            pstmt.setString(1, groupName);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                members.add(rs.getString(1));
            }
            rs.close();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
        return members;
    }
}