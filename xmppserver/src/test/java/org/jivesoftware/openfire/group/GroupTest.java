/*
 * Copyright (C) 2023 Ignite Realtime Foundation. All rights reserved.
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
import org.jivesoftware.openfire.event.GroupEventListener;
import org.jivesoftware.util.CacheableOptional;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PersistableMap;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xmpp.packet.JID;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the functionality as implemented by #Group
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
@ExtendWith(MockitoExtension.class)
public class GroupTest
{
    private static Cache<String, CacheableOptional<Group>> groupCache;

    private GroupManager groupManager;

    @BeforeAll
    public static void beforeClass() throws Exception {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
        groupCache = CacheFactory.createCache("Group");
        groupCache.clear();
        JiveGlobals.setProperty("provider.group.className", GroupTest.TestGroupProvider.class.getName());
    }

    @BeforeEach
    public void setUp() {
        // Ensure that Openfire caches are reset before each test to avoid tests to affect each-other.
        Arrays.stream(CacheFactory.getAllCaches()).forEach(Map::clear);
        groupManager = GroupManager.getInstance();
    }

    @AfterEach
    public void tearDown() {
        // Teardown fixture by removing any groups that have been created.
        GroupManager.getInstance().getGroups().forEach(group -> {
            try {
                GroupManager.getInstance().deleteGroup(group);
            } catch (GroupNotFoundException e) {
                throw new RuntimeException(e);
            }
        });

        groupCache.clear();
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

        GroupManager.getInstance().getGroups().forEach(group -> {
            try {
                GroupManager.getInstance().deleteGroup(group);
            } catch (GroupNotFoundException e) {
                throw new RuntimeException(e);
            }
        });

        groupCache.clear();
    }

    /**
     * Asserts that when a bare JID is added to a group, it is added successfully.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2708">OF-2708: Ensure that Groups operate on bare JIDs</a>
     */
    @Test
    public void testAddBareJid() throws Exception
    {
        // Setup test fixture.
        final String groupName = "unit-test-group-h";
        final Group group = groupManager.createGroup(groupName);
        final JID fullJid = new JID("unit-test-user-h", "example.org", "unit-test-resource-h");
        final JID bareJid = fullJid.asBareJID();

        // Execute system under test.
        final boolean result = group.getAdmins().add(bareJid);

        // Verify results.
        assertTrue(result);
        assertTrue(group.getAdmins().contains(fullJid)); // OF-2708: Contains check should be applied based on the bare-JID equivalent!
        assertTrue(group.getAdmins().contains(bareJid));
    }

    /**
     * Asserts that when a full JID is added to a group, its bare JID representation is added successfully.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2708">OF-2708: Ensure that Groups operate on bare JIDs</a>
     */
    @Test
    public void testAddFullJid() throws Exception
    {
        // Setup test fixture.
        final String groupName = "unit-test-group-i";
        final Group group = groupManager.createGroup(groupName);
        final JID fullJid = new JID("unit-test-user-i", "example.org", "unit-test-resource-i");

        final JID bareJid = fullJid.asBareJID();

        // Execute system under test.
        final boolean result = group.getAdmins().add(fullJid);

        // Verify results.
        assertTrue(result);
        assertTrue(group.getAdmins().contains(fullJid));
        assertTrue(group.getAdmins().contains(bareJid));
    }

    /**
     * Asserts that an entity can be removed from a group by using its bare JID.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2708">OF-2708: Ensure that Groups operate on bare JIDs</a>
     */
    @Test
    public void testRemoveBareJid() throws Exception
    {
        // Setup test fixture.
        final String groupName = "unit-test-group-j";
        final Group group = groupManager.createGroup(groupName);
        final JID fullJid = new JID("unit-test-user-j", "example.org", "unit-test-resource-j");
        final JID bareJid = fullJid.asBareJID();
        group.getAdmins().add(fullJid);

        // Execute system under test.
        final boolean result = group.getAdmins().remove(bareJid);

        // Verify results.
        assertTrue(result);
        assertFalse(group.getAdmins().contains(fullJid));
        assertFalse(group.getAdmins().contains(bareJid));
    }

    /**
     * Asserts that an entity can be removed from a group by using its full JID.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2708">OF-2708: Ensure that Groups operate on bare JIDs</a>
     */
    @Test
    public void testRemoveFullJid() throws Exception
    {
        // Setup test fixture.
        final String groupName = "unit-test-group-k";
        final Group group = groupManager.createGroup(groupName);
        final JID fullJid = new JID("unit-test-user-k", "example.org", "unit-test-resource-k");
        final JID bareJid = fullJid.asBareJID();
        group.getAdmins().add(bareJid);

        // Execute system under test.
        final boolean result = group.getAdmins().remove(fullJid);

        // Verify results.
        assertTrue(result);
        assertFalse(group.getAdmins().contains(fullJid));
        assertFalse(group.getAdmins().contains(bareJid));
    }

    /**
     * Asserts that when an entity is in a group, added with its bare JID, adding a corresponding full JID does not change the group.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2708">OF-2708: Ensure that Groups operate on bare JIDs</a>
     */
    @Test
    public void testOverrideBareJidWithFullJid() throws Exception
    {
        // Setup test fixture.
        final String groupName = "unit-test-group-l";
        final Group group = groupManager.createGroup(groupName);
        final JID fullJid = new JID("unit-test-user-l", "example.org", "unit-test-resource-l");
        final JID bareJid = fullJid.asBareJID();
        group.getAdmins().add(bareJid);

        // Execute system under test.
        final boolean result = group.getAdmins().add(fullJid);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that when an entity is in a group, added with its full JID, adding a corresponding bare JID does not change the group.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2708">OF-2708: Ensure that Groups operate on bare JIDs</a>
     */
    @Test
    public void testOverrideFullJidWithBareJid() throws Exception
    {
        // Setup test fixture.
        final String groupName = "unit-test-group-m";
        final Group group = groupManager.createGroup(groupName);
        final JID fullJid = new JID("unit-test-user-m", "example.org", "unit-test-resource-m");
        final JID bareJid = fullJid.asBareJID();
        group.getAdmins().add(fullJid);

        // Execute system under test.
        final boolean result = group.getAdmins().add(bareJid);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Verifies that Group cache's content contains a group after it has been created.
     */
    @Test
    public void testReflectedInCacheGroupCreated() throws Exception
    {
        // Setup test fixture.
        final String groupName = "unit-test-group-n";
        final Group group = groupManager.createGroup(groupName);
        final JID testUser = new JID("unit-test-user-n", "example.org", null);

        // Execute system under test.
        group.getAdmins().add(testUser);
        final CacheableOptional<Group> cachedGroup = groupCache.get(groupName);

        // Verify results.
        assertNotNull(cachedGroup.get());
        assertTrue(cachedGroup.get().isUser(testUser));
    }

    /**
     * Verifies that the corresponding event listener has been invoked after a group has been created.
     */
    @Test
    public void testEventListenerInvokedGroupCreated() throws Exception
    {
        // Setup test fixture.
        final String groupName = "unit-test-group-o";
        final RecordingGroupEventListener listener = new RecordingGroupEventListener();
        GroupEventDispatcher.addListener(listener);

        // Execute system under test.
        groupManager.createGroup(groupName);

        // Verify results.
        assertEquals(groupName, listener.lastGroupCreated);
    }

    /**
     * Verifies that Group cache's content no longer contains a group after it has been deleted.
     */
    @Test
    public void testReflectedInCacheGroupDeleted() throws Exception
    {
        // Setup test fixture.
        final String groupName = "unit-test-group-p";
        final Group group = groupManager.createGroup(groupName);
        final JID testUser = new JID("unit-test-user-p", "example.org", null);
        group.getAdmins().add(testUser);

        // Execute system under test.
        groupManager.deleteGroup(group);
        final CacheableOptional<Group> cachedGroup = groupCache.get(groupName);

        // Verify results.
        assertNull(cachedGroup.get());
    }

    /**
     * Verifies that the corresponding event listener has been invoked after a group has been deleted.
     */
    @Test
    public void testEventListenerInvokedGroupDeleted() throws Exception
    {
        // Setup test fixture.
        final String groupName = "unit-test-group-q";
        final Group group = groupManager.createGroup(groupName);
        final RecordingGroupEventListener listener = new RecordingGroupEventListener();
        GroupEventDispatcher.addListener(listener);

        // Execute system under test.
        groupManager.deleteGroup(group);

        // Verify results.
        assertEquals(groupName, listener.lastGroupDeleted);
    }

    /**
     * Verifies that the group contained in the Group cache contains an admin user after it has been added to the group.
     */
    @Test
    public void testReflectedInCacheAdminAdded() throws Exception
    {
        // Setup test fixture.
        final String groupName = "unit-test-group-r";
        final Group group = groupManager.createGroup(groupName);
        final JID testUser = new JID("unit-test-user-r", "example.org", null);

        // Execute system under test.
        group.getAdmins().add(testUser);
        final CacheableOptional<Group> cachedGroup = groupCache.get(groupName);

        // Verify results.
        assertTrue(cachedGroup.get().isUser(testUser));
        assertTrue(cachedGroup.get().getAdmins().contains(testUser));
    }

    /**
     * Verifies that the corresponding event listener has been invoked after a user has been added to a group as an admin.
     */
    @Test
    public void testEventListenerInvokedAdminAdded() throws Exception
    {
        // Setup test fixture.
        final String groupName = "unit-test-group-a";
        final Group group = groupManager.createGroup(groupName);
        final JID testUser = new JID("unit-test-user-a", "example.org", null);
        final RecordingGroupEventListener listener = new RecordingGroupEventListener();
        GroupEventDispatcher.addListener(listener);

        // Execute system under test.
        group.getAdmins().add(testUser);

        // Verify results.
        assertEquals(groupName, listener.lastGroupAdminAdded);
        assertEquals(testUser.toString(), listener.lastParams.get("admin"));
    }

    /**
     * Verifies that the group contained in the Group cache contains a member user after it has been added to the group.
     */
    @Test
    public void testReflectedInCacheMemberAdded() throws Exception
    {
        // Setup test fixture.
        final String groupName = "unit-test-group-b";
        final Group group = groupManager.createGroup(groupName);
        final JID testUser = new JID("unit-test-user-b", "example.org", null);

        // Execute system under test.
        group.getMembers().add(testUser);
        final CacheableOptional<Group> cachedGroup = groupCache.get(groupName);

        // Verify results.
        assertTrue(cachedGroup.get().isUser(testUser));
        assertTrue(cachedGroup.get().getMembers().contains(testUser));
    }

    /**
     * Verifies that the corresponding event listener has been invoked after a user has been added to a group as a member.
     */
    @Test
    public void testEventListenerInvokedMemberAdded() throws Exception
    {
        // Setup test fixture.
        final String groupName = "unit-test-group-c";
        final Group group = groupManager.createGroup(groupName);
        final JID testUser = new JID("unit-test-user-c", "example.org", null);
        final RecordingGroupEventListener listener = new RecordingGroupEventListener();
        GroupEventDispatcher.addListener(listener);

        // Execute system under test.
        group.getMembers().add(testUser);

        // Verify results.
        assertEquals(groupName, listener.lastGroupMemberAdded);
        assertEquals(testUser.toString(), listener.lastParams.get("member"));
    }

    /**
     * Verifies that the group contained in the Group cache no longer contains an admin user after it has been removed from the group.
     */
    @Test
    public void testReflectedInCacheAdminRemoved() throws Exception
    {
        // Setup test fixture.
        final String groupName = "unit-test-group-d";
        final Group group = groupManager.createGroup(groupName);
        final JID testUser = new JID("unit-test-user-d", "example.org", null);
        group.getAdmins().add(testUser);

        // Execute system under test.
        group.getAdmins().remove(testUser);
        final CacheableOptional<Group> cachedGroup = groupCache.get(groupName);

        // Verify results.
        assertFalse(cachedGroup.get().isUser(testUser));
        assertFalse(cachedGroup.get().getAdmins().contains(testUser));
    }

    /**
     * Verifies that the corresponding event listener has been invoked after a user that was an admin has been removed from a group.
     */
    @Test
    public void testEventListenerInvokedAdminRemoved() throws Exception
    {
        // Setup test fixture.
        final String groupName = "unit-test-group-e";
        final Group group = groupManager.createGroup(groupName);
        final JID testUser = new JID("unit-test-user-e", "example.org", null);
        group.getAdmins().add(testUser);
        final RecordingGroupEventListener listener = new RecordingGroupEventListener();
        GroupEventDispatcher.addListener(listener);

        // Execute system under test.
        group.getAdmins().remove(testUser);

        // Verify results.
        assertEquals(groupName, listener.lastGroupAdminRemoved);
        assertEquals(testUser.toString(), listener.lastParams.get("admin"));
    }

    /**
     * Verifies that the group contained in the Group cache no longer contains a member user after it has been removed from the group.
     */
    @Test
    public void testReflectedInCacheMemberRemoved() throws Exception
    {
        // Setup test fixture.
        final String groupName = "unit-test-group-f";
        final Group group = groupManager.createGroup(groupName);
        final JID testUser = new JID("unit-test-user-f", "example.org", null);
        group.getMembers().add(testUser);

        // Execute system under test.
        group.getMembers().remove(testUser);
        final CacheableOptional<Group> cachedGroup = groupCache.get(groupName);

        // Verify results.
        assertFalse(cachedGroup.get().isUser(testUser));
        assertFalse(cachedGroup.get().getMembers().contains(testUser));
    }

    /**
     * Verifies that the corresponding event listener has been invoked after a user that was a member has been removed from a group.
     */
    @Test
    public void testEventListenerInvokedMemberRemoved() throws Exception
    {
        // Setup test fixture.
        final String groupName = "unit-test-group-g";
        final Group group = groupManager.createGroup(groupName);
        final JID testUser = new JID("unit-test-user-g", "example.org", null);
        group.getMembers().add(testUser);
        final RecordingGroupEventListener listener = new RecordingGroupEventListener();
        GroupEventDispatcher.addListener(listener);

        // Execute system under test.
        group.getMembers().remove(testUser);

        // Verify results.
        assertEquals(groupName, listener.lastGroupMemberRemoved);
        assertEquals(testUser.toString(), listener.lastParams.get("member"));
    }


    /**
     * Verifies that when a group is explicitly shared with 1 other group, that it is then shared with that group and itself
     */
    @Test
    public void testGroupSharingSingle() throws Exception
    {
        // Setup test fixture.
        final String groupName = "unit-test-group-sharing-single";
        final String groupNameSharedWith = "unit-test-group-sharedWith";
        final Group group = groupManager.createGroup(groupName);
        final Group groupSharedWith = groupManager.createGroup(groupNameSharedWith);

        // Execute system under test.
        group.shareWithUsersInGroups(List.of(groupSharedWith.getName()), "TestGroupSharing");

        // Verify results.
        List<String> sharedWithGroups = group.getSharedWithUsersInGroupNames();
        assertEquals( sharedWithGroups.size(), 2);
        assertTrue(sharedWithGroups.contains(groupName));
        assertTrue(sharedWithGroups.contains(groupNameSharedWith));
    }

    /**
     * Verifies that when a group is explicitly shared with multiple other groups, that it is then shared with those groups and itself
     */
    @Test
    public void testGroupSharingMultiple() throws Exception
    {
        // Setup test fixture.
        final String groupName = "unit-test-group-sharing-multiple";
        final String groupNameSharedWith1 = "unit-test-group-sharedWith1";
        final String groupNameSharedWith2 = "unit-test-group-sharedWith2";
        final Group group = groupManager.createGroup(groupName);
        final Group groupSharedWith1 = groupManager.createGroup(groupNameSharedWith1);
        final Group groupSharedWith2 = groupManager.createGroup(groupNameSharedWith2);

        // Execute system under test.
        group.shareWithUsersInGroups(
            List.of(groupSharedWith1.getName(), groupSharedWith2.getName()),
            "TestGroupSharing");

        // Verify results.
        List<String> sharedWithGroups = group.getSharedWithUsersInGroupNames();
        assertEquals( sharedWithGroups.size(), 3);
        assertTrue(sharedWithGroups.contains(groupName));
        assertTrue(sharedWithGroups.contains(groupNameSharedWith1));
        assertTrue(sharedWithGroups.contains(groupNameSharedWith2));
    }

    /**
     * A {@link GroupEventListener} that records its last invocation.
     */
    public static class RecordingGroupEventListener implements GroupEventListener
    {
        public String lastGroupCreated;
        public String lastGroupDeleted;
        public String lastGroupModified;
        public String lastGroupMemberAdded;
        public String lastGroupMemberRemoved;
        public String lastGroupAdminAdded;
        public String lastGroupAdminRemoved;

        public Map lastParams;

        @Override
        public void groupCreated(Group group, Map params)
        {
            lastGroupCreated = group.getName();
            lastParams = params;
        }

        @Override
        public void groupDeleting(Group group, Map params)
        {
            lastGroupDeleted = group.getName();
            lastParams = params;
        }

        @Override
        public void groupModified(Group group, Map params)
        {
            lastGroupModified = group.getName();
            lastParams = params;
        }

        @Override
        public void memberAdded(Group group, Map params)
        {
            lastGroupMemberAdded = group.getName();
            lastParams = params;
        }

        @Override
        public void memberRemoved(Group group, Map params)
        {
            lastGroupMemberRemoved = group.getName();
            lastParams = params;
        }

        @Override
        public void adminAdded(Group group, Map params)
        {
            lastGroupAdminAdded = group.getName();
            lastParams = params;
        }

        @Override
        public void adminRemoved(Group group, Map params)
        {
            lastGroupAdminRemoved = group.getName();
            lastParams = params;
        }
    }

    /**
     * An in-memory implementation of a GroupProvider with limited functionality.
     */
    public static class TestGroupProvider implements GroupProvider {

        private final Map<String, String> descriptionsByGroupName = new HashMap<>();
        private final ConcurrentMap<String, List<JID>> membersByGroupName = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, List<JID>> adminsByGroupName = new ConcurrentHashMap<>();


        @Override
        public Group createGroup(final String name) throws GroupAlreadyExistsException, GroupNameInvalidException {
            if (descriptionsByGroupName.containsKey(name)) {
                throw new GroupAlreadyExistsException();
            }
            descriptionsByGroupName.put(name, null);

            final String description = descriptionsByGroupName.get(name);
            final Collection<JID> members = membersByGroupName.computeIfAbsent(name, n -> new LinkedList<>());
            final Collection<JID> administrators = adminsByGroupName.computeIfAbsent(name, n -> new LinkedList<>());
            return new Group(name, description, members, administrators);
        }

        @Override
        public void deleteGroup(final String name) throws GroupNotFoundException {
            membersByGroupName.remove(name);
            adminsByGroupName.remove(name);
            if (!descriptionsByGroupName.containsKey(name)) {
                throw new GroupNotFoundException();
            }
            descriptionsByGroupName.remove(name);
        }

        @Override
        public Group getGroup(final String name) throws GroupNotFoundException {
            if (!descriptionsByGroupName.containsKey(name)) {
                throw new GroupNotFoundException();
            }

            final String description = descriptionsByGroupName.get(name);
            final Collection<JID> members = membersByGroupName.computeIfAbsent(name, n -> new LinkedList<>());
            final Collection<JID> administrators = adminsByGroupName.computeIfAbsent(name, n -> new LinkedList<>());
            return new Group(name, description, members, administrators);
        }

        @Override
        public void setName(final String oldName, final String newName) throws GroupAlreadyExistsException, GroupNameInvalidException, GroupNotFoundException {
            if (!descriptionsByGroupName.containsKey(oldName)) {
                throw new GroupNotFoundException();
            }
            if (!descriptionsByGroupName.containsKey(newName)) {
                throw new GroupAlreadyExistsException();
            }

            descriptionsByGroupName.put(newName, descriptionsByGroupName.remove(oldName));
            membersByGroupName.put(newName, membersByGroupName.remove(oldName));
            adminsByGroupName.put(newName, adminsByGroupName.remove(oldName));
        }

        @Override
        public void setDescription(final String name, final String description) throws GroupNotFoundException {
            if (!descriptionsByGroupName.containsKey(name)) {
                throw new GroupNotFoundException();
            }
            descriptionsByGroupName.put(name, description);
        }

        @Override
        public int getGroupCount() {
            return descriptionsByGroupName.size();
        }

        @Override
        public Collection<String> getGroupNames() {
            return descriptionsByGroupName.keySet();
        }

        @Override
        public boolean isSharingSupported() {
            return false;
        }

        @Override
        public Collection<String> getSharedGroupNames() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Collection<String> getSharedGroupNames(final JID user) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Collection<String> getPublicSharedGroupNames() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Collection<String> getVisibleGroupNames(final String userGroup) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Collection<String> getGroupNames(final int startIndex, final int numResults) {
            return getGroupNames().stream().skip(startIndex).limit(numResults).collect(Collectors.toList());
        }

        @Override
        public Collection<String> getGroupNames(JID user) {
            user = user.asBareJID();
            final Set<String> result = new HashSet<>();
            for (final Map.Entry<String, List<JID>> entry : membersByGroupName.entrySet()) {
                if (entry.getValue().contains(user)) {
                    result.add(entry.getKey());
                }
            }
            for (final Map.Entry<String, List<JID>> entry : adminsByGroupName.entrySet()) {
                if (entry.getValue().contains(user)) {
                    result.add(entry.getKey());
                }
            }
            return result;
        }

        @Override
        public void addMember(final String groupName, JID user, final boolean administrator) throws GroupNotFoundException {
            user = user.asBareJID();

            if (!descriptionsByGroupName.containsKey(groupName)) {
                throw new GroupNotFoundException();
            }
            if (administrator) {
                adminsByGroupName.computeIfAbsent(groupName, n -> new LinkedList<>()).add(user);
            } else {
                membersByGroupName.computeIfAbsent(groupName, n -> new LinkedList<>()).add(user);
            }
        }

        @Override
        public void updateMember(final String groupName, JID user, final boolean administrator) throws GroupNotFoundException {
            user = user.asBareJID();

            if (!descriptionsByGroupName.containsKey(groupName)) {
                throw new GroupNotFoundException();
            }
            if (administrator) {
                membersByGroupName.getOrDefault(groupName, new ArrayList<>()).remove(user);
                adminsByGroupName.computeIfAbsent(groupName, n -> new LinkedList<>()).add(user);
            } else {
                adminsByGroupName.getOrDefault(groupName, new ArrayList<>()).remove(user);
                membersByGroupName.computeIfAbsent(groupName, n -> new LinkedList<>()).add(user);
            }
        }

        @Override
        public void deleteMember(final String groupName, JID user) {
            user = user.asBareJID();
            membersByGroupName.getOrDefault(groupName, new ArrayList<>()).remove(user);
            adminsByGroupName.getOrDefault(groupName, new ArrayList<>()).remove(user);
        }

        @Override
        public boolean isReadOnly() {
            return false;
        }

        @Override
        public Collection<String> search(final String query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Collection<String> search(final String query, final int startIndex, final int numResults) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Collection<String> search(final String key, final String value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSearchSupported() {
            return false;
        }

        @Override
        public PersistableMap<String, String> loadProperties(final Group group) {
            return new PersistableMap<>() {
                @Override
                public String put(String key, String value, boolean persist) {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }
}
