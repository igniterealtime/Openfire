/*
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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dispatches presence events. The following events are supported:
 * <ul>
 * <li><b>availableSession</b> --&gt; A session is now available to receive communication.</li>
 * <li><b>unavailableSession</b> --&gt; A session is no longer available.</li>
 * <li><b>presencePriorityChanged</b> --&gt; The priority of a resource has changed.</li>
 * <li><b>presenceChanged</b> --&gt; The show or status value of an available session has changed.</li>
 * </ul>
 * Use {@link #addListener(PresenceEventListener)} and
 * {@link #removeListener(PresenceEventListener)} to add or remove {@link PresenceEventListener}.
 *
 * @author Gaston Dombiak
 */
public class PresenceEventDispatcher {
    private static final Logger Log = LoggerFactory.getLogger(PresenceEventDispatcher.class);

    private static List<PresenceEventListener> listeners =
            new CopyOnWriteArrayList<>();

    /**
     * Registers a listener to receive events.
     *
     * @param listener the listener.
     */
    public static void addListener(PresenceEventListener listener) {
        if (listener == null) {
            throw new NullPointerException();
        }
        listeners.add(listener);
    }

    /**
     * Unregisters a listener to receive events.
     *
     * @param listener the listener.
     */
    public static void removeListener(PresenceEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Notification message indicating that a session that was not available is now
     * available. A session becomes available when an available presence is received.
     * Sessions that are available will have a route in the routing table thus becoming
     * eligible for receiving messages (in particular messages sent to the user bare JID).
     *
     * @param session the session that is now available.
     * @param presence the received available presence.
     */
    public static void availableSession(ClientSession session, Presence presence) {
        if (!listeners.isEmpty()) {
            for (PresenceEventListener listener : listeners) {
                try {
                    listener.availableSession(session, presence);
                } catch (Exception e) {
                    Log.warn("An exception occurred while dispatching a 'availableSession' event!", e);
                }
            }
        }
    }

    /**
     * Notification message indicating that a session that was available is no longer
     * available. A session becomes unavailable when an unavailable presence is received.
     * The entity may still be connected to the server and may send an available presence
     * later to indicate that communication can proceed.
     *
     * @param session the session that is no longer available.
     * @param presence the received unavailable presence.
     */
    public static void unavailableSession(ClientSession session, Presence presence) {
        if (!listeners.isEmpty()) {
            for (PresenceEventListener listener : listeners) {
                try {
                    listener.unavailableSession(session, presence);
                } catch (Exception e) {
                    Log.warn("An exception occurred while dispatching a 'unavailableSession' event!", e);
                }
            }
        }
    }


    /**
     * Notification message indicating that an available session has changed its
     * presence. This is the case when the user presence changed the show value
     * (e.g. away, dnd, etc.) or the presence status message.
     *
     * @param session the affected session.
     * @param presence the received available presence with the new information.
     */
    public static void presenceChanged(ClientSession session, Presence presence) {
        if (!listeners.isEmpty()) {
            for (PresenceEventListener listener : listeners) {
                try {
                    listener.presenceChanged(session, presence); 
                } catch (Exception e) {
                    Log.warn("An exception occurred while dispatching a 'presenceChanged' event!", e);
                }
            }
        }
    }

    /**
     * Notification message indicating that a user has successfully subscribed
     * to the presence of another user.
     * 
     * @param subscriberJID the user that initiated the subscription.
     * @param authorizerJID the user that authorized the subscription.
     */
    public static void subscribedToPresence(JID subscriberJID, JID authorizerJID) {
        if (!listeners.isEmpty()) {
            for (PresenceEventListener listener : listeners) {
                try {
                    listener.subscribedToPresence(subscriberJID, authorizerJID);   
                } catch (Exception e) {
                    Log.warn("An exception occurred while dispatching a 'subscribedToPresence' event!", e);
                }
            }
        }
    }
    
    /**
     * Notification message indicating that a user has unsubscribed
     * to the presence of another user.
     * 
     * @param unsubscriberJID the user that initiated the unsubscribe request.
     * @param recipientJID    the recipient user of the unsubscribe request.
     */
    public static void unsubscribedToPresence(JID unsubscriberJID, JID recipientJID) {
        if (!listeners.isEmpty()) {
            for (PresenceEventListener listener : listeners) {
                try {
                    listener.unsubscribedToPresence(unsubscriberJID, recipientJID); 
                } catch (Exception e) {
                    Log.warn("An exception occurred while dispatching a 'unsubscribedToPresence' event!", e);
                }
            }
        }
    }
}
