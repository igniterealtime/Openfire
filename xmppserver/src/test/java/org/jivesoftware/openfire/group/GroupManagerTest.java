/*
 * Copyright (C) 2019-2023 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.Fixtures;
import org.jivesoftware.util.CacheableOptional;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PersistableMap;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xmpp.packet.JID;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GroupManagerTest {

    private static final String GROUP_NAME = "test-group-name";
    private static Cache<String, CacheableOptional<Group>> groupCache;
    @Mock
    GroupProvider groupProvider;
    private GroupManager groupManager;
    @Mock
    private Group cachedGroup;
    @Mock
    private Group unCachedGroup;

    @BeforeAll
    public static void beforeClass() throws Exception {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
        groupCache = CacheFactory.createCache("Group");
        groupCache.clear();
        JiveGlobals.setProperty("provider.group.className", TestGroupProvider.class.getName());
    }

    @BeforeEach
    public void setUp() {
        // Ensure that Openfire caches are reset before each test to avoid tests to affect each-other.
        Arrays.stream(CacheFactory.getAllCaches()).forEach(Map::clear);

        TestGroupProvider.mockGroupProvider = groupProvider;
        groupManager = GroupManager.getInstance();
    }

    @AfterAll
    public static void afterClass() throws Exception {

        // Reset static fields after use (to not confuse other test classes).
        for (String fieldName : Arrays.asList("INSTANCE", "provider")) {
            Field field = GroupManager.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(null, null);
            field.setAccessible(false);
        }

        groupCache.clear();
    }

    @Test
    public void willUseACacheHit() throws Exception {

        groupCache.put(GROUP_NAME, CacheableOptional.of(cachedGroup));

        final Group returnedGroup = groupManager.getGroup(GROUP_NAME, false);

        assertThat(returnedGroup, is(cachedGroup));
        verifyNoMoreInteractions(groupProvider);
    }

    @Test
    public void willUseACacheMiss() {

        groupCache.put(GROUP_NAME, CacheableOptional.of(null));

        try {
            groupManager.getGroup(GROUP_NAME, false);
        } catch (final GroupNotFoundException ignored) {
            verifyNoMoreInteractions(groupProvider);
            return;
        }
        fail();
    }

    @Test
    public void willCacheAHitIfNotAlreadyCached() throws Exception {

        doReturn(unCachedGroup).when(groupProvider).getGroup(GROUP_NAME);

        final Group returnedGroup = groupManager.getGroup(GROUP_NAME, false);

        assertThat(returnedGroup, is(unCachedGroup));
        verify(groupProvider).getGroup(GROUP_NAME);
        assertThat(groupCache.get(GROUP_NAME), is(CacheableOptional.of(unCachedGroup)));
    }

    @Test
    public void willCacheAMissIfNotAlreadyCached() throws Exception {

        doThrow(new GroupNotFoundException()).when(groupProvider).getGroup(GROUP_NAME);

        try {
            groupManager.getGroup(GROUP_NAME, false);
        } catch (final GroupNotFoundException ignored) {
            verify(groupProvider).getGroup(GROUP_NAME);
            assertThat(groupCache.get(GROUP_NAME), is(CacheableOptional.of(null)));
            return;
        }
        fail();
    }

    @Test
    public void aForceLookupHitWillIgnoreTheExistingCache() throws Exception {
        groupCache.put(GROUP_NAME, CacheableOptional.of(cachedGroup));
        doReturn(unCachedGroup).when(groupProvider).getGroup(GROUP_NAME);

        final Group returnedGroup = groupManager.getGroup(GROUP_NAME, true);

        assertThat(returnedGroup, is(unCachedGroup));
        verify(groupProvider).getGroup(GROUP_NAME);
        assertThat(groupCache.get(GROUP_NAME), is(CacheableOptional.of(unCachedGroup)));
    }

    @Test
    public void aForceLookupMissWillIgnoreTheExistingCache() throws Exception {
        groupCache.put(GROUP_NAME, CacheableOptional.of(cachedGroup));
        doThrow(new GroupNotFoundException()).when(groupProvider).getGroup(GROUP_NAME);

        try {
            groupManager.getGroup(GROUP_NAME, true);
        } catch (final GroupNotFoundException ignored) {
            verify(groupProvider).getGroup(GROUP_NAME);
            assertThat(groupCache.get(GROUP_NAME), is(CacheableOptional.of(null)));
            return;
        }
        fail();
    }

    /**
     * As the GroupManager creates the instance of the GroupProvider, use this class to delegate calls
     * to the mock.
     */
    public static class TestGroupProvider implements GroupProvider {

        private static GroupProvider mockGroupProvider;

        @Override
        public Group createGroup(final String name) throws GroupAlreadyExistsException, GroupNameInvalidException {
            return mockGroupProvider.createGroup(name);
        }

        @Override
        public void deleteGroup(final String name) throws GroupNotFoundException {
            mockGroupProvider.deleteGroup(name);
        }

        @Override
        public Group getGroup(final String name) throws GroupNotFoundException {
            return mockGroupProvider.getGroup(name);
        }

        @Override
        public void setName(final String oldName, final String newName) throws GroupAlreadyExistsException, GroupNameInvalidException, GroupNotFoundException {
            mockGroupProvider.setName(oldName, newName);
        }

        @Override
        public void setDescription(final String name, final String description) throws GroupNotFoundException {
            mockGroupProvider.setDescription(name, description);
        }

        @Override
        public int getGroupCount() {
            return mockGroupProvider.getGroupCount();
        }

        @Override
        public Collection<String> getGroupNames() {
            return mockGroupProvider.getGroupNames();
        }

        @Override
        public boolean isSharingSupported() {
            return mockGroupProvider.isSharingSupported();
        }

        @Override
        public Collection<String> getSharedGroupNames() {
            return mockGroupProvider.getSharedGroupNames();
        }

        @Override
        public Collection<String> getSharedGroupNames(final JID user) {
            return mockGroupProvider.getSharedGroupNames(user);
        }

        @Override
        public Collection<String> getPublicSharedGroupNames() {
            return mockGroupProvider.getPublicSharedGroupNames();
        }

        @Override
        public Collection<String> getVisibleGroupNames(final String userGroup) {
            return mockGroupProvider.getVisibleGroupNames(userGroup);
        }

        @Override
        public Collection<String> getGroupNames(final int startIndex, final int numResults) {
            return mockGroupProvider.getGroupNames(startIndex, numResults);
        }

        @Override
        public Collection<String> getGroupNames(final JID user) {
            return mockGroupProvider.getGroupNames(user);
        }

        @Override
        public void addMember(final String groupName, final JID user, final boolean administrator) throws GroupNotFoundException {
            mockGroupProvider.addMember(groupName, user, administrator);
        }

        @Override
        public void updateMember(final String groupName, final JID user, final boolean administrator) throws GroupNotFoundException {
            mockGroupProvider.updateMember(groupName, user, administrator);
        }

        @Override
        public void deleteMember(final String groupName, final JID user) {
            mockGroupProvider.deleteMember(groupName, user);
        }

        @Override
        public boolean isReadOnly() {
            return mockGroupProvider.isReadOnly();
        }

        @Override
        public Collection<String> search(final String query) {
            return mockGroupProvider.search(query);
        }

        @Override
        public Collection<String> search(final String query, final int startIndex, final int numResults) {
            return mockGroupProvider.search(query, startIndex, numResults);
        }

        @Override
        public Collection<String> search(final String key, final String value) {
            return mockGroupProvider.search(key, value);
        }

        @Override
        public boolean isSearchSupported() {
            return mockGroupProvider.isSearchSupported();
        }

        @Override
        public PersistableMap<String, String> loadProperties(final Group group) {
            return mockGroupProvider.loadProperties(group);
        }
    }
}
