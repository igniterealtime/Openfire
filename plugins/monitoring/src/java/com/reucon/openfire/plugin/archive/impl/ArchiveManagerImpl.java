package com.reucon.openfire.plugin.archive.impl;

import com.reucon.openfire.plugin.archive.ArchiveFactory;
import com.reucon.openfire.plugin.archive.ArchiveManager;
import com.reucon.openfire.plugin.archive.IndexManager;
import com.reucon.openfire.plugin.archive.PersistenceManager;
import com.reucon.openfire.plugin.archive.model.ArchivedMessage;
import com.reucon.openfire.plugin.archive.model.Conversation;
import com.reucon.openfire.plugin.archive.model.Participant;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.session.Session;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Default implementation of ArchiveManager.
 */
public class ArchiveManagerImpl implements ArchiveManager
{
    private final PersistenceManager persistenceManager;
    private final IndexManager indexManager;
    private final Collection<Conversation> activeConversations;
    private int conversationTimeout;

    public ArchiveManagerImpl(PersistenceManager persistenceManager, IndexManager indexManager,
                              int conversationTimeout)
    {
        this.persistenceManager = persistenceManager;
        this.indexManager = indexManager;
        this.conversationTimeout = conversationTimeout;

        activeConversations = persistenceManager.getActiveConversations(conversationTimeout);
    }

    public void archiveMessage(Session session, Message message, boolean incoming)
    {
        final XMPPServer server = XMPPServer.getInstance();
        final ArchivedMessage.Direction direction;
        final ArchivedMessage archivedMessage;
        final Conversation conversation;
        final JID ownerJid;
        final JID withJid;

        // TODO support groupchat
        if (message.getType() != Message.Type.chat && message.getType() != Message.Type.normal)
        {
            return;
        }

        if (server.isLocal(message.getFrom()) && incoming)
        {
            ownerJid = message.getFrom();
            withJid = message.getTo();
            // sent by the owner => to
            direction = ArchivedMessage.Direction.to;
        }
        else if (server.isLocal(message.getTo()) && ! incoming)
        {
            ownerJid = message.getTo();
            withJid = message.getFrom();
            // received by the owner => from
            direction = ArchivedMessage.Direction.from;
        }
        else
        {
            return;
        }

        archivedMessage = ArchiveFactory.createArchivedMessage(session, message, direction, withJid);
        if (archivedMessage.isEmpty())
        {
            return;
        }

        conversation = determineConversation(ownerJid, withJid, message.getSubject(), message.getThread(), archivedMessage);
        archivedMessage.setConversation(conversation);

        persistenceManager.createMessage(archivedMessage);
        if (indexManager != null)
        {
            indexManager.indexObject(archivedMessage);
        }
    }

    public void setConversationTimeout(int conversationTimeout)
    {
        this.conversationTimeout = conversationTimeout;
    }

    private Conversation determineConversation(JID ownerJid, JID withJid, String subject, String thread, ArchivedMessage archivedMessage)
    {
        Conversation conversation = null;
        Collection<Conversation> staleConversations;

        staleConversations = new ArrayList<Conversation>();
        synchronized (activeConversations)
        {
            for (Conversation c : activeConversations)
            {
                if (c.isStale(conversationTimeout))
                {
                    staleConversations.add(c);
                    continue;
                }

                if (matches(ownerJid, withJid, thread, c))
                {
                    conversation = c;
                    break;
                }
            }

            activeConversations.removeAll(staleConversations);
            
            if (conversation == null)
            {
                final Participant p1;
                final Participant p2;

                conversation = new Conversation(archivedMessage.getTime(),
                        ownerJid.toBareJID(), ownerJid.getResource(), withJid.toBareJID(), withJid.getResource(),
                        subject, thread);
                persistenceManager.createConversation(conversation);

                p1 = new Participant(archivedMessage.getTime(), ownerJid.toBareJID());
                conversation.addParticipant(p1);
                persistenceManager.createParticipant(p1, conversation.getId());

                p2 = new Participant(archivedMessage.getTime(), withJid.toBareJID());
                conversation.addParticipant(p2);
                persistenceManager.createParticipant(p2, conversation.getId());
                activeConversations.add(conversation);
            }
            else
            {
                conversation.setEnd(archivedMessage.getTime());
                persistenceManager.updateConversationEnd(conversation);
            }
        }

        return conversation;
    }

    private boolean matches(JID ownerJid, JID withJid, String thread, Conversation c)
    {
        if (! ownerJid.toBareJID().equals(c.getOwnerJid()))
        {
            return false;
        }
        if (! withJid.toBareJID().equals(c.getWithJid()))
        {
            return false;
        }

        /*
        if (ownerJid.getResource() != null)
        {
            if (! ownerJid.getResource().equals(c.getOwnerResource()))
            {
                return false;
            }
        }
        else
        {
            if (c.getOwnerResource() != null)
            {
                return false;
            }
        }

        if (withJid.getResource() != null)
        {
            if (! withJid.getResource().equals(c.getWithResource()))
            {
                return false;
            }
        }
        else
        {
            if (c.getWithResource() != null)
            {
                return false;
            }
        }
        */

        if (thread != null)
        {
            if (! thread.equals(c.getThread()))
            {
                return false;
            }
        }
        else
        {
            if (c.getThread() != null)
            {
                return false;
            }
        }

        return true;
    }
}
