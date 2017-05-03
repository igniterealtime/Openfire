/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.roster;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.database.SequenceManager;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.LocaleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

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
public class DefaultRosterItemProvider implements RosterItemProvider {

	private static final Logger Log = LoggerFactory.getLogger(DefaultRosterItemProvider.class);

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
             "SELECT ofRosterGroups.rosterID,groupName FROM ofRosterGroups " +
             "INNER JOIN ofRoster ON ofRosterGroups.rosterID = ofRoster.rosterID " +
             "WHERE username=? ORDER BY ofRosterGroups.rosterID, rank";

    /* (non-Javadoc)
	 * @see org.jivesoftware.openfire.roster.RosterItemProvider#createItem(java.lang.String, org.jivesoftware.openfire.roster.RosterItem)
	 */
	@Override
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

    /* (non-Javadoc)
	 * @see org.jivesoftware.openfire.roster.RosterItemProvider#updateItem(java.lang.String, org.jivesoftware.openfire.roster.RosterItem)
	 */
	@Override
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
            DbConnectionManager.fastcloseStmt(pstmt);

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

    /* (non-Javadoc)
	 * @see org.jivesoftware.openfire.roster.RosterItemProvider#deleteItem(java.lang.String, long)
	 */
	@Override
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
            DbConnectionManager.fastcloseStmt(pstmt);

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

    /* (non-Javadoc)
	 * @see org.jivesoftware.openfire.roster.RosterItemProvider#getUsernames(java.lang.String)
	 */
	@Override
	public Iterator<String> getUsernames(String jid) {
        List<String> answer = new ArrayList<>();
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

    /* (non-Javadoc)
	 * @see org.jivesoftware.openfire.roster.RosterItemProvider#getItemCount(java.lang.String)
	 */
	@Override
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

    /* (non-Javadoc)
	 * @see org.jivesoftware.openfire.roster.RosterItemProvider#getItems(java.lang.String)
	 */
	@Override
	public Iterator<RosterItem> getItems(String username) {
        LinkedList<RosterItem> itemList = new LinkedList<>();
        Map<Long, RosterItem> itemsByID = new HashMap<>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
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
            DbConnectionManager.fastcloseStmt(rs, pstmt);
            // Set null to pstmt to be sure that it's not closed twice. It seems that
            // Sybase driver is raising an error when trying to close an already closed statement.
            // it2000 comment: TODO interesting, that's the only place with the sybase fix
            // it2000 comment: one should move this in closeStatement()
            pstmt = null;

            // Load the groups for the loaded contact
            if (!itemList.isEmpty()) {
            	pstmt = con.prepareStatement(LOAD_ROSTER_ITEM_GROUPS);
            	pstmt.setString(1, username);
                rs = pstmt.executeQuery();
                while (rs.next()) {
                    itemsByID.get(rs.getLong(1)).getGroups().add(rs.getString(2));
                }
            }
        }
        catch (SQLException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
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
                    Log.error(e.getMessage(), e);
                }
            }
        }
        finally {
            DbConnectionManager.closeStatement(pstmt);
        }
    }
}
