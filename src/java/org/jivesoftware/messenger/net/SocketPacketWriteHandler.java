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
package org.jivesoftware.messenger.net;

import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;

/**
 * This ChannelHandler writes packet data to connections.
 *
 * @author Iain Shigeoka
 * @see PacketRouter
 */
public class SocketPacketWriteHandler implements ChannelHandler {

    private SessionManager sessionManager;
    private OfflineMessageStrategy messageStrategy;

    public SocketPacketWriteHandler(SessionManager sessionManager, OfflineMessageStrategy messageStrategy) {
        this.sessionManager = sessionManager;
        this.messageStrategy = messageStrategy;
    }

     public void process(XMPPPacket packet) throws UnauthorizedException, PacketException {
        try {
            XMPPAddress recipient = packet.getRecipient();
            Session senderSession = packet.getOriginatingSession();
            if (recipient == null || (recipient.getName() == null && recipient.getResource() == null)) {
                if (senderSession != null && !senderSession.getConnection().isClosed()) {
                    senderSession.getConnection().deliver(packet);
                }
                else {
                    dropPacket(packet);
                }
            }
            else {
                Session session = sessionManager.getBestRoute(recipient);
                if (session == null) {
                    if (packet instanceof Message) {
                        messageStrategy.storeOffline((Message)packet);
                    }
                    else if (packet instanceof Presence) {
                        // presence packets are dropped silently
                        //dropPacket(packet);
                    }
                    else {
                        // IQ packets are logged but dropped
                        dropPacket(packet);
                    }
                }
                else {
                    try {
                        session.getConnection().deliver(packet);
                    }
                    catch (Exception e) {
                        // do nothing
                    }
                }
            }
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error.deliver") + "\n" + packet.toString(), e);
        }
    }

    /**
     * Drop the packet.
     *
     * @param packet The packet being dropped
     */
    private void dropPacket(XMPPPacket packet) {
        Log.warn(LocaleUtils.getLocalizedString("admin.error.routing") + "\n" + packet.toString());
    }
}
