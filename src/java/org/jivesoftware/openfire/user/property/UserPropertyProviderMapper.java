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

import java.util.Set;

/**
 * Implementations are used to determine what UserPropertyProvider is to be used for a particular username.
 *
 * Note that the provided username need not reflect a pre-existing user (the instance might be used to determine in
 * which provider a new user is to be created).
 *
 * Implementation must have a no-argument constructor.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @see MappedUserPropertyProvider
 */
public interface UserPropertyProviderMapper
{
    /**
     * Finds a suitable UserPropertyProvider for the user.
     *
     * Note that the provided username need not reflect a pre-existing user (the instance might be used to determine in
     * which provider a new user is to be created).
     *
     * Implementations are expected to be able to find a UserPropertyProvider for any username. If an implementation
     * fails to do so, such a failure is assumed to be the result of a problem in implementation or configuration.
     *
     * @param username A user identifier (cannot be null or empty).
     * @return A UserPropertyProvider for the user (never null).
     */
    UserPropertyProvider getUserPropertyProvider( String username );

    /**
     * Returns all providers that are used by this instance.
     *
     * The returned collection should have a consistent, predictable iteration order.
     *
     * @return all providers (never null).
     */
    Set<UserPropertyProvider> getUserPropertyProviders();
}
