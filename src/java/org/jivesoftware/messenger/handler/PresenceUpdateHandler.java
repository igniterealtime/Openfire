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

package org.jivesoftware.messenger.handler;

import org.jivesoftware.messenger.container.TrackInfo;
import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.spi.SessionImpl;
import org.jivesoftware.messenger.user.CachedRoster;
import org.jivesoftware.messenger.user.RosterItem;
import org.jivesoftware.messenger.user.RosterManager;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.xmpp.packet.*;
import org.xmlpull.v1.XmlPullParserException;

import java.lang.ref.WeakReference;
import java.util.*;

/**
 * Implements the presence protocol. Clients use this protocol to
 * update presence and roster information.
 * <p/>
 * The handler must properly detect the presence type, update the user's roster,
 * and inform presence subscribers of the session's updated presence
 * status. Presence serves many purposes in Jabber so this handler will
 * likely be the most complex of all handlers in the server.
 * <p/>
 * There are four basic types of presence updates:
 * <ul>
 * <li>Simple presence updates - addressed to the server (or to address), these updates
 * are properly addressed by the server, and multicast to
 * interested subscribers on the user's roster. An empty, missing,
 * or "unavailable" type attribute indicates a simple update (there
 * is no "available" type although it should be accepted by the server.
 * <li>Directed presence updates - addressed to particular jabber entities,
 * these presence updates are properly addressed and directly delivered
 * to the entity without broadcast to roster subscribers. Any update type
 * is possible except those reserved for subscription requests.
 * <li>Subscription requests - these updates request presence subscription
 * status changes. Such requests always affect the roster.  The server must:
 * <ul>
 * <li>update the roster with the proper subscriber info
 * <li>push the roster changes to the user
 * <li>forward the update to the correct parties.
 * </ul>
 * The valid types include "subscribe", "subscribed", "unsubscribed",
 * and "unsubscribe".
 * <li>BasicServer probes - Provides a mechanism for servers to query the presence
 * status of users on another server. This allows users to immediately
 * know the presence status of users when they come online rather than way
 * for a presence update broadcast from the other server or tracking them
 * as they are received.  Requires S2S capabilities.
 * </ul>
 * <p/>
 * <h2>Warning</h2>
 * There should be a way of determining whether a session has
 * authorization to access this feature. I'm not sure it is a good
 * idea to do authorization in each handler. It would be nice if
 * the framework could assert authorization policies across channels.
 *
 * @author Iain Shigeoka
 *
 * todo Support probe packets (only needed with s2s)
 */
public class PresenceUpdateHandler extends BasicModule implements ChannelHandler {

    private Map<String, Set> directedPresences = new HashMap<String, Set>();

    public PresenceUpdateHandler() {
        super("Presence update handler");
    }

    public void process(Packet xmppPacket) throws UnauthorizedException, PacketException {
        Presence presence = (Presence)xmppPacket;
        try {
            Session session = SessionManager.getInstance().getSession(presence.getFrom());
            Presence.Type type = presence.getType();
            // Available
            if (type == null) {
                broadcastUpdate(presence.createCopy());
                if (session != null) {
                    session.setPresence(presence);
                    if (!session.isInitialized()) {
                        initSession(session);
                        session.setInitialized(true);
                    }
                }
            }
            else if (Presence.Type.unavailable == type) {
                broadcastUpdate(presence.createCopy());
                broadcastUnavailableForDirectedPresences(presence.createCopy());
                if (session != null) {
                    session.setPresence(presence);
                }
            }
            else {
                presence = presence.createCopy();
                if (session != null) {
                    presence.setFrom(new JID(null, session.getServerName(), null));
                    presence.setTo(session.getAddress());
                }
                else {
                    JID sender = presence.getFrom();
                    presence.setFrom(presence.getTo());
                    presence.setTo(sender);
                }
                presence.setError(PacketError.Condition.bad_request);
                deliverer.deliver(presence);
            }

        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
    }

    /**
     * Handle presence updates that affect roster subscriptions.
     *
     * @param presence The presence presence to handle
     */
    public synchronized void process(Presence presence) throws PacketException {
        try {
            process((Packet)presence);
        }
        catch (UnauthorizedException e) {
            try {
                Session session = SessionManager.getInstance().getSession(presence.getFrom());
                presence = presence.createCopy();
                if (session != null) {
                    presence.setFrom(new JID(null, session.getServerName(), null));
                    presence.setTo(session.getAddress());
                }
                else {
                    JID sender = presence.getFrom();
                    presence.setFrom(presence.getTo());
                    presence.setTo(sender);
                }
                presence.setError(PacketError.Condition.not_authorized);
                deliverer.deliver(presence);
            }
            catch (Exception err) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), err);
            }
        }
    }

    /**
     * A session that has transitioned to available status must be initialized.
     * This includes:
     * <ul>
     * <li>Sending all offline presence subscription requests</li>
     * <li>Sending offline messages</li>
     * </ul>
     *
     * @param session The session being updated
     * @throws UnauthorizedException If the caller doesn't have the right permissions
     * @throws UserNotFoundException If the user being updated does not exist
     */
    private void initSession(Session session)  throws UnauthorizedException, UserNotFoundException, XmlPullParserException {

        // Only user sessions need to be authenticated
        if (!"".equals(session.getAddress().getNode())) {
            String username = session.getAddress().getNode();
            CachedRoster roster = rosterManager.getRoster(username);
            Iterator items = roster.getRosterItems();
            while (items.hasNext()) {
                RosterItem item = (RosterItem)items.next();
                if (item.getRecvStatus() == RosterItem.RECV_SUBSCRIBE) {
                    session.getConnection().deliver(createSubscribePresence(item.getJid(), true));
                }
                else if (item.getRecvStatus() == RosterItem.RECV_UNSUBSCRIBE) {
                    session.getConnection().deliver(createSubscribePresence(item.getJid(), false));
                }
                if (item.getSubStatus() == RosterItem.SUB_TO
                        || item.getSubStatus() == RosterItem.SUB_BOTH) {
                    presenceManager.probePresence(username, item.getJid());
                }
            }
            // deliver offline messages if any
            Collection<Message> messages = messageStore.getMessages(username);
            for (Message message : messages) {
                session.getConnection().deliver(message);
            }
        }
    }

    public Presence createSubscribePresence(JID senderAddress, boolean isSubscribe) {
        Presence presence = new Presence();
        presence.setFrom(senderAddress);
        if (isSubscribe) {
            presence.setType(Presence.Type.subscribe);
        }
        else {
            presence.setType(Presence.Type.unsubscribe);
        }
        return presence;
    }

    /**
     * Broadcast the given update to all subscribers. We need to:
     * <ul>
     * <li>Query the roster table for subscribers</li>
     * <li>Iterate through the list and send the update to each subscriber</li>
     * </ul>
     * <p/>
     * Is there a safe way to cache the query results while maintaining
     * integrity with roster changes?
     *
     * @param update The update to broadcast
     */
    private void broadcastUpdate(Presence update) throws PacketException {
        if (update.getFrom() == null) {
            return;
        }
        if (localServer.isLocal(update.getFrom())) {
            // Local updates can simply run through the roster of the local user
            String name = update.getFrom().getNode();
            try {
                if (name != null && !"".equals(name)) {
                    name = name.toLowerCase();
                    CachedRoster roster = rosterManager.getRoster(name);
                    roster.broadcastPresence(update);
                }
            }
            catch (UserNotFoundException e) {
                Log.warn("Presence being sent from unknown user " + name, e);
            }
            catch (PacketException e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
        else {
            // Foreign updates will do a reverse lookup of entries in rosters
            // on the server
            Log.warn("Presence requested from server "
                    + localServer.getServerInfo().getName()
                    + " by unknown user: " + update.getFrom());
            /*
            Connection con = null;
            PreparedStatement pstmt = null;
            try {

                pstmt = con.prepareStatement(GET_ROSTER_SUBS);
                pstmt.setString(1, update.getSender().toBareString().toLowerCase());
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()){
                    long userID = rs.getLong(1);
                    try {
                        User user = server.getUserManager().getUser(userID);

                        update.setRecipient(user.getAddress());
                        server.getSessionManager().userBroadcast(user.getUsername(),
                                                                update.getPacket());
                    } catch (UserNotFoundException e) {
                        Log.error(LocaleUtils.getLocalizedString("admin.error"),e);
                    } catch (UnauthorizedException e) {
                        Log.error(LocaleUtils.getLocalizedString("admin.error"),e);
                    }
                }
            }
            catch (SQLException e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"),e);
            }
            */
        }
    }

    /**
     * Notification method sent to this handler when a user has sent a directed presence to an entity.
     * If the sender of the presence is local (to this server) and the target entity does not belong
     * to the user's roster then update the registry of sent directed presences by the user.
     *
     * @param update  the directed Presence sent by the user to an entity.
     * @param handler the handler that routed the presence to the entity.
     */
    public synchronized void directedPresenceSent(Presence update, ChannelHandler handler) {
        if (update.getFrom() == null) {
            return;
        }
        if (localServer.isLocal(update.getFrom())) {
            String name = update.getFrom().getNode();
            try {
                if (name != null && !"".equals(name)) {
                    name = name.toLowerCase();
                    CachedRoster roster = rosterManager.getRoster(name);
                    // If the directed presence was sent to an entity that is not in the user's
                    // roster, keep a registry of this so that when the user goes offline we will
                    // be able to send the unavialable presence to the entity
                    if (!roster.isRosterItem(update.getTo())) {
                        Set set = (Set)directedPresences.get(update.getFrom().toString());
                        if (set == null) {
                            // We are using a set to avoid duplicate handlers in case the user
                            // sends several directed presences to the same entity
                            set = new HashSet();
                            directedPresences.put(update.getFrom().toString(), set);
                        }
                        if (Presence.Type.unavailable.equals(update.getType())) {
                            // It's a directed unavailable presence so remove the target entity
                            // from the registry
                            if (handler instanceof SessionImpl) {
                                set.remove(new HandlerWeakReference(handler));
                                if (set.isEmpty()) {
                                    // Remove the user from the registry since the list of directed
                                    // presences is empty
                                    directedPresences.remove(update.getFrom().toString());
                                }
                            }
                        }
                        else {
                            // Add the handler to the list of handler that processed the directed
                            // presence sent by the user. This handler will be used to send
                            // the unavailable presence when the user goes offline
                            set.add(new HandlerWeakReference(handler));
                        }
                    }
                }
            }
            catch (UserNotFoundException e) {
                Log.warn("Presence being sent from unknown user " + name, e);
            }
            catch (PacketException e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
    }

    /**
     * Sends an unavailable presence to the entities that received a directed (available) presence
     * by the user that is now going offline.
     *
     * @param update the unavailable presence sent by the user.
     */
    private void broadcastUnavailableForDirectedPresences(Presence update) {
        if (update.getFrom() == null) {
            return;
        }
        if (localServer.isLocal(update.getFrom())) {
            Set set = (Set)directedPresences.get(update.getFrom().toString());
            if (set != null) {
                RoutableChannelHandler handler;
                // Iterate over all the entities that the user sent a directed presence
                for (Iterator it = set.iterator(); it.hasNext();) {
                    // It is assumed that any type of PacketHandler (besides SessionImpl) will be
                    // responsible for sending/processing the offline presence to ALL the entities
                    // were the user has sent a directed presence. This is a consequence of using
                    // a set in order to prevent duplicte handlers.
                    // e.g. MultiUserChatServerImpl will remove the user from ALL the rooms
                    handler = (RoutableChannelHandler)((HandlerWeakReference)it.next()).get();
                    if (handler != null) {
                        update.setTo(handler.getAddress());
                        try {
                            handler.process(update);
                        }
                        catch (UnauthorizedException ue) {
                            Log.error(ue);
                        }
                    }
                }
                // Remove the registry of directed presences of this user
                directedPresences.remove(update.getFrom().toString());
            }
        }
    }


    public RosterManager rosterManager;
    public XMPPServer localServer;
    private SessionManager sessionManager = SessionManager.getInstance();
    public PresenceManager presenceManager;
    public PacketDeliverer deliverer;
    public OfflineMessageStore messageStore;

    protected TrackInfo getTrackInfo() {
        TrackInfo trackInfo = new TrackInfo();
        trackInfo.getTrackerClasses().put(RosterManager.class, "rosterManager");
        trackInfo.getTrackerClasses().put(XMPPServer.class, "localServer");
        trackInfo.getTrackerClasses().put(PresenceManager.class, "presenceManager");
        trackInfo.getTrackerClasses().put(PacketDeliverer.class, "deliverer");
        trackInfo.getTrackerClasses().put(OfflineMessageStore.class, "messageStore");
        return trackInfo;
    }

    /**
     * A WeakReference that redefines #equals(Object) so that the referent objects could be compared
     * as if the weak reference does not exists.
     *
     * @author Gaston Dombiak
     */
    private class HandlerWeakReference extends WeakReference {
        //We need to store the hash code separately since the referent
        //could be removed by the GC.
        private int hash;

        public HandlerWeakReference(Object referent) {
            super(referent);
            hash = referent.hashCode();
        }

        public int hashCode() {
            return hash;
        }

        public boolean equals(Object object) {
            if (this == object) return true;
            if (object instanceof HandlerWeakReference) {
                Object t = this.get();
                Object u = ((HandlerWeakReference)object).get();
                if ((t == null) || (u == null)) return false;
                if (t == u) return true;
                return t.equals(u);
            }
            else {
                Object t = this.get();
                if (t == null || (object == null)) return false;
                if (t == object) return true;
                return t.equals(object);

            }
        }
    }
}
