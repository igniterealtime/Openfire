package org.jivesoftware.openfire.clearspace;

import org.xmpp.packet.JID;
import org.jivesoftware.util.cache.ExternalizableUtil;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectInput;

/**
 * A MUC event that is intended to be recorded in a transcript for a group chat room in Clearspace.
 *
 * @author Armando Jagucki
 */
public class ClearspaceMUCTranscriptEvent implements Externalizable {
    public Type type;
    public long timestamp;
    public String body;

    public JID roomJID;
    public JID user;

    public static ClearspaceMUCTranscriptEvent roomDestroyed(JID roomJID, long timestamp) {
        ClearspaceMUCTranscriptEvent event = new ClearspaceMUCTranscriptEvent();
        event.type = Type.roomDestroyed;
        event.roomJID = roomJID;
        event.timestamp = timestamp;
        return event;
    }

    public static ClearspaceMUCTranscriptEvent occupantJoined(JID roomJID, JID user, long timestamp) {
        ClearspaceMUCTranscriptEvent event = new ClearspaceMUCTranscriptEvent();
        event.type = Type.occupantJoined;
        event.roomJID = roomJID;
        event.user = user;
        event.timestamp = timestamp;
        return event;
    }

    public static ClearspaceMUCTranscriptEvent occupantLeft(JID roomJID, JID user, long timestamp) {
        ClearspaceMUCTranscriptEvent event = new ClearspaceMUCTranscriptEvent();
        event.type = Type.occupantLeft;
        event.roomJID = roomJID;
        event.user = user;
        event.timestamp = timestamp;
        return event;
    }

    public static ClearspaceMUCTranscriptEvent roomMessageReceived(JID roomJID, JID user, String body,
                                                                   long timestamp) {
        ClearspaceMUCTranscriptEvent event = new ClearspaceMUCTranscriptEvent();
        event.type = Type.roomMessageReceived;
        event.roomJID = roomJID;
        event.user = user;
        event.body = body;
        event.timestamp = timestamp;
        return event;
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeInt(out, type.ordinal());
        ExternalizableUtil.getInstance().writeLong(out, timestamp);

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
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        type = Type.values()[ExternalizableUtil.getInstance().readInt(in)];
        timestamp = ExternalizableUtil.getInstance().readLong(in);

        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            body = ExternalizableUtil.getInstance().readSafeUTF(in);
        }

        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            roomJID = (JID) ExternalizableUtil.getInstance().readSerializable(in);
        }
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            user = (JID) ExternalizableUtil.getInstance().readSerializable(in);
        }
    }

    public static enum Type {
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
         * Event triggered when a room occupant sent a message to a room.
         */
        roomMessageReceived
    }
}
