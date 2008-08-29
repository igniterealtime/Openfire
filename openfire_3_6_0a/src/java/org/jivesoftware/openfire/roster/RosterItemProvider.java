/**
 * $RCSfile$
 * $Revision: 1751 $
 * $Date: 2005-08-07 20:08:47 -0300 (Sun, 07 Aug 2005) $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.roster;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.database.SequenceManager;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Defines the provider methods required for creating, reading, updating and deleting roster
 * items.<p>
 *
 * Rosters are another user resource accessed via the user or chatbot's long ID. A user/chatbot
 * may have zero or more roster items and each roster item may have zero or more groups. Each
 * roster item is additionaly keyed on a XMPP jid. In most cases, the entire roster will be read
 * in from memory and manipulated or sent to the user. However some operations will need to retrive
 * specific roster items rather than the entire roster.
 *
 * @author Iain Shigeoka
 */
public class RosterItemProvider {

    private static final String CREATE_ROSTER_ITEM =
            "INSERT INTO ofRoster (username, rosterID, jid, sub, ask, recv, nick) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String UPDATE_ROSTER_ITEM =
            "UPDATE ofRoster SET sub=?, ask=?, recv=?, nick=? WHERE rosterID=?";
    private static final String DELETE_ROSTER_ITEM_GROUPS =
            "DELETE FROM ofRosterGroups WHERE rosterID=?";
    private static final String CREATE_ROSTER_ITEM_GROUPS =
            "INSERT INTO ofRosterGroups (rosterID, rank, groupName) VALUES (?, ?, ?)";
    private static final String DELETE_ROSTER_ITEM =
            "DELETE FROM ofRoster WHERE rosterID=?";
    private static final String LOAD_USERNAMES =
            "SELECT DISTINCT username from ofRoster WHERE jid=?";
    private static final String COUNT_ROSTER_ITEMS =
            "SELECT COUNT(rosterID) FROM ofRoster WHERE username=?";
     private static final String LOAD_ROSTER =
             "SELECT jid, rosterID, sub, ask, recv, nick FROM ofRoster WHERE username=?";
    private static final String LOAD_ROSTER_ITEM_GROUPS =
            "SELECT rosterID,groupName FROM ofRosterGroups";


    private static RosterItemProvider instance = new RosterItemProvider();

    public static RosterItemProvider getInstance() {
        return instance;
    }

    /**
     * Creates a new roster item for the given user (optional operation).<p>
     *
     * <b>Important!</b> The item passed as a parameter to this method is strictly a convenience
     * for passing all of the data needed for a new roster item. The roster item returned from the
     * method will be cached by Openfire. In some cases, the roster item passed in will be passed
     * back out. However, if an implementation may return RosterItems as a separate class
     * (for example, a RosterItem that directly accesses the backend storage, or one that is an
     * object in an object database).<p>
     *
     * @param username the username of the user/chatbot that owns the roster item.
     * @param item the settings for the roster item to create.
     * @return the new roster item.
     * @throws UserAlreadyExistsException if a roster item with the username already exists. 
     */
    public RosterItem createItem(String username, RosterItem item)
            throws UserAlreadyExistsException
    {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            long rosterID = SequenceManager.nextID(JiveConstants.ROSTER);
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(CREATE_ROSTER_ITEM);
            pstmt.setString(1, username);
            pstmt.setLong(2, rosterID);
            pstmt.setString(3, item.getJid().toBareJID());
            pstmt.setInt(4, item.getSubStatus().getValue());
            pstmt.setInt(5, item.getAskStatus().getValue());
            pstmt.setInt(6, item.getRecvStatus().getValue());
            pstmt.setString(7, item.getNickname());
            pstmt.executeUpdate();

            item.setID(rosterID);
            insertGroups(rosterID, item.getGroups().iterator(), con);
        }
        catch (SQLException e) {
            Log.warn("Error trying to insert a new row in ofRoster", e);
            throw new UserAlreadyExistsException(item.getJid().toBareJID());
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        return item;
    }

    /**
     * Update the roster item in storage with the information contained in the given item
     * (optional operation).<p>
     *
     * If you don't want roster items edited through openfire, throw UnsupportedOperationException.
     *
     * @param username the username of the user/chatbot that owns the roster item
     * @param item   The roster item to update
     * @throws UserNotFoundException If no entry could be found to update
     */
    public void updateItem(String username, RosterItem item) throws UserNotFoundException {
        Connection con = null;
        PreparedStatement pstmt = null;
        long rosterID = item.getID();
        try {
            con = DbConnectionManager.getConnection();
            // Update existing roster item
            pstmt = con.prepareStatement(UPDATE_ROSTER_ITEM);
            pstmt.setInt(1, item.getSubStatus().getValue());
            pstmt.setInt(2, item.getAskStatus().getValue());
            pstmt.setInt(3, item.getRecvStatus().getValue());
            pstmt.setString(4, item.getNickname());
            pstmt.setLong(5, rosterID);
            pstmt.executeUpdate();
            // Close now the statement (do not wait to be GC'ed)
            pstmt.close();

            // Delete old group list
            pstmt = con.prepareStatement(DELETE_ROSTER_ITEM_GROUPS);
            pstmt.setLong(1, rosterID);
            pstmt.executeUpdate();

            insertGroups(rosterID, item.getGroups().iterator(), con);
        }
        catch (SQLException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /**
     * Delete the roster item with the given itemJID for the user (optional operation).<p>
     *
     * If you don't want roster items deleted through openfire, throw
     * UnsupportedOperationException.
     *
     * @param username the long ID of the user/chatbot that owns the roster item
     * @param rosterItemID The roster item to delete
     */
    public void deleteItem(String username, long rosterItemID) {
        // Only try to remove the user if they exist in the roster already:
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            // Remove roster groups
            pstmt = con.prepareStatement(DELETE_ROSTER_ITEM_GROUPS);

            pstmt.setLong(1, rosterItemID);
            pstmt.executeUpdate();
            // Close now the statement (do not wait to be GC'ed)
            pstmt.close();

            // Remove roster
            pstmt = con.prepareStatement(DELETE_ROSTER_ITEM);

            pstmt.setLong(1, rosterItemID);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /**
     * Returns an iterator on the usernames whose roster includes the specified JID.
     *
     * @param jid the jid that the rosters should include.
     * @return an iterator on the usernames whose roster includes the specified JID.
     */
    public Iterator<String> getUsernames(String jid) {
        List<String> answer = new ArrayList<String>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_USERNAMES);
            pstmt.setString(1, jid);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                answer.add(rs.getString(1));
            }
        }
        catch (SQLException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return answer.iterator();
    }

    /**
     * Obtain a count of the number of roster items available for the given user.
     *
     * @param username the username of the user/chatbot that owns the roster items
     * @return The number of roster items available for the user
     */
    public int getItemCount(String username) {
        int count = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(COUNT_ROSTER_ITEMS);
            pstmt.setString(1, username);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                count = rs.getInt(1);
            }
        }
        catch (SQLException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return count;
    }

    /**
     * Retrieve an iterator of RosterItems for the given user.<p>
     *
     * This method will commonly be called when a user logs in. The data will be cached
     * in memory when possible. However, some rosters may be very large so items may need
     * to be retrieved from the provider more frequently than usual for provider data.
     *
     * @param username the username of the user/chatbot that owns the roster items
     * @return An iterator of all RosterItems owned by the user
     */
    public Iterator<RosterItem> getItems(String username) {
        LinkedList<RosterItem> itemList = new LinkedList<RosterItem>();
        Map<Long, RosterItem> itemsByID = new HashMap<Long, RosterItem>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs;
        try {
            // Load all the contacts in the roster
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_ROSTER);
            pstmt.setString(1, username);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                // Create a new RosterItem (ie. user contact) from the stored information
                RosterItem item = new RosterItem(rs.getLong(2),
                        new JID(rs.getString(1)),
                        RosterItem.SubType.getTypeFromInt(rs.getInt(3)),
                        RosterItem.AskType.getTypeFromInt(rs.getInt(4)),
                        RosterItem.RecvType.getTypeFromInt(rs.getInt(5)),
                        rs.getString(6),
                        null);
                // Add the loaded RosterItem (ie. user contact) to the result
                itemList.add(item);
                itemsByID.put(item.getID(), item);
            }
            // Close the statement and result set
            rs.close();
            pstmt.close();
            // Set null to pstmt to be sure that it's not closed twice. It seems that
            // Sybase driver is raising an error when trying to close an already closed statement.
            pstmt = null;

            // Load the groups for the loaded contact
            if (!itemList.isEmpty()) {
                StringBuilder sb = new StringBuilder(100);
                sb.append(LOAD_ROSTER_ITEM_GROUPS).append(" WHERE rosterID IN (");
                for (RosterItem item : itemList) {
                    sb.append(item.getID()).append(",");
                }
                sb.setLength(sb.length()-1);
                sb.append(") ORDER BY rosterID, rank");
                pstmt = con.prepareStatement(sb.toString());
                rs = pstmt.executeQuery();
                while (rs.next()) {
                    itemsByID.get(rs.getLong(1)).getGroups().add(rs.getString(2));
                }
                rs.close();
            }
        }
        catch (SQLException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        return itemList.iterator();
    }

    /**
     * Insert the groups into the given roster item.
     *
     * @param rosterID the roster ID of the item the groups belong to
     * @param iter an iterator over the group names to insert
     * @param con the database connection to use for the operation.
     * @throws SQLException if an SQL exception occurs.
     */
    private void insertGroups(long rosterID, Iterator<String> iter, Connection con) throws SQLException
    {
        PreparedStatement pstmt = null;
        try {
            pstmt = con.prepareStatement(CREATE_ROSTER_ITEM_GROUPS);
            pstmt.setLong(1, rosterID);
            for (int i = 0; iter.hasNext(); i++) {
                pstmt.setInt(2, i);
                String groupName = iter.next();
                pstmt.setString(3, groupName);
                try {
                    pstmt.executeUpdate();
                }
                catch (SQLException e) {
                    Log.error(e);
                }
            }
        }
        finally {
            DbConnectionManager.closeStatement(pstmt);
        }
    }
}
