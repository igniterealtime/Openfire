/**
 * $RCSfile$
 * $Revision: 3117 $
 * $Date: 2005-11-25 22:57:29 -0300 (Fri, 25 Nov 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.group;

import org.xmpp.packet.JID;

import java.util.Collection;

/**
 * Provider interface for groups. Users that wish to integrate with
 * their own group system must implement this class and then register
 * the implementation with Openfire in the <tt>openfire.xml</tt>
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
     * @param name the name of the group to delete.
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
     * Returns the Collection of all group names in the system.
     *
     * @return the Collection of all groups.
     */
    Collection<String> getGroupNames();

    /**
     * Returns the Collection of all groups in the system.
     *
     * @param startIndex start index in results.
     * @param numResults number of results to return.
     * @return the Collection of all group names given the
     *      <tt>startIndex</tt> and <tt>numResults</tt>.
     */
    Collection<String> getGroupNames(int startIndex, int numResults);

    /**
     * Returns the Collection of group names that an entity belongs to.
     *
     * @param user the JID of the entity.
     * @return the Collection of group names that the user belongs to.
     */
    Collection<String> getGroupNames(JID user);

    /**
     * Adds an entity to a group (optional operation).
     *
     * @param groupName the group to add the member to
     * @param user the JID of the entity to add
     * @param administrator True if the member is an administrator of the group
     * @throws UnsupportedOperationException if the provider does not
     *      support the operation.
     */
    void addMember(String groupName, JID user, boolean administrator)
            throws UnsupportedOperationException;

    /**
     * Updates the privileges of an entity in a group.
     *
     * @param groupName the group where the change happened
     * @param user the JID of the entity with new privileges
     * @param administrator True if the member is an administrator of the group
     * @throws UnsupportedOperationException if the provider does not
     *      support the operation.
     */
    void updateMember(String groupName, JID user, boolean administrator)
            throws UnsupportedOperationException;

    /**
     * Deletes an entity from a group (optional operation).
     *
     * @param groupName the group name.
     * @param user the JID of the entity to delete.
     * @throws UnsupportedOperationException if the provider does not
     *      support the operation.
     */
    void deleteMember(String groupName, JID user) throws UnsupportedOperationException;

    /**
     * Returns true if this GroupProvider is read-only. When read-only,
     * groups can not be created, deleted, or modified.
     *
     * @return true if the user provider is read-only.
     */
    boolean isReadOnly();

    /**
     * Returns the group names that match a search. The search is over group names and
     * implicitly uses wildcard matching (although the exact search semantics are left
     * up to each provider implementation). For example, a search for "HR" should match
     * the groups "HR", "HR Department", and "The HR People".<p>
     *
     * Before searching or showing a search UI, use the {@link #isSearchSupported} method
     * to ensure that searching is supported.
     *
     * @param query the search string for group names.
     * @return all groups that match the search.
     */
    Collection<String> search(String query);

    /**
     * Returns the group names that match a search given a start index and desired number of results.
     * The search is over group names and implicitly uses wildcard matching (although the
     * exact search semantics are left up to each provider implementation). For example, a
     * search for "HR" should match the groups "HR", "HR Department", and "The HR People".<p>
     *
     * Before searching or showing a search UI, use the {@link #isSearchSupported} method
     * to ensure that searching is supported.
     *
     * @param query the search string for group names.
     * @param startIndex start index in results.
     * @param numResults number of results to return.
     * @return all groups that match the search.
     */
    Collection<String> search(String query, int startIndex, int numResults);

    /**
     * Returns true if group searching is supported by the provider.
     *
     * @return true if searching is supported.
     */
    boolean isSearchSupported();
}