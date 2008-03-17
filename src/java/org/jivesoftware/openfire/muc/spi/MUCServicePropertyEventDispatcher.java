/**
 * $RCSfile$
 * $Revision: 8589 $
 * $Date: 2007-06-21 16:08:03 -0400 (Thu, 21 Jun 2007) $
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.muc.spi;

import org.jivesoftware.util.Log;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Dispatches property events. Each event has a {@link EventType type}
 * and optional parameters, as follows:<p>
 *
 * <table border="1">
 * <tr><th>Event Type</th><th>Extra Params</th></tr>
 * <tr><td>{@link EventType#property_set property_set}</td><td>A param named <tt>value</tt> that
 *      has the value of the property set.</td></tr>
 * <tr><td>{@link EventType#property_deleted property_deleted}</td><td><i>None</i></td></tr>
 * </table>
 *
 * @author Daniel Henninger
 */
public class MUCServicePropertyEventDispatcher {

    private static Set<MUCServicePropertyEventListener> listeners =
            new CopyOnWriteArraySet<MUCServicePropertyEventListener>();

    private MUCServicePropertyEventDispatcher() {
        // Not instantiable.
    }

    /**
     * Registers a listener to receive events.
     *
     * @param listener the listener.
     */
    public static void addListener(MUCServicePropertyEventListener listener) {
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
    public static void removeListener(MUCServicePropertyEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Dispatches an event to all listeners.
     *
     * @param service the subdomain of the MUC service the property is set for.
     * @param property the property.
     * @param eventType the event type.
     * @param params event parameters.
     */
    public static void dispatchEvent(String service, String property, EventType eventType, Map<String, Object> params) {
        for (MUCServicePropertyEventListener listener : listeners) {
            try {
                switch (eventType) {
                    case property_set: {
                        listener.propertySet(service, property, params);
                        break;
                    }
                    case property_deleted: {
                        listener.propertyDeleted(service, property, params);
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
         * A property was set.
         */
        property_set,

        /**
         * A property was deleted.
         */
        property_deleted
    }
}