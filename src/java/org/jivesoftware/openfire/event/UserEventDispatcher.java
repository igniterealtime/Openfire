/**
 * $RCSfile$
 * $Revision: 1705 $
 * $Date: 2005-07-26 14:10:33 -0300 (Tue, 26 Jul 2005) $
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

package org.jivesoftware.openfire.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jivesoftware.openfire.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dispatches user events. Each event has a {@link EventType type}
 * and optional parameters, as follows:<p>
 *
 * <table border="1">
 * <tr><th>Event Type</th><th>Extra Params</th></tr>
 * <tr><td>{@link EventType#user_created user_created}</td><td><i>None</i></td></tr>
 * <tr><td>{@link EventType#user_deleting user_deleting}</td><td><i>None</i></td></tr>
 * <tr valign="top"><td>{@link EventType#user_modified user_modified}</td><td>
 * <table><tr><td><b>Reason</b></td><td><b>Key</b></td><td><b>Value</b></td></tr>
 *      <tr><td colspan="3">Name modified</td></tr>
 *      <tr><td>&nbsp;</td><td>type</td><td>nameModified</td></tr>
 *      <tr><td>&nbsp;</td><td>originalValue</td><td><i>(Name before it was modified)</i></td></tr>
 *
 *      <tr><td colspan="3"><hr></td></tr>
 *      <tr><td colspan="3">Email modified</td></tr>
 *      <tr><td>&nbsp;</td><td>type</td><td>emailModified</td></tr>
 *      <tr><td>&nbsp;</td><td>originalValue</td><td><i>(Email before it was
 * modified)</i></td></tr>
 *      <tr><td colspan="3"><hr></td></tr>
 *
 *      <tr><td colspan="3">Password modified</td></tr>
 *      <tr><td>&nbsp;</td><td>type</td><td>passwordModified</td></tr>
 *      <tr><td colspan="3"><hr></td></tr>
 *
 *      <tr><td colspan="3">Creation date modified</td></tr>
 *      <tr><td>&nbsp;</td><td>type</td><td>creationDateModified</td></tr>
 *      <tr><td>&nbsp;</td><td>originalValue</td><td><i>(Creation date before it was
 * modified)</i></td></tr>
 *      <tr><td colspan="3"><hr></td></tr>
 *
 *      <tr><td colspan="3">Modification date modified</td></tr>
 *      <tr><td>&nbsp;</td><td>type</td><td>modificationDateModified</td></tr>
 *      <tr><td>&nbsp;</td><td>originalValue</td><td><i>(Modification date before it was
 * modified)</i></td></tr>
 *      <tr><td colspan="3"><hr></td></tr>
 *
 *      <tr><td colspan="3">Property modified</td></tr>
 *      <tr><td>&nbsp;</td><td>type</td><td>propertyModified</td></tr>
 *      <tr><td>&nbsp;</td><td>propertyKey</td><td><i>(Name of the property)</i></td>
 * </tr>
 *      <tr><td>&nbsp;</td><td>originalValue</td><td><i>(Property value before it was
 * modified)</i></td></tr>
 *      <tr><td colspan="3"><hr></td></tr>
 *      <tr><td colspan="3">Property added</td></tr>
 *      <tr><td>&nbsp;</td><td>type</td><td>propertyAdded</td></tr>
 *      <tr><td>&nbsp;</td><td>propertyKey</td><td><i>(Name of the new property)</i></td>
 * </tr>
 *      <tr><td colspan="3"><hr></td></tr>
 *      <tr><td colspan="3">Property deleted</td></tr>
 *      <tr><td>&nbsp;</td><td>type</td><td>propertyDeleted</td></tr>
 *      <tr><td>&nbsp;</td><td>propertyKey</td><td><i>(Name of the property deleted)</i></td></tr></table></td></tr>
 * </table>
 *
 * @author Matt Tucker
 */
public class UserEventDispatcher {

	private static final Logger Log = LoggerFactory.getLogger(UserEventDispatcher.class);

    private static List<UserEventListener> listeners =
            new CopyOnWriteArrayList<UserEventListener>();

    private UserEventDispatcher() {
        // Not instantiable.
    }

    /**
     * Registers a listener to receive events.
     *
     * @param listener the listener.
     */
    public static void addListener(UserEventListener listener) {
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
    public static void removeListener(UserEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Dispatches an event to all listeners.
     *
     * @param user the user.
     * @param eventType the event type.
     * @param params event parameters.
     */
    public static void dispatchEvent(User user, EventType eventType, Map<String,Object> params) {
        for (UserEventListener listener : listeners) {
            try {
                switch (eventType) {
                    case user_created: {
                        listener.userCreated(user, params);
                        break;
                    }
                    case user_deleting: {
                        listener.userDeleting(user, params);
                        break;
                    }
                    case user_modified: {
                        listener.userModified(user, params);
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
         * A new user was created.
         */
        user_created,

        /**
         * A user is about to be deleted. Note that this event is fired before
         * a user is actually deleted. This allows for referential cleanup
         * before the user is gone.
         */
        user_deleting,

        /**
         * The name, email, password, or extended property of a user was changed.
         */
        user_modified,
    }
}