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

import org.jivesoftware.messenger.XMPPAddress;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.util.IntEnum;

import java.util.List;

/**
 * <p>Represents a single roster item for a User's Roster.</p>
 * <p>The server doesn't need to know anything about roster groups so they are
 * not stored with easy retrieval or manipulation in mind. The important data
 * elements of a roster item (beyond the jid adddress of the roster entry) includes:</p>
 * <p/>
 * <ul>
 * <li>nick   - A nickname for the user when used in this roster</li>
 * <li>sub    - A subscription type: to, from, none, both</li>
 * <li>ask    - An optional subscription ask status: subscribe, unsubscribe</li>
 * <li>groups - A list of groups to organize roster entries under (e.g. friends, co-workers, etc)</li>
 * </ul>
 *
 * @author Iain Shigeoka
 */
public interface RosterItem {

    class SubType extends IntEnum {
        protected SubType(String name, int value) {
            super(name, value);
            register(this);
        }

        public static SubType getTypeFromInt(int value) {
            return (SubType)getEnumFromInt(SubType.class, value);
        }
    }

    class AskType extends IntEnum {
        protected AskType(String name, int value) {
            super(name, value);
            register(this);
        }

        public static AskType getTypeFromInt(int value) {
            return (AskType)getEnumFromInt(AskType.class, value);
        }
    }

    class RecvType extends IntEnum {
        protected RecvType(String name, int value) {
            super(name, value);
            register(this);
        }

        public static RecvType getTypeFromInt(int value) {
            return (RecvType)getEnumFromInt(RecvType.class, value);
        }
    }

    /**
     * <p>Indicates the roster item should be removed.</p>
     */
    public static final SubType SUB_REMOVE = new SubType("remove", -1);
    /**
     * <p>No subscription is established.</p>
     */
    public static final SubType SUB_NONE = new SubType("none", 0);
    /**
     * <p>The roster owner has a subscription to the roster item's presence.</p>
     */
    public static final SubType SUB_TO = new SubType("to", 1);
    /**
     * <p>The roster item has a subscription to the roster owner's presence.</p>
     */
    public static final SubType SUB_FROM = new SubType("from", 2);
    /**
     * <p>The roster item and owner have a mutual subscription.</p>
     */
    public static final SubType SUB_BOTH = new SubType("both", 3);

    /**
     * <p>The roster item has no pending subscripton requests.</p>
     */
    public static final AskType ASK_NONE = new AskType("", -1);
    /**
     * <p>The roster item has been asked for permission to subscribe to their presence
     * but no response has been received.</p>
     */
    public static final AskType ASK_SUBSCRIBE = new AskType("subscribe", 0);
    /**
     * <p>The roster owner has asked to the roster item to unsubscribe from it's
     * presence but has not received confirmation.</p>
     */
    public static final AskType ASK_UNSUBSCRIBE = new AskType("unsubscribe", 1);

    /**
     * <p>There are no subscriptions that have been received but not presented to the user.</p>
     */
    public static final RecvType RECV_NONE = new RecvType("", -1);
    /**
     * <p>The server has received a subscribe request, but has not forwarded it to the user.</p>
     */
    public static final RecvType RECV_SUBSCRIBE = new RecvType("sub", 1);
    /**
     * <p>The server has received an unsubscribe request, but has not forwarded it to the user.</p>
     */
    public static final RecvType RECV_UNSUBSCRIBE = new RecvType("unsub", 2);

    /**
     * <p>Obtain the current subscription status of the item.</p>
     *
     * @return The subscription status of the item
     */
    public SubType getSubStatus();

    /**
     * <p>Set the current subscription status of the item.</p>
     *
     * @param subStatus The subscription status of the item
     */
    public void setSubStatus(SubType subStatus) throws UnauthorizedException;

    /**
     * <p>Obtain the current ask status of the item.</p>
     *
     * @return The ask status of the item
     */
    public AskType getAskStatus();

    /**
     * <p>Set the current ask status of the item.</p>
     *
     * @param askStatus The ask status of the item
     */
    public void setAskStatus(AskType askStatus) throws UnauthorizedException;

    /**
     * <p>Obtain the current recv status of the item.</p>
     *
     * @return The recv status of the item
     */
    public RecvType getRecvStatus();

    /**
     * <p>Set the current recv status of the item.</p>
     *
     * @param recvStatus The recv status of the item
     */
    public void setRecvStatus(RecvType recvStatus) throws UnauthorizedException;

    /**
     * <p>Obtain the address of the item.</p>
     *
     * @return The address of the item
     */
    public XMPPAddress getJid();

    /**
     * <p>Obtain the current nickname for the item.</p>
     *
     * @return The subscription status of the item
     */
    public String getNickname();

    /**
     * <p>Set the current nickname for the item.</p>
     *
     * @param name The subscription status of the item
     */
    public void setNickname(String name) throws UnauthorizedException;

    /**
     * <p>Obtain the groups for the item.</p>
     *
     * @return The subscription status of the item
     */
    public List getGroups();

    /**
     * <p>Set the current groups for the item.</p>
     *
     * @param groups The subscription status of the item
     */
    public void setGroups(List groups) throws UnauthorizedException;
}