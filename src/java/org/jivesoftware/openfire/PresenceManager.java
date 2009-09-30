/**
 * $RCSfile$
 * $Revision: 1661 $
 * $Date: 2005-07-21 00:06:49 -0300 (Thu, 21 Jul 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire;

import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

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
     * Probes the presence of the given XMPPAddress and attempts to send it to the given user. If
     * the user probing the presence is using his bare JID then the probee's presence will be
     * sent to all connected resources of the prober. 
     *
     * @param prober The user requesting the probe
     * @param probee The XMPPAddress whos presence we would like sent have have probed
     */
    public void probePresence(JID prober, JID probee);

    /**
     * Handle a presence probe sent by a remote server. The logic to apply is the following: If
     * the remote user is not in the local user's roster with a subscription state of "From", or
     * "Both", then return a presence stanza of type "error" in response to the presence probe.
     * Otherwise, answer the presence of the local user sessions or the last unavailable presence.
     *
     * @param packet the received probe presence from a remote server.
     */
    public void handleProbe(Presence packet) throws UnauthorizedException;

    /**
     * Returns true if the the prober is allowed to see the presence of the probee.
     *
     * @param prober the user that is trying to probe the presence of another user.
     * @param probee the username of the uset that is being probed.
     * @return true if the the prober is allowed to see the presence of the probee.
     * @throws UserNotFoundException If the probee does not exist in the local server or the prober
     *         is not present in the roster of the probee.
     */
    public boolean canProbePresence(JID prober, String probee) throws UserNotFoundException;

    /**
     * Sends unavailable presence from all of the user's available resources to the remote user.
     * When a remote user unsubscribes from the presence of a local user then the server should
     * send to the remote user unavailable presence from all of the local user's available
     * resources. Moreover, if the recipient user is a local user then the unavailable presence
     * will be sent to all user resources.
     *
     * @param recipientJID JID of the remote user that will receive the unavailable presences.
     * @param userJID JID of the local user.
     */
    public void sendUnavailableFromSessions(JID recipientJID, JID userJID);

    /**
     * Notification message saying that the sender of the given presence just became available.
     *
     * @param presence the presence sent by the available user.
     */
    public void userAvailable(Presence presence);

    /**
     * Notification message saying that the sender of the given presence just became unavailable.
     *
     * @param presence the presence sent by the unavailable user.
     */
    public void userUnavailable(Presence presence);

    /**
     * Returns the status sent by the user in his last unavailable presence or <tt>null</tt> if the
     * user is online or never set such information.
     *
     * @param user the user to return his last status information
     * @return the status sent by the user in his last unavailable presence or <tt>null</tt> if the
     *         user is online or never set such information.
     */
    public String getLastPresenceStatus(User user);

    /**
     * Returns the number of milliseconds since the user went offline or -1 if such information
     * is not available or if the user is online.
     *
     * @param user the user to return his information.
     * @return the number of milliseconds since the user went offline or -1 if such information
     *         is not available or if the user is online.
     */
    public long getLastActivity(User user);
}