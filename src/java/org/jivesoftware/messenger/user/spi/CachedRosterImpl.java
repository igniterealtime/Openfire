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

import org.jivesoftware.util.CacheSizes;
import org.jivesoftware.messenger.container.ServiceLookupFactory;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.chatbot.ChatbotManager;
import org.jivesoftware.messenger.user.*;
import java.util.Iterator;
import java.util.List;
import javax.xml.stream.XMLStreamException;

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

    /**
     * <p>ID of the user that owns this roster.</p>
     */
    private long userID;
    /**
     * <p>Backend storage provider for rosters.</p>
     */
    private RosterItemProvider rosterItemProvider;
    // TODO: we should move all routing to a single place, doing routing logic here sucks
    /**
     * <p>Chatbot manager used for routing roster broadcasts.</p>
     */
    private ChatbotManager chatbotManager;
    /**
     * <p>User name of the user/chatbot that owns this roster.</p>
     */
    private String username;

    /**
     * <p>Create a roster for the given user, pulling the existing roster items
     * out of the backend storage provider.</p>
     *
     * @param id       ID of the user that owns this roster
     * @param username The username of the user that owns this roster
     */
    public CachedRosterImpl(long id, String username) {
        this.userID = id;
        this.username = username;
        rosterItemProvider = UserProviderFactory.getRosterItemProvider();
        Iterator items = rosterItemProvider.getItems(userID);
        while (items.hasNext()) {
            RosterItem item = (RosterItem)items.next();
            rosterItems.put(item.getJid().toBareStringPrep(), item);
        }
    }

    public long getUserID() {
        return userID;
    }

    public IQRoster getReset() throws UnauthorizedException {
        IQRoster roster = new IQRosterImpl();
        Iterator items = getRosterItems();
        while (items.hasNext()) {
            RosterItem item = (RosterItem)items.next();
            if (item.getSubStatus() != RosterItem.SUB_NONE || item.getAskStatus() != RosterItem.ASK_NONE) {
                try {
                    roster.createRosterItem(item);
                }
                catch (UserAlreadyExistsException e) {
                    Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                }
            }
        }
        return roster;
    }

    public void broadcastPresence(Presence packet) {
        try {
            if (routingTable == null) {
                routingTable =
                        (RoutingTable)ServiceLookupFactory.getLookup().lookup(RoutingTable.class);
            }
            if (routingTable == null) {
                return;
            }
            Iterator items = getRosterItems();
            while (items.hasNext()) {
                RosterItem item = (RosterItem)items.next();
                if (item.getSubStatus() == RosterItem.SUB_BOTH
                        || item.getSubStatus() == RosterItem.SUB_FROM) {
                    XMPPAddress searchNode =
                            new XMPPAddress(item.getJid().getName(),
                                    item.getJid().getHost(),
                                    null);
                    Iterator sessions = routingTable.getRoutes(searchNode);
                    packet.setRecipient(item.getJid());
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

    protected RosterItem provideRosterItem(XMPPAddress user, String nickname, List group)
            throws UserAlreadyExistsException, UnauthorizedException {
        return provideRosterItem(new BasicRosterItem(user, nickname, group));
    }

    protected RosterItem provideRosterItem(RosterItem item)
            throws UserAlreadyExistsException, UnauthorizedException {
        item = rosterItemProvider.createItem(userID, new BasicRosterItem(item));

        // Broadcast the roster push to the user
        IQRoster roster = new IQRosterImpl();
        roster.setType(IQ.SET);
        roster.createRosterItem(item);
        broadcast(roster);

        return item;
    }

    private PresenceManager presenceManager;

    public void updateRosterItem(RosterItem item) throws UnauthorizedException, UserNotFoundException {
        CachedRosterItem cachedItem = null;
        if (item instanceof CachedRosterItem) {
            // This is a known item
            cachedItem = (CachedRosterItem)item;
        }
        else {
            // This is a different item object, probably an IQRosterItem update for an existing item
            // So grab the cached version out of the super to learn the rosterID for the item
            // And create a new cached roster item with the new info
            cachedItem = (CachedRosterItem)super.getRosterItem(item.getJid());
            cachedItem = new CachedRosterItemImpl(cachedItem.getID(), item);
        }
        // Update the super first, this will throw a UserNotFoundException if the entry doesn't
        // already exist
        super.updateRosterItem(cachedItem);
        // Update the backend data store
        rosterItemProvider.updateItem(userID, cachedItem);
        // broadcast roster update
        if (!(cachedItem.getSubStatus() == RosterItem.SUB_NONE
                && cachedItem.getAskStatus() == RosterItem.ASK_NONE)) {

            try {
                IQRoster roster = new IQRosterImpl();
                roster.setType(IQ.SET);
                roster.createRosterItem(cachedItem);
                broadcast(roster);
            }
            catch (UserAlreadyExistsException e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }

        }
        if (cachedItem.getSubStatus() == RosterItem.SUB_BOTH
                || cachedItem.getSubStatus() == RosterItem.SUB_TO) {
            if (presenceManager == null) {
                presenceManager = (PresenceManager)ServiceLookupFactory.getLookup().lookup(PresenceManager.class);
            }
            presenceManager.probePresence(username, cachedItem.getJid());
        }
    }

    public synchronized RosterItem deleteRosterItem(XMPPAddress user) throws UnauthorizedException {

        // Note that the super cache will always only hold cached roster items
        CachedRosterItem item = (CachedRosterItem)super.deleteRosterItem(user);
        if (item != null) {
            // If removing the user was successful, remove the user from the backend store
            rosterItemProvider.deleteItem(userID, item.getID());

            try {
                // broadcast the update to the user
                IQRoster roster = new IQRosterImpl();
                roster.setType(IQ.SET);
                IQRosterItem iqItem = (IQRosterItem)roster.createRosterItem(user);
                iqItem.setSubStatus(RosterItem.SUB_REMOVE);
                broadcast(roster);
            }
            catch (UserAlreadyExistsException e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
        return item;
    }

    private SessionManager sessionManager;
    private XMPPServer server;
    private RoutingTable routingTable;

    private void broadcast(IQRoster roster) throws UnauthorizedException {
        try {
            if (server == null) {
                server = (XMPPServer)ServiceLookupFactory.getLookup().lookup(XMPPServer.class);
            }
            XMPPAddress recipient = server.createAddress(username, null);
            roster.setRecipient(recipient);
            roster.setOriginatingSession(server.getSession());
            if (chatbotManager == null) {
                chatbotManager = (ChatbotManager)ServiceLookupFactory.getLookup().lookup(ChatbotManager.class);
            }
            if (chatbotManager.isChatbot(recipient)) {
                if (routingTable == null) {
                    routingTable = (RoutingTable)ServiceLookupFactory.getLookup().lookup(RoutingTable.class);
                }
                try {
                    ChannelHandler handler = routingTable.getRoute(recipient);
                    handler.process(roster);
                }
                catch (NoSuchRouteException e) {
                    Log.warn("Chatbot unreachable " + recipient, e);
                }
            }
            else {
                if (sessionManager == null) {
                    sessionManager = (SessionManager)ServiceLookupFactory.getLookup().lookup(SessionManager.class);
                }
                sessionManager.userBroadcast(username, roster);
            }
        }
        catch (XMLStreamException e) {
            // We couldn't send to the user, no big deal
        }
    }

    public int getCachedSize() {
        // Approximate the size of the object in bytes by calculating the size
        // of each field.
        int size = 0;
        size += CacheSizes.sizeOfObject();              // overhead of object
        size += CacheSizes.sizeOfLong();                // userID
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
