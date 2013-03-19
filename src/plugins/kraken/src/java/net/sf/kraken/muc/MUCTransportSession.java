/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.muc;

import net.sf.kraken.roster.TransportBuddy;
import net.sf.kraken.session.TransportSession;
import net.sf.kraken.type.PresenceType;

/**
 * @author Daniel Henninger
 */
public abstract class MUCTransportSession<B extends TransportBuddy> {

    /**
     * Creates the MUC session instance, associated with a particular room name.
     *
     * @param session Transport session that the MUC session is associated with.
     * @param roomname Name of room associated with this MUC session.
     * @param nickname Nickname we are using with the MUC session.
     * @param transport MUCTransport we are associated with.
     */
    public MUCTransportSession(TransportSession<B> session, String roomname, String nickname, BaseMUCTransport<B> transport) {
        this.session = session;
        this.roomname = roomname;
        this.nickname = nickname;
        this.transport = transport;
    }

    /* MUC transport we are associaed with. */
    public BaseMUCTransport<B> transport = null;

    /* Name of room this session is associated with. */
    public String roomname = null;

    /* Transport session this session are associated with. */
    public TransportSession<B> session = null;

    /* Nickname associated with this session. */
    public String nickname = null;

    /**
     * Retrieves the nickname associated with this session.
     *
     * @return The nickname associated with the session.
     */
    public String getNickname() {
        return nickname;
    }

    /**
     * Connect to the actual chatroom using a specified nickname.
     */
    public abstract void enterRoom();

    /**
     * Disconnects from/leaves a chatroom on the remote service.
     */
    public abstract void leaveRoom();

    /**
     * Sends a message to the chatroom.
     *
     * @param message Message to send to chatroom.
     */
    public abstract void sendMessage(String message);

    /**
     * Sends a private message to a user in a chatroom.
     *
     * @param nickname Nickname of user to send message to.
     * @param message Message to send to person in chatroom.
     */
    public abstract void sendPrivateMessage(String nickname, String message);

    /**
     * Updates the user's status in the chat room.
     *
     * @param presenceType Type of presence.
     */
    public abstract void updateStatus(PresenceType presenceType);

    /**
     * Updates the topic of the chat room.
     *
     * @param topic New topic to set.
     */
    public abstract void updateTopic(String topic);

    /**
     * Kick a person from the chat room.
     *
     * @param nickname Nickname of user to kick.
     * @param reason Reason for kicking the user.
     */
    public abstract void kickUser(String nickname, String reason);

    /**
     * Grant voice to a chat room member.
     *
     * @param nickname Nickname to grant voice to.
     */
    public abstract void grantVoice(String nickname);

    /**
     * Revoke voice from a chat room member.
     *
     * @param nickname Nickname to revoke voice from.
     */
    public abstract void revokeVoice(String nickname);

    /**
     * Ban a user from the chat room.
     *
     * @param nickname Nickname to ban from the chat room.
     * @param reason Reason for banning the user.
     */
    public abstract void banUser(String nickname, String reason);

    /**
     * Grant membership to a chat room member.
     *
     * @param nickname Nickname to grant membership to.
     */
    public abstract void grantMembership(String nickname);

    /**
     * Revoke membership from a chat room member.
     *
     * @param nickname Nickname to revoke membership from.
     */
    public abstract void revokeMembership(String nickname);

    /**
     * Grant moderator status to a chat room member.
     *
     * @param nickname Nickname to grant moderator status to.
     */
    public abstract void grantModerator(String nickname);

    /**
     * Revoke moderator status from a chat room member.
     *
     * @param nickname Nickname to revoke moderator status from.
     */
    public abstract void revokeModerator(String nickname);

}
