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
import org.jivesoftware.messenger.container.Container;
import org.jivesoftware.messenger.container.TrackInfo;
import org.jivesoftware.util.Cache;
import org.jivesoftware.util.*;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.User;
import org.jivesoftware.messenger.user.UserManager;
import org.jivesoftware.messenger.user.UserNotFoundException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in memory implementation of the PresenceManager interface.
 *
 * @author Iain Shigeoka
 */
public class PresenceManagerImpl extends BasicModule implements PresenceManager {

    private Map<String,Presence> onlineGuests;
    private Map<String,Presence> onlineUsers;

    /**
     * table: key jid.getUserJid().toLowerCase() (String); value Presence
     */
    private Cache foreignUserCache;

    public UserManager userManager;
    public SessionManager sessionManager;
    public RoutingTable routingTable;
    public XMPPServer server;
    public PacketDeliverer deliverer;

    public PresenceManagerImpl() {
        super("Presence manager");
    }

    private void initializeCaches() {
        int foreignCacheSize = 128 * 1024 * 8; // 1 MB

        // create caches - no size limit and never expire for presence caches
        long HOUR = JiveConstants.HOUR;
        onlineGuests = new ConcurrentHashMap<String,Presence>();
        onlineUsers = new ConcurrentHashMap<String,Presence>();
        foreignUserCache = new Cache("Foreign Users", foreignCacheSize, HOUR);
    }

    public boolean isAvailable(User user) throws UnauthorizedException {
        return sessionManager.getSessionCount(user.getUsername()) > 0;
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
                    users.add(userManager.getUser(presence.getUsername()));
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
            Presence presence = (Presence)iter.next();

            if (presence.isAvailable()) {
                presences.add(presence);
            }
        }

        switch (sortField) {
            case PresenceManager.SORT_ONLINE_TIME: {
                Collections.sort(presences, new Comparator() {
                    public int compare(Object object1, Object object2) {
                        Presence presence1 = (Presence)object1;
                        Presence presence2 = (Presence)object2;
                        if (ascending) {
                            return presence1.getLoginTime().compareTo(presence2.getLoginTime());
                        }
                        else {
                            return presence2.getLoginTime().compareTo(presence1.getLoginTime());
                        }
                    }
                });
                break;
            }
            case PresenceManager.SORT_USERNAME: {
                Collections.sort(presences, new Comparator() {
                    public int compare(Object object1, Object object2) {
                        Presence presence1 = (Presence)object1;
                        Presence presence2 = (Presence)object2;
                        String presenceUser1 = "";
                        String presenceUser2 = "";
                        try {
                            presenceUser1 =
                                    userManager.getUser(presence1.getUsername()).getUsername();
                            presenceUser2 =
                                    userManager.getUser(presence2.getUsername()).getUsername();
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
            default: {
                // ignore invalid sort field
            }
        }

        List<User> users = new ArrayList<User>();

        for (int i = 0; i < presences.size(); i++) {
            Presence presence = (Presence)presences.get(i);
            try {
                users.add(userManager.getUser(presence.getUsername()));
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

    public Presence createPresence(User user, String uid) {
        Presence presence = null;
        presence = new PresenceImpl(user, uid);
        setOnline(presence);
        return presence;
    }

    public void setOnline(Presence presence) {
        User user = null;
        try {
            user = userManager.getUser(presence.getUsername());
        }
        catch (UserNotFoundException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }

        if (user != null) {
            synchronized (onlineUsers) {
                onlineUsers.put(user.getUsername(), presence);
            }
        }
        else {
            synchronized (onlineGuests) {
                onlineGuests.put(presence.getID(), presence);
            }
        }
    }

    public void setOffline(Presence presence) {
        if (presence.getUsername() != null) {
            synchronized (onlineUsers) {
                onlineUsers.remove(presence.getUsername());
            }
        }
        else {
            synchronized (onlineGuests) {
                onlineGuests.remove(presence.getID());
            }
        }
    }

    public void setOffline(XMPPAddress jid) throws UnauthorizedException {
    }

    public boolean isOnline(User user) {
        if (user == null) {
            return false;
        }
        Presence presence = (Presence)onlineUsers.get(user.getUsername());
        if (presence != null) {
            return presence.isAvailable();
        }
        return false;
    }

    public Presence getPresence(User user) {
        if (user == null) {
            return null;
        }
        // try getting the presence obj from the online user cache:
        Presence userPresence = (Presence)onlineUsers.get(user.getUsername());
        if (userPresence != null) {
            return userPresence;
        }
        else {
            // Load up the presence from the db
            return new PresenceImpl(user, null);
        }
    }

    public Presence getPresence(String presenceID) {
        if (presenceID == null) {
            return null;
        }
        // search the current lists for the presence
        Iterator onlineUsers = this.onlineUsers.values().iterator();
        while (onlineUsers.hasNext()) {
            Presence presence = (Presence)onlineUsers.next();

            if (presence.getID().equals(presenceID)) {
                return presence;
            }
        }
        Iterator guestUsers = onlineGuests.values().iterator();
        while (guestUsers.hasNext()) {
            Presence presence = (Presence)guestUsers.next();

            if (presence.getID().equals(presenceID)) {
                return presence;
            }
        }
        return null;
    }

    public void probePresence(String prober, XMPPAddress probee) {
        try {
            if (server.isLocal(probee)) {
                if (probee.getNamePrep() != null && !"".equals(probee.getNamePrep())) {
                    Iterator sessionIter =
                            sessionManager.getSessions(probee.getNamePrep());
                    while (sessionIter.hasNext()) {
                        Session session = (Session)sessionIter.next();
                        Presence presencePacket =
                                (Presence)session.getPresence().createDeepCopy();
                        presencePacket.setSender(session.getAddress());
                        try {
                            sessionManager.userBroadcast(prober, presencePacket);
                        }
                        catch (Exception e) {
                            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                        }
                    }
                }
            }
            else {
                Presence presence =
                        (Presence)foreignUserCache.get(probee.toBareStringPrep());
                if (presence != null) {
                    Presence presencePacket = (Presence)presence.createDeepCopy();
                    presencePacket.setSender(probee);
                    try {
                        sessionManager.userBroadcast(prober, presencePacket);
                    }
                    catch (Exception e) {
                        Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                    }
                }
            }
        }
        catch (UnauthorizedException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
    }

    public void probePresence(XMPPAddress prober, XMPPAddress probee) {
        try {
            if (server.isLocal(probee)) {
                Iterator sessionIter =
                        sessionManager.getSessions(probee.getName().toLowerCase());
                while (sessionIter.hasNext()) {
                    Session session = (Session)sessionIter.next();
                    Presence presencePacket =
                            (Presence)session.getPresence().createDeepCopy();
                    presencePacket.setSender(session.getAddress());
                    try {
                        deliverer.deliver(presencePacket);
                    }
                    catch (Exception e) {
                        Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                    }
                }
            }
            else {
                Presence presence =
                        (Presence)foreignUserCache.get(probee.toBareStringPrep());
                if (presence != null) {
                    Presence presencePacket = (Presence)presence.createDeepCopy();
                    presencePacket.setSender(probee);
                    try {
                        deliverer.deliver(presencePacket);
                    }
                    catch (Exception e) {
                        Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                    }
                }
            }
        }
        catch (UnauthorizedException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
    }

    // #####################################################################
    // Module management
    // #####################################################################

    public void initialize(Container container) {
        super.initialize(container);
        initializeCaches();
    }

    protected TrackInfo getTrackInfo() {
        TrackInfo trackInfo = new TrackInfo();
        trackInfo.getTrackerClasses().put(SessionManager.class, "sessionManager");
        trackInfo.getTrackerClasses().put(UserManager.class, "userManager");
        trackInfo.getTrackerClasses().put(XMPPServer.class, "server");
        trackInfo.getTrackerClasses().put(PacketDeliverer.class, "deliverer");
        trackInfo.getTrackerClasses().put(RoutingTable.class, "routingTable");
        return trackInfo;
    }
}