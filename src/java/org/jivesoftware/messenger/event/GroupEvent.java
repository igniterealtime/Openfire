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

import org.jivesoftware.messenger.group.Group;

import java.util.Map;
import java.util.Date;

/**
 * Group event. Event types with extened parameters (if any) are as follows:<p>
 *
 * <table border="1">
 * <tr><th>Event Type</th><th>Extra Params</th></tr>
 * <tr><td>{@link EventType#group_created group_created}</td><td><i>None</i></td></tr>
 * <tr><td>{@link EventType#group_deleting group_deleting}</td><td><i>None</i></td></tr>
 * <tr><td>{@link EventType#member_added member_added}</td><td>A param named <tt>member</tt> with a String username as a
 *      payload</td></tr>
 * <tr><td>{@link EventType#member_removed member_removed}</td><td>A param named <tt>member</tt> with a String username as a
 *      payload</td></tr>
 * <tr><td>{@link EventType#admin_added admin_added}</td><td>A param named <tt>admin</tt> with a String username
 *      as a payload</td></tr>
 * <tr><td>{@link EventType#admin_removed admin_removed}</td><td>A param named <tt>admin</tt> with a String username
 *      as a payload</td></tr>
 * <tr valign="top"><td>{@link EventType#group_modified group_modified}</td><td>
 * <table><tr><td><b>Reason</b></td><td><b>Key</b></td><td><b>Value</b></td></tr>
 *      <tr><td colspan="3">Name modified</td></tr>
 *      <tr><td>&nbsp;</td><td>type</td><td>nameModified</td></tr>
 *      <tr><td>&nbsp;</td><td>originalValue</td><td><i>(Name before it was modified)</i>
 * </td></tr>
 *      <tr><td colspan="3"><hr></td></tr>
 *      <tr><td colspan="3">Description modified</td></tr>
 *      <tr><td>&nbsp;</td><td>type</td><td>descriptionModified</td></tr>
 *      <tr><td>&nbsp;</td><td>originalValue</td><td><i>(Description before it was
 * modified)</i></td></tr>
 *      <tr><td colspan="3"><hr></td></tr>
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
public class GroupEvent implements Event {

    private EventType eventType;
    private Group group;
    private Map params;
    private Date date;

    /**
     * Constructs a new group event.
     *
     * @param eventType the event type.
     * @param group the group triggering the event.
     * @param params event parameters.
     */
    public GroupEvent(EventType eventType, Group group, Map params) {
        this.eventType = eventType;
        this.group = group;
        this.params = params;
        this.date = new Date();
    }

    /**
     * Returns the type of the event.
     *
     * @return the type of the event.
     */
    public EventType getEventType() {
        return eventType;
    }

    /**
     * Returns the Group that triggered the event.
     *
     * @return the Group that triggered the event.
     */
    public Group getGroup() {
        return group;
    }

    public Map getParams() {
        return params;
    }

    public Date getDate() {
        return date;
    }

    /**
     * Represents valid event types.
     */
    public enum EventType {

        /**
         * A new group was created.
         */
        group_created,

        /**
         * A group is about to be deleted. Note that this event is fired before
         * a group is actually deleted. This allows for referential cleanup
         * before the group is gone.
         */
        group_deleting,

        /**
         * The name, description, or extended property of a group was changed.
         */
        group_modified,

        /**
         * A member was added to a group.
         */
        member_added,

        /**
         * A member was removed from a group.
         */
        member_removed,

        /**
         * An administrator was added to a group.
         */
        admin_added,

        /**
         * An administrator was removed from a group.
         */
        admin_removed;
    }
}