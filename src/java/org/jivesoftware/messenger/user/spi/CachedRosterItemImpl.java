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
import org.jivesoftware.messenger.XMPPAddress;
import org.jivesoftware.messenger.user.BasicRosterItem;
import org.jivesoftware.messenger.user.CachedRosterItem;
import org.jivesoftware.messenger.user.RosterItem;
import org.jivesoftware.util.CacheSizes;

import java.util.List;

/**
 * In-memory implementation of a roster item. The ID of the roster item is it's roster ID.
 *
 * @author Iain Shigeoka
 */
public class CachedRosterItemImpl extends BasicRosterItem implements CachedRosterItem {

    public CachedRosterItemImpl(long id,
                                XMPPAddress jid,
                                SubType subStatus,
                                AskType askStatus,
                                RecvType recvStatus,
                                String nickname,
                                List groups) {
        super(jid, subStatus, askStatus, recvStatus, nickname, groups);
        this.rosterID = id;
    }

    public CachedRosterItemImpl(long id, XMPPAddress jid) {
        this(id,
                jid,
                RosterItem.SUB_NONE,
                RosterItem.ASK_NONE,
                RosterItem.RECV_NONE,
                null,
                null);
    }

    public CachedRosterItemImpl(long id, XMPPAddress jid, String nickname, List groups) {
        this(id,
                jid,
                RosterItem.SUB_NONE,
                RosterItem.ASK_NONE,
                RosterItem.RECV_NONE,
                nickname,
                groups);
    }

    /**
     * <p>Create a roster item from the data in another one.</p>
     *
     * @param id
     * @param item
     */
    public CachedRosterItemImpl(long id, RosterItem item) {
        this(id,
                item.getJid(),
                item.getSubStatus(),
                item.getAskStatus(),
                item.getRecvStatus(),
                item.getNickname(),
                item.getGroups());
    }

    private long rosterID;

    public void setID(long rosterID) {
        this.rosterID = rosterID;
    }

    public long getID() {
        return rosterID;
    }

    public void setAsCopyOf(RosterItem item) {
        setNickname(item.getNickname());
        setGroups(item.getGroups());
    }

    public int getCachedSize() {
        int size = jid.getCachedSize();
        size += CacheSizes.sizeOfString(nickname);
        size += CacheSizes.sizeOfList(groups);
        size += CacheSizes.sizeOfInt(); // subStatus
        size += CacheSizes.sizeOfInt(); // askStatus
        size += CacheSizes.sizeOfLong(); // id
        return size;
    }
}
