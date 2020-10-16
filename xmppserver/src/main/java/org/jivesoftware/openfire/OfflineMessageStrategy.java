/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.jivesoftware.openfire.privacy.PrivacyList;
import org.jivesoftware.openfire.privacy.PrivacyListManager;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.PacketError;

/**
 * Controls what is done with offline messages.
 *
 * @author Iain Shigeoka
 */
public class OfflineMessageStrategy extends BasicModule implements ServerFeaturesProvider {

    private static final Logger Log = LoggerFactory.getLogger(OfflineMessageStrategy.class);

    private static int quota = 100*1024; // Default to 100 K.
    private static Type type = Type.store_and_bounce;

    private static List<OfflineMessageListener> listeners = new CopyOnWriteArrayList<>();

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
            // Do nothing if the message was sent to the server itself, an anonymous user or a non-existent user
            // Also ignore message carbons
            JID recipientJID = message.getTo();
            if (recipientJID == null || serverAddress.equals(recipientJID) ||
                    recipientJID.getNode() == null ||
                    message.getExtension("received", "urn:xmpp:carbons:2") != null ||
                    !UserManager.getInstance().isRegisteredUser(recipientJID, false)) {
                return;
            }

            // Do not store messages if communication is blocked
            PrivacyList list =
                    PrivacyListManager.getInstance().getDefaultPrivacyList(recipientJID.getNode());
            if (list != null && list.shouldBlockPacket(message)) {
                Message result = message.createCopy();
                result.setTo(message.getFrom());
                result.setFrom(message.getTo());
                result.setError(PacketError.Condition.service_unavailable);
                XMPPServer.getInstance().getRoutingTable().routePacket(message.getFrom(), result, true);
                return;
            }

            // 8.5.2.  localpart@domainpart
            // 8.5.2.2.  No Available or Connected Resources
            if (recipientJID.getResource() == null) {
                if (message.getType() == Message.Type.headline || message.getType() == Message.Type.error) {
                    // For a message stanza of type "headline" or "error", the server MUST silently ignore the message.
                    return;
                }
                // // For a message stanza of type "groupchat", the server MUST return an error to the sender, which SHOULD be <service-unavailable/>.
                else if (message.getType() == Message.Type.groupchat) {
                    bounce(message);
                    return;
                }
            } else {
                // 8.5.3.  localpart@domainpart/resourcepart
                // 8.5.3.2.1.  Message

                // For a message stanza of type "normal", "groupchat", or "headline", the server MUST either (a) silently ignore the stanza
                // or (b) return an error stanza to the sender, which SHOULD be <service-unavailable/>.
                if (message.getType() == Message.Type.normal || message.getType() == Message.Type.groupchat || message.getType() == Message.Type.headline) {
                    // Depending on the OfflineMessageStragey, we may silently ignore or bounce
                    if (type == Type.bounce) {
                        bounce(message);
                    }
                    // Either bounce or silently ignore, never store such messages
                    return;
                }
                // For a message stanza of type "error", the server MUST silently ignore the stanza.
                else if (message.getType() == Message.Type.error) {
                    return;
                }
            }

            switch (type) {
            case bounce:
                bounce(message);
                break;
            case store:
                store(message);
                break;
            case store_and_bounce:
                if (underQuota(message)) {
                    store(message);
                }
                else {
                    Log.debug( "Unable to store, as user is over storage quota. Bouncing message instead: " + message.toXML() );
                    bounce(message);
                }
                break;
            case store_and_drop:
                if (underQuota(message)) {
                    store(message);
                } else {
                    Log.debug( "Unable to store, as user is over storage quota. Silently dropping message: " + message.toXML() );
                }
                break;
            case drop:
                // Drop essentially means silently ignore/do nothing
                break;
            }
        }
    }

    /**
     * Registers a listener to receive events.
     *
     * @param listener the listener.
     */
    public static void addListener(OfflineMessageListener listener) {
        if (listener == null) {
            throw new NullPointerException();
        }
        listeners.add(listener);
    }

    /**
     * Unregisters a listener to receive events.
     *
     * @param listener the listener.
     */
    public static void removeListener(OfflineMessageListener listener) {
        listeners.remove(listener);
    }

    private boolean underQuota(Message message) {
        return quota > messageStore.getSize(message.getTo().getNode()) + message.toXML().length();
    }

    private void store(Message message) {
        final boolean stored = messageStore.addMessage(message);
        // Inform listeners that an offline message was stored
        if (stored && !listeners.isEmpty()) {
            for (OfflineMessageListener listener : listeners) {
                try {
                    listener.messageStored(message);    
                } catch (Exception e) {
                    Log.warn("An exception occurred while dispatching a 'messageStored' event!", e);
                }
            }
        }
    }

    private void bounce(Message message) {
        // Do nothing if the sender was the server itself
        if (message.getFrom() == null || message.getFrom().equals( serverAddress )) {
            return;
        }
        try {
            // Generate a rejection response to the sender
            Message errorResponse = message.createCopy();
            // return an error stanza to the sender, which SHOULD be <service-unavailable/>
            errorResponse.setError(PacketError.Condition.service_unavailable);
            errorResponse.setFrom(message.getTo());
            errorResponse.setTo(message.getFrom());
            // Send the response
            router.route(errorResponse);
            // Inform listeners that an offline message was bounced
            if (!listeners.isEmpty()) {
                for (OfflineMessageListener listener : listeners) {
                    try {
                        listener.messageBounced(message);
                    } catch (Exception e) {
                        Log.warn("An exception occurred while dispatching a 'messageBounced' event!", e);
                    }
                }
            }
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
    }

    @Override
    public void initialize(XMPPServer server) {
        super.initialize(server);
        messageStore = server.getOfflineMessageStore();
        router = server.getPacketRouter();
        serverAddress = new JID(server.getServerInfo().getXMPPDomain());

        JiveGlobals.migrateProperty("xmpp.offline.quota");
        JiveGlobals.migrateProperty("xmpp.offline.type");

        String quota = JiveGlobals.getProperty("xmpp.offline.quota");
        if (quota != null && quota.length() > 0) {
            OfflineMessageStrategy.quota = Integer.parseInt(quota);
        }
        String type = JiveGlobals.getProperty("xmpp.offline.type");
        if (type != null && type.length() > 0) {
            OfflineMessageStrategy.type = Type.valueOf(type);
        }
    }

    @Override
    public Iterator<String> getFeatures() {
        switch (type) {
            case store:
            case store_and_bounce:
            case store_and_drop:
                // http://xmpp.org/extensions/xep-0160.html#disco
                return Collections.singleton("msgoffline").iterator();
        }
        return Collections.emptyIterator();
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
        store_and_drop
    }
}
