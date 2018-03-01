/*
 * Copyright 2016 IgniteRealtime.org
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

package org.jivesoftware.openfire.auth;

import java.util.Set;

/**
 * Implementations are used to determine what AuthProvider is to be used for a particular username.
 *
 * Implementation must have a no-argument constructor.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @see MappedAuthProvider
 */
public interface AuthProviderMapper
{
    /**
     * Finds a suitable AuthProvider for the user. Returns null when no AuthProvider can be found for the particular
     * user.
     *
     * @param username A user identifier (cannot be null or empty).
     * @return An AuthProvider for the user (possibly null).
     */
    AuthProvider getAuthProvider( String username );

    /**
     * Returns all providers that are used by this instance.
     *
     * The returned collection should have a consistent, predictable iteration order.
     *
     * @return all providers (never null).
     */
    Set<AuthProvider> getAuthProviders();
}
