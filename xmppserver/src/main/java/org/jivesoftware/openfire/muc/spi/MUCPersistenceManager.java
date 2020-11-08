/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.muc.spi;

import java.math.BigInteger;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.database.SequenceManager;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.group.GroupJID;
import org.jivesoftware.openfire.muc.*;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import static org.jivesoftware.openfire.muc.spi.FMUCMode.MasterMaster;

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

    private static final Logger Log = LoggerFactory.getLogger(MUCPersistenceManager.class);
    
    // property name for optional number of days to limit persistent MUC history during reload (OF-764)
    private static final String MUC_HISTORY_RELOAD_LIMIT = "xmpp.muc.history.reload.limit";

    private static final String GET_RESERVED_NAME =
        "SELECT nickname FROM ofMucMember WHERE roomID=? AND jid=?";
    private static final String LOAD_ROOM =
        "SELECT roomID, creationDate, modificationDate, naturalName, description, lockedDate, " +
        "emptyDate, canChangeSubject, maxUsers, publicRoom, moderated, membersOnly, canInvite, " +
        "roomPassword, canDiscoverJID, logEnabled, subject, rolesToBroadcast, useReservedNick, " +
        "canChangeNick, canRegister, allowpm, fmucEnabled, fmucOutboundNode, fmucOutboundMode, " +
        "fmucInboundNodes " +
        " FROM ofMucRoom WHERE serviceID=? AND name=?";
    private static final String LOAD_AFFILIATIONS =
        "SELECT jid, affiliation FROM ofMucAffiliation WHERE roomID=?";
    private static final String LOAD_MEMBERS =
        "SELECT jid, nickname FROM ofMucMember WHERE roomID=?";
    private static final String LOAD_HISTORY =
        "SELECT sender, nickname, logTime, subject, body, stanza FROM ofMucConversationLog " +
        "WHERE logTime>? AND roomID=? AND (nickname IS NOT NULL OR subject IS NOT NULL) ORDER BY logTime";
    private static final String RELOAD_ALL_ROOMS_WITH_RECENT_ACTIVITY =
        "SELECT roomID, creationDate, modificationDate, name, naturalName, description, " +
        "lockedDate, emptyDate, canChangeSubject, maxUsers, publicRoom, moderated, membersOnly, " +
        "canInvite, roomPassword, canDiscoverJID, logEnabled, subject, rolesToBroadcast, " +
        "useReservedNick, canChangeNick, canRegister, allowpm, fmucEnabled, fmucOutboundNode, " +
        "fmucOutboundMode, fmucInboundNodes " +
        "FROM ofMucRoom WHERE serviceID=? AND (emptyDate IS NULL or emptyDate > ?)";
    private static final String LOAD_ALL_ROOMS =
        "SELECT roomID, creationDate, modificationDate, name, naturalName, description, " +
        "lockedDate, emptyDate, canChangeSubject, maxUsers, publicRoom, moderated, membersOnly, " +
        "canInvite, roomPassword, canDiscoverJID, logEnabled, subject, rolesToBroadcast, " +
        "useReservedNick, canChangeNick, canRegister, allowpm, fmucEnabled, fmucOutboundNode, " +
        "fmucOutboundMode, fmucInboundNodes " +
        "FROM ofMucRoom WHERE serviceID=?";
    private static final String LOAD_ALL_AFFILIATIONS =
        "SELECT ofMucAffiliation.roomID AS roomID, ofMucAffiliation.jid AS jid, ofMucAffiliation.affiliation AS affiliation " +
        "FROM ofMucAffiliation,ofMucRoom WHERE ofMucAffiliation.roomID = ofMucRoom.roomID AND ofMucRoom.serviceID=?";
    private static final String LOAD_ALL_MEMBERS =
        "SELECT ofMucMember.roomID AS roomID, ofMucMember.jid AS jid, ofMucMember.nickname AS nickname FROM ofMucMember,ofMucRoom " +
        "WHERE ofMucMember.roomID = ofMucRoom.roomID AND ofMucRoom.serviceID=?";
    private static final String LOAD_ALL_HISTORY =
        "SELECT ofMucConversationLog.roomID AS roomID, ofMucConversationLog.sender AS sender, ofMucConversationLog.nickname AS nickname, " +
        "ofMucConversationLog.logTime AS logTime, ofMucConversationLog.subject AS subject, ofMucConversationLog.body AS body, ofMucConversationLog.stanza AS stanza FROM " +
        "ofMucConversationLog, ofMucRoom WHERE ofMucConversationLog.roomID = ofMucRoom.roomID AND " +
        "ofMucRoom.serviceID=? AND ofMucConversationLog.logTime>? AND (ofMucConversationLog.nickname IS NOT NULL " +
        "OR ofMucConversationLog.subject IS NOT NULL) ORDER BY ofMucConversationLog.logTime";
    private static final String UPDATE_ROOM =
        "UPDATE ofMucRoom SET modificationDate=?, naturalName=?, description=?, " +
        "canChangeSubject=?, maxUsers=?, publicRoom=?, moderated=?, membersOnly=?, " +
        "canInvite=?, roomPassword=?, canDiscoverJID=?, logEnabled=?, rolesToBroadcast=?, " +
        "useReservedNick=?, canChangeNick=?, canRegister=?, allowpm=?, fmucEnabled=?, " +
        "fmucOutboundNode=?, fmucOutboundMode=?, fmucInboundNodes=? " +
        "WHERE roomID=?";
    private static final String ADD_ROOM = 
        "INSERT INTO ofMucRoom (serviceID, roomID, creationDate, modificationDate, name, naturalName, " +
        "description, lockedDate, emptyDate, canChangeSubject, maxUsers, publicRoom, moderated, " +
        "membersOnly, canInvite, roomPassword, canDiscoverJID, logEnabled, subject, " +
        "rolesToBroadcast, useReservedNick, canChangeNick, canRegister, allowpm, fmucEnabled, fmucOutboundNode, " +
        "fmucOutboundMode, fmucInboundNodes) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
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
        "INSERT INTO ofMucConversationLog (roomID,messageID,sender,nickname,logTime,subject,body,stanza) VALUES (?,?,?,?,?,?,?,?)";

    /* Map of subdomains to their associated properties */
    private static ConcurrentHashMap<String,MUCServiceProperties> propertyMaps = new ConcurrentHashMap<>();

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
        ResultSet rs = null;
        String answer = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GET_RESERVED_NAME);
            pstmt.setLong(1, room.getID());
            pstmt.setString(2, bareJID);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                answer = rs.getString("nickname");
            }
        }
        catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
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
        ResultSet rs = null;
        try {
            Long serviceID = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatServiceID(room.getMUCService().getServiceName());
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_ROOM);
            pstmt.setLong(1, serviceID);
            pstmt.setString(2, room.getName());
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new IllegalArgumentException("Room " + room.getName() + " was not found in the database.");
            }
            room.setID(rs.getLong("roomID"));
            room.setCreationDate(new Date(Long.parseLong(rs.getString("creationDate").trim())));
            room.setModificationDate(new Date(Long.parseLong(rs.getString("modificationDate").trim())));
            room.setNaturalLanguageName(rs.getString("naturalName"));
            room.setDescription(rs.getString("description"));
            room.setLockedDate(new Date(Long.parseLong(rs.getString("lockedDate").trim())));
            if (rs.getString("emptyDate") != null) {
                room.setEmptyDate(new Date(Long.parseLong(rs.getString("emptyDate").trim())));
            }
            else {
                room.setEmptyDate(null);
            }
            room.setCanOccupantsChangeSubject(rs.getInt("canChangeSubject") == 1);
            room.setMaxUsers(rs.getInt("maxUsers"));
            room.setPublicRoom(rs.getInt("publicRoom") == 1);
            room.setModerated(rs.getInt("moderated") == 1);
            room.setMembersOnly(rs.getInt("membersOnly") == 1);
            room.setCanOccupantsInvite(rs.getInt("canInvite") == 1);
            room.setPassword(rs.getString("roomPassword"));
            room.setCanAnyoneDiscoverJID(rs.getInt("canDiscoverJID") == 1);
            room.setLogEnabled(rs.getInt("logEnabled") == 1);
            room.setSubject(rs.getString("subject"));
            List<String> rolesToBroadcast = new ArrayList<>();
            String roles = StringUtils.zeroPadString(Integer.toBinaryString(rs.getInt("rolesToBroadcast")), 3);
            if (roles.charAt(0) == '1') {
                rolesToBroadcast.add("moderator");
            }
            if (roles.charAt(1) == '1') {
                rolesToBroadcast.add("participant");
            }
            if (roles.charAt(2) == '1') {
                rolesToBroadcast.add("visitor");
            }
            room.setRolesToBroadcastPresence(rolesToBroadcast);
            room.setLoginRestrictedToNickname(rs.getInt("useReservedNick") == 1);
            room.setChangeNickname(rs.getInt("canChangeNick") == 1);
            room.setRegistrationEnabled(rs.getInt("canRegister") == 1);
            switch (rs.getInt("allowpm")) // null returns 0.
            {
                default:
                case 0: room.setCanSendPrivateMessage( "anyone"       ); break;
                case 1: room.setCanSendPrivateMessage( "participants" ); break;
                case 2: room.setCanSendPrivateMessage( "moderators"   ); break;
                case 3: room.setCanSendPrivateMessage( "none"         ); break;
            }
            room.setFmucEnabled(rs.getInt("fmucEnabled") == 1);

            if ( rs.getString("fmucOutboundNode") != null ) {
                final JID fmucOutboundNode = new JID(rs.getString("fmucOutboundNode"));
                final FMUCMode fmucOutboundJoinMode;
                switch (rs.getInt("fmucOutboundMode")) // null returns 0.
                {
                    default:
                    case 0: fmucOutboundJoinMode = MasterMaster; break;
                    case 1: fmucOutboundJoinMode = FMUCMode.MasterSlave; break;
                }
                room.setFmucOutboundNode( fmucOutboundNode );
                room.setFmucOutboundMode( fmucOutboundJoinMode );
            } else {
                room.setFmucOutboundNode( null );
                room.setFmucOutboundMode( null );
            }
            if ( rs.getString("fmucInboundNodes") != null ) {
                final Set<JID> fmucInboundNodes = Stream.of(rs.getString("fmucInboundNodes").split("\n"))
                                                        .map(String::trim)
                                                        .map(JID::new)
                                                        .collect(Collectors.toSet());
                // A list, which is an 'allow only on list' configuration. Note that the list can be empty (effectively: disallow all).
                room.setFmucInboundNodes(fmucInboundNodes);
            } else {
                // Null: this is an 'allow all' configuration.
                room.setFmucInboundNodes(null);
            }

            room.setPersistent(true);
            DbConnectionManager.fastcloseStmt(rs, pstmt);

            // Recreate the history only for the rooms that have the conversation logging
            // enabled
            if (room.isLogEnabled()) {
                pstmt = con.prepareStatement(LOAD_HISTORY);
                // Reload the history, using "muc.history.reload.limit" (days); defaults to 2
                int reloadLimitDays = JiveGlobals.getIntProperty(MUC_HISTORY_RELOAD_LIMIT, 2);
                long from = System.currentTimeMillis() - (BigInteger.valueOf(86400000).multiply(BigInteger.valueOf(reloadLimitDays))).longValue();
                pstmt.setString(1, StringUtils.dateToMillis(new Date(from)));
                pstmt.setLong(2, room.getID());
                rs = pstmt.executeQuery();
                while (rs.next()) {
                    String senderJID = rs.getString("sender");
                    String nickname = rs.getString("nickname");
                    Date sentDate = new Date(Long.parseLong(rs.getString("logTime").trim()));
                    String subject = rs.getString("subject");
                    String body = rs.getString("body");
                    String stanza = rs.getString("stanza");
                    room.getRoomHistory().addOldMessage(senderJID, nickname, sentDate, subject,
                            body, stanza);
                }
            }
            DbConnectionManager.fastcloseStmt(rs, pstmt);

            // If the room does not include the last subject in the history then recreate one if
            // possible
            if (!room.getRoomHistory().hasChangedSubject() && room.getSubject() != null &&
                    room.getSubject().length() > 0) {
                room.getRoomHistory().addOldMessage(room.getRole().getRoleAddress().toString(),
                        null, room.getModificationDate(), room.getSubject(), null, null);
            }

            pstmt = con.prepareStatement(LOAD_AFFILIATIONS);
            pstmt.setLong(1, room.getID());
            rs = pstmt.executeQuery();
            while (rs.next()) {
                // might be a group JID
                JID affiliationJID = GroupJID.fromString(rs.getString("jid"));
                MUCRole.Affiliation affiliation = MUCRole.Affiliation.valueOf(rs.getInt("affiliation"));
                try {
                    switch (affiliation) {
                        case owner:
                            room.addOwner(affiliationJID, room.getRole());
                            break;
                        case admin:
                            room.addAdmin(affiliationJID, room.getRole());
                            break;
                        case outcast:
                            room.addOutcast(affiliationJID, null, room.getRole());
                            break;
                        default:
                            Log.error("Unkown affiliation value " + affiliation + " for user "
                                    + affiliationJID.toBareJID() + " in persistent room " + room.getID());
                    }
                }
                catch (Exception e) {
                    Log.error(e.getMessage(), e);
                }
            }
            DbConnectionManager.fastcloseStmt(rs, pstmt);
            
            pstmt = con.prepareStatement(LOAD_MEMBERS);
            pstmt.setLong(1, room.getID());
            rs = pstmt.executeQuery();
            while (rs.next()) {
                try {
                    room.addMember(new JID(rs.getString("jid")), rs.getString("nickname"), room.getRole());
                }
                catch (Exception e) {
                    Log.error(e.getMessage(), e);
                }
            }
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
            Log.error(sqle.getMessage(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
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
                switch (room.canSendPrivateMessage())
                {
                    default:
                    case "anyone":       pstmt.setInt(17, 0); break;
                    case "participants": pstmt.setInt(17, 1); break;
                    case "moderators":   pstmt.setInt(17, 2); break;
                    case "none":         pstmt.setInt(17, 3); break;
                }
                pstmt.setInt(18, (room.isFmucEnabled() ? 1 : 0 ));
                if ( room.getFmucOutboundNode() == null ) {
                    pstmt.setNull(19, Types.VARCHAR);
                } else {
                    pstmt.setString(19, room.getFmucOutboundNode().toString());
                }
                if ( room.getFmucOutboundMode() == null ) {
                    pstmt.setNull(20, Types.INTEGER);
                } else {
                    pstmt.setInt(20, room.getFmucOutboundMode().equals(MasterMaster) ? 0 : 1);
                }

                // Store a newline-separated collection, which is an 'allow only on list' configuration. Note that the list can be empty (effectively: disallow all), or null: this is an 'allow all' configuration.
                if (room.getFmucInboundNodes() == null) {
                    pstmt.setNull(21, Types.VARCHAR); // Null: allow all.
                } else {
                    final String content = room.getFmucInboundNodes().stream().map(JID::toString).collect(Collectors.joining("\n")); // result potentially is an empty String, but will not be null.
                    pstmt.setString(21, content);
                }
                pstmt.setLong(22, room.getID());
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
                switch (room.canSendPrivateMessage())
                {
                    default:
                    case "anyone":       pstmt.setInt(24, 0); break;
                    case "participants": pstmt.setInt(24, 1); break;
                    case "moderators":   pstmt.setInt(24, 2); break;
                    case "none":         pstmt.setInt(24, 3); break;
                }
                pstmt.setInt(25, (room.isFmucEnabled() ? 1 : 0 ));
                if ( room.getFmucOutboundNode() == null ) {
                    pstmt.setNull(26, Types.VARCHAR);
                } else {
                    pstmt.setString(26, room.getFmucOutboundNode().toString());
                }
                if ( room.getFmucOutboundMode() == null ) {
                    pstmt.setNull(27, Types.INTEGER);
                } else {
                    pstmt.setInt(27, room.getFmucOutboundMode().equals(MasterMaster) ? 0 : 1);
                }

                // Store a newline-separated collection, which is an 'allow only on list' configuration. Note that the list can be empty (effectively: disallow all), or null: this is an 'allow all' configuration.
                if (room.getFmucInboundNodes() == null) {
                    pstmt.setNull(28, Types.VARCHAR); // Null: allow all.
                } else {
                    final String content = room.getFmucInboundNodes().stream().map(JID::toString).collect(Collectors.joining("\n")); // result potentially is an empty String, but will not be null.
                    pstmt.setString(28, content);
                }
                pstmt.executeUpdate();
            }
        }
        catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
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
            DbConnectionManager.fastcloseStmt(pstmt);

            pstmt = con.prepareStatement(DELETE_MEMBERS);
            pstmt.setLong(1, room.getID());
            pstmt.executeUpdate();
            DbConnectionManager.fastcloseStmt(pstmt);

            pstmt = con.prepareStatement(DELETE_ROOM);
            pstmt.setLong(1, room.getID());
            pstmt.executeUpdate();

            // Update the room (in memory) to indicate the it's no longer in the database.
            room.setSavedToDB(false);
        }
        catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
            abortTransaction = true;
        }
        finally {
            DbConnectionManager.closeStatement(pstmt);
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
        }
    }

    /**
     * Loads all the rooms that had occupants after a given date from the database. This query
     * will be executed only when the service is starting up.
     *
     * @param chatserver the chat server that will hold the loaded rooms.
     * @param cleanupDate rooms that hadn't been used before this date won't be loaded.
     * @param packetRouter the PacketRouter that loaded rooms will use to send packets.
     * @return a collection with all the persistent rooms.
     */
    public static Collection<LocalMUCRoom> loadRoomsFromDB(MultiUserChatService chatserver, Date cleanupDate, PacketRouter packetRouter) {
        Log.debug( "Loading rooms for chat service {}", chatserver.getServiceName() );
        Long serviceID = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatServiceID(chatserver.getServiceName());

        final Map<Long, LocalMUCRoom> rooms;
        try {
            rooms = loadRooms(serviceID, cleanupDate, chatserver, packetRouter);
            loadHistory(serviceID, rooms);
            loadAffiliations(serviceID, rooms);
            loadMembers(serviceID, rooms);
        }
        catch (SQLException sqle) {
            Log.error("A database error prevented MUC rooms to be loaded from the database.", sqle);
            return Collections.emptyList();
        }

        // Set now that the room's configuration is updated in the database. Note: We need to
        // set this now since otherwise the room's affiliations will be saved to the database
        // "again" while adding them to the room!
        for (final MUCRoom room : rooms.values()) {
            room.setSavedToDB(true);
            if (room.getEmptyDate() == null) {
                // The service process was killed somehow while the room was being used. Since
                // the room won't have occupants at this time we need to set the best date when
                // the last occupant left the room that we can
                room.setEmptyDate(new Date());
            }
        }
        Log.debug( "Loaded {} rooms for chat service {}", rooms.size(), chatserver.getServiceName() );
        return rooms.values();
    }

    private static Map<Long, LocalMUCRoom> loadRooms(Long serviceID, Date cleanupDate, MultiUserChatService chatserver, PacketRouter packetRouter) throws SQLException {
        final Map<Long, LocalMUCRoom> rooms = new HashMap<>();

        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            connection = DbConnectionManager.getConnection();
            if (cleanupDate!=null) 
            {
                statement = connection.prepareStatement(RELOAD_ALL_ROOMS_WITH_RECENT_ACTIVITY);
                statement.setLong(1, serviceID);
                statement.setString(2, StringUtils.dateToMillis(cleanupDate));
            }
            else
            {
                statement = connection.prepareStatement(LOAD_ALL_ROOMS);
                statement.setLong(1, serviceID);
            }
            resultSet = statement.executeQuery();

            while (resultSet.next()) {
                try {
                    LocalMUCRoom room = new LocalMUCRoom(chatserver, resultSet.getString("name"), packetRouter);
                    room.setID(resultSet.getLong("roomID"));
                    room.setCreationDate(new Date(Long.parseLong(resultSet.getString("creationDate").trim())));
                    room.setModificationDate(new Date(Long.parseLong(resultSet.getString("modificationDate").trim())));
                    room.setNaturalLanguageName(resultSet.getString("naturalName"));
                    room.setDescription(resultSet.getString("description"));
                    room.setLockedDate(new Date(Long.parseLong(resultSet.getString("lockedDate").trim())));
                    if (resultSet.getString("emptyDate") != null) {
                        room.setEmptyDate(new Date(Long.parseLong(resultSet.getString("emptyDate").trim())));
                    }
                    else {
                        room.setEmptyDate(null);
                    }
                    room.setCanOccupantsChangeSubject(resultSet.getInt("canChangeSubject") == 1);
                    room.setMaxUsers(resultSet.getInt("maxUsers"));
                    room.setPublicRoom(resultSet.getInt("publicRoom") == 1);
                    room.setModerated(resultSet.getInt("moderated") == 1);
                    room.setMembersOnly(resultSet.getInt("membersOnly") == 1);
                    room.setCanOccupantsInvite(resultSet.getInt("canInvite") == 1);
                    room.setPassword(resultSet.getString("roomPassword"));
                    room.setCanAnyoneDiscoverJID(resultSet.getInt("canDiscoverJID") == 1);
                    room.setLogEnabled(resultSet.getInt("logEnabled") == 1);
                    room.setSubject(resultSet.getString("subject"));
                    List<String> rolesToBroadcast = new ArrayList<>();
                    String roles = StringUtils.zeroPadString(Integer.toBinaryString(resultSet.getInt("rolesToBroadcast")), 3);
                    if (roles.charAt(0) == '1') {
                        rolesToBroadcast.add("moderator");
                    }
                    if (roles.charAt(1) == '1') {
                        rolesToBroadcast.add("participant");
                    }
                    if (roles.charAt(2) == '1') {
                        rolesToBroadcast.add("visitor");
                    }
                    room.setRolesToBroadcastPresence(rolesToBroadcast);
                    room.setLoginRestrictedToNickname(resultSet.getInt("useReservedNick") == 1);
                    room.setChangeNickname(resultSet.getInt("canChangeNick") == 1);
                    room.setRegistrationEnabled(resultSet.getInt("canRegister") == 1);
                    switch (resultSet.getInt("allowpm")) // null returns 0.
                    {
                        default:
                        case 0: room.setCanSendPrivateMessage( "anyone"       ); break;
                        case 1: room.setCanSendPrivateMessage( "participants" ); break;
                        case 2: room.setCanSendPrivateMessage( "moderators"   ); break;
                        case 3: room.setCanSendPrivateMessage( "none"         ); break;
                    }

                    room.setFmucEnabled(resultSet.getInt("fmucEnabled") == 1);
                    if ( resultSet.getString("fmucOutboundNode") != null ) {
                        final JID fmucOutboundNode = new JID(resultSet.getString("fmucOutboundNode"));
                        final FMUCMode fmucOutboundJoinMode;
                        switch (resultSet.getInt("fmucOutboundMode")) // null returns 0.
                        {
                            default:
                            case 0: fmucOutboundJoinMode = MasterMaster; break;
                            case 1: fmucOutboundJoinMode = FMUCMode.MasterSlave; break;
                        }
                        room.setFmucOutboundNode( fmucOutboundNode );
                        room.setFmucOutboundMode( fmucOutboundJoinMode );
                    } else {
                        room.setFmucOutboundNode( null );
                        room.setFmucOutboundMode( null );
                    }
                    if ( resultSet.getString("fmucInboundNodes") != null ) {
                        final Set<JID> fmucInboundNodes = Stream.of(resultSet.getString("fmucInboundNodes").split("\n"))
                            .map(String::trim)
                            .map(JID::new)
                            .collect(Collectors.toSet());
                        // A list, which is an 'allow only on list' configuration. Note that the list can be empty (effectively: disallow all).
                        room.setFmucInboundNodes(fmucInboundNodes);
                    } else {
                        // Null: this is an 'allow all' configuration.
                        room.setFmucInboundNodes(null);
                    }

                    room.setPersistent(true);
                    rooms.put(room.getID(), room);
                } catch (SQLException e) {
                    Log.error("A database exception prevented one particular MUC room to be loaded from the database.", e);
                }
            }
        } finally {
            DbConnectionManager.closeConnection(resultSet, statement, connection);
        }

        return rooms;
    }

    private static void loadHistory(Long serviceID, Map<Long, LocalMUCRoom> rooms) throws SQLException {
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            connection = DbConnectionManager.getConnection();
            statement = connection.prepareStatement(LOAD_ALL_HISTORY);

            // Reload the history, using "muc.history.reload.limit" (days) if present
            long from = 0;
            String reloadLimit = JiveGlobals.getProperty(MUC_HISTORY_RELOAD_LIMIT);
            if (reloadLimit != null) {
                // if the property is defined, but not numeric, default to 2 (days)
                int reloadLimitDays = JiveGlobals.getIntProperty(MUC_HISTORY_RELOAD_LIMIT, 2);
                Log.warn("MUC history reload limit set to " + reloadLimitDays + " days");
                from = System.currentTimeMillis() - (BigInteger.valueOf(86400000).multiply(BigInteger.valueOf(reloadLimitDays))).longValue();
            }
            statement.setLong(1, serviceID);
            statement.setString(2, StringUtils.dateToMillis(new Date(from)));
            resultSet = statement.executeQuery();

            while (resultSet.next()) {
                try {
                    LocalMUCRoom room = rooms.get(resultSet.getLong("roomID"));
                    // Skip to the next position if the room does not exist or if history is disabled
                    if (room == null || !room.isLogEnabled()) {
                        continue;
                    }
                    String senderJID = resultSet.getString("sender");
                    String nickname  = resultSet.getString("nickname");
                    Date sentDate    = new Date(Long.parseLong(resultSet.getString("logTime").trim()));
                    String subject   = resultSet.getString("subject");
                    String body      = resultSet.getString("body");
                    String stanza    = resultSet.getString("stanza");
                    room.getRoomHistory().addOldMessage(senderJID, nickname, sentDate, subject, body, stanza);
                } catch (SQLException e) {
                    Log.warn("A database exception prevented the history for one particular MUC room to be loaded from the database.", e);
                }
            }
        } finally {
            DbConnectionManager.closeConnection(resultSet, statement, connection);
        }

        // Add the last known room subject to the room history only for those rooms that still
        // don't have in their histories the last room subject
        for (MUCRoom loadedRoom : rooms.values())
        {
            if (!loadedRoom.getRoomHistory().hasChangedSubject()
                && loadedRoom.getSubject() != null
                && loadedRoom.getSubject().length() > 0)
            {
                loadedRoom.getRoomHistory().addOldMessage(  loadedRoom.getRole().getRoleAddress().toString(),
                                                            null,
                                                            loadedRoom.getModificationDate(),
                                                            loadedRoom.getSubject(),
                                                            null,
                                                            null);
            }
        }
    }

    private static void loadAffiliations(Long serviceID, Map<Long, LocalMUCRoom> rooms) throws SQLException {
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            connection = DbConnectionManager.getConnection();
            statement = connection.prepareStatement(LOAD_ALL_AFFILIATIONS);
            statement.setLong(1, serviceID);
            resultSet = statement.executeQuery();

            while (resultSet.next()) {
                try {
                    long roomID = resultSet.getLong("roomID");
                    LocalMUCRoom room = rooms.get(roomID);
                    // Skip to the next position if the room does not exist
                    if (room == null) {
                        continue;
                    }

                    final MUCRole.Affiliation affiliation = MUCRole.Affiliation.valueOf(resultSet.getInt("affiliation"));

                    final String jidValue = resultSet.getString("jid");
                    final JID affiliationJID;
                    try {
                        // might be a group JID
                        affiliationJID = GroupJID.fromString(jidValue);
                    } catch (IllegalArgumentException ex) {
                        Log.warn("An illegal JID ({}) was found in the database, "
                                + "while trying to load all affiliations for room "
                                + "{}. The JID is ignored."
                                , new Object[] { jidValue, roomID });
                        continue;
                    }

                    try {
                        switch (affiliation) {
                            case owner:
                                room.addOwner(affiliationJID, room.getRole());
                                break;
                            case admin:
                                room.addAdmin(affiliationJID, room.getRole());
                                break;
                            case outcast:
                                room.addOutcast(affiliationJID, null, room.getRole());
                                break;
                            default:
                                Log.error("Unknown affiliation value " + affiliation + " for user " + affiliationJID + " in persistent room " + room.getID());
                        }
                    } catch (ForbiddenException | ConflictException | NotAllowedException e) {
                        Log.warn("An exception prevented affiliations to be added to the room with id " + roomID, e);
                    }
                } catch (SQLException e) {
                    Log.error("A database exception prevented affiliations for one particular MUC room to be loaded from the database.", e);
                }
            }

        } finally {
            DbConnectionManager.closeConnection(resultSet, statement, connection);
        }
    }

    private static void loadMembers(Long serviceID, Map<Long, LocalMUCRoom> rooms) throws SQLException {
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        JID affiliationJID = null;
        try {
            connection = DbConnectionManager.getConnection();
            statement = connection.prepareStatement(LOAD_ALL_MEMBERS);
            statement.setLong(1, serviceID);
            resultSet = statement.executeQuery();

            while (resultSet.next()) {
                try {
                    LocalMUCRoom room = rooms.get(resultSet.getLong("roomID"));
                    // Skip to the next position if the room does not exist
                    if (room == null) {
                        continue;
                    }
                    try {
                        // might be a group JID
                        affiliationJID = GroupJID.fromString(resultSet.getString("jid"));
                        room.addMember(affiliationJID, resultSet.getString("nickname"), room.getRole());
                    } catch (ForbiddenException | ConflictException e) {
                        Log.warn("Unable to add member to room.", e);
                    }
                } catch (SQLException e) {
                    Log.error("A database exception prevented members for one particular MUC room to be loaded from the database.", e);
                }
            }
        } finally {
            DbConnectionManager.closeConnection(resultSet, statement, connection);
        }
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
            Log.error(sqle.getMessage(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
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
            Log.error(sqle.getMessage(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
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
            Log.error(sqle.getMessage(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /**
     * Update the DB with the new affiliation of the user in the room. The new information will be
     * saved only if the room is_persistent and has already been saved to the database previously.
     * 
     * @param room The room where the affiliation of the user was updated.
     * @param jid The bareJID of the user to update this affiliation.
     * @param nickname The reserved nickname of the user in the room or null if none.
     * @param newAffiliation the new affiliation of the user in the room.
     * @param oldAffiliation the previous affiliation of the user in the room.
     */
    public static void saveAffiliationToDB(MUCRoom room, JID jid, String nickname,
            MUCRole.Affiliation newAffiliation, MUCRole.Affiliation oldAffiliation)
    {
        final String affiliationJid = jid.toBareJID();
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
                    pstmt.setString(2, affiliationJid);
                    pstmt.setString(3, nickname);
                    pstmt.executeUpdate();
                }
                catch (SQLException sqle) {
                    Log.error(sqle.getMessage(), sqle);
                }
                finally {
                    DbConnectionManager.closeConnection(pstmt, con);
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
                    pstmt.setString(2, affiliationJid);
                    pstmt.setInt(3, newAffiliation.getValue());
                    pstmt.executeUpdate();
                }
                catch (SQLException sqle) {
                    Log.error(sqle.getMessage(), sqle);
                }
                finally {
                    DbConnectionManager.closeConnection(pstmt, con);
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
                    pstmt.setString(3, affiliationJid);
                    pstmt.executeUpdate();
                }
                catch (SQLException sqle) {
                    Log.error(sqle.getMessage(), sqle);
                }
                finally {
                    DbConnectionManager.closeConnection(pstmt, con);
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
                    pstmt.setString(2, affiliationJid);
                    pstmt.executeUpdate();
                    DbConnectionManager.fastcloseStmt(pstmt);

                    // Add them as a member.
                    pstmt = con.prepareStatement(ADD_MEMBER);
                    pstmt.setLong(1, room.getID());
                    pstmt.setString(2, affiliationJid);
                    pstmt.setString(3, nickname);
                    pstmt.executeUpdate();
                }
                catch (SQLException sqle) {
                    Log.error(sqle.getMessage(), sqle);
                    abortTransaction = true;
                }
                finally {
                    DbConnectionManager.closeStatement(pstmt);
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
                    pstmt.setString(2, affiliationJid);
                    pstmt.executeUpdate();
                    DbConnectionManager.fastcloseStmt(pstmt);

                    pstmt = con.prepareStatement(ADD_AFFILIATION);
                    pstmt.setLong(1, room.getID());
                    pstmt.setString(2, affiliationJid);
                    pstmt.setInt(3, newAffiliation.getValue());
                    pstmt.executeUpdate();
                }
                catch (SQLException sqle) {
                    Log.error(sqle.getMessage(), sqle);
                    abortTransaction = true;
                }
                finally {
                    DbConnectionManager.closeStatement(pstmt);
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
                    pstmt.setString(3, affiliationJid);
                    pstmt.executeUpdate();
                }
                catch (SQLException sqle) {
                    Log.error(sqle.getMessage(), sqle);
                }
                finally {
                    DbConnectionManager.closeConnection(pstmt, con);
                }
            }
        }
    }

    /**
     * Removes the affiliation of the user from the DB if the room is persistent.
     * 
     * @param room The room where the affiliation of the user was removed.
     * @param jid The bareJID of the user to remove his affiliation.
     * @param oldAffiliation the previous affiliation of the user in the room.
     */
    public static void removeAffiliationFromDB(MUCRoom room, JID jid,
            MUCRole.Affiliation oldAffiliation)
    {
        final String affiliationJID = jid.toBareJID();
        if (room.isPersistent() && room.wasSavedToDB()) {
            if (MUCRole.Affiliation.member == oldAffiliation) {
                // Remove the user from the members table
                Connection con = null;
                PreparedStatement pstmt = null;
                try {
                    con = DbConnectionManager.getConnection();
                    pstmt = con.prepareStatement(DELETE_MEMBER);
                    pstmt.setLong(1, room.getID());
                    pstmt.setString(2, affiliationJID);
                    pstmt.executeUpdate();
                }
                catch (SQLException sqle) {
                    Log.error(sqle.getMessage(), sqle);
                }
                finally {
                    DbConnectionManager.closeConnection(pstmt, con);
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
                    pstmt.setString(2, affiliationJID);
                    pstmt.executeUpdate();
                }
                catch (SQLException sqle) {
                    Log.error(sqle.getMessage(), sqle);
                }
                finally {
                    DbConnectionManager.closeConnection(pstmt, con);
                }
            }
        }
    }

    /**
     * Removes the affiliation of the user from the DB if ANY room that is persistent.
     *
     * @param affiliationJID The bareJID of the user to remove his affiliation from ALL persistent rooms.
     */
    public static void removeAffiliationFromDB(JID affiliationJID)
    {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            // Remove the user from the members table
            pstmt = con.prepareStatement(DELETE_USER_MEMBER);
            pstmt.setString(1, affiliationJID.toBareJID());
            pstmt.executeUpdate();
            DbConnectionManager.fastcloseStmt(pstmt);

            // Remove the user from the generic affiliations table
            pstmt = con.prepareStatement(DELETE_USER_MUCAFFILIATION);
            pstmt.setString(1, affiliationJID.toBareJID());
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /**
     * Saves the conversation log entry batch to the database.
     *
     * @param batch a list of ConversationLogEntry to save to the database.
     * @return true if the batch was saved successfully to the database.
     */
    public static boolean saveConversationLogBatch(List<ConversationLogEntry> batch) {
        Connection con = null;
        PreparedStatement pstmt = null;

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ADD_CONVERSATION_LOG);
            con.setAutoCommit(false);

            for(ConversationLogEntry entry : batch) {
                pstmt.setLong(1, entry.getRoomID());
                pstmt.setLong(2, SequenceManager.nextID(JiveConstants.MUC_MESSAGE_ID));
                pstmt.setString(3, entry.getSender().toString());
                pstmt.setString(4, entry.getNickname());
                pstmt.setString(5, StringUtils.dateToMillis(entry.getDate()));
                pstmt.setString(6, entry.getSubject());
                pstmt.setString(7, entry.getBody());
                pstmt.setString(8, entry.getStanza());
                pstmt.addBatch();
            }

            pstmt.executeBatch();
            con.commit();
            return true;
        }
        catch (SQLException sqle) {
            Log.error("Error saving conversation log batch", sqle);
            if (con != null) {
            	try {
					con.rollback();
				} catch (SQLException ignore) {}
            }
            return false;
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
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
        final MUCServiceProperties props = propertyMaps.computeIfAbsent( subdomain, MUCServiceProperties::new );
        return props.get(name);
    }

    /**
     * Returns a Jive property. If the specified property doesn't exist, the
     * {@code defaultValue} will be returned.
     *
     * @param subdomain the subdomain of the service to retrieve a property from
     * @param name the name of the property to return.
     * @param defaultValue value returned if the property doesn't exist.
     * @return the property value specified by name.
     */
    public static String getProperty(String subdomain, String name, String defaultValue) {
        final String value = getProperty(subdomain, name);
        if (value != null) {
            return value;
        } else {
            return defaultValue;
        }
    }

    /**
     * Returns an integer value Jive property. If the specified property doesn't exist, the
     * {@code defaultValue} will be returned.
     *
     * @param subdomain the subdomain of the service to retrieve a property from
     * @param name the name of the property to return.
     * @param defaultValue value returned if the property doesn't exist or was not
     *      a number.
     * @return the property value specified by name or {@code defaultValue}.
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
     * {@code defaultValue} will be returned.
     *
     * @param subdomain the subdomain of the service to retrieve a property from
     * @param name the name of the property to return.
     * @param defaultValue value returned if the property doesn't exist or was not
     *      a number.
     * @return the property value specified by name or {@code defaultValue}.
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
     * @return true if the property value exists and is set to {@code "true"} (ignoring case).
     *      Otherwise {@code false} is returned.
     */
    public static boolean getBooleanProperty(String subdomain, String name) {
        return Boolean.valueOf(getProperty(subdomain, name));
    }

    /**
     * Returns a boolean value Jive property. If the property doesn't exist, the {@code defaultValue}
     * will be returned.
     *
     * If the specified property can't be found, or if the value is not a number, the
     * {@code defaultValue} will be returned.
     *
     * @param subdomain the subdomain of the service to retrieve a property from
     * @param name the name of the property to return.
     * @param defaultValue value returned if the property doesn't exist.
     * @return true if the property value exists and is set to {@code "true"} (ignoring case).
     *      Otherwise {@code false} is returned.
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
     * the properties {@code X.Y.A}, {@code X.Y.B}, {@code X.Y.C} and {@code X.Y.C.D}, then
     * the immediate child properties of {@code X.Y} are {@code A}, {@code B}, and
     * {@code C} ({@code C.D} would not be returned using this method).<p>
     *
     * @param subdomain the subdomain of the service to retrieve a property from
     * @param parent the root "node" of the properties to retrieve
     * @return a List of all immediate children property names (Strings).
     */
    public static List<String> getPropertyNames(String subdomain, String parent) {
        final MUCServiceProperties props = propertyMaps.computeIfAbsent( subdomain, MUCServiceProperties::new );
        return new ArrayList<>(props.getChildrenNames(parent));
    }

    /**
     * Return all immediate children property values of a parent Jive property as a list of strings,
     * or an empty list if there are no children. For example, given
     * the properties {@code X.Y.A}, {@code X.Y.B}, {@code X.Y.C} and {@code X.Y.C.D}, then
     * the immediate child properties of {@code X.Y} are {@code X.Y.A}, {@code X.Y.B}, and
     * {@code X.Y.C} (the value of {@code X.Y.C.D} would not be returned using this method).<p>
     *
     * @param subdomain the subdomain of the service to retrieve a property from
     * @param parent the name of the parent property to return the children for.
     * @return all child property values for the given parent.
     */
    public static List<String> getProperties(String subdomain, String parent) {
        final MUCServiceProperties props = propertyMaps.computeIfAbsent( subdomain, MUCServiceProperties::new );
        Collection<String> propertyNames = props.getChildrenNames(parent);
        List<String> values = new ArrayList<>();
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
        final MUCServiceProperties props = propertyMaps.computeIfAbsent( subdomain, MUCServiceProperties::new );
        return new ArrayList<>(props.getPropertyNames());
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
