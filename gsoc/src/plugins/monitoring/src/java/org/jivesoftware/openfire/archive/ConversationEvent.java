/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.archive;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.JID;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Date;

/**
 * Conversation events are only used when running in a cluster as a way to send to the senior cluster
 * member information about a conversation that is taking place in this cluster node.
 *
 * @author Gaston Dombiak
 */
public class ConversationEvent implements Externalizable {
    private Type type;
    private Date date;
    private String body;

    private JID sender;
    private JID receiver;

    private JID roomJID;
    private JID user;
    private String nickname;

    /**
     * Do not use this constructor. It only exists for serialization purposes.
     */
    public ConversationEvent() {
    }

    public void run(ConversationManager conversationManager) {
        if (Type.chatMessageReceived == type) {
            conversationManager.processMessage(sender, receiver, body, date);
        }
        else if (Type.roomDestroyed == type) {
            conversationManager.roomConversationEnded(roomJID, date);
        }
        else if (Type.occupantJoined == type) {
            conversationManager.joinedGroupConversation(roomJID, user, nickname, date);
        }
        else if (Type.occupantLeft == type) {
            conversationManager.leftGroupConversation(roomJID, user, date);
            // If there are no more occupants then consider the group conversarion over
            MUCRoom mucRoom = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(roomJID).getChatRoom(roomJID.getNode());
            if (mucRoom != null &&  mucRoom.getOccupantsCount() == 0) {
                conversationManager.roomConversationEnded(roomJID, date);
            }
        }
        else if (Type.nicknameChanged == type) {
            conversationManager.leftGroupConversation(roomJID, user, date);
            conversationManager.joinedGroupConversation(roomJID, user, nickname, new Date(date.getTime() + 1));
        }
        else if (Type.roomMessageReceived == type) {
            conversationManager.processRoomMessage(roomJID, user, nickname, body, date);
        }
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeInt(out, type.ordinal());
        ExternalizableUtil.getInstance().writeLong(out, date.getTime());

        ExternalizableUtil.getInstance().writeBoolean(out, sender != null);
        if (sender != null) {
            ExternalizableUtil.getInstance().writeSerializable(out, sender);
        }
        ExternalizableUtil.getInstance().writeBoolean(out, receiver != null);
        if (receiver != null) {
            ExternalizableUtil.getInstance().writeSerializable(out, receiver);
        }
        ExternalizableUtil.getInstance().writeBoolean(out, body != null);
        if (body != null) {
            ExternalizableUtil.getInstance().writeSafeUTF(out, body);
        }

        ExternalizableUtil.getInstance().writeBoolean(out, roomJID != null);
        if (roomJID != null) {
            ExternalizableUtil.getInstance().writeSerializable(out, roomJID);
        }
        ExternalizableUtil.getInstance().writeBoolean(out, user != null);
        if (user != null) {
            ExternalizableUtil.getInstance().writeSerializable(out, user);
        }
        ExternalizableUtil.getInstance().writeBoolean(out, nickname != null);
        if (nickname != null) {
            ExternalizableUtil.getInstance().writeSafeUTF(out, nickname);
        }
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        type = Type.values()[ExternalizableUtil.getInstance().readInt(in)];
        date = new Date(ExternalizableUtil.getInstance().readLong(in));

        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            sender = (JID) ExternalizableUtil.getInstance().readSerializable(in);
        }
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            receiver = (JID) ExternalizableUtil.getInstance().readSerializable(in);
        }
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            body = ExternalizableUtil.getInstance().readSafeUTF(in);
        }

        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            roomJID = (JID) ExternalizableUtil.getInstance().readSerializable(in);
        }
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            user = (JID) ExternalizableUtil.getInstance().readSerializable(in);
        }
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            nickname = ExternalizableUtil.getInstance().readSafeUTF(in);
        }
    }

    public static ConversationEvent chatMessageReceived(JID sender, JID receiver, String body, Date date) {
        ConversationEvent event = new ConversationEvent();
        event.type = Type.chatMessageReceived;
        event.sender = sender;
        event.receiver = receiver;
        event.body = body;
        event.date = date;
        return event;
    }

    public static ConversationEvent roomDestroyed(JID roomJID, Date date) {
        ConversationEvent event = new ConversationEvent();
        event.type = Type.roomDestroyed;
        event.roomJID = roomJID;
        event.date = date;
        return event;
    }

    public static ConversationEvent occupantJoined(JID roomJID, JID user, String nickname, Date date) {
        ConversationEvent event = new ConversationEvent();
        event.type = Type.occupantJoined;
        event.roomJID = roomJID;
        event.user = user;
        event.nickname = nickname;
        event.date = date;
        return event;
    }

    public static ConversationEvent occupantLeft(JID roomJID, JID user, Date date) {
        ConversationEvent event = new ConversationEvent();
        event.type = Type.occupantLeft;
        event.roomJID = roomJID;
        event.user = user;
        event.date = date;
        return event;
    }

    public static ConversationEvent nicknameChanged(JID roomJID, JID user, String newNickname, Date date) {
        ConversationEvent event = new ConversationEvent();
        event.type = Type.nicknameChanged;
        event.roomJID = roomJID;
        event.user = user;
        event.nickname = newNickname;
        event.date = date;
        return event;
    }

    public static ConversationEvent roomMessageReceived(JID roomJID, JID user, String nickname, String body,
                                                        Date date) {
        ConversationEvent event = new ConversationEvent();
        event.type = Type.roomMessageReceived;
        event.roomJID = roomJID;
        event.user = user;
        event.nickname = nickname;
        event.body = body;
        event.date = date;
        return event;
    }

    private static enum Type {
        /**
         * Event triggered when a room was destroyed.
         */
        roomDestroyed,
        /**
         * Event triggered when a new occupant joins a room.
         */
        occupantJoined,
        /**
         * Event triggered when an occupant left a room.
         */
        occupantLeft,
        /**
         * Event triggered when an occupant changed his nickname in a room.
         */
        nicknameChanged,
        /**
         * Event triggered when a room occupant sent a message to a room.
         */
        roomMessageReceived,
        /**
         * Event triggered when a user sent a message to another user.
         */
        chatMessageReceived
    }
}
