package com.reucon.openfire.plugin.archive;

import com.reucon.openfire.plugin.archive.model.ArchivedMessage;
import com.reucon.openfire.plugin.archive.model.Conversation;
import com.reucon.openfire.plugin.archive.model.Participant;
import com.reucon.openfire.plugin.archive.xep0059.XmppResultSet;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * Manages database persistence.
 */
public interface PersistenceManager
{
    /**
     * Creates a new archived message.
     *
     * @param message the message to create.
     * @return <code>true</code> on success, <code>false</code> otherwise.
     */
    boolean createMessage(ArchivedMessage message);

    /**
     * Selects all messages and passes each message to the given callback for processing.
     *
     * @param callback callback to process messages.
     * @return number of messages processed.
     */
    int processAllMessages(ArchivedMessageConsumer callback);

    /**
     * Creates a new conversation.
     *
     * @param conversation the conversation to create.
     * @return <code>true</code> on success, <code>false</code> otherwise.
     */
    boolean createConversation(Conversation conversation);

    /**
     * Updates the end time of a conversation. The conversation must be persisted.
     *
     * @param conversation conversation to update with id and endDate attributes not null.
     * @return <code>true</code> on success, <code>false</code> otherwise.
     */
    boolean updateConversationEnd(Conversation conversation);

    /**
     * Adds a new participant to a conversation.
     *
     * @param participant    the participant to add.
     * @param conversationId id of the conversation to add the participant to.
     * @return <code>true</code> on success, <code>false</code> otherwise.
     */
    boolean createParticipant(Participant participant, Long conversationId);

    List<Conversation> findConversations(String[] participants, Date startDate, Date endDate);

    /**
     * Searches for conversations.
     *
     * @param startDate earliest start date of the conversation to find or <code>null</code> for any.
     * @param endDate   latest end date of the conversation to find or <code>null</code> for any.
     * @param owner     bare jid of the owner of the conversation to find or <code>null</code> for any.
     * @param with      bare jid of the communication partner or <code>null</code> for any. This is either
     *                  the jid of another XMPP user or the jid of a group chat.
     * @return the conversations that matched search critera without messages and participants.
     */
    Collection<Conversation> findConversations(Date startDate, Date endDate, String owner, String with, XmppResultSet xmppResultSet);

    Collection<Conversation> getActiveConversations(int conversationTimeout);

    List<Conversation> getConversations(Collection<Long> conversationIds);

    /**
     * Returns the conversation with the given owner, with and start time including participants and messages.
     *
     * @param ownerJid bare jid of the conversation's owner.
     * @param withJid  bare jid of the communication partner.
     * @param start    exact start time
     * @return the matching conversation or <code>null</code> if none matches.
     */
    Conversation getConversation(String ownerJid, String withJid, Date start);

    /**
     * Returns the conversation with the given id including participants and messages.
     *
     * @param conversationId id of the conversation to retrieve.
     * @return the matching conversation or <code>null</code> if none matches.
     */
    Conversation getConversation(Long conversationId);
}
