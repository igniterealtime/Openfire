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

package org.jivesoftware.messenger.roster;

import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.jivesoftware.messenger.user.UserAlreadyExistsException;
import org.jivesoftware.messenger.*;
import org.jivesoftware.util.Cacheable;
import org.jivesoftware.util.CacheSizes;
import org.xmpp.packet.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>A roster is a list of users that the user wishes to know if they are online.</p>
 * <p>Rosters are similar to buddy groups in popular IM clients. The Roster class is
 * a representation of the roster data.<p/>
 *
 * <p>Updates to this roster is effectively a change to the user's roster. To reflect this,
 * the changes to this class will automatically update the persistently stored roster, as well as
 * send out update announcements to all logged in user sessions.</p>
 *
 * @author Gaston Dombiak
 */
public class Roster implements Cacheable {

    /**
     * <p>Roster item cache - table: key jabberid string; value roster item.</p>
     */
    protected ConcurrentHashMap<String, RosterItem> rosterItems = new ConcurrentHashMap<String, RosterItem>();

    private RosterItemProvider rosterItemProvider;
    private String username;
    private SessionManager sessionManager;
    private XMPPServer server;
    private RoutingTable routingTable;
    private PresenceManager presenceManager;


    /**
     * <p>Create a roster for the given user, pulling the existing roster items
     * out of the backend storage provider.</p>
     *
     * @param username The username of the user that owns this roster
     */
    public Roster(String username) {
        sessionManager = SessionManager.getInstance();

        this.username = username;
        rosterItemProvider =  RosterItemProvider.getInstance();
        Iterator items = rosterItemProvider.getItems(username);
        while (items.hasNext()) {
            RosterItem item = (RosterItem)items.next();
            rosterItems.put(item.getJid().toBareJID(), item);
        }
    }

    /**
     * Returns true if the specified user is a member of the roster, false otherwise.
     *
     * @param user the user object to check.
     * @return true if the specified user is a member of the roster, false otherwise.
     */
    public boolean isRosterItem(JID user) {
        return rosterItems.containsKey(user.toBareJID());
    }

    /**
     * Returns a collection of users in this roster.
     *
     * @return a collection of users in this roster.
     */
    public Collection<RosterItem> getRosterItems() {
        return Collections.unmodifiableCollection(rosterItems.values());
    }

    /**
     * Returns the total number of users in the roster.
     *
     * @return the number of online users in the roster.
     */
    public int getTotalRosterItemCount() {
        return rosterItems.size();
    }

    /**
     * Gets a user from the roster. If the roster item does not exist, an empty one is created.
     * The new roster item is not stored in the roster until it is added using
     * addRosterItem().
     *
     * @param user the XMPPAddress for the roster item to retrieve
     * @return The roster item associated with the user XMPPAddress
     */
    public RosterItem getRosterItem(JID user) throws UserNotFoundException {
        RosterItem item = rosterItems.get(user.toBareJID());
        if (item == null) {
            throw new UserNotFoundException(user.toBareJID());
        }
        return item;
    }

    /**
     * Create a new item to the roster. Roster items may not be created that contain the same user
     * address as an existing item.
     *
     * @param user the item to add to the roster.
     */
    public RosterItem createRosterItem(JID user) throws UserAlreadyExistsException {
        return createRosterItem(user, null, null);
    }

    /**
     * Create a new item to the roster. Roster items may not be created that contain the same user
     * address as an existing item.
     *
     * @param user     the item to add to the roster.
     * @param nickname The nickname for the roster entry (can be null)
     * @param groups   The list of groups to assign this roster item to (can be null)
     */
    public RosterItem createRosterItem(JID user, String nickname, List<String> groups)
            throws UserAlreadyExistsException {
        RosterItem item = provideRosterItem(user, nickname, groups);
        rosterItems.put(item.getJid().toBareJID(), item);
        return item;
    }

    /**
     * Create a new item to the roster based as a copy of the given item.
     * Roster items may not be created that contain the same user address
     * as an existing item in the roster.
     *
     * @param item the item to copy and add to the roster.
     */
    public void createRosterItem(org.xmpp.packet.Roster.Item item)
            throws UnauthorizedException, UserAlreadyExistsException {
        RosterItem rosterItem = provideRosterItem(item);
        rosterItems.put(item.getJID().toBareJID(), rosterItem);
    }

    /**
     * <p>Generate a new RosterItem for use with createRosterItem.<p>
     *
     * @param item The item to copy settings for the new item in this roster
     * @return The newly created roster items ready to be stored by the Roster item's hash table
     */
    protected RosterItem provideRosterItem(org.xmpp.packet.Roster.Item item)
            throws UserAlreadyExistsException, UnauthorizedException {
        return provideRosterItem(item.getJID(), item.getName(),
                new ArrayList<String>(item.getGroups()));
    }

    /**
     * <p>Generate a new RosterItem for use with createRosterItem.<p>
     *
     * @param user     The roster jid address to create the roster item for
     * @param nickname The nickname to assign the item (or null for none)
     * @param groups   The groups the item belongs to (or null for none)
     * @return The newly created roster items ready to be stored by the Roster item's hash table
     */
    protected RosterItem provideRosterItem(JID user, String nickname, List<String> groups)
            throws UserAlreadyExistsException {
        org.xmpp.packet.Roster roster = new org.xmpp.packet.Roster();
        roster.setType(IQ.Type.set);
        org.xmpp.packet.Roster.Item item = roster.addItem(user, nickname, null,
                org.xmpp.packet.Roster.Subscription.none, groups);

        RosterItem rosterItem = rosterItemProvider.createItem(username, new RosterItem(item));

        // Broadcast the roster push to the user
        broadcast(roster);

        return rosterItem;
    }

    /**
     * Update an item that is already in the roster.
     *
     * @param item the item to update in the roster.
     * @throws UserNotFoundException If the roster item for the given user doesn't already exist
     */
    public void updateRosterItem(RosterItem item) throws UserNotFoundException {
        if (rosterItems.putIfAbsent(item.getJid().toBareJID(), item) == null) {
            rosterItems.remove(item.getJid().toBareJID());
            throw new UserNotFoundException(item.getJid().toBareJID());
        }
        // Update the backend data store
        rosterItemProvider.updateItem(username, item);
        // broadcast roster update
        if (!(item.getSubStatus() == RosterItem.SUB_NONE
                && item.getAskStatus() == RosterItem.ASK_NONE)) {

            org.xmpp.packet.Roster roster = new org.xmpp.packet.Roster();
            roster.setType(IQ.Type.set);
            roster.addItem(item.getJid(), item.getNickname(),
                    getAskStatus(item.getAskStatus()),
                    org.xmpp.packet.Roster.Subscription.valueOf(item.getSubStatus().getName()),
                    item.getGroups());
            broadcast(roster);

        }
        if (item.getSubStatus() == RosterItem.SUB_BOTH
                || item.getSubStatus() == RosterItem.SUB_TO) {
            if (presenceManager == null) {
                presenceManager = XMPPServer.getInstance().getPresenceManager();
            }
            presenceManager.probePresence(username, item.getJid());
        }
    }

    /**
     * Remove a user from the roster.
     *
     * @param user the user to remove from the roster.
     * @return The roster item being removed or null if none existed
     */
    public RosterItem deleteRosterItem(JID user) {
        // If removing the user was successful, remove the user from the subscriber list:
        RosterItem item = rosterItems.remove(user.toBareJID());
        if (item != null) {
            // If removing the user was successful, remove the user from the backend store
            rosterItemProvider.deleteItem(username, item.getID());

            // Broadcast the update to the user
            org.xmpp.packet.Roster roster = new org.xmpp.packet.Roster();
            roster.setType(IQ.Type.set);
            roster.addItem(user, org.xmpp.packet.Roster.Subscription.remove);
            broadcast(roster);
        }
        return item;

    }

    /**
     * <p>Return the username of the user or chatbot that owns this roster.</p>
     *
     * @return the username of the user or chatbot that owns this roster
     */
    public String getUsername() {
        return username;
    }

    /**
     * <p>Obtain a 'roster reset', a snapshot of the full cached roster as an Roster.</p>
     *
     * @return The roster reset (snapshot) as an Roster
     */
    public org.xmpp.packet.Roster getReset() {
        org.xmpp.packet.Roster roster = new org.xmpp.packet.Roster();
        for (RosterItem item : rosterItems.values()) {
            if (item.getSubStatus() != RosterItem.SUB_NONE || item.getAskStatus() != RosterItem.ASK_NONE) {
                roster.addItem(item.getJid(), item.getNickname(),
                        getAskStatus(item.getAskStatus()),
                        org.xmpp.packet.Roster.Subscription.valueOf(item.getSubStatus().getName()),
                        item.getGroups());
            }
        }
        return roster;
    }

    private org.xmpp.packet.Roster.Ask getAskStatus(RosterItem.AskType askType) {
        if ("".equals(askType.getName())) {
            return null;
        }
        return org.xmpp.packet.Roster.Ask.valueOf(askType.getName());
    }

    /**
     * <p>Broadcast the presence update to all subscribers of the roter.</p>
     * <p/>
     * <p>Any presence change typically results in a broadcast to the roster members.</p>
     *
     * @param packet The presence packet to broadcast
     */
    public void broadcastPresence(Presence packet) {
        if (routingTable == null) {
            routingTable = XMPPServer.getInstance().getRoutingTable();
        }
        if (routingTable == null) {
            return;
        }
        for (RosterItem item : rosterItems.values()) {
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

    private void broadcast(org.xmpp.packet.Roster roster) {
        if (server == null) {
            server = XMPPServer.getInstance();
        }
        JID recipient = server.createJID(username, null);
        roster.setTo(recipient);
        if (sessionManager == null) {
            sessionManager = SessionManager.getInstance();
        }
        try {
            sessionManager.userBroadcast(username, roster);
        }
        catch (UnauthorizedException e) {
            // Do nothing. We should never end here.
        }
    }

    public int getCachedSize() {
        // Approximate the size of the object in bytes by calculating the size
        // of each field.
        int size = 0;
        size += CacheSizes.sizeOfObject();                           // overhead of object
        size += CacheSizes.sizeOfCollection(rosterItems.values());   // roster item cache
        size += CacheSizes.sizeOfString(username);                   // username
        return size;
    }
}