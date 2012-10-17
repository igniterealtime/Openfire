package com.reucon.openfire.plugin.archive;

import org.jivesoftware.openfire.session.Session;
import org.xmpp.packet.Message;

/**
 * Adds messages to the archive.
 */
public interface ArchiveManager
{
    /**
     * Adds a message to the archive.
     *
     * @param session  the session the message was received through.
     * @param message  the message to archive.
     * @param incoming <code>true</code> if this a message received by the server, <code>false</code> if it
     *                 is sent by the server.
     */
    void archiveMessage(Session session, Message message, boolean incoming);

    /**
     * Sets the conversation timeout.<p>
     * A new conversation is created if there no messages have been exchanged between two JIDs
     * for the given timeout.
     *
     * @param conversationTimeout the conversation timeout to set in minutes.
     */
    void setConversationTimeout(int conversationTimeout);
}
