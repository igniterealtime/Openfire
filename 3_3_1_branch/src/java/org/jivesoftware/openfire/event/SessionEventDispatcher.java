/**
 * Copyright (C) 2004-2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.event;

import org.jivesoftware.util.Log;
import org.jivesoftware.openfire.session.Session;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dispatches session events. Each event has a {@link EventType type}
 *
 * @author Matt Tucker
 */
public class SessionEventDispatcher {

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
                   
                    default:
                        break;
                }
            }
            catch (Exception e) {
                Log.error(e);
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
                
    }
}