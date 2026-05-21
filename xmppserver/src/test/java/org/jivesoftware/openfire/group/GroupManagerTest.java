/*
 * Copyright (C) 2019-2026 Ignite Realtime Foundation. All rights reserved.
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
import org.jivesoftware.openfire.event.GroupEventDispatcher;
import org.jivesoftware.util.CacheableOptional;
import org.jivesoftware.util.PersistableMap;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xmpp.packet.JID;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GroupManagerTest
{
    private static final String GROUP_NAME = "test-group-name";
    private static Cache<String, CacheableOptional<Group>> groupCache;
    private static Cache<String, Serializable> groupMetaCache;

    @Mock
    GroupProvider groupProvider;

    private GroupManager groupManager;

    @Mock
    private Group cachedGroup;

    @Mock
    private Group unCachedGroup;

    @BeforeAll
    public static void beforeClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
        groupCache = CacheFactory.createCache("Group");
        groupMetaCache = CacheFactory.createCache("Group Metadata Cache");
        groupCache.clear();
        groupMetaCache.clear();
    }

    @BeforeEach
    public void setUp()
    {
        // Reset shared caches before each test, then build GroupManager directly with test dependencies.
        Arrays.stream(CacheFactory.getAllCaches()).forEach(Map::clear);
        groupManager = new GroupManager(groupProvider, groupCache, groupMetaCache);
    }

    @AfterAll
    public static void afterClass()
    {
        groupCache.clear();
        groupMetaCache.clear();
    }

    /**
     * Unit-level test (not behavioral): Verifies internal caching behavior of GroupManager when a group is found
     * in the cache. This test is implementation-focused and validates that the provider is not called when a cache hit occurs.
     */
    @Test
    public void willUseACacheHit() throws Exception
    {
        // Setup test fixture.
        groupCache.put(GROUP_NAME, CacheableOptional.of(cachedGroup));

        // Execute system under test.
        final Group returnedGroup = groupManager.getGroup(GROUP_NAME, false);

        // Verify result.
        assertThat("Cache hit should return the cached group without consulting the provider.", returnedGroup, is(cachedGroup));
        verifyNoMoreInteractions(groupProvider);
    }

    /**
     * Unit-level test (not behavioral): Verifies internal caching behavior of GroupManager when a negative cache entry
     * (group-not-found) is found in the cache. This test is implementation-focused and validates that the provider is not called.
     */
    @Test
    public void willUseACacheMiss()
    {
        // Setup test fixture.
        groupCache.put(GROUP_NAME, CacheableOptional.of(null));

        // Execute system under test and verify result.
        assertThrows(GroupNotFoundException.class, () -> groupManager.getGroup(GROUP_NAME, false), "Cache miss should throw GroupNotFoundException without consulting the provider.");
        verifyNoMoreInteractions(groupProvider);
    }

    /**
     * Unit-level test (not behavioral): Verifies internal caching behavior of GroupManager when a group is looked up
     * but not yet cached. This test is implementation-focused and validates that the result from the provider is cached.
     */
    @Test
    public void willCacheAHitIfNotAlreadyCached() throws Exception
    {
        // Setup test fixture.
        doReturn(unCachedGroup).when(groupProvider).getGroup(GROUP_NAME);

        // Execute system under test.
        final Group returnedGroup = groupManager.getGroup(GROUP_NAME, false);

        // Verify result.
        assertThat("Uncached group should be returned from the provider.", returnedGroup, is(unCachedGroup));
        verify(groupProvider).getGroup(GROUP_NAME);
        assertThat("Provider result should be cached for future lookups.", groupCache.get(GROUP_NAME), is(CacheableOptional.of(unCachedGroup)));
    }

    /**
     * Unit-level test (not behavioral): Verifies internal caching behavior of GroupManager when a group is not found
     * by the provider. This test is implementation-focused and validates that negative results (group-not-found) are
     * cached to avoid repeated failed lookups.
     */
    @Test
    public void willCacheAMissIfNotAlreadyCached() throws Exception
    {
        // Setup test fixture.
        doThrow(new GroupNotFoundException()).when(groupProvider).getGroup(GROUP_NAME);

        // Execute system under test and verify result.
        assertThrows(GroupNotFoundException.class, () -> groupManager.getGroup(GROUP_NAME, false), "Uncached miss should throw GroupNotFoundException from the provider.");
        verify(groupProvider).getGroup(GROUP_NAME);
        assertThat("Cache miss should be cached to avoid repeated provider lookups.", groupCache.get(GROUP_NAME), is(CacheableOptional.of(null)));
    }

    /**
     * Unit-level test (not behavioral): Verifies internal caching behavior of GroupManager when force-refresh is
     * requested. This test is implementation-focused and validates that the cache is bypassed and the provider is
     * consulted even if an entry exists in the cache.
     */
    @Test
    public void aForceLookupHitWillIgnoreTheExistingCache() throws Exception
    {
        // Setup test fixture.
        groupCache.put(GROUP_NAME, CacheableOptional.of(cachedGroup));
        doReturn(unCachedGroup).when(groupProvider).getGroup(GROUP_NAME);

        // Execute system under test.
        final Group returnedGroup = groupManager.getGroup(GROUP_NAME, true);

        // Verify result.
        assertThat("Force-refresh should bypass cache and return fresh result from provider.", returnedGroup, is(unCachedGroup));
        verify(groupProvider).getGroup(GROUP_NAME);
        assertThat("Cache should be updated with the fresh lookup result.", groupCache.get(GROUP_NAME), is(CacheableOptional.of(unCachedGroup)));
    }

    /**
     * Unit-level test (not behavioral): Verifies internal caching behavior of GroupManager when force-refresh is
     * requested and the group is not found. This test is implementation-focused and validates that the cache bypass
     * applies even when the provider fails to find the group.
     */
    @Test
    public void aForceLookupMissWillIgnoreTheExistingCache() throws Exception
    {
        // Setup test fixture.
        groupCache.put(GROUP_NAME, CacheableOptional.of(cachedGroup));
        doThrow(new GroupNotFoundException()).when(groupProvider).getGroup(GROUP_NAME);

        // Execute system under test and verify result.
        assertThrows(GroupNotFoundException.class, () -> groupManager.getGroup(GROUP_NAME, true), "Force-refresh should bypass cache and consult provider even if cached entry exists.");
        verify(groupProvider).getGroup(GROUP_NAME);
        assertThat("Cache should be updated with the fresh lookup failure.", groupCache.get(GROUP_NAME), is(CacheableOptional.of(null)));
    }

    /**
     * Unit-level test (not behavioral): Verifies internal caching behavior after group creation.
     * This test validates that createGroupPostProcess places newly created groups into the cache.
     */
    @Test
    public void createGroupPostProcessCachesGroup()
    {
        // Setup test fixture.
        final Group groupToCache = mock(Group.class);
        when(groupToCache.getName()).thenReturn("new-group");
        when(groupToCache.getSharedWith()).thenReturn(SharedGroupVisibility.nobody);
        when(groupToCache.getAdmins()).thenReturn(new HashSet<>());
        when(groupToCache.getMembers()).thenReturn(new HashSet<>());
        final GroupManager manager = new GroupManager(groupProvider, groupCache, groupMetaCache);

        // Execute system under test.
        manager.createGroupPostProcess(groupToCache);

        // Verify result.
        assertNotNull(groupCache.get("new-group"), "Group should be cached in group cache after creation.");
    }

    /**
     * Verifies that createGroupPostProcess dispatches a group_created event.
     */
    @Test
    public void createGroupPostProcessDispatchesCreatedEvent()
    {
        // Setup test fixture.
        final Group groupToCreate = mock(Group.class);
        when(groupToCreate.getName()).thenReturn("new-group");
        when(groupToCreate.getSharedWith()).thenReturn(SharedGroupVisibility.nobody);
        when(groupToCreate.getAdmins()).thenReturn(new HashSet<>());
        when(groupToCreate.getMembers()).thenReturn(new HashSet<>());
        final GroupEventDispatcher.EventType[] capturedEventType = new GroupEventDispatcher.EventType[1];
        final GroupManager manager = new GroupManager(groupProvider, groupCache, groupMetaCache) {
            @Override
            protected void dispatchGroupEvent(final Group group, final GroupEventDispatcher.EventType type, final Map<String, ?> params) {
                capturedEventType[0] = type;
            }
        };

        // Execute system under test.
        manager.createGroupPostProcess(groupToCreate);

        // Verify result.
        assertEquals(GroupEventDispatcher.EventType.group_created, capturedEventType[0], "createGroupPostProcess should dispatch group_created event.");
    }

    /**
     * Verifies that deleteGroupPreProcess dispatches a group_deleting event.
     */
    @Test
    public void deleteGroupPreProcessDispatchesDeletingEvent()
    {
        // Setup test fixture.
        final GroupEventDispatcher.EventType[] capturedEventType = new GroupEventDispatcher.EventType[1];
        final GroupManager manager = new GroupManager(groupProvider, groupCache, groupMetaCache) {
            @Override
            protected void dispatchGroupEvent(final Group group, final GroupEventDispatcher.EventType type, final Map<String, ?> params) {
                capturedEventType[0] = type;
            }
        };

        // Execute system under test.
        manager.deleteGroupPreProcess(cachedGroup);

        // Verify result.
        assertEquals(GroupEventDispatcher.EventType.group_deleting, capturedEventType[0], "deleteGroupPreProcess should dispatch group_deleting event.");
    }

    /**
     * Verifies that adminAddedPostProcess dispatches an admin_added event.
     */
    @Test
    public void adminAddedPostProcessDispatchesAdminAddedEvent() throws GroupNotFoundException
    {
        // Setup test fixture.
        final JID adminJid = new JID("admin@example.org");
        when(cachedGroup.getName()).thenReturn("test-group");
        doReturn(cachedGroup).when(groupProvider).getGroup("test-group");
        groupCache.put("test-group", CacheableOptional.of(cachedGroup));
        final GroupEventDispatcher.EventType[] capturedEventType = new GroupEventDispatcher.EventType[1];
        final GroupManager manager = new GroupManager(groupProvider, groupCache, groupMetaCache) {
            @Override
            protected void dispatchGroupEvent(final Group group, final GroupEventDispatcher.EventType type, final Map<String, ?> params) {
                capturedEventType[0] = type;
            }
        };

        // Execute system under test.
        manager.adminAddedPostProcess(cachedGroup, adminJid, false);

        // Verify result.
        assertEquals(GroupEventDispatcher.EventType.admin_added, capturedEventType[0], "adminAddedPostProcess should dispatch admin_added event.");
    }

    /**
     * Verifies that memberAddedPostProcess dispatches a member_added event.
     */
    @Test
    public void memberAddedPostProcessDispatchesMemberAddedEvent() throws GroupNotFoundException
    {
        // Setup test fixture.
        final JID memberJid = new JID("member@example.org");
        when(cachedGroup.getName()).thenReturn("test-group");
        doReturn(cachedGroup).when(groupProvider).getGroup("test-group");
        groupCache.put("test-group", CacheableOptional.of(cachedGroup));
        final GroupEventDispatcher.EventType[] capturedEventType = new GroupEventDispatcher.EventType[1];
        final GroupManager manager = new GroupManager(groupProvider, groupCache, groupMetaCache) {
            @Override
            protected void dispatchGroupEvent(final Group group, final GroupEventDispatcher.EventType type, final Map<String, ?> params) {
                capturedEventType[0] = type;
            }
        };

        // Execute system under test.
        manager.memberAddedPostProcess(cachedGroup, memberJid, false);

        // Verify result.
        assertEquals(GroupEventDispatcher.EventType.member_added, capturedEventType[0], "memberAddedPostProcess should dispatch member_added event.");
    }

    /**
     * Verifies that propertyAddedPostProcess dispatches a group_modified event.
     */
    @Test
    public void propertyAddedPostProcessDispatchesModifiedEvent() throws GroupNotFoundException
    {
        // Setup test fixture.
        when(cachedGroup.getName()).thenReturn("test-group");
        doReturn(cachedGroup).when(groupProvider).getGroup("test-group");
        groupCache.put("test-group", CacheableOptional.of(cachedGroup));
        final GroupEventDispatcher.EventType[] capturedEventType = new GroupEventDispatcher.EventType[1];
        final GroupManager manager = new GroupManager(groupProvider, groupCache, groupMetaCache) {
            @Override
            protected void dispatchGroupEvent(final Group group, final GroupEventDispatcher.EventType type, final Map<String, ?> params) {
                capturedEventType[0] = type;
            }
        };

        // Execute system under test.
        manager.propertyAddedPostProcess(cachedGroup, "prop-key");

        // Verify result.
        assertEquals(GroupEventDispatcher.EventType.group_modified, capturedEventType[0], "propertyAddedPostProcess should dispatch group_modified event.");
    }

    /**
     * Unit-level test (not behavioral): Verifies internal cache-eviction behavior when a group is created. This test
     * validates that createGroupPostProcess evicts all paginated group-name cache entries (keys prefixed with
     * GROUP_NAMES_KEY) from groupMetaCache, while leaving unrelated entries (such as per-user group-membership entries)
     * untouched. This test is implementation-focused and necessary to ensure cache correctness but does not represent
     * user-visible behavior.
     */
    @Test
    public void createGroupPostProcessEvictsPaginatedGroupNamesCacheEntries()
    {
        // Setup test fixture: a group that is not shared with anybody.
        final String groupName = "new-group";
        when(cachedGroup.getName()).thenReturn(groupName);
        when(cachedGroup.getSharedWith()).thenReturn(SharedGroupVisibility.nobody);
        when(cachedGroup.getAdmins()).thenReturn(new HashSet<>());
        when(cachedGroup.getMembers()).thenReturn(new HashSet<>());

        // Pre-populate groupMetaCache with:
        //   - a paginated GROUP_NAMES entry  → must be evicted by evictCachedPaginatedGroupNames()
        //   - a USER_GROUPS entry            → must NOT be evicted (different responsibility)
        final String paginatedKey = "GROUP_NAMES0,10";
        final String userGroupsKey = "USER_GROUPSjane@example.org";
        groupMetaCache.put(paginatedKey,  new HashSet<String>());
        groupMetaCache.put(userGroupsKey, new HashSet<String>());

        final GroupManager manager = new GroupManager(groupProvider, groupCache, groupMetaCache) {
            @Override
            protected void dispatchGroupEvent(final Group group, final GroupEventDispatcher.EventType type, final Map<String, ?> params) {
                // Suppress event dispatch; not the focus of this test.
            }
        };

        // Execute system under test: createGroupPostProcess calls evictCachedPaginatedGroupNames().
        manager.createGroupPostProcess(cachedGroup);

        // Verify result.
        assertNull(groupMetaCache.get(paginatedKey), "Paginated GROUP_NAMES cache entry should be evicted after group creation.");
        assertNotNull(groupMetaCache.get(userGroupsKey), "USER_GROUPS cache entry should NOT be evicted by evictCachedPaginatedGroupNames().");
    }
}
