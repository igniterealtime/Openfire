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

package org.jivesoftware.messenger.user;

import org.jivesoftware.messenger.auth.AuthToken;
import org.jivesoftware.messenger.auth.Group;
import org.jivesoftware.messenger.auth.Permissions;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import java.util.Iterator;

/**
 * <p>The User interface provides information about and services for users of the system. Users can be
 * identified by a unique id or username. Users can also be organized into groups for easier
 * management of permissions.</p>
 * <p/>
 * <p>Security for User objects is provide by UserProxy protection proxy objects.</p>
 *
 * @author Iain Shigeoka
 *
 * @see Group
 */
public interface User {

    /**
     * <p>Returns the entity's username.</p>
     * <p/>
     * <p>All usernames must be unique in the system.</p>
     *
     * @return the username of the entity.
     */
    String getUsername();

    /**
     * <p>Sets a new password for the user.</p>
     *
     * @param password The new password for the user
     * @throws UnauthorizedException If the provider does not allow password changes or the caller does not have permission
     */
    void setPassword(String password) throws UnauthorizedException;

    /**
     * <p>Returns meta information for this user such as full name, email address, etc.</p>
     *
     * @return Meta information about the user
     * @throws UserNotFoundException If no information is available for this user
     */
    UserInfo getInfo() throws UserNotFoundException;

    /**
     * <p>Saves the user info associated with this user to persistent storage.</p>
     *
     * @throws UnauthorizedException If the user info provider does not support the updating of user info
     */
    void saveInfo() throws UnauthorizedException;

    /**
     * Returns an extended property of the user. Each user can have an arbitrary number of extended
     * properties. This lets particular skins or filters provide enhanced functionality that is not
     * part of the base interface.
     *
     * @param name the name of the property to get.
     * @return the value of the property
     */
    String getProperty(String name);

    /**
     * Sets an extended property of the user. Each user can have an arbitrary number of extended
     * properties. This lets particular skins or filters provide enhanced functionality that is not
     * part of the base interface. Property names and values must be valid Strings. If <tt>null</tt>
     * or an empty length String is used, a NullPointerException will be thrown.
     *
     * @param name  the name of the property to set.
     * @param value the new value for the property.
     * @throws UnauthorizedException if not allowed to edit.
     */
    void setProperty(String name, String value) throws UnauthorizedException;

    /**
     * Deletes an extended property. If the property specified by <code>name</code> does not exist,
     * this method will do nothing.
     *
     * @param name the name of the property to delete.
     * @throws UnauthorizedException if not allowed to edit.
     */
    void deleteProperty(String name) throws UnauthorizedException;

    /**
     * Returns an Iterator for all the names of the extended user properties.
     *
     * @return an Iterator for the property names.
     */
    Iterator getPropertyNames();

    /**
     * Returns the user's roster. A roster is a list of users that the user wishes to know
     * if they are online. Rosters are similar to buddy groups in popular IM clients.
     *
     * @return the user's roster.
     * @throws UnauthorizedException if not the user or an administrator.
     */
    CachedRoster getRoster() throws UnauthorizedException;

    /**
     * Returns the permissions for the user that correspond to the passed-in AuthToken.
     *
     * @param authToken the auth token to look up permissions with.
     */
    Permissions getPermissions(AuthToken authToken);

    /**
     * Returns true if the handle on the object has the permission specified. A list of possible
     * permissions can be found in the Permissions class. Certain methods of this class are
     * restricted to certain permissions as specified in the method comments.
     *
     * @param permissionType the permission to check for.
     * @see Permissions
     */
    boolean isAuthorized(long permissionType);

    /**
     * Sets the user's vCard information. Advanced user systems can use vCard
     * information to link to user directory information or store other
     * relevant user information.
     *
     * @param name  The name of the vcard property
     * @param value The value of the vcard property
     * @throws UnauthorizedException If the caller doesn't have permission to change the user's vcard
     */
    void setVCardProperty(String name, String value) throws UnauthorizedException;

    /**
     * Obtains the user's vCard information for a given vcard property name.
     * Advanced user systems can use vCard
     * information to link to user directory information or store other
     * relevant user information.
     *
     * @param name The name of the vcard property to retrieve
     * @return The vCard value found
     */
    String getVCardProperty(String name);

    /**
     * Deletes a given vCard property from the user account.
     *
     * @param name The name of the vcard property to remove
     * @throws UnauthorizedException If not the user or administrator
     */
    void deleteVCardProperty(String name) throws UnauthorizedException;

    /**
     * Obtain an iterator for all vcard property names.
     *
     * @return the iterator over all vcard property names.
     */
    Iterator getVCardPropertyNames();
}