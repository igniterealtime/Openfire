/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.auth.spi;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.database.SequenceManager;
import org.jivesoftware.util.*;
import org.jivesoftware.messenger.auth.*;
import org.jivesoftware.messenger.user.UserAlreadyExistsException;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.database.SequenceManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;

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

    public Group createGroup(String name)
            throws UnauthorizedException, GroupAlreadyExistsException {

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
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
            try {
                if (con != null) {
                    con.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
        if (group == null) {
            throw new GroupAlreadyExistsException(name);
        }
        return group;
    }

    private static final String LOAD_PROPERTIES =
            "SELECT name, propValue FROM jiveGroupProp WHERE groupID=?";
    private static final String LOAD_GROUP_BY_ID =
            "SELECT name, description, groupID, creationDate, modificationDate " +
            "FROM jiveGroup WHERE groupID=?";

    public Group getGroup(long groupID) throws GroupNotFoundException {
        Connection con = null;
        PreparedStatement pstmt = null;
        Group group = null;
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
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
            try {
                if (con != null) {
                    con.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
        if (group == null) {
            throw new GroupNotFoundException("Group with ID "
                    + groupID + " not found.");
        }
        return group;
    }

    private static final String SELECT_GROUP_BY_NAME =
            "SELECT name, description, groupID, creationDate, modificationDate " +
            "FROM jiveGroup WHERE name=?";

    public Group getGroup(String name) throws GroupNotFoundException {
        long id = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        Group group = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SELECT_GROUP_BY_NAME);
            pstmt.setString(1, name);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                group = new GroupImpl(rs.getLong(3),
                        rs.getString(1),
                        rs.getString(2),
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
            }
        }
        catch (SQLException e) {
            Log.error(e);
        }
        catch (UnauthorizedException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
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
                if (con != null) {
                    con.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
        if (group == null) {
            throw new GroupNotFoundException("Group with name "
                    + name + " not found.");
        }
        return group;
    }

    private static final String SAVE_GROUP =
            "UPDATE jiveGroup SET name=?, description=?, creationDate=?, modificationDate=? "
            + "WHERE groupID=?";

    public void updateGroup(Group group)
            throws UnauthorizedException, GroupNotFoundException {
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
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
            try {
                if (con != null) {
                    con.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
    }

    private static final String DELETE_GROUP_USERS =
            "DELETE FROM jiveGroupUser WHERE groupID=?";
    private static final String DELETE_GROUP =
            "DELETE FROM jiveGroup WHERE groupID=?";

    public void deleteGroup(long groupID) throws UnauthorizedException {
        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;

        try {
            con = DbConnectionManager.getTransactionConnection();
            // Remove all users in the group.
            pstmt = con.prepareStatement(DELETE_GROUP_USERS);
            pstmt.setLong(1, groupID);
            pstmt.execute();
            pstmt.close();
            // Remove the group entry.
            pstmt = con.prepareStatement(DELETE_GROUP);
            pstmt.setLong(1, groupID);
            pstmt.execute();
            pstmt.close();
        }
        catch (SQLException e) {
            Log.error(e);
            abortTransaction = true;
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
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
        }
        // Removing a group can change the permissions of all the users in that
        // group. Therefore, expire permissions cache.
    }

    private static final String GROUP_COUNT = "SELECT count(*) FROM jiveGroup";

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
                if (con != null) {
                    con.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
        return count;
    }

    private static final String ALL_GROUPS = "SELECT groupID FROM jiveGroup";

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
                if (con != null) {
                    con.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
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
                if (con != null) {
                    con.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
        return groups;
    }

    private static final String USER_GROUPS =
            "SELECT groupID FROM jiveGroupUser WHERE userID=? AND administrator=0";

    public LongList getGroups(long entityID) {
        Connection con = null;
        PreparedStatement pstmt = null;
        LongList groups = new LongList();

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(USER_GROUPS);
            pstmt.setLong(1, entityID);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                groups.add(rs.getLong(1));
            }
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
                if (con != null) {
                    con.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
        return groups;
    }

    private static final String ADD_USER =
            "INSERT INTO jiveGroupUser (groupID, userID, administrator) VALUES (?, ?, ?)";

    public void createMember(long groupID, long entityID, boolean administrator)
            throws UserAlreadyExistsException {
        Connection con = null;
        PreparedStatement pstmt = null;

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ADD_USER);
            pstmt.setLong(1, groupID);
            pstmt.setLong(2, entityID);
            pstmt.setInt(3, administrator ? 1 : 0);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e);
            throw new UserAlreadyExistsException(e);
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
                if (con != null) {
                    con.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
    }

    private static final String UPDATE_USER =
            "UPDATE jiveGroupUser SET administrator=? WHERE  groupID=? userID=?";

    public void updateMember(long groupID, long entityID, boolean administrator) {
        Connection con = null;
        PreparedStatement pstmt = null;

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_USER);
            pstmt.setInt(1, administrator ? 1 : 0);
            pstmt.setLong(2, groupID);
            pstmt.setLong(3, entityID);
            pstmt.executeUpdate();
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
                if (con != null) {
                    con.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
    }

    private static final String REMOVE_USER =
            "DELETE FROM jiveGroupUser WHERE groupID=? AND userID=?";

    public void deleteMember(long groupID, long entityID) {
        Connection con = null;
        PreparedStatement pstmt = null;

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(REMOVE_USER);
            pstmt.setLong(1, groupID);
            pstmt.setLong(2, entityID);
            pstmt.executeUpdate();
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
                if (con != null) {
                    con.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
    }

    private static final String GROUP_MEMBER_COUNT =
            "SELECT count(*) FROM jiveGroupUser WHERE administrator =0";
    private static final String GROUP_ADMIN_COUNT =
            "SELECT count(*) FROM jiveGroupUser WHERE administrator =1";

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
                if (con != null) {
                    con.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
        return count;
    }

    private static final String LOAD_ADMINS =
            "SELECT userID FROM jiveGroupUser WHERE administrator=1 AND groupID=?";
    private static final String LOAD_MEMBERS =
            "SELECT userID FROM jiveGroupUser WHERE administrator=0 AND groupID=?";

    public LongList getMembers(long groupID, boolean adminsOnly) {
        Connection con = null;
        PreparedStatement pstmt = null;
        LongList members = new LongList();
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
                members.add(rs.getLong(1));
            }
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
                if (con != null) {
                    con.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
        }

        return members;
    }

    public LongList getMembers(long groupID,
                               BasicResultFilter filter,
                               boolean adminsOnly) {
        Connection con = null;
        PreparedStatement pstmt = null;
        LongList members = new LongList();
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
                    members.add(rs.getLong(1));
                }
                else {
                    break;
                }
            }
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
                if (con != null) {
                    con.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
        }

        return members;
    }


    private static final String MEMBER_TEST =
            "SELECT userID FROM jiveGroupUser " +
            "WHERE groupID=? AND userID=? AND administrator=0";
    private static final String ADMIN_TEST =
            "SELECT userID FROM jiveGroupUser " +
            "WHERE groupID=? AND userID=? AND administrator=1";

    public boolean isMember(long groupID, long entityID, boolean adminsOnly) {
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
            pstmt.setLong(2, entityID);
            ResultSet rs = pstmt.executeQuery();
            // If there is a result, then the user is a member of the group.
            member = rs.next();
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
                if (con != null) {
                    con.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
        return member;
    }

    private static final String INSERT_PROPERTY =
            "INSERT INTO jiveGroupProp (groupID, name, propValue) VALUES (?, ?, ?)";

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
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
            try {
                if (con != null) {
                    con.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
    }

    private static final String UPDATE_PROPERTY =
            "UPDATE jiveGroupProp SET propValue=? WHERE name=? AND groupID=?";

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
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
            try {
                if (con != null) {
                    con.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
    }

    private static final String DELETE_PROPERTY =
            "DELETE FROM jiveGroupProp WHERE groupID=? AND name=?";

    public void deleteProperty(long groupID, String name) {
        Connection con = null;
        PreparedStatement pstmt = null;

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_PROPERTY);
            pstmt.setLong(1, groupID);
            pstmt.setString(2, name);
            pstmt.execute();
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
                if (con != null) {
                    con.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
    }
}
