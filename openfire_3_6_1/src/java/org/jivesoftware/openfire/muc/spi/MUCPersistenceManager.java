/**
 * $RCSfile$
 * $Revision: 1623 $
 * $Date: 2005-07-12 18:40:57 -0300 (Tue, 12 Jul 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.muc.spi;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.muc.MUCRole;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;
import org.xmpp.packet.JID;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A manager responsible for ensuring room persistence. There are different ways to make a room 
 * persistent. The first attempt will be to save the room in a relation database. If for some reason
 * the room can't be saved in the database an alternative repository will be used to save the room
 * such as XML files.<p>
 * 
 * After the problem with the database has been solved, the information saved in the XML files will
 * be moved to the database.
 *
 * @author Gaston Dombiak
 */
public class MUCPersistenceManager {

    private static final String GET_RESERVED_NAME =
        "SELECT nickname FROM ofMucMember WHERE roomID=? AND jid=?";
    private static final String LOAD_ROOM =
        "SELECT roomID, creationDate, modificationDate, naturalName, description, lockedDate, " +
        "emptyDate, canChangeSubject, maxUsers, publicRoom, moderated, membersOnly, canInvite, " +
        "roomPassword, canDiscoverJID, logEnabled, subject, rolesToBroadcast, useReservedNick, " +
        "canChangeNick, canRegister FROM ofMucRoom WHERE name=?";
    private static final String LOAD_AFFILIATIONS =
        "SELECT jid, affiliation FROM ofMucAffiliation WHERE roomID=?";
    private static final String LOAD_MEMBERS =
        "SELECT jid, nickname FROM ofMucMember WHERE roomID=?";
    private static final String LOAD_HISTORY =
        "SELECT sender, nickname, logTime, subject, body FROM ofMucConversationLog " +
        "WHERE logTime>? AND roomID=? AND (nickname IS NOT NULL OR subject IS NOT NULL) ORDER BY logTime";
    private static final String LOAD_ALL_ROOMS =
        "SELECT roomID, creationDate, modificationDate, name, naturalName, description, " +
        "lockedDate, emptyDate, canChangeSubject, maxUsers, publicRoom, moderated, membersOnly, " +
        "canInvite, roomPassword, canDiscoverJID, logEnabled, subject, rolesToBroadcast, " +
        "useReservedNick, canChangeNick, canRegister " +
        "FROM ofMucRoom WHERE serviceID=? AND (emptyDate IS NULL or emptyDate > ?)";
    private static final String LOAD_ALL_AFFILIATIONS =
        "SELECT ofMucAffiliation.roomID,ofMucAffiliation.jid,ofMucAffiliation.affiliation " +
        "FROM ofMucAffiliation,ofMucRoom WHERE ofMucAffiliation.roomID = ofMucRoom.roomID AND ofMucRoom.serviceID=?";
    private static final String LOAD_ALL_MEMBERS =
        "SELECT ofMucMember.roomID,ofMucMember.jid,ofMucMember.nickname FROM ofMucMember,ofMucRoom " +
        "WHERE ofMucMember.roomID = ofMucRoom.roomID AND ofMucRoom.serviceID=?";
    private static final String LOAD_ALL_HISTORY =
        "SELECT ofMucConversationLog.roomID, ofMucConversationLog.sender, ofMucConversationLog.nickname, " +
        "ofMucConversationLog.logTime, ofMucConversationLog.subject, ofMucConversationLog.body FROM " +
        "ofMucConversationLog, ofMucRoom WHERE ofMucConversationLog.roomID = ofMucRoom.roomID AND " +
        "ofMucRoom.serviceID=? AND ofMucConversationLog.logTime>? AND (ofMucConversationLog.nickname IS NOT NULL " +
        "OR ofMucConversationLog.subject IS NOT NULL) ORDER BY ofMucConversationLog.logTime";
    private static final String UPDATE_ROOM =
        "UPDATE ofMucRoom SET modificationDate=?, naturalName=?, description=?, " +
        "canChangeSubject=?, maxUsers=?, publicRoom=?, moderated=?, membersOnly=?, " +
        "canInvite=?, roomPassword=?, canDiscoverJID=?, logEnabled=?, rolesToBroadcast=?, " +
        "useReservedNick=?, canChangeNick=?, canRegister=? WHERE roomID=?";
    private static final String ADD_ROOM = 
        "INSERT INTO ofMucRoom (serviceID, roomID, creationDate, modificationDate, name, naturalName, " +
        "description, lockedDate, emptyDate, canChangeSubject, maxUsers, publicRoom, moderated, " +
        "membersOnly, canInvite, roomPassword, canDiscoverJID, logEnabled, subject, " +
        "rolesToBroadcast, useReservedNick, canChangeNick, canRegister) VALUES (?,?,?,?,?,?,?,?,?," +
            "?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    private static final String UPDATE_SUBJECT =
        "UPDATE ofMucRoom SET subject=? WHERE roomID=?";
    private static final String UPDATE_LOCK =
        "UPDATE ofMucRoom SET lockedDate=? WHERE roomID=?";
    private static final String UPDATE_EMPTYDATE =
        "UPDATE ofMucRoom SET emptyDate=? WHERE roomID=?";
    private static final String DELETE_ROOM =
        "DELETE FROM ofMucRoom WHERE roomID=?";
    private static final String DELETE_AFFILIATIONS =
        "DELETE FROM ofMucAffiliation WHERE roomID=?";
    private static final String DELETE_MEMBERS =
        "DELETE FROM ofMucMember WHERE roomID=?";
    private static final String ADD_MEMBER =
        "INSERT INTO ofMucMember (roomID,jid,nickname) VALUES (?,?,?)";
    private static final String UPDATE_MEMBER =
        "UPDATE ofMucMember SET nickname=? WHERE roomID=? AND jid=?";
    private static final String DELETE_MEMBER =
        "DELETE FROM ofMucMember WHERE roomID=? AND jid=?";
    private static final String ADD_AFFILIATION =
        "INSERT INTO ofMucAffiliation (roomID,jid,affiliation) VALUES (?,?,?)";
    private static final String UPDATE_AFFILIATION =
        "UPDATE ofMucAffiliation SET affiliation=? WHERE roomID=? AND jid=?";
    private static final String DELETE_AFFILIATION =
        "DELETE FROM ofMucAffiliation WHERE roomID=? AND jid=?";
    private static final String DELETE_USER_MEMBER =
        "DELETE FROM ofMucMember WHERE jid=?";
    private static final String DELETE_USER_MUCAFFILIATION =
        "DELETE FROM ofMucAffiliation WHERE jid=?";
    private static final String ADD_CONVERSATION_LOG =
        "INSERT INTO ofMucConversationLog (roomID,sender,nickname,logTime,subject,body) " +
        "VALUES (?,?,?,?,?,?)";

    /* Map of subdomains to their associated properties */
    private static ConcurrentHashMap<String,MUCServiceProperties> propertyMaps = new ConcurrentHashMap<String,MUCServiceProperties>();

    /**
     * Returns the reserved room nickname for the bare JID in a given room or null if none.
     *
     * @param room the room where the user would like to obtain his reserved nickname. 
     * @param bareJID The bare jid of the user of which you'd like to obtain his reserved nickname.
     * @return the reserved room nickname for the bare JID or null if none.
     */
    public static String getReservedNickname(MUCRoom room, String bareJID) {
        Connection con = null;
        PreparedStatement pstmt = null;
        String answer = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GET_RESERVED_NAME);
            pstmt.setLong(1, room.getID());
            pstmt.setString(2, bareJID);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                answer = rs.getString(1);
            }
            rs.close();
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
        return answer;
    }

    /**
     * Loads the room configuration from the database if the room was persistent.
     * 
     * @param room the room to load from the database if persistent
     */
    public static void loadFromDB(LocalMUCRoom room) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_ROOM);
            pstmt.setString(1, room.getName());
            ResultSet rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new IllegalArgumentException("Room " + room.getName() + " was not found in the database.");
            }
            room.setID(rs.getLong(1));
            room.setCreationDate(new Date(Long.parseLong(rs.getString(2).trim()))); // creation date
            room.setModificationDate(new Date(Long.parseLong(rs.getString(3).trim()))); // modification date
            room.setNaturalLanguageName(rs.getString(4));
            room.setDescription(rs.getString(5));
            room.setLockedDate(new Date(Long.parseLong(rs.getString(6).trim())));
            if (rs.getString(7) != null) {
                room.setEmptyDate(new Date(Long.parseLong(rs.getString(7).trim())));
            }
            else {
                room.setEmptyDate(null);
            }
            room.setCanOccupantsChangeSubject(rs.getInt(8) == 1);
            room.setMaxUsers(rs.getInt(9));
            room.setPublicRoom(rs.getInt(10) == 1);
            room.setModerated(rs.getInt(11) == 1);
            room.setMembersOnly(rs.getInt(12) == 1);
            room.setCanOccupantsInvite(rs.getInt(13) == 1);
            room.setPassword(rs.getString(14));
            room.setCanAnyoneDiscoverJID(rs.getInt(15) == 1);
            room.setLogEnabled(rs.getInt(16) == 1);
            room.setSubject(rs.getString(17));
            List<String> rolesToBroadcast = new ArrayList<String>();
            String roles = Integer.toBinaryString(rs.getInt(18));
            if (roles.charAt(0) == '1') {
                rolesToBroadcast.add("moderator");
            }
            if (roles.length() > 1 && roles.charAt(1) == '1') {
                rolesToBroadcast.add("participant");
            }
            if (roles.length() > 2 && roles.charAt(2) == '1') {
                rolesToBroadcast.add("visitor");
            }
            room.setRolesToBroadcastPresence(rolesToBroadcast);
            room.setLoginRestrictedToNickname(rs.getInt(19) == 1);
            room.setChangeNickname(rs.getInt(20) == 1);
            room.setRegistrationEnabled(rs.getInt(21) == 1);
            room.setPersistent(true);
            rs.close();
            pstmt.close();

            pstmt = con.prepareStatement(LOAD_HISTORY);
            // Recreate the history until two days ago
            long from = System.currentTimeMillis() - (86400000 * 2);
            pstmt.setString(1, StringUtils.dateToMillis(new Date(from)));
            pstmt.setLong(2, room.getID());
            rs = pstmt.executeQuery();
            while (rs.next()) {
                String senderJID = rs.getString(1);
                String nickname = rs.getString(2);
                Date sentDate = new Date(Long.parseLong(rs.getString(3).trim()));
                String subject = rs.getString(4);
                String body = rs.getString(5);
                // Recreate the history only for the rooms that have the conversation logging
                // enabled
                if (room.isLogEnabled()) {
                    room.getRoomHistory().addOldMessage(senderJID, nickname, sentDate, subject,
                            body);
                }
            }
            rs.close();
            pstmt.close();

            // If the room does not include the last subject in the history then recreate one if
            // possible
            if (!room.getRoomHistory().hasChangedSubject() && room.getSubject() != null &&
                    room.getSubject().length() > 0) {
                room.getRoomHistory().addOldMessage(room.getRole().getRoleAddress().toString(),
                        null, room.getModificationDate(), room.getSubject(), null);
            }

            pstmt = con.prepareStatement(LOAD_AFFILIATIONS);
            pstmt.setLong(1, room.getID());
            rs = pstmt.executeQuery();
            while (rs.next()) {
                String jid = rs.getString(1);
                MUCRole.Affiliation affiliation = MUCRole.Affiliation.valueOf(rs.getInt(2));
                try {
                    switch (affiliation) {
                        case owner:
                            room.addOwner(jid, room.getRole());
                            break;
                        case admin:
                            room.addAdmin(jid, room.getRole());
                            break;
                        case outcast:
                            room.addOutcast(jid, null, room.getRole());
                            break;
                        default:
                            Log.error("Unkown affiliation value " + affiliation + " for user "
                                    + jid + " in persistent room " + room.getID());
                    }
                }
                catch (Exception e) {
                    Log.error(e);
                }
            }
            rs.close();
            pstmt.close();

            pstmt = con.prepareStatement(LOAD_MEMBERS);
            pstmt.setLong(1, room.getID());
            rs = pstmt.executeQuery();
            while (rs.next()) {
                try {
                    room.addMember(rs.getString(1), rs.getString(2), room.getRole());
                }
                catch (Exception e) {
                    Log.error(e);
                }
            }
            rs.close();
            // Set now that the room's configuration is updated in the database. Note: We need to
            // set this now since otherwise the room's affiliations will be saved to the database
            // "again" while adding them to the room!
            room.setSavedToDB(true);
            if (room.getEmptyDate() == null) {
                // The service process was killed somehow while the room was being used. Since
                // the room won't have occupants at this time we need to set the best date when
                // the last occupant left the room that we can
                room.setEmptyDate(new Date());
            }
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
    }

    /**
     * Save the room configuration to the DB.
     * 
     * @param room The room to save its configuration.
     */
    public static void saveToDB(LocalMUCRoom room) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            if (room.wasSavedToDB()) {
                pstmt = con.prepareStatement(UPDATE_ROOM);
                pstmt.setString(1, StringUtils.dateToMillis(room.getModificationDate()));
                pstmt.setString(2, room.getNaturalLanguageName());
                pstmt.setString(3, room.getDescription());
                pstmt.setInt(4, (room.canOccupantsChangeSubject() ? 1 : 0));
                pstmt.setInt(5, room.getMaxUsers());
                pstmt.setInt(6, (room.isPublicRoom() ? 1 : 0));
                pstmt.setInt(7, (room.isModerated() ? 1 : 0));
                pstmt.setInt(8, (room.isMembersOnly() ? 1 : 0));
                pstmt.setInt(9, (room.canOccupantsInvite() ? 1 : 0));
                pstmt.setString(10, room.getPassword());
                pstmt.setInt(11, (room.canAnyoneDiscoverJID() ? 1 : 0));
                pstmt.setInt(12, (room.isLogEnabled() ? 1 : 0));
                pstmt.setInt(13, marshallRolesToBroadcast(room));
                pstmt.setInt(14, (room.isLoginRestrictedToNickname() ? 1 : 0));
                pstmt.setInt(15, (room.canChangeNickname() ? 1 : 0));
                pstmt.setInt(16, (room.isRegistrationEnabled() ? 1 : 0));
                pstmt.setLong(17, room.getID());
                pstmt.executeUpdate();
            }
            else {
                pstmt = con.prepareStatement(ADD_ROOM);
                pstmt.setLong(1, XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatServiceID(room.getMUCService().getServiceName()));
                pstmt.setLong(2, room.getID());
                pstmt.setString(3, StringUtils.dateToMillis(room.getCreationDate()));
                pstmt.setString(4, StringUtils.dateToMillis(room.getModificationDate()));
                pstmt.setString(5, room.getName());
                pstmt.setString(6, room.getNaturalLanguageName());
                pstmt.setString(7, room.getDescription());
                pstmt.setString(8, StringUtils.dateToMillis(room.getLockedDate()));
                Date emptyDate = room.getEmptyDate();
                if (emptyDate == null) {
                    pstmt.setString(9, null);
                }
                else {
                    pstmt.setString(9, StringUtils.dateToMillis(emptyDate));
                }
                pstmt.setInt(10, (room.canOccupantsChangeSubject() ? 1 : 0));
                pstmt.setInt(11, room.getMaxUsers());
                pstmt.setInt(12, (room.isPublicRoom() ? 1 : 0));
                pstmt.setInt(13, (room.isModerated() ? 1 : 0));
                pstmt.setInt(14, (room.isMembersOnly() ? 1 : 0));
                pstmt.setInt(15, (room.canOccupantsInvite() ? 1 : 0));
                pstmt.setString(16, room.getPassword());
                pstmt.setInt(17, (room.canAnyoneDiscoverJID() ? 1 : 0));
                pstmt.setInt(18, (room.isLogEnabled() ? 1 : 0));
                pstmt.setString(19, room.getSubject());
                pstmt.setInt(20, marshallRolesToBroadcast(room));
                pstmt.setInt(21, (room.isLoginRestrictedToNickname() ? 1 : 0));
                pstmt.setInt(22, (room.canChangeNickname() ? 1 : 0));
                pstmt.setInt(23, (room.isRegistrationEnabled() ? 1 : 0));
                pstmt.executeUpdate();
            }
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
    }

    /**
     * Removes the room configuration and its affiliates from the database.
     * 
     * @param room the room to remove from the database.
     */
    public static void deleteFromDB(MUCRoom room) {
        if (!room.isPersistent() || !room.wasSavedToDB()) {
            return;
        }
        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();
            pstmt = con.prepareStatement(DELETE_AFFILIATIONS);
            pstmt.setLong(1, room.getID());
            pstmt.executeUpdate();
            pstmt.close();

            pstmt = con.prepareStatement(DELETE_MEMBERS);
            pstmt.setLong(1, room.getID());
            pstmt.executeUpdate();
            pstmt.close();

            pstmt = con.prepareStatement(DELETE_ROOM);
            pstmt.setLong(1, room.getID());
            pstmt.executeUpdate();

            // Update the room (in memory) to indicate the it's no longer in the database.
            room.setSavedToDB(false);
        }
        catch (SQLException sqle) {
            Log.error(sqle);
            abortTransaction = true;
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
        }
    }

    /**
     * Loads all the rooms that had occupants after a given date from the database. This query
     * will be executed only when the service is starting up.
     *
     * @param chatserver the chat server that will hold the loaded rooms.
     * @param emptyDate rooms that hadn't been used before this date won't be loaded.
     * @param packetRouter the PacketRouter that loaded rooms will use to send packets.
     * @return a collection with all the persistent rooms.
     */
    public static Collection<LocalMUCRoom> loadRoomsFromDB(MultiUserChatService chatserver,
            Date emptyDate, PacketRouter packetRouter) {
        Connection con = null;
        PreparedStatement pstmt = null;
        Map<Long, LocalMUCRoom> rooms = new HashMap<Long, LocalMUCRoom>();
        try {
            Long serviceID = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatServiceID(chatserver.getServiceName());
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_ALL_ROOMS);
            pstmt.setLong(1, serviceID);
            pstmt.setString(2, StringUtils.dateToMillis(emptyDate));
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                LocalMUCRoom room = new LocalMUCRoom(chatserver, rs.getString(4), packetRouter);
                room.setID(rs.getLong(1));
                room.setCreationDate(new Date(Long.parseLong(rs.getString(2).trim()))); // creation date
                room.setModificationDate(new Date(Long.parseLong(rs.getString(3).trim()))); // modification date
                room.setNaturalLanguageName(rs.getString(5));
                room.setDescription(rs.getString(6));
                room.setLockedDate(new Date(Long.parseLong(rs.getString(7).trim())));
                if (rs.getString(8) != null) {
                    room.setEmptyDate(new Date(Long.parseLong(rs.getString(8).trim())));
                }
                else {
                    room.setEmptyDate(null);
                }
                room.setCanOccupantsChangeSubject(rs.getInt(9) == 1);
                room.setMaxUsers(rs.getInt(10));
                room.setPublicRoom(rs.getInt(11) == 1);
                room.setModerated(rs.getInt(12) == 1);
                room.setMembersOnly(rs.getInt(13) == 1);
                room.setCanOccupantsInvite(rs.getInt(14) == 1);
                room.setPassword(rs.getString(15));
                room.setCanAnyoneDiscoverJID(rs.getInt(16) == 1);
                room.setLogEnabled(rs.getInt(17) == 1);
                room.setSubject(rs.getString(18));
                List<String> rolesToBroadcast = new ArrayList<String>();
                String roles = Integer.toBinaryString(rs.getInt(19));
                if (roles.charAt(0) == '1') {
                    rolesToBroadcast.add("moderator");
                }
                if (roles.length() > 1 && roles.charAt(1) == '1') {
                    rolesToBroadcast.add("participant");
                }
                if (roles.length() > 2 && roles.charAt(2) == '1') {
                    rolesToBroadcast.add("visitor");
                }
                room.setRolesToBroadcastPresence(rolesToBroadcast);
                room.setLoginRestrictedToNickname(rs.getInt(20) == 1);
                room.setChangeNickname(rs.getInt(21) == 1);
                room.setRegistrationEnabled(rs.getInt(22) == 1);
                room.setPersistent(true);
                rooms.put(room.getID(), room);
            }
            rs.close();
            pstmt.close();

            pstmt = con.prepareStatement(LOAD_ALL_HISTORY);
            // Recreate the history until two days ago
            long from = System.currentTimeMillis() - (86400000 * 2);
            pstmt.setLong(1, serviceID);
            pstmt.setString(2, StringUtils.dateToMillis(new Date(from)));
            // Load the rooms conversations from the last two days
            rs = pstmt.executeQuery();
            while (rs.next()) {
                LocalMUCRoom room = rooms.get(rs.getLong(1));
                // Skip to the next position if the room does not exist
                if (room == null) {
                    continue;
                }
                String senderJID = rs.getString(2);
                String nickname = rs.getString(3);
                Date sentDate = new Date(Long.parseLong(rs.getString(4).trim()));
                String subject = rs.getString(5);
                String body = rs.getString(6);
                try {
                    // Recreate the history only for the rooms that have the conversation logging
                    // enabled
                    if (room.isLogEnabled()) {
                        room.getRoomHistory().addOldMessage(senderJID, nickname, sentDate, subject,
                                body);
                    }
                }
                catch (Exception e) {
                    Log.error(e);
                }
            }
            rs.close();
            pstmt.close();

            // Add the last known room subject to the room history only for those rooms that still
            // don't have in their histories the last room subject
            for (MUCRoom loadedRoom : rooms.values()) {
                if (!loadedRoom.getRoomHistory().hasChangedSubject() &&
                        loadedRoom.getSubject() != null &&
                        loadedRoom.getSubject().length() > 0) {
                    loadedRoom.getRoomHistory().addOldMessage(loadedRoom.getRole().getRoleAddress()
                            .toString(), null,
                            loadedRoom.getModificationDate(), loadedRoom.getSubject(), null);
                }
            }

            pstmt = con.prepareStatement(LOAD_ALL_AFFILIATIONS);
            pstmt.setLong(1, serviceID);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                long roomID = rs.getLong(1);
                String jid = rs.getString(2);
                MUCRole.Affiliation affiliation = MUCRole.Affiliation.valueOf(rs.getInt(3));
                LocalMUCRoom room = rooms.get(roomID);
                // Skip to the next position if the room does not exist
                if (room == null) {
                    continue;
                }
                try {
                    switch (affiliation) {
                        case owner:
                            room.addOwner(jid, room.getRole());
                            break;
                        case admin:
                            room.addAdmin(jid, room.getRole());
                            break;
                        case outcast:
                            room.addOutcast(jid, null, room.getRole());
                            break;
                        default:
                            Log.error("Unkown affiliation value " + affiliation + " for user "
                                    + jid + " in persistent room " + room.getID());
                    }
                }
                catch (Exception e) {
                    Log.error(e);
                }
            }
            rs.close();
            pstmt.close();

            pstmt = con.prepareStatement(LOAD_ALL_MEMBERS);
            pstmt.setLong(1, serviceID);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                LocalMUCRoom room = rooms.get(rs.getLong(1));
                // Skip to the next position if the room does not exist
                if (room == null) {
                    continue;
                }
                try {
                    room.addMember(rs.getString(2), rs.getString(3), room.getRole());
                }
                catch (Exception e) {
                    Log.error(e);
                }
            }
            rs.close();
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
        // Set now that the room's configuration is updated in the database. Note: We need to
        // set this now since otherwise the room's affiliations will be saved to the database
        // "again" while adding them to the room!
        for (MUCRoom room : rooms.values()) {
            room.setSavedToDB(true);
            if (room.getEmptyDate() == null) {
                // The service process was killed somehow while the room was being used. Since
                // the room won't have occupants at this time we need to set the best date when
                // the last occupant left the room that we can
                room.setEmptyDate(new Date());
            }
        }

        return rooms.values();
    }

    /**
     * Updates the room's subject in the database. 
     * 
     * @param room the room to update its subject in the database.
     */
    public static void updateRoomSubject(MUCRoom room) {
        if (!room.isPersistent() || !room.wasSavedToDB()) {
            return;
        }

        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_SUBJECT);
            pstmt.setString(1, room.getSubject());
            pstmt.setLong(2, room.getID());
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
    }

    /**
     * Updates the room's lock status in the database.
     *
     * @param room the room to update its lock status in the database.
     */
    public static void updateRoomLock(LocalMUCRoom room) {
        if (!room.isPersistent() || !room.wasSavedToDB()) {
            return;
        }

        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_LOCK);
            pstmt.setString(1, StringUtils.dateToMillis(room.getLockedDate()));
            pstmt.setLong(2, room.getID());
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
    }

    /**
     * Updates the room's lock status in the database.
     *
     * @param room the room to update its lock status in the database.
     */
    public static void updateRoomEmptyDate(MUCRoom room) {
        if (!room.isPersistent() || !room.wasSavedToDB()) {
            return;
        }

        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_EMPTYDATE);
            Date emptyDate = room.getEmptyDate();
            if (emptyDate == null) {
                pstmt.setString(1, null);
            }
            else {
                pstmt.setString(1, StringUtils.dateToMillis(emptyDate));
            }
            pstmt.setLong(2, room.getID());
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
    }

    /**
     * Update the DB with the new affiliation of the user in the room. The new information will be
     * saved only if the room is_persistent and has already been saved to the database previously.
     * 
     * @param room The room where the affiliation of the user was updated.
     * @param bareJID The bareJID of the user to update this affiliation.
     * @param nickname The reserved nickname of the user in the room or null if none.
     * @param newAffiliation the new affiliation of the user in the room.
     * @param oldAffiliation the previous affiliation of the user in the room.
     */
    public static void saveAffiliationToDB(MUCRoom room, String bareJID, String nickname,
            MUCRole.Affiliation newAffiliation, MUCRole.Affiliation oldAffiliation)
    {
        if (!room.isPersistent() || !room.wasSavedToDB()) {
            return;
        }
        if (MUCRole.Affiliation.none == oldAffiliation) {
            if (MUCRole.Affiliation.member == newAffiliation) {
                // Add the user to the members table
                Connection con = null;
                PreparedStatement pstmt = null;
                try {
                    con = DbConnectionManager.getConnection();
                    pstmt = con.prepareStatement(ADD_MEMBER);
                    pstmt.setLong(1, room.getID());
                    pstmt.setString(2, bareJID);
                    pstmt.setString(3, nickname);
                    pstmt.executeUpdate();
                }
                catch (SQLException sqle) {
                    Log.error(sqle);
                }
                finally {
                    try { if (pstmt != null) pstmt.close(); }
                    catch (Exception e) { Log.error(e); }
                    try { if (con != null) con.close(); }
                    catch (Exception e) { Log.error(e); }
                }
            }
            else {
                // Add the user to the generic affiliations table
                Connection con = null;
                PreparedStatement pstmt = null;
                try {
                    con = DbConnectionManager.getConnection();
                    pstmt = con.prepareStatement(ADD_AFFILIATION);
                    pstmt.setLong(1, room.getID());
                    pstmt.setString(2, bareJID);
                    pstmt.setInt(3, newAffiliation.getValue());
                    pstmt.executeUpdate();
                }
                catch (SQLException sqle) {
                    Log.error(sqle);
                }
                finally {
                    try { if (pstmt != null) pstmt.close(); }
                    catch (Exception e) { Log.error(e); }
                    try { if (con != null) con.close(); }
                    catch (Exception e) { Log.error(e); }
                }
            }
        }
        else {
            if (MUCRole.Affiliation.member == newAffiliation &&
                    MUCRole.Affiliation.member == oldAffiliation)
            {
                // Update the member's data in the member table.
                Connection con = null;
                PreparedStatement pstmt = null;
                try {
                    con = DbConnectionManager.getConnection();
                    pstmt = con.prepareStatement(UPDATE_MEMBER);
                    pstmt.setString(1, nickname);
                    pstmt.setLong(2, room.getID());
                    pstmt.setString(3, bareJID);
                    pstmt.executeUpdate();
                }
                catch (SQLException sqle) {
                    Log.error(sqle);
                }
                finally {
                    try { if (pstmt != null) pstmt.close(); }
                    catch (Exception e) { Log.error(e); }
                    try { if (con != null) con.close(); }
                    catch (Exception e) { Log.error(e); }
                }
            }
            else if (MUCRole.Affiliation.member == newAffiliation) {
                Connection con = null;
                PreparedStatement pstmt = null;
                boolean abortTransaction = false;
                try {
                    // Remove the user from the generic affiliations table
                    con = DbConnectionManager.getTransactionConnection();
                    pstmt = con.prepareStatement(DELETE_AFFILIATION);
                    pstmt.setLong(1, room.getID());
                    pstmt.setString(2, bareJID);
                    pstmt.executeUpdate();
                    pstmt.close();

                    // Add them as a member.
                    pstmt = con.prepareStatement(ADD_MEMBER);
                    pstmt.setLong(1, room.getID());
                    pstmt.setString(2, bareJID);
                    pstmt.setString(3, nickname);
                    pstmt.executeUpdate();
                }
                catch (SQLException sqle) {
                    Log.error(sqle);
                    abortTransaction = true;
                }
                finally {
                    try { if (pstmt != null) pstmt.close(); }
                    catch (Exception e) { Log.error(e); }
                    DbConnectionManager.closeTransactionConnection(con, abortTransaction);
                }
            }
            else if (MUCRole.Affiliation.member == oldAffiliation) {
                Connection con = null;
                PreparedStatement pstmt = null;
                boolean abortTransaction = false;
                try {
                    con = DbConnectionManager.getTransactionConnection();
                    pstmt = con.prepareStatement(DELETE_MEMBER);
                    pstmt.setLong(1, room.getID());
                    pstmt.setString(2, bareJID);
                    pstmt.executeUpdate();
                    pstmt.close();

                    pstmt = con.prepareStatement(ADD_AFFILIATION);
                    pstmt.setLong(1, room.getID());
                    pstmt.setString(2, bareJID);
                    pstmt.setInt(3, newAffiliation.getValue());
                    pstmt.executeUpdate();
                }
                catch (SQLException sqle) {
                    Log.error(sqle);
                    abortTransaction = true;
                }
                finally {
                    try { if (pstmt != null) pstmt.close(); }
                    catch (Exception e) { Log.error(e); }
                    DbConnectionManager.closeTransactionConnection(con, abortTransaction);
                }
            }
            else {
                // Update the user in the generic affiliations table.
                Connection con = null;
                PreparedStatement pstmt = null;
                try {
                    con = DbConnectionManager.getConnection();
                    pstmt = con.prepareStatement(UPDATE_AFFILIATION);
                    pstmt.setInt(1, newAffiliation.getValue());
                    pstmt.setLong(2, room.getID());
                    pstmt.setString(3, bareJID);
                    pstmt.executeUpdate();
                }
                catch (SQLException sqle) {
                    Log.error(sqle);
                }
                finally {
                    try { if (pstmt != null) pstmt.close(); }
                    catch (Exception e) { Log.error(e); }
                    try { if (con != null) con.close(); }
                    catch (Exception e) { Log.error(e); }
                }
            }
        }
    }

    /**
     * Removes the affiliation of the user from the DB if the room is persistent.
     * 
     * @param room The room where the affiliation of the user was removed.
     * @param bareJID The bareJID of the user to remove his affiliation.
     * @param oldAffiliation the previous affiliation of the user in the room.
     */
    public static void removeAffiliationFromDB(MUCRoom room, String bareJID,
            MUCRole.Affiliation oldAffiliation)
    {
        if (room.isPersistent() && room.wasSavedToDB()) {
            if (MUCRole.Affiliation.member == oldAffiliation) {
                // Remove the user from the members table
                Connection con = null;
                PreparedStatement pstmt = null;
                try {
                    con = DbConnectionManager.getConnection();
                    pstmt = con.prepareStatement(DELETE_MEMBER);
                    pstmt.setLong(1, room.getID());
                    pstmt.setString(2, bareJID);
                    pstmt.executeUpdate();
                }
                catch (SQLException sqle) {
                    Log.error(sqle);
                }
                finally {
                    try { if (pstmt != null) pstmt.close(); }
                    catch (Exception e) { Log.error(e); }
                    try { if (con != null) con.close(); }
                    catch (Exception e) { Log.error(e); }
                }
            }
            else {
                // Remove the user from the generic affiliations table
                Connection con = null;
                PreparedStatement pstmt = null;
                try {
                    con = DbConnectionManager.getConnection();
                    pstmt = con.prepareStatement(DELETE_AFFILIATION);
                    pstmt.setLong(1, room.getID());
                    pstmt.setString(2, bareJID);
                    pstmt.executeUpdate();
                }
                catch (SQLException sqle) {
                    Log.error(sqle);
                }
                finally {
                    try { if (pstmt != null) pstmt.close(); }
                    catch (Exception e) { Log.error(e); }
                    try { if (con != null) con.close(); }
                    catch (Exception e) { Log.error(e); }
                }
            }
        }
    }

    /**
     * Removes the affiliation of the user from the DB if ANY room that is persistent.
     *
     * @param bareJID The bareJID of the user to remove his affiliation from ALL persistent rooms.
     */
    public static void removeAffiliationFromDB(JID bareJID)
    {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            // Remove the user from the members table
            pstmt = con.prepareStatement(DELETE_USER_MEMBER);
            pstmt.setString(1, bareJID.toBareJID());
            pstmt.executeUpdate();
            pstmt.close();
                                       
            // Remove the user from the generic affiliations table
            pstmt = con.prepareStatement(DELETE_USER_MUCAFFILIATION);
            pstmt.setString(1, bareJID.toBareJID());
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
    }

    /**
     * Saves the conversation log entry to the database.
     * 
     * @param entry the ConversationLogEntry to save to the database.
     * @return true if the ConversationLogEntry was saved successfully to the database.
     */
    public static boolean saveConversationLogEntry(ConversationLogEntry entry) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ADD_CONVERSATION_LOG);
            pstmt.setLong(1, entry.getRoomID());
            pstmt.setString(2, entry.getSender().toString());
            pstmt.setString(3, entry.getNickname());
            pstmt.setString(4, StringUtils.dateToMillis(entry.getDate()));
            pstmt.setString(5, entry.getSubject());
            pstmt.setString(6, entry.getBody());
            pstmt.executeUpdate();
            return true;
        }
        catch (SQLException sqle) {
            Log.error("Error saving conversation log entry", sqle);
            return false;
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
    }

    /**
     * Returns an integer based on the binary representation of the roles to broadcast.
     * 
     * @param room the room to marshall its roles to broadcast.
     * @return an integer based on the binary representation of the roles to broadcast.
     */
    private static int marshallRolesToBroadcast(MUCRoom room) {
        StringBuilder buffer = new StringBuilder();
        buffer.append((room.canBroadcastPresence("moderator") ? "1" : "0"));
        buffer.append((room.canBroadcastPresence("participant") ? "1" : "0"));
        buffer.append((room.canBroadcastPresence("visitor") ? "1" : "0"));
        return Integer.parseInt(buffer.toString(), 2);
    }

    /**
     * Returns a Jive property.
     *
     * @param subdomain the subdomain of the service to retrieve a property from
     * @param name the name of the property to return.
     * @return the property value specified by name.
     */
    public static String getProperty(String subdomain, String name) {
        MUCServiceProperties properties = propertyMaps.get(subdomain);
        if (properties == null) {
            properties = new MUCServiceProperties(subdomain);
            propertyMaps.put(subdomain, properties);
        }
        return properties.get(name);
    }

    /**
     * Returns a Jive property. If the specified property doesn't exist, the
     * <tt>defaultValue</tt> will be returned.
     *
     * @param subdomain the subdomain of the service to retrieve a property from
     * @param name the name of the property to return.
     * @param defaultValue value returned if the property doesn't exist.
     * @return the property value specified by name.
     */
    public static String getProperty(String subdomain, String name, String defaultValue) {
        MUCServiceProperties properties = propertyMaps.get(subdomain);
        if (properties == null) {
            properties = new MUCServiceProperties(subdomain);
            propertyMaps.put(subdomain, properties);
        }
        String value = properties.get(name);
        if (value != null) {
            return value;
        }
        else {
            return defaultValue;
        }
    }

    /**
     * Returns an integer value Jive property. If the specified property doesn't exist, the
     * <tt>defaultValue</tt> will be returned.
     *
     * @param subdomain the subdomain of the service to retrieve a property from
     * @param name the name of the property to return.
     * @param defaultValue value returned if the property doesn't exist or was not
     *      a number.
     * @return the property value specified by name or <tt>defaultValue</tt>.
     */
    public static int getIntProperty(String subdomain, String name, int defaultValue) {
        String value = getProperty(subdomain, name);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            }
            catch (NumberFormatException nfe) {
                // Ignore.
            }
        }
        return defaultValue;
    }

    /**
     * Returns a long value Jive property. If the specified property doesn't exist, the
     * <tt>defaultValue</tt> will be returned.
     *
     * @param subdomain the subdomain of the service to retrieve a property from
     * @param name the name of the property to return.
     * @param defaultValue value returned if the property doesn't exist or was not
     *      a number.
     * @return the property value specified by name or <tt>defaultValue</tt>.
     */
    public static long getLongProperty(String subdomain, String name, long defaultValue) {
        String value = getProperty(subdomain, name);
        if (value != null) {
            try {
                return Long.parseLong(value);
            }
            catch (NumberFormatException nfe) {
                // Ignore.
            }
        }
        return defaultValue;
    }

    /**
     * Returns a boolean value Jive property.
     *
     * @param subdomain the subdomain of the service to retrieve a property from
     * @param name the name of the property to return.
     * @return true if the property value exists and is set to <tt>"true"</tt> (ignoring case).
     *      Otherwise <tt>false</tt> is returned.
     */
    public static boolean getBooleanProperty(String subdomain, String name) {
        return Boolean.valueOf(getProperty(subdomain, name));
    }

    /**
     * Returns a boolean value Jive property. If the property doesn't exist, the <tt>defaultValue</tt>
     * will be returned.
     *
     * If the specified property can't be found, or if the value is not a number, the
     * <tt>defaultValue</tt> will be returned.
     *
     * @param subdomain the subdomain of the service to retrieve a property from
     * @param name the name of the property to return.
     * @param defaultValue value returned if the property doesn't exist.
     * @return true if the property value exists and is set to <tt>"true"</tt> (ignoring case).
     *      Otherwise <tt>false</tt> is returned.
     */
    public static boolean getBooleanProperty(String subdomain, String name, boolean defaultValue) {
        String value = getProperty(subdomain, name);
        if (value != null) {
            return Boolean.valueOf(value);
        }
        else {
            return defaultValue;
        }
    }

    /**
     * Return all immediate children property names of a parent Jive property as a list of strings,
     * or an empty list if there are no children. For example, given
     * the properties <tt>X.Y.A</tt>, <tt>X.Y.B</tt>, <tt>X.Y.C</tt> and <tt>X.Y.C.D</tt>, then
     * the immediate child properties of <tt>X.Y</tt> are <tt>A</tt>, <tt>B</tt>, and
     * <tt>C</tt> (<tt>C.D</tt> would not be returned using this method).<p>
     *
     * @param subdomain the subdomain of the service to retrieve a property from
     * @param parent the root "node" of the properties to retrieve
     * @return a List of all immediate children property names (Strings).
     */
    public static List<String> getPropertyNames(String subdomain, String parent) {
        MUCServiceProperties properties = propertyMaps.get(subdomain);
        if (properties == null) {
            properties = new MUCServiceProperties(subdomain);
            propertyMaps.put(subdomain, properties);
        }
        return new ArrayList<String>(properties.getChildrenNames(parent));
    }

    /**
     * Return all immediate children property values of a parent Jive property as a list of strings,
     * or an empty list if there are no children. For example, given
     * the properties <tt>X.Y.A</tt>, <tt>X.Y.B</tt>, <tt>X.Y.C</tt> and <tt>X.Y.C.D</tt>, then
     * the immediate child properties of <tt>X.Y</tt> are <tt>X.Y.A</tt>, <tt>X.Y.B</tt>, and
     * <tt>X.Y.C</tt> (the value of <tt>X.Y.C.D</tt> would not be returned using this method).<p>
     *
     * @param subdomain the subdomain of the service to retrieve a property from
     * @param parent the name of the parent property to return the children for.
     * @return all child property values for the given parent.
     */
    public static List<String> getProperties(String subdomain, String parent) {
        MUCServiceProperties properties = propertyMaps.get(subdomain);
        if (properties == null) {
            properties = new MUCServiceProperties(subdomain);
            propertyMaps.put(subdomain, properties);
        }

        Collection<String> propertyNames = properties.getChildrenNames(parent);
        List<String> values = new ArrayList<String>();
        for (String propertyName : propertyNames) {
            String value = getProperty(subdomain, propertyName);
            if (value != null) {
                values.add(value);
            }
        }

        return values;
    }

    /**
     * Returns all MUC service property names.
     *
     * @param subdomain the subdomain of the service to retrieve a property from
     * @return a List of all property names (Strings).
     */
    public static List<String> getPropertyNames(String subdomain) {
        MUCServiceProperties properties = propertyMaps.get(subdomain);
        if (properties == null) {
            properties = new MUCServiceProperties(subdomain);
            propertyMaps.put(subdomain, properties);
        }
        return new ArrayList<String>(properties.getPropertyNames());
    }

    /**
     * Sets a Jive property. If the property doesn't already exists, a new
     * one will be created.
     *
     * @param subdomain the subdomain of the service to set a property for
     * @param name the name of the property being set.
     * @param value the value of the property being set.
     */
    public static void setProperty(String subdomain, String name, String value) {
        MUCServiceProperties properties = propertyMaps.get(subdomain);
        if (properties == null) {
            properties = new MUCServiceProperties(subdomain);
        }
        properties.put(name, value);
        propertyMaps.put(subdomain, properties);
    }

    public static void setLocalProperty(String subdomain, String name, String value) {
        MUCServiceProperties properties = propertyMaps.get(subdomain);
        if (properties == null) {
            properties = new MUCServiceProperties(subdomain);
        }
        properties.localPut(name, value);
        propertyMaps.put(subdomain, properties);
    }

   /**
     * Sets multiple Jive properties at once. If a property doesn't already exists, a new
     * one will be created.
     *
    * @param subdomain the subdomain of the service to set properties for
     * @param propertyMap a map of properties, keyed on property name.
     */
    public static void setProperties(String subdomain, Map<String, String> propertyMap) {
        MUCServiceProperties properties = propertyMaps.get(subdomain);
        if (properties == null) {
            properties = new MUCServiceProperties(subdomain);
        }
        properties.putAll(propertyMap);
        propertyMaps.put(subdomain, properties);
    }

    /**
     * Deletes a Jive property. If the property doesn't exist, the method
     * does nothing. All children of the property will be deleted as well.
     *
     * @param subdomain the subdomain of the service to delete a property from
     * @param name the name of the property to delete.
     */
    public static void deleteProperty(String subdomain, String name) {
        MUCServiceProperties properties = propertyMaps.get(subdomain);
        if (properties == null) {
            properties = new MUCServiceProperties(subdomain);
        }
        properties.remove(name);
        propertyMaps.put(subdomain, properties);
    }

    public static void deleteLocalProperty(String subdomain, String name) {
        MUCServiceProperties properties = propertyMaps.get(subdomain);
        if (properties == null) {
            properties = new MUCServiceProperties(subdomain);
        }
        properties.localRemove(name);
        propertyMaps.put(subdomain, properties);
    }

    /**
     * Resets (reloads) the properties for a specified subdomain.
     *
     * @param subdomain the subdomain of the service to reload properties for.
     */
    public static void refreshProperties(String subdomain) {
        propertyMaps.replace(subdomain, new MUCServiceProperties(subdomain));
    }
    
}