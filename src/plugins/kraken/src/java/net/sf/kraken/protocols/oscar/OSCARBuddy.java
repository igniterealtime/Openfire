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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.kano.joscar.ssiitem.BuddyItem;
import net.sf.kraken.roster.TransportBuddy;
import net.sf.kraken.roster.TransportBuddyManager;

/**
 * Representation of a AIM or ICQ specific {@link TransportBuddy}.
 * 
 * OSCAR contacts are represented by the {@link BuddyItem} class. Each instance
 * is linked to exactly one group. A person on ones OSCAR list can exist in
 * multiple groups though. If this happens, multiple {@link BuddyItem}s will
 * exist for the same person. This implementation of Krakens {@link OSCARBuddy}
 * can combine multiple {@link BuddyItem} instances. In this way, it allows for
 * one contact list entity that is assigned to multiple groups.
 * 
 * @author Daniel Henninger
 */
public class OSCARBuddy extends TransportBuddy {

    /**
     * The {@link BuddyItem} instances of which this {@link OSCARBuddy} instance
     * is a representation. Each BuddyItem is mapped by its unique ID (obtained
     * through {@link BuddyItem#getId()}.
     */
    public Map<Integer,BuddyItem> buddyItems = new ConcurrentHashMap<Integer,BuddyItem>();

    public OSCARBuddy(TransportBuddyManager<OSCARBuddy> manager, BuddyItem buddyItem) {
        super(manager, buddyItem.getScreenname(), buddyItem.getAlias(), null);
        buddyItems.put(buddyItem.getGroupId(), buddyItem);
    }

    /**
     * Runs through group id list, matching to finished collection of groups to get names.
     */
    public void populateGroupList() {
        List<String> groupList = new ArrayList<String>();

        final SSIHierarchy ssi = ((OSCARSession) this.getManager().getSession()).getSsiHierarchy();
            for (BuddyItem buddyItem : buddyItems.values()) {
            try {
                // resolve the group name that matches the ID in the buddy item.
                final int groupID = buddyItem.getGroupId();
                final String groupName = ssi.getGroupName(groupID);
                groupList.add(groupName);
            }
            catch (Exception e) {
                // Hrm, unknown group.  Don't include.
            }
        }
        setGroups(groupList);
    }

    /**
     * Links this instance to a {@link BuddyItem} instance. Multiple BuddyItem
     * instances can be linked to one {@link OSCARBuddy}. This is used to
     * simulate one contact belonging to multiple groups on the contact list.
     * 
     * This method adds the provided BuddyItem, replacing any previous BuddyItem
     * that has the same ID (obtained through {@link BuddyItem#getId()}.
     *
     * @param buddyItem Buddy item to attach.
     * @param autoPopulate Trigger an automatic regeneration of group list.
     */
    public synchronized void tieBuddyItem(BuddyItem buddyItem, boolean autoPopulate) {
        buddyItems.put(buddyItem.getGroupId(), buddyItem);
        if (autoPopulate) {
            populateGroupList();
        }
    }

    /**
     * Retrieves one of the buddy items, as tied to a group.
     *
     * @param groupId Group id of buddy item to retrieve.
     * @return Buddy item in said group.
     */
    public BuddyItem getBuddyItem(int groupId) {
        return buddyItems.get(groupId);
    }

    /**
     * Removes a buddy item from the list as specified by a group id.
     *
     * @param groupId Group id of buddy item to be removed.
     * @param autoPopulate Trigger an automatic regeneration of group list.
     */
    public synchronized void removeBuddyItem(int groupId, boolean autoPopulate) {
        buddyItems.remove(groupId);
        if (autoPopulate) {
            populateGroupList();
        }
    }

    /**
     * Retrieves all of the buddy items associated with the buddy.
     *
     * @return List of buddy items associated with the buddy.
     */
    public Collection<BuddyItem> getBuddyItems() {
        return buddyItems.values();
    }

    /**
     * Updates the status of the buddy given an OSCAR FullUserInfo packet.
     *
     * @param info FullUserInfo packet to parse and set status based off of.
     */
//    public void parseFullUserInfo(FullUserInfo info) {
//        PresenceType pType = PresenceType.available;
//        String vStatus = "";
//        if (info.getAwayStatus()) {
//            pType = PresenceType.away;
//        }
//
//        if (getManager().getSession().getTransport().getType().equals(TransportType.icq) && info.getScreenname().matches("/^\\d+$/")) {
//            pType = ((OSCARTransport)getManager().getSession().getTransport()).convertICQStatusToXMPP(info.getIcqStatus());
//        }
//
//        List<ExtraInfoBlock> extraInfo = info.getExtraInfoBlocks();
//        if (extraInfo != null) {
//            for (ExtraInfoBlock i : extraInfo) {
//                ExtraInfoData data = i.getExtraData();
//
//                if (i.getType() == ExtraInfoBlock.TYPE_AVAILMSG) {
//                    ByteBlock msgBlock = data.getData();
//                    int len = BinaryTools.getUShort(msgBlock, 0);
//                    if (len >= 0) {
//                        byte[] msgBytes = msgBlock.subBlock(2, len).toByteArray();
//                        String msg;
//                        try {
//                            msg = new String(msgBytes, "UTF-8");
//                        }
//                        catch (UnsupportedEncodingException e1) {
//                            continue;
//                        }
//                        if (msg.length() > 0) {
//                            vStatus = msg;
//                        }
//                    }
//                }
//            }
//        }
//
//        setPresenceAndStatus(pType, vStatus);
//    }

}
