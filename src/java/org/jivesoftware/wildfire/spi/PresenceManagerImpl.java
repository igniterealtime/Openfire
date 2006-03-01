/**
 * $RCSfile: PresenceManagerImpl.java,v $
 * $Revision: 3128 $
 * $Date: 2005-11-30 15:31:54 -0300 (Wed, 30 Nov 2005) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.spi;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.jivesoftware.util.CacheManager;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.*;
import org.jivesoftware.wildfire.auth.UnauthorizedException;
import org.jivesoftware.wildfire.component.InternalComponentManager;
import org.jivesoftware.wildfire.container.BasicModule;
import org.jivesoftware.wildfire.handler.PresenceUpdateHandler;
import org.jivesoftware.wildfire.privacy.PrivacyList;
import org.jivesoftware.wildfire.privacy.PrivacyListManager;
import org.jivesoftware.wildfire.roster.Roster;
import org.jivesoftware.wildfire.roster.RosterItem;
import org.jivesoftware.wildfire.user.User;
import org.jivesoftware.wildfire.user.UserManager;
import org.jivesoftware.wildfire.user.UserNotFoundException;
import org.xmpp.component.Component;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.Presence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Simple in memory implementation of the PresenceManager interface.
 *
 * @author Iain Shigeoka
 */
public class PresenceManagerImpl extends BasicModule implements PresenceManager {

    private static final String LAST_PRESENCE_PROP = "lastUnavailablePresence";
    private static final String LAST_ACTIVITY_PROP = "lastActivity";

    private SessionManager sessionManager;
    private UserManager userManager;
    private XMPPServer server;
    private PacketDeliverer deliverer;
    private PresenceUpdateHandler presenceUpdateHandler;

    private InternalComponentManager componentManager;

    public PresenceManagerImpl() {
        super("Presence manager");

        // Use component manager for Presence Updates.
        componentManager = InternalComponentManager.getInstance();
    }

    public boolean isAvailable(User user) {
        return sessionManager.getSessionCount(user.getUsername()) > 0;
    }

    public Presence getPresence(User user) {
        if (user == null) {
            return null;
        }
        Presence presence = null;

        for (ClientSession session : sessionManager.getSessions(user.getUsername())) {
            if (presence == null) {
                presence = session.getPresence();
            }
            else {
                // Get the ordinals of the presences to compare. If no ordinal is available then
                // assume a value of -1
                int o1 = presence.getShow() != null ? presence.getShow().ordinal() : -1;
                int o2 = session.getPresence().getShow() != null ?
                        session.getPresence().getShow().ordinal() : -1;
                // Compare the presences' show ordinals
                if (o1 > o2) {
                    presence = session.getPresence();
                }
            }
        }
        return presence;
    }

    public Collection<Presence> getPresences(String username) {
        if (username == null) {
            return null;
        }
        List<Presence> presences = new ArrayList<Presence>();

        for (ClientSession session : sessionManager.getSessions(username)) {
            presences.add(session.getPresence());
        }
        return Collections.unmodifiableCollection(presences);
    }

    public String getLastPresenceStatus(User user) {
        String answer = null;
        String presenceXML = user.getProperties().get(LAST_PRESENCE_PROP);
        if (presenceXML != null) {
            try {
                // Parse the element
                Document element = DocumentHelper.parseText(presenceXML);
                answer = element.getRootElement().elementTextTrim("status");
            }
            catch (DocumentException e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
        return answer;
    }

    public long getLastActivity(User user) {
        long answer = -1;
        String offline = user.getProperties().get(LAST_ACTIVITY_PROP);
        if (offline != null) {
            try {
                answer = (System.currentTimeMillis() - Long.parseLong(offline)) / 1000;
            }
            catch (NumberFormatException e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
        return answer;
    }

    public void userAvailable(Presence presence) {
        // Delete the last unavailable presence of this user since the user is now
        // available. Only perform this operation if this is an available presence sent to
        // THE SERVER and the presence belongs to a local user.
        if (presence.getTo() == null && server.isLocal(presence.getFrom())) {
            String username = presence.getFrom().getNode();
            if (username == null || !userManager.isRegisteredUser(username)) {
                // Ignore anonymous users
                return;
            }
            try {
                User probeeUser = userManager.getUser(username);
                probeeUser.getProperties().remove(LAST_PRESENCE_PROP);
            }
            catch (UserNotFoundException e) {
            }
        }
    }

    public void userUnavailable(Presence presence) {
        // Only save the last presence status and keep track of the time when the user went
        // offline if this is an unavailable presence sent to THE SERVER and the presence belongs
        // to a local user.
        if (presence.getTo() == null && server.isLocal(presence.getFrom())) {
            String username = presence.getFrom().getNode();
            if (username == null || !userManager.isRegisteredUser(username)) {
                // Ignore anonymous users
                return;
            }
            try {
                User probeeUser = userManager.getUser(username);
                if (!presence.getElement().elements().isEmpty()) {
                    // Save the last unavailable presence of this user if the presence contains any
                    // child element such as <status>
                    probeeUser.getProperties().put(LAST_PRESENCE_PROP, presence.toXML());
                }
                // Keep track of the time when the user went offline
                probeeUser.getProperties().put(LAST_ACTIVITY_PROP,
                        String.valueOf(System.currentTimeMillis()));
            }
            catch (UserNotFoundException e) {
            }
        }
    }

    public void handleProbe(Presence packet) throws UnauthorizedException {
        String username = packet.getTo().getNode();
        // Check for a cached roster:
        Roster roster = (Roster)CacheManager.getCache("username2roster").get(username);
        if (roster == null) {
            synchronized(username.intern()) {
                roster = (Roster)CacheManager.getCache("username2roster").get(username);
                if (roster == null) {
                    // Not in cache so load a new one:
                    roster = new Roster(username);
                    CacheManager.getCache("username2roster").put(username, roster);
                }
            }
        }
        try {
            RosterItem item = roster.getRosterItem(packet.getFrom());
            if (item.getSubStatus() == RosterItem.SUB_FROM
                    || item.getSubStatus() == RosterItem.SUB_BOTH) {
                probePresence(packet.getFrom(),  packet.getTo());
            }
            else {
                PacketError.Condition error = PacketError.Condition.not_authorized;
                if ((item.getSubStatus() == RosterItem.SUB_NONE &&
                        item.getRecvStatus() != RosterItem.RECV_SUBSCRIBE) ||
                        (item.getSubStatus() == RosterItem.SUB_TO &&
                        item.getRecvStatus() != RosterItem.RECV_SUBSCRIBE)) {
                    error = PacketError.Condition.forbidden;
                }
                Presence presenceToSend = new Presence();
                presenceToSend.setError(error);
                presenceToSend.setTo(packet.getFrom());
                presenceToSend.setFrom(packet.getTo());
                deliverer.deliver(presenceToSend);
            }
        }
        catch (UserNotFoundException e) {
            Presence presenceToSend = new Presence();
            presenceToSend.setError(PacketError.Condition.forbidden);
            presenceToSend.setTo(packet.getFrom());
            presenceToSend.setFrom(packet.getTo());
            deliverer.deliver(presenceToSend);
        }
    }

    public boolean canProbePresence(JID prober, String probee) throws UserNotFoundException {
        // Check that the probee is a valid user
        userManager.getUser(probee);
        // Check for a cached roster:
        Roster roster = (Roster)CacheManager.getCache("username2roster").get(probee);
        if (roster == null) {
            synchronized(probee.intern()) {
                roster = (Roster)CacheManager.getCache("username2roster").get(probee);
                if (roster == null) {
                    // Not in cache so load a new one:
                    roster = new Roster(probee);
                    CacheManager.getCache("username2roster").put(probee, roster);
                }
            }
        }
        RosterItem item = roster.getRosterItem(prober);
        if (item.getSubStatus() == RosterItem.SUB_FROM
                || item.getSubStatus() == RosterItem.SUB_BOTH) {
            return true;
        }
        return false;
    }

    public void probePresence(JID prober, JID probee) {
        try {
            if (server.isLocal(probee)) {
                // If the probee is a local user then don't send a probe to the contact's server.
                // But instead just send the contact's presence to the prober
                if (userManager.isRegisteredUser(probee.getNode())) {
                    Collection<ClientSession> sessions =
                            sessionManager.getSessions(probee.getNode());
                    if (sessions.isEmpty()) {
                        // If the probee is not online then try to retrieve his last unavailable
                        // presence which may contain particular information and send it to the
                        // prober
                        try {
                            User probeeUser = userManager.getUser(probee.getNode());
                            String presenceXML = probeeUser.getProperties().get(LAST_PRESENCE_PROP);
                            if (presenceXML != null) {
                                try {
                                    // Parse the element
                                    Document element = DocumentHelper.parseText(presenceXML);
                                    // Create the presence from the parsed element
                                    Presence presencePacket = new Presence(element.getRootElement());
                                    presencePacket.setFrom(probee.toBareJID());
                                    presencePacket.setTo(prober);
                                    // Check if default privacy list of the probee blocks the
                                    // outgoing presence
                                    PrivacyList list = PrivacyListManager.getInstance()
                                            .getDefaultPrivacyList(probee.getNode());
                                    if (list == null || !list.shouldBlockPacket(presencePacket)) {
                                        // Send the presence to the prober
                                        deliverer.deliver(presencePacket);
                                    }
                                }
                                catch (Exception e) {
                                    Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                                }

                            }
                        }
                        catch (UserNotFoundException e) {
                        }
                    }
                    else {
                        // The contact is online so send to the prober all the resources where the
                        // probee is connected
                        for (ClientSession session : sessions) {
                            // Create presence to send from probee to prober
                            Presence presencePacket = session.getPresence().createCopy();
                            presencePacket.setFrom(session.getAddress());
                            presencePacket.setTo(prober);
                            // Check if a privacy list of the probee blocks the outgoing presence
                            PrivacyList list = session.getActiveList();
                            list = list == null ? session.getDefaultList() : list;
                            if (list != null) {
                                if (list.shouldBlockPacket(presencePacket)) {
                                    // Default list blocked outgoing presence so skip this session
                                    continue;
                                }
                            }
                            try {
                                deliverer.deliver(presencePacket);
                            }
                            catch (Exception e) {
                                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                            }
                        }
                    }
                }
            }
            else {
                Component component = getPresenceComponent(probee);
                if (component != null) {
                    // If the probee belongs to a component then ask the component to process the
                    // probe presence
                    Presence presence = new Presence();
                    presence.setType(Presence.Type.probe);
                    presence.setFrom(prober);
                    presence.setTo(probee);
                    component.processPacket(presence);
                }
                else {
                    // Check if the probee may be hosted by this server
                    /*String serverDomain = server.getServerInfo().getName();
                    if (!probee.getDomain().contains(serverDomain)) {*/
                    if (server.isRemote(probee)) {
                        // Send the probe presence to the remote server
                        Presence probePresence = new Presence();
                        probePresence.setType(Presence.Type.probe);
                        probePresence.setFrom(prober);
                        probePresence.setTo(probee.toBareJID());
                        // Send the probe presence
                        deliverer.deliver(probePresence);
                    }
                    else {
                        // The probee may be related to a component that has not yet been connected so
                        // we will keep a registry of this presence probe. The component will answer
                        // this presence probe when he becomes online
                        componentManager.addPresenceRequest(prober, probee);
                    }
                }
            }
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
    }

    public void sendUnavailableFromSessions(JID recipientJID, JID userJID) {
        if (userManager.isRegisteredUser(userJID.getNode())) {
            for (ClientSession session : sessionManager.getSessions(userJID.getNode())) {
                // Do not send an unavailable presence if the user sent a direct available presence
                if (presenceUpdateHandler.hasDirectPresence(session, recipientJID)) {
                    continue;
                }
                Presence presencePacket = new Presence();
                presencePacket.setType(Presence.Type.unavailable);
                presencePacket.setFrom(session.getAddress());
                presencePacket.setTo(recipientJID);
                try {
                    deliverer.deliver(presencePacket);
                }
                catch (Exception e) {
                    Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                }
            }
        }
    }

    // #####################################################################
    // Module management
    // #####################################################################

    public void initialize(XMPPServer server) {
        super.initialize(server);
        this.server = server;
        deliverer = server.getPacketDeliverer();
        sessionManager = server.getSessionManager();
        userManager = server.getUserManager();
        presenceUpdateHandler = server.getPresenceUpdateHandler();
    }

    public Component getPresenceComponent(JID probee) {
        // Check for registered components
        Component component = componentManager.getComponent(probee);
        if (component != null) {
            return component;
        }
        return null;
    }
}