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

import org.jivesoftware.messenger.auth.UnauthorizedException;
import java.util.Map;

/**
 * <p>Implement this provider to store user and vcard properties somewhere
 * other than the Jive tables, or to capture jive property events.</p>
 * <p/>
 * <p>Implementors: Most integrators will not need to create their own
 * user property providers. In almost all cases, it's best to let Messenger
 * store these values in the Jive tables.</p>
 *
 * @author Iain Shigeoka
 */
public interface UserPropertiesProvider {

    /**
     * <p>Delete a user's vcard property (optional operation).</p>
     *
     * @param username the username of the user
     * @param name The name of the property to delete
     * @throws UnauthorizedException         If the caller does not have permission to carry out the operation
     * @throws UnsupportedOperationException If the provider does not support the operation (this is an optional operation)
     */
    public void deleteVcardProperty(String username, String name) throws UnauthorizedException, UnsupportedOperationException;

    /**
     * <p>Delete a user's user property (optional operation).</p>
     *
     * @param username the username of the user
     * @param name The name of the property to delete
     * @throws UnauthorizedException         If the caller does not have permission to carry out the operation
     * @throws UnsupportedOperationException If the provider does not support the operation (this is an optional operation)
     */
    public void deleteUserProperty(String username, String name) throws UnauthorizedException, UnsupportedOperationException;

    /**
     * <p>Insert a new vcard property (optional operation).</p>
     *
     * @param username the username of the user
     * @param name  The name of the property
     * @param value The value of the property
     * @throws UnauthorizedException         If the caller does not have permission to carry out the operation
     * @throws UnsupportedOperationException If the provider does not support the operation (this is an optional operation)
     */
    public void insertVcardProperty(String username, String name, String value) throws UnauthorizedException, UnsupportedOperationException;

    /**
     * <p>Insert a new user property (optional operation).</p>
     *
     * @param username the username of the user
     * @param name  The name of the property
     * @param value The value of the property
     * @throws UnauthorizedException         If the caller does not have permission to carry out the operation
     * @throws UnsupportedOperationException If the provider does not support the operation (this is an optional operation)
     */
    public void insertUserProperty(String username, String name, String value) throws UnauthorizedException, UnsupportedOperationException;

    /**
     * <p>Update a vcard property (optional operation).</p>
     *
     * @param username the username of the user.
     * @param name  The name of the property.
     * @param value The value of the property.
     * @throws UnauthorizedException  if the caller does not have permission to carry out the operation
     * @throws UnsupportedOperationException if the provider does not support the operation (this is an optional operation).
     */
    public void updateVcardProperty(String username, String name, String value)
            throws UnauthorizedException, UnsupportedOperationException;

    /**
     * <p>Update an Existing user property (optional operation).</p>
     *
     * @param username the username of the user
     * @param name  The name of the property
     * @param value The value of the property
     * @throws UnauthorizedException         If the caller does not have permission to carry out the operation
     * @throws UnsupportedOperationException If the provider does not support the operation (this is an optional operation)
     */
    public void updateUserProperty(String username, String name, String value) throws UnauthorizedException, UnsupportedOperationException;

    /**
     * <p>Obtain a map containing all vcard properties for a user.</p>
     * <p/>
     * <p>If the provider doesn't support vcard properties, return an empty map.</p>
     *
     * @param username the username of the user to retrieve the vcard properties
     * @return A map of property name-value pairs
     */
    public Map getVcardProperties(String username);

    /**
     * <p>Obtain a map containing all user properties for a user.</p>
     * <p/>
     * <p>If the provider doesn't support user properties, return an empty map.</p>
     *
     * @param username the username of the user to retrieve the user properties
     * @return A map of property name-value pairs
     */
    public Map getUserProperties(String username);
}
