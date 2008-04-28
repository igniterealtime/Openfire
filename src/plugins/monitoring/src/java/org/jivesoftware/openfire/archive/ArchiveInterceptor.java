/**
 * $Revision: 3034 $
 * $Date: 2005-11-04 21:02:33 -0300 (Fri, 04 Nov 2005) $
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.archive;

import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.session.Session;
import org.picocontainer.Startable;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import java.util.Date;

/**
 * Intercepts packets to track conversations. Only the following messages
 * are processed:
 * <ul>
 *  <li>Messages sent between local users.</li>
 *  <li>Messages sent between local user and remote entities (e.g. remote users).</li>
 *  <li>Messages sent between local users and users using legacy networks (i.e. transports).</li>
 * </ul>
 * Therefore, messages that are sent to Publish-Subscribe or any other internal service are ignored.
 *
 * @author Matt Tucker
 */
public class ArchiveInterceptor implements PacketInterceptor, Startable {

    private ConversationManager conversationManager;

    public ArchiveInterceptor(ConversationManager conversationManager) {
        this.conversationManager = conversationManager;
    }

    public void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed)
            throws PacketRejectedException
    {
        // Ignore any packets that haven't already been processed by interceptors.
        if (!processed) {
            return;
        }
        if (packet instanceof Message) {
            // Ignore any outgoing messages (we'll catch them when they're incoming).
            if (!incoming) {
                return;
            }
            Message message = (Message) packet;
            // Ignore any messages that don't have a body so that we skip events.
            // Note: XHTML messages should always include a body so we should be ok. It's
            // possible that we may need special XHTML filtering in the future, however.
            if (message.getBody() != null) {
                // Only process messages that are between two users, group chat rooms, or gateways.
                if (conversationManager.isConversation(message)) {
                    // Process this event in the senior cluster member or local JVM when not in a cluster
                    if (ClusterManager.isSeniorClusterMember()) {
                        conversationManager.processMessage(message.getFrom(), message.getTo(), message.getBody(), new Date());
                    }
                    else {
                        JID sender = message.getFrom();
                        JID receiver = message.getTo();
                        ConversationEventsQueue eventsQueue = conversationManager.getConversationEventsQueue();
                        eventsQueue.addChatEvent(conversationManager.getConversationKey(sender, receiver),
                                ConversationEvent.chatMessageReceived(sender, receiver,
                                        conversationManager.isMessageArchivingEnabled() ? message.getBody() : null,
                                        new Date()));
                    }
                }
            }
        }
    }

    public void start() {
        InterceptorManager.getInstance().addInterceptor(this);
    }

    public void stop() {
        InterceptorManager.getInstance().removeInterceptor(this);
        conversationManager = null;
    }
}
