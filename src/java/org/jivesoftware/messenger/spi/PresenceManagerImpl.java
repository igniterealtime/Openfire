/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2002 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.spi;

import org.jivesoftware.util.Cache;
import org.jivesoftware.util.CacheFactory;
import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.container.Container;
import org.jivesoftware.messenger.container.ModuleContext;
import org.jivesoftware.messenger.container.TrackInfo;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.Cache;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.chatbot.ChatbotManager;
import org.jivesoftware.messenger.user.RosterItem;
import org.jivesoftware.messenger.user.User;
import org.jivesoftware.messenger.user.UserManager;
import org.jivesoftware.messenger.user.UserNotFoundException;
import java.util.*;

/**
 * Simple in memory implementation of the PresenceManager interface.
 *
 * @author Iain Shigeoka
 */
public class PresenceManagerImpl extends BasicModule implements PresenceManager {

    /**
     * Query for roster subscribers
     */
    private static final String GET_ROSTER_SUBS =
            "SELECT userID FROM jiveRoster WHERE jid=? AND (sub=" +
            RosterItem.SUB_BOTH + " OR sub=" + RosterItem.SUB_TO + ")";

    /**
     * table: key Presence ID (Long); value Presence
     */
    private Cache onlineGuestCache;
    /**
     * table: key User ID (Long); value Presence
     */
    private Cache onlineUserCache;
    /**
     * table: key jid.getUserJid().toLowerCase() (String); value Presence
     */
    private Cache foreignUserCache;

    public UserManager userManager;
    public ChatbotManager chatbotManager;
    public SessionManager sessionManager;
    public RoutingTable routingTable;
    public XMPPServer server;
    private String serverName;
    public PacketDeliverer deliverer;

    public PresenceManagerImpl() {
        super("Presence manager");
    }

    private void initializeCaches() {
        int foreignCacheSize = 128 * 1024 * 8; // 1 MB

        // create caches - no size limit and never expire for presence caches
        long HOUR = JiveConstants.HOUR;
        onlineGuestCache = CacheFactory.createCache("Online Guests", -1, -1);
        onlineUserCache = CacheFactory.createCache("Online Users", -1, -1);
        foreignUserCache = CacheFactory.createCache("Foreign Users",
                foreignCacheSize, HOUR);
    }

    public boolean isAvailable(User user) throws UnauthorizedException {
        return sessionManager.getSessionCount(user.getUsername()) > 0;
    }

    public int getOnlineUserCount() {
        Iterator iter = getOnlineUsers();
        int count = 0;

        while (iter.hasNext()) {
            iter.next();
            count++;
        }

        return count;
    }

    public int getOnlineGuestCount() {
        Iterator iter = onlineGuestCache.values().iterator();
        int count = 0;

        while (iter.hasNext()) {
            Presence presence = (Presence)iter.next();
            if (presence.isAvailable()) {
                count++;
            }
        }

        return count;
    }

    public Iterator getOnlineUsers() {
        Iterator iter = onlineUserCache.values().iterator();
        List users = new ArrayList();

        while (iter.hasNext()) {
            Presence presence = (Presence)iter.next();

            if (presence.isAvailable()) {
                try {
                    users.add(userManager.getUser(presence.getUserID()));
                }
                catch (UserNotFoundException e) {
                    Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                }
            }
        }

        return Collections.unmodifiableList(getOnlineUserList()).iterator();
    }

    public Iterator getOnlineUsers(boolean ascending, int sortField) {
        return getOnlineUsers(ascending, sortField, Integer.MAX_VALUE);
    }

    public Iterator getOnlineUsers(final boolean ascending,
                                   int sortField,
                                   int numResults) {
        Iterator iter = onlineUserCache.values().iterator();
        List presences = new ArrayList();

        while (iter.hasNext()) {
            Presence presence = (Presence)iter.next();

            if (presence.isAvailable()) {
                presences.add(presence);
            }
        }

        switch (sortField) {
            case PresenceManager.SORT_ONLINE_TIME:
                {
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
            case PresenceManager.SORT_USERNAME:
                {
                    Collections.sort(presences, new Comparator() {
                        public int compare(Object object1, Object object2) {
                            Presence presence1 = (Presence)object1;
                            Presence presence2 = (Presence)object2;
                            String presenceUser1 = "";
                            String presenceUser2 = "";
                            try {
                                presenceUser1 =
                                        userManager.getUser(presence1.getUserID()).getUsername();
                                presenceUser2 =
                                        userManager.getUser(presence2.getUserID()).getUsername();
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

        List users = new ArrayList();

        for (int i = 0; i < presences.size(); i++) {
            Presence presence = (Presence)presences.get(i);
            try {
                users.add(userManager.getUser(presence.getUserID()));
            }
            catch (UserNotFoundException e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }

        if (numResults > users.size()) {
            return Collections.unmodifiableList(users).iterator();
        }
        else {
            return Collections.unmodifiableList(users.subList(0, numResults - 1)).iterator();
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
            user = userManager.getUser(presence.getUserID());
        }
        catch (UserNotFoundException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }

        if (user != null) {
            synchronized (onlineUserCache) {
                onlineUserCache.put(new Long(user.getID()), presence);
            }
        }
        else {
            synchronized (onlineGuestCache) {
                onlineGuestCache.put(presence.getID(), presence);
            }
        }
    }

    public void setOffline(Presence presence) {
        if (presence.getUserID() != -1) {
            synchronized (onlineUserCache) {
                Long id = new Long(presence.getUserID());
                onlineUserCache.remove(id);
            }
        }
        else {
            synchronized (onlineGuestCache) {
                onlineGuestCache.remove(presence.getID());
            }
        }
    }

    public void setOffline(XMPPAddress jid) throws UnauthorizedException {
    }

    public boolean isOnline(User user) {
        if (user == null) {
            return false;
        }

        Presence presence = (Presence)onlineUserCache.get(new Long(user.getID()));

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
        Presence userPresence = (Presence)onlineUserCache.get(new Long(user.getID()));
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
        Iterator onlineUsers = onlineUserCache.values().iterator();
        while (onlineUsers.hasNext()) {
            Presence presence = (Presence)onlineUsers.next();

            if (presence.getID().equals(presenceID)) {
                return presence;
            }
        }

        Iterator guestUsers = onlineGuestCache.values().iterator();
        while (guestUsers.hasNext()) {
            Presence presence = (Presence)guestUsers.next();

            if (presence.getID().equals(presenceID)) {
                return presence;
            }
        }

        return null;
    }

    private List getOnlineUserList() {
        Iterator iter = onlineUserCache.values().iterator();
        List users = new ArrayList();

        while (iter.hasNext()) {
            Presence presence = (Presence)iter.next();

            if (presence.isAvailable()) {
                try {
                    users.add(userManager.getUser(presence.getUserID()));
                }
                catch (UserNotFoundException e) {
                    Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                }
            }
        }

        return users;
    }

    public void probePresence(String prober, XMPPAddress probee) {
        try {
            if (chatbotManager.isChatbot(probee)) {
                try {
                    RoutableChannelHandler handler = routingTable.getRoute(probee);
                    Presence presence = new PresenceImpl();
                    presence.setType(Presence.PROBE);
                    presence.setSender(server.createAddress(prober, ""));
                    presence.setRecipient(handler.getAddress());
                    handler.process(presence);
                }
                catch (NoSuchRouteException e) {
                    Log.info(prober + " probing presence of unavailable chatbot " + probee);
                }
            }
            else if (server.isLocal(probee)) {
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
                            e.printStackTrace();
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
                        e.printStackTrace();
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
            if (chatbotManager.isChatbot(probee)) {
                try {
                    RoutableChannelHandler handler = routingTable.getRoute(probee);
                    Presence presence = new PresenceImpl();
                    presence.setType(Presence.PROBE);
                    presence.setSender(prober);
                    presence.setRecipient(handler.getAddress());
                    handler.process(presence);
                }
                catch (NoSuchRouteException e) {
                    Log.info(prober + " probing presence of unavailable chatbot " + probee);
                }
            }
            else if (server.isLocal(probee)) {
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
                        e.printStackTrace();
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
                        e.printStackTrace();
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

    public void initialize(ModuleContext context, Container container) {
        super.initialize(context, container);
        initializeCaches();
    }

    protected TrackInfo getTrackInfo() {
        TrackInfo trackInfo = new TrackInfo();
        trackInfo.getTrackerClasses().put(SessionManager.class, "sessionManager");
        trackInfo.getTrackerClasses().put(UserManager.class, "userManager");
        trackInfo.getTrackerClasses().put(ChatbotManager.class, "chatbotManager");
        trackInfo.getTrackerClasses().put(XMPPServer.class, "server");
        trackInfo.getTrackerClasses().put(PacketDeliverer.class, "deliverer");
        trackInfo.getTrackerClasses().put(RoutingTable.class, "routingTable");
        return trackInfo;
    }

    public void serviceAdded(Object service) {
        if (service instanceof XMPPServer && server != null) {
            serverName = server.getServerInfo().getName();
        }
    }

    public void serviceRemoved(Object service) {
        if (server == null) {
            serverName = null;
        }
    }
}