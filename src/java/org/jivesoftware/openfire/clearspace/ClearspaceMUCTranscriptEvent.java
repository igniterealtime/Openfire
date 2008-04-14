package org.jivesoftware.openfire.clearspace;

import org.xmpp.packet.JID;

/**
 * A MUC event that is intended to be recorded in a transcript for a group chat room in Clearspace.
 *
 * @author Armando Jagucki
 */
public class ClearspaceMUCTranscriptEvent {
    public Type type;
    public long timestamp;
    public String content;

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

    public static ClearspaceMUCTranscriptEvent messageReceived(JID roomJID, JID user, String body,
                                                                   long timestamp) {
        ClearspaceMUCTranscriptEvent event = new ClearspaceMUCTranscriptEvent();
        event.type = Type.messageReceived;
        event.roomJID = roomJID;
        event.user = user;
        event.content = body;
        event.timestamp = timestamp;
        return event;
    }

    public static ClearspaceMUCTranscriptEvent roomSubjectChanged(JID roomJID, JID user, String newSubject,
                                                                  long timestamp) {
        ClearspaceMUCTranscriptEvent event = new ClearspaceMUCTranscriptEvent();
        event.type = Type.roomSubjectChanged;
        event.roomJID = roomJID;
        event.user = user;
        event.content = newSubject;
        event.timestamp = timestamp;
        return event;
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
        messageReceived,
        /**
         * Event triggered when a room's subject has changed.
         */
        roomSubjectChanged
    }
}
