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

package org.jivesoftware.messenger.spi;

import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.container.TrackInfo;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.util.Log;
import org.xmpp.packet.Message;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;

/**
 * Generic message routing base class.
 *
 * @author Iain Shigeoka
 */
public class MessageRouterImpl extends BasicModule implements MessageRouter {

    public OfflineMessageStrategy messageStrategy;
    public RoutingTable routingTable;

    /**
     * <p>Create a packet router.</p>
     */
    public MessageRouterImpl() {
        super("XMPP Message Router");
    }

    public void route(Message packet) {
        if (packet == null) {
            throw new NullPointerException();
        }
        Session session = SessionManager.getInstance().getSession(packet.getFrom());
        if (session == null
                || session.getStatus() == Session.STATUS_AUTHENTICATED)
        {
            JID recipientJID = packet.getTo();

            try {
                routingTable.getBestRoute(recipientJID).process(packet);
            }
            catch (Exception e) {
                try {
                    messageStrategy.storeOffline(packet);
                }
                catch (Exception e1) {
                    // user could not be reached. Don't bounce or they can probe for valid addresses by just sending
                    // messages and seeing what bounces.
                }
            }

        }
        else {
            packet.setTo(session.getAddress());
            packet.setFrom((JID)null);
            packet.setError(PacketError.Condition.not_authorized);
            try {
                session.process(packet);
            }
            catch (UnauthorizedException ue) {
                Log.error(ue);
            }
        }
    }

    protected TrackInfo getTrackInfo() {
        TrackInfo trackInfo = new TrackInfo();
        trackInfo.getTrackerClasses().put(OfflineMessageStrategy.class, "messageStrategy");
        trackInfo.getTrackerClasses().put(RoutingTable.class, "routingTable");
        return trackInfo;
    }

}
