/*
 * Copyright (C) 2004-2008 Jive Software, 2016-2025 Ignite Realtime Foundation. All rights reserved.
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

import com.google.common.annotations.VisibleForTesting;
import org.dom4j.Element;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.group.GroupJID;
import org.jivesoftware.openfire.muc.*;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.NamedThreadFactory;
import org.jivesoftware.util.SAXReaderUtil;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    /**
     * Controls the number of parallel workers used when loading MUC rooms from the database at
     * service startup. A value of 1 (the default) loads rooms sequentially. Values 2-5 enable
     * parallel loading which can improve startup time for services with many rooms, but increases
     * database load. Consider your database's connection pool size and capacity before increasing
     * this value.
     */
    public static final SystemProperty<Integer> ROOM_LOADING_WORKERS = SystemProperty.Builder.ofType(Integer.class)
        .setKey("xmpp.muc.loading.workers")
        .setDynamic(false)
        .setDefaultValue(1)
        .setMinValue(1)
        .setMaxValue(5)
        .build();

    /**
     * Defines the maximum duration to wait for loading MUC rooms from the database at service startup.
     * If the loading process exceeds this timeout, it will be aborted. A value of zero (the default)
     * indicates that no timeout is configured, and the process may run indefinitely.
     */
    public static final SystemProperty<Duration> ROOM_LOADING_TIMEOUT = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.muc.loading.timeout")
        .setDynamic(false)
        .setChronoUnit(ChronoUnit.MILLIS)
        .setDefaultValue(Duration.ZERO)
        .build();

    /**
     * If true, room loading aborts on the first failure (fail-fast); if false, all rooms are attempted (resilient).
     */
    public static final SystemProperty<Boolean> ROOM_LOADING_FAILFAST = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.muc.loading.failfast")
        .setDynamic(false)
        .setDefaultValue(true)
        .build();

    private static final String GET_RESERVED_NAME =
        "SELECT nickname FROM ofMucMember WHERE roomID=? AND jid=?";
    private static final String LOAD_ROOM =
        "SELECT roomID, creationDate, modificationDate, naturalName, description, lockedDate, " +
        "emptyDate, canChangeSubject, maxUsers, publicRoom, moderated, membersOnly, canInvite, " +
        "roomPassword, canDiscoverJID, logEnabled, retireOnDeletion, preserveHistOnDel, subject, rolesToBroadcast, " +
        "useReservedNick, canChangeNick, canRegister, allowpm, fmucEnabled, fmucOutboundNode, fmucOutboundMode, " +
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
        "canInvite, roomPassword, canDiscoverJID, logEnabled, retireOnDeletion, preserveHistOnDel, subject, " +
        "rolesToBroadcast, useReservedNick, canChangeNick, canRegister, allowpm, fmucEnabled, fmucOutboundNode, " +
        "fmucOutboundMode, fmucInboundNodes " +
        "FROM ofMucRoom WHERE serviceID=? AND (emptyDate IS NULL or emptyDate > ?)";
    private static final String LOAD_ALL_ROOMS =
        "SELECT roomID, creationDate, modificationDate, name, naturalName, description, " +
        "lockedDate, emptyDate, canChangeSubject, maxUsers, publicRoom, moderated, membersOnly, " +
        "canInvite, roomPassword, canDiscoverJID, logEnabled, retireOnDeletion, preserveHistOnDel, subject, " +
        "rolesToBroadcast, useReservedNick, canChangeNick, canRegister, allowpm, fmucEnabled, fmucOutboundNode, " +
        "fmucOutboundMode, fmucInboundNodes " +
        "FROM ofMucRoom WHERE serviceID=?";
    private static final String COUNT_ALL_ROOMS =
        "SELECT count(*) FROM ofMucRoom WHERE serviceID=?";
    private static final String LOAD_ALL_ROOM_NAMES =
        "SELECT name FROM ofMucRoom WHERE serviceID=?";
    private static final String UPDATE_ROOM =
        "UPDATE ofMucRoom SET modificationDate=?, naturalName=?, description=?, " +
        "canChangeSubject=?, maxUsers=?, publicRoom=?, moderated=?, membersOnly=?, " +
        "canInvite=?, roomPassword=?, canDiscoverJID=?, logEnabled=?, retireOnDeletion=?, preserveHistOnDel=?, " +
        "subject=?, rolesToBroadcast=?, useReservedNick=?, canChangeNick=?, canRegister=?, allowpm=?, fmucEnabled=?, " +
        "fmucOutboundNode=?, fmucOutboundMode=?, fmucInboundNodes=? " +
        "WHERE roomID=?";
    private static final String ADD_ROOM = 
        "INSERT INTO ofMucRoom (serviceID, roomID, creationDate, modificationDate, name, naturalName, " +
        "description, lockedDate, emptyDate, canChangeSubject, maxUsers, publicRoom, moderated, " +
        "membersOnly, canInvite, roomPassword, canDiscoverJID, logEnabled, retireOnDeletion, preserveHistOnDel, subject, " +
        "rolesToBroadcast, useReservedNick, canChangeNick, canRegister, allowpm, fmucEnabled, fmucOutboundNode, " +
        "fmucOutboundMode, fmucInboundNodes) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    private static final String CHECK_RETIREES = "SELECT 1 FROM ofMucRoomRetiree WHERE serviceID=? AND name=?";
    private static final String ADD_RETIREE =
        "INSERT INTO ofMucRoomRetiree (serviceID, name, alternateJID, reason) VALUES (?,?,?,?)";
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
    private static final String DELETE_ROOM_HISTORY =
        "DELETE FROM ofMucConversationLog WHERE roomID=?";
    // Clear the chat history for a room but don't clear the messages that set the room's subject
    private static final String CLEAR_ROOM_CHAT_HISTORY =
        "DELETE FROM ofMucConversationLog WHERE roomID=? AND subject IS NULL";

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
            Log.error("A database error occurred while trying to load reserved nickname for {} in room {}", bareJID, room.getName(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return answer;
    }

    /**
     * Counts all rooms of a chat service.
     *
     * Note that this method will count only rooms that are persisted in the database, and can exclude in-memory rooms
     * that are not persisted.
     *
     * @param service the chat service for which to return a room count.
     * @return A room number count
     */
    public static int countRooms(MultiUserChatService service) {
        Log.debug("Counting rooms for service '{}' in the database.", service.getServiceName());
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            Long serviceID = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatServiceID(service.getServiceName());
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(COUNT_ALL_ROOMS);
            pstmt.setLong(1, serviceID);
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new IllegalArgumentException("Service " + service.getServiceName() + " was not found in the database.");
            }
            return rs.getInt(1);
        } catch (SQLException sqle) {
            Log.error("An exception occurred while trying to count all persisted rooms of service '{}'", service.getServiceName(), sqle);
            return -1;
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    /**
     * Loads the room configuration from the database if the room was persistent.
     * 
     * @param room the room to load from the database if persistent
     */
    public static void loadFromDB(MUCRoom room) {
        Log.debug("Attempting to load room '{}' from the database.", room.getName());
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
            try {
                room.setMembersOnly(rs.getInt("membersOnly") == 1, Affiliation.owner, null);
            } catch (ForbiddenException | NotAllowedException e) {
                Log.error("Unable to set members-only when loading room from database (this is likely a bug in Openfire). Room: {}", room.getJID(), e);
            }
            room.setCanOccupantsInvite(rs.getInt("canInvite") == 1);
            room.setPassword(rs.getString("roomPassword"));
            room.setCanAnyoneDiscoverJID(rs.getInt("canDiscoverJID") == 1);
            room.setLogEnabled(rs.getInt("logEnabled") == 1);
            room.setRetireOnDeletion(rs.getInt("retireOnDeletion") == 1);
            room.setPreserveHistOnRoomDeletionEnabled(rs.getInt("preserveHistOnDel") == 1);
            try {
                final String subjectRaw = rs.getString("subject");
                if (subjectRaw != null) {
                    final Message subjectStanza;
                    if (subjectRaw.trim().startsWith("<message ")) {
                        // Expected: the database contains a stanza
                        final Element subjectEl = SAXReaderUtil.readRootElement(subjectRaw);
                        subjectStanza = new Message(subjectEl);
                    } else {
                        // Fallback: as a result of the migration for OF-3131, the database _may_ contain plain text.
                        Log.debug("Plain text (instead of stanza) subject found in database for room '{}'", room.getJID());
                        subjectStanza = constructRoomSubjectMessage(room.getJID(), subjectRaw, null, null);
                    }
                    room.initializeSubject(subjectStanza);
                }
            } catch (Throwable t) {
                Log.warn("Unable to parse data as a subject-changing stanza for room '{}'", room.getJID(), t);
            }
            List<Role> rolesToBroadcast = new ArrayList<>();
            String roles = StringUtils.zeroPadString(Integer.toBinaryString(rs.getInt("rolesToBroadcast")), 3);
            if (roles.charAt(0) == '1') {
                rolesToBroadcast.add(Role.moderator);
            }
            if (roles.charAt(1) == '1') {
                rolesToBroadcast.add(Role.participant);
            }
            if (roles.charAt(2) == '1') {
                rolesToBroadcast.add(Role.visitor);
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
                final FMUCMode fmucOutboundJoinMode = switch (rs.getInt("fmucOutboundMode")) // null returns 0.
                {
                    case 1  -> FMUCMode.MasterSlave;
                    default -> MasterMaster;
                };
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

            // Recreate the history only for the rooms that have the conversation logging enabled
            loadHistory(room);

            pstmt = con.prepareStatement(LOAD_AFFILIATIONS);
            pstmt.setLong(1, room.getID());
            rs = pstmt.executeQuery();
            while (rs.next()) {
                // might be a group JID
                JID affiliationJID = GroupJID.fromString(rs.getString("jid"));
                Affiliation affiliation = Affiliation.valueOf(rs.getInt("affiliation"));
                try {
                    switch (affiliation) {
                        case owner:
                            room.addOwner(affiliationJID, room.getSelfRepresentation().getAffiliation());
                            break;
                        case admin:
                            room.addAdmin(affiliationJID, room.getSelfRepresentation().getAffiliation());
                            break;
                        case outcast:
                            room.addOutcast(affiliationJID, null, null, room.getSelfRepresentation().getAffiliation(), room.getSelfRepresentation().getRole());
                            break;
                        default:
                            Log.error("Unknown affiliation value {} for user {} in persistent room {}", affiliation, affiliationJID.toBareJID(), room.getID());
                    }
                }
                catch (Exception e) {
                    Log.error("Unable to load affiliation for room: {}", room.getName(), e);
                }
            }
            DbConnectionManager.fastcloseStmt(rs, pstmt);
            
            pstmt = con.prepareStatement(LOAD_MEMBERS);
            pstmt.setLong(1, room.getID());
            rs = pstmt.executeQuery();
            while (rs.next()) {
                try {
                    final JID jid = GroupJID.fromString(rs.getString("jid"));
                    room.addMember(jid, rs.getString("nickname"), room.getSelfRepresentation().getAffiliation());
                }
                catch (Exception e) {
                    Log.error("Unable to load member for room: {}", room.getName(), e);
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
            Log.error("A database error occurred while trying to load room: {}", room.getName(), sqle);
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
    public static void saveToDB(MUCRoom room) {
        Log.debug("Attempting to save room '{}' to the database.", room.getName());
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
                pstmt.setInt(13, (room.isRetireOnDeletion() ? 1 : 0));
                pstmt.setInt(14, (room.isPreserveHistOnRoomDeletionEnabled() ? 1 : 0));
                pstmt.setString(15, room.getSubject());
                pstmt.setInt(16, marshallRolesToBroadcast(room));
                pstmt.setInt(17, (room.isLoginRestrictedToNickname() ? 1 : 0));
                pstmt.setInt(18, (room.canChangeNickname() ? 1 : 0));
                pstmt.setInt(19, (room.isRegistrationEnabled() ? 1 : 0));
                switch (room.canSendPrivateMessage())
                {
                    default:
                    case "anyone":       pstmt.setInt(20, 0); break;
                    case "participants": pstmt.setInt(20, 1); break;
                    case "moderators":   pstmt.setInt(20, 2); break;
                    case "none":         pstmt.setInt(20, 3); break;
                }
                pstmt.setInt(21, (room.isFmucEnabled() ? 1 : 0 ));
                if ( room.getFmucOutboundNode() == null ) {
                    pstmt.setNull(22, Types.VARCHAR);
                } else {
                    pstmt.setString(22, room.getFmucOutboundNode().toString());
                }
                if ( room.getFmucOutboundMode() == null ) {
                    pstmt.setNull(23, Types.INTEGER);
                } else {
                    pstmt.setInt(23, room.getFmucOutboundMode().equals(MasterMaster) ? 0 : 1);
                }

                // Store a newline-separated collection, which is an 'allow only on list' configuration. Note that the list can be empty (effectively: disallow all), or null: this is an 'allow all' configuration.
                if (room.getFmucInboundNodes() == null) {
                    pstmt.setNull(24, Types.VARCHAR); // Null: allow all.
                } else {
                    final String content = room.getFmucInboundNodes().stream().map(JID::toString).collect(Collectors.joining("\n")); // result potentially is an empty String, but will not be null.
                    pstmt.setString(24, content);
                }
                pstmt.setLong(25, room.getID());
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
                pstmt.setInt(19, (room.isRetireOnDeletion() ? 1 : 0));
                pstmt.setInt(20, (room.isPreserveHistOnRoomDeletionEnabled() ? 1 : 0));
                pstmt.setString(21, room.getSubject());
                pstmt.setInt(22, marshallRolesToBroadcast(room));
                pstmt.setInt(23, (room.isLoginRestrictedToNickname() ? 1 : 0));
                pstmt.setInt(24, (room.canChangeNickname() ? 1 : 0));
                pstmt.setInt(25, (room.isRegistrationEnabled() ? 1 : 0));
                switch (room.canSendPrivateMessage())
                {
                    default:
                    case "anyone":       pstmt.setInt(26, 0); break;
                    case "participants": pstmt.setInt(26, 1); break;
                    case "moderators":   pstmt.setInt(26, 2); break;
                    case "none":         pstmt.setInt(26, 3); break;
                }
                pstmt.setInt(27, (room.isFmucEnabled() ? 1 : 0 ));
                if ( room.getFmucOutboundNode() == null ) {
                    pstmt.setNull(28, Types.VARCHAR);
                } else {
                    pstmt.setString(28, room.getFmucOutboundNode().toString());
                }
                if ( room.getFmucOutboundMode() == null ) {
                    pstmt.setNull(29, Types.INTEGER);
                } else {
                    pstmt.setInt(29, room.getFmucOutboundMode().equals(MasterMaster) ? 0 : 1);
                }

                // Store a newline-separated collection, which is an 'allow only on list' configuration. Note that the list can be empty (effectively: disallow all), or null: this is an 'allow all' configuration.
                if (room.getFmucInboundNodes() == null) {
                    pstmt.setNull(30, Types.VARCHAR); // Null: allow all.
                } else {
                    final String content = room.getFmucInboundNodes().stream().map(JID::toString).collect(Collectors.joining("\n")); // result potentially is an empty String, but will not be null.
                    pstmt.setString(30, content);
                }
                pstmt.executeUpdate();
            }
        }
        catch (SQLException sqle) {
            Log.error("A database error occurred while trying to save room: {}", room.getName(), sqle);
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
        deleteFromDB(room, null, null);
    }

    /**
     * Removes the room configuration and its affiliates from the database.
     * 
     * @param room the room to remove from the database.
     * @param alternateJID an optional alternate JID. Commonly used to provide a replacement room. (can be {@code null})
     * @param reason an optional reason why the room was destroyed (can be {@code null}).
     */
    public static void deleteFromDB(MUCRoom room, JID alternateJID, String reason) {
        Log.debug("Attempting to delete room '{}' from the database.", room.getName());

        boolean shouldDeleteFromDB = room.isPersistent() && room.wasSavedToDB();

        // If the room should be retired but isn't persistent/saved, we still need to create a retiree entry
        if (!shouldDeleteFromDB && !room.isRetireOnDeletion()) {
            return;
        }

        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();

            if (shouldDeleteFromDB) {
                // Delete existing data only if the room was actually in the DB
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
                DbConnectionManager.fastcloseStmt(pstmt);

                if(!room.isPreserveHistOnRoomDeletionEnabled()) {
                    pstmt = con.prepareStatement(DELETE_ROOM_HISTORY);
                    pstmt.setLong(1, room.getID());
                    pstmt.executeUpdate();
                    DbConnectionManager.fastcloseStmt(pstmt);
                }

                // Update the room (in memory) to indicate that it's no longer in the database.
                room.setSavedToDB(false);
            }

            if (room.isRetireOnDeletion()) {
                pstmt = con.prepareStatement(ADD_RETIREE);
                pstmt.setLong(1, XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatServiceID(room.getMUCService().getServiceName()));
                pstmt.setString(2, room.getName());

                if (alternateJID == null) {
                    pstmt.setNull(3, Types.VARCHAR);
                } else {
                    pstmt.setString(3, alternateJID.toString());
                }

                if (reason == null || reason.isBlank()) {
                    pstmt.setNull(4, Types.VARCHAR);
                } else {
                    pstmt.setString(4, reason.trim());
                }

                pstmt.executeUpdate();
                DbConnectionManager.fastcloseStmt(pstmt);
            }
        }
        catch (SQLException sqle) {
            Log.error("A database error occurred while trying to delete room: {}", room.getName(), sqle);
            abortTransaction = true;
        }
        finally {
            DbConnectionManager.closeStatement(pstmt);
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
        }
    }

    /**
     * Clears the chat history for a room
     *
     * @param room the room to cleaer chat history from
     */
    public static void clearRoomChatFromDB(MUCRoom room) {
        Log.debug("Attempting to clear the chat history of room '{}' from the database.", room.getName());

        if (!room.isPersistent() || !room.wasSavedToDB()) {
            return;
        }
        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();
            pstmt = con.prepareStatement(CLEAR_ROOM_CHAT_HISTORY);
            pstmt.setLong(1, room.getID());
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            Log.error("A database error occurred while trying to delete room: {}", room.getName(), sqle);
            abortTransaction = true;
        }
        finally {
            DbConnectionManager.closeStatement(pstmt);
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
        }
    }

    /**
     * Loads the name of all the rooms that are in the database.
     *
     * @param chatserver the chat server that will hold the loaded rooms.
     * @return a collection with all room names.
     */
    public static Collection<String> loadRoomNamesFromDB(MultiUserChatService chatserver) {
        Log.debug("Loading room names for chat service {}", chatserver.getServiceName());
        Long serviceID = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatServiceID(chatserver.getServiceName());

        final Set<String> names = new HashSet<>();
        try {
            Connection connection = null;
            PreparedStatement statement = null;
            ResultSet resultSet = null;
            try {
                connection = DbConnectionManager.getConnection();
                statement = connection.prepareStatement(LOAD_ALL_ROOM_NAMES);
                statement.setLong(1, serviceID);
                resultSet = statement.executeQuery();

                while (resultSet.next()) {
                    try {
                        names.add(resultSet.getString("name"));
                    } catch (SQLException e) {
                        Log.error("A database exception prevented one particular MUC room name to be loaded from the database.", e);
                    }
                }
            } finally {
                DbConnectionManager.closeConnection(resultSet, statement, connection);
            }
        }
        catch (SQLException sqle) {
            Log.error("A database error prevented MUC room names to be loaded from the database.", sqle);
            return Collections.emptyList();
        }

        Log.debug( "Loaded {} room names for chat service {}", names.size(), chatserver.getServiceName() );
        return names;
    }

    /**
     * Loads all the rooms that had occupants after a given date from the database.
     *
     * @param chatserver the chat server that will hold the loaded rooms.
     * @param cleanupDate rooms that hadn't been used after this date won't be loaded.
     * @return a collection with all the persistent rooms.
     */
    public static Collection<MUCRoom> loadRoomsFromDB(MultiUserChatService chatserver, Date cleanupDate) {
        final int workers = ROOM_LOADING_WORKERS.getValue() < 1 ? 1 : ROOM_LOADING_WORKERS.getValue();
        final Instant startTime = Instant.now();
        Log.info( "Loading rooms for chat service {} using {} worker(s)", chatserver.getServiceName(), workers );
        Long serviceID = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatServiceID(chatserver.getServiceName());

        final Map<Long, MUCRoom> rooms;
        try {
            rooms = loadRooms(serviceID, cleanupDate, chatserver);
        }
        catch (SQLException sqle) {
            Log.error("A database error prevented MUC rooms to be loaded from the database.", sqle);
            return Collections.emptyList();
        }

        // Parallel loading with configured number of workers
        final ThreadFactory threadFactory = new NamedThreadFactory(
            "MUC-RoomLoad-", Executors.defaultThreadFactory(), false, Thread.NORM_PRIORITY);
        final ExecutorService executor = Executors.newFixedThreadPool(workers, threadFactory);
        final AtomicInteger failedCount = new AtomicInteger(0);
        final AtomicReference<Exception> firstFailure = new AtomicReference<>();
        final boolean failFast = ROOM_LOADING_FAILFAST.getValue();

        for (MUCRoom room : rooms.values()) {
            executor.submit(() -> {
                // Skip loading if fail-fast and a failure has already occurred
                if (failFast && failedCount.get() > 0) {
                    return;
                }

                try {
                    loadFromDB(room);
                } catch (Exception e) {
                    Log.error("Failed to load room '{}' from database.", room.getName(), e);
                    failedCount.incrementAndGet();
                    firstFailure.compareAndSet(null, e);
                }
            });
        }

        executor.shutdown();
        try {
            final Duration timeout = ROOM_LOADING_TIMEOUT.getValue();
            final boolean terminationSuccessful = executor.awaitTermination(timeout.isZero() ? Long.MAX_VALUE : timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!terminationSuccessful) {
                Log.warn("Room loading timed out after {}.", timeout);
            }
        } catch (InterruptedException e) {
            Log.warn("Interrupted while waiting for MUC room loading to complete for service {}.",
                chatserver.getServiceName(), e);
            Thread.currentThread().interrupt();
        }

        if (failedCount.get() > 0) {
            if (failFast) {
                throw new RuntimeException("Failed to load a room for chat service " + chatserver.getServiceName(), firstFailure.get());
            }
            Log.warn("Failed to load {} room(s) for chat service {}. See previous error messages for details.",
                failedCount.get(), chatserver.getServiceName());
        }

        final Duration elapsedTime = Duration.between(startTime, Instant.now());
        Log.info( "Loaded {} rooms for chat service {} in {}", rooms.size(), chatserver.getServiceName(), elapsedTime );
        return rooms.values();
    }

    private static Map<Long, MUCRoom> loadRooms(Long serviceID, Date cleanupDate, MultiUserChatService chatserver) throws SQLException {
        final Map<Long, MUCRoom> rooms = new HashMap<>();

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
                    MUCRoom room = new MUCRoom(chatserver, resultSet.getString("name"));
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
                    try {
                        room.setMembersOnly(resultSet.getInt("membersOnly") == 1, Affiliation.owner, null);
                    } catch (ForbiddenException | NotAllowedException e) {
                        Log.error("Unable to set members-only when loading room from database (this is likely a bug in Openfire). Room: {}", room.getJID(), e);
                    }
                    room.setCanOccupantsInvite(resultSet.getInt("canInvite") == 1);
                    room.setPassword(resultSet.getString("roomPassword"));
                    room.setCanAnyoneDiscoverJID(resultSet.getInt("canDiscoverJID") == 1);
                    room.setLogEnabled(resultSet.getInt("logEnabled") == 1);
                    room.setRetireOnDeletion(resultSet.getInt("retireOnDeletion") == 1);
                    room.setPreserveHistOnRoomDeletionEnabled(resultSet.getInt("preserveHistOnDel") == 1);
                    try {
                        final String subjectRaw = resultSet.getString("subject");
                        if (subjectRaw != null) {
                            final Message subjectStanza;
                            if (subjectRaw.trim().startsWith("<message ")) {
                                // Expected: the database contains a stanza
                                final Element subjectEl = SAXReaderUtil.readRootElement(subjectRaw);
                                subjectStanza = new Message(subjectEl);
                            } else {
                                // Fallback: as a result of the migration for OF-3131, the database _may_ contain plain text.
                                Log.debug("Plain text (instead of stanza) subject found in database for room '{}'", room.getJID());
                                subjectStanza = constructRoomSubjectMessage(room.getJID(), subjectRaw, null, null);
                            }
                            room.initializeSubject(subjectStanza);
                        }
                    } catch (Throwable t) {
                        Log.warn("Unable to parse data as a subject-changing stanza for room '{}'", room.getJID(), t);
                    }

                    List<Role> rolesToBroadcast = new ArrayList<>();
                    String roles = StringUtils.zeroPadString(Integer.toBinaryString(resultSet.getInt("rolesToBroadcast")), 3);
                    if (roles.charAt(0) == '1') {
                        rolesToBroadcast.add(Role.moderator);
                    }
                    if (roles.charAt(1) == '1') {
                        rolesToBroadcast.add(Role.participant);
                    }
                    if (roles.charAt(2) == '1') {
                        rolesToBroadcast.add(Role.visitor);
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
                        final FMUCMode fmucOutboundJoinMode = switch (resultSet.getInt("fmucOutboundMode")) // null returns 0.
                        {
                            case 1  -> FMUCMode.MasterSlave;
                            default -> MasterMaster;
                        };
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

    /**
     * Load or reload the room history for a particular room from the database into memory.
     *
     * Invocation of this method will replace exising room history that's stored in memory (if any) with a freshly set
     * of messages obtained from the database.
     *
     * @param room The room for which to load message history from the database into memory.
     * @throws SQLException
     */
    public static void loadHistory(@Nonnull final MUCRoom room) throws SQLException
    {
        loadHistory(room, room.getRoomHistory().getMaxMessages());
    }

    /**
     * Load or reload the room history for a particular room from the database into memory.
     *
     * Invocation of this method will replace exising room history that's stored in memory (if any) with a freshly set
     * of messages obtained from the database.
     *
     * @param room The room for which to load message history from the database into memory.
     * @param maxNumber A hint for the maximum number of messages that need to be read from the database. -1 for all messages.
     * @throws SQLException
     */
    // TODO Consider merging this method with the one above, to avoid confusion. You'd hope that people use MAM instead of this anyway.
    public static void loadHistory(@Nonnull final MUCRoom room, final int maxNumber) throws SQLException
    {
        Log.debug("Loading room history for room '{}' (max: {})", room.getJID(), maxNumber == -1 ? "all" : maxNumber);

        final List<Message> oldMessages = new LinkedList<>();
        if (room.isLogEnabled() && maxNumber != 0)
        {
            Connection con = null;
            PreparedStatement pstmt = null;
            ResultSet rs = null;
            try {
                // Reload historic messages from the database.
                con = DbConnectionManager.getConnection();
                pstmt = con.prepareStatement(LOAD_HISTORY, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);

                // Reload the history, using "muc.history.reload.limit" (days) if present
                long from = 0;
                String reloadLimit = JiveGlobals.getProperty(MUC_HISTORY_RELOAD_LIMIT);
                if (reloadLimit != null) {
                    // if the property is defined, but not numeric, default to 2 (days)
                    int reloadLimitDays = JiveGlobals.getIntProperty(MUC_HISTORY_RELOAD_LIMIT, 2);
                    Log.warn("MUC history reload limit set to " + reloadLimitDays + " days");
                    from = System.currentTimeMillis() - (BigInteger.valueOf(86400000).multiply(BigInteger.valueOf(reloadLimitDays))).longValue();
                }

                pstmt.setString(1, StringUtils.dateToMillis(new Date(from)));
                pstmt.setLong(2, room.getID());
                rs = pstmt.executeQuery();

                // When reloading history, make sure that the old data is removed from memory before re-adding it.
                room.getRoomHistory().purge();

                try {
                    if (maxNumber > -1 && rs.last()) {
                        // Try to skip to the last few rows from the result set.
                        rs.relative(maxNumber * -1);
                    }
                } catch (SQLException e) {
                    Log.debug("Unable to skip to the last {} rows of the result set.", maxNumber, e);
                }

                while (rs.next()) {
                    String senderJID = rs.getString("sender");
                    String nickname = rs.getString("nickname");
                    Date sentDate = new Date(Long.parseLong(rs.getString("logTime").trim()));
                    String subject = rs.getString("subject");
                    String body = rs.getString("body");
                    String stanza = rs.getString("stanza");
                    oldMessages.add(room.getRoomHistory().parseHistoricMessage(senderJID, nickname, sentDate, subject, body, stanza));
                }
            } finally {
                DbConnectionManager.closeConnection(rs, pstmt, con);
            }
        }

        room.getRoomHistory().purge();
        if (!oldMessages.isEmpty()) {
            room.getRoomHistory().addOldMessages(oldMessages);
        }

        // If the room does not include the last subject in the history, then recreate one if possible.
        if (!room.getRoomHistory().hasChangedSubject() && room.getSubject() != null && !room.getSubject().isEmpty()) {
            final Message subject = room.getRoomHistory().parseHistoricMessage(room.getSelfRepresentation().getOccupantJID().toString(),
                null, room.getModificationDate(), room.getSubject(), null, null);
            room.getRoomHistory().addOldMessages(subject);
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
            pstmt.setString(1, room.getSubjectStanza() == null ? null : room.getSubjectStanza().toXML());
            pstmt.setLong(2, room.getID());
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            Log.error("A database error occurred while trying to update subject for room: {}", room.getName(), sqle);
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
    public static void updateRoomLock(MUCRoom room) {
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
            Log.error("A database error occurred while trying to update lock status for room: {}", room.getName(), sqle);
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
            Log.error("A database error occurred while trying to update empty date for room: {}", room.getName(), sqle);
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
                                           Affiliation newAffiliation, Affiliation oldAffiliation)
    {
        final String affiliationJid = jid.toBareJID();
        if (!room.isPersistent() || !room.wasSavedToDB()) {
            return;
        }
        if (Affiliation.none == oldAffiliation) {
            if (Affiliation.member == newAffiliation) {
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
                    Log.error("A database error occurred while trying to save member {} in room: {}", jid, room.getName(), sqle);
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
                    Log.error("A database error occurred while trying to save affiliation for {} in room: {}", jid, room.getName(), sqle);
                }
                finally {
                    DbConnectionManager.closeConnection(pstmt, con);
                }
            }
        }
        else {
            if (Affiliation.member == newAffiliation &&
                    Affiliation.member == oldAffiliation)
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
                    Log.error("A database error occurred while trying to update member {} in room: {}", jid, room.getName(), sqle);
                }
                finally {
                    DbConnectionManager.closeConnection(pstmt, con);
                }
            }
            else if (Affiliation.member == newAffiliation) {
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
                    Log.error("A database error occurred while trying to change affiliation to member for {} in room: {}", jid, room.getName(), sqle);
                    abortTransaction = true;
                }
                finally {
                    DbConnectionManager.closeStatement(pstmt);
                    DbConnectionManager.closeTransactionConnection(con, abortTransaction);
                }
            }
            else if (Affiliation.member == oldAffiliation) {
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
                    Log.error("A database error occurred while trying to change member to affiliation for {} in room: {}", jid, room.getName(), sqle);
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
                    if (pstmt.executeUpdate() == 0) {
                        Log.warn("While trying to persist the update the affiliation of {} in room: {} from {} to {}, no database rows were modified. The change was possibly not persisted (or was unnecessary). This may be a bug in Openfire logic.", jid, room.getJID(), oldAffiliation, newAffiliation);
                    }
                }
                catch (SQLException sqle) {
                    Log.error("A database error occurred while trying to update affiliation for {} in room: {}", jid, room.getName(), sqle);
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
                                               Affiliation oldAffiliation)
    {
        final String affiliationJID = jid.toBareJID();
        if (room.isPersistent() && room.wasSavedToDB()) {
            if (Affiliation.member == oldAffiliation) {
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
                    Log.error("A database error occurred while trying to remove member for {} in room: {}", jid, room.getName(), sqle);
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
                    Log.error("A database error occurred while trying to remove affiliation for {} in room: {}", jid, room.getName(), sqle);
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
            Log.error("A database error occurred while trying to remove affiliation for {} in all rooms", affiliationJID, sqle);
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
                pstmt.setLong(2, entry.getMessageID());
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
        final String buffer =
            (room.canBroadcastPresence(Role.moderator) ? "1" : "0") +
            (room.canBroadcastPresence(Role.participant) ? "1" : "0") +
            (room.canBroadcastPresence(Role.visitor) ? "1" : "0");
        return Integer.parseInt(buffer, 2);
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
        return Boolean.parseBoolean(getProperty(subdomain, name));
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
            return Boolean.parseBoolean(value);
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

    /**
     * Check if a room name is retired for a given service.
     *
     * @param roomName the name of the room to check.
     * @param multiUserChatService the service to check the room name against.
     * @return true if the room name is retired for the supplied service, false otherwise.
     */
    public static boolean isRoomRetired(String roomName, MultiUserChatService multiUserChatService) {
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            connection = DbConnectionManager.getConnection();
            statement = connection.prepareStatement(CHECK_RETIREES);
            statement.setLong(1, XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatServiceID(multiUserChatService.getServiceName()));
            statement.setString(2, roomName);
            resultSet = statement.executeQuery();
            return resultSet.next();
        }
        catch (SQLException sqle) {
            Log.error("A database error prevented checking MUC room retired state.", sqle);
            return true; // Assume retired if we can't check.
        }
        finally {
            DbConnectionManager.closeConnection(resultSet, statement, connection);
        }
    }

    /**
     * Constructs a message stanza that represents a room subject change.
     *
     * @param roomJid The room address
     * @param subject The subject test
     * @param date The moment in time that the subject was set/changed.
     * @param authorNickname The nickname (JID resourcepart) of the entity that set/changed the subject.
     * @return A message stanza representing the subject change
     */
    @VisibleForTesting
    static Message constructRoomSubjectMessage(@Nonnull final JID roomJid, @Nullable final String subject, @Nullable final Instant date, @Nullable final String authorNickname)
    {
        final Message roomSubject = new Message();
        roomSubject.setType(Message.Type.groupchat);
        roomSubject.setID(UUID.randomUUID().toString());

        // If the author of the subject is known, use their nickname as the originator of the message.
        if (authorNickname != null && !authorNickname.isEmpty()) {
            roomSubject.setFrom(new JID(roomJid.getNode(), roomJid.getDomain(), authorNickname));
        } else {
            roomSubject.setFrom(roomJid);
        }

        // Add the subject to the 'subject' element of the message. Ensure that this element is always present (even
        // when empty), as MUC joins require 'subject' element to be present.
        if (subject != null && !subject.isEmpty()) {
            roomSubject.setSubject(subject);
        } else {
            roomSubject.getElement().addElement("subject");
        }

        // Include the time when this subject was set.
        if (date != null) {
            final Element delayElement = roomSubject.addChildElement("delay", "urn:xmpp:delay");
            delayElement.addAttribute("stamp", XMPPDateTimeFormat.format(date));
            delayElement.addAttribute("from", roomJid.toBareJID()); // XEP-0045: "If the <delay/> element is included, its 'from' attribute MUST be set to the JID of the room itself."
        }

        return roomSubject;
    }
}
