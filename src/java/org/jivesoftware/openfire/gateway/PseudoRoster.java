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
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/**
 * Representation of an entire roster associated with a registration id.
 *
 * @author Daniel Henninger
 */
public class PseudoRoster {

    private static final String GET_ALL_USER_ROSTER_ITEMS =
            "SELECT username FROM gatewayPseudoRoster WHERE registrationID=?";
    private static final String REMOVE_ROSTER_ITEM =
            "DELETE FROM gatewayPseudoRoster WHERE registrationID=? AND username=?";

    private long registrationID;
    private ConcurrentHashMap<String,PseudoRosterItem> pseudoRosterItems = new ConcurrentHashMap<String,PseudoRosterItem>();

    /**
     * Loads an existing pseudo roster.
     *
     * @param registrationID The ID of the registration the roster item is assocaited with.
     */
    public PseudoRoster(long registrationID) {
        this.registrationID = registrationID;
        loadFromDb();
    }

    /**
     * Returns the unique ID of the registration associated with the roster.
     *
     * @return the registration ID.
     */
    public long getRegistrationID() {
        return registrationID;
    }

    /**
     * Returns the list of roster items associated with this registration ID.
     *
     * @return Map of roster item usernames to PseudoRosterItems.
     */
    public ConcurrentHashMap<String,PseudoRosterItem> getRosterItems() {
        return pseudoRosterItems;
    }

    /**
     * Returns a set of just the usernames of contacts from this roster.
     *
     * @return Set of usernames.
     */
    public Set<String> getContacts() {
        return pseudoRosterItems.keySet();
    }

    /**
     * Returns true or false if a pseudo roster item exists for a username.
     *
     * @param username Username to locate.
     * @return Whether a roster item exists with the username.
     */
    public Boolean hasItem(String username) {
        return pseudoRosterItems.containsKey(username);
    }

    /**
     * Retrieves a pseudo roster item for a username.
     *
     * @param username Username to locate.
     * @return A PseudoRosterItem for the user specified.
     */
    public PseudoRosterItem getItem(String username) {
        return pseudoRosterItems.get(username);
    }

    /**
     * Removes a pseudo roster item for a username.
     *
     * @param username Username to remove.
     */
    public void removeItem(String username) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(REMOVE_ROSTER_ITEM);
            pstmt.setLong(1, registrationID);
            pstmt.setString(2, username);
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
     * Creates a new pseudo roster item for a username, nickname, and list of groups.
     *
     * @param username Username to add.
     * @param nickname Nickname for roster item.
     * @param groups List of groups for roster item.
     */
    public PseudoRosterItem createItem(String username, String nickname, String groups) {
        PseudoRosterItem rosterItem = new PseudoRosterItem(registrationID, username, nickname, groups);
        pseudoRosterItems.put(username, rosterItem);
        return rosterItem;
    }

    /**
     * Load pseudo roster from database.
     */
    private void loadFromDb() {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GET_ALL_USER_ROSTER_ITEMS);
            pstmt.setLong(1, registrationID);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                String username = rs.getString(1);
                try {
                    pseudoRosterItems.put(username, new PseudoRosterItem(registrationID, username));
                }
                catch (NotFoundException e) {
                    Log.error("Could not find pseudo roster item after already having found it.", e);
                }
            }
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

}
