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
import org.jivesoftware.messenger.user.User;
import java.util.Collection;

/**
 * The presence manager tracks on a global basis who's online. The presence
 * monitor watches and reports on what users are present on the server, and
 * in other jabber domains that it knows about. The presence manager does
 * not know about invisible users (they are invisible).
 *
 * @author Iain Shigeoka
 */
public interface PresenceManager {

    /**
     * Sort by username.
     */
    public static final int SORT_USERNAME = 0;

    /**
     * Sort by online time.
     */
    public static final int SORT_ONLINE_TIME = 1;

    /**
     * <p>Returns the availability of the user.<p>
     *
     * @param user the user who's availability is in question
     * @return true if the user as available for messaging (1 or more available sessions)
     */
    public boolean isAvailable(User user);

    /**
     * Returns the user's current presence, or <tt>null</tt> if the user is unavailable.
     * If the user is connected with more than one session, the user's "most available"
     * presence status is returned.
     *
     * @param user the user.
     * @return the user's current presence.
     */
    public Presence getPresence(User user);

    /**
     * Returns all presences for the user, or <tt>null</tt> if the user is unavailable.
     *
     * @param user the user.
     * @return the Presence packets for all the users's connected sessions.
     */
    public Collection<Presence> getPresences(User user);

    /**
     * Returns the number of guests who are currently online. Guests with a
     * presence status other that online or idle will not be included.
     *
     * @return the number of online users.
     */
    public int getOnlineGuestCount();

    /**
     * Returns a Collection of users who are currently online. Online users with a
     * presence status other that online or idle will not be included.
     *
     * @return a Collection of online users.
     */
    public Collection<User> getOnlineUsers();

    /**
     * Returns a Collection of users sorted in the manner requested who are currently online.
     * Online users with a presence status other that online or idle will not be included.
     *
     * @param ascending sort ascending if true, descending if false.
     * @param sortField a valid sort field from the PresenceManager interface.
     * @return a Collection of online users.
     */
    public Collection<User> getOnlineUsers(boolean ascending, int sortField);

    /**
     * Returns a Collection of users who are currently online matching the criteria given.
     * Online users with a presence status other than online or idle will not be included.
     *
     * @param ascending sort ascending if true, descending if false.
     * @param sortField  a valid sort field from the PresenceManager interface.
     * @param numResults - the number of results to return.
     * @return an Collection of online users matching the given criteria.
     */
    public Collection<User> getOnlineUsers(boolean ascending, int sortField, int numResults);

    /**
     * Create a presence for a user. Creating a presence will automatically set the user to be
     * online.<p>
     * <p/>
     * The uid should be unique within the application instance. A good source of a uid is the
     * servlet session id.
     *
     * @param user the user to create a presence for.
     * @param uid  a unique string.
     * @return the presence for the user.
     * @throws UnauthorizedException if not the user.
     */
    public Presence createPresence(User user, String uid) throws UnauthorizedException;

    /**
     * Sets a presence to be offline which causes the presence to be removed from the system.
     *
     * @param presence to presence to set to be offline.
     * @throws UnauthorizedException if not the user.
     */
    public void setOffline(Presence presence) throws UnauthorizedException;

    /**
     * Sets a user to be offline which causes the presence to be removed from the system.
     *
     * @param jid the user to set to be offline.
     * @throws UnauthorizedException if not the user.
     */
    public void setOffline(XMPPAddress jid) throws UnauthorizedException;

    /**
     * Probes the presence of the given XMPPAddress and attempts to send it to the given user.
     *
     * @param prober The user requesting the probe
     * @param probee The XMPPAddress whos presence we would like sent have have probed
     */
    public void probePresence(String prober, XMPPAddress probee) throws UnauthorizedException;

    /**
     * Probes the presence of the given XMPPAddress and attempts to send it to the given user.
     *
     * @param prober The user requesting the probe
     * @param probee The XMPPAddress whos presence we would like sent have have probed
     */
    public void probePresence(XMPPAddress prober, XMPPAddress probee) throws UnauthorizedException;
}