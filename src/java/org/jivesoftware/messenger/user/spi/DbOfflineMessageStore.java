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

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.database.SequenceManager;
import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.container.TrackInfo;
import org.jivesoftware.util.*;
import org.jivesoftware.messenger.Message;
import org.jivesoftware.messenger.OfflineMessageStore;
import org.jivesoftware.messenger.PacketFactory;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.UserManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Date;
import java.util.Iterator;

/**
 * An empty implementation of a message store (drops message).
 *
 * @author Iain Shigeoka
 */
public class DbOfflineMessageStore extends BasicModule implements OfflineMessageStore {

    private static final String INSERT_OFFLINE =
        "INSERT INTO jiveOffline (username, messageID, creationDate, messageSize, message) " +
        "VALUES (?, ?, ?, ?, ?)";
    private static final String LOAD_OFFLINE =
        "SELECT message FROM jiveOffline WHERE username=?";
    private static final String SELECT_SIZE_OFFLINE =
        "SELECT SUM(messageSize) FROM jiveOffline WHERE username=?";
    private static final String DELETE_OFFLINE =
        "DELETE FROM jiveOffline WHERE username=?";

    public DbOfflineMessageStore() {
        super("Offline Message Store");
    }

    public void addMessage(Message message) throws UnauthorizedException {
        if (message != null) {

            Connection con = null;
            PreparedStatement pstmt = null;
            long messageID = SequenceManager.nextID(JiveConstants.OFFLINE);
            String username = message.getRecipient().getNamePrep();
            try {
                StringXMLStreamWriter serMsg = new StringXMLStreamWriter();
                message.send(serMsg, 0);
                String msg = serMsg.toString();

                con = DbConnectionManager.getConnection();
                pstmt = con.prepareStatement(INSERT_OFFLINE);
                pstmt.setString(1, username);
                pstmt.setLong(2, messageID);
                pstmt.setString(3, StringUtils.dateToMillis(new Date()));
                pstmt.setInt(4, msg.length());
                pstmt.setString(5, msg);
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
    }

    public Iterator getMessages(String username) throws UnauthorizedException {
        java.util.LinkedList msgs = new java.util.LinkedList();
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_OFFLINE);
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String msg = rs.getString(1);
                msgs.add(packetFactory.getMessage(msg));
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
        return msgs.iterator();
    }

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

    public PacketFactory packetFactory;
    public UserManager userManager;

    public TrackInfo getTrackInfo() {
        TrackInfo trackInfo = new TrackInfo();
        trackInfo.getTrackerClasses().put(PacketFactory.class, "packetFactory");
        trackInfo.getTrackerClasses().put(UserManager.class, "userManager");
        return trackInfo;
    }

}
