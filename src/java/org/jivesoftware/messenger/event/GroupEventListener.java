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

/**
 * Interface to listen for group events. Use the
 * {@link GroupEventDispatcher#addListener(GroupEventListener)}
 * method to register for events.
 *
 * @author Matt Tucker
 */
public interface GroupEventListener {

    /**
     * A group was created.
     *
     * @param event the event.
     */
    public void groupCreated(GroupEvent event);

    /**
     * A group is being deleted.
     *
     * @param event the event.
     */
    public void groupDeleting(GroupEvent event);

    /**
     * A group's name, description, or an extended property was changed.
     *
     * @param event the event.
     */
    public void groupModified(GroupEvent event);

    /**
     * A member was added to a group.
     *
     * @param event the event.
     */
    public void memberAdded(GroupEvent event);

    /**
     * A member was removed from a group.
     *
     * @param event the event.
     */
    public void memberRemoved(GroupEvent event);

    /**
     * An administrator was added to a group.
     *
     * @param event the event.
     */
    public void adminAdded(GroupEvent event);

    /**
     * An administrator was removed from a group.
     *
     * @param event the event.
     */
    public void adminRemoved(GroupEvent event);
}