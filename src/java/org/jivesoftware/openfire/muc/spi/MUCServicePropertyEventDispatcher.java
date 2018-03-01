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

package org.jivesoftware.openfire.muc.spi;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dispatches property events. Each event has a {@link EventType type}
 * and optional parameters, as follows:<p>
 *
 * <table border="1">
 * <caption></caption>
 * <tr><th>Event Type</th><th>Extra Params</th></tr>
 * <tr><td>{@link EventType#property_set property_set}</td><td>A param named <tt>value</tt> that
 *      has the value of the property set.</td></tr>
 * <tr><td>{@link EventType#property_deleted property_deleted}</td><td><i>None</i></td></tr>
 * </table>
 *
 * @author Daniel Henninger
 */
public class MUCServicePropertyEventDispatcher {

    private static final Logger Log = LoggerFactory.getLogger(MUCServicePropertyEventDispatcher.class);

    private static Set<MUCServicePropertyEventListener> listeners =
            new CopyOnWriteArraySet<>();

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
                Log.error(e.getMessage(), e);
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
