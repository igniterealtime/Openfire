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

package org.jivesoftware.messenger.auth.spi;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.database.SequenceManager;
import org.jivesoftware.util.*;
import org.jivesoftware.messenger.auth.*;
import org.jivesoftware.messenger.user.UserAlreadyExistsException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;

/**
 * Database implementation of the GroupManager interface.
 *
 * @author Iain Shigeoka
 */
public class DbGroupProvider implements GroupProvider {

    private static final String INSERT_GROUP =
        "INSERT INTO jiveGroup " +
        "(name, description, groupID, creationDate, modificationDate) " +
        "VALUES (?, ?, ?, ?, ?)";
    private static final String LOAD_PROPERTIES =
        "SELECT name, propValue FROM jiveGroupProp WHERE groupID=?";
    private static final String LOAD_GROUP_BY_ID =
        "SELECT name, description, groupID, creationDate, modificationDate " +
        "FROM jiveGroup WHERE groupID=?";
    private static final String DELETE_PROPERTY =
        "DELETE FROM jiveGroupProp WHERE groupID=? AND name=?";
    private static final String SAVE_GROUP =
        "UPDATE jiveGroup SET name=?, description=?, creationDate=?, modificationDate=? " +
        "WHERE groupID=?";
    private static final String DELETE_GROUP_USERS =
        "DELETE FROM jiveGroupUser WHERE groupID=?";
    private static final String DELETE_GROUP =
        "DELETE FROM jiveGroup WHERE groupID=?";
    private static final String GROUP_COUNT = "SELECT count(*) FROM jiveGroup";
    private static final String UPDATE_PROPERTY =
        "UPDATE jiveGroupProp SET propValue=? WHERE name=? AND groupID=?";
    private static final String INSERT_PROPERTY =
        "INSERT INTO jiveGroupProp (groupID, name, propValue) VALUES (?, ?, ?)";
     private static final String MEMBER_TEST =
        "SELECT username FROM jiveGroupUser " +
        "WHERE groupID=? AND username=? AND administrator=0";
    private static final String ADMIN_TEST =
        "SELECT username FROM jiveGroupUser " +
        "WHERE groupID=? AND username=? AND administrator=1";
     private static final String LOAD_ADMINS =
        "SELECT username FROM jiveGroupUser WHERE administrator=1 AND groupID=?";
    private static final String LOAD_MEMBERS =
        "SELECT username FROM jiveGroupUser WHERE administrator=0 AND groupID=?";
    private static final String GROUP_MEMBER_COUNT =
        "SELECT count(*) FROM jiveGroupUser WHERE administrator=0";
    private static final String GROUP_ADMIN_COUNT =
        "SELECT count(*) FROM jiveGroupUser WHERE administrator=1";
    private static final String SELECT_GROUP_BY_NAME =
        "SELECT name, description, groupID, creationDate, modificationDate " +
        "FROM jiveGroup WHERE name=?";
    private static final String REMOVE_USER =
        "DELETE FROM jiveGroupUser WHERE groupID=? AND username=?";
    private static final String UPDATE_USER =
        "UPDATE jiveGroupUser SET administrator=? WHERE  groupID=? username=?";
    private static final String ADD_USER =
        "INSERT INTO jiveGroupUser (groupID, username, administrator) VALUES (?, ?, ?)";
    private static final String USER_GROUPS =
        "SELECT groupID FROM jiveGroupUser WHERE username=? AND administrator=0";
    private static final String ALL_GROUPS = "SELECT groupID FROM jiveGroup";

    public Group createGroup(String name) throws UnauthorizedException,
            GroupAlreadyExistsException
    {
        long now = System.currentTimeMillis();
        Date nowDate = new Date(now);
        long id = SequenceManager.nextID(JiveConstants.GROUP);
        Group group = null;

        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(INSERT_GROUP);
            pstmt.setString(1, name);
            pstmt.setString(2, "None");
            pstmt.setLong(3, id);
            pstmt.setString(4, StringUtils.dateToMillis(nowDate));
            pstmt.setString(5, StringUtils.dateToMillis(nowDate));
            pstmt.executeUpdate();
            group = new GroupImpl(id, name, null, nowDate, nowDate);
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
        if (group == null) {
            throw new GroupAlreadyExistsException(name);
        }
        return group;
    }

    public Group getGroup(long groupID) throws GroupNotFoundException {
        Group group = null;
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_GROUP_BY_ID);
            pstmt.setLong(1, groupID);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                group = new GroupImpl(groupID,
                        rs.getString(1),
                        rs.getString(2),
                        new java.util.Date(Long.parseLong(rs.getString(4).trim())),
                        new java.util.Date(Long.parseLong(rs.getString(5).trim())));
                // Load any extended properties.
                pstmt = con.prepareStatement(LOAD_PROPERTIES);
                pstmt.setLong(1, groupID);
                rs = pstmt.executeQuery();
                while (rs.next()) {
                    // Add in name, value as a new property.
                    group.setProperty(rs.getString(1), rs.getString(2));
                }
                rs.close();
            }
        }
        catch (SQLException e) {
            Log.error(e);
            throw new GroupNotFoundException();
        }
        catch (NumberFormatException nfe) {
            Log.error("WARNING: There was an error parsing the dates " +
                    "returned from the database. Ensure that they're being stored " +
                    "correctly.");
            throw new GroupNotFoundException("Group with id "
                    + groupID + " could not be loaded from the database.");
        }
        catch (UnauthorizedException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
        if (group == null) {
            throw new GroupNotFoundException("Group with ID "
                    + groupID + " not found.");
        }
        return group;
    }

    public Group getGroup(String name) throws GroupNotFoundException {
        Group group = null;
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SELECT_GROUP_BY_NAME);
            pstmt.setString(1, name);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                group = new GroupImpl(rs.getLong(3), rs.getString(1), rs.getString(2),
                        new java.util.Date(Long.parseLong(rs.getString(4).trim())),
                        new java.util.Date(Long.parseLong(rs.getString(5).trim())));
                // Load any extended properties.
                pstmt = con.prepareStatement(LOAD_PROPERTIES);
                pstmt.setLong(1, group.getID());
                rs = pstmt.executeQuery();
                while (rs.next()) {
                    // Add in name, value as a new property.
                    group.setProperty(rs.getString(1), rs.getString(2));
                }
                rs.close();
            }
        }
        catch (SQLException e) {
            Log.error(e);
        }
        catch (UnauthorizedException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
        if (group == null) {
            throw new GroupNotFoundException("Group with name "
                    + name + " not found.");
        }
        return group;
    }

    public void updateGroup(Group group) throws UnauthorizedException, GroupNotFoundException {
        group.setModificationDate(new Date());
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SAVE_GROUP);
            pstmt.setString(1, group.getName());
            pstmt.setString(2, group.getDescription());
            pstmt.setString(3, StringUtils.dateToMillis(group.getCreationDate()));
            pstmt.setString(4, StringUtils.dateToMillis(group.getModificationDate()));
            pstmt.setLong(5, group.getID());
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

    public void deleteGroup(long groupID) throws UnauthorizedException {
        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();
            // Remove all users in the group.
            pstmt = con.prepareStatement(DELETE_GROUP_USERS);
            pstmt.setLong(1, groupID);
            pstmt.executeUpdate();
            pstmt.close();
            // Remove the group entry.
            pstmt = con.prepareStatement(DELETE_GROUP);
            pstmt.setLong(1, groupID);
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
        // Removing a group can change the permissions of all the users in that
        // group. Therefore, expire permissions cache.
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

    public LongList getGroups() {
        LongList groups = new LongList();
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ALL_GROUPS);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                groups.add(rs.getLong(1));
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
        return groups;
    }

    public LongList getGroups(BasicResultFilter filter) {
        LongList groups = new LongList();
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ALL_GROUPS);
            ResultSet rs = pstmt.executeQuery();
            // Move to start of index
            for (int i = 0; i < filter.getStartIndex(); i++) {
                if (!rs.next()) {
                    break;
                }
            }
            // Now read in desired number of results
            for (int i = 0; i < filter.getNumResults(); i++) {
                if (rs.next()) {
                    groups.add(rs.getLong(1));
                }
                else {
                    break;
                }
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
        return groups;
    }

    public LongList getGroups(String username) {
        LongList groups = new LongList();
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(USER_GROUPS);
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                groups.add(rs.getLong(1));
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
        return groups;
    }

    public void createMember(long groupID, String username, boolean administrator)
            throws UserAlreadyExistsException
    {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ADD_USER);
            pstmt.setLong(1, groupID);
            pstmt.setString(2, username);
            pstmt.setInt(3, administrator ? 1 : 0);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e);
            throw new UserAlreadyExistsException(e);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
    }

    public void updateMember(long groupID, String username, boolean administrator) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_USER);
            pstmt.setInt(1, administrator ? 1 : 0);
            pstmt.setLong(2, groupID);
            pstmt.setString(3, username);
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

    public void deleteMember(long groupID, String username) {
        Connection con = null;
        PreparedStatement pstmt = null;

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(REMOVE_USER);
            pstmt.setLong(1, groupID);
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

    public int getMemberCount(long groupID, boolean adminsOnly) {
        int count = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            if (adminsOnly) {
                pstmt = con.prepareStatement(GROUP_MEMBER_COUNT);
            }
            else {
                pstmt = con.prepareStatement(GROUP_ADMIN_COUNT);
            }
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

    public String [] getMembers(long groupID, boolean adminsOnly) {
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
            pstmt.setLong(1, groupID);
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
        return (String [])members.toArray();
    }

    public String [] getMembers(long groupID, BasicResultFilter filter, boolean adminsOnly) {
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
            pstmt.setLong(1, groupID);
            ResultSet rs = pstmt.executeQuery();
            // Move to start of index
            for (int i = 0; i < filter.getStartIndex(); i++) {
                if (!rs.next()) {
                    break;
                }
            }
            // Now read in desired number of results
            for (int i = 0; i < filter.getNumResults(); i++) {
                if (rs.next()) {
                    members.add(rs.getString(1));
                }
                else {
                    break;
                }
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
        return (String [])members.toArray();
    }

    public boolean isMember(long groupID, String username, boolean adminsOnly) {
        boolean member = false;
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            if (adminsOnly) {
                pstmt = con.prepareStatement(ADMIN_TEST);
            }
            else {
                pstmt = con.prepareStatement(MEMBER_TEST);
            }
            pstmt.setLong(1, groupID);
            pstmt.setString(2, username);
            ResultSet rs = pstmt.executeQuery();
            // If there is a result, then the user is a member of the group.
            member = rs.next();
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
        return member;
    }

    public void createProperty(long groupID, String name, String value) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(INSERT_PROPERTY);
            pstmt.setLong(1, groupID);
            pstmt.setString(2, name);
            pstmt.setString(3, value);
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

    public void updateProperty(long groupID, String name, String value) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_PROPERTY);
            pstmt.setString(1, value);
            pstmt.setString(2, name);
            pstmt.setLong(3, groupID);
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

    public void deleteProperty(long groupID, String name) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_PROPERTY);
            pstmt.setLong(1, groupID);
            pstmt.setString(2, name);
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
}