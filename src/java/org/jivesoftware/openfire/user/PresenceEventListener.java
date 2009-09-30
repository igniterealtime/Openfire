/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.user;

import org.jivesoftware.openfire.session.ClientSession;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

/**
 * Interface to listen for presence events. Use the
 * {@link PresenceEventDispatcher#addListener(PresenceEventListener)}
 * method to register for events.
 *
 * @author Gaston Dombiak
 */
public interface PresenceEventListener {

    /**
     * Notification message indicating that a session that was not available is now
     * available. A session becomes available when an available presence is received.
     * Sessions that are available will have a route in the routing table thus becoming
     * eligible for receiving messages (in particular messages sent to the user bare JID).
     *
     * @param session the session that is now available.
     * @param presence the received available presence.
     */
    public void availableSession(ClientSession session, Presence presence);

    /**
     * Notification message indicating that a session that was available is no longer
     * available. A session becomes unavailable when an unavailable presence is received.
     * The entity may still be connected to the server and may send an available presence
     * later to indicate that communication can proceed.
     *
     * @param session the session that is no longer available.
     * @param presence the received unavailable presence.
     */
    public void unavailableSession(ClientSession session, Presence presence);

    /**
     * Notification message indicating that an available session has changed its
     * presence. This is the case when the user presence changed the show value
     * (e.g. away, dnd, etc.) or the presence status message.
     *
     * @param session the affected session.
     * @param presence the received available presence with the new information.
     */
    public void presenceChanged(ClientSession session, Presence presence);

    /**
     * Notification message indicating that a user has successfully subscribed
     * to the presence of another user.
     * 
     * @param subscriberJID the user that initiated the subscription.
     * @param authorizerJID the user that authorized the subscription.
     */
    public void subscribedToPresence(JID subscriberJID, JID authorizerJID);

    /**
     * Notification message indicating that a user has unsubscribed
     * to the presence of another user.
     * 
     * @param unsubscriberJID the user that initiated the unsubscribe request.
     * @param recipientJID    the recipient user of the unsubscribe request.
     */
    public void unsubscribedToPresence(JID unsubscriberJID, JID recipientJID);
}
