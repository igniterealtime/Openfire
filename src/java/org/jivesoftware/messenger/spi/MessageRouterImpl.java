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
package org.jivesoftware.messenger.spi;

import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.container.TrackInfo;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.util.Log;

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
        if (packet.getOriginatingSession() == null
                || packet.getOriginatingSession().getStatus() == Session.STATUS_AUTHENTICATED) {
            XMPPAddress recipientJID = packet.getRecipient();

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
            packet.setRecipient(packet.getOriginatingSession().getAddress());
            packet.setSender(null);
            packet.setError(XMPPError.Code.UNAUTHORIZED);
            try {
                packet.getOriginatingSession().process(packet);
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
