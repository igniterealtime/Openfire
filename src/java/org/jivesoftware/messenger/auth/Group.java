/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2002 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.auth;

import org.jivesoftware.messenger.Entity;
import org.jivesoftware.messenger.user.UserAlreadyExistsException;
import java.util.Date;
import java.util.Iterator;

/**
 * Organizes entities into a group for easier permissions management.
 * In this way, groups essentially serve the same purpose that
 * they do in Unix or Windows.<p>
 * <p/>
 * For example, CREATE_THREAD permissions can be set per forum. A
 * forum administrator may wish to create a "Thread Posters" group
 * that has CREATE_THREAD permissions in the forum. Then, users can
 * be added to that group and will automatically receive CREATE_THREAD
 * permissions in that forum.<p>
 * <p/>
 * Security for Group objects is provide by GroupProxy protection proxy objects.
 *
 * @author Iain Shigeoka
 *
 * @see Entity
 */
public interface Group {

    /**
     * Returns the id of the group.
     *
     * @return the id of the group.
     */
    public long getID();

    /**
     * Returns the name of the group. For example, 'XYZ Admins'.
     *
     * @return the name of the group.
     */
    public String getName();

    /**
     * Sets the name of the group. For example, 'XYZ Admins'. This
     * method is restricted to those with group administration permission.
     *
     * @param name the name for the group.
     * @throws UnauthorizedException if does not have group administrator permissions.
     */
    public void setName(String name) throws UnauthorizedException;

    /**
     * Returns the description of the group. The description often
     * summarizes a group's function, such as 'Administrators of the XYZ forum'.
     *
     * @return the description of the group.
     */
    public String getDescription();

    /**
     * Sets the description of the group. The description often
     * summarizes a group's function, such as 'Administrators of
     * the XYZ forum'. This method is restricted to those with group
     * administration permission.
     *
     * @param description the description of the group.
     * @throws UnauthorizedException if does not have group administrator permissions.
     */
    public void setDescription(String description) throws UnauthorizedException;

    /**
     * Returns the date that the group was created.
     *
     * @return the date the group was created.
     */
    public Date getCreationDate();

    /**
     * Sets the creation date of the group. In most cases, the
     * creation date will default to when the group was entered
     * into the system. However, the date needs to be set manually when
     * importing data. In other words, skin authors should ignore
     * this method since it only intended for system maintenance.
     *
     * @param creationDate the date the group was created.
     * @throws UnauthorizedException if does not have administrator permissions.
     */
    public void setCreationDate(Date creationDate) throws UnauthorizedException;

    /**
     * Returns the date that the group was last modified.
     *
     * @return the date the group record was last modified.
     */
    public Date getModificationDate();

    /**
     * Sets the date the group was last modified. Skin authors
     * should ignore this method since it only intended for
     * system maintenance.
     *
     * @param modificationDate the date the group was modified.
     * @throws UnauthorizedException if does not have administrator permissions.
     */
    public void setModificationDate(Date modificationDate) throws UnauthorizedException;

    /**
     * Returns an extended property of the group. Each group can
     * have an arbitrary number of extended properties. This
     * lets particular skins or filters provide enhanced functionality
     * that is not part of the base interface.
     *
     * @param name the name of the property to get.
     * @return the value of the property
     */
    public String getProperty(String name);

    /**
     * Sets an extended property of the group. Each group can have
     * an arbitrary number of extended properties. This lets
     * particular skins or filters provide enhanced functionality that is not
     * part of the base interface.
     *
     * @param name  the name of the property to set.
     * @param value the new value for the property.
     * @throws UnauthorizedException if not allowed to change the group.
     */
    public void setProperty(String name, String value) throws UnauthorizedException;

    /**
     * Deletes an extended property. If the property specified by
     * <code>name</code> does not exist, this method will do nothing.
     *
     * @param name the name of the property to delete.
     * @throws UnauthorizedException if not allowed to edit messages.
     */
    public void deleteProperty(String name) throws UnauthorizedException;

    /**
     * Returns an Iterator for all the names of the extended group properties.
     *
     * @return an Iterator for the property names.
     */
    public Iterator getPropertyNames();

    /**
     * Grants administrator privileges of the group to a user. This
     * method is restricted to those with group administration permission.
     *
     * @param entity the User to grant adminstrative privileges to.
     * @throws UnauthorizedException if does not have group administrator permissions.
     */
    public void addAdministrator(Entity entity) throws UnauthorizedException, UserAlreadyExistsException;

    /**
     * Revokes administrator privileges of the group to a user.
     * This method is restricted to those with group administration permission.
     *
     * @param entity the User to grant adminstrative privileges to.
     * @throws UnauthorizedException if does not have group administrator permissions.
     */
    public void removeAdministrator(Entity entity) throws UnauthorizedException;

    /**
     * Adds a member to the group. This method is restricted to
     * those with group administration permission.
     *
     * @param entity the User to add to the group.
     * @throws UnauthorizedException if does not have group administrator permissions.
     */
    public void addMember(Entity entity) throws UnauthorizedException, UserAlreadyExistsException;

    /**
     * Removes a member from the group. If the User is not
     * in the group, this method does nothing. This method
     * is restricted to those with group administration permission.
     *
     * @param entity the User to remove from the group.
     * @throws UnauthorizedException if does not have group administrator permissions.
     */
    public void removeMember(Entity entity) throws UnauthorizedException;

    /**
     * Returns true if the User has group administrator permissions.
     *
     * @return true if the User is an administrator of the group.
     */
    public boolean isAdministrator(Entity entity);

    /**
     * Returns true if if the User is a member of the group.
     *
     * @return true if the User is a member of the group.
     */
    public boolean isMember(Entity entity);

    /**
     * Returns the number of group administrators.
     *
     * @return the number of group administrators.
     */
    public int getAdministratorCount();

    /**
     * Returns the number of group members.
     *
     * @return the number of group members.
     */
    public int getMemberCount();

    /**
     * An iterator for all the Entities that are members of the group.
     *
     * @return an Iterator for all members of the group.
     */
    public Iterator members();

    /**
     * An iterator for all the Entities that are administrators of the group.
     *
     * @return an Iterator for all administrators of the group.
     */
    public Iterator administrators();

    /**
     * Returns the permissions for the group that correspond to the passed-in AuthToken.
     *
     * @param authToken the auth token to lookup permissions for.
     * @return the permissions for the group that correspond to the passed-in AuthToken.
     * @see Permissions
     */
    public Permissions getPermissions(AuthToken authToken);

    /**
     * Returns true if the handle on the object has the
     * permission specified. A list of possible
     * permissions can be found in the Permissions class.
     * Certain methods of this class are restricted to
     * certain permissions as specified in the method comments.
     *
     * @param permissionType a permission type.
     * @return true if the specified permission is valid.
     * @see Permissions
     */
    public boolean isAuthorized(long permissionType);
}