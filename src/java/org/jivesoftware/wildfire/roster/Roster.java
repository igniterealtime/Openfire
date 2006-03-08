/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.roster;

import org.jivesoftware.database.JiveID;
import org.jivesoftware.util.CacheSizes;
import org.jivesoftware.util.Cacheable;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.*;
import org.jivesoftware.wildfire.auth.UnauthorizedException;
import org.jivesoftware.wildfire.group.Group;
import org.jivesoftware.wildfire.group.GroupManager;
import org.jivesoftware.wildfire.privacy.PrivacyList;
import org.jivesoftware.wildfire.privacy.PrivacyListManager;
import org.jivesoftware.wildfire.user.*;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

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
public class Roster implements Cacheable {

    /**
     * <p>Roster item cache - table: key jabberid string; value roster item.</p>
     */
    protected ConcurrentHashMap<String, RosterItem> rosterItems = new ConcurrentHashMap<String, RosterItem>();

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
     * <p>Create a roster for the given user, pulling the existing roster items
     * out of the backend storage provider. The roster will also include items that
     * belong to the user's shared groups.</p>
     *
     * <p>RosterItems that ONLY belong to shared groups won't be persistent unless the user
     * explicitly subscribes to the contact's presence, renames the contact in his roster or adds
     * the item to a personal group.</p>
     *
     * @param username The username of the user that owns this roster
     */
    public Roster(String username) {
        presenceManager = XMPPServer.getInstance().getPresenceManager();
        rosterManager = XMPPServer.getInstance().getRosterManager();
        sessionManager = SessionManager.getInstance();
        this.username = username;

        // Get the shared groups of this user
        Collection<Group> sharedGroups = null;
        Collection<Group> userGroups = null;
        try {
            User rosterUser = UserManager.getInstance().getUser(getUsername());
            sharedGroups = rosterManager.getSharedGroups(rosterUser);
            userGroups = GroupManager.getInstance().getGroups(getUserJID());
        }
        catch (UserNotFoundException e) {
            sharedGroups = new ArrayList<Group>();
        }

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
                String nickname = UserNameManager.getUserName(jid);
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
                rosterItems.put(item.getJid().toBareJID(), item);
            }
            catch (UserNotFoundException e) {
                Log.error("Groups (" + sharedUsers.get(jid) + ") include non-existent username (" +
                        jid.getNode() +
                        ")");
            }
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
    public RosterItem createRosterItem(JID user) throws UserAlreadyExistsException,
            SharedGroupException {
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
            throws UserAlreadyExistsException, SharedGroupException {
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
            throws UserAlreadyExistsException, SharedGroupException {
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
            throws UserAlreadyExistsException, SharedGroupException {
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
            throws UserAlreadyExistsException, SharedGroupException {
        if (groups != null && !groups.isEmpty()) {
            // Raise an error if the groups the item belongs to include a shared group
            Collection<Group> sharedGroups = GroupManager.getInstance().getGroups();
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
        // If the item only had shared groups before this update then make it persistent
        if (item.isShared() && item.getID() == 0) {
            try {
                rosterItemProvider.createItem(username, item);
            }
            catch (UserAlreadyExistsException e) {
                // Do nothing. We shouldn't be here.
            }
        }
        else {
            // Update the backend data store
            rosterItemProvider.updateItem(username, item);
        }
        // broadcast roster update
        //if (item.getAskStatus() != RosterItem.ASK_NONE) {
            broadcast(item, true);
        //}
        /*if (item.getSubStatus() == RosterItem.SUB_BOTH || item.getSubStatus() == RosterItem.SUB_TO) {
            probePresence(item.getJid());
        }*/
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
        if (doChecking && itemToRemove != null && itemToRemove.isShared()) {
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
            }

            return item;
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
            //if (item.getAskStatus() != RosterItem.ASK_NONE) {
                roster.addItem(item.getJid(), item.getNickname(), ask, sub, groups);
            //}
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
            routingTable = XMPPServer.getInstance().getRoutingTable();
        }
        if (routingTable == null) {
            return;
        }
        // Get the privacy list of this user
        PrivacyList list = null;
        if (packet.getFrom() != null) {
            // Try to use the active list of the session. If none was found then try to use
            // the default privacy list of the session
            ClientSession session = sessionManager.getSession(packet.getFrom());
            if (session != null) {
                list = session.getActiveList();
                list = list == null ? session.getDefaultList() : list;
            }
        }
        if (list == null) {
            // No privacy list was found (based on the session) so check if there is a default list
            list = PrivacyListManager.getInstance().getDefaultPrivacyList(username);
        }
        for (RosterItem item : rosterItems.values()) {
            if (item.getSubStatus() == RosterItem.SUB_BOTH
                    || item.getSubStatus() == RosterItem.SUB_FROM) {
                JID searchNode = new JID(item.getJid().getNode(), item.getJid().getDomain(), null);
                Iterator sessions = routingTable.getRoutes(searchNode);
                packet.setTo(item.getJid());
                if (list != null && list.shouldBlockPacket(packet)) {
                    // Outgoing presence notifications are blocked for this contact
                    continue;
                }
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
                if (!isRosterItem(jid) && !userJID.equals(jid)) {
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
    void broadcast(RosterItem item, boolean optimize) {
        // Do not broadcast items with status FROM that exist only because of shared groups
        if (optimize && item.isOnlyShared() && item.getSubStatus() == RosterItem.SUB_FROM) {
            return;
        }
        // Set the groups to broadcast (include personal and shared groups)
        List<String> groups = new ArrayList<String>(item.getGroups());
        for (Group sharedGroup : item.getSharedGroups()) {
            groups.add(sharedGroup.getProperties().get("sharedRoster.displayName"));
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

        // Brodcast to all the user resources of the updated roster item
        broadcast(item, true);
        // Probe the presence of the new group user
        if (item.getSubStatus() == RosterItem.SUB_BOTH || item.getSubStatus() == RosterItem.SUB_TO) {
            probePresence(item.getJid());
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
        // Brodcast to all the user resources of the updated roster item
        broadcast(item, true);
        // Probe the presence of the new group user
        if (item.getSubStatus() == RosterItem.SUB_BOTH || item.getSubStatus() == RosterItem.SUB_TO) {
            probePresence(item.getJid());
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
                if (!item.getSharedGroups().contains(sharedGroup)) {
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

    void deleteSharedUser(JID deletedUser, Collection<Group> groups, Group deletedGroup) {
        try {
            // Get the RosterItem for the *local* user to remove
            RosterItem item = getRosterItem(deletedUser);
            int groupSize = item.getSharedGroups().size() + item.getInvisibleSharedGroups().size();
            if (item.isOnlyShared() && groupSize == 1 &&
                    // Do not delete the item if deletedUser belongs to a public group since the
                    // subcription status will change
                    !(deletedGroup.isUser(deletedUser) &&
                    rosterManager.isGroupPublic(deletedGroup))) {
                // Delete the roster item from the roster since it exists only because of this
                // group which is being removed
                deleteRosterItem(deletedUser, false);
            }
            else {
                // Remove the shared group from the item if deletedUser does not belong to a
                // public group
                if (!(deletedGroup.isUser(deletedUser) &&
                        rosterManager.isGroupPublic(deletedGroup))) {
                    item.removeSharedGroup(deletedGroup);
                }
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
                            userGroups = GroupManager.getInstance().getGroups(getUserJID());
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
            RosterItem item = null;
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
}