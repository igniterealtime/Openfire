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

package org.jivesoftware.messenger.auth;

import org.jivesoftware.util.BasicResultFilter;
import org.jivesoftware.util.LongList;
import org.jivesoftware.messenger.user.UserAlreadyExistsException;

/**
 * The group permission interface to implement when creating an
 * authentication service plug-in. Implementations of group provider
 * handles the persistent storage access for group in Messenger. Groups
 * are associated with Entities (users or chatbots) strictly by the long ID
 * for the entities. This allows groups to be handled by Jive while the user
 * store is in another backend system, or groups can be handled
 * separately from a native Jive user table.
 *
 * @author Iain Shigeoka
 */
public interface GroupProvider {

    /**
     * <p>Create a group with the given name (optional operation).</p>
     * <p/>
     * <p>The provider is responsible for setting the creation date and
     * modification date to the current date/time and generating a unique
     * group ID for the group.</p>
     *
     * @param name
     * @return The created group
     * @throws UnauthorizedException         If the password is invalid or
     *                                       the caller does not have permission to make the change
     * @throws UnsupportedOperationException If the provider does not
     *                                       support the operation (this is an optional operation)
     * @throws GroupAlreadyExistsException   if the group name already
     *                                       exists in the system.
     */
    Group createGroup(String name)
            throws UnauthorizedException,
            UnsupportedOperationException,
            GroupAlreadyExistsException;

    /**
     * <p>Deletes the group (optional operation).</p>
     *
     * @param groupID The ID of the group to delete
     * @throws UnauthorizedException If the caller does not have permission to
     *                               make the change
     */
    void deleteGroup(long groupID) throws UnauthorizedException;

    /**
     * <p>Obtains a group by it's long ID.</p>
     *
     * @param groupID The ID of the group
     * @return The group with the given ID
     * @throws GroupNotFoundException If no group with that ID could be found
     */
    Group getGroup(long groupID) throws GroupNotFoundException;

    /**
     * <p>Obtain a group given it's name.</p>
     *
     * @param name The name of the group
     * @return The the group with the given name
     * @throws GroupNotFoundException If no group with that name exists
     */
    Group getGroup(String name) throws GroupNotFoundException;

    /**
     * <p>Updates the backend storage with the group's information.</p>
     * <p/>
     * <p>The system will adjust the group's properties then call this
     * method so the backend storage can update the group information.</p>
     *
     * @param group The group to update
     * @throws UnauthorizedException  If the caller does not have permission to
     *                                make the change
     * @throws GroupNotFoundException If no existing group could be found to update
     */
    void updateGroup(Group group) throws UnauthorizedException, GroupNotFoundException;

    /**
     * <p>Obtain the number of groups in the system.</p>
     *
     * @return The number of groups in the system
     */
    int getGroupCount();

    /**
     * <p>Obtain a list of all Group IDs known by the provider.</p>
     *
     * @return The list of IDs of the groups on the system
     */
    LongList getGroups();

    /**
     * <p>Obtain a list of all Group IDs known by the provider
     * restricted by the given resut filter.</p>
     *
     * @param filter The filter to apply to the results before returning them
     * @return The list of IDs of the groups on the system after filtering
     */
    LongList getGroups(BasicResultFilter filter);

    /**
     * <p>Obtain an list of all Group IDs known by the provider
     * that a given entity belongs to.</p>
     *
     * @param entityID The ID of the entity to find groups for
     * @return The list of IDs of the groups that the entity belongs to
     */
    LongList getGroups(long entityID);

    /**
     * <p>Creates a membership relationship between the
     * group and the entity (optional operation).</p>
     *
     * @param groupID       The group to add the member to
     * @param entityID      The entity to add
     * @param administrator True if the member is an administrator of the group
     * @throws UnauthorizedException         If the password is invalid or
     *                                       the caller does not have permission to make the change
     * @throws UnsupportedOperationException If the provider does not
     *                                       support the operation (this is an optional operation)
     * @throws UserAlreadyExistsException    If the given entity is already a member
     *                                       of the group
     */
    void createMember(long groupID, long entityID, boolean administrator)
            throws UnauthorizedException,
            UserAlreadyExistsException,
            UnsupportedOperationException;

    /**
     * <p>Updates a membership relationship between the
     * group and the entity (optional operation).</p>
     *
     * @param groupID       The group to add the member to
     * @param entityID      The entity to add
     * @param administrator True if the member is an administrator of the group
     * @throws UnauthorizedException         If the password is invalid or
     *                                       the caller does not have permission to make the change
     * @throws UnsupportedOperationException If the provider does not
     *                                       support the operation (this is an optional operation)
     */
    void updateMember(long groupID, long entityID, boolean administrator)
            throws UnauthorizedException, UnsupportedOperationException;

    /**
     * <p>Deletes a membership relationship between the
     * group and the entity (optional operation).</p>
     *
     * @param groupID  The group to add the member to
     * @param entityID The entity to add
     * @throws UnauthorizedException         If the password is invalid or
     *                                       the caller does not have permission to make the change
     * @throws UnsupportedOperationException If the provider does not
     *                                       support the operation (this is an optional operation)
     */
    void deleteMember(long groupID, long entityID)
            throws UnauthorizedException, UnsupportedOperationException;

    /**
     * <p>Obtains a counto f the number of members in the group.</p>
     *
     * @param groupID    The group to add the member to
     * @param adminsOnly True if the member count should be restricted to
     *                   administrators only
     */
    int getMemberCount(long groupID, boolean adminsOnly);

    /**
     * <p>Obtain a list of all entity ID's of members of the given group.</p>
     *
     * @param groupID    The ID of the group to locate members for
     * @param adminsOnly True if the results should be restricted to administrators only
     * @return The list of IDs of the entities that belongs to the group
     */
    LongList getMembers(long groupID, boolean adminsOnly);

    /**
     * <p>Obtain a list of all entity ID's of members of the given group
     * restricted by the given filter.</p>
     *
     * @param groupID    The ID of the group to locate members for
     * @param filter     The filter to restrict the results list
     * @param adminsOnly True if the results should be restricted to administrators only
     * @return The list of IDs of the entities that belongs to the group
     */
    LongList getMembers(long groupID, BasicResultFilter filter, boolean adminsOnly);

    /**
     * <p>Determine if a given entity is a member of a group.</p>
     *
     * @param groupID    The ID of the group
     * @param entityID   The ID of the entity being checked for membership in the group
     * @param adminsOnly True if the entity must be an administrator of the group
     * @return True if the entity is a member of the group
     */
    boolean isMember(long groupID, long entityID, boolean adminsOnly);

    /**
     * <p>Creates a property on a group (optional operation).</p>
     *
     * @param groupID The ID of the group
     * @param name    The name of the property
     * @param value   The value of the property
     * @throws UnauthorizedException         If the password is invalid or
     *                                       the caller does not have permission to make the change
     * @throws UnsupportedOperationException If the provider does not
     *                                       support the operation (this is an optional operation)
     */
    void createProperty(long groupID, String name, String value)
            throws UnauthorizedException, UnsupportedOperationException;

    /**
     * <p>Updates a property on a group (optional operation).</p>
     *
     * @param groupID The ID of the group
     * @param name    The name of the property
     * @param value   The value of the property
     * @throws UnauthorizedException         If the password is invalid or
     *                                       the caller does not have permission to make the change
     * @throws UnsupportedOperationException If the provider does not
     *                                       support the operation (this is an optional operation)
     */
    void updateProperty(long groupID, String name, String value)
            throws UnauthorizedException, UnsupportedOperationException;

    /**
     * <p>Deletes a property from a group (optional operation).</p>
     *
     * @param groupID The ID of the group
     * @param name    The name of the property
     * @throws UnauthorizedException         If the password is invalid or
     *                                       the caller does not have permission to make the change
     * @throws UnsupportedOperationException If the provider does not
     *                                       support the operation (this is an optional operation)
     */
    void deleteProperty(long groupID, String name)
            throws UnauthorizedException, UnsupportedOperationException;
}
