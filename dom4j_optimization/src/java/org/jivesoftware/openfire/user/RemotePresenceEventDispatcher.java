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

import org.xmpp.packet.Presence;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dispatches presence events of remote users. The following events are supported:
 * <ul>
 * <li><b>remoteUserAvailable</b> --> A remote user is now available.</li>
 * <li><b>remoteUserUnavailable</b> --> A remote user is no longer available.</li>
 * </ul>
 * Use {@link #addListener(RemotePresenceEventListener)} and
 * {@link #removeListener(RemotePresenceEventListener)} to add or remove {@link RemotePresenceEventListener}.
 *
 * @author Armando Jagucki
 */
public class RemotePresenceEventDispatcher {

    private static List<RemotePresenceEventListener> listeners =
            new CopyOnWriteArrayList<RemotePresenceEventListener>();

    /**
     * Registers a listener to receive events.
     *
     * @param listener the listener.
     */
    public static void addListener(RemotePresenceEventListener listener) {
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
    public static void removeListener(RemotePresenceEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Notification message indicating that a remote user is now available or has changed
     * his available presence. This event is triggered when an available presence is received
     * by <tt>PresenceRouter</tt>.
     *
     * @param presence the received available presence.
     */
    public static void remoteUserAvailable(Presence presence) {
        if (!listeners.isEmpty()) {
            for (RemotePresenceEventListener listener : listeners) {
                listener.remoteUserAvailable(presence);
            }
        }
    }

    /**
     * Notification message indicating that a remote user that was available is no longer
     * available. A remote user becomes unavailable when an unavailable presence is received.
     * by <tt>PresenceRouter</tt>.
     *
     * @param presence the received unavailable presence.
     */
    public static void remoteUserUnavailable(Presence presence) {
        if (!listeners.isEmpty()) {
            for (RemotePresenceEventListener listener : listeners) {
                listener.remoteUserUnavailable(presence);
            }
        }
    }

}
