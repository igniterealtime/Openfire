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

package org.jivesoftware.messenger.user;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.xmpp.packet.JID;

/**
 * <p>Implements the basic RosterItem interface storing all data into simple fields.</p>
 * <p>This class is intended to be used as a simple based for creating specialized RosterItem
 * implementations without having to recode the very boring and copies set/get accessor methods.</p>
 *
 * @author Iain Shigeoka
 */
public class BasicRosterItem implements RosterItem {
    protected RecvType recvStatus;
    protected JID jid;
    protected String nickname;
    protected List<String> groups;
    protected SubType subStatus;
    protected AskType askStatus;


    public BasicRosterItem(JID jid,
                           SubType subStatus,
                           AskType askStatus,
                           RecvType recvStatus,
                           String nickname,
                           List<String> groups) {
        this.jid = jid;
        this.subStatus = subStatus;
        this.askStatus = askStatus;
        this.recvStatus = recvStatus;
        this.nickname = nickname;
        this.groups = new LinkedList<String>();
        if (groups != null) {
            Iterator<String> groupItr = groups.iterator();
            while (groupItr.hasNext()) {
                this.groups.add(groupItr.next());
            }
        }
    }

    public BasicRosterItem(JID jid) {
        this(jid,
                RosterItem.SUB_NONE,
                RosterItem.ASK_NONE,
                RosterItem.RECV_NONE,
                null,
                null);
    }

    public BasicRosterItem(JID jid, String nickname, List<String> groups) {
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
    public BasicRosterItem(org.xmpp.packet.Roster.Item item) {
        this(item.getJID(),
                getSubType(item),
                getAskStatus(item),
                RosterItem.RECV_NONE,
                item.getName(),
                new LinkedList<String>(item.getGroups()));
    }

    private static RosterItem.AskType getAskStatus(org.xmpp.packet.Roster.Item item) {
        if (item.getAsk() == org.xmpp.packet.Roster.Ask.subscribe) {
            return RosterItem.ASK_SUBSCRIBE;
        }
        else if (item.getAsk() == org.xmpp.packet.Roster.Ask.unsubscribe) {
            return RosterItem.ASK_UNSUBSCRIBE;
        }
        else {
            return RosterItem.ASK_NONE;
        }
    }

    private static RosterItem.SubType getSubType(org.xmpp.packet.Roster.Item item) {
        if (item.getSubscription() == org.xmpp.packet.Roster.Subscription.to) {
            return RosterItem.SUB_TO;
        }
        else if (item.getSubscription() == org.xmpp.packet.Roster.Subscription.from) {
            return RosterItem.SUB_FROM;
        }
        else if (item.getSubscription() == org.xmpp.packet.Roster.Subscription.both) {
            return RosterItem.SUB_BOTH;
        }
        else if (item.getSubscription() == org.xmpp.packet.Roster.Subscription.remove) {
            return RosterItem.SUB_REMOVE;
        }
        else {
            return RosterItem.SUB_NONE;
        }
    }

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

    public JID getJid() {
        return jid;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public List<String> getGroups() {
        return groups;
    }

    public void setGroups(List<String> groups) {
        if (groups == null) {
            this.groups = new LinkedList<String>();
        }
        else {
            this.groups = groups;
        }
    }
}
