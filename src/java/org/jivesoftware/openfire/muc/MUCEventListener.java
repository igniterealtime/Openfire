/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.muc;

import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

/**
 * Interface to listen for MUC events. Use the {@link MUCEventDispatcher#addListener(MUCEventListener)}
 * method to register for events.
 *
 * @author Gaston Dombiak
 */
public interface MUCEventListener {

    /**
     * Event triggered when a new room was created.
     *
     * @param roomJID JID of the room that was created.
     */
    void roomCreated(JID roomJID);

    /**
     * Event triggered when a room was destroyed.
     *
     * @param roomJID JID of the room that was destroyed.
     */
    void roomDestroyed(JID roomJID);

    /**
     * Event triggered when a new occupant joins a room.
     *
     * @param roomJID the JID of the room where the occupant has joined.
     * @param user the JID of the user joining the room.
     * @param nickname nickname of the user in the room.
     */
    void occupantJoined(JID roomJID, JID user, String nickname);

    /**
     * Event triggered when an occupant left a room.
     *
     * @param roomJID the JID of the room where the occupant has left.
     * @param user the JID of the user leaving the room.
     */
    void occupantLeft(JID roomJID, JID user);

    /**
     * Event triggered when an occupant changed his nickname in a room.
     *
     * @param roomJID the JID of the room where the user changed his nickname.
     * @param user the JID of the user that changed his nickname.
     * @param oldNickname old nickname of the user in the room.
     * @param newNickname new nickname of the user in the room.
     */
    void nicknameChanged(JID roomJID, JID user, String oldNickname, String newNickname);

    /**
     * Event triggered when a room occupant sent a message to a room.
     *
     * @param roomJID the JID of the room that received the message.
     * @param user the JID of the user that sent the message.
     * @param nickname nickname used by the user when sending the message.
     * @param message the message sent by the room occupant.
     */
    void messageReceived(JID roomJID, JID user, String nickname, Message message);
}
