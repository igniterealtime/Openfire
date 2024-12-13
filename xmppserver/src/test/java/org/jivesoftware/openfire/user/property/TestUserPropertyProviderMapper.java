/*
 * Copyright (C) 2024 Ignite Realtime Foundation. All rights reserved.
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A mapper that can be used with the TestUserPropertyProvider implementations.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class TestUserPropertyProviderMapper implements UserPropertyProviderMapper
{
    private final Map<String, UserPropertyProvider> providers = new HashMap<>();

    public TestUserPropertyProviderMapper() {
        providers.put(null, new TestUserPropertyProvider.NoUserProvider());
        providers.put("jane", new TestUserPropertyProvider.JaneUserProvider());
        providers.put("john", new TestUserPropertyProvider.JohnUserProvider());
    }

    @Override
    public UserPropertyProvider getUserPropertyProvider(String username)
    {
        if (providers.containsKey(username)) {
            return providers.get(username);
        }
        return providers.get(null);
    }

    @Override
    public Set<UserPropertyProvider> getUserPropertyProviders()
    {
        return new HashSet<>(providers.values());
    }
}
