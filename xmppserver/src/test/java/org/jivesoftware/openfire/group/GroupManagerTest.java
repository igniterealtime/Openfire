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
import org.jivesoftware.openfire.XMPPServer;
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
        // Install a functional XMPPServer fixture used by post-process paths (for example, GroupJID construction).
        Fixtures.clearExistingProperties();
        XMPPServer.setInstance(Fixtures.mockXMPPServer());

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

        Fixtures.clearExistingProperties();
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

    /**
     * Unit-level test (not behavioral): Verifies internal cache-eviction behavior when a group's sharing mode changes
     * to "everybody". This test validates that propertyModifiedPostProcess evicts all per-user group-membership cache
     * entries (keys prefixed with USER_GROUPS_KEY) from groupMetaCache, while leaving unrelated entries (such as
     * paginated group-name entries) untouched. This test is implementation-focused and necessary to ensure cache
     * correctness but does not represent user-visible behavior.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3285">OF-3285: Changes to group sharing visibility are not immediately reflected in users' contact lists</a>
     */
    @Test
    public void sharingChangeToEverybodyEvictsUserGroupsCacheEntries() throws Exception
    {
        // Setup test fixture: a group whose sharing is about to change to "everybody".
        final String groupName = "test-shared-group";
        when(cachedGroup.getName()).thenReturn(groupName);

        // The group's properties must return "everybody" so that propertyChangePostProcess
        // triggers the eviction path.
        final PersistableMap<String, String> props = new PersistableMap<>() {
            @Override
            public String put(final String key, final String value, final boolean persist) {
                return super.put(key, value);
            }
        };
        props.put(Group.SHARED_ROSTER_SHOW_IN_ROSTER_PROPERTY_KEY, "everybody");
        when(cachedGroup.getProperties()).thenReturn(props);

        // The force-lookup inside propertyModifiedPostProcess must resolve the group.
        doReturn(cachedGroup).when(groupProvider).getGroup(groupName);

        // Pre-populate groupMetaCache with:
        //   - a USER_GROUPS entry  → must be evicted by evictCachedUserSharedGroups()
        //   - a paginated GROUP_NAMES entry → must NOT be evicted (different responsibility)
        final String userGroupsKey   = "USER_GROUPSjane@example.org";
        final String paginatedKey    = "GROUP_NAMES0,10";
        groupMetaCache.put(userGroupsKey, new HashSet<String>());
        groupMetaCache.put(paginatedKey,  new HashSet<String>());

        final GroupManager manager = new GroupManager(groupProvider, groupCache, groupMetaCache) {
            @Override
            protected void dispatchGroupEvent(final Group group, final GroupEventDispatcher.EventType type, final Map<String, ?> params) {
                // Suppress event dispatch; not the focus of this test.
            }
        };

        // Execute system under test: change sharing from "nobody" → "everybody".
        manager.propertyModifiedPostProcess(cachedGroup, Group.SHARED_ROSTER_SHOW_IN_ROSTER_PROPERTY_KEY, "nobody");

        // Verify result.
        assertNull(groupMetaCache.get(userGroupsKey), "USER_GROUPS cache entry should be evicted when group sharing changes to 'everybody'.");
        assertNull(groupMetaCache.get(paginatedKey), "Paginated GROUP_NAMES cache entry should be evicted by evictCachedUserSharedGroups().");
    }

    /**
     * Unit-level test (not behavioral): Verifies internal cache-eviction behavior when a group's sharing mode changes
     * FROM "everybody" to another mode. This test validates that propertyModifiedPostProcess evicts all per-user
     * group-membership cache entries (keys prefixed with USER_GROUPS_KEY) from groupMetaCache, while leaving unrelated
     * entries (such as paginated group-name entries) untouched. This test is implementation-focused and necessary to
     * ensure cache correctness but does not represent user-visible behavior. This test covers the reverse direction of
     * the sharing-visibility change: the condition that triggers evictCachedUserSharedGroups() fires when the original
     * value is "everybody", regardless of the new value.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3285">OF-3285: Changes to group sharing visibility are not immediately reflected in users' contact lists</a>
     */
    @Test
    public void sharingChangeFromEverybodyEvictsUserGroupsCacheEntries() throws Exception
    {
        // Setup test fixture: a group whose sharing is about to change FROM "everybody" to "nobody".
        final String groupName = "test-shared-group";
        when(cachedGroup.getName()).thenReturn(groupName);

        // The group's properties must return "nobody" as the new value so that propertyChangePostProcess can read it when evaluating the change.
        final PersistableMap<String, String> props = new PersistableMap<>() {
            @Override
            public String put(final String key, final String value, final boolean persist) {
                return super.put(key, value);
            }
        };
        props.put(Group.SHARED_ROSTER_SHOW_IN_ROSTER_PROPERTY_KEY, "nobody");
        when(cachedGroup.getProperties()).thenReturn(props);

        // The force-lookup inside propertyModifiedPostProcess must resolve the group.
        doReturn(cachedGroup).when(groupProvider).getGroup(groupName);

        // Pre-populate groupMetaCache with a USER_GROUPS entry (to be evicted) and a paginated GROUP_NAMES entry (must survive, as it is a different cache responsibility).
        final String userGroupsKey = "USER_GROUPSjane@example.org";
        final String paginatedKey  = "GROUP_NAMES0,10";
        groupMetaCache.put(userGroupsKey, new HashSet<String>());
        groupMetaCache.put(paginatedKey,  new HashSet<String>());

        final GroupManager manager = new GroupManager(groupProvider, groupCache, groupMetaCache) {
            @Override
            protected void dispatchGroupEvent(final Group group, final GroupEventDispatcher.EventType type, final Map<String, ?> params) {
                // Suppress event dispatch; not the focus of this test.
            }
        };

        // Execute system under test: simulate a sharing-mode change FROM "everybody" to "nobody".
        manager.propertyModifiedPostProcess(cachedGroup, Group.SHARED_ROSTER_SHOW_IN_ROSTER_PROPERTY_KEY, "everybody");

        // Verify result: USER_GROUPS must be evicted; paginated GROUP_NAMES must be left intact.
        assertNull(groupMetaCache.get(userGroupsKey), "USER_GROUPS cache entry should be evicted when group sharing changes from 'everybody'.");
        assertNull(groupMetaCache.get(paginatedKey), "Paginated GROUP_NAMES cache entry should be evicted by evictCachedUserSharedGroups().");
    }

    /**
     * Unit-level test (not behavioral): Verifies internal cache-eviction behavior when a group's sharing target list
     * is modified AND one of the target groups is itself shared with everybody. This test validates that
     * propertyModifiedPostProcess evicts all per-user group-membership cache entries (keys prefixed with USER_GROUPS_KEY)
     * from groupMetaCache. This is a complex scenario combining multiple cache-eviction conditions. This test is
     * implementation-focused and necessary to ensure cache correctness but does not represent user-visible behavior.
     *
     * Without this invalidation, a user's cached group memberships would be stale after the sharing configuration change,
     * causing their contact list to not reflect the updated sharing state until the cache naturally expires.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3285">OF-3285: Changes to group sharing visibility are not immediately reflected in users' contact lists</a>
     */
    @Test
    public void sharingTargetListChangeWithEverybodyGroupInvalidatesPerUserGroupMembershipCache() throws Exception
    {
        // Setup test fixture: Group B is shared with everybody and has no members or admins.
        final String groupAName = "group-a";
        final String groupBName = "group-b";

        final Group groupB = mock(Group.class);
        when(groupB.getSharedWith()).thenReturn(SharedGroupVisibility.everybody);
        when(groupB.getAdmins()).thenReturn(new HashSet<>());
        when(groupB.getMembers()).thenReturn(new HashSet<>());
        groupCache.put(groupBName, CacheableOptional.of(groupB));

        // Group A is shared with users of Group B.
        when(cachedGroup.getName()).thenReturn(groupAName);
        when(cachedGroup.getSharedWith()).thenReturn(SharedGroupVisibility.usersOfGroups);
        when(cachedGroup.getSharedWithUsersInGroupNames()).thenReturn(List.of(groupBName));

        // Group A's properties return the new group-list value; this differs from the originalValue passed to
        // propertyModifiedPostProcess below, ensuring the cache-invalidation branch is entered.
        final PersistableMap<String, String> props = new PersistableMap<>() {
            @Override
            public String put(final String key, final String value, final boolean persist) {
                return super.put(key, value);
            }
        };
        props.put(Group.SHARED_ROSTER_GROUP_LIST_PROPERTY_KEY, groupBName);
        when(cachedGroup.getProperties()).thenReturn(props);

        // The reload of the group after the property change must resolve Group A.
        doReturn(cachedGroup).when(groupProvider).getGroup(groupAName);
        doThrow(new GroupNotFoundException()).when(groupProvider).getGroup(argThat(arg -> !arg.equals(groupAName) && !arg.equals(groupBName)));

        // Sharing must be supported so that the shared-group target list is traversed.
        when(groupProvider.isSharingSupported()).thenReturn(true);

        // Pre-populate the cache with a per-user group-membership entry (to be invalidated) and a paginated
        // group-names entry (must survive, as it covers a different concern).
        final String userGroupsKey = "USER_GROUPSjane@example.org";
        final String paginatedKey  = "GROUP_NAMES0,10";
        groupMetaCache.put(userGroupsKey, new HashSet<String>());
        groupMetaCache.put(paginatedKey,  new HashSet<String>());

        final GroupManager manager = new GroupManager(groupProvider, groupCache, groupMetaCache) {
            @Override
            protected void dispatchGroupEvent(final Group group, final GroupEventDispatcher.EventType type, final Map<String, ?> params) {
                // Suppress event dispatch; not the focus of this test.
            }
        };

        // Execute system under test: change Group A's list of groups it is shared with.
        manager.propertyModifiedPostProcess(cachedGroup, Group.SHARED_ROSTER_GROUP_LIST_PROPERTY_KEY, "old-group");

        // Verify result: per-user group-membership cache entries must be invalidated so that subsequent lookups return
        // fresh data; paginated group-names cache entries are unrelated and must be left intact.
        assertNull(groupMetaCache.get(userGroupsKey), "Per-user group-membership cache entry should be invalidated after sharing target list changes when one target is shared with everybody.");
        assertNotNull(groupMetaCache.get(paginatedKey), "Paginated group-names cache entry should NOT be invalidated by a sharing target list change.");
    }

    /**
     * Verifies that deleteGroupPostProcess evicts the per-user group-membership cache entries for members of a
     * NON-SHARED group. This test exposes the bug where evictCachedUsersForGroup only processes groups returned by
     * getSharedGroups(), which returns empty for non-shared groups, leaving members with stale cache entries.
     *
     * This is a regression test for the issue where members of deleted non-shared groups retain stale
     * cached membership information in their per-user group cache.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3287">Issue: Group members' user cache not evicted for non-shared groups</a>
     */
    @Test
    public void deleteGroupPostProcessEvictsPerUserCacheForNonSharedGroupMembers() throws Exception
    {
        // Setup test fixture: a non-shared group with members and admins
        final String groupName = "test-group-non-shared";
        final JID member1 = new JID("member1@example.org");
        final JID member2 = new JID("member2@example.org");
        final JID admin1 = new JID("admin1@example.org");

        final Group groupToDelete = mock(Group.class);
        when(groupToDelete.getName()).thenReturn(groupName);
        when(groupToDelete.getSharedWith()).thenReturn(SharedGroupVisibility.nobody);
        when(groupToDelete.getMembers()).thenReturn(new HashSet<>(Arrays.asList(member1, member2)));
        when(groupToDelete.getAdmins()).thenReturn(new HashSet<>(List.of(admin1)));

        // Pre-populate per-user group-membership cache for all members
        final String member1CacheKey = "USER_GROUPS" + member1.toBareJID();
        final String member2CacheKey = "USER_GROUPS" + member2.toBareJID();
        final String admin1CacheKey = "USER_GROUPS" + admin1.toBareJID();
        final HashSet<String> member1Groups = new HashSet<>(Arrays.asList(groupName, "other-group"));
        final HashSet<String> member2Groups = new HashSet<>(List.of(groupName));
        final HashSet<String> admin1Groups = new HashSet<>(List.of(groupName));

        groupMetaCache.put(member1CacheKey, member1Groups);
        groupMetaCache.put(member2CacheKey, member2Groups);
        groupMetaCache.put(admin1CacheKey, admin1Groups);

        final GroupManager manager = new GroupManager(groupProvider, groupCache, groupMetaCache) {
            @Override
            protected void dispatchGroupEvent(final Group group, final GroupEventDispatcher.EventType type, final Map<String, ?> params) {
                // Suppress event dispatch; not the focus of this test.
            }
        };

        // Verify precondition: cache entries exist before deletion
        assertNotNull(groupMetaCache.get(member1CacheKey), "Precondition: member1 should have cached groups before deletion.");
        assertNotNull(groupMetaCache.get(member2CacheKey), "Precondition: member2 should have cached groups before deletion.");
        assertNotNull(groupMetaCache.get(admin1CacheKey), "Precondition: admin1 should have cached groups before deletion.");

        // Execute system under test: delete a non-shared group
        manager.deleteGroupPostProcess(groupToDelete);

        // Verify result: per-user group-membership cache entries must be evicted for all members/admins
        assertNull(groupMetaCache.get(member1CacheKey), "REGRESSION: member1's per-user group-membership cache should be evicted after group deletion.");
        assertNull(groupMetaCache.get(member2CacheKey), "REGRESSION: member2's per-user group-membership cache should be evicted after group deletion.");
        assertNull(groupMetaCache.get(admin1CacheKey), "REGRESSION: admin1's per-user group-membership cache should be evicted after group deletion.");
    }

    /**
     * Verifies that renameGroupPostProcess evicts the per-user group-membership cache entries for members of a
     * NON-SHARED group. This test exposes the same bug as deleteGroupPostProcess: evictCachedUsersForGroup only
     * processes groups returned by getSharedGroups(), which returns empty for non-shared groups, leaving members
     * with stale cache entries.
     *
     * The test exercises the full rename post-processing flow, including forced refresh of the renamed group and
     * cache updates for old/new group names.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3287">Issue: Group members' user cache not evicted for non-shared groups</a>
     */
    @Test
    public void renameGroupPostProcessEvictsPerUserCacheAndRefreshesGroupCacheForNonSharedGroupMembers() throws Exception
    {
        // Setup test fixture: a non-shared group with members
        final String oldGroupName = "old-group-name";
        final String newGroupName = "new-group-name";
        final JID member1 = new JID("member1@example.org");
        final JID admin1 = new JID("admin1@example.org");

        final Group renamedGroup = mock(Group.class);
        when(renamedGroup.getName()).thenReturn(newGroupName);
        when(renamedGroup.getSharedWith()).thenReturn(SharedGroupVisibility.nobody);
        when(renamedGroup.getMembers()).thenReturn(new HashSet<>(List.of(member1)));
        when(renamedGroup.getAdmins()).thenReturn(new HashSet<>(List.of(admin1)));
        doReturn(renamedGroup).when(groupProvider).getGroup(newGroupName);

        // Simulate stale cache entries before rename: old-name entry should be removed and new-name entry refreshed.
        groupCache.put(oldGroupName, CacheableOptional.of(cachedGroup));
        groupCache.put(newGroupName, CacheableOptional.of(cachedGroup));

        // Pre-populate per-user group-membership cache with old group name
        final String memberCacheKey = "USER_GROUPS" + member1.toBareJID();
        final HashSet<String> memberGroups = new HashSet<>(Arrays.asList(oldGroupName, "other-group"));
        groupMetaCache.put(memberCacheKey, memberGroups);

        final GroupManager manager = new GroupManager(groupProvider, groupCache, groupMetaCache) {
            @Override
            protected void dispatchGroupEvent(final Group group, final GroupEventDispatcher.EventType type, final Map<String, ?> params) {
                // Suppress event dispatch; not the focus of this test.
            }
        };

        // Verify precondition: cache entry exists before rename
        assertNotNull(groupMetaCache.get(memberCacheKey), "Precondition: member should have cached groups before rename.");
        assertNotNull(groupCache.get(oldGroupName), "Precondition: old group-name cache entry should exist before rename.");

        // Execute system under test: rename a non-shared group
        // Exercise full rename post-processing, including cache refresh and event parameter construction.
        manager.renameGroupPostProcess(renamedGroup, oldGroupName);

        // Verify result: per-user group-membership cache entries must be evicted and group cache entries refreshed.
        assertNull(groupMetaCache.get(memberCacheKey), "REGRESSION: member's per-user group-membership cache should be evicted after group rename.");
        assertNull(groupCache.get(oldGroupName), "Old group-name cache entry should be removed after rename.");
        assertThat("Renamed group should be force-refreshed into cache under its new name.", groupCache.get(newGroupName), is(CacheableOptional.of(renamedGroup)));
        verify(groupProvider).getGroup(newGroupName);
    }

    /**
     * Verifies that createGroupPostProcess evicts the per-user group-membership cache entries for members of a newly
     * created NON-SHARED group. This is a precautionary test to ensure consistency in cache handling across all group
     * lifecycle operations.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3287">Issue: Group members' user cache not evicted for non-shared groups</a>
     */
    @Test
    public void createGroupPostProcessEvictsPerUserCacheForNewNonSharedGroupMembers() throws Exception
    {
        // Setup test fixture: a newly created non-shared group with members
        final String groupName = "new-group";
        final JID member1 = new JID("member1@example.org");

        final Group newGroup = mock(Group.class);
        when(newGroup.getName()).thenReturn(groupName);
        when(newGroup.getSharedWith()).thenReturn(SharedGroupVisibility.nobody);
        when(newGroup.getMembers()).thenReturn(new HashSet<>(List.of(member1)));
        when(newGroup.getAdmins()).thenReturn(new HashSet<>());

        // Pre-populate per-user group-membership cache (simulating a cached result from before the group existed)
        final String memberCacheKey = "USER_GROUPS" + member1.toBareJID();
        final HashSet<String> memberGroups = new HashSet<>(List.of("other-group"));
        groupMetaCache.put(memberCacheKey, memberGroups);

        final GroupManager manager = new GroupManager(groupProvider, groupCache, groupMetaCache) {
            @Override
            protected void dispatchGroupEvent(final Group group, final GroupEventDispatcher.EventType type, final Map<String, ?> params) {
                // Suppress event dispatch; not the focus of this test.
            }
        };

        // Verify precondition: cache entry exists before creation
        assertNotNull(groupMetaCache.get(memberCacheKey), "Precondition: member should have cached groups before new group is created.");

        // Execute system under test: create a new non-shared group
        manager.createGroupPostProcess(newGroup);

        // Verify result: per-user group-membership cache entries must be evicted to pick up the new group
        assertNull(groupMetaCache.get(memberCacheKey), "REGRESSION: member's per-user group-membership cache should be evicted after new group creation.");
    }

    /**
     * Verifies that changing a group's membership list properly evicts the affected users' per-user group-membership
     * caches. This test validates the propertyModifiedPostProcess behavior for SHARED_ROSTER_GROUP_LIST_PROPERTY_KEY
     * changes on non-shared groups.
     *
     * This ensures that when a group's sharing configuration changes, all affected users get fresh cache entries.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3287">Issue: Group members' user cache not evicted for non-shared groups</a>
     */
    @Test
    public void groupMembershipChangeEvictsAffectedUsersCache() throws Exception
    {
        // Setup test fixture: a group with members whose membership list is changing
        final String groupName = "test-group";
        final String targetGroupName = "target-group";
        final JID member1 = new JID("member1@example.org");
        final JID member2 = new JID("member2@example.org");

        final Group group = mock(Group.class);
        when(group.getName()).thenReturn(groupName);
        when(group.getSharedWith()).thenReturn(SharedGroupVisibility.usersOfGroups);
        when(group.getSharedWithUsersInGroupNames()).thenReturn(List.of(targetGroupName));
        when(group.getMembers()).thenReturn(new HashSet<>(Arrays.asList(member1, member2)));
        when(group.getAdmins()).thenReturn(new HashSet<>());

        // Target group also needs to be mocked
        final Group targetGroup = mock(Group.class);
        when(targetGroup.getMembers()).thenReturn(new HashSet<>());
        when(targetGroup.getAdmins()).thenReturn(new HashSet<>());

        // Group properties indicate the shared group list changed
        final PersistableMap<String, String> props = new PersistableMap<>() {
            @Override
            public String put(final String key, final String value, final boolean persist) {
                return super.put(key, value);
            }
        };
        props.put(Group.SHARED_ROSTER_GROUP_LIST_PROPERTY_KEY, targetGroupName);
        when(group.getProperties()).thenReturn(props);

        // The getGroup call during postProcess must resolve the group
        doReturn(group).when(groupProvider).getGroup(groupName);
        doThrow(new GroupNotFoundException()).when(groupProvider).getGroup("old-target-group");
        when(groupProvider.isSharingSupported()).thenReturn(true);

        // Cache the target group
        groupCache.put(targetGroupName, CacheableOptional.of(targetGroup));

        // Pre-populate per-user group-membership cache for members
        final String member1CacheKey = "USER_GROUPS" + member1.toBareJID();
        final String member2CacheKey = "USER_GROUPS" + member2.toBareJID();
        groupMetaCache.put(member1CacheKey, new HashSet<>(Arrays.asList(groupName, "other-group")));
        groupMetaCache.put(member2CacheKey, new HashSet<>(List.of(groupName)));

        final GroupManager manager = new GroupManager(groupProvider, groupCache, groupMetaCache) {
            @Override
            protected void dispatchGroupEvent(final Group group, final GroupEventDispatcher.EventType type, final Map<String, ?> params) {
                // Suppress event dispatch; not the focus of this test.
            }
        };

        // Verify precondition: cache entries exist before the modification
        assertNotNull(groupMetaCache.get(member1CacheKey), "Precondition: member1 should have cached groups before modification.");
        assertNotNull(groupMetaCache.get(member2CacheKey), "Precondition: member2 should have cached groups before modification.");

        // Execute system under test: change the group's shared-with list
        manager.propertyModifiedPostProcess(group, Group.SHARED_ROSTER_GROUP_LIST_PROPERTY_KEY, "old-target-group");

        // Verify result: per-user group-membership cache entries must be evicted for all members
        assertNull(groupMetaCache.get(member1CacheKey), "REGRESSION: member1's per-user group-membership cache should be evicted after group sharing change.");
        assertNull(groupMetaCache.get(member2CacheKey), "REGRESSION: member2's per-user group-membership cache should be evicted after group sharing change.");
    }

    /**
     * Verifies that per-user group-membership cache eviction works correctly for shared groups (where getSharedGroups()
     * DOES return some groups). This is a positive test case to ensure that the eviction mechanism works for shared
     * groups and serves as a baseline for comparison with the non-shared group failure case.
     *
     * This test validates that members of groups that ARE shared INTO generate proper cache evictions.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3287">Issue: Group members' user cache not evicted for non-shared groups</a>
     */
    @Test
    public void deleteSharedGroupProperlyEvictsSharedIntoGroupMembers() throws Exception
    {
        // Setup test fixture: Group A is shared into Group B, and Group B has members
        final String groupAName = "group-a-shared";
        final String groupBName = "group-b";
        final JID groupBMember = new JID("member@example.org");

        // Group A: the one being deleted (shared with users of Group B)
        final Group groupA = mock(Group.class);
        when(groupA.getName()).thenReturn(groupAName);
        when(groupA.getSharedWith()).thenReturn(SharedGroupVisibility.usersOfGroups);
        when(groupA.getSharedWithUsersInGroupNames()).thenReturn(List.of(groupBName));

        // Group B: a target group that Group A is shared with
        final Group groupB = mock(Group.class);
        when(groupB.getMembers()).thenReturn(new HashSet<>(List.of(groupBMember)));
        when(groupB.getAdmins()).thenReturn(new HashSet<>());

        groupCache.put(groupBName, CacheableOptional.of(groupB));

        when(groupProvider.isSharingSupported()).thenReturn(true);

        // Pre-populate per-user group-membership cache for Group B's member
        final String memberCacheKey = "USER_GROUPS" + groupBMember.toBareJID();
        groupMetaCache.put(memberCacheKey, new HashSet<>(Arrays.asList(groupAName, groupBName)));

        final GroupManager manager = new GroupManager(groupProvider, groupCache, groupMetaCache) {
            @Override
            protected void dispatchGroupEvent(final Group group, final GroupEventDispatcher.EventType type, final Map<String, ?> params) {
                // Suppress event dispatch; not the focus of this test.
            }
        };

        // Verify precondition: cache entry exists
        assertNotNull(groupMetaCache.get(memberCacheKey), "Precondition: member should have cached groups before deletion.");

        // Execute system under test: delete the shared group
        manager.deleteGroupPostProcess(groupA);

        // Verify result: the member's cache IS evicted because Group B (shared-with) is included in evictCachedUsersForGroup
        assertNull(groupMetaCache.get(memberCacheKey), "Group B member's per-user group-membership cache SHOULD be evicted because Group A is shared with Group B.");
    }

    /**
     * Verifies that per-user group-membership cache eviction correctly handles the case where a group is shared with
     * multiple target groups. This tests that all members of all target groups get their caches evicted.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3287">Issue: Group members' user cache not evicted for non-shared groups</a>
     */
    @Test
    public void deleteGroupSharedWithMultipleTargetsEvictsAllTargetMembers() throws Exception
    {
        // Setup test fixture: Group A is shared with Groups B and C
        final String groupAName = "group-a";
        final String groupBName = "group-b";
        final String groupCName = "group-c";
        final JID memberB = new JID("memberb@example.org");
        final JID memberC = new JID("memberc@example.org");

        // Group A: being deleted, shared with B and C
        final Group groupA = mock(Group.class);
        when(groupA.getName()).thenReturn(groupAName);
        when(groupA.getSharedWith()).thenReturn(SharedGroupVisibility.usersOfGroups);
        when(groupA.getSharedWithUsersInGroupNames()).thenReturn(Arrays.asList(groupBName, groupCName));

        // Group B: a target group, with a member
        final Group groupB = mock(Group.class);
        when(groupB.getMembers()).thenReturn(new HashSet<>(List.of(memberB)));
        when(groupB.getAdmins()).thenReturn(new HashSet<>());

        // Group C: another target group, with a member
        final Group groupC = mock(Group.class);
        when(groupC.getMembers()).thenReturn(new HashSet<>(List.of(memberC)));
        when(groupC.getAdmins()).thenReturn(new HashSet<>());

        groupCache.put(groupBName, CacheableOptional.of(groupB));
        groupCache.put(groupCName, CacheableOptional.of(groupC));

        when(groupProvider.isSharingSupported()).thenReturn(true);

        // Pre-populate caches for both members
        final String memberBCacheKey = "USER_GROUPS" + memberB.toBareJID();
        final String memberCCacheKey = "USER_GROUPS" + memberC.toBareJID();
        groupMetaCache.put(memberBCacheKey, new HashSet<>(List.of(groupBName)));
        groupMetaCache.put(memberCCacheKey, new HashSet<>(List.of(groupCName)));

        final GroupManager manager = new GroupManager(groupProvider, groupCache, groupMetaCache) {
            @Override
            protected void dispatchGroupEvent(final Group group, final GroupEventDispatcher.EventType type, final Map<String, ?> params) {
                // Suppress event dispatch; not the focus of this test.
            }
        };

        // Verify precondition: both cache entries exist before deletion
        assertNotNull(groupMetaCache.get(memberBCacheKey), "Precondition: member B should have cached groups before deletion.");
        assertNotNull(groupMetaCache.get(memberCCacheKey), "Precondition: member C should have cached groups before deletion.");

        // Execute system under test: delete group A which is shared with B and C
        manager.deleteGroupPostProcess(groupA);

        // Verify result: both target group members have their caches evicted because they are in the shared-with groups
        assertNull(groupMetaCache.get(memberBCacheKey), "Member of Group B should have cache evicted after Group A deletion.");
        assertNull(groupMetaCache.get(memberCCacheKey), "Member of Group C should have cache evicted after Group A deletion.");
    }
}
