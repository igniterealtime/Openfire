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

import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.container.Container;
import org.jivesoftware.messenger.container.TrackInfo;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.PacketError;

/**
 * <p>Implements the strategy as a basic server module.</p>
 *
 * @author Iain Shigeoka
 */
public class OfflineMessageStrategy extends BasicModule {

    private static int quota = -1;
    private static Type type = Type.store;
    private SessionManager sessionManager;


    public PacketFactory packetFactory;
    public XMPPServer xmppServer;
    public PacketDeliverer deliverer;
    public OfflineMessageStore messageStore;

    public OfflineMessageStrategy() {
        super("Offline Message Strategy");

        sessionManager = SessionManager.getInstance();
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

    public void storeOffline(Message message) throws UnauthorizedException, UserNotFoundException {
        if (message != null) {
            Session senderSession = null;
            try {
                senderSession = sessionManager.getSession(message.getFrom());
            }
            catch (SessionNotFoundException e) {
                Log.error(e);
            }
            JID sender = senderSession.getAddress();

            // server messages and anonymous messages can be silently dropped
            if (sender == null || sender.getNode() == null || senderSession == null) {
                // silently drop the server message
            }
            else {
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
    }

    private boolean underQuota(Message message) {
        return quota > messageStore.getSize(message.getTo().getNode()) + message.toXML().length();
    }

    private void store(Message message) {
        messageStore.addMessage(message);
    }

    public void initialize(Container container) {
        super.initialize(container);
        String quota = JiveGlobals.getProperty("xmpp.offline.quota");
        if (quota != null && quota.length() > 0) {
            OfflineMessageStrategy.quota = Integer.parseInt(quota);
        }
        String type = JiveGlobals.getProperty("xmpp.offline.type");
        if (type != null && type.length() > 0) {
            OfflineMessageStrategy.type = Type.valueOf(type);
        }
    }

    private void bounce(Message message) {
        // Generate a rejection response to the sender
        try {
            Message response = packetFactory.getMessage();
            response.setTo(message.getFrom());
            response.setFrom(xmppServer.createJID(null, null));
            response.setBody("Message could not be delivered to " + message.getTo() + ". User is offline or unreachable.");

            Session session = sessionManager.getSession(message.getFrom());
            session.getConnection().deliver(response);

            Message errorResponse = message.createCopy();
            errorResponse.setError(new PacketError(PacketError.Type.continue_processing, PacketError.Condition.item_not_found));
            session.getConnection().deliver(errorResponse);
        }
        catch (Exception e) {
            Log.error(e);
        }
    }


    public TrackInfo getTrackInfo() {
        TrackInfo trackInfo = new TrackInfo();
        trackInfo.getTrackerClasses().put(PacketDeliverer.class, "deliverer");
        trackInfo.getTrackerClasses().put(XMPPServer.class, "xmppServer");
        trackInfo.getTrackerClasses().put(PacketFactory.class, "packetFactory");
        trackInfo.getTrackerClasses().put(OfflineMessageStore.class, "messageStore");
        return trackInfo;
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