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

package org.jivesoftware.messenger;

import org.jivesoftware.messenger.auth.UnauthorizedException;
import java.util.Date;

/**
 * <p>Allows the simple creation, reading, and updating of presence packets.<p>
 * <p>The methods used are mainly convenience interfaces to the various parts
 * of a typical presence packet.</p>
 * <p>A Presence encapsulates information relating to the owning user such as login time, status and
 * last update time.</p>
 *
 * @author Iain Shigeoka
 */
public interface Presence extends XMPPPacket {


    /**
     * <p>Sender is available for messaging.</p>
     */
    Type AVAILABLE = new Type("");
    /**
     * <p>Sender is unavailable for messaging.</p>
     */
    Type UNAVAILABLE = new Type("unavailable");
    /**
     * <p>Sender is available for message reciept but presence should not be broadcast to roster members.</p>
     */
    Type INVISIBLE = new Type("invisible");
    /**
     * <p>Sender wishes to subscribe to recipient's roster.</p>
     */
    Type SUBSCRIBE = new Type("subscribe");
    /**
     * <p>Indicates the sender should be unsubscribed from the recipient's roster.</p>
     */
    Type UNSUBSCRIBE = new Type("unsubscribe");
    /**
     * <p>Sent to indicate a pending subcription request has been filled.</p>
     */
    Type SUBSCRIBED = new Type("subscribed");
    /**
     * <p>Sent to indicate a pending subscription removal request has been filled.</p>
     */
    Type UNSUBSCRIBED = new Type("unsubscribed");
    /**
     * <p>Used when the sender wants to know the recipient's presence.</p>
     */
    Type PROBE = new Type("probe");

    int NO_PRIORITY = -10;

    public static final int STATUS_ONLINE = 0;

    public static final int STATUS_IDLE = 1;

    public static final int STATUS_INVISIBLE = 2;

    public static final int STATUS_OFFLINE = 4;

    public static final int STATUS_PROBE = -1;

    /**
     * Available to chat show state
     */
    public static final int SHOW_CHAT = 100;
    /**
     * No show state
     */
    public static final int SHOW_NONE = 101;
    /**
     * Away show state
     */
    public static final int SHOW_AWAY = 102;
    /**
     * "Extended away" show state
     */
    public static final int SHOW_XA = 103;
    /**
     * Do not disturb show state
     */
    public static final int SHOW_DND = 104;
    /**
     * Invisible show state (user not shown)
     */
    public static final int SHOW_INVISIBLE = 110;

    /**
     * The online/offline status of the user. Being available indicates that the node can be
     * sent packets and does not imply how the presence is propogated or presented to users.
     *
     * @return True if the node is available for messaging
     */
    public boolean isAvailable();

    /**
     * Sets the online/offline status of the user.
     *
     * @param online True if the node is available for messaging
     */
    public void setAvailable(boolean online) throws UnauthorizedException;

    /**
     * Gets the visibility of the user. Invisible nodes can be sent packets, but don't
     * automatically propagate presence to subscribers.
     *
     * @return True if the user is visible
     *         (presence should be automatically propagated to subscribers)
     */
    public boolean isVisible();

    /**
     * Sets the visibility of the user. Invisible nodes can be sent packets, but don't
     * automatically propagate presence to subscribers.
     *
     * @param visible True if the node is visible (presence is broadcast)
     * @throws UnauthorizedException If the caller doesn't have permission
     */
    public void setVisible(boolean visible) throws UnauthorizedException;

    /**
     * Returns the unique ID for this status. The ID in the default implmentation is the user's
     * session ID, which is unique within a single JVM.
     *
     * @return the unique ID for the presence.
     */
    public String getID();

    /**
     * Return the user owning the presence.
     *
     * @return the presence owner.
     */
    public long getUserID();

    /**
     * Return the time when the presence was created.
     *
     * @return the time when the presence was created.
     */
    public Date getLoginTime();

    /**
     * Return the time when the presence was last updated (when the user last visited).
     *
     * @return the time when the presence was last updated (when the user last visited).
     */
    public Date getLastUpdateTime();

    /**
     * Set the time when the presence was last updated (when the user last visited).
     *
     * @param time the time of the last update.
     * @throws UnauthorizedException If the caller doesn't have permissions to make this modification
     */
    public void setLastUpdateTime(Date time) throws UnauthorizedException;

    /**
     * Returns the status of the presence.
     *
     * @return the status of the presence.
     */
    public int getShow();

    /**
     * Sets the status of the user.
     *
     * @param status the status of the user.
     * @throws UnauthorizedException If the caller doesn't have permissions to make this modification
     */
    public void setShow(int status) throws UnauthorizedException;

    /**
     * Gets the free text status of the user. (e.g. "out to lunch").
     *
     * @return The status text for the user or null if none has been set
     */
    public String getStatus();

    /**
     * Sets the free text status of the user. (e.g. "out to lunch").
     *
     * @param status The new status or null if none is set
     * @throws UnauthorizedException
     */
    public void setStatus(String status) throws UnauthorizedException;

    /**
     * Obtain the priority associated with this presence if any
     *
     * @return The priority of this presence or -1 to indicate none set
     */
    public int getPriority();

    /**
     * Sets the new priority for this presence
     *
     * @param priority The new priority value
     * @throws UnauthorizedException If the caller doesn't have the appropriate authorization
     */
    public void setPriority(int priority) throws UnauthorizedException;
}
