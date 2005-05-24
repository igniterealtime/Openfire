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

import org.jivesoftware.messenger.user.User;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.xmpp.packet.Presence;
import org.xmpp.packet.JID;

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
     * @param username the name of the user.
     * @return the Presence packets for all the users's connected sessions.
     */
    public Collection<Presence> getPresences(String username);

    /**
     * Probes the presence of the given XMPPAddress and attempts to send it to the given user.
     *
     * @param prober The user requesting the probe
     * @param probee The XMPPAddress whos presence we would like sent have have probed
     */
    public void probePresence(JID prober, JID probee);

    /**
     * Saves the last unavailable presence of the user so that future presence probes may answer
     * this presence which may contain valuable information. If a last unavailable presence does
     * not exist for a contact then no unavailable presence will be sent to the owner of the
     * contact.
     *
     * @param username the name of the user whose last presence is going to be saved to the
     *        database.
     * @param presence the last unavailable presence to save to the database for this user.
     */
    public void saveLastUnavailablePresence(String username, Presence presence);

    /**
     * Deletes the last unavailable presence of a user. This may be necessary if the user has
     * become available.
     *
     * @param username the name of the user whose last presence is going to be delete from the
     *        database.
     */
    public void deleteLastUnavailablePresence(String username);

    /**
     * Handle a presence probe sent by a remote server. The logic to apply is the following: If
     * the remote user is not in the local user's roster with a subscription state of "From", or
     * "Both", then return a presence stanza of type "error" in response to the presence probe.
     * Otherwise, answer the presence of the local user sessions or the last unavailable presence.
     *
     * @param packet the received probe presence from a remote server.
     */
    void handleProbe(Presence packet) throws UnauthorizedException;
}