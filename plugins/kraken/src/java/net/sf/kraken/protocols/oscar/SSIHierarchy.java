/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package net.sf.kraken.protocols.oscar;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.kano.joscar.ByteBlock;
import net.kano.joscar.snaccmd.ExtraInfoData;
import net.kano.joscar.snaccmd.ssi.BuddyAuthRequest;
import net.kano.joscar.snaccmd.ssi.CreateItemsCmd;
import net.kano.joscar.snaccmd.ssi.DeleteItemsCmd;
import net.kano.joscar.snaccmd.ssi.ModifyItemsCmd;
import net.kano.joscar.snaccmd.ssi.PostModCmd;
import net.kano.joscar.snaccmd.ssi.PreModCmd;
import net.kano.joscar.snaccmd.ssi.SsiCommand;
import net.kano.joscar.snaccmd.ssi.SsiItem;
import net.kano.joscar.ssiitem.BuddyItem;
import net.kano.joscar.ssiitem.GroupItem;
import net.kano.joscar.ssiitem.IconItem;
import net.kano.joscar.ssiitem.VisibilityItem;
import net.sf.kraken.roster.TransportBuddy;
import net.sf.kraken.roster.TransportBuddyManager;
import net.sf.kraken.type.TransportType;

import org.apache.log4j.Logger;
import org.jivesoftware.util.NotFoundException;
import org.xmpp.packet.JID;

/**
 * A representation of a Server Stored Information hierarchy.
 * 
 * Implementation guide: this class encapsulates all SSI-specific attributes. In
 * particular, no other class outside this one in Kraken's code should have to
 * worry about what ID is valid in what group context. All of that logic must be
 * kept within this class!
 * 
 * @see <a
 *      href="http://code.google.com/p/joscar/wiki/SsiItems">JOscar&nbsp;SSI&nbsp;Items&nbsp;documentation</a>
 */
public class SSIHierarchy {

    private static final Logger Log = Logger.getLogger(SSIHierarchy.class);

    /**
     * Default group name for AIM. This group name should be used only if no
     * group name was provided by the end user, and no existing groups are
     * available.
     */
    private static final String DEFAULT_AIM_GROUP = "Buddies";

    /**
     * Default group name for ICQ. This group name should be used only if no
     * group name was provided by the end user, and no existing groups are
     * available.
     */
    private static final String DEFAULT_ICQ_GROUP = "General";

    /**
     * A reference to the session that instantiated this {@link SSIHierarchy}
     * instance.
     */
    private final OSCARSession parent;

    /**
     * A list of known groups, mapped by their unique group IDs.
     */
    private final Map<Integer, GroupItem> groups = new ConcurrentHashMap<Integer, GroupItem>();

    /**
     * The highest buddy ID in a particular group (mapped by the group ID).
     */
    private final Map<Integer, Integer> highestBuddyIdPerGroup = new ConcurrentHashMap<Integer, Integer>();

    /**
     * The (unique) visibility settings item
     */
    private VisibilityItem visibility;

    /**
     * The (unique) icon item
     */
    private IconItem icon;

    /**
     * Avatar byte data that is likely to be requested by the OSCAR network.
     */
    private byte[] pendingAvatar;

    /**
     * Instantiates a new {@link SSIHierarchy} object, which is linked to a
     * {@link OSCARSession}.
     * 
     * @param parent
     *            The session of this {@link SSIHierarchy} instance.
     */
    public SSIHierarchy(OSCARSession parent) {
        if (parent == null) {
            throw new IllegalArgumentException(
                    "Argument 'parent' cannot be null.");
        }

        this.parent = parent;
        highestBuddyIdPerGroup.put(0, 0); // Main group highest id
    }

    /**
     * Sends data to the network.
     * 
     * @param command
     *            the data to send.
     */
    private void request(SsiCommand command) {
        parent.request(command);
    }

    /**
     * Updates the avatar of the entity owning this instance on the network.
     */
    public void setIcon(final String type, final byte[] data) {
        this.pendingAvatar = data;
        
        try {
            final MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(data);

            final ExtraInfoData eid = new ExtraInfoData(
                    ExtraInfoData.FLAG_HASH_PRESENT, ByteBlock.wrap(digest
                            .digest()));
            final SsiCommand request;
            final IconItem newIconItem;
            if (icon != null) {
                newIconItem = new IconItem(icon);
                newIconItem.setIconInfo(eid);
                request = new ModifyItemsCmd(newIconItem.toSsiItem());
            }
            else {
                newIconItem = new IconItem(IconItem.NAME_DEFAULT, this
                        .getNextBuddyId(SsiItem.GROUP_ROOT), eid);
                request = new CreateItemsCmd(newIconItem.toSsiItem());
            }

            request(new PreModCmd());
            request(request);
            request(new PostModCmd());

            this.icon = newIconItem;
        }
        catch (NoSuchAlgorithmException e) {
            Log.error("No algorithm found for MD5 checksum??");
        }
    }

    /**
     * Adds a visibility settings flag, if that flag had not been set
     * previously.
     * 
     * @param flag
     *            the that will be added to the existing settings.
     * @see VisibilityItem
     */
    public void setVisibilityFlag(long flag) {
        if (visibility != null) {
            if ((visibility.getVisFlags() & flag) == 0) {
                visibility.setVisFlags((visibility.getVisFlags() | flag));
                request(new ModifyItemsCmd(visibility.toSsiItem()));
            }
        }
        else {
            final VisibilityItem newItem = new VisibilityItem(
                    getNextBuddyId(SsiItem.GROUP_ROOT), SsiItem.GROUP_ROOT);
            newItem.setVisFlags(flag);
            this.visibility = newItem;
            request(new CreateItemsCmd(newItem.toSsiItem()));
        }
    }

    /**
     * Update highest buddy id in a group if new id is indeed higher.
     * 
     * @param buddyItem
     *            Buddy item to compare.
     */
    private void updateHighestId(BuddyItem buddyItem) {
        if (!highestBuddyIdPerGroup.containsKey(buddyItem.getGroupId())) {
            highestBuddyIdPerGroup.put(buddyItem.getGroupId(), 0);
        }
        if (buddyItem.getId() > highestBuddyIdPerGroup.get(buddyItem
                .getGroupId())) {
            highestBuddyIdPerGroup.put(buddyItem.getGroupId(), buddyItem
                    .getId());
        }
    }

    /**
     * Returns the name of a default group to which a new user should be added,
     * if a new contact item was created without a group.
     * 
     * This method will first check for existing groups. If groups exist. The
     * first group (the group with the lowest ID) will be returned as the
     * default group. If no groups exist, a hardcoded default group name will be
     * used.
     * 
     * @return a group name (never <tt>null</tt>).
     */
    private String getDefaultGroup() {
        if (!groups.isEmpty()) {
            // if existing groups are available, use the first one.
            final Integer firstKey = Collections.min(groups.keySet());
            final GroupItem firstKnownGroup = groups.get(firstKey);

            Log.debug("Returning first group as default group name: "
                    + firstKnownGroup.getGroupName());

            return firstKnownGroup.getGroupName();
        }
        else {
            // in the (unlikely?) situation that we do not know about existing
            // groups, use a hardcoded default one.
            Log.debug("Returning hard coded value as default group name "
                    + "(no existing groups are available).");

            if (TransportType.icq.equals(parent.getTransport().getType())) {
                return DEFAULT_ICQ_GROUP;
            }
            else {
                return DEFAULT_AIM_GROUP;
            }
        }
    }

    /**
     * Synchronizes the basic characteristics of one contact, including:
     * <ul>
     * <li>the list of groups a contact is a member of</li>
     * <li>nicknames</li>
     * </ul>
     * 
     * As an OSCAR contact must be in at least one group, a default group is
     * used if the provided group list is empty or <tt>null</tt>.
     * 
     * @param contact
     *            Screen name/UIN of the contact.
     * @param nickname
     *            Nickname of the contact (should not be <tt>null</tt>)
     * @param grouplist
     *            List of groups the contact should be a member of.
     * @see SSIHierarchy#getDefaultGroup()
     */
    public void syncContactGroupsAndNickname(String contact, String nickname, List<String> grouplist) {
        if (grouplist == null) {
            grouplist = new ArrayList<String>();
        }
        if (grouplist.isEmpty()) {
            Log.debug("No groups provided for the sync of contact " + contact
                    + ". Using default group.");
            grouplist.add(getDefaultGroup());
        }
        
        Log.debug("Syncing contact = "+contact+", nickname = "+nickname+", grouplist = "+grouplist);
        OSCARBuddy oscarBuddy = null;
        try {
            final JID jid = parent.getTransport().convertIDToJID(contact);
            oscarBuddy = parent.getBuddyManager().getBuddy(jid);
            Log.debug("Found related oscarbuddy: " + oscarBuddy);
        }
        catch (NotFoundException e) {
            Log.debug("Didn't find related oscarbuddy. One will be created.");
        }

        //TODO: Should we do a premodcmd here and postmodcmd at the end and not have separate ones?

        // Stored 'removed' list of buddy items for later use
        final List<BuddyItem> freeBuddyItems = new ArrayList<BuddyItem>();
        
        // First, lets clean up any groups this contact should no longer be a member of.
        // We'll keep the buddy items around for potential modification instead of deletion.
        if (oscarBuddy != null) {
            for (BuddyItem buddy : oscarBuddy.getBuddyItems()) {
//                if (buddy.getScreenname().equalsIgnoreCase(contact)) {
                    if (!groups.containsKey(buddy.getGroupId())) {
                        // Well this is odd, a group we don't know about?  Nuke it.
                        Log.debug("Removing "+buddy+" because of unknown group");
                        freeBuddyItems.add(buddy);
//                        request(new DeleteItemsCmd(buddy.toSsiItem()));
//                        oscarBuddy.removeBuddyItem(buddy.getGroupId(), true);
                    }
                    else if (!grouplist.contains(groups.get(buddy.getGroupId()).getGroupName())) {
                        Log.debug("Removing "+buddy+" because not in list of groups");
                        freeBuddyItems.add(buddy);
//                        request(new DeleteItemsCmd(buddy.toSsiItem()));
//                        oscarBuddy.removeBuddyItem(buddy.getGroupId(), true);
                    }
                    else {
                        // nothing to delete? lets update Aliases then.
                        if (buddy.getAlias() == null || !buddy.getAlias().equals(nickname)) {
                            Log.debug("Updating alias for "+buddy);
                            buddy.setAlias(nickname);
                            request(new PreModCmd());
                            request(new ModifyItemsCmd(buddy.toSsiItem()));
                            request(new PostModCmd());
                            
                            updateHighestId(buddy);
                            oscarBuddy.tieBuddyItem(buddy, true);
                        }
                    }
//                }
            }
        }
        // Now, lets take the known good list of groups and add whatever is missing on the server.
        for (String group : grouplist) {
            Integer groupId = getGroupIdOrCreateNew(group);
            if (isMemberOfGroup(groupId, contact)) {
                // Already a member, moving on
                continue;
            }

            Integer newBuddyId = 1;
            if (highestBuddyIdPerGroup.containsKey(groupId)) {
                newBuddyId = getNextBuddyId(groupId);
            }

            if (freeBuddyItems.size() > 0) {
                // Moving a freed buddy item
                // TODO: This isn't working.. why?  Returns RESULT_ID_TAKEN
//                BuddyItem buddy = freeBuddyItems.remove(0);
//                if (oscarBuddy != null) {
//                    oscarBuddy.removeBuddyItem(buddy.getGroupId(), false);
//                }
//                buddy.setGroupid(groupId);
//                buddy.setId(newBuddyId);
//                buddy.setAlias(nickname);
//                request(new ModifyItemsCmd(buddy.toSsiItem()));
//                if (oscarBuddy == null) {
//                    oscarBuddy = new OSCARBuddy(getBuddyManager(), buddy);
//                    // TODO: translate this
//                    request(new BuddyAuthRequest(contact, "Automated add request on behalf of user."));
//                }
//                else {
//                    oscarBuddy.tieBuddyItem(buddy, false);
//                }
                request(new PreModCmd());
                BuddyItem buddy = freeBuddyItems.remove(0);
                BuddyItem newBuddy = new BuddyItem(buddy);
                newBuddy.setGroupid(groupId);
                newBuddy.setId(newBuddyId);
                newBuddy.setAlias(nickname);
                request(new DeleteItemsCmd(buddy.toSsiItem()));
                if (oscarBuddy != null) {
                    oscarBuddy.removeBuddyItem(buddy.getGroupId(), false);
                }
                request(new CreateItemsCmd(newBuddy.toSsiItem()));
                if (oscarBuddy == null) {
                    oscarBuddy = new OSCARBuddy(parent.getBuddyManager(), newBuddy);
                    // TODO: translate this
                    request(new BuddyAuthRequest(contact, "Automated add request on behalf of user."));
                }
                else {
                    oscarBuddy.tieBuddyItem(newBuddy, false);
                }
                request(new PostModCmd());
            }
            else {
                // Creating a new buddy item
                final BuddyItem newBuddy = new BuddyItem(contact, groupId, newBuddyId);
                newBuddy.setAlias(nickname);
                updateHighestId(newBuddy);
                //  TODO: Should we be doing this for AIM too?
                if (parent.getTransport().getType().equals(TransportType.icq)) {
                    newBuddy.setAwaitingAuth(true);
                }
                request(new PreModCmd());
                request(new CreateItemsCmd(newBuddy.toSsiItem()));
                request(new PostModCmd());
                if (oscarBuddy == null) {
                    oscarBuddy = new OSCARBuddy(parent.getBuddyManager(), newBuddy);
                    // TODO: translate this
                    request(new BuddyAuthRequest(contact, "Automated add request on behalf of user."));
                }
                else {
                    oscarBuddy.tieBuddyItem(newBuddy, true);
                }
            }
        }
        // Now, lets remove any leftover buddy items that we're no longer using.
        for (BuddyItem buddy : freeBuddyItems) {
            request(new DeleteItemsCmd(buddy.toSsiItem()));
            if (oscarBuddy != null) {
                oscarBuddy.removeBuddyItem(buddy.getGroupId(), false);
            }
        }
        // Lastly, lets store the final buddy item after we've modified it, making sure to update groups first.
        if (oscarBuddy != null) {
//            oscarBuddy.populateGroupList();
            parent.getBuddyManager().storeBuddy(oscarBuddy);
        }
    }

    /**
     * Returns the name of a group, based on its ID.
     * 
     * @param groupID
     *            the ID of the group.
     * @return the name of the group
     */
    public String getGroupName(int groupID) {
        if (!groups.containsKey(groupID)) {
            return null;
        }

        return groups.get(groupID).getGroupName();
    }

    /**
     * Finds the id number of a group specified or creates a new one and returns
     * that id.
     * 
     * @param groupName
     *            Name of the group we are looking for.
     * @return Id number of the group.
     */
    public int getGroupIdOrCreateNew(String groupName) {
        for (final GroupItem g : groups.values()) {
            if (groupName.equalsIgnoreCase(g.getGroupName())) {
                return g.getId();
            }
        }

        // Group doesn't exist, lets create a new one.
        final int newGroupId = getNextBuddyId(0);
        final GroupItem newGroup = new GroupItem(groupName, newGroupId);
        request(new CreateItemsCmd(newGroup.toSsiItem()));

        gotGroup(newGroup);

        return newGroupId;
    } 
    
    /**
     * Determines if a contact is a member of a group.
     * 
     * @param groupId
     *            ID of group to check
     * @param member
     *            Screen name of member
     * @return True or false if member is in group with id groupId
     */
    public boolean isMemberOfGroup(int groupId, String member) {
        for (TransportBuddy buddy : parent.getBuddyManager().getBuddies()) {
            if (buddy.getName().equalsIgnoreCase(member)
                    && ((OSCARBuddy) buddy).getBuddyItem(groupId) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Retrieves the next highest buddy ID for a group and stores the new
     * highest id.
     * 
     * @param groupId
     *            ID of group to get highest of
     * @return ID number of buddy ID you should use.
     */
    public int getNextBuddyId(int groupId) {
        final int id = highestBuddyIdPerGroup.get(groupId) + 1;
        highestBuddyIdPerGroup.put(groupId, id);
        return id;
    }

    /**
     * Deletes a buddy from the contact list.
     * 
     * @param buddy
     *            the buddy to be deleted.
     */
    public void delete(BuddyItem buddy) {
        request(new DeleteItemsCmd(buddy.toSsiItem()));
    }

    /**
     * Avatar byte data that is likely to be requested by the OSCAR network.
     * 
     * @return Avatar byte data
     */
    public byte[] getPendingAvatarData() {
        return pendingAvatar;
    }

    /**
     * Clears the pending avatar data, freeing up (a lot of) memory.
     */
    public void clearPendingAvatar() {
        pendingAvatar = null;
    }
    
    // Below this point: Event handlers used to fill instances of this class,
    // based on incoming data from the AIM network.

    /**
     * We've been told about a group that exists on the buddy list.
     * 
     * @param group
     *            The group we've been told about.
     */
    void gotGroup(GroupItem group) {
        Log.debug("Found group item: " + group.toString() + " at id "
                + group.getId());
        groups.put(group.getId(), group);
        if (!highestBuddyIdPerGroup.containsKey(0)) {
            highestBuddyIdPerGroup.put(0, 0);
        }
        if (group.getId() > highestBuddyIdPerGroup.get(0)) {
            highestBuddyIdPerGroup.put(0, group.getId());
        }
    }

    /**
     * We've been told about an icon that exists on the buddy list.
     * 
     * @param iconItem
     *            The icon info we've been told about.
     */
    void gotIconItem(IconItem iconItem) {
        this.icon = iconItem;
    }

    /**
     * We've been told about a visibility item that exists on the buddy list.
     * 
     * @param visibilityItem
     *            The visibility info we've been told about.
     */
    void gotVisibilityItem(VisibilityItem visibilityItem) {
        this.visibility = visibilityItem;
    }

    /**
     * We've been told about a buddy that exists on the buddy list.
     * 
     * @param buddyItem
     *            the buddy we've been told about.
     */
    public void gotBuddy(BuddyItem buddyItem) {
        updateHighestId(buddyItem);
        final TransportBuddyManager<OSCARBuddy> buddyManager = parent.getBuddyManager();
        try {
            final JID jid = parent.getTransport().convertIDToJID(
                    buddyItem.getScreenname());
            final OSCARBuddy oscarBuddy = buddyManager
                    .getBuddy(jid);
            oscarBuddy.tieBuddyItem(buddyItem, false);
        }
        catch (NotFoundException ee) {
            final OSCARBuddy oscarBuddy = new OSCARBuddy(buddyManager, buddyItem);
            buddyManager.storeBuddy(oscarBuddy);
        }
    }
    
    // Up to this point: Event handlers used to fill instances of this class,
    // based on incoming data from the AIM network.
}
