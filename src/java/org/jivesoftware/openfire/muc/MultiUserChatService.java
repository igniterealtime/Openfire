/**
 * $RCSfile$
 * $Revision: 3036 $
 * $Date: 2005-11-07 15:15:00 -0300 (Mon, 07 Nov 2005) $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.muc;

import org.xmpp.component.Component;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.jivesoftware.openfire.muc.spi.LocalMUCRoom;
import org.jivesoftware.database.JiveID;
import org.jivesoftware.util.JiveConstants;

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
     * @return a list of bare JIDs.
     */
    Collection<String> getSysadmins();

    /**
     * Adds a new system administrator of the MUC service. A sysadmin has the same permissions as 
     * a room owner. 
     * 
     * @param userJID the bare JID of the new user to add as a system administrator.
     */
    void addSysadmin(String userJID);

    /**
     * Removes a system administrator of the MUC service.
     * 
     * @param userJID the bare JID of the user to remove from the list.
     */
    void removeSysadmin(String userJID);

    /**
     * Returns false if anyone can create rooms or true if only the returned JIDs in
     * <code>getUsersAllowedToCreate</code> are allowed to create rooms.
     *
     * @return true if only some JIDs are allowed to create rooms.
     */
    boolean isRoomCreationRestricted();

    /**
     * Sets if anyone can create rooms or if only the returned JIDs in
     * <code>getUsersAllowedToCreate</code> are allowed to create rooms.
     *
     * @param roomCreationRestricted whether anyone can create rooms or not.
     */
    void setRoomCreationRestricted(boolean roomCreationRestricted);

    /**
     * Returns the collection of JIDs that are allowed to create MUC rooms. An empty list means that
     * anyone can create a room. 
     * 
     * @return a list of bare JIDs.
     */
    Collection<String> getUsersAllowedToCreate();

    /**
     * Adds a new user to the list of JIDs that are allowed to create MUC rooms.
     * 
     * @param userJID the bare JID of the new user to add to list.
     */
    void addUserAllowedToCreate(String userJID);

    /**
     * Removes a user from list of JIDs that are allowed to create MUC rooms.
     * 
     * @param userJID the bare JID of the user to remove from the list.
     */
    void removeUserAllowedToCreate(String userJID);

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
     */
    void setLogConversationsTimeout(int timeout);

    /**
     * Returns the time to elapse between logging the room conversations. A <code>TimerTask</code>
     * will be added to a <code>Timer</code> scheduled for repeated fixed-delay execution whose main
     * responsibility is to log queued rooms conversations. The number of queued conversations to
     * save on each run can be configured. See {@link #getLogConversationBatchSize()}.
     *
     * @return the time to elapse between logging the room conversations.
     */
    int getLogConversationsTimeout();

    /**
     * Sets the number of messages to save to the database on each run of the logging process.
     * Even though the saving of queued conversations takes place in another thread it is not
     * recommended specifying a big number.
     *
     * @param size the number of messages to save to the database on each run of the logging process.
     */
    void setLogConversationBatchSize(int size);

    /**
     * Returns the number of messages to save to the database on each run of the logging process.
     *
     * @return the number of messages to save to the database on each run of the logging process.
     */
    int getLogConversationBatchSize();

    /**
     * Obtain the server-wide default message history settings.
     * 
     * @return The message history strategy defaults for the server.
     */
    HistoryStrategy getHistoryStrategy();

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
    public void chatRoomRemoved(LocalMUCRoom room);

    /**
     * Notification message indicating that a chat room has been created
     * in another cluster member.
     *
     * @param room the created room in another cluster node.
     */
    public void chatRoomAdded(LocalMUCRoom room);

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
    public long getTotalChatTime();

    /**
     * Retuns the number of existing rooms in the server (i.e. persistent or not,
     * in memory or not).
     *
     * @return the number of existing rooms in the server.
     */
    public int getNumberChatRooms();

    /**
     * Retuns the total number of occupants in all rooms in the server.
     *
     * @param onlyLocal true if only users connected to this JVM will be considered. Otherwise count cluster wise.
     * @return the number of existing rooms in the server.
     */
    public int getNumberConnectedUsers(boolean onlyLocal);

    /**
     * Retuns the total number of users that have joined in all rooms in the server.
     *
     * @return the number of existing rooms in the server.
     */
    public int getNumberRoomOccupants();

    /**
     * Returns the total number of incoming messages since last reset.
     *
     * @param resetAfter True if you want the counter to be reset after results returned.
     * @return the number of incoming messages through the service.
     */
    public long getIncomingMessageCount(boolean resetAfter);

    /**
     * Returns the total number of outgoing messages since last reset.
     *
     * @param resetAfter True if you want the counter to be reset after results returned.
     * @return the number of outgoing messages through the service.
     */
    public long getOutgoingMessageCount(boolean resetAfter);

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
}