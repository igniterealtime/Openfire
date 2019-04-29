/*
 * Copyright 2017 IgniteRealtime.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package org.jivesoftware.openfire.user.property;

import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserNotFoundException;

import java.util.Map;

/**
 * A provider for user properties. User properties are defined Map of String key and values that does not support null.
 * value.
 *
 * Some, but not all, implementations are expected to store user properties in a relation to an existing user object.
 * This interface definition does not require implementations to verify that a user object indeed exists, when
 * processing data. As a result, methods defined here may, but are not required to throw {@link UserNotFoundException}
 * when processing property data for non-existing users. Implementations should clearly document their behavior in
 * this respect.
 *
 * <b>Warning:</b> in virtually all cases a user property provider should not be used directly. Instead, use the
 * Map returned by {@link User#getProperties()} to create, read, update or delete user properties. Failure to do so
 * is likely to result in inconsistent data behavior and race conditions. Direct access to the user property
 * provider is only provided for special-case logic.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @see User#getProperties
 */
public interface UserPropertyProvider
{
    /**
     * Returns true if this UserPropertyProvider is read-only. When read-only, properties can not be created, deleted or
     * modified. Invocation of the corresponding methods should result in an {@link UnsupportedOperationException}.
     *
     * @return true if the user provider is read-only.
     */
    boolean isReadOnly();

    /**
     * Retrieves all properties for a particular user.
     *
     * @param username The identifier of the user (cannot be null or empty).
     * @return A collection, possibly empty, but never null.
     * @throws UserNotFoundException if the user cannot be found
     */
    Map<String, String> loadProperties( String username ) throws UserNotFoundException;

    /**
     * Retrieves a property value for a user.
     *
     * This method will return null when the desired property was not defined for the user (null values are not
     * supported).
     *
     * @param username The identifier of the user (cannot be null or empty).
     * @param propName The property name (cannot be null or empty).
     * @return The property value (possibly null).
     * @throws UserNotFoundException if the user cannot be found
     */
    String loadProperty( String username, String propName ) throws UserNotFoundException;

    /**
     * Adds a property for an user.
     *
     * The behavior of inserting a duplicate property name is not defined by this interface.
     *
     * @param username  The identifier of the user (cannot be null or empty).
     * @param propName  The property name (cannot be null or empty).
     * @param propValue The property value (cannot be null).
     * @throws UserNotFoundException if the user cannot be found
     * @throws UnsupportedOperationException if the property cannot be added
     */
    void insertProperty( String username, String propName, String propValue ) throws UserNotFoundException, UnsupportedOperationException;

    /**
     * Changes a property value for an user.
     *
     * The behavior of updating a non-existing property is not defined by this interface.
     *
     * @param username  The identifier of the user (cannot be null or empty).
     * @param propName  The property name (cannot be null or empty).
     * @param propValue The property value (cannot be null).
     * @throws UserNotFoundException if the user cannot be found
     * @throws UnsupportedOperationException if the property cannot be updated
     */
    void updateProperty( String username, String propName, String propValue ) throws UserNotFoundException, UnsupportedOperationException;

    /**
     * Removes one particular property for a particular user.
     *
     * The behavior of deleting a non-existing property is not defined by this interface.
     *
     * @param username The identifier of the user (cannot be null or empty).
     * @param propName The property name (cannot be null or empty).
     * @throws UserNotFoundException if the user cannot be found
     * @throws UnsupportedOperationException if the property cannot be deleted
     */
    void deleteProperty( String username, String propName ) throws UserNotFoundException, UnsupportedOperationException;
}
