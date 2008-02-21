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

import org.jivesoftware.openfire.group.Group;

import java.util.Map;

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
     * @param group the group.
     * @param params event parameters.
     */
    public void groupCreated(Group group, Map params);

    /**
     * A group is being deleted.
     *
     * @param group the group.
     * @param params event parameters.
     */
    public void groupDeleting(Group group, Map params);

    /**
     * A group's name, description, or an extended property was changed.
     *
     * @param group the group.
     * @param params event parameters.
     */
    public void groupModified(Group group, Map params);

    /**
     * A member was added to a group.
     *
     * @param group the group.
     * @param params event parameters.
     */
    public void memberAdded(Group group, Map params);

    /**
     * A member was removed from a group.
     *
     * @param group the group.
     * @param params event parameters.
     */
    public void memberRemoved(Group group, Map params);

    /**
     * An administrator was added to a group.
     *
     * @param group the group.
     * @param params event parameters.
     */
    public void adminAdded(Group group, Map params);

    /**
     * An administrator was removed from a group.
     *
     * @param group the group.
     * @param params event parameters.
     */
    public void adminRemoved(Group group, Map params);
}