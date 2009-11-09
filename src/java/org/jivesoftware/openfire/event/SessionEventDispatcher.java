/**
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

package org.jivesoftware.openfire.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jivesoftware.openfire.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dispatches session events. Each event has a {@link EventType type}
 *
 * @author Matt Tucker
 */
public class SessionEventDispatcher {

	private static final Logger Log = LoggerFactory.getLogger(SessionEventDispatcher.class);

    private static List<SessionEventListener> listeners =
            new CopyOnWriteArrayList<SessionEventListener>();

    private SessionEventDispatcher() {
        // Not instantiable.
    }

    /**
     * Registers a listener to receive events.
     *
     * @param listener the listener.
     */
    public static void addListener(SessionEventListener listener) {
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
    public static void removeListener(SessionEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Dispatches an event to all listeners.
     *
     * @param session the session.
     * @param eventType the event type.
     */
    public static void dispatchEvent(Session session, EventType eventType) {
        for (SessionEventListener listener : listeners) {
            try {
                switch (eventType) {
                    case session_created: {
                        listener.sessionCreated(session);
                        break;
                    }
                    case session_destroyed: {
                        listener.sessionDestroyed(session);
                        break;
                    }
                    case anonymous_session_created: {
                      listener.anonymousSessionCreated(session);
                      break;
                    }
                    case anonymous_session_destroyed: {
                      listener.anonymousSessionDestroyed(session);
                      break;
                    }
                    case resource_bound: {
                      listener.resourceBound(session);
                      break;
                    }
                    default:
                        break;
                }
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
        }
    }

    /**
     * Represents valid event types.
     */
    public enum EventType {

        /**
         * A session was created.
         */
        session_created,

        /**
         * A session was destroyed
         */
        session_destroyed,
        
        /**
         * An anonymous session was created.
         */
        anonymous_session_created,

        /**
         * A anonymous session was destroyed
         */
        anonymous_session_destroyed,

        /**
         * A resource was bound to the session.
         */
        resource_bound
    }
}