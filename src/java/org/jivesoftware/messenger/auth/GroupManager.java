/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.auth;

import org.jivesoftware.util.BasicResultFilter;
import org.jivesoftware.messenger.Entity;
import java.util.Iterator;

/**
 * <p>Manages groups.</p>
 *
 * @author Iain Shigeoka
 * @see Group
 */
public interface GroupManager {

    /**
     * <p>The name of the group cache mapping long ID to Group object.</p>
     */
    String GROUP_CACHE_NAME = "group";
    /**
     * <p>Cache of group names to group Long ID's.</p>
     */
    String GROUP_ID_CACHE_NAME = "groupID";
    /**
     * <p>Cache of String("userGroups-" + memberID) to long[]
     * array of group ID's that member belongs to.</p>
     */
    String GROUP_MEMBER_CACHE_NAME = "groupMember";

    /**
     * Factory method for creating a new Group. A unique name is the only required field.
     *
     * @param name the new and unique name for the group.
     * @return a new Group.
     * @throws GroupAlreadyExistsException if the group name already exists in the system.
     */
    Group createGroup(String name)
            throws UnauthorizedException, GroupAlreadyExistsException;

    /**
     * Gets a Group by ID.
     *
     * @param groupID The ID of the group to retrieve
     * @return The group corresponding to the given ID
     * @throws GroupNotFoundException if the group does not exist.
     */
    Group getGroup(long groupID) throws GroupNotFoundException;

    /**
     * Gets a Group by name.
     *
     * @param name The name of the group to retrieve
     * @return The group corresponding to that name
     * @throws GroupNotFoundException if the group does not exist.
     */
    Group getGroup(String name) throws GroupNotFoundException;

    /**
     * Deletes a group from the system.
     *
     * @param group the group to delete.
     * @throws UnauthorizedException if not a system administrator.
     */
    void deleteGroup(Group group) throws UnauthorizedException;

    /**
     * Returns the total number of groups in the system.
     *
     * @return the total number of groups.
     */
    int getGroupCount();

    /**
     * Returns an iterator for all groups in the system.
     *
     * @return an Iterator for all groups.
     */
    public Iterator getGroups();

    /**
     * Returns an iterator for all groups according to a filter.
     * <p/>
     * This is useful to support
     * pagination in a GUI where you may only want to display a certain
     * number of results per page. It is possible that the
     * number of results returned will be less than that specified by
     * numResults if numResults is greater than the number of records left in
     * the system to display.
     *
     * @param filter the filter to restrict results with
     * @return an Iterator for all groups in the specified range.
     */
    public Iterator getGroups(BasicResultFilter filter);

    /**
     * Returns an iterator for all groups that a user is a member of.
     *
     * @param entity the user to get a list of groups for.
     * @return all groups that a user belongs to.
     */
    public Iterator getGroups(Entity entity);
}