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

package org.jivesoftware.messenger.user.spi;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.database.SequenceManager;
import org.jivesoftware.messenger.user.CachedRosterItem;
import org.jivesoftware.messenger.user.RosterItem;
import org.jivesoftware.messenger.user.RosterItemProvider;
import org.jivesoftware.messenger.user.UserAlreadyExistsException;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;

/**
 * <p>Implements the roster item provider against the jiveRoster table
 * using standard Jive default JDBC connections.</p>
 *
 * @author Iain Shigeoka
 */
public class DbRosterItemProvider implements RosterItemProvider {

    private static final String CREATE_ROSTER_ITEM =
            "INSERT INTO jiveRoster (username, rosterID, jid, sub, ask, recv, nick) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";

    /**
     * <p>Creates a new roster item for the given user (optional operation).</p>
     * <p/>
     * <p><b>Important!</b> The item passed as a parameter to this method is strictly a convenience for passing all
     * of the data needed for a new roster item. The roster item returned from the method will be cached by Messenger.
     * In some cases, the roster item passed in will be passed back out. However, if an implementation may
     * return RosterItems as a separate class (for example, a RosterItem that directly accesses the backend
     * storage, or one that is an object in an object database).
     * <p/>
     * <p>If you don't want roster items edited through messenger, throw UnsupportedOperationException.</p>
     *
     * @param username the username of the user/chatbot that owns the roster item
     * @param item     the settings for the roster item to create
     * @return The created roster item
     * @throws UnsupportedOperationException If the provider does not support the operation (this is an optional operation)
     */
    public CachedRosterItem createItem(String username, RosterItem item)
            throws UserAlreadyExistsException, UnsupportedOperationException {
        Connection con = null;
        PreparedStatement pstmt = null;
        CachedRosterItem cachedItem = null;
        try {
            con = DbConnectionManager.getConnection();

            long rosterID = SequenceManager.nextID(JiveConstants.ROSTER);
            pstmt = con.prepareStatement(CREATE_ROSTER_ITEM);
            pstmt.setString(1, username);
            pstmt.setLong(2, rosterID);
            pstmt.setString(3, item.getJid().toBareJID());
            pstmt.setInt(4, item.getSubStatus().getValue());
            pstmt.setInt(5, item.getAskStatus().getValue());
            pstmt.setInt(6, item.getRecvStatus().getValue());
            pstmt.setString(7, item.getNickname());
            pstmt.executeUpdate();

            if (item instanceof CachedRosterItemImpl) {
                // If a RosterItemImpl we can reuse it by setting the new roster ID
                cachedItem = (CachedRosterItem)item;
                ((CachedRosterItemImpl)cachedItem).setID(rosterID);
            }
            else {
                // Otherwise, just create a coyy of the item with the new roster ID
                cachedItem = new CachedRosterItemImpl(rosterID, item);
            }
            insertGroups(rosterID, item.getGroups().iterator(), pstmt, con);
        }
        catch (SQLException e) {
            throw new UserAlreadyExistsException(item.getJid().toBareJID());
        }
        finally {
            DbConnectionManager.close(pstmt, con);
        }
        return cachedItem;
    }

    private static final String UPDATE_ROSTER_ITEM =
            "UPDATE jiveRoster SET sub=?, ask=?, recv=?, nick=? WHERE rosterID=?";
    private static final String DELETE_ROSTER_ITEM_GROUPS =
            "DELETE FROM jiveRosterGroups WHERE rosterID=?";

    /**
     * <p>Update the roster item in storage with the information contained in the given item (optional operation).</p>
     * <p/>
     * <p>If you don't want roster items edited through messenger, throw UnsupportedOperationException.</p>
     *
     * @param username the username of the user/chatbot that owns the roster item
     * @param item     The roster item to update
     * @throws UserNotFoundException         If no entry could be found to update
     * @throws UnsupportedOperationException If the provider does not support the operation (this is an optional operation)
     */
    public void updateItem(String username, CachedRosterItem item) throws UserNotFoundException, UnsupportedOperationException {
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

            // Delete old group list
            pstmt = con.prepareStatement(DELETE_ROSTER_ITEM_GROUPS);
            pstmt.setLong(1, rosterID);
            pstmt.executeUpdate();

            insertGroups(rosterID, item.getGroups().iterator(), pstmt, con);

        }
        catch (SQLException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        finally {
            DbConnectionManager.close(pstmt, con);
        }
    }

    private static final String CREATE_ROSTER_ITEM_GROUPS =
            "INSERT INTO jiveRosterGroups (rosterID, rank, groupName) VALUES (?, ?, ?)";

    /**
     * <p>Insert the groups into the given roster item.</p>
     *
     * @param rosterID The roster ID of the item the groups belong to
     * @param iter     An iterator over the group names to insert
     */
    private void insertGroups(long rosterID,
                              Iterator iter,
                              PreparedStatement pstmt,
                              Connection con)
            throws SQLException {
        try {
            pstmt = con.prepareStatement(CREATE_ROSTER_ITEM_GROUPS);
            pstmt.setLong(1, rosterID);
            for (int i = 0; iter.hasNext(); i++) {
                pstmt.setInt(2, i);
                pstmt.setString(3, (String)iter.next());
                pstmt.executeUpdate();
            }
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
        }
    }

    private static final String DELETE_ROSTER_ITEM =
            "DELETE FROM jiveRoster WHERE rosterID=?";

    /**
     * <p>Delete the roster item with the given itemJID for the user (optional operation).</p>
     * <p/>
     * <p>If you don't want roster items deleted through messenger, throw UnsupportedOperationException.</p>
     *
     * @param username     the long ID of the user/chatbot that owns the roster item
     * @param rosterItemID The roster item to delete
     * @throws UnsupportedOperationException If the provider does not support the operation (this is an optional operation)
     */
    public void deleteItem(String username, long rosterItemID)
            throws UnsupportedOperationException {
        // Only try to remove the user if they exist in the roster already:
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            // Remove roster groups
            pstmt = con.prepareStatement(DELETE_ROSTER_ITEM_GROUPS);

            pstmt.setLong(1, rosterItemID);
            pstmt.executeUpdate();

            // Remove roster
            pstmt = con.prepareStatement(DELETE_ROSTER_ITEM);

            pstmt.setLong(1, rosterItemID);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        finally {
            DbConnectionManager.close(pstmt, con);
        }
    }

    private static final String LOAD_USERNAMES = "SELECT DISTINCT username from jiveRoster WHERE jid=?";

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
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_USERNAMES);
            pstmt.setString(1, jid);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                answer.add(rs.getString(1));
            }
        }
        catch (SQLException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        finally {
            DbConnectionManager.close(pstmt, con);
        }
        return answer.iterator();
    }

    private static final String COUNT_ROSTER_ITEMS =
            "SELECT COUNT(rosterID) FROM jiveRoster WHERE username=?";

    /**
     * <p>Obtain a count of the number of roster items available for the given user.</p>
     *
     * @param username the username of the user/chatbot that owns the roster items
     * @return The number of roster items available for the user
     */
    public int getItemCount(String username) {
        int count = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(COUNT_ROSTER_ITEMS);
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                count = rs.getInt(1);
            }
        }
        catch (SQLException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        finally {
            DbConnectionManager.close(pstmt, con);
        }
        return count;
    }

    private static final String LOAD_ROSTER = "SELECT jid, rosterID, sub, ask, recv, nick FROM jiveRoster WHERE username=?";
    private static final String LOAD_ROSTER_ITEM_GROUPS = "SELECT groupName FROM jiveRosterGroups WHERE rosterID=? ORDER BY rank";

    /**
     * <p>Retrieve an iterator of RosterItems for the given user.</p>
     * <p/>
     * <p>This method will commonly be called when a user logs in. The data will be cached
     * in memory when possible. However, some rosters may be very large so items may need
     * to be retrieved from the provider more frequently than usual for provider data.
     *
     * @param username the username of the user/chatbot that owns the roster items
     * @return An iterator of all RosterItems owned by the user
     */
    public Iterator getItems(String username) {
        LinkedList itemList = new LinkedList();
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_ROSTER);
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                CachedRosterItem item = new CachedRosterItemImpl(rs.getLong(2),
                        new JID(rs.getString(1)),
                        RosterItem.SubType.getTypeFromInt(rs.getInt(3)),
                        RosterItem.AskType.getTypeFromInt(rs.getInt(4)),
                        RosterItem.RecvType.getTypeFromInt(rs.getInt(5)),
                        rs.getString(6),
                        null);
                PreparedStatement gstmt = null;
                ResultSet gs = null;
                try {
                    gstmt = con.prepareStatement(LOAD_ROSTER_ITEM_GROUPS);
                    gstmt.setLong(1, item.getID());
                    gs = gstmt.executeQuery();
                    while (gs.next()) {
                        item.getGroups().add(gs.getString(1));
                    }
                    itemList.add(item);
                }
                finally {
                    DbConnectionManager.close(gstmt, con);
                }
            }
        }
        catch (SQLException e) {
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
        return itemList.iterator();
    }
}