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

package org.jivesoftware.messenger.user.spi;

import java.util.Iterator;
import java.util.List;
import org.jivesoftware.messenger.ChannelHandler;
import org.jivesoftware.messenger.PresenceManager;
import org.jivesoftware.messenger.RoutingTable;
import org.jivesoftware.messenger.SessionManager;
import org.jivesoftware.messenger.XMPPServer;
import org.jivesoftware.messenger.spi.BasicServer;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.BasicRoster;
import org.jivesoftware.messenger.user.BasicRosterItem;
import org.jivesoftware.messenger.user.CachedRoster;
import org.jivesoftware.messenger.user.CachedRosterItem;
import org.jivesoftware.messenger.user.RosterItem;
import org.jivesoftware.messenger.user.RosterItemProvider;
import org.jivesoftware.messenger.user.UserAlreadyExistsException;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.jivesoftware.messenger.user.UserProviderFactory;
import org.jivesoftware.util.CacheSizes;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;
import org.xmpp.packet.Roster;

/**
 * <p>A roster implemented against a JDBC database.</p>
 * <p/>
 * <p>Updates to this roster is effectively a change to the user or chatbot
 * account's roster. To reflect this, the changes to this class will
 * will automatically update the persistently stored roster, as well as
 * send out update announcements to all logged in user sessions.</p>
 *
 * @author Iain Shigeoka
 */
public class CachedRosterImpl extends BasicRoster implements CachedRoster {

    private RosterItemProvider rosterItemProvider;
    private String username;
    private SessionManager sessionManager;
    private XMPPServer server;
    private RoutingTable routingTable;


    /**
     * <p>Create a roster for the given user, pulling the existing roster items
     * out of the backend storage provider.</p>
     *
     * @param username The username of the user that owns this roster
     */
    public CachedRosterImpl(String username) {
        sessionManager = SessionManager.getInstance();

        this.username = username;
        rosterItemProvider = UserProviderFactory.getRosterItemProvider();
        Iterator items = rosterItemProvider.getItems(username);
        while (items.hasNext()) {
            RosterItem item = (RosterItem)items.next();
            rosterItems.put(item.getJid().toBareJID(), item);
        }
    }

    public String getUsername() {
        return username;
    }

    public Roster getReset() throws UnauthorizedException {
        Roster roster = new Roster();
        Iterator items = getRosterItems();
        while (items.hasNext()) {
            RosterItem item = (RosterItem)items.next();
            if (item.getSubStatus() != RosterItem.SUB_NONE || item.getAskStatus() != RosterItem.ASK_NONE) {
                roster.addItem(item.getJid(), item.getNickname(),
                        Roster.Ask.valueOf(item.getAskStatus().getName()),
                        Roster.Subscription.valueOf(item.getSubStatus().getName()),
                        item.getGroups());
            }
        }
        return roster;
    }

    public void broadcastPresence(Presence packet) {
        try {
            if (routingTable == null) {
                routingTable = BasicServer.getInstance().getRoutingTable();
            }
            if (routingTable == null) {
                return;
            }
            Iterator items = getRosterItems();
            while (items.hasNext()) {
                RosterItem item = (RosterItem)items.next();
                if (item.getSubStatus() == RosterItem.SUB_BOTH
                        || item.getSubStatus() == RosterItem.SUB_FROM) {
                    JID searchNode = new JID(item.getJid().getNode(), item.getJid().getDomain(), null);
                    Iterator sessions = routingTable.getRoutes(searchNode);
                    packet.setTo(item.getJid());
                    while (sessions.hasNext()) {
                        ChannelHandler session = (ChannelHandler)sessions.next();
                        try {
                            session.process(packet);
                        }
                        catch (Exception e) {
                            // Ignore any problems with sending - theoretically
                            // only happens if session has been closed
                        }
                    }
                }
            }
        }
        catch (UnauthorizedException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
    }

    protected RosterItem provideRosterItem(JID user, String nickname, List<String> group) throws UserAlreadyExistsException, UnauthorizedException {
        Roster roster = new Roster();
        roster.setType(IQ.Type.set);
        Roster.Item item = roster.addItem(user, nickname, null, Roster.Subscription.none, group);

        RosterItem rosterItem = rosterItemProvider.createItem(username, new BasicRosterItem(item));

        // Broadcast the roster push to the user
        broadcast(roster);

        return rosterItem;
    }

    private PresenceManager presenceManager;

    public void updateRosterItem(RosterItem item) throws UnauthorizedException, UserNotFoundException {
        CachedRosterItem cachedItem = null;
        if (item instanceof CachedRosterItem) {
            // This is a known item
            cachedItem = (CachedRosterItem)item;
        }
        else {
            // This is a different item object, probably an BasicRosterItem update for an existing
            // item. So grab the cached version out of the super to learn the rosterID for the item
            // And create a new cached roster item with the new info
            cachedItem = (CachedRosterItem)super.getRosterItem(item.getJid());
            cachedItem = new CachedRosterItemImpl(cachedItem.getID(), item);
        }
        // Update the super first, this will throw a UserNotFoundException if the entry doesn't
        // already exist
        super.updateRosterItem(cachedItem);
        // Update the backend data store
        rosterItemProvider.updateItem(username, cachedItem);
        // broadcast roster update
        if (!(cachedItem.getSubStatus() == RosterItem.SUB_NONE
                && cachedItem.getAskStatus() == RosterItem.ASK_NONE)) {

            Roster roster = new Roster();
            roster.setType(IQ.Type.set);
            roster.addItem(cachedItem.getJid(), cachedItem.getNickname(),
                    Roster.Ask.valueOf(cachedItem.getAskStatus().getName()),
                    Roster.Subscription.valueOf(cachedItem.getSubStatus().getName()),
                    cachedItem.getGroups());
            broadcast(roster);

        }
        if (cachedItem.getSubStatus() == RosterItem.SUB_BOTH
                || cachedItem.getSubStatus() == RosterItem.SUB_TO) {
            if (presenceManager == null) {
                presenceManager = BasicServer.getInstance().getPresenceManager();
            }
            presenceManager.probePresence(username, cachedItem.getJid());
        }
    }

    public synchronized RosterItem deleteRosterItem(JID user) throws UnauthorizedException {

        // Note that the super cache will always only hold cached roster items
        CachedRosterItem item = (CachedRosterItem)super.deleteRosterItem(user);
        if (item != null) {
            // If removing the user was successful, remove the user from the backend store
            rosterItemProvider.deleteItem(username, item.getID());

            // broadcast the update to the user
            Roster roster = new Roster();
            roster.setType(IQ.Type.set);
            roster.addItem(user, Roster.Subscription.remove);
            broadcast(roster);
        }
        return item;
    }



    private void broadcast(Roster roster) throws UnauthorizedException {
        if (server == null) {
            server = BasicServer.getInstance();
        }
        JID recipient = server.createJID(username, null);
        roster.setTo(recipient);
        if (sessionManager == null) {
            sessionManager = SessionManager.getInstance();
        }
        sessionManager.userBroadcast(username, roster);
    }

    public int getCachedSize() {
        // Approximate the size of the object in bytes by calculating the size
        // of each field.
        int size = 0;
        size += CacheSizes.sizeOfObject();              // overhead of object
        size += CacheSizes.sizeOfString(username);     // username
        try {
            Iterator itemIter = getRosterItems();
            while (itemIter.hasNext()) {
                CachedRosterItem item = (CachedRosterItem)itemIter.next();
                size += item.getCachedSize();
            }
        }
        catch (UnauthorizedException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        return size;
    }
}
