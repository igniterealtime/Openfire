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

package org.jivesoftware.messenger.muc.spi;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.messenger.muc.MUCRole;
import org.jivesoftware.messenger.muc.MUCRoom;
import org.jivesoftware.messenger.muc.MultiUserChatServer;
import org.jivesoftware.messenger.PacketRouter;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;

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
        "SELECT nickname FROM mucMember WHERE roomID=? AND jid=?";
    private static final String LOAD_ROOM =
        "SELECT roomID, creationDate, modificationDate, naturalName, description, lockedDate, " +
        "canChangeSubject, maxUsers, publicRoom, moderated, membersOnly, canInvite, " +
        "password, canDiscoverJID, logEnabled, subject, rolesToBroadcast " +
        "FROM mucRoom WHERE name=?";
    private static final String LOAD_AFFILIATIONS =
        "SELECT jid, affiliation FROM mucAffiliation WHERE roomID=?";
    private static final String LOAD_MEMBERS =
        "SELECT jid, nickname FROM mucMember WHERE roomID=?";
    private static final String LOAD_HISTORY =
        "SELECT sender, nickname, time, subject, body FROM mucConversationLog " +
        "WHERE time>? AND roomID=? AND (nickname != \"\" OR subject IS NOT NULL) ORDER BY time";
    private static final String LOAD_ALL_ROOMS =
        "SELECT roomID, creationDate, modificationDate, name, naturalName, description, " +
        "lockedDate, canChangeSubject, maxUsers, publicRoom, moderated, membersOnly, canInvite, " +
        "password, canDiscoverJID, logEnabled, subject, rolesToBroadcast " +
        "FROM mucRoom";
    private static final String LOAD_ALL_AFFILIATIONS =
        "SELECT roomID,jid,affiliation FROM mucAffiliation";
    private static final String LOAD_ALL_MEMBERS =
        "SELECT roomID,jid, nickname FROM mucMember";
    private static final String LOAD_ALL_HISTORY =
        "SELECT roomID, sender, nickname, time, subject, body FROM mucConversationLog " +
        "WHERE time>? AND (nickname != \"\" OR subject IS NOT NULL) ORDER BY time";
    private static final String UPDATE_ROOM =
        "UPDATE mucRoom SET modificationDate=?, naturalName=?, description=?, " +
        "canChangeSubject=?, maxUsers=?, publicRoom=?, moderated=?, membersOnly=?, " +
        "canInvite=?, password=?, canDiscoverJID=?, logEnabled=?, rolesToBroadcast=? " +
        "WHERE roomID=?";
    private static final String ADD_ROOM = 
        "INSERT INTO mucRoom (roomID, creationDate, modificationDate, name, naturalName, " +
        "description, lockedDate, canChangeSubject, maxUsers, publicRoom, moderated, " +
        "membersOnly, canInvite, password, canDiscoverJID, logEnabled, subject, rolesToBroadcast)" +
        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    private static final String UPDATE_SUBJECT =
        "UPDATE mucRoom SET subject=? WHERE roomID=?";
    private static final String UPDATE_LOCK =
        "UPDATE mucRoom SET lockedDate=? WHERE roomID=?";
    private static final String DELETE_ROOM =
        "DELETE FROM mucRoom WHERE roomID=?";
    private static final String DELETE_AFFILIATIONS =
        "DELETE FROM mucAffiliation WHERE roomID=?";
    private static final String DELETE_MEMBERS =
        "DELETE FROM mucMember WHERE roomID=?";
    private static final String ADD_MEMBER =
        "INSERT INTO mucMember (roomID,jid,nickname) VALUES (?,?,?)";
    private static final String UPDATE_MEMBER =
        "UPDATE mucMember SET nickname=? WHERE roomID=? AND jid=?";
    private static final String DELETE_MEMBER =
        "DELETE FROM mucMember WHERE roomID=? AND jid=?";
    private static final String ADD_AFFILIATION =
        "INSERT INTO mucAffiliation (roomID,jid,affiliation) VALUES (?,?,?)";
    private static final String UPDATE_AFFILIATION =
        "UPDATE mucAffiliation SET affiliation=? WHERE roomID=? AND jid=?";
    private static final String DELETE_AFFILIATION =
        "DELETE FROM mucAffiliation WHERE roomID=? AND jid=?";

    private static final String ADD_CONVERSATION_LOG =
        "INSERT INTO mucConversationLog (roomID,sender,nickname,time,subject,body) " +
        "VALUES (?,?,?,?,?,?)";

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
            pstmt.close();
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
    public static void loadFromDB(MUCRoomImpl room) {
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
            room.setCanOccupantsChangeSubject(rs.getInt(7) == 1 ? true : false);
            room.setMaxUsers(rs.getInt(8));
            room.setPublicRoom(rs.getInt(9) == 1 ? true : false);
            room.setModerated(rs.getInt(10) == 1 ? true : false);
            room.setMembersOnly(rs.getInt(11) == 1 ? true : false);
            room.setCanOccupantsInvite(rs.getInt(12) == 1 ? true : false);
            room.setPassword(rs.getString(13));
            room.setCanAnyoneDiscoverJID(rs.getInt(14) == 1 ? true : false);
            room.setLogEnabled(rs.getInt(15) == 1 ? true : false);
            room.setSubject(rs.getString(16));
            List<String> rolesToBroadcast = new ArrayList<String>();
            String roles = Integer.toBinaryString(rs.getInt(17));
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
                Date sentDate = new Date(Long.parseLong(rs.getString(3).trim()));
                // Recreate the history only for the rooms that have the conversation logging
                // enabled
                if (room.isLogEnabled()) {
                    room.getRoomHistory().addOldMessage(rs.getString(1), rs.getString(2), sentDate,
                            rs.getString(4), rs.getString(5));
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
                int affiliation = rs.getInt(2);
                try {
                    switch (affiliation) {
                        case MUCRole.OWNER:
                            room.addOwner(jid, room.getRole());
                            break;
                        case MUCRole.ADMINISTRATOR:
                            room.addAdmin(jid, room.getRole());
                            break;
                        case MUCRole.OUTCAST:
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
    public static void saveToDB(MUCRoomImpl room) {
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
                pstmt.setLong(14, room.getID());
                pstmt.executeUpdate();
            }
            else {
                pstmt = con.prepareStatement(ADD_ROOM);
                pstmt.setLong(1, room.getID());
                pstmt.setString(2, StringUtils.dateToMillis(room.getCreationDate()));
                pstmt.setString(3, StringUtils.dateToMillis(room.getModificationDate()));
                pstmt.setString(4, room.getName());
                pstmt.setString(5, room.getNaturalLanguageName());
                pstmt.setString(6, room.getDescription());
                pstmt.setString(7, StringUtils.dateToMillis(room.getLockedDate()));
                pstmt.setInt(8, (room.canOccupantsChangeSubject() ? 1 : 0));
                pstmt.setInt(9, room.getMaxUsers());
                pstmt.setInt(10, (room.isPublicRoom() ? 1 : 0));
                pstmt.setInt(11, (room.isModerated() ? 1 : 0));
                pstmt.setInt(12, (room.isMembersOnly() ? 1 : 0));
                pstmt.setInt(13, (room.canOccupantsInvite() ? 1 : 0));
                pstmt.setString(14, room.getPassword());
                pstmt.setInt(15, (room.canAnyoneDiscoverJID() ? 1 : 0));
                pstmt.setInt(16, (room.isLogEnabled() ? 1 : 0));
                pstmt.setString(17, room.getSubject());
                pstmt.setInt(18, marshallRolesToBroadcast(room));
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
     * Loads all the rooms from the database. This query will be executed only when
     * the service is started up.
     *
     * @return a collection with all the persistent rooms.
     */
    public static Collection<MUCRoom> loadRoomsFromDB(MultiUserChatServer chatserver,
            PacketRouter packetRouter) {
        Connection con = null;
        PreparedStatement pstmt = null;
        Map<Long,MUCRoom> rooms = new HashMap<Long,MUCRoom>();
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_ALL_ROOMS);
            ResultSet rs = pstmt.executeQuery();
            MUCRoomImpl room = null;
            while (rs.next()) {
                room = new MUCRoomImpl(chatserver, rs.getString(4), packetRouter);
                room.setID(rs.getLong(1));
                room.setCreationDate(new Date(Long.parseLong(rs.getString(2).trim()))); // creation date
                room.setModificationDate(new Date(Long.parseLong(rs.getString(3).trim()))); // modification date
                room.setNaturalLanguageName(rs.getString(5));
                room.setDescription(rs.getString(6));
                room.setLockedDate(new Date(Long.parseLong(rs.getString(7).trim())));
                room.setCanOccupantsChangeSubject(rs.getInt(8) == 1 ? true : false);
                room.setMaxUsers(rs.getInt(9));
                room.setPublicRoom(rs.getInt(10) == 1 ? true : false);
                room.setModerated(rs.getInt(11) == 1 ? true : false);
                room.setMembersOnly(rs.getInt(12) == 1 ? true : false);
                room.setCanOccupantsInvite(rs.getInt(13) == 1 ? true : false);
                room.setPassword(rs.getString(14));
                room.setCanAnyoneDiscoverJID(rs.getInt(15) == 1 ? true : false);
                room.setLogEnabled(rs.getInt(16) == 1 ? true : false);
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
                room.setPersistent(true);
                rooms.put(room.getID(), room);
            }
            rs.close();
            pstmt.close();

            pstmt = con.prepareStatement(LOAD_ALL_HISTORY);
            // Recreate the history until two days ago
            long from = System.currentTimeMillis() - (86400000 * 2);
            pstmt.setString(1, StringUtils.dateToMillis(new Date(from)));
            // Load the rooms conversations from the last two days
            rs = pstmt.executeQuery();
            while (rs.next()) {
                room = (MUCRoomImpl) rooms.get(rs.getLong(1));
                // Skip to the next position if the room does not exist
                if (room == null) {
                    continue;
                }
                Date sentDate = new Date(Long.parseLong(rs.getString(4).trim()));
                try {
                    // Recreate the history only for the rooms that have the conversation logging
                    // enabled
                    if (room.isLogEnabled()) {
                        room.getRoomHistory().addOldMessage(rs.getString(2), rs.getString(3),
                                sentDate, rs.getString(5), rs.getString(6));
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
            rs = pstmt.executeQuery();
            while (rs.next()) {
                String jid = rs.getString(2);
                int affiliation = rs.getInt(3);
                room = (MUCRoomImpl) rooms.get(rs.getLong(1));
                try {
                    switch (affiliation) {
                        case MUCRole.OWNER:
                            room.addOwner(jid, room.getRole());
                            break;
                        case MUCRole.ADMINISTRATOR:
                            room.addAdmin(jid, room.getRole());
                            break;
                        case MUCRole.OUTCAST:
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
            rs = pstmt.executeQuery();
            while (rs.next()) {
                room = (MUCRoomImpl) rooms.get(rs.getLong(1));
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
    public static void updateRoomLock(MUCRoomImpl room) {
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
            int newAffiliation, int oldAffiliation)
    {
        if (!room.isPersistent() || !room.wasSavedToDB()) {
            return;
        }
        if (MUCRole.NONE == oldAffiliation) {
            if (MUCRole.MEMBER == newAffiliation) {
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
                    pstmt.setInt(3, newAffiliation);
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
            if (MUCRole.MEMBER == newAffiliation && MUCRole.MEMBER == oldAffiliation) {
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
            else if (MUCRole.MEMBER == newAffiliation) {
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
            else if (MUCRole.MEMBER == oldAffiliation) {
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
                    pstmt.setInt(3, newAffiliation);
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
                    pstmt.setInt(1, newAffiliation);
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
    public static void removeAffiliationFromDB(MUCRoom room, String bareJID, int oldAffiliation) {
        if (room.isPersistent() && room.wasSavedToDB()) {
            if (MUCRole.MEMBER == oldAffiliation) {
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
        StringBuffer buffer = new StringBuffer();
        buffer.append((room.canBroadcastPresence("moderator") ? "1" : "0"));
        buffer.append((room.canBroadcastPresence("participant") ? "1" : "0"));
        buffer.append((room.canBroadcastPresence("visitor") ? "1" : "0"));
        return Integer.parseInt(buffer.toString(), 2);
    }
}