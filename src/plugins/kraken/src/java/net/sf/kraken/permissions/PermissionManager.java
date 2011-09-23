/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.permissions;

import net.sf.kraken.type.TransportType;

import org.xmpp.packet.JID;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.user.User;
import org.apache.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Registration Permissions Manager
 *
 * Handles who has access to a given transport, both for checking access and for
 * managing who is in the access list.  Should be used regardless of whether permissions
 * are set to "all" or "none" or not as this class checks for those settings on it's own.
 *
 * @author Daniel Henninger
 */
public class PermissionManager {

    static Logger Log = Logger.getLogger(PermissionManager.class);

    private static final String IS_USER_LISTED =
            "SELECT count(*) FROM ofGatewayRestrictions WHERE transportType=? AND username=?";
    private static final String GROUPS_LISTED =
            "SELECT groupname FROM ofGatewayRestrictions WHERE transportType=?";
    private static final String DELETE_ALL_USERS =
            "DELETE FROM ofGatewayRestrictions WHERE transportType=? AND username IS NOT NULL";
    private static final String DELETE_ALL_GROUPS =
            "DELETE FROM ofGatewayRestrictions WHERE transportType=? AND groupname IS NOT NULL";
    private static final String ADD_NEW_USER =
            "INSERT INTO ofGatewayRestrictions(transportType,username) VALUES(?,?)";
    private static final String ADD_NEW_GROUP =
            "INSERT INTO ofGatewayRestrictions(transportType,groupname) VALUES(?,?)";
    private static final String GET_ALL_USERS =
            "SELECT username FROM ofGatewayRestrictions WHERE transportType=? AND username IS NOT NULL ORDER BY username";
    private static final String GET_ALL_GROUPS =
            "SELECT groupname FROM ofGatewayRestrictions WHERE transportType=? AND groupname IS NOT NULL ORDER BY groupname";

    private TransportType transportType = null;

    /**
     * Create a permissionManager instance.
     *
     * @param type Type of the transport that this permission manager serves.
     */
    public PermissionManager(TransportType type) {
        this.transportType = type;
    }


    /**
     * Checks if a user has access to the transport, via a number of methods.
     *
     * @param jid JID of the user who may or may not have access.
     * @return True or false if the user has access.
     */
    public boolean hasAccess(JID jid) {
        int setting = JiveGlobals.getIntProperty("plugin.gateway."+transportType.toString()+".registration", 1);
        if (setting == 1) { return true; }
        if (setting == 3) { return false; }
        if (isUserAllowed(jid)) { return true; }
        if (isUserInAllowedGroup(jid)) { return true; }
        return false;
    }

    /**
     * Checks if a user has specific access to the transport.
     *
     * @param jid JID of the user who may or may not have access.
     * @return True or false of the user has access.
     */
    public boolean isUserAllowed(JID jid) {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(IS_USER_LISTED);
            pstmt.setString(1, transportType.toString());
            pstmt.setString(2, jid.getNode());
            rs = pstmt.executeQuery();
            rs.next();
            return (rs.getInt(1) > 0);
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return false;
    }

    /**
     * Checks if a user is in a group that has access to the transport.
     *
     * @param jid JID of the user who may or may not have access.
     * @return True or false of the user is in a group that has access.
     */
    public boolean isUserInAllowedGroup(JID jid) {
        ArrayList<String> allowedGroups = new ArrayList<String>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GROUPS_LISTED);
            pstmt.setString(1, transportType.toString());
            rs = pstmt.executeQuery();
            while (rs.next()) {
                allowedGroups.add(rs.getString(1));
            }
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        Collection<Group> userGroups = GroupManager.getInstance().getGroups(jid);
        for (Group g : userGroups) {
            if (allowedGroups.contains(g.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Stores a list of users as having access to the transport in question.
     *
     * @param users list of users who should have access.
     */
    public void storeUserList(ArrayList<User> users) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_ALL_USERS);
            pstmt.setString(1, transportType.toString());
            pstmt.executeUpdate();
            pstmt.close();

            pstmt = con.prepareStatement(ADD_NEW_USER);
            pstmt.setString(1, transportType.toString());
            for (User user : users) {
                pstmt.setString(2, user.getUsername());
                pstmt.executeUpdate();
            }
            pstmt.close();
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }


    /**
     * Stores a list of groups as having access to the transport in question.
     *
     * @param groups list of groups who should have access.
     */
    public void storeGroupList(ArrayList<Group> groups) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_ALL_GROUPS);
            pstmt.setString(1, transportType.toString());
            pstmt.executeUpdate();
            pstmt.close();

            pstmt = con.prepareStatement(ADD_NEW_GROUP);
            pstmt.setString(1, transportType.toString());
            for (Group group : groups) {
                pstmt.setString(2, group.getName());
                pstmt.executeUpdate();
            }
            pstmt.close();
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /**
     * Retrieves a list of all of the users permitted to access this transport.
     *
     * @return List of users (as strings)
     */
    public ArrayList<String> getAllUsers() {
        ArrayList<String> userList = new ArrayList<String>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GET_ALL_USERS);
            pstmt.setString(1, transportType.toString());
            rs = pstmt.executeQuery();
            while (rs.next()) {
                userList.add(rs.getString(1));
            }
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return userList;
    }

    /**
     * Retrieves a list of all of the groups permitted to access this transport.
     *
     * @return List of groups (as strings)
     */
    public ArrayList<String> getAllGroups() {
        ArrayList<String> groupList = new ArrayList<String>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GET_ALL_GROUPS);
            pstmt.setString(1, transportType.toString());
            rs = pstmt.executeQuery();
            while (rs.next()) {
                groupList.add(rs.getString(1));
            }
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return groupList;
    }

}
