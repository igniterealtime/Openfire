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

import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.user.User;
import org.jivesoftware.messenger.user.UserManager;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;
import org.xmpp.component.Component;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;

import java.util.*;

/**
 * Simple in memory implementation of the PresenceManager interface.
 *
 * @author Iain Shigeoka
 */
public class PresenceManagerImpl extends BasicModule implements PresenceManager {

    private static final String lastPresenceProp = "lastUnavailablePresence";

    private SessionManager sessionManager;
    private XMPPServer server;
    private PacketDeliverer deliverer;

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

    public Collection<Presence> getPresences(User user) {
        if (user == null) {
            return null;
        }
        List<Presence> presences = new ArrayList<Presence>();

        for (ClientSession session : sessionManager.getSessions(user.getUsername())) {
            presences.add(session.getPresence());
        }
        return Collections.unmodifiableCollection(presences);
    }

    public void probePresence(JID prober, JID probee) {
        try {
            Component component = getPresenceComponent(probee);
            if (server.isLocal(probee)) {
                // If the probee is a local user then don't send a probe to the contact's server.
                // But instead just send the contact's presence to the prober
                if (probee.getNode() != null && !"".equals(probee.getNode())) {
                    Collection<ClientSession> sessions =
                            sessionManager.getSessions(probee.getNode());
                    if (sessions.isEmpty()) {
                        // If the probee is not online then try to retrieve his last unavailable
                        // presence which may contain particular information and send it to the
                        // prober
                        try {
                            User probeeUser = UserManager.getInstance().getUser(probee.getNode());
                            String presenceXML = probeeUser.getProperties().get(lastPresenceProp);
                            if (presenceXML != null) {
                                try {
                                    // Parse the element
                                    Document element = DocumentHelper.parseText(presenceXML);
                                    // Create the presence from the parsed element
                                    Presence presencePacket = new Presence(element.getRootElement());
                                    presencePacket.setFrom(probee.toBareJID());
                                    presencePacket.setTo(prober);
                                    // Send the presence to the prober
                                    deliverer.deliver(presencePacket);
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
                            Presence presencePacket = session.getPresence().createCopy();
                            presencePacket.setFrom(session.getAddress());
                            presencePacket.setTo(prober);
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
            else if (component != null) {
                // If the probee belongs to a component then ask the component to process the
                // probe presence
                Presence presence = new Presence();
                presence.setType(Presence.Type.probe);
                presence.setFrom(prober);
                presence.setTo(probee);
                component.processPacket(presence);
            }
            else {
                String serverDomain = server.getServerInfo().getName();
                // Check if the probee may be hosted by this server
                if (!probee.getDomain().contains(serverDomain)) {
                    // TODO Implete when s2s is implemented
                }
                else {
                    // The probee may be related to a component that has not yet been connected so
                    // we will keep a registry of this presence probe. The component will answer
                    // this presence probe when he becomes online
                    componentManager.addPresenceRequest(prober, probee);
                }
            }
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
    }

    public void deleteLastUnavailablePresence(String username) {
        if (username == null) {
            return;
        }
        try {
            User probeeUser = UserManager.getInstance().getUser(username);
            probeeUser.getProperties().remove(lastPresenceProp);
        }
        catch (UserNotFoundException e) {
        }
    }

    public void saveLastUnavailablePresence(String username, Presence presence) {
        if (username == null) {
            return;
        }
        try {
            User probeeUser = UserManager.getInstance().getUser(username);
            probeeUser.getProperties().put(lastPresenceProp, presence.toXML());
        }
        catch (UserNotFoundException e) {
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
    }

    public Component getPresenceComponent(JID probee) {
        // Check for registered components
        Component component = componentManager.getComponent(probee.toBareJID());
        if (component != null) {
            return component;
        }
        return null;
    }
}