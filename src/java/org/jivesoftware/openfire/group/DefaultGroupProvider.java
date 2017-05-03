/*
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

package org.jivesoftware.openfire.group;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

/**
 * Database implementation of the GroupManager interface.
 *
 * @author Matt Tucker
 */
public class DefaultGroupProvider extends AbstractGroupProvider {

	private static final Logger Log = LoggerFactory.getLogger(DefaultGroupProvider.class);

    private static final String INSERT_GROUP =
        "INSERT INTO ofGroup (groupName, description) VALUES (?, ?)";
    private static final String SAVE_GROUP =
        "UPDATE ofGroup SET description=? WHERE groupName=?";
    private static final String SET_GROUP_NAME_1 =
        "UPDATE ofGroup SET groupName=? WHERE groupName=?";
    private static final String SET_GROUP_NAME_2 =
        "UPDATE ofGroupProp SET groupName=? WHERE groupName=?";
    private static final String SET_GROUP_NAME_3 =
        "UPDATE ofGroupUser SET groupName=? WHERE groupName=?";
    private static final String DELETE_GROUP_USERS =
        "DELETE FROM ofGroupUser WHERE groupName=?";
    private static final String DELETE_PROPERTIES =
        "DELETE FROM ofGroupProp WHERE groupName=?";
    private static final String DELETE_GROUP =
        "DELETE FROM ofGroup WHERE groupName=?";
    private static final String GROUP_COUNT = "SELECT count(*) FROM ofGroup";
     private static final String LOAD_ADMINS =
        "SELECT username FROM ofGroupUser WHERE administrator=1 AND groupName=? ORDER BY username";
    private static final String LOAD_MEMBERS =
        "SELECT username FROM ofGroupUser WHERE administrator=0 AND groupName=? ORDER BY username";
    private static final String LOAD_GROUP =
        "SELECT description FROM ofGroup WHERE groupName=?";
    private static final String REMOVE_USER =
        "DELETE FROM ofGroupUser WHERE groupName=? AND username=?";
    private static final String ADD_USER =
        "INSERT INTO ofGroupUser (groupName, username, administrator) VALUES (?, ?, ?)";
    private static final String UPDATE_USER =
        "UPDATE ofGroupUser SET administrator=? WHERE groupName=? AND username=?";
    private static final String USER_GROUPS =
        "SELECT groupName FROM ofGroupUser WHERE username=?";
    private static final String ALL_GROUPS = "SELECT groupName FROM ofGroup ORDER BY groupName";
    private static final String SEARCH_GROUP_NAME = "SELECT groupName FROM ofGroup WHERE groupName LIKE ? ORDER BY groupName";

    private XMPPServer server = XMPPServer.getInstance();

    @Override
    public Group createGroup(String name) {
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
            Log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        Collection<JID> members = getMembers(name, false);
        Collection<JID> administrators = getMembers(name, true);
        return new Group(name, "", members, administrators);
    }

    @Override
    public Group getGroup(String name) throws GroupNotFoundException {
        String description = null;

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_GROUP);
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

    @Override
    public void setDescription(String name, String description) throws GroupNotFoundException {
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
            Log.error(e.getMessage(), e);
            throw new GroupNotFoundException();
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    @Override
    public void setName(String oldName, String newName) throws GroupAlreadyExistsException
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
            DbConnectionManager.fastcloseStmt(pstmt);
            
            pstmt = con.prepareStatement(SET_GROUP_NAME_2);
            pstmt.setString(1, newName);
            pstmt.setString(2, oldName);
            pstmt.executeUpdate();
            DbConnectionManager.fastcloseStmt(pstmt);
            
            pstmt = con.prepareStatement(SET_GROUP_NAME_3);
            pstmt.setString(1, newName);
            pstmt.setString(2, oldName);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e.getMessage(), e);
            abortTransaction = true;
        }
        finally {
            DbConnectionManager.closeStatement(pstmt);
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
        }
    }

    @Override
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
            DbConnectionManager.fastcloseStmt(pstmt);
            
            // Remove all properties of the group.
            pstmt = con.prepareStatement(DELETE_PROPERTIES);
            pstmt.setString(1, groupName);
            pstmt.executeUpdate();
            DbConnectionManager.fastcloseStmt(pstmt);
            
            // Remove the group entry.
            pstmt = con.prepareStatement(DELETE_GROUP);
            pstmt.setString(1, groupName);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e.getMessage(), e);
            abortTransaction = true;
        }
        finally {
            DbConnectionManager.closeStatement(pstmt);
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
        }
    }

    @Override
    public int getGroupCount() {
        int count = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GROUP_COUNT);
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
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ALL_GROUPS);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                groupNames.add(rs.getString(1));
            }
        }
        catch (SQLException e) {
            Log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);       }
        return groupNames;
    }

    @Override
    public Collection<String> getGroupNames(int startIndex, int numResults) {
        List<String> groupNames = new ArrayList<>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = DbConnectionManager.createScrollablePreparedStatement(con, ALL_GROUPS);
            rs = pstmt.executeQuery();
            DbConnectionManager.scrollResultSet(rs, startIndex);
            int count = 0;
            while (rs.next() && count < numResults) {
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
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(USER_GROUPS);
            pstmt.setString(1, server.isLocal(user) ? user.getNode() : user.toString());
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
    public void addMember(String groupName, JID user, boolean administrator) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ADD_USER);
            pstmt.setString(1, groupName);
            pstmt.setString(2, server.isLocal(user) ? user.getNode() : user.toString());
            pstmt.setInt(3, administrator ? 1 : 0);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    @Override
    public void updateMember(String groupName, JID user, boolean administrator) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_USER);
            pstmt.setInt(1, administrator ? 1 : 0);
            pstmt.setString(2, groupName);
            pstmt.setString(3, server.isLocal(user) ? user.getNode() : user.toString());
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    @Override
    public void deleteMember(String groupName, JID user) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(REMOVE_USER);
            pstmt.setString(1, groupName);
            pstmt.setString(2, server.isLocal(user) ? user.getNode() : user.toString());
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public Collection<String> search(String query) {
        return search(query, 0, Integer.MAX_VALUE);
    }

    @Override
    public Collection<String> search(String query, int startIndex, int numResults) {
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

        List<String> groupNames = new ArrayList<>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            if ((startIndex==0) && (numResults==Integer.MAX_VALUE))
            {
               pstmt = con.prepareStatement(SEARCH_GROUP_NAME);
               pstmt.setString(1, query);
               rs = pstmt.executeQuery();
               while (rs.next()) {
                   groupNames.add(rs.getString(1));
               }
            } else {
               pstmt = DbConnectionManager.createScrollablePreparedStatement(con, SEARCH_GROUP_NAME);
               DbConnectionManager.limitRowsAndFetchSize(pstmt, startIndex, numResults);
               pstmt.setString(1, query);
               rs = pstmt.executeQuery();
               // Scroll to the start index.
               DbConnectionManager.scrollResultSet(rs, startIndex);
               int count = 0;
               while (rs.next() && count < numResults) {
                   groupNames.add(rs.getString(1));
                   count++;
               }	
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
    public boolean isSearchSupported() {
        return true;
    }

    @Override
    public boolean isSharingSupported() {
        return true;
    }

    private Collection<JID> getMembers(String groupName, boolean adminsOnly) {
        List<JID> members = new ArrayList<>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            if (adminsOnly) {
                pstmt = con.prepareStatement(LOAD_ADMINS);
            }
            else {
                pstmt = con.prepareStatement(LOAD_MEMBERS);
            }
            pstmt.setString(1, groupName);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                String user = rs.getString(1);
                JID userJID = null;
                if (user.indexOf('@') == -1) {
                    // Create JID of local user if JID does not match a component's JID
                    if (!server.matchesComponent(userJID)) {
                        userJID = server.createJID(user, null);
                    }
                }
                else {
                    userJID = new JID(user);
                }
                members.add(userJID);
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

}