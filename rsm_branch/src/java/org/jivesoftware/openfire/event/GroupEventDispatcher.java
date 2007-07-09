/**
 * $RCSfile$
 * $Revision: 1705 $
 * $Date: 2005-07-26 14:10:33 -0300 (Tue, 26 Jul 2005) $
 *
 * Copyright (C) 2004-2005 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.event;

import org.jivesoftware.util.Log;
import org.jivesoftware.openfire.group.Group;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dispatches group events. Each event has a {@link EventType type}
 * and optional parameters, as follows:<p>
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
     * @param group the group.
     * @param eventType the event type.
     * @param params event parameters.
     */
    public static void dispatchEvent(Group group, EventType eventType, Map params) {
        for (GroupEventListener listener : listeners) {
            try {
                switch (eventType) {
                    case group_created: {
                        listener.groupCreated(group, params);
                        break;
                    }
                    case group_deleting: {
                        listener.groupDeleting(group, params);
                        break;
                    }
                    case member_added: {
                        listener.memberAdded(group, params);
                        break;
                    }
                    case member_removed: {
                        listener.memberRemoved(group, params);
                        break;
                    }
                    case admin_added: {
                        listener.adminAdded(group, params);
                        break;
                    }
                    case admin_removed: {
                        listener.adminRemoved(group, params);
                        break;
                    }
                    case group_modified: {
                        listener.groupModified(group, params);
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