/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger;

import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.PacketError;

/**
 * Controls what is done with offline messages.
 *
 * @author Iain Shigeoka
 */
public class OfflineMessageStrategy extends BasicModule {

    private static int quota = 100*1024; // Default to 100 K.
    private static Type type = Type.store_and_bounce;

    private OfflineMessageStore messageStore;
    private JID serverAddress;
    private PacketRouter router;

    public OfflineMessageStrategy() {
        super("Offline Message Strategy");
    }

    public int getQuota() {
        return quota;
    }

    public void setQuota(int quota) {
        OfflineMessageStrategy.quota = quota;
        JiveGlobals.setProperty("xmpp.offline.quota", Integer.toString(quota));
    }

    public OfflineMessageStrategy.Type getType() {
        return type;
    }

    public void setType(OfflineMessageStrategy.Type type) {
        if (type == null) {
            throw new IllegalArgumentException();
        }
        OfflineMessageStrategy.type = type;
        JiveGlobals.setProperty("xmpp.offline.type", type.toString());
    }

    public void storeOffline(Message message) {
        if (message != null) {
            // Do nothing if the message was sent to the server itself or to an anonymous user
            if (message.getTo() == null || serverAddress.equals(message.getTo()) ||
                    message.getTo().getNode() == null) {
                return;
            }

            if (type == Type.bounce) {
                bounce(message);
            }
            else if (type == Type.store) {
                store(message);
            }
            else if (type == Type.store_and_bounce) {
                if (underQuota(message)) {
                    store(message);
                }
                else {
                    bounce(message);
                }
            }
            else if (type == Type.store_and_drop) {
                if (underQuota(message)) {
                    store(message);
                }
            }
        }
    }

    private boolean underQuota(Message message) {
        return quota > messageStore.getSize(message.getTo().getNode()) + message.toXML().length();
    }

    private void store(Message message) {
        messageStore.addMessage(message);
    }

    private void bounce(Message message) {
        // Do nothing if the sender was the server itself
        if (message.getFrom() == null) {
            return;
        }
        try {
            // Generate a rejection response to the sender
            Message errorResponse = message.createCopy();
            errorResponse.setError(new PacketError(PacketError.Condition.item_not_found,
                    PacketError.Type.continue_processing));
            errorResponse.setFrom(message.getTo());
            errorResponse.setTo(message.getFrom());
            // Send the response
            router.route(errorResponse);
        }
        catch (Exception e) {
            Log.error(e);
        }
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);
        messageStore = server.getOfflineMessageStore();
        router = server.getPacketRouter();
        serverAddress = new JID(server.getServerInfo().getName());

        String quota = JiveGlobals.getProperty("xmpp.offline.quota");
        if (quota != null && quota.length() > 0) {
            OfflineMessageStrategy.quota = Integer.parseInt(quota);
        }
        String type = JiveGlobals.getProperty("xmpp.offline.type");
        if (type != null && type.length() > 0) {
            OfflineMessageStrategy.type = Type.valueOf(type);
        }
    }

    /**
     * Strategy types.
     */
    public enum Type {

        /**
         * All messages are bounced to the sender.
         */
        bounce,

        /**
         * All messages are silently dropped.
         */
        drop,

        /**
         * All messages are stored.
         */
        store,

        /**
         * Messages are stored up to the storage limit, and then bounced.
         */
        store_and_bounce,

        /**
         * Messages are stored up to the storage limit, and then silently dropped.
         */
        store_and_drop;
    }
}