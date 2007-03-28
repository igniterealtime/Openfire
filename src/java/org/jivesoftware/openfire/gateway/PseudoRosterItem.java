/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006-2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.gateway;

import org.jivesoftware.util.Log;
import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.database.DbConnectionManager;

import java.sql.*;

/**
 * Representation of a pseudo roster item.
 *
 * This is used for tracking information about a contact that we can't track on the legacy system itself.
 *
 * @author Daniel Henninger
 */
public class PseudoRosterItem {

    private static final String INSERT_ROSTER_ITEM =
            "INSERT INTO gatewayPseudoRoster(registrationID, username, nickname, groups) VALUES (?,?,?,?)";
    private static final String LOAD_ROSTER_ITEM =
            "SELECT nickname, groups FROM gatewayPseudoRoster WHERE registrationID=? AND username=?";
    private static final String CHANGE_USERNAME =
            "UPDATE gatewayPseudoRoster SET username=? WHERE registrationID=? AND username=?";
    private static final String SET_NICKNAME =
            "UPDATE gatewayPseudoRoster SET nickname=? WHERE registrationID=? AND username=?";
    private static final String SET_GROUPS =
            "UPDATE gatewayPseudoRoster SET groups=? WHERE registrationID=? AND username=?";
    private static final String REMOVE_ROSTER_ITEM =
            "DELETE FROM gatewayPseudoRoster WHERE registrationID=? AND username=?";

    private long registrationID;
    private String username;
    private String nickname;
    private String groups;

    /**
     * Creates a new roster item associated with a registration id.
     *
     * @param registrationID Id of the registration this roster item is associated with.
     * @param username The username of the roster item.
     * @param nickname The nickname associated with the roster item (may be null).
     * @param groups The group list associated wit the roster item (may be null).
     */
    public PseudoRosterItem(Long registrationID, String username, String nickname, String groups) {
        if (registrationID == null || username == null) {
            throw new NullPointerException("Arguments cannot be null.");
        }
        this.registrationID = registrationID;
        this.username = username;
        this.nickname = nickname;
        this.groups = groups;
        try {
            // Clean up potentially already existing item.
            removeFromDb();
            try {
                // Insert new roster item.
                insertIntoDb();
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
    }

    /**
     * Loads an existing roster item.
     *
     * @param registrationID The ID of the registration the roster item is assocaited with.
     * @param username The username of the roster item.
     * @throws org.jivesoftware.util.NotFoundException if the registration could not be loaded.
     */
    public PseudoRosterItem(long registrationID, String username)
            throws NotFoundException
    {
        this.registrationID = registrationID;
        this.username = username;
        loadFromDb();
    }

    /**
     * Returns the unique ID of the registration associated with the roster item.
     *
     * @return the registration ID.
     */
    public long getRegistrationID() {
        return registrationID;
    }

    /**
     * Returns the username associated with the roster item.
     *
     * @return the username.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the nickname associated with the roster item.
     *
     * @return the nickname.
     */
    public String getNickname() {
        return nickname;
    }

    /**
     * Returns the groups associated with the roster item.
     *
     * @return the groups.
     */
    public String getGroups() {
        return groups;
    }

    /**
     * Changes the username of the roster item.
     * @param username New username.
     */
    public void changeUsername(String username) {
        if (username == null) {
            throw new NullPointerException("Arguments cannot be null.");
        }
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(CHANGE_USERNAME);
            pstmt.setString(1, username);
            pstmt.setLong(2, registrationID);
            pstmt.setString(3, this.username);
            pstmt.executeUpdate();
            this.username = username;
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /**
     * Sets the nickname for the roster item.
     * @param nickname New nickname to be associated.
     */
    public void setNickname(String nickname) {
        this.nickname = nickname;
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SET_NICKNAME);
            if (nickname != null) {
                pstmt.setString(1, nickname);
            }
            else {
                pstmt.setNull(1, Types.VARCHAR);
            }
            pstmt.setLong(2, registrationID);
            pstmt.setString(3, this.username);
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /**
     * Sets the groups for the roster item.
     * @param groups New group list to be associated.
     */
    public void setGroups(String groups) {
        this.groups = groups;
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SET_GROUPS);
            if (groups != null) {
                pstmt.setString(1, groups);
            }
            else {
                pstmt.setNull(1, Types.VARCHAR);
            }
            pstmt.setLong(2, registrationID);
            pstmt.setString(3, this.username);
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }


    public String toString() {
        return username + ", " + nickname + ", " + groups;
    }

    /**
     * Inserts a new roster item into the database.
     *
     * @throws SQLException if the SQL statement is wrong for whatever reason.
     */
    private void insertIntoDb() throws SQLException {
        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();
            pstmt = con.prepareStatement(INSERT_ROSTER_ITEM);
            pstmt.setLong(1, registrationID);
            pstmt.setString(2, username);
            if (nickname != null) {
                pstmt.setString(3, nickname);
            }
            else {
                pstmt.setNull(3, Types.VARCHAR);
            }
            if (groups != null) {
                pstmt.setString(4, groups);
            }
            else {
                pstmt.setNull(4, Types.VARCHAR);
            }
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            abortTransaction = true;
            throw sqle;
        }
        finally {
            DbConnectionManager.closeTransactionConnection(pstmt, con, abortTransaction);
        }
    }

    /**
     * Removeds a roster item from the database.
     *
     * @throws SQLException if the SQL statement is wrong for whatever reason.
     */
    private void removeFromDb() throws SQLException {
        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();
            pstmt = con.prepareStatement(REMOVE_ROSTER_ITEM);
            pstmt.setLong(1, registrationID);
            pstmt.setString(2, username);
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            abortTransaction = true;
            throw sqle;
        }
        finally {
            DbConnectionManager.closeTransactionConnection(pstmt, con, abortTransaction);
        }
    }

    private void loadFromDb() throws NotFoundException {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_ROSTER_ITEM);
            pstmt.setLong(1, registrationID);
            pstmt.setString(2, username);
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new NotFoundException("Pseudo roster item not found: " + registrationID + "/" + username);
            }
            this.nickname = rs.getString(1);
            this.groups = rs.getString(2);
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }
    
}
