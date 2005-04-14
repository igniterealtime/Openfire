/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.group;

import org.jivesoftware.messenger.user.User;

import java.util.Collection;

/**
 * Provider interface for groups. Users that wish to integrate with
 * their own group system must implement this class and then register
 * the implementation with Jive Messenger in the <tt>jive-messenger.xml</tt>
 * file. An entry in that file would look like the following:
 *
 * <pre>
 *   &lt;provider&gt;
 *     &lt;group&gt;
 *       &lt;className&gt;com.foo.auth.CustomGroupProvider&lt;/className&gt;
 *     &lt;/group&gt;
 *   &lt;/provider&gt;</pre>
 *
 * @author Matt Tucker
 */
public interface GroupProvider {

    /**
     * Creates a group with the given name (optional operation).<p>
     *
     * The provider is responsible for setting the creation date and
     * modification date to the current date/time.
     *
     * @param name name of the group.
     * @return the newly created group.
     * @throws GroupAlreadyExistsException if a group with the same name already
     *      exists.
     * @throws UnsupportedOperationException if the provider does not
     *      support the operation.
     */
    Group createGroup(String name) throws UnsupportedOperationException,
            GroupAlreadyExistsException;

    /**
     * Deletes the group (optional operation).
     *
     * @param name the name of the group to delete
     * @throws UnsupportedOperationException if the provider does not
     *      support the operation.
     */
    void deleteGroup(String name) throws UnsupportedOperationException;

    /**
     * Returns a group based on it's name.
     *
     * @param name the name of the group.
     * @return the group with the given name.
     * @throws GroupNotFoundException If no group with that ID could be found
     */
    Group getGroup(String name) throws GroupNotFoundException;

    /**
     * Sets the name of a group to a new name.
     *
     * @param oldName the current name of the group.
     * @param newName the desired new name of the group.
     * @throws GroupAlreadyExistsException if a group with the same name already
     *      exists.
     * @throws UnsupportedOperationException if the provider does not
     *      support the operation.
     */
    void setName(String oldName, String newName) throws UnsupportedOperationException,
            GroupAlreadyExistsException;

    /**
     * Updates the group's description.
     *
     * @param name the group name.
     * @param description the group description.
     * @throws GroupNotFoundException if no existing group could be found to update.
     */
    void setDescription(String name, String description)
            throws GroupNotFoundException;

    /**
     * Returns the number of groups in the system.
     *
     * @return the number of groups in the system.
     */
    int getGroupCount();

    /**
     * Returns the Collection of all groups in the system.
     *
     * @return the Collection of all groups.
     */
    Collection<Group> getGroups();

    /**
     * Returns the Collection of all groups in the system.
     *
     * @param startIndex start index in results.
     * @param numResults number of results to return.
     * @return the Collection of all groups given the <tt>startIndex</tt> and <tt>numResults</tt>.
     */
    Collection<Group> getGroups(int startIndex, int numResults);

    /**
     * Returns the Collection of Groups that a user belongs to.
     *
     * @param user the user.
     * @return the Collection of groups that the user belongs to.
     */
    Collection<Group> getGroups(User user);

    /**
     * Adds a user to a group (optional operation).
     *
     * @param groupName the group to add the member to
     * @param username the username to add
     * @param administrator True if the member is an administrator of the group
     * @throws UnsupportedOperationException if the provider does not
     *      support the operation.
     */
    void addMember(String groupName, String username, boolean administrator)
            throws UnsupportedOperationException;

    /**
     * Updates the privileges of a user in a group.
     *
     * @param groupName the group where the change happened
     * @param username the username to of the user with new privileges
     * @param administrator True if the member is an administrator of the group
     * @throws UnsupportedOperationException if the provider does not
     *      support the operation.
     */
    void updateMember(String groupName, String username, boolean administrator)
            throws UnsupportedOperationException;

    /**
     * Deletes a user from a group (optional operation).
     *
     * @param groupName the group name.
     * @param username the username.
     * @throws UnsupportedOperationException if the provider does not
     *      support the operation.
     */
    void deleteMember(String groupName, String username) throws UnsupportedOperationException;
}