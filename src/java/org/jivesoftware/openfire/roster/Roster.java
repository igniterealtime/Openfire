/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.roster;

import org.jivesoftware.database.JiveID;
import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.privacy.PrivacyList;
import org.jivesoftware.openfire.privacy.PrivacyListManager;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserNameManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.cache.CacheSizes;
import org.jivesoftware.util.cache.Cacheable;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
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
@JiveID(JiveConstants.ROSTER)
public class Roster implements Cacheable, Externalizable {

    /**
     * Roster item cache - table: key jabberid string; value roster item.
     */
    protected ConcurrentHashMap<String, RosterItem> rosterItems = new ConcurrentHashMap<String, RosterItem>();
    /**
     * Contacts with subscription FROM that only exist due to shared groups
     * key: jabberid string; value: groups why the implicit roster item exists (aka invisibleSharedGroups).
     */
    protected ConcurrentHashMap<String, Set<String>> implicitFrom = new ConcurrentHashMap<String, Set<String>>();

    private RosterItemProvider rosterItemProvider;
    private String username;
    private SessionManager sessionManager;
    private XMPPServer server = XMPPServer.getInstance();
    private RoutingTable routingTable;
    private PresenceManager presenceManager;
    /**
     * Note: Used only for shared groups logic.
     */
    private RosterManager rosterManager;


    /**
     * Constructor added for Externalizable. Do not use this constructor.
     */
    public Roster() {
    }

    /**
     * Create a roster for the given user, pulling the existing roster items
     * out of the backend storage provider. The roster will also include items that
     * belong to the user's shared groups.<p>
     *
     * RosterItems that ONLY belong to shared groups won't be persistent unless the user
     * explicitly subscribes to the contact's presence, renames the contact in his roster or adds
     * the item to a personal group.<p>
     *
     * This constructor is not public and instead you should use
     * {@link org.jivesoftware.openfire.roster.RosterManager#getRoster(String)}.
     *
     * @param username The username of the user that owns this roster
     */
    Roster(String username) {
        presenceManager = XMPPServer.getInstance().getPresenceManager();
        rosterManager = XMPPServer.getInstance().getRosterManager();
        sessionManager = SessionManager.getInstance();
        routingTable = XMPPServer.getInstance().getRoutingTable();
        this.username = username;

        // Get the shared groups of this user
        Collection<Group> sharedGroups = rosterManager.getSharedGroups(username);
        Collection<Group> userGroups = GroupManager.getInstance().getGroups(getUserJID());

        // Add RosterItems that belong to the personal roster
        rosterItemProvider =  RosterItemProvider.getInstance();
        Iterator items = rosterItemProvider.getItems(username);
        while (items.hasNext()) {
            RosterItem item = (RosterItem)items.next();
            // Check if the item (i.e. contact) belongs to a shared group of the user. Add the
            // shared group (if any) to this item
            for (Group group : sharedGroups) {
                if (group.isUser(item.getJid())) {
                    // TODO Group name conflicts are not being considered (do we need this?)
                    item.addSharedGroup(group);
                    item.setSubStatus(RosterItem.SUB_BOTH);
                }
            }
            rosterItems.put(item.getJid().toBareJID(), item);
        }
        // Add RosterItems that belong only to shared groups
        Map<JID,List<Group>> sharedUsers = getSharedUsers(sharedGroups);
        for (JID jid : sharedUsers.keySet()) {
            try {
                Collection<Group> itemGroups = new ArrayList<Group>();
                String nickname = "";
                RosterItem item = new RosterItem(jid, RosterItem.SUB_TO, RosterItem.ASK_NONE,
                        RosterItem.RECV_NONE, nickname , null);
                // Add the shared groups to the new roster item
                for (Group group : sharedUsers.get(jid)) {
                    if (group.isUser(jid)) {
                        item.addSharedGroup(group);
                        itemGroups.add(group);
                    }
                    else {
                        item.addInvisibleSharedGroup(group);
                    }
                }
                // Set subscription type to BOTH if the roster user belongs to a shared group
                // that is mutually visible with a shared group of the new roster item
                if (rosterManager.hasMutualVisibility(username, userGroups, jid, itemGroups)) {
                    item.setSubStatus(RosterItem.SUB_BOTH);
                }
                else {
                    // Set subscription type to FROM if the contact does not belong to any of
                    // the associated shared groups
                    boolean belongsToGroup = false;
                    for (Group group : sharedUsers.get(jid)) {
                        if (group.isUser(jid)) {
                            belongsToGroup = true;
                        }
                    }
                    if (!belongsToGroup) {
                        item.setSubStatus(RosterItem.SUB_FROM);
                    }
                }
                // Set nickname and store in memory only if subscription type is not FROM.
                // Roster items with subscription type FROM that exist only because of shared
                // groups will be recreated on demand in #getRosterItem(JID) and #isRosterItem()
                // but will never be stored in memory nor in the database. This is an important
                // optimization to reduce objects in memory and avoid loading users in memory
                // to get their nicknames that will never be shown
                if (item.getSubStatus() != RosterItem.SUB_FROM) {
                    item.setNickname(UserNameManager.getUserName(jid));
                    rosterItems.put(item.getJid().toBareJID(), item);
                }
                else {
                    // Cache information about shared contacts with subscription status FROM
                    implicitFrom
                            .put(item.getJid().toBareJID(), item.getInvisibleSharedGroupsNames());
                }
            }
            catch (UserNotFoundException e) {
                Log.error("Groups (" + sharedUsers.get(jid) + ") include non-existent username (" +
                        jid.getNode() +
                        ")");
            }
        }
        // Fire event indicating that a roster has just been loaded
        RosterEventDispatcher.rosterLoaded(this);
    }

    /**
     * Returns true if the specified user is a member of the roster, false otherwise.
     *
     * @param user the user object to check.
     * @return true if the specified user is a member of the roster, false otherwise.
     */
    public boolean isRosterItem(JID user) {
        // Optimization: Check if the contact has a FROM subscription due to shared groups
        // (only when not present in the rosterItems collection)
        return rosterItems.containsKey(user.toBareJID()) || getImplicitRosterItem(user) != null;
    }

    /**
     * Returns a collection of users in this roster.<p>
     *
     * Note: Roster items with subscription type FROM that exist only because of shared groups
     * are not going to be returned.
     *
     * @return a collection of users in this roster.
     */
    public Collection<RosterItem> getRosterItems() {
        return Collections.unmodifiableCollection(rosterItems.values());
    }

    /**
     * Returns the roster item that is associated with the specified JID. If no roster item
     * was found then a UserNotFoundException will be thrown.
     *
     * @param user the XMPPAddress for the roster item to retrieve
     * @return The roster item associated with the user XMPPAddress.
     * @throws UserNotFoundException if no roster item was found for the specified JID.
     */
    public RosterItem getRosterItem(JID user) throws UserNotFoundException {
        RosterItem item = rosterItems.get(user.toBareJID());
        if (item == null) {
            // Optimization: Check if the contact has a FROM subscription due to shared groups
            item = getImplicitRosterItem(user);
            if (item == null) {
                throw new UserNotFoundException(user.toBareJID());
            }
        }
        return item;
    }

    /**
     * Returns a roster item if the specified user has a subscription of type FROM to this
     * user and the susbcription only exists due to some shared groups or otherwise
     * <tt>null</tt>. This method assumes that this user does not have a subscription to
     * the contact. In other words, this method will not check if there should be a subscription
     * of type TO ot BOTH.
     *
     * @param user the contact to check if he is subscribed to the presence of this user.
     * @return a roster item if the specified user has a subscription of type FROM to this
     *         user and the susbcription only exists due to some shared groups or otherwise null.
     */
    private RosterItem getImplicitRosterItem(JID user) {
        Set<String> invisibleSharedGroups = implicitFrom.get(user.toBareJID());
        if (invisibleSharedGroups != null) {
            RosterItem rosterItem = new RosterItem(user, RosterItem.SUB_FROM, RosterItem.ASK_NONE,
                    RosterItem.RECV_NONE, "", null);
            rosterItem.setInvisibleSharedGroupsNames(invisibleSharedGroups);
            return rosterItem;
        }
        return null;
    }

    /**
     * Create a new item to the roster. Roster items may not be created that contain the same user
     * address as an existing item.
     *
     * @param user       The item to add to the roster.
     * @param push       True if the new item must be pushed to the user.
     * @param persistent True if the new roster item should be persisted to the DB.
     */
    public RosterItem createRosterItem(JID user, boolean push, boolean persistent)
            throws UserAlreadyExistsException, SharedGroupException {
        return createRosterItem(user, null, null, push, persistent);
    }

    /**
     * Create a new item to the roster. Roster items may not be created that contain the same user
     * address as an existing item.
     *
     * @param user       The item to add to the roster.
     * @param nickname   The nickname for the roster entry (can be null).
     * @param push       True if the new item must be push to the user.
     * @param persistent True if the new roster item should be persisted to the DB.
     * @param groups   The list of groups to assign this roster item to (can be null)
     */
    public RosterItem createRosterItem(JID user, String nickname, List<String> groups, boolean push,
                                       boolean persistent)
            throws UserAlreadyExistsException, SharedGroupException {
        return provideRosterItem(user, nickname, groups, push, persistent);
    }

    /**
     * Create a new item to the roster based as a copy of the given item.
     * Roster items may not be created that contain the same user address
     * as an existing item in the roster.
     *
     * @param item the item to copy and add to the roster.
     */
    public void createRosterItem(org.xmpp.packet.Roster.Item item)
            throws UserAlreadyExistsException, SharedGroupException {
        provideRosterItem(item.getJID(), item.getName(), new ArrayList<String>(item.getGroups()), true, true);
    }

    /**
     * Generate a new RosterItem for use with createRosterItem.
     *
     * @param user       The roster jid address to create the roster item for.
     * @param nickname   The nickname to assign the item (or null for none).
     * @param groups     The groups the item belongs to (or null for none).
     * @param push       True if the new item must be push to the user.
     * @param persistent True if the new roster item should be persisted to the DB.
     * @return The newly created roster items ready to be stored by the Roster item's hash table
     */
    protected RosterItem provideRosterItem(JID user, String nickname, List<String> groups,
                                           boolean push, boolean persistent)
            throws UserAlreadyExistsException, SharedGroupException {
        if (groups != null && !groups.isEmpty()) {
            // Raise an error if the groups the item belongs to include a shared group
            Collection<Group> sharedGroups = GroupManager.getInstance().getSharedGroups();
            for (String group : groups) {
                for (Group sharedGroup : sharedGroups) {
                    if (group.equals(sharedGroup.getProperties().get("sharedRoster.displayName"))) {
                        throw new SharedGroupException("Cannot add an item to a shared group");
                    }
                }
            }
        }
        org.xmpp.packet.Roster roster = new org.xmpp.packet.Roster();
        roster.setType(IQ.Type.set);
        org.xmpp.packet.Roster.Item item = roster.addItem(user, nickname, null,
                org.xmpp.packet.Roster.Subscription.none, groups);

        RosterItem rosterItem = new RosterItem(item);
        // Fire event indicating that a roster item is about to be added
        persistent = RosterEventDispatcher.addingContact(this, rosterItem, persistent);

        // Check if we need to make the new roster item persistent
        if (persistent) {
            rosterItemProvider.createItem(username, rosterItem);
        }

        if (push) {
            // Broadcast the roster push to the user
            broadcast(roster);
        }

        rosterItems.put(user.toBareJID(), rosterItem);

        // Fire event indicating that a roster item has been added
        RosterEventDispatcher.contactAdded(this, rosterItem);

        return rosterItem;
    }

    /**
     * Update an item that is already in the roster.
     *
     * @param item the item to update in the roster.
     * @throws UserNotFoundException If the roster item for the given user doesn't already exist
     */
    public void updateRosterItem(RosterItem item) throws UserNotFoundException {
        // Check if we need to convert an implicit roster item into an explicit one
        if (implicitFrom.remove(item.getJid().toBareJID()) != null) {
            // Ensure that the item is an explicit roster item
            rosterItems.put(item.getJid().toBareJID(), item);
            // Fire event indicating that a roster item has been updated
            RosterEventDispatcher.contactUpdated(this, item);
        }
        if (rosterItems.putIfAbsent(item.getJid().toBareJID(), item) == null) {
            rosterItems.remove(item.getJid().toBareJID());
            if (item.getSubStatus() != RosterItem.SUB_NONE) {
                throw new UserNotFoundException(item.getJid().toBareJID());
            }
            return;
        }
        // Check if the item is not persistent
        if (item.getID() == 0) {
            // Make the item persistent if a new nickname has been set for a shared contact
            if (item.isShared()) {
                // Do nothing if item is only shared and it is using the default user name
                if (item.isOnlyShared()) {
                    String defaultContactName;
                    try {
                        defaultContactName = UserNameManager.getUserName(item.getJid());
                    }
                    catch (UserNotFoundException e) {
                        // Cannot update a roster item for a local user that does not exist
                        defaultContactName = item.getNickname();
                    }
                    if (defaultContactName.equals(item.getNickname())) {
                        return;
                    }
                }
                try {
                    rosterItemProvider.createItem(username, item);
                }
                catch (UserAlreadyExistsException e) {
                    // Do nothing. We shouldn't be here.
                }
            }
            else {
                // Item is not persistent and it does not belong to a shared contact so do nothing
            }
        }
        else {
            // Update the backend data store
            rosterItemProvider.updateItem(username, item);
        }
        // broadcast roster update
        // Do not push items with a state of "None + Pending In"
        if (item.getSubStatus() != RosterItem.SUB_NONE ||
                item.getRecvStatus() != RosterItem.RECV_SUBSCRIBE) {
            broadcast(item, true);
        }
        /*if (item.getSubStatus() == RosterItem.SUB_BOTH || item.getSubStatus() == RosterItem.SUB_TO) {
            probePresence(item.getJid());
        }*/
        // Fire event indicating that a roster item has been updated
        RosterEventDispatcher.contactUpdated(this, item);
    }

    /**
     * Remove a user from the roster.
     *
     * @param user the user to remove from the roster.
     * @param doChecking flag that indicates if checkings should be done before deleting the user.
     * @return The roster item being removed or null if none existed
     * @throws SharedGroupException if the user to remove belongs to a shared group
     */
    public RosterItem deleteRosterItem(JID user, boolean doChecking) throws SharedGroupException {
        // Answer an error if user (i.e. contact) to delete belongs to a shared group
        RosterItem itemToRemove = rosterItems.get(user.toBareJID());
        if (doChecking && itemToRemove != null && !itemToRemove.getSharedGroups().isEmpty()) {
            throw new SharedGroupException("Cannot remove contact that belongs to a shared group");
        }

        if (itemToRemove != null) {
            RosterItem.SubType subType = itemToRemove.getSubStatus();

            // Cancel any existing presence subscription between the user and the contact
            if (subType == RosterItem.SUB_TO || subType == RosterItem.SUB_BOTH) {
                Presence presence = new Presence();
                presence.setFrom(server.createJID(username, null));
                presence.setTo(itemToRemove.getJid());
                presence.setType(Presence.Type.unsubscribe);
                server.getPacketRouter().route(presence);
            }

            // cancel any existing presence subscription between the contact and the user
            if (subType == RosterItem.SUB_FROM || subType == RosterItem.SUB_BOTH) {
                Presence presence = new Presence();
                presence.setFrom(server.createJID(username, null));
                presence.setTo(itemToRemove.getJid());
                presence.setType(Presence.Type.unsubscribed);
                server.getPacketRouter().route(presence);
            }

            // If removing the user was successful, remove the user from the subscriber list:
            RosterItem item = rosterItems.remove(user.toBareJID());

            if (item != null) {
                // Delete the item from the provider if the item is persistent. RosteItems that only
                // belong to shared groups won't be persistent
                if (item.getID() > 0) {
                    // If removing the user was successful, remove the user from the backend store
                    rosterItemProvider.deleteItem(username, item.getID());
                }

                // Broadcast the update to the user
                org.xmpp.packet.Roster roster = new org.xmpp.packet.Roster();
                roster.setType(IQ.Type.set);
                roster.addItem(user, org.xmpp.packet.Roster.Subscription.remove);
                broadcast(roster);
                // Fire event indicating that a roster item has been deleted
                RosterEventDispatcher.contactDeleted(this, item);
            }

            return item;
        }
        else {
            // Verify if the item being removed is an implicit roster item
            // that only exists due to some shared group
            RosterItem item = getImplicitRosterItem(user);
            if (item != null) {
                implicitFrom.remove(user.toBareJID());
                // If the contact being removed is not a local user then ACK unsubscription
                if (!server.isLocal(user)) {
                    Presence presence = new Presence();
                    presence.setFrom(server.createJID(username, null));
                    presence.setTo(user);
                    presence.setType(Presence.Type.unsubscribed);
                    server.getPacketRouter().route(presence);
                }
                // Fire event indicating that a roster item has been deleted
                RosterEventDispatcher.contactDeleted(this, item);
            }
        }

        return null;
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

        // Add the roster items (includes the personal roster and shared groups) to the answer
        for (RosterItem item : rosterItems.values()) {
            // Do not include items with status FROM that exist only because of shared groups
            if (item.isOnlyShared() && item.getSubStatus() == RosterItem.SUB_FROM) {
                continue;
            }
            org.xmpp.packet.Roster.Ask ask = getAskStatus(item.getAskStatus());
            org.xmpp.packet.Roster.Subscription sub = org.xmpp.packet.Roster.Subscription.valueOf(item.getSubStatus()
                    .getName());
            // Set the groups to broadcast (include personal and shared groups)
            List<String> groups = new ArrayList<String>(item.getGroups());
            if (groups.contains(null)) {
                Log.warn("A group is null in roster item: " + item.getJid() + " of user: " +
                        getUsername());
            }
            for (Group sharedGroup : item.getSharedGroups()) {
                String displayName = sharedGroup.getProperties().get("sharedRoster.displayName");
                if (displayName != null) {
                    groups.add(displayName);
                }
                else {
                    // Do not add the shared group if it does not have a displayName. 
                    Log.warn("Found shared group: " + sharedGroup.getName() +
                            " with no displayName");
                }
            }
            // Do not push items with a state of "None + Pending In"
            if (item.getSubStatus() != RosterItem.SUB_NONE ||
                    item.getRecvStatus() != RosterItem.RECV_SUBSCRIBE) {
                roster.addItem(item.getJid(), item.getNickname(), ask, sub, groups);
            }
        }
        return roster;
    }

    private org.xmpp.packet.Roster.Ask getAskStatus(RosterItem.AskType askType) {
        if (askType == null || "".equals(askType.getName())) {
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
            return;
        }
        // Get the privacy list of this user
        PrivacyList list = null;
        JID from = packet.getFrom();
        if (from != null) {
            // Try to use the active list of the session. If none was found then try to use
            // the default privacy list of the session
            ClientSession session = sessionManager.getSession(from);
            if (session != null) {
                list = session.getActiveList();
                list = list == null ? session.getDefaultList() : list;
            }
        }
        if (list == null) {
            // No privacy list was found (based on the session) so check if there is a default list
            list = PrivacyListManager.getInstance().getDefaultPrivacyList(username);
        }
        // Broadcast presence to subscribed entities
        for (RosterItem item : rosterItems.values()) {
            if (item.getSubStatus() == RosterItem.SUB_BOTH
                    || item.getSubStatus() == RosterItem.SUB_FROM) {
                packet.setTo(item.getJid());
                if (list != null && list.shouldBlockPacket(packet)) {
                    // Outgoing presence notifications are blocked for this contact
                    continue;
                }
                JID searchNode = new JID(item.getJid().getNode(), item.getJid().getDomain(), null);
                for (ChannelHandler session : routingTable.getRoutes(searchNode)) {
                    try {
                        session.process(packet);
                    }
                    catch (Exception e) {
                        // Theoretically only happens if session has been closed.
                        Log.debug(e);
                    }
                }
            }
        }
        // Broadcast presence to shared contacts whose subscription status is FROM
        for (String contact : implicitFrom.keySet()) {
            packet.setTo(contact);
            if (list != null && list.shouldBlockPacket(packet)) {
                // Outgoing presence notifications are blocked for this contact
                continue;
            }
            for (ChannelHandler session : routingTable.getRoutes(new JID(contact))) {
                try {
                    session.process(packet);
                }
                catch (Exception e) {
                    // Theoretically only happens if session has been closed.
                    Log.debug(e);
                }
            }
        }
        if (from != null) {
            // Broadcast presence to other user's resources
            sessionManager.broadcastPresenceToOtherResources(from, packet);
        }
    }

    /**
     * Returns the list of users that belong ONLY to a shared group of this user. If the contact
     * belongs to the personal roster and a shared group then it wont' be included in the answer.
     *
     * @param sharedGroups the shared groups of this user.
     * @return the list of users that belong ONLY to a shared group of this user.
     */
    private Map<JID,List<Group>> getSharedUsers(Collection<Group> sharedGroups) {
        // Get the users to process from the shared groups. Users that belong to different groups
        // will have one entry in the map associated with all the groups
        Map<JID,List<Group>> sharedGroupUsers = new HashMap<JID,List<Group>>();
        for (Group group : sharedGroups) {
            // Get all the users that should be in this roster
            Collection<JID> users = rosterManager.getSharedUsersForRoster(group, this);
            // Add the users of the group to the general list of users to process
            JID userJID = getUserJID();
            for (JID jid : users) {
                // Add the user to the answer if the user doesn't belong to the personal roster
                // (since we have already added the user to the answer)
                boolean isRosterItem = rosterItems.containsKey(jid.toBareJID());
                if (!isRosterItem && !userJID.equals(jid)) {
                    List<Group> groups = sharedGroupUsers.get(jid);
                    if (groups == null) {
                        groups = new ArrayList<Group>();
                        sharedGroupUsers.put(jid, groups);
                    }
                    groups.add(group);
                }
            }
        }
        return sharedGroupUsers;
    }

    private void broadcast(org.xmpp.packet.Roster roster) {
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

    /**
     * Broadcasts the RosterItem to all the connected resources of this user. Due to performance
     * optimizations and due to some clients errors that are showing items with subscription status
     * FROM we added a flag that indicates if a roster items that exists only because of a shared
     * group with subscription status FROM will not be sent.
     *
     * @param item     the item to broadcast.
     * @param optimize true indicates that items that exists only because of a shared
     *                 group with subscription status FROM will not be sent
     */
    public void broadcast(RosterItem item, boolean optimize) {
        // Do not broadcast items with status FROM that exist only because of shared groups
        if (optimize && item.isOnlyShared() && item.getSubStatus() == RosterItem.SUB_FROM) {
            return;
        }
        // Set the groups to broadcast (include personal and shared groups)
        List<String> groups = new ArrayList<String>(item.getGroups());
        for (Group sharedGroup : item.getSharedGroups()) {
            String displayName = sharedGroup.getProperties().get("sharedRoster.displayName");
            if (displayName != null) {
                groups.add(displayName);
            }
        }

        org.xmpp.packet.Roster roster = new org.xmpp.packet.Roster();
        roster.setType(IQ.Type.set);
        roster.addItem(item.getJid(), item.getNickname(),
                getAskStatus(item.getAskStatus()),
                org.xmpp.packet.Roster.Subscription.valueOf(item.getSubStatus().getName()),
                groups);
        broadcast(roster);
    }

    /**
     * Sends a presence probe to the probee for each connected resource of this user.
     */
    private void probePresence(JID probee) {
        for (ClientSession session : sessionManager.getSessions(username)) {
            presenceManager.probePresence(session.getAddress(), probee);
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

    /**
     * Update the roster since a group user has been added to a shared group. Create a new
     * RosterItem if the there doesn't exist an item for the added user. The new RosterItem won't be
     * saved to the backend store unless the user explicitly subscribes to the contact's presence,
     * renames the contact in his roster or adds the item to a personal group. Otherwise the shared
     * group will be added to the shared groups lists. In any case an update broadcast will be sent
     * to all the users logged resources.
     *
     * @param group the shared group where the user was added.
     * @param addedUser the contact to update in the roster.
     */
    void addSharedUser(Group group, JID addedUser) {
        boolean newItem = false;
        RosterItem item = null;
        try {
            // Get the RosterItem for the *local* user to add
            item = getRosterItem(addedUser);
            // Do nothing if the item already includes the shared group
            if (item.getSharedGroups().contains(group)) {
                return;
            }
            newItem = false;
        }
        catch (UserNotFoundException e) {
            try {
                // Create a new RosterItem for this new user
                String nickname = UserNameManager.getUserName(addedUser);
                item =
                        new RosterItem(addedUser, RosterItem.SUB_BOTH, RosterItem.ASK_NONE,
                                RosterItem.RECV_NONE, nickname, null);
                // Add the new item to the list of items
                rosterItems.put(item.getJid().toBareJID(), item);
                newItem = true;
            }
            catch (UserNotFoundException ex) {
                Log.error("Group (" + group.getName() + ") includes non-existent username (" +
                        addedUser +
                        ")");
            }
        }

        // If an item already exists then take note of the old subscription status
        RosterItem.SubType prevSubscription = null;
        if (!newItem) {
            prevSubscription = item.getSubStatus();
        }

        // Update the subscription of the item **based on the item groups**
        Collection<Group> userGroups = GroupManager.getInstance().getGroups(getUserJID());
        Collection<Group> sharedGroups = new ArrayList<Group>();
        sharedGroups.addAll(item.getSharedGroups());
        // Add the new group to the list of groups to check
        sharedGroups.add(group);
        // Set subscription type to BOTH if the roster user belongs to a shared group
        // that is mutually visible with a shared group of the new roster item
        if (rosterManager.hasMutualVisibility(getUsername(), userGroups, addedUser, sharedGroups)) {
            item.setSubStatus(RosterItem.SUB_BOTH);
        }
        // Update the subscription status depending on the group membership of the new
        // user and this user
        else if (group.isUser(addedUser) && !group.isUser(getUsername())) {
            item.setSubStatus(RosterItem.SUB_TO);
        }
        else if (!group.isUser(addedUser) && group.isUser(getUsername())) {
            item.setSubStatus(RosterItem.SUB_FROM);
        }

        // Add the shared group to the list of shared groups
        if (item.getSubStatus() != RosterItem.SUB_FROM) {
            item.addSharedGroup(group);
        }
        else {
            item.addInvisibleSharedGroup(group);
        }

        // If the item already exists then check if the subscription status should be
        // changed to BOTH based on the old and new subscription status
        if (prevSubscription != null) {
            if (prevSubscription == RosterItem.SUB_TO &&
                    item.getSubStatus() == RosterItem.SUB_FROM) {
                item.setSubStatus(RosterItem.SUB_BOTH);
            }
            else if (prevSubscription == RosterItem.SUB_FROM &&
                    item.getSubStatus() == RosterItem.SUB_TO) {
                item.setSubStatus(RosterItem.SUB_BOTH);
            }
        }

        // Optimization: Check if we do not need to keep the item in memory
        if (item.isOnlyShared() && item.getSubStatus() == RosterItem.SUB_FROM) {
            // Remove from memory and do nothing else
            rosterItems.remove(item.getJid().toBareJID());
            // Cache information about shared contacts with subscription status FROM
            implicitFrom.put(item.getJid().toBareJID(), item.getInvisibleSharedGroupsNames());
        }
        else {
            // Remove from list of shared contacts with status FROM (if any)
            implicitFrom.remove(item.getJid().toBareJID());
            // Ensure that the item is an explicit roster item
            rosterItems.put(item.getJid().toBareJID(), item);
            // Brodcast to all the user resources of the updated roster item
            broadcast(item, true);
            // Probe the presence of the new group user
            if (item.getSubStatus() == RosterItem.SUB_BOTH ||
                    item.getSubStatus() == RosterItem.SUB_TO) {
                probePresence(item.getJid());
            }
        }
        if (newItem) {
            // Fire event indicating that a roster item has been added
            RosterEventDispatcher.contactAdded(this, item);
        }
        else {
            // Fire event indicating that a roster item has been updated
            RosterEventDispatcher.contactUpdated(this, item);
        }
    }

    /**
     * Adds a new contact that belongs to a certain list of groups to the roster. Depending on
     * the contact's groups and this user's groups, the presence subscription of the roster item may
     * vary.
     *
     * @param addedUser the new contact to add to the roster
     * @param groups the groups where the contact is a member
     */
    void addSharedUser(JID addedUser, Collection<Group> groups, Group addedGroup) {
        boolean newItem = false;
        RosterItem item = null;
        try {
            // Get the RosterItem for the *local* user to add
            item = getRosterItem(addedUser);
            newItem = false;
        }
        catch (UserNotFoundException e) {
            try {
                // Create a new RosterItem for this new user
                String nickname = UserNameManager.getUserName(addedUser);
                item =
                        new RosterItem(addedUser, RosterItem.SUB_BOTH, RosterItem.ASK_NONE,
                                RosterItem.RECV_NONE, nickname, null);
                // Add the new item to the list of items
                rosterItems.put(item.getJid().toBareJID(), item);
                newItem = true;
            }
            catch (UserNotFoundException ex) {
                Log.error("Couldn't find a user with username (" + addedUser + ")");
            }
        }
        // Update the subscription of the item **based on the item groups**
        Collection<Group> userGroups = GroupManager.getInstance().getGroups(getUserJID());
        // Set subscription type to BOTH if the roster user belongs to a shared group
        // that is mutually visible with a shared group of the new roster item
        if (rosterManager.hasMutualVisibility(getUsername(), userGroups, addedUser, groups)) {
            item.setSubStatus(RosterItem.SUB_BOTH);
            for (Group group : groups) {
                if (rosterManager.isGroupVisible(group, getUserJID())) {
                    // Add the shared group to the list of shared groups
                    item.addSharedGroup(group);
                }
            }
            // Add to the item the groups of this user that generated a FROM subscription
            // Note: This FROM subscription is overridden by the BOTH subscription but in
            // fact there is a TO-FROM relation between these two users that ends up in a
            // BOTH subscription
            for (Group group : userGroups) {
                if (!group.isUser(addedUser) && rosterManager.isGroupVisible(group, addedUser)) {
                    // Add the shared group to the list of invisible shared groups
                    item.addInvisibleSharedGroup(group);
                }
            }
        }
        else {
            // If an item already exists then take note of the old subscription status
            RosterItem.SubType prevSubscription = null;
            if (!newItem) {
                prevSubscription = item.getSubStatus();
            }

            // Assume by default that the contact has subscribed from the presence of
            // this user
            item.setSubStatus(RosterItem.SUB_FROM);
            // Check if the user may see the new contact in a shared group
            for (Group group : groups) {
                if (rosterManager.isGroupVisible(group, getUserJID())) {
                    // Add the shared group to the list of shared groups
                    item.addSharedGroup(group);
                    item.setSubStatus(RosterItem.SUB_TO);
                }
            }
            if (item.getSubStatus() == RosterItem.SUB_FROM) {
                item.addInvisibleSharedGroup(addedGroup);
            }

            // If the item already exists then check if the subscription status should be
            // changed to BOTH based on the old and new subscription status
            if (prevSubscription != null) {
                if (prevSubscription == RosterItem.SUB_TO &&
                        item.getSubStatus() == RosterItem.SUB_FROM) {
                    item.setSubStatus(RosterItem.SUB_BOTH);
                }
                else if (prevSubscription == RosterItem.SUB_FROM &&
                        item.getSubStatus() == RosterItem.SUB_TO) {
                    item.setSubStatus(RosterItem.SUB_BOTH);
                }
            }
        }
        // Optimization: Check if we do not need to keep the item in memory
        if (item.isOnlyShared() && item.getSubStatus() == RosterItem.SUB_FROM) {
            // Remove from memory and do nothing else
            rosterItems.remove(item.getJid().toBareJID());
            // Cache information about shared contacts with subscription status FROM
            implicitFrom.put(item.getJid().toBareJID(), item.getInvisibleSharedGroupsNames());
        }
        else {
            // Remove from list of shared contacts with status FROM (if any)
            implicitFrom.remove(item.getJid().toBareJID());
            // Ensure that the item is an explicit roster item
            rosterItems.put(item.getJid().toBareJID(), item);
            // Brodcast to all the user resources of the updated roster item
            broadcast(item, true);
            // Probe the presence of the new group user
            if (item.getSubStatus() == RosterItem.SUB_BOTH ||
                    item.getSubStatus() == RosterItem.SUB_TO) {
                probePresence(item.getJid());
            }
        }
        if (newItem) {
            // Fire event indicating that a roster item has been added
            RosterEventDispatcher.contactAdded(this, item);
        }
        else {
            // Fire event indicating that a roster item has been updated
            RosterEventDispatcher.contactUpdated(this, item);
        }
    }

    /**
     * Update the roster since a group user has been deleted from a shared group. If the RosterItem
     * (of the deleted contact) exists only because of of the sahred group then the RosterItem will
     * be deleted physically from the backend store. Otherwise the shared group will be removed from
     * the shared groups lists. In any case an update broadcast will be sent to all the users
     * logged resources.
     *
     * @param sharedGroup the shared group from where the user was deleted.
     * @param deletedUser the contact to update in the roster.
     */
    void deleteSharedUser(Group sharedGroup, JID deletedUser) {
        try {
            // Get the RosterItem for the *local* user to remove
            RosterItem item = getRosterItem(deletedUser);
            int groupSize = item.getSharedGroups().size() + item.getInvisibleSharedGroups().size();
            if (item.isOnlyShared() && groupSize == 1) {
                // Do nothing if the existing shared group is not the sharedGroup to remove
                if (!item.getSharedGroups().contains(sharedGroup) &&
                        !item.getInvisibleSharedGroups().contains(sharedGroup)) {
                    return;
                }
                // Delete the roster item from the roster since it exists only because of this
                // group which is being removed
                deleteRosterItem(deletedUser, false);
            }
            else {
                // Remove the removed shared group from the list of shared groups
                item.removeSharedGroup(sharedGroup);
                // Update the subscription of the item based on the remaining groups
                if (item.isOnlyShared()) {
                    Collection<Group> userGroups =
                            GroupManager.getInstance().getGroups(getUserJID());
                    Collection<Group> sharedGroups = new ArrayList<Group>();
                    sharedGroups.addAll(item.getSharedGroups());
                    // Set subscription type to BOTH if the roster user belongs to a shared group
                    // that is mutually visible with a shared group of the new roster item
                    if (rosterManager.hasMutualVisibility(getUsername(), userGroups, deletedUser,
                            sharedGroups)) {
                        item.setSubStatus(RosterItem.SUB_BOTH);
                    }
                    else if (item.getSharedGroups().isEmpty() &&
                            !item.getInvisibleSharedGroups().isEmpty()) {
                        item.setSubStatus(RosterItem.SUB_FROM);
                    }
                    else {
                        item.setSubStatus(RosterItem.SUB_TO);
                    }
                }
                // Brodcast to all the user resources of the updated roster item
                broadcast(item, false);
            }
        }
        catch (SharedGroupException e) {
            // Do nothing. Checkings are disabled so this exception should never happen.
        }
        catch (UserNotFoundException e) {
            // Do nothing since the contact does not exist in the user's roster. (strange case!)
        }
    }

    void deleteSharedUser(JID deletedUser, Group deletedGroup) {
        try {
            // Get the RosterItem for the *local* user to remove
            RosterItem item = getRosterItem(deletedUser);
            int groupSize = item.getSharedGroups().size() + item.getInvisibleSharedGroups().size();
            if (item.isOnlyShared() && groupSize == 1 &&
                    // Do not delete the item if deletedUser belongs to a public group since the
                    // subcription status will change
                    !(deletedGroup.isUser(deletedUser) &&
                    RosterManager.isPublicSharedGroup(deletedGroup))) {
                // Delete the roster item from the roster since it exists only because of this
                // group which is being removed
                deleteRosterItem(deletedUser, false);
            }
            else {
                // Remove the shared group from the item if deletedUser does not belong to a
                // public group
                if (!(deletedGroup.isUser(deletedUser) &&
                        RosterManager.isPublicSharedGroup(deletedGroup))) {
                    item.removeSharedGroup(deletedGroup);
                }
                // Get the groups of the deleted user
                Collection<Group> groups = GroupManager.getInstance().getGroups(deletedUser);
                // Remove all invalid shared groups from the roster item
                for (Group group : groups) {
                    if (!rosterManager.isGroupVisible(group, getUserJID())) {
                        // Remove the shared group from the list of shared groups
                        item.removeSharedGroup(group);
                    }
                }

                // Update the subscription of the item **based on the item groups**
                if (item.isOnlyShared()) {
                    Collection<Group> userGroups =
                            GroupManager.getInstance().getGroups(getUserJID());
                    // Set subscription type to BOTH if the roster user belongs to a shared group
                    // that is mutually visible with a shared group of the new roster item
                    if (rosterManager
                            .hasMutualVisibility(getUsername(), userGroups, deletedUser, groups)) {
                        item.setSubStatus(RosterItem.SUB_BOTH);
                    }
                    else {
                        // Assume by default that the contact has subscribed from the presence of
                        // this user
                        item.setSubStatus(RosterItem.SUB_FROM);
                        // Check if the user may see the new contact in a shared group
                        for (Group group : groups) {
                            if (rosterManager.isGroupVisible(group, getUserJID())) {
                                item.setSubStatus(RosterItem.SUB_TO);
                            }
                        }
                    }
                }
                // Brodcast to all the user resources of the updated roster item
                broadcast(item, false);
            }
        }
        catch (SharedGroupException e) {
            // Do nothing. Checkings are disabled so this exception should never happen.
        }
        catch (UserNotFoundException e) {
            // Do nothing since the contact does not exist in the user's roster. (strange case!)
        }
    }

    /**
     * A shared group of the user has been renamed. Update the existing roster items with the new
     * name of the shared group and make a roster push for all the available resources.
     *
     * @param users group users of the renamed group.
     */
    void shareGroupRenamed(Collection<JID> users) {
        JID userJID = getUserJID();
        for (JID user : users) {
            if (userJID.equals(user)) {
                continue;
            }
            RosterItem item;
            try {
                // Get the RosterItem for the *local* user to add
                item = getRosterItem(user);
                // Brodcast to all the user resources of the updated roster item
                broadcast(item, true);
            }
            catch (UserNotFoundException e) {
                // Do nothing since the contact does not exist in the user's roster. (strange case!)
            }
        }
    }

    private JID getUserJID() {
        return XMPPServer.getInstance().createJID(getUsername(), null);
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeSafeUTF(out, username);
        ExternalizableUtil.getInstance().writeExternalizableMap(out, rosterItems);
        ExternalizableUtil.getInstance().writeStringsMap(out, implicitFrom);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        presenceManager = XMPPServer.getInstance().getPresenceManager();
        rosterManager = XMPPServer.getInstance().getRosterManager();
        sessionManager = SessionManager.getInstance();
        rosterItemProvider =  RosterItemProvider.getInstance();
        routingTable = XMPPServer.getInstance().getRoutingTable();

        username = ExternalizableUtil.getInstance().readSafeUTF(in);
        ExternalizableUtil.getInstance().readExternalizableMap(in, rosterItems, getClass().getClassLoader());
        ExternalizableUtil.getInstance().readStringsMap(in, implicitFrom);
    }
}