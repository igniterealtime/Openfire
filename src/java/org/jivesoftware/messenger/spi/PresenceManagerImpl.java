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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in memory implementation of the PresenceManager interface.
 *
 * @author Iain Shigeoka
 */
public class PresenceManagerImpl extends BasicModule implements PresenceManager {

    private Map<String, Presence> onlineGuests;
    private Map<String, Presence> onlineUsers;

    private UserManager userManager;
    private SessionManager sessionManager;
    private XMPPServer server;
    private PacketDeliverer deliverer;

    private InternalComponentManager componentManager;

    public PresenceManagerImpl() {
        super("Presence manager");

        // Use component manager for Presence Updates.
        componentManager = InternalComponentManager.getInstance();
    }

    private void initializeCaches() {
        // create caches - no size limit and never expire for presence caches
        onlineGuests = new ConcurrentHashMap<String, Presence>();
        onlineUsers = new ConcurrentHashMap<String, Presence>();
    }

    public int getOnlineGuestCount() {
        int count = 0;
        for (Presence presence : onlineGuests.values()) {
            if (presence.isAvailable()) {
                count++;
            }
        }
        return count;
    }

    public Collection<User> getOnlineUsers() {
        List<User> users = new ArrayList<User>();
        for (Presence presence : onlineUsers.values()) {
            if (presence.isAvailable()) {
                try {
                    users.add(userManager.getUser(presence.getFrom().getNode()));
                }
                catch (UserNotFoundException e) {
                    Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                }
            }
        }
        return Collections.unmodifiableCollection(users);
    }

    public Collection<User> getOnlineUsers(boolean ascending, int sortField) {
        return getOnlineUsers(ascending, sortField, Integer.MAX_VALUE);
    }

    public Collection<User> getOnlineUsers(final boolean ascending, int sortField, int numResults) {
        Iterator iter = onlineUsers.values().iterator();
        List presences = new ArrayList();

        while (iter.hasNext()) {
            Presence presence = (Presence) iter.next();

            if (presence.isAvailable()) {
                presences.add(presence);
            }
        }

        switch (sortField) {
            case PresenceManager.SORT_ONLINE_TIME:
                {
                    Collections.sort(presences, new Comparator() {
                        public int compare(Object object1, Object object2) {
                            Presence presence1 = (Presence) object1;
                            Presence presence2 = (Presence) object2;
                            Session presence1Session = sessionManager.getSession(presence1.getFrom());
                            Session presence2Session = sessionManager.getSession(presence2.getFrom());

                            if (ascending) {
                                return presence1Session.getCreationDate().compareTo(presence2Session.getCreationDate());
                            }
                            else {
                                return presence2Session.getCreationDate().compareTo(presence1Session.getCreationDate());

                            }
                        }
                    });
                    break;
                }
            case PresenceManager.SORT_USERNAME:
                {
                    Collections.sort(presences, new Comparator() {
                        public int compare(Object object1, Object object2) {
                            Presence presence1 = (Presence) object1;
                            Presence presence2 = (Presence) object2;
                            String presenceUser1 = "";
                            String presenceUser2 = "";
                            try {
                                presenceUser1 =
                                        userManager.getUser(presence1.getFrom().getNode()).getUsername();
                                presenceUser2 =
                                        userManager.getUser(presence2.getFrom().getNode()).getUsername();
                            }
                            catch (UserNotFoundException e) {
                                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                            }
                            if (ascending) {
                                return presenceUser1.compareTo(presenceUser2);
                            }
                            else {
                                return presenceUser2.compareTo(presenceUser1);
                            }
                        }
                    });
                    break;
                }
            default:
                {
                    // ignore invalid sort field
                }
        }

        List<User> users = new ArrayList<User>();

        for (int i = 0; i < presences.size(); i++) {
            Presence presence = (Presence) presences.get(i);
            try {
                users.add(userManager.getUser(presence.getFrom().getNode()));
            }
            catch (UserNotFoundException e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }

        if (numResults > users.size()) {
            return Collections.unmodifiableCollection(users);
        }
        else {
            return Collections.unmodifiableCollection(users.subList(0, numResults - 1));
        }
    }

    public Presence createPresence(User user) {
        Presence presence = null;
        presence = new Presence();
        presence.setFrom(server.createJID(user.getUsername(), null));
        setOnline(presence);
        return presence;
    }

    public void setOnline(Presence presence) {
        User user = null;
        try {
            user = userManager.getUser(presence.getFrom().getNode());
        }
        catch (UserNotFoundException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }

        if (user != null) {
            onlineUsers.put(user.getUsername(), presence);
        }
        else {
            onlineGuests.put(presence.getID(), presence);
        }
    }

    public void setOffline(Presence presence) {
        if (presence.getFrom().getNode() != null) {
            onlineUsers.remove(presence.getFrom().getNode());
        }
        else {
            onlineGuests.remove(presence.getID());
        }
    }

    public void setOffline(JID jid) {
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

    public Presence getPresence(String presenceID) {
        if (presenceID == null) {
            return null;
        }
        // search the current lists for the presence
        Iterator onlineUsers = this.onlineUsers.values().iterator();
        while (onlineUsers.hasNext()) {
            Presence presence = (Presence) onlineUsers.next();

            if (presence.getID().equals(presenceID)) {
                return presence;
            }
        }
        Iterator guestUsers = onlineGuests.values().iterator();
        while (guestUsers.hasNext()) {
            Presence presence = (Presence) guestUsers.next();

            if (presence.getID().equals(presenceID)) {
                return presence;
            }
        }
        return null;
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

    // #####################################################################
    // Module management
    // #####################################################################

    public void initialize(XMPPServer server) {
        super.initialize(server);
        this.server = server;
        userManager = server.getUserManager();
        deliverer = server.getPacketDeliverer();
        sessionManager = server.getSessionManager();
        initializeCaches();
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