/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright 2004 Jive Software.
 *
 * All rights reserved. Licensed under the Apache License, Version 2.0 (the "License");
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

package org.jivesoftware.messenger.event;

import org.jivesoftware.util.Log;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dispatches group events.
 *
 * @author Matt Tucker
 */
public class GroupEventDispatcher {

    private static List<GroupEventListener> listeners =
            new CopyOnWriteArrayList<GroupEventListener>();

    private GroupEventDispatcher() {
        // Not instantiable.
    }

    /**
     * Registers a listener to receive events.
     *
     * @param listener the listener.
     */
    public static void addListener(GroupEventListener listener) {
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
    public static void removeListener(GroupEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Dispatches an event to all listeners.
     *
     * @param event the event.
     */
    public static void dispatchEvent(GroupEvent event) {
        GroupEvent.EventType eventType = event.getEventType();

        for (GroupEventListener listener : listeners) {
            try {
                switch (eventType) {
                    case group_created: {
                        listener.groupCreated(event);
                        break;
                    }
                    case group_deleting: {
                        listener.groupDeleting(event);
                        break;
                    }
                    case member_added: {
                        listener.memberAdded(event);
                        break;
                    }
                    case member_removed: {
                        listener.memberRemoved(event);
                        break;
                    }
                    case admin_added: {
                        listener.adminAdded(event);
                        break;
                    }
                    case admin_removed: {
                        listener.adminRemoved(event);
                        break;
                    }
                    case group_modified: {
                        listener.groupModified(event);
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
}