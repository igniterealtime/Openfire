/**
 * $RCSfile$
 * $Revision: 3187 $
 * $Date: 2005-12-11 13:34:34 -0300 (Sun, 11 Dec 2005) $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.session;

import org.jivesoftware.openfire.privacy.PrivacyList;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

/**
 * Represents a session between the server and a client.
 *
 * @author Gaston Dombiak
 */
public interface ClientSession extends Session {

    /**
     * Returns the Privacy list that overrides the default privacy list. This list affects
     * only this session and only for the duration of the session.
     *
     * @return the Privacy list that overrides the default privacy list.
     */
    public PrivacyList getActiveList();

    /**
     * Sets the Privacy list that overrides the default privacy list. This list affects
     * only this session and only for the duration of the session.
     *
     * @param activeList the Privacy list that overrides the default privacy list.
     */
    public void setActiveList(PrivacyList activeList);

    /**
     * Returns the default Privacy list used for the session's user. This list is
     * processed if there is no active list set for the session.
     *
     * @return the default Privacy list used for the session's user.
     */
    public PrivacyList getDefaultList();

    /**
     * Sets the default Privacy list used for the session's user. This list is
     * processed if there is no active list set for the session.
     *
     * @param defaultList the default Privacy list used for the session's user.
     */
    public void setDefaultList(PrivacyList defaultList);

    /**
     * Returns the username associated with this session. Use this information
     * with the user manager to obtain the user based on username.
     *
     * @return the username associated with this session
     * @throws UserNotFoundException if a user is not associated with a session
     *      (the session has not authenticated yet)
     */
    public String getUsername() throws UserNotFoundException;

    /**
     * Returns true if the offline messages of the user should be sent to the user when
     * the user becomes online. If the user sent a disco request with node
     * "http://jabber.org/protocol/offline" before the available presence then do not
     * flood the user with the offline messages. If the user is connected from many resources
     * then if one of the sessions stopped the flooding then no session should flood the user.
     *
     * @return true if the offline messages of the user should be sent to the user when the user
     *         becomes online.
     */
    public boolean canFloodOfflineMessages();

    /**
     * Returns true if the user requested to not receive offline messages when sending
     * an available presence. The user may send a disco request with node
     * "http://jabber.org/protocol/offline" so that no offline messages are sent to the
     * user when he becomes online. If the user is connected from many resources then
     * if one of the sessions stopped the flooding then no session should flood the user.
     *
     * @return true if the user requested to not receive offline messages when sending
     *         an available presence.
     */
    public boolean isOfflineFloodStopped();

    /**
     * Obtain the presence of this session.
     *
     * @return The presence of this session or null if not authenticated
     */
    public Presence getPresence();

    /**
     * Set the presence of this session
     *
     * @param presence The presence for the session
     * @return The old priority of the session or null if not authenticated
     */
    public Presence setPresence(Presence presence);

    /**
     * Returns the number of conflicts detected on this session.
     * Conflicts typically occur when another session authenticates properly
     * to the user account and requests to use a resource matching the one
     * in use by this session. Administrators may configure the server to automatically
     * kick off existing sessions when their conflict count exceeds some limit including
     * 0 (old sessions are kicked off immediately to accommodate new sessions). Conflicts
     * typically signify the existing (old) session is broken/hung.
     *
     * @return The number of conflicts detected for this session
     */
    public int getConflictCount();

    /**
     * Increments the conflict by one.
     */
    public void incrementConflictCount();

    /**
     * Returns true if the specified packet must not be blocked based on the active or default
     * privacy list rules. The active list will be tried first. If none was found then the
     * default list is going to be used. If no default list was defined for this user then
     * allow the packet to flow.
     *
     * @param packet the packet to analyze if it must be blocked.
     * @return true if the specified packet must be blocked.
     */
    public boolean canProcess(Packet packet);

}
