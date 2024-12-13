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
package org.jivesoftware.openfire.auth;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A mapper that can be used with the TestAuthProvider implementations.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class TestAuthProviderMapper implements AuthProviderMapper
{
    private final Map<String, AuthProvider> providers = new HashMap<>();

    public TestAuthProviderMapper() {
        providers.put(null, new TestAuthProvider.NoAuthProvider());
        providers.put("jane", new TestAuthProvider.JaneAuthProvider());
        providers.put("john", new TestAuthProvider.JohnAuthProvider());
    }

    @Override
    public AuthProvider getAuthProvider(String username)
    {
        if (providers.containsKey(username)) {
            return providers.get(username);
        }
        return providers.get(null);
    }

    @Override
    public Set<AuthProvider> getAuthProviders()
    {
        return new HashSet<>(providers.values());
    }
}
