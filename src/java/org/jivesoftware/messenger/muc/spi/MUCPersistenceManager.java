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

package org.jivesoftware.messenger.muc.spi;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.messenger.muc.MUCRole;
import org.jivesoftware.messenger.muc.MUCRoom;
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

    private static final String LOAD_ROOM =
        "SELECT roomID, description, canChangeSubject, maxUsers, publicRoom, " +
        "moderated, invitationRequired, canInvite, passwordProtected, " +
        "password, canDiscoverJID, logEnabled, subject, rolesToBroadcast " +
        "FROM mucRoom WHERE name=?";
    private static final String LOAD_AFFILIATIONS =
        "SELECT jid,affiliation FROM mucAffiliation WHERE roomID=?";
    private static final String LOAD_MEMBERS =
        "SELECT jid, nickname FROM mucMember WHERE roomID=?";
    private static final String UPDATE_ROOM = 
        "UPDATE mucRoom SET name=?, description=?, canChangeSubject=?, maxUsers=?, publicRoom=?, " +
        "moderated=?, invitationRequired=?, canInvite=?, passwordProtected=?, password=?, " +
        "canDiscoverJID=?, logEnabled=?, rolesToBroadcast=?, inMemory=? WHERE roomID=?";
    private static final String ADD_ROOM = 
        "INSERT INTO mucRoom (roomID, name, description, canChangeSubject, maxUsers, publicRoom, " +
        "moderated, invitationRequired, canInvite, passwordProtected, password, canDiscoverJID, " +
        "logEnabled, subject, rolesToBroadcast, inMemory) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    private static final String UPDATE_SUBJECT =
        "UPDATE mucRoom SET subject=? WHERE roomID=?";
    private static final String UPDATE_IN_MEMORY =
        "UPDATE mucRoom SET inMemory=? WHERE roomID=?";
    private static final String RESET_IN_MEMORY =
        "UPDATE mucRoom SET inMemory=0 WHERE inMemory=1";
    private static final String DELETE_ROOM =
        "DELETE FROM mucRoom WHERE roomID=?";
    private static final String DELETE_AFFILIATIONS =
        "DELETE FROM mucAffiliation WHERE roomID=?";
    private static final String DELETE_MEMBERS =
        "DELETE FROM mucMember WHERE roomID=?";
    private static final String ADD_MEMBER =
        "INSERT INTO mucMember (roomID,jid,nickname) VALUES (?,?,?)";
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
     * Loads the room configuration from the database if the room was persistent.
     * 
     * @param room the room to load from the database if persistent
     */
    public static void loadFromDB(MUCRoom room) {
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
            room.setDescription(rs.getString(2));
            room.setCanOccupantsChangeSubject(rs.getInt(3) == 1 ? true : false);
            room.setMaxUsers(rs.getInt(4));
            room.setPublicRoom(rs.getInt(5) == 1 ? true : false);
            room.setModerated(rs.getInt(6) == 1 ? true : false);
            room.setInvitationRequiredToEnter(rs.getInt(7) == 1 ? true : false);
            room.setCanOccupantsInvite(rs.getInt(8) == 1 ? true : false);
            room.setPasswordProtected(rs.getInt(9) == 1 ? true : false);
            room.setPassword(rs.getString(10));
            room.setCanAnyoneDiscoverJID(rs.getInt(11) == 1 ? true : false);
            room.setLogEnabled(rs.getInt(12) == 1 ? true : false);
            room.setSubject(rs.getString(13));
            List rolesToBroadcast = new ArrayList();
            String roles = Integer.toBinaryString(rs.getInt(14));
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
    public static void saveToDB(MUCRoom room) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            if (room.wasSavedToDB()) {
                pstmt = con.prepareStatement(UPDATE_ROOM);
                pstmt.setString(1, room.getName());
                pstmt.setString(2, room.getDescription());
                pstmt.setInt(3, (room.canOccupantsChangeSubject() ? 1 : 0));
                pstmt.setInt(4, room.getMaxUsers());
                pstmt.setInt(5, (room.isPublicRoom() ? 1 : 0));
                pstmt.setInt(6, (room.isModerated() ? 1 : 0));
                pstmt.setInt(7, (room.isInvitationRequiredToEnter() ? 1 : 0));
                pstmt.setInt(8, (room.canOccupantsInvite() ? 1 : 0));
                pstmt.setInt(9, (room.isPasswordProtected() ? 1 : 0));
                pstmt.setString(10, room.getPassword());
                pstmt.setInt(11, (room.canAnyoneDiscoverJID() ? 1 : 0));
                pstmt.setInt(12, (room.isLogEnabled() ? 1 : 0));
                pstmt.setInt(13, marshallRolesToBroadcast(room));
                pstmt.setInt(14, 1);
                pstmt.setLong(15, room.getID());
                pstmt.executeUpdate();
            }
            else {
                pstmt = con.prepareStatement(ADD_ROOM);
                pstmt.setLong(1, room.getID());
                pstmt.setString(2, room.getName());
                pstmt.setString(3, room.getDescription());
                pstmt.setInt(4, (room.canOccupantsChangeSubject() ? 1 : 0));
                pstmt.setInt(5, room.getMaxUsers());
                pstmt.setInt(6, (room.isPublicRoom() ? 1 : 0));
                pstmt.setInt(7, (room.isModerated() ? 1 : 0));
                pstmt.setInt(8, (room.isInvitationRequiredToEnter() ? 1 : 0));
                pstmt.setInt(9, (room.canOccupantsInvite() ? 1 : 0));
                pstmt.setInt(10, (room.isPasswordProtected() ? 1 : 0));
                pstmt.setString(11, room.getPassword());
                pstmt.setInt(12, (room.canAnyoneDiscoverJID() ? 1 : 0));
                pstmt.setInt(13, (room.isLogEnabled() ? 1 : 0));
                pstmt.setString(14, room.getSubject());
                pstmt.setInt(15, marshallRolesToBroadcast(room));
                pstmt.setInt(16, 1); // the room starts always "in memory"
                pstmt.execute();
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
            pstmt.execute();
            pstmt.close();

            pstmt = con.prepareStatement(DELETE_MEMBERS);
            pstmt.setLong(1, room.getID());
            pstmt.execute();
            pstmt.close();

            pstmt = con.prepareStatement(DELETE_ROOM);
            pstmt.setLong(1, room.getID());
            pstmt.execute();

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
     * Updates the in-memmory status of the room in the database.
     * 
     * @param room the room to update its in-memory status.
     * @param inMemory boolean that indicates whether the room is available in memory or not. 
     */
    public static void updateRoomInMemory(MUCRoom room, boolean inMemory) {
        if (!room.isPersistent() || !room.wasSavedToDB()) {
            return;
        }

        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_IN_MEMORY);
            pstmt.setBoolean(1, inMemory);
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
     * Updates the in-memmory status of ALL the rooms in the database to false. This is necessary
     * in case the Multi-User Chat service went down unexpectedly. This query will be executed when
     * the service is starting up (again).
     */
    public static void resetRoomInMemory() {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(RESET_IN_MEMORY);
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
     * Updates the room's subject in the database. 
     * 
     * @param room the room to update its subject in the database.
     * @param subject the new subject of the room.
     */
    public static void updateRoomSubject(MUCRoom room, String subject) {
        if (!room.isPersistent() || !room.wasSavedToDB()) {
            return;
        }

        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_SUBJECT);
            pstmt.setString(1, subject);
            pstmt.setLong(2, room.getID());

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
                    pstmt.execute();
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
                    pstmt.execute();
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
            if (MUCRole.MEMBER == newAffiliation) {
                Connection con = null;
                PreparedStatement pstmt = null;
                boolean abortTransaction = false;
                try {
                    // Remove the user from the generic affiliations table
                    con = DbConnectionManager.getTransactionConnection();
                    pstmt = con.prepareStatement(DELETE_AFFILIATION);
                    pstmt.setLong(1, room.getID());
                    pstmt.setString(2, bareJID);
                    pstmt.execute();
                    pstmt.close();

                    // Add them as a member.
                    pstmt = con.prepareStatement(ADD_MEMBER);
                    pstmt.setLong(1, room.getID());
                    pstmt.setString(2, bareJID);
                    pstmt.setString(3, nickname);
                    pstmt.execute();
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
                    pstmt.execute();
                    pstmt.close();

                    pstmt = con.prepareStatement(ADD_AFFILIATION);
                    pstmt.setLong(1, room.getID());
                    pstmt.setString(2, bareJID);
                    pstmt.setInt(3, newAffiliation);
                    pstmt.execute();
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
                    pstmt.execute();
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
                    pstmt.execute();
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
                    pstmt.execute();
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
            pstmt.setString(2, entry.getSender().toStringPrep());
            pstmt.setString(3, entry.getNickname());
            pstmt.setString(4, StringUtils.dateToMillis(entry.getDate()));
            pstmt.setString(5, entry.getSubject());
            pstmt.setString(6, entry.getBody());
            pstmt.execute();
            return true;
        }
        catch (SQLException sqle) {
            Log.error(sqle);
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