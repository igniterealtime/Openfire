/*
 * Copyright (C) 2024 Ignite Realtime Foundation. All rights reserved
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

package org.jivesoftware.openfire.group;

import java.util.Set;

/**
 * Implementations are used to determine what GroupProvider is to be used for a particular group name.
 *
 * Note that the provided group name need not reflect a pre-existing group (the instance might be used to determine in
 * which provider a new group is to be created).
 *
 * Implementation must have a no-argument constructor.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @see MappedGroupProvider
 */
public interface GroupProviderMapper
{
    /**
     * Finds a suitable GroupProvider for the group.
     *
     * Note that the provided group name need not reflect a pre-existing group (the instance might be used to determine in
     * which provider a new group is to be created).
     *
     * Implementations are expected to be able to find a GroupProvider for any group name. If an implementation fails to do
     * so, such a failure is assumed to be the result of a problem in implementation or configuration.
     *
     * @param groupname A group identifier (cannot be null or empty).
     * @return A GroupProvider for the group (never null).
     */
    GroupProvider getGroupProvider(String groupname);

    /**
     * Returns all providers that are used by this instance.
     *
     * The returned collection should have a consistent, predictable iteration order.
     *
     * @return all providers (never null).
     */
    Set<GroupProvider> getGroupProviders();
}
