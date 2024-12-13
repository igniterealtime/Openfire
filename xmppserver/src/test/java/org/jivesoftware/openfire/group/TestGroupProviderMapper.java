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
package org.jivesoftware.openfire.group;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A mapper that can be used with the TestUserProvider implementations.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class TestGroupProviderMapper implements GroupProviderMapper
{
    private final Map<String, GroupProvider> providers = new HashMap<>();

    public TestGroupProviderMapper() {
        providers.put(null, new TestGroupProvider.NoGroupProvider());
        providers.put("little-endians", new TestGroupProvider.LittleEndiansGroupProvider());
        providers.put("big-endians", new TestGroupProvider.BigEndiansGroupProvider());
    }

    @Override
    public GroupProvider getGroupProvider(String groupname)
    {
        if (providers.containsKey(groupname)) {
            return providers.get(groupname);
        }
        return providers.get(null);
    }

    @Override
    public Set<GroupProvider> getGroupProviders()
    {
        return new HashSet<>(providers.values());
    }
}
