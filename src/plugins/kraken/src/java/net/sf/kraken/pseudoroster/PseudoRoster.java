/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.pseudoroster;

import org.apache.log4j.Logger;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.NotFoundException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Representation of an entire roster associated with a registration id.
 *
 * @author Daniel Henninger
 */
public class PseudoRoster {

    static Logger Log = Logger.getLogger(PseudoRoster.class);

    private static final String GET_ALL_USER_ROSTER_ITEMS =
            "SELECT username FROM ofGatewayPseudoRoster WHERE registrationID=?";

    private long registrationID;
    private ConcurrentHashMap<String,PseudoRosterItem> pseudoRosterItems = new ConcurrentHashMap<String,PseudoRosterItem>();

    /**
     * Loads an existing pseudo roster.
     *
     * @param registrationID The ID of the registration the roster item is associated with.
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
        PseudoRosterItem rosterItem = pseudoRosterItems.get(username);
        if (rosterItem != null) {
            rosterItem.delete();
        }
        pseudoRosterItems.remove(username);
    }

    /**
     * Creates a new pseudo roster item for a username, nickname, and list of groups.
     *
     * @param username Username to add.
     * @param nickname Nickname for roster item.
     * @param groups List of groups for roster item.
     * @return PseudoRosterItem that was created.
     */
    public PseudoRosterItem createItem(String username, String nickname, List<String> groups) {
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
