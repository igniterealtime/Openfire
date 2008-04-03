package org.jivesoftware.openfire.clearspace;

import org.xmpp.packet.JID;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.jivesoftware.util.cache.Cacheable;
import org.jivesoftware.util.cache.CacheSizes;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectInput;
import java.util.Date;

/**
 * A MUC event that is intended to be recorded in a transcript for a group chat room in Clearspace.
 *
 * @author Armando Jagucki
 */
public class ClearspaceMUCTranscriptEvent implements Externalizable, Cacheable {
    private Type type;
    public Date date;
    public String body;

    public JID roomJID;
    private JID user;
    public String nickname;


    public static ClearspaceMUCTranscriptEvent roomDestroyed(JID roomJID, Date date) {
        ClearspaceMUCTranscriptEvent event = new ClearspaceMUCTranscriptEvent();
        event.type = Type.roomDestroyed;
        event.roomJID = roomJID;
        event.date = date;
        return event;
    }

    public static ClearspaceMUCTranscriptEvent occupantJoined(JID roomJID, JID user, String nickname, Date date) {
        ClearspaceMUCTranscriptEvent event = new ClearspaceMUCTranscriptEvent();
        event.type = Type.occupantJoined;
        event.roomJID = roomJID;
        event.user = user;
        event.nickname = nickname;
        event.date = date;
        return event;
    }

    public static ClearspaceMUCTranscriptEvent occupantLeft(JID roomJID, JID user, Date date) {
        ClearspaceMUCTranscriptEvent event = new ClearspaceMUCTranscriptEvent();
        event.type = Type.occupantLeft;
        event.roomJID = roomJID;
        event.user = user;
        event.date = date;
        return event;
    }

    public static ClearspaceMUCTranscriptEvent nicknameChanged(JID roomJID, JID user, String newNickname, Date date) {
        ClearspaceMUCTranscriptEvent event = new ClearspaceMUCTranscriptEvent();
        event.type = Type.nicknameChanged;
        event.roomJID = roomJID;
        event.user = user;
        event.nickname = newNickname;
        event.date = date;
        return event;
    }

    public static ClearspaceMUCTranscriptEvent roomMessageReceived(JID roomJID, JID user, String nickname, String body,
                                                                   Date date) {
        ClearspaceMUCTranscriptEvent event = new ClearspaceMUCTranscriptEvent();
        event.type = Type.roomMessageReceived;
        event.roomJID = roomJID;
        event.user = user;
        event.nickname = nickname;
        event.body = body;
        event.date = date;
        return event;
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeInt(out, type.ordinal());
        ExternalizableUtil.getInstance().writeLong(out, date.getTime());

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

    /**
     * Used to estimate the size of this event as represented in a fragment of a
     * transcript-update packet sent by ClearspaceMUCTranscriptManager.
     *
     * We do not intend to use this object in a cache.
     *
     * @return the estimated size of this event as represented in a transcript-update packet.
     */
    public int getCachedSize() {
        int size = CacheSizes.sizeOfString(date.toString());
        size += CacheSizes.sizeOfString(body);
        size += CacheSizes.sizeOfString(roomJID.toString());
        size += CacheSizes.sizeOfString(user.toString());
        size += CacheSizes.sizeOfString(nickname);
        return size;
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
