/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.muc;

import org.jivesoftware.database.JiveID;
import org.jivesoftware.openfire.archive.ArchiveManager;
import org.jivesoftware.openfire.archive.Archiver;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.openfire.muc.spi.LocalMUCRoom;
import org.jivesoftware.openfire.muc.spi.MUCPersistenceManager;
import org.jivesoftware.util.JiveConstants;
import org.xmpp.component.Component;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

/**
 * Manages groupchat conversations, chatrooms, and users. This class is designed to operate
 * independently from the rest of the Jive server infrastruture. This theoretically allows
 * deployment of the groupchat on a separate server from the main IM server.
 * 
 * @author Gaston Dombiak
 */
@JiveID(JiveConstants.MUC_SERVICE)
public interface MultiUserChatService extends Component {

    /**
     * Returns the fully-qualifed domain name of this chat service.
     * The domain is composed by the service name and the
     * name of the XMPP server where the service is running.
     * 
     * @return the chat server domain (service name + host name).
     */
    String getServiceDomain();

    /**
     * Returns the subdomain of the chat service.
     *
     * @return the subdomain of the chat service.
     */
    String getServiceName();

    /**
     * Returns the collection of JIDs that are system administrators of the MUC service. A sysadmin has
     * the same permissions as a room owner. 
     * 
     * @return a list of user/group JIDs.
     */
    Collection<JID> getSysadmins();
    
    /**
     * Validates the given JID as a MUC service administrator. 
     *
     * @param bareJID the bare JID of the user
     * @return true if the given JID is a MUC service administrator
     */
    boolean isSysadmin(JID bareJID);

    /**
     * Adds a new system administrator of the MUC service. A sysadmin has the same permissions as 
     * a room owner. 
     * 
     * @param userJID the bare JID of the new user/group to add as a system administrator.
     */
    void addSysadmin(JID userJID);

    /**
     * Adds multiple system administrators for the MUC service. A sysadmin has the same permissions as 
     * a room owner. 
     * 
     * @param userJIDs the JIDs of the new users/groups to add as a system administrator.
     */
    void addSysadmins(Collection<JID> userJIDs);

    /**
     * Removes a system administrator of the MUC service.
     * 
     * @param userJID the bare JID of the user/group to remove from the list.
     */
    void removeSysadmin(JID userJID);

    /**
     * Returns true when a system administrator of the MUC service can join a
     * password-protected room, without supplying the password.
     *
     * @return false if a sysadmin can join a password-protected room without a password, otherwise true.
     */
    default boolean isPasswordRequiredForSysadminsToJoinRoom() {
        return MUCPersistenceManager.getBooleanProperty( getServiceName(), "sysadmin.requires.room.passwords", false );
    }

    /**
     * Sets if a system administrator of the MUC service can join a
     * password-protected room, without supplying the password.
     *
     * @param isRequired false if a sysadmin is allowed to join a password-protected room without a password, otherwise true.
     */
    default void setPasswordRequiredForSysadminsToJoinRoom(boolean isRequired) {
        MUCPersistenceManager.setProperty( getServiceName(), "sysadmin.requires.room.passwords", Boolean.toString(isRequired) );
    }

    /**
     * Returns false if anyone can create rooms or true if only the returned JIDs in
     * <code>getUsersAllowedToCreate</code> are allowed to create rooms.
     *
     * @return true if only some JIDs are allowed to create rooms.
     */
    boolean isRoomCreationRestricted();

    /**
     * Sets if anyone can create rooms. When set to true, users are allowed to
     * create rooms only when <code>isAllRegisteredUsersAllowedToCreate</code>
     * or <code>getUsersAllowedToCreate</code> (or both) allow them to.
     *
     * @param roomCreationRestricted whether anyone can create rooms or not.
     */
    void setRoomCreationRestricted(boolean roomCreationRestricted);

    /**
     * Sets if all registered users of Openfire are allowed to create rooms.
     *
     * When true, anonymous users and users from other domains (through
     * federation) are initially prohibited from creating rooms, but can still
     * be allowed by registering their JIDs in <code>addUserAllowedToCreate</code>.
     *
     * @return true if all registered users are allowed to create rooms.
     */
    boolean isAllRegisteredUsersAllowedToCreate();

    /**
     * Sets if all registered users of Openfire are allowed to create rooms.
     *
     * @param allow whether all registered users can create rooms.
     */
    void setAllRegisteredUsersAllowedToCreate(boolean allow);

    /**
     * Returns the collection of JIDs that are allowed to create MUC rooms.
     * When <code>isAllRegisteredUsersAllowedToCreate</code>, this method will
     * not return a JID of every user in the system.
     *
     * @return a list of user/group JIDs.
     */
    Collection<JID> getUsersAllowedToCreate();

    /**
     * Adds a new user/group to the list of JIDs that are allowed to create MUC rooms.
     * 
     * @param userJID the JID of the new user/group to add to list.
     */
    void addUserAllowedToCreate(JID userJID);
    
    /**
     * Adds new users/groups to the list of JIDs that are allowed to create MUC rooms.
     * @param userJIDs collection of JIDs for users/groups to add to list.
     */
    void addUsersAllowedToCreate(Collection<JID> userJIDs);

    /**
     * Removes a user/group from list of JIDs that are allowed to create MUC rooms.
     * 
     * @param userJID the JID of the user/group to remove from the list.
     */
    void removeUserAllowedToCreate(JID userJID);

    /**
     * Removes users/groups from list of JIDs that are allowed to create MUC rooms.
     * 
     * @param userJIDs collection of JIDs of users/groups to remove from the list.
     */
    void removeUsersAllowedToCreate(Collection<JID> userJIDs);

    /**
     * Sets the time to elapse between clearing of idle chat users. A <code>TimerTask</code> will be
     * added to a <code>Timer</code> scheduled for repeated fixed-delay execution whose main
     * responsibility is to kick users that have been idle for a certain time. A user is considered
     * idle if he/she didn't send any message to any group chat room for a certain amount of time.
     * See {@link #setUserIdleTime(int)}.
     *
     * @param timeout the time to elapse between clearing of idle chat users.
     */
    void setKickIdleUsersTimeout(int timeout);

    /**
     * Returns the time to elapse between clearing of idle chat users. A user is considered
     * idle if he/she didn't send any message to any group chat room for a certain amount of time.
     * See {@link #getUserIdleTime()}.
     *
     * @return the time to elapse between clearing of idle chat users.
     */
    int getKickIdleUsersTimeout();

    /**
     * Sets the number of milliseconds a user must be idle before he/she gets kicked from all
     * the rooms. By idle we mean that the user didn't send any message to any group chat room.
     *
     * @param idle the amount of time to wait before considering a user idle.
     */
    void setUserIdleTime(int idle);

    /**
     * Returns the number of milliseconds a user must be idle before he/she gets kicked from all
     * the rooms. By idle we mean that the user didn't send any message to any group chat room.
     *
     * @return the amount of time to wait before considering a user idle.
     */
    int getUserIdleTime();

    /**
     * Sets the time to elapse between logging the room conversations. A <code>TimerTask</code> will
     * be added to a <code>Timer</code> scheduled for repeated fixed-delay execution whose main
     * responsibility is to log queued rooms conversations. The number of queued conversations to
     * save on each run can be configured. See {@link #setLogConversationBatchSize(int)}.
     *
     * @param timeout the time to elapse between logging the room conversations.
     * @deprecated No longer used in Openfire 4.4.0 and later (replaced with continuous writes to database: see {@link ArchiveManager}).
     */
    @Deprecated
    default void setLogConversationsTimeout(int timeout) {}

    /**
     * Returns the time to elapse between logging the room conversations. A <code>TimerTask</code>
     * will be added to a <code>Timer</code> scheduled for repeated fixed-delay execution whose main
     * responsibility is to log queued rooms conversations. The number of queued conversations to
     * save on each run can be configured. See {@link #getLogConversationBatchSize()}.
     *
     * @return the time to elapse between logging the room conversations.
     * @deprecated No longer used in Openfire 4.4.0 and later (replaced with continuous writes to database: see {@link ArchiveManager}).
     */
    @Deprecated
    default int getLogConversationsTimeout() { return 300000; }

    /**
     * Sets the number of messages to save to the database on each run of the logging process.
     * Even though the saving of queued conversations takes place in another thread it is not
     * recommended specifying a big number.
     *
     * @param size the number of messages to save to the database on each run of the logging process.
     * @deprecated No longer used in Openfire 4.4.0 and later (replaced with continuous writes to database: see {@link ArchiveManager}).
     */
    @Deprecated
    default void setLogConversationBatchSize(int size) {};

    /**
     * Returns the number of messages to save to the database on each run of the logging process.
     *
     * @return the number of messages to save to the database on each run of the logging process.
     * @deprecated No longer used in Openfire 4.4.0 and later (replaced with continuous writes to database: see {@link ArchiveManager}).
     */
    @Deprecated
    default int getLogConversationBatchSize() { return 50; };

    Archiver<?> getArchiver();


    /**
     * Returns the maximum number of messages to save to the database on each run of the archiving process.
     * @return the maximum number of messages to save to the database on each run of the archiving process.
     */
    default int getLogMaxConversationBatchSize() { return 50; }

    /**
     * Sets the maximum number of messages to save to the database on each run of the archiving process.
     * Even though the saving of queued conversations takes place in another thread it is not
     * recommended specifying a big number.
     *
     * @param size the maximum number of messages to save to the database on each run of the archiving process.
     */
    default void setLogMaxConversationBatchSize(int size) {}

    /**
     * Returns the maximum time allowed to elapse between writing archive entries to the database.
     * @return the maximum time allowed to elapse between writing archive entries to the database.
     */
    default Duration getLogMaxBatchInterval() { return Duration.ofSeconds(10L); }

    /**
     * Sets the maximum time allowed to elapse between writing archive batches to the database.
     * @param interval the maximum time allowed to elapse between writing archive batches to the database.
     */
    default void setLogMaxBatchInterval(Duration interval) {}

    /**
     * Returns the maximum time to wait for a next incoming entry before writing the batch to the database.
     * @return the maximum time to wait for a next incoming entry before writing the batch to the database.
     */
    default Duration getLogBatchGracePeriod() { return Duration.ofSeconds(1L); }

    /**
     * Sets the maximum time to wait for a next incoming entry before writing the batch to the database.
     * @param interval the maximum time to wait for a next incoming entry before writing the batch to the database.
     */
    default void setLogBatchGracePeriod(Duration interval) {}

    /**
     * Obtain the server-wide default message history settings.
     * 
     * @return The message history strategy defaults for the server.
     */
    HistoryStrategy getHistoryStrategy();

    /**
     * Checks if the a particular entity is allowed to discover the room's existence.
     *
     * @param room The room to be discovered (cannot be null).
     * @param entity The JID of the entity (cannot be null).
     * @return true if the entity can discover the room, otherwise false.
     */
    boolean canDiscoverRoom(final MUCRoom room, final JID entity);

    /**
     * Obtains a chatroom by name. A chatroom is created for that name if none exists and the user
     * has permission. The user that asked for the chatroom will be the room's owner if the chatroom
     * was created.
     * 
     * @param roomName Name of the room to get.
     * @param userjid The user's normal jid, not the chat nickname jid.
     * @return The chatroom for the given name.
     * @throws NotAllowedException If the caller doesn't have permission to create a new room.
     */
    MUCRoom getChatRoom(String roomName, JID userjid) throws NotAllowedException;

    /**
     * Obtains a chatroom by name. If the chatroom does not exists then null will be returned.
     * 
     * @param roomName Name of the room to get.
     * @return The chatroom for the given name or null if the room does not exists.
     */
    MUCRoom getChatRoom(String roomName);
    
    /**
    * Forces a re-read of the room. Useful when a change occurs externally.
    * 
    * @param roomName Name of the room to refresh.
    */
    void refreshChatRoom(String roomName);
    
    /**
     * Retuns a list with a snapshot of all the rooms in the server (i.e. persistent or not,
     * in memory or not).
     *
     * @return a list with a snapshot of all the rooms.
     */
    List<MUCRoom> getChatRooms();

    /**
     * Returns true if the server includes a chatroom with the requested name.
     * 
     * @param roomName the name of the chatroom to check.
     * @return true if the server includes a chatroom with the requested name.
     */
    boolean hasChatRoom(String roomName);

    /**
     * Notification message indicating that the specified chat room was
     * removed from some other cluster member.
     *
     * @param room the removed room in another cluster node.
     */
    void chatRoomRemoved( LocalMUCRoom room );

    /**
     * Notification message indicating that a chat room has been created
     * in another cluster member.
     *
     * @param room the created room in another cluster node.
     */
    void chatRoomAdded( LocalMUCRoom room );

    /**
     * Removes the room associated with the given name.
     * 
     * @param roomName The room to remove.
     */
    void removeChatRoom(String roomName);

    /**
     * Returns the list of {@link org.jivesoftware.openfire.muc.MUCRole} in all rooms for the specified
     * user's session. When running in a cluster the list will include
     * {@link org.jivesoftware.openfire.muc.spi.LocalMUCRole} and {@link org.jivesoftware.openfire.muc.spi.RemoteMUCRole}.
     *
     *
     * @param user the full JID that identifies the session of the user.
     * @return the list of MUCRoles in all rooms for the specified user's session.
     */
    Collection<MUCRole> getMUCRoles(JID user);

    /**
     * Returns the total chat time of all rooms combined.
     * 
     * @return total chat time in milliseconds.
     */
    long getTotalChatTime();

    /**
     * Retuns the number of existing rooms in the server (i.e. persistent or not,
     * in memory or not).
     *
     * @return the number of existing rooms in the server.
     */
    int getNumberChatRooms();

    /**
     * Retuns the total number of occupants in all rooms in the server.
     *
     * @param onlyLocal true if only users connected to this JVM will be considered. Otherwise count cluster wise.
     * @return the number of existing rooms in the server.
     */
    int getNumberConnectedUsers( boolean onlyLocal );

    /**
     * Retuns the total number of users that have joined in all rooms in the server.
     *
     * @return the number of existing rooms in the server.
     */
    int getNumberRoomOccupants();

    /**
     * Returns the total number of incoming messages since last reset.
     *
     * @param resetAfter True if you want the counter to be reset after results returned.
     * @return the number of incoming messages through the service.
     */
    long getIncomingMessageCount( boolean resetAfter );

    /**
     * Returns the total number of outgoing messages since last reset.
     *
     * @param resetAfter True if you want the counter to be reset after results returned.
     * @return the number of outgoing messages through the service.
     */
    long getOutgoingMessageCount( boolean resetAfter );

    /**
     * Logs that a given message was sent to a room as part of a conversation. Every message sent
     * to the room that is allowed to be broadcasted and that was sent either from the room itself 
     * or from an occupant will be logged.<p>
     * 
     * Note: For performane reasons, the logged message won't be immediately saved. Instead we keep
     * the logged messages in memory until the logging process saves them to the database. It's 
     * possible to configure the logging process to run every X milliseconds and also the number 
     * of messages to log on each execution. 
     * @see org.jivesoftware.openfire.muc.spi.MultiUserChatServiceImpl#initialize(org.jivesoftware.openfire.XMPPServer)
     * 
     * @param room the room that received the message.
     * @param message the message to log as part of the conversation in the room.
     * @param sender the real XMPPAddress of the sender (e.g. john@example.org). 
     */
    void logConversation(MUCRoom room, Message message, JID sender);

    /**
     * Notification message indicating the server that an incoming message was broadcasted
     * to a given number of occupants.
     *
     * @param numOccupants number of occupants that received the message.
     */
    void messageBroadcastedTo(int numOccupants);

    /**
     * Enables or disables the MUC service. When disabled the MUC service will disappear from
     * the disco#items list. Moreover, service discovery features will be disabled. 
     *
     * @param enabled true if the service is enabled.
     * @param persistent true if the new setting will persist across restarts.
     */
    void enableService(boolean enabled, boolean persistent);

    /**
     * Returns true if the MUC service is available. Use {@link #enableService(boolean, boolean)} to
     * enable or disable the service.
     *
     * @return true if the MUC service is available.
     */
    boolean isServiceEnabled();

    /**
     * Returns true if the MUC service is a hidden, externally managed, service.  This is typically
     * set to true when the implementation is not the default one, and is not to be managed by
     * the standard Openfire interface.  If this is set to true, the service will not show up in
     * the service list in the admin console.
     *
     * @return true if the MUC service is hidden and externally managed.
     */
    boolean isHidden();

    /**
     * Add a IQHandler to MUC rooms and services. If the IQHandler only supports one or
     * other, it should quietly ignore it.
     * @param handler the IQ handler to add
     */
    void addIQHandler(IQHandler handler);
    void removeIQHandler(IQHandler handler);

    /**
     * Adds an extra Disco feature to the list of features returned for the conference service.
     * @param feature Feature to add.
     */
    void addExtraFeature(String feature);

    /**
     * Removes an extra Disco feature from the list of features returned for the conference service.
     * @param feature Feature to remove.
     */
    void removeExtraFeature(String feature);

    /**
     * Adds an extra Disco identity to the list of identities returned for the conference service.
     * @param category Category for identity.  e.g. conference
     * @param name Descriptive name for identity.  e.g. Public Chatrooms
     * @param type Type for identity.  e.g. text
     */
    void addExtraIdentity(String category, String name, String type);

    /**
     * Removes an extra Disco identity from the list of identities returned for the conference service.
     * @param name Name of identity to remove.
     */
    void removeExtraIdentity(String name);
}
