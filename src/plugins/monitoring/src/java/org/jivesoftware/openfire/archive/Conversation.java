/**
 * $Revision: 3034 $
 * $Date: 2005-11-04 21:02:33 -0300 (Fri, 04 Nov 2005) $
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.archive;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.database.JiveID;
import org.jivesoftware.database.SequenceManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.muc.MUCRole;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.plugin.MonitoringPlugin;
import org.jivesoftware.openfire.user.UserNameManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

/**
 * Represents an IM conversation between two people. A conversation encompasses a
 * series of messages sent back and forth. It may cover a single topic or several.
 * The start of a conversation occurs when the first message between two users is
 * sent. It ends when either:
 * <ul>
 * <li>No messages are sent between the users for a certain period of time (default of 10
 * minutes). The default value can be overridden by setting the Openfire property
 * <tt>conversation.idleTime</tt>.</li>
 * <li>The total conversation time reaches a maximum value (default of 60 minutes).
 * The default value can be overridden by setting the Openfire property
 * <tt>conversation.maxTime</tt>. When the max time has been reached and additional
 * messages are sent between the users, a new conversation will simply be
 * started.</li>
 * </ul>
 * <p/>
 * Each conversation has a start time, date of the last message, and count of the
 * messages in the conversation. Conversations are specially marked if one of the
 * participants is on an external server. If archiving is enabled, the actual messages in
 * the conversation can be retrieved.
 *
 * @author Matt Tucker
 */
@JiveID(50)
public class Conversation implements Externalizable {

	private static final Logger Log = LoggerFactory.getLogger(Conversation.class);
	
    private static final String INSERT_CONVERSATION =
            "INSERT INTO ofConversation(conversationID, room, isExternal, startDate, " +
                    "lastActivity, messageCount) VALUES (?,?,?,?,?,0)";
    private static final String INSERT_PARTICIPANT =
            "INSERT INTO ofConParticipant(conversationID, joinedDate, bareJID, jidResource, nickname) " +
                    "VALUES (?,?,?,?,?)";
    private static final String LOAD_CONVERSATION =
            "SELECT room, isExternal, startDate, lastActivity, messageCount " +
                    "FROM ofConversation WHERE conversationID=?";
    private static final String LOAD_PARTICIPANTS =
            "SELECT bareJID, jidResource, nickname, joinedDate, leftDate FROM ofConParticipant " +
                    "WHERE conversationID=? ORDER BY joinedDate";
    private static final String LOAD_MESSAGES =
            "SELECT fromJID, toJID, sentDate, body FROM ofMessageArchive WHERE conversationID=? " +
                    "ORDER BY sentDate";

    private transient ConversationManager conversationManager;

    private long conversationID = -1;
    private Map<String, UserParticipations> participants;
    private boolean external;
    private Date startDate;
    private Date lastActivity;
    private int messageCount;
    /**
     * Room where the group conversion is taking place. For one-to-one chats
     * there is no room so this variable will be null.
     */
    private JID room;

    /**
     * Do not use this constructor. It only exists for serialization purposes.
     */
    public Conversation() {
    }

    /**
     * Constructs a new one-to-one conversation.
     *
     * @param conversationManager the ConversationManager.
     * @param users               the two participants in the conversation.
     * @param external            true if the conversation includes a user on another server.
     * @param startDate           the starting date of the conversation.
     */
    public Conversation(ConversationManager conversationManager, Collection<JID> users,
                        boolean external, Date startDate) {
        if (users.size() != 2) {
            throw new IllegalArgumentException("Illegal number of participants: " + users.size());
        }
        this.conversationManager = conversationManager;
        this.participants = new HashMap<String, UserParticipations>(2);
        // Ensure that we're use the full JID of each participant.
        for (JID user : users) {
            UserParticipations userParticipations = new UserParticipations(false);
            userParticipations.addParticipation(new ConversationParticipation(startDate));
            participants.put(user.toString(), userParticipations);
        }
        this.external = external;
        this.startDate = startDate;
        this.lastActivity = startDate;
        // If archiving is enabled, insert the conversation into the database.
        if (conversationManager.isMetadataArchivingEnabled()) {
            try {
                insertIntoDb();
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
        }
    }

    /**
     * Constructs a new group chat conversation that is taking place in a room.
     *
     * @param conversationManager the ConversationManager.
     * @param room                the JID of the room where the conversation is taking place.
     * @param external            true if the conversation includes a user on another server.
     * @param startDate           the starting date of the conversation.
     */
    public Conversation(ConversationManager conversationManager, JID room, boolean external, Date startDate) {
        this.conversationManager = conversationManager;
        this.participants = new ConcurrentHashMap<String, UserParticipations>();
        // Add list of existing room occupants as participants of this conversation
        MUCRoom mucRoom = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(room).getChatRoom(room.getNode());
        if (mucRoom != null) {
            for (MUCRole role : mucRoom.getOccupants()) {
                UserParticipations userParticipations = new UserParticipations(true);
                userParticipations.addParticipation(new ConversationParticipation(startDate, role.getNickname()));
                participants.put(role.getUserAddress().toString(), userParticipations);
            }
        }
        this.room = room;
        this.external = external;
        this.startDate = startDate;
        this.lastActivity = startDate;
        // If archiving is enabled, insert the conversation into the database.
        if (conversationManager.isMetadataArchivingEnabled()) {
            try {
                insertIntoDb();
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
        }
    }

    /**
     * Loads a conversation from the database.
     *
     * @param conversationManager the conversation manager.
     * @param conversationID      the ID of the conversation.
     * @throws NotFoundException if the conversation can't be loaded.
     */
    public Conversation(ConversationManager conversationManager, long conversationID)
            throws NotFoundException {
        this.conversationManager = conversationManager;
        this.conversationID = conversationID;
        loadFromDb();
    }

    /**
     * Returns the unique ID of the conversation. A unique ID is only meaningful when
     * conversation archiving is enabled. Therefore, this method returns <tt>-1</tt> if
     * archiving is not turned on.
     *
     * @return the unique ID of the conversation, or <tt>-1</tt> if conversation
     *         archiving is not enabled.
     */
    public long getConversationID() {
        return conversationID;
    }


    /**
     * Returns the JID of the room where the group conversation took place. If the conversation
     * was a one-to-one chat then a <tt>null</tt> value is returned.
     *
     * @return the JID of room or null if this was a one-to-one chat.
     */
    public JID getRoom() {
        return room;
    }

    /**
     * Returns the conversation participants.
     *
     * @return the two conversation participants. Returned JIDs are full JIDs.
     */
    public Collection<JID> getParticipants() {
        List<JID> users = new ArrayList<JID>();
        for (String key : participants.keySet()) {
            users.add(new JID(key));
        }
        return users;
    }

    /**
     * Returns the participations of the specified user (full JID) in this conversation. Each
     * participation will hold the time when the user joined and left the conversation and the
     * nickname if the room happened in a room.
     *
     * @param user the full JID of the user.
     * @return the participations of the specified user (full JID) in this conversation.
     */
    public Collection<ConversationParticipation> getParticipations(JID user) {
        UserParticipations userParticipations = participants.get(user.toString());
        if (userParticipations == null) {
            return Collections.emptyList();
        }
        return userParticipations.getParticipations();
    }

    /**
     * Returns true if one of the conversation participants is on an external server.
     *
     * @return true if one of the conversation participants is on an external server.
     */
    public boolean isExternal() {
        return external;
    }

    /**
     * Returns the starting timestamp of the conversation.
     *
     * @return the start date.
     */
    public Date getStartDate() {
        return startDate;
    }

    /**
     * Returns the timestamp the last message was receieved.
     *
     * @return the last activity.
     */
    public Date getLastActivity() {
        return lastActivity;
    }

    /**
     * Returns the number of messages that make up the conversation.
     *
     * @return the message count.
     */
    public int getMessageCount() {
        return messageCount;
    }

    /**
     * Returns the archived messages in the conversation. If message archiving is not
     * enabled, this method will always return an empty collection. This method will only
     * return messages that have already been batch-archived to the database; in other
     * words, it does not provide a real-time view of new messages.
     *
     * @return the archived messages in the conversation.
     */
    public List<ArchivedMessage> getMessages() {
        if (room == null && !conversationManager.isMessageArchivingEnabled()) {
            return Collections.emptyList();
        }
        else if (room != null && !conversationManager.isRoomArchivingEnabled()) {
            return Collections.emptyList();
        }

        List<ArchivedMessage> messages = new ArrayList<ArchivedMessage>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_MESSAGES);
            pstmt.setLong(1, getConversationID());
            rs = pstmt.executeQuery();
            while (rs.next()) {
                JID fromJID = new JID(rs.getString(1));
                JID toJID = new JID(rs.getString(2));
                Date date = new Date(rs.getLong(3));
                String body = DbConnectionManager.getLargeTextField(rs, 4);
                messages.add(new ArchivedMessage(conversationID, fromJID, toJID, date, body, false));
            }
        }
        catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        // Add messages of users joining or leaving the group chat conversation
        if (room != null) {
            for (Map.Entry<String, UserParticipations> entry : participants.entrySet()) {
                JID user = new JID(entry.getKey());
                boolean anonymous = false;
                String name;
                try {
                    name = UserNameManager.getUserName(user);
                }
                catch (UserNotFoundException e) {
                    name = user.toBareJID();
                    anonymous = true;
                }
                for (ConversationParticipation participation : entry.getValue().getParticipations()) {
                    if (participation.getJoined() == null) {
                        Log.warn("Found muc participant with no join date in conversation: " + conversationID);
                        continue;
                    }
                    JID jid = new JID(room + "/" + participation.getNickname());
                    String joinBody;
                    String leftBody;
                    if (anonymous) {
                        joinBody = LocaleUtils.getLocalizedString("muc.conversation.joined.anonymous", "monitoring",
                                Arrays.asList(participation.getNickname()));
                        leftBody = LocaleUtils.getLocalizedString("muc.conversation.left.anonymous", "monitoring",
                                Arrays.asList(participation.getNickname()));
                    }
                    else {
                        joinBody = LocaleUtils.getLocalizedString("muc.conversation.joined", "monitoring",
                                Arrays.asList(participation.getNickname(), name));
                        leftBody = LocaleUtils.getLocalizedString("muc.conversation.left", "monitoring",
                                Arrays.asList(participation.getNickname(), name));
                    }
                    messages.add(
                            new ArchivedMessage(conversationID, user, jid, participation.getJoined(), joinBody, true));
                    if (participation.getLeft() != null) {
                        messages.add(new ArchivedMessage(conversationID, user, jid, participation.getLeft(), leftBody,
                                true));
                    }
                }
            }
            // Sort messages by sent date
            Collections.sort(messages, new Comparator<ArchivedMessage>() {
                public int compare(ArchivedMessage o1, ArchivedMessage o2) {
                    return o1.getSentDate().compareTo(o2.getSentDate());
                }
            });
        }
        return messages;
    }

    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("Conversation [").append(conversationID).append("]");
        if (room != null) {
            buf.append(" in room").append(room);
        }
        buf.append(" between ").append(participants);
        buf.append(". started ").append(JiveGlobals.formatDateTime(startDate));
        buf.append(", last active ").append(JiveGlobals.formatDateTime(lastActivity));
        buf.append(". Total messages: ").append(messageCount);
        return buf.toString();
    }

    /**
     * Called when a new message for the conversation is received. Each time a new
     * message is received, the last activity date will be updated and the message
     * count incremented.
     *
     * @param entity JID of the entity that sent the message.
     * @param date   the date the message was sent.
     */
    synchronized void messageReceived(JID entity, Date date) {
        lastActivity = date;
        messageCount++;
    }

    synchronized void participantJoined(JID user, String nickname, long timestamp) {
        // Add the sender of the message as a participant of this conversation. If the sender
        // was already a participant then he/she will appear just once. Rooms are never considered
        // as participants
        UserParticipations userParticipations = participants.get(user.toString());
        if (userParticipations == null) {
            userParticipations = new UserParticipations(true);
            participants.put(user.toString(), userParticipations);
        }
        else {
            // Get last known participation and check that the user has finished it
            ConversationParticipation lastParticipation = userParticipations.getRecentParticipation();
            if (lastParticipation != null && lastParticipation.getLeft() == null) {
                Log.warn("Found user that never left a previous conversation: " + user);
                lastParticipation.participationEnded(new Date(timestamp));
                // Queue storeage of updated participation information
                conversationManager.queueParticipantLeft(this, user, lastParticipation);
            }
        }
        ConversationParticipation newParticipation = new ConversationParticipation(new Date(timestamp), nickname);
        // Add element to the beginning of the list
        userParticipations.addParticipation(newParticipation);
        // If archiving is enabled, insert the conversation into the database (if not persistent yet).
        if (conversationManager.isMetadataArchivingEnabled()) {
            try {
                if (conversationID == -1) {
                    // Save new conversation to the database
                    insertIntoDb();
                }
                else {
                    // Store new participation information
                    insertIntoDb(user, nickname, timestamp);
                }
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
        }
    }

    synchronized void participantLeft(JID user, long timestamp) {
        // Get the list of participations of the specified user
        UserParticipations userParticipations = participants.get(user.toString());
        if (userParticipations == null) {
            Log.warn("Found user that left a conversation but never started it: " + user);
        }
        else {
            // Get last known participation and check that the user has not finished it
            ConversationParticipation currentParticipation = userParticipations.getRecentParticipation();
            if (currentParticipation == null || currentParticipation.getLeft() != null) {
                Log.warn("Found user that left a conversation but never started it: " + user);
            }
            else {
                currentParticipation.participationEnded(new Date(timestamp));
                // Queue storeage of updated participation information
                conversationManager.queueParticipantLeft(this, user, currentParticipation);
            }
        }
    }

    /**
     * Inserts a new conversation into the database.
     *
     * @throws SQLException if an error occurs inserting the conversation.
     */
    private void insertIntoDb() throws SQLException {
        this.conversationID = SequenceManager.nextID(this);
        Connection con = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();
            PreparedStatement pstmt = con.prepareStatement(INSERT_CONVERSATION);
            pstmt.setLong(1, conversationID);
            pstmt.setString(2, room == null ? null : room.toString());
            pstmt.setInt(3, (external ? 1 : 0));
            pstmt.setLong(4, startDate.getTime());
            pstmt.setLong(5, lastActivity.getTime());
            pstmt.executeUpdate();
            pstmt.close();

            pstmt = con.prepareStatement(INSERT_PARTICIPANT);
            for (Map.Entry<String, UserParticipations> entry : participants.entrySet()) {
                JID user = new JID(entry.getKey());
                for (ConversationParticipation participation : entry.getValue().getParticipations()) {
                    pstmt.setLong(1, conversationID);
                    pstmt.setLong(2, participation.getJoined().getTime());
                    pstmt.setString(3, user.toBareJID());
                    pstmt.setString(4, user.getResource() == null ? " " : user.getResource());
                    pstmt.setString(5, participation.getNickname());
                    pstmt.executeUpdate();
                }
            }
            pstmt.close();
        }
        catch (SQLException sqle) {
            abortTransaction = true;
            throw sqle;
        }
        finally {
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
        }
    }

    /**
     * Adds a new conversation participant into the database.
     *
     * @param participant the full JID of the participant.
     * @param nickname    nickname of the user in the room.
     * @param joined      timestamp when user joined the conversation.
     * @throws SQLException if an error occurs inserting the conversation.
     */
    private void insertIntoDb(JID participant, String nickname, long joined) throws SQLException {
        Connection con = null;
        try {
            con = DbConnectionManager.getConnection();
            PreparedStatement pstmt = con.prepareStatement(INSERT_PARTICIPANT);
            pstmt.setLong(1, conversationID);
            pstmt.setLong(2, joined);
            pstmt.setString(3, participant.toBareJID());
            pstmt.setString(4, participant.getResource());
            pstmt.setString(5, nickname);
            pstmt.executeUpdate();
            pstmt.close();
        }
        catch (SQLException sqle) {
            throw sqle;
        }
        finally {
            DbConnectionManager.closeConnection(con);
        }
    }

    private void loadFromDb() throws NotFoundException {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_CONVERSATION);
            pstmt.setLong(1, conversationID);
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new NotFoundException("Conversation not found: " + conversationID);
            }
            this.room = rs.getString(1) == null ? null : new JID(rs.getString(1));
            this.external = rs.getInt(2) == 1;
            this.startDate = new Date(rs.getLong(3));
            this.lastActivity = new Date(rs.getLong(4));
            this.messageCount = rs.getInt(5);
            rs.close();
            pstmt.close();

            this.participants = new ConcurrentHashMap<String, UserParticipations>();
            pstmt = con.prepareStatement(LOAD_PARTICIPANTS);
            pstmt.setLong(1, conversationID);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                // Rebuild full JID of participant
                String baredJID = rs.getString(1);
                String resource = rs.getString(2);
                JID fullJID = new JID("".equals(resource) ? baredJID : baredJID + "/" + resource);
                // Rebuild joined and left time
                ConversationParticipation participation =
                        new ConversationParticipation(new Date(rs.getLong(4)), rs.getString(3));
                if (rs.getLong(5) > 0) {
                    participation.participationEnded(new Date(rs.getLong(5)));
                }
                // Store participation data
                UserParticipations userParticipations = participants.get(fullJID.toString());
                if (userParticipations == null) {
                    userParticipations = new UserParticipations(room != null);
                    participants.put(fullJID.toString(), userParticipations);
                }
                userParticipations.addParticipation(participation);
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
     * Notification message inficating that conversation has finished so remaining participants
     * should be marked that they left the conversation.
     *
     * @param nowDate the date when the conversation was finished
     */
    void conversationEnded(Date nowDate) {
        for (Map.Entry<String, UserParticipations> entry : participants.entrySet()) {
            ConversationParticipation currentParticipation = entry.getValue().getRecentParticipation();
            if (currentParticipation.getLeft() == null) {
                currentParticipation.participationEnded(nowDate);
                // Queue storage of updated participation information
                conversationManager.queueParticipantLeft(this, new JID(entry.getKey()), currentParticipation);
            }
        }
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeLong(out, conversationID);
        ExternalizableUtil.getInstance().writeExternalizableMap(out, participants);
        ExternalizableUtil.getInstance().writeBoolean(out, external);
        ExternalizableUtil.getInstance().writeLong(out, startDate.getTime());
        ExternalizableUtil.getInstance().writeLong(out, lastActivity.getTime());
        ExternalizableUtil.getInstance().writeInt(out, messageCount);
        ExternalizableUtil.getInstance().writeBoolean(out, room != null);
        if (room != null) {
            ExternalizableUtil.getInstance().writeSerializable(out, room);
        }
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        MonitoringPlugin plugin = (MonitoringPlugin) XMPPServer.getInstance().getPluginManager().getPlugin(
                "monitoring");
        conversationManager = (ConversationManager) plugin.getModule(ConversationManager.class);


        this.participants = new ConcurrentHashMap<String, UserParticipations>();

        conversationID = ExternalizableUtil.getInstance().readLong(in);
        ExternalizableUtil.getInstance().readExternalizableMap(in, participants, getClass().getClassLoader());
        external = ExternalizableUtil.getInstance().readBoolean(in);
        startDate = new Date(ExternalizableUtil.getInstance().readLong(in));
        lastActivity = new Date(ExternalizableUtil.getInstance().readLong(in));
        messageCount = ExternalizableUtil.getInstance().readInt(in);
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            room = (JID) ExternalizableUtil.getInstance().readSerializable(in);
        }
    }
}