/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.user;

import org.jivesoftware.messenger.XMPPAddress;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * <p>Implements the basic RosterItem interface storing all data into simple fields.</p>
 * <p>This class is intended to be used as a simple based for creating specialized RosterItem
 * implementations without having to recode the very boring and copies set/get accessor methods.</p>
 *
 * @author Iain Shigeoka
 */
public class BasicRosterItem implements RosterItem {
    public BasicRosterItem(XMPPAddress jid,
                           SubType subStatus,
                           AskType askStatus,
                           RecvType recvStatus,
                           String nickname,
                           List groups) {
        this.jid = jid;
        this.subStatus = subStatus;
        this.askStatus = askStatus;
        this.recvStatus = recvStatus;
        this.nickname = nickname;
        this.groups = new LinkedList();
        if (groups != null) {
            Iterator groupItr = groups.iterator();
            while (groupItr.hasNext()) {
                this.groups.add(groupItr.next());
            }
        }
    }

    public BasicRosterItem(XMPPAddress jid) {
        this(jid,
                RosterItem.SUB_NONE,
                RosterItem.ASK_NONE,
                RosterItem.RECV_NONE,
                null,
                null);
    }

    public BasicRosterItem(XMPPAddress jid, String nickname, List groups) {
        this(jid,
                RosterItem.SUB_NONE,
                RosterItem.ASK_NONE,
                RosterItem.RECV_NONE,
                nickname,
                groups);
    }

    /**
     * <p>Create a roster item from the data in another one.</p>
     *
     * @param item
     */
    public BasicRosterItem(RosterItem item) {
        this(item.getJid(),
                item.getSubStatus(),
                item.getAskStatus(),
                item.getRecvStatus(),
                item.getNickname(),
                item.getGroups());
    }

    protected RecvType recvStatus;
    protected XMPPAddress jid;
    protected String nickname;
    protected List groups;
    protected SubType subStatus;
    protected AskType askStatus;

    public SubType getSubStatus() {
        return subStatus;
    }

    public void setSubStatus(SubType subStatus) {
        this.subStatus = subStatus;
    }

    public AskType getAskStatus() {
        return askStatus;
    }

    public void setAskStatus(AskType askStatus) {
        this.askStatus = askStatus;
    }

    public RecvType getRecvStatus() {
        return recvStatus;
    }

    public void setRecvStatus(RecvType recvStatus) {
        this.recvStatus = recvStatus;
    }

    public XMPPAddress getJid() {
        return jid;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public List getGroups() {
        return groups;
    }

    public void setGroups(List groups) {
        if (groups == null) {
            this.groups = new LinkedList();
        }
        else {
            this.groups = groups;
        }
    }
}
