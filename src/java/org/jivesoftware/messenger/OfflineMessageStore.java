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
import org.dom4j.Element;

import java.util.*;
import java.util.Date;
import java.sql.*;
import java.sql.Connection;
import java.io.StringReader;
import java.text.SimpleDateFormat;

/**
 * Represents the user's offline message storage. A message store holds messages that were
 * sent to the user while they were unavailable. The user can retrieve their messages by
 * setting their presence to "available". The messages will then be delivered normally.
 * Offline message storage is optional, in which case a null implementation is returned that
 * always throws UnauthorizedException when adding messages to the store.
 *
 * @author Iain Shigeoka
 */
public class OfflineMessageStore extends BasicModule {

    private static final String INSERT_OFFLINE =
        "INSERT INTO jiveOffline (username, messageID, creationDate, messageSize, message) " +
        "VALUES (?, ?, ?, ?, ?)";
    private static final String LOAD_OFFLINE =
        "SELECT message, creationDate FROM jiveOffline WHERE username=?";
    private static final String SELECT_SIZE_OFFLINE =
        "SELECT SUM(messageSize) FROM jiveOffline WHERE username=?";
    private static final String SELECT_SIZE_ALL_OFFLINE =
        "SELECT SUM(messageSize) FROM jiveOffline";
    private static final String DELETE_OFFLINE =
        "DELETE FROM jiveOffline WHERE username=?";

    private Cache sizeCache;
    private SimpleDateFormat dateFormat;

    /**
     * Returns the instance of <tt>OfflineMessageStore</tt> being used by the XMPPServer.
     *
     * @return the instance of <tt>OfflineMessageStore</tt> being used by the XMPPServer.
     */
    public static OfflineMessageStore getInstance() {
        return XMPPServer.getInstance().getOfflineMessageStore();
    }

    private SAXReader saxReader = new SAXReader();

    /**
     * Constructs a new offline message store.
     */
    public OfflineMessageStore() {
        super("Offline Message Store");
        dateFormat = new SimpleDateFormat("yyyyMMdd'T'hh:mm:ss");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        sizeCache = new Cache("Offline Message Size Cache", 1024*100, JiveConstants.HOUR*12);
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
        String username = message.getTo().getNode();
        // If the username is null (such as when an anonymous user), don't store.
        if (username == null) {
            return;
        }

        long messageID = SequenceManager.nextID(JiveConstants.OFFLINE);

        // Get the message in XML format.
        String msgXML = message.getElement().asXML();

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

        // Update the cached size if it exists.
        if (sizeCache.containsKey(username)) {
            int size = (Integer)sizeCache.get(username);
            size += msgXML.length();
            sizeCache.put(username, size);
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
                Date creationDate = new Date(Long.parseLong(rs.getString(2).trim()));
                Message message = new Message(saxReader.read(new StringReader(msgXML)).getRootElement());
                // Add a delayed delivery (JEP-0091) element to the message.
                Element delay = message.addChildElement("x", "jabber:x:delay");
                delay.addAttribute("from", XMPPServer.getInstance().getServerInfo().getName());
                synchronized (dateFormat) {
                    delay.addAttribute("stamp", dateFormat.format(creationDate));
                }
                messages.add(message);
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
        // See if the size is cached.
        if (sizeCache.containsKey(username)) {
            return (Integer)sizeCache.get(username);
        }
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
            // Add the value to cache.
            sizeCache.put(username, size);
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

    /**
     * Returns the approximate size (in bytes) of the XML messages stored for all
     * users.
     *
     * @return the approximate size of all stored messages (in bytes).
     */
    public int getSize() {
        int size = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SELECT_SIZE_ALL_OFFLINE);
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