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
package org.jivesoftware.openfire.user;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A mapper that can be used with the TestUserProvider implementations.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class TestUserProviderMapper implements UserProviderMapper
{
    private final Map<String, UserProvider> providers = new HashMap<>();

    public TestUserProviderMapper() {
        providers.put(null, new TestUserProvider.NoUserProvider());
        providers.put("jane", new TestUserProvider.JaneUserProvider());
        providers.put("john", new TestUserProvider.JohnUserProvider());
    }

    @Override
    public UserProvider getUserProvider(String username)
    {
        if (providers.containsKey(username)) {
            return providers.get(username);
        }
        return providers.get(null);
    }

    @Override
    public Set<UserProvider> getUserProviders()
    {
        return new HashSet<>(providers.values());
    }
}
