/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
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
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.database.SequenceManager;

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
            "INSERT INTO jiveOffline (userID, messageID, creationDate, messageSize, message) " +
            "VALUES (?, ?, ?, ?, ?)";
    private static final String LOAD_OFFLINE =
            "SELECT message FROM jiveOffline WHERE userID=?";
    private static final String SELECT_SIZE_OFFLINE =
            "SELECT SUM(messageSize) FROM jiveOffline WHERE userID=?";
    private static final String DELETE_OFFLINE =
            "DELETE FROM jiveOffline WHERE userID=?";

    public DbOfflineMessageStore() {
        super("Offline Message Store");
    }

    public void addMessage(Message message) throws UnauthorizedException {
        if (message != null) {

            Connection con = null;
            PreparedStatement pstmt = null;
            long messageID = SequenceManager.nextID(JiveConstants.OFFLINE);
            String user = message.getRecipient().getNamePrep();
            try {
                long userID = userManager.getUserID(user);
                StringXMLStreamWriter serMsg = new StringXMLStreamWriter();
                message.send(serMsg, 0);
                String msg = serMsg.toString();

                con = DbConnectionManager.getConnection();
                pstmt = con.prepareStatement(INSERT_OFFLINE);
                pstmt.setLong(1, userID);
                pstmt.setLong(2, messageID);
                pstmt.setString(3, StringUtils.dateToMillis(new Date()));
                pstmt.setInt(4, msg.length());
                pstmt.setString(5, msg);
                pstmt.executeUpdate();
            }
            catch (UserNotFoundException e) {
                Log.warn("Could not store offline message for " + user +
                        " address " + message.getRecipient(), e);
            }
            catch (Exception e) {
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
        }
    }

    public Iterator getMessages(String userName)
            throws UnauthorizedException, UserNotFoundException {
        return getMessages(userManager.getUserID(userName));
    }

    public Iterator getMessages(long userID) throws UnauthorizedException {
        java.util.LinkedList msgs = new java.util.LinkedList();
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_OFFLINE);
            pstmt.setLong(1, userID);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String msg = rs.getString(1);
                msgs.add(packetFactory.getMessage(msg));
            }
            pstmt = con.prepareStatement(DELETE_OFFLINE);
            pstmt.setLong(1, userID);
            pstmt.executeUpdate();
        }
        catch (Exception e) {
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
        return msgs.iterator();
    }

    public int getSize(long userID) {
        int size = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SELECT_SIZE_OFFLINE);
            pstmt.setLong(1, userID);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                size = rs.getInt(1);
            }
        }
        catch (Exception e) {
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
        return size;
    }

    public int getSize(String userName) throws UserNotFoundException {
        return getSize(userManager.getUserID(userName));
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
