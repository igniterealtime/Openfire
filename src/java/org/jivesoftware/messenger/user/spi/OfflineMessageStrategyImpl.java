/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.user.spi;

import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.container.Container;
import org.jivesoftware.messenger.container.ModuleContext;
import org.jivesoftware.messenger.container.TrackInfo;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.UserNotFoundException;

/**
 * <p>Implements the strategy as a basic server module.</p>
 *
 * @author Iain Shigeoka
 */
public class OfflineMessageStrategyImpl extends BasicModule implements OfflineMessageStrategy {

    public OfflineMessageStrategyImpl() {
        super("Offline Message Strategy");
    }

    private static int quota = -1;
    private static Type type = Type.store;
    private ModuleContext context;

    public int getQuota() {
        return quota;
    }

    public void setQuota(int quota) {
        OfflineMessageStrategyImpl.quota = quota;
        context.setProperty("xmpp.offline.quota", Integer.toString(quota));
    }

    public OfflineMessageStrategy.Type getType() {
        return type;
    }

    public void setType(OfflineMessageStrategy.Type type) {
        if (type == null) {
            throw new IllegalArgumentException();
        }
        OfflineMessageStrategyImpl.type = type;
        context.setProperty("xmpp.offline.type", type.toString());
    }

    public void storeOffline(Message message) throws UnauthorizedException, UserNotFoundException {
        if (message != null) {
            Session senderSession = message.getOriginatingSession();
            XMPPAddress sender = senderSession.getAddress();
            // server messages and anonymous messages can be silently dropped
            if (sender == null || sender.getName() == null) {
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

    private boolean underQuota(Message message) throws UnauthorizedException, UserNotFoundException {
        return quota > messageStore.getSize(message.getRecipient().getNamePrep()) + message.getSize();
    }

    private void store(Message message) throws UnauthorizedException {
        messageStore.addMessage(message);
    }

    public void initialize(ModuleContext context, Container container) {
        super.initialize(context, container);
        this.context = context;
        String quota = context.getProperty("xmpp.offline.quota");
        if (quota != null && quota.length() > 0) {
            OfflineMessageStrategyImpl.quota = Integer.parseInt(quota);
        }
        String type = context.getProperty("xmpp.offline.type");
        if (type != null && type.length() > 0) {
            OfflineMessageStrategyImpl.type = Type.valueOf(type);
        }
    }

    private void bounce(Message message) {
        // Generate a rejection response to the sender
        try {
            Message response = packetFactory.getMessage();
            response.setOriginatingSession(xmppServer.getSession());
            response.setRecipient(message.getSender());
            response.setSender(xmppServer.createAddress(null, null));
            response.setBody("Message could not be delivered to " + message.getRecipient() + ". User is offline or unreachable.");
            message.getOriginatingSession().getConnection().deliver(response);

            Message errorResponse = (Message)message.createDeepCopy();
            errorResponse.setError(XMPPError.Code.NOT_FOUND);
            message.getOriginatingSession().getConnection().deliver(errorResponse);
        }
        catch (Exception e) {
            //
        }
    }

    public PacketFactory packetFactory;
    public XMPPServer xmppServer;
    public PacketDeliverer deliverer;
    public OfflineMessageStore messageStore;

    public TrackInfo getTrackInfo() {
        TrackInfo trackInfo = new TrackInfo();
        trackInfo.getTrackerClasses().put(PacketDeliverer.class, "deliverer");
        trackInfo.getTrackerClasses().put(XMPPServer.class, "xmppServer");
        trackInfo.getTrackerClasses().put(PacketFactory.class, "packetFactory");
        trackInfo.getTrackerClasses().put(OfflineMessageStore.class, "messageStore");
        return trackInfo;
    }
}