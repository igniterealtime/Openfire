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
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.handler.PresenceSubscribeHandler;
import org.jivesoftware.messenger.handler.PresenceUpdateHandler;

/**
 * Generic presence routing base class.
 *
 * @author Iain Shigeoka
 */
public class PresenceRouterImpl extends BasicModule implements PresenceRouter {

    public XMPPServer localServer;
    public RoutingTable routingTable;
    public PresenceUpdateHandler updateHandler;
    public PresenceSubscribeHandler subscribeHandler;

    /**
     * Create a packet router.
     */
    public PresenceRouterImpl() {
        super("XMPP Presence Router");
    }

    public void route(Presence packet) {
        if (packet == null) {
            throw new NullPointerException();
        }
        if (packet.getOriginatingSession() == null
                || packet.getOriginatingSession().getStatus() == Session.STATUS_AUTHENTICATED) {
            handle(packet);
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

    private void handle(Presence packet) {
        XMPPAddress recipientJID = packet.getRecipient();
        try {
            XMPPPacket.Type type = packet.getType();
            // Presence updates (null is 'available')
            if (type == null
                    || Presence.UNAVAILABLE == type
                    || Presence.INVISIBLE == type
                    || Presence.AVAILABLE == type) {


                // ridiculously long check for local server target
                if (recipientJID == null
                        || recipientJID.getHost() == null
                        || "".equals(recipientJID.getHost())
                        || (recipientJID.getName() == null && recipientJID.getResource() == null)) {

                    updateHandler.process(packet);
                }
                else {
                    // The user sent a directed presence to an entity
                    ChannelHandler handler = routingTable.getRoute(recipientJID);
                    handler.process(packet);
                    // Notify the PresenceUpdateHandler of the directed presence
                    updateHandler.directedPresenceSent(packet, handler);
                }

            }
            else if (Presence.SUBSCRIBE == type // presence subscriptions
                    || Presence.UNSUBSCRIBE == type
                    || Presence.SUBSCRIBED == type
                    || Presence.UNSUBSCRIBED == type) {

                subscribeHandler.process(packet);
            }
            else {
                // It's an unknown or ERROR type, just deliver it because there's nothing else to do with it
                routingTable.getRoute(recipientJID).process(packet);
            }

        }
        catch (NoSuchRouteException e) {
            // Do nothing, presence to unreachable routes are dropped
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error.routing"), e);
            try {
                Session session = packet.getOriginatingSession();
                if (session != null) {
                    Connection conn = session.getConnection();
                    if (conn != null) {
                        conn.close();
                    }
                }
            }
            catch (UnauthorizedException e1) {
                // do nothing
            }
        }
    }

    protected TrackInfo getTrackInfo() {
        TrackInfo trackInfo = new TrackInfo();
        trackInfo.getTrackerClasses().put(XMPPServer.class, "localServer");
        trackInfo.getTrackerClasses().put(RoutingTable.class, "routingTable");
        trackInfo.getTrackerClasses().put(PresenceUpdateHandler.class, "updateHandler");
        trackInfo.getTrackerClasses().put(PresenceSubscribeHandler.class, "subscribeHandler");
        return trackInfo;
    }

}
