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

package org.jivesoftware.messenger;

import org.jivesoftware.database.SequenceManager;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.*;
import org.jivesoftware.messenger.container.BasicModule;
import org.xmpp.packet.Message;
import org.dom4j.io.SAXReader;
import org.dom4j.DocumentFactory;

import java.util.*;
import java.sql.*;
import java.sql.Connection;

/**
 * Represents the user's offline message storage. A message store holds messages that were sent
 * to the user while they were unavailable. The user can retrieve their messages by setting
 * their presence to "available". The messages will then be delivered normally.
 * Offline message storage is optional in which case, a null implementation is returned that
 * always throws UnauthorizedException adding messages to the store.
 *
 * @author Iain Shigeoka
 */
public class OfflineMessageStore extends BasicModule {

    private static final String INSERT_OFFLINE =
        "INSERT INTO jiveOffline (username, messageID, creationDate, messageSize, message) " +
        "VALUES (?, ?, ?, ?, ?)";
    private static final String LOAD_OFFLINE =
        "SELECT message FROM jiveOffline WHERE username=?";
    private static final String SELECT_SIZE_OFFLINE =
        "SELECT SUM(messageSize) FROM jiveOffline WHERE username=?";
    private static final String DELETE_OFFLINE =
        "DELETE FROM jiveOffline WHERE username=?";

    private static OfflineMessageStore instance;

    /**
     * Returns a singleton instance of OfflineMessageStore.
     *
     * @return an instance.
     */
    public static OfflineMessageStore getInstance() {
        return instance;
    }

    private SAXReader saxReader = new SAXReader();
    private DocumentFactory docFactory = new DocumentFactory();

    public OfflineMessageStore() {
        super("Offline Message Store");
        instance = this;
    }

    /**
     * Adds a message to this message store. Messages will be stored and made
     * available for later delivery.
     *
     * @param message the message to store.
     */
    public void addMessage(Message message) {
        if (message == null) {
            return;
        }
        String username = message.getFrom().getNode();
        // If the username is null (such as when an anonymous user), don't store.
        if (username == null) {
            return;
        }

        long messageID = SequenceManager.nextID(JiveConstants.OFFLINE);

        // Get the message in XML format. We add the element to a new document so
        // that we can easily parse the message from the database later.
        String msgXML = docFactory.createDocument(message.getElement()).asXML();

        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(INSERT_OFFLINE);
            pstmt.setString(1, username);
            pstmt.setLong(2, messageID);
            pstmt.setString(3, StringUtils.dateToMillis(new java.util.Date()));
            pstmt.setInt(4, msgXML.length());
            pstmt.setString(5, msgXML);
            pstmt.executeUpdate();
        }

        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        finally {
            try { if (pstmt != null) { pstmt.close(); } }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) { con.close(); } }
            catch (Exception e) { Log.error(e); }
        }
    }

    /**
     * Returns a Collection of all messages in the store for a user.
     * Messages are deleted after being selected from the database.
     *
     * @param username the username of the user who's messages you'd like to receive
     * @return An iterator of packets containing all offline messages
     */
    public Collection<Message> getMessages(String username) {
        List<Message> messages = new ArrayList<Message>();
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_OFFLINE);
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String msgXML = rs.getString(1);
                messages.add(new Message(saxReader.read(msgXML).getRootElement()));
            }
            rs.close();
            pstmt.close();

            pstmt = con.prepareStatement(DELETE_OFFLINE);
            pstmt.setString(1, username);
            pstmt.executeUpdate();
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        finally {
            try { if (pstmt != null) { pstmt.close(); } }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) { con.close(); } }
            catch (Exception e) { Log.error(e); }
        }
        return messages;
    }

    /**
     * Returns the approximate size (in bytes) of the XML messages stored for
     * a particular user.
     *
     * @param username the username of the user.
     * @return the approximate size of stored messages (in bytes).
     */
    public int getSize(String username) {
        int size = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SELECT_SIZE_OFFLINE);
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                size = rs.getInt(1);
            }
            rs.close();
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        finally {
            try { if (pstmt != null) { pstmt.close(); } }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) { con.close(); } }
            catch (Exception e) { Log.error(e); }
        }
        return size;
    }
}