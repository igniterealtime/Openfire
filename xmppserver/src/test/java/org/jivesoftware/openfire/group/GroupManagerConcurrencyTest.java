/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
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
import org.jivesoftware.util.PersistableMap;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xmpp.packet.JID;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for concurrency issues in {@link GroupManager}'s locking strategy.
 */
@ExtendWith(MockitoExtension.class)
public class GroupManagerConcurrencyTest
{
    private static Cache<String, CacheableOptional<Group>> groupCache;
    private static Cache<String, Serializable> groupMetaCache;

    private GroupManager groupManager;
    private ControllableGroupProvider provider;

    @BeforeAll
    public static void beforeClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
        groupCache = CacheFactory.createCache("Group");
        groupMetaCache = CacheFactory.createCache("Group Metadata Cache");
    }

    @BeforeEach
    public void setUp()
    {
        Arrays.stream(CacheFactory.getAllCaches()).forEach(Map::clear);
        provider = new ControllableGroupProvider();
        groupManager = new GroupManager(provider, groupCache, groupMetaCache);
        GroupManager.setInstance(groupManager);
    }

    @AfterEach
    public void tearDown() {
        groupManager.getGroups().forEach(group -> {
            try { groupManager.deleteGroup(group); }
            catch (GroupNotFoundException e) { throw new RuntimeException(e); }
        });
        GroupManager.setInstance(null);
        Arrays.stream(CacheFactory.getAllCaches()).forEach(Map::clear);
    }

    @AfterAll
    public static void afterClass() {
        Arrays.stream(CacheFactory.getAllCaches()).forEach(Map::clear);
        GroupManager.setInstance(null);
    }

    /**
     * Verifies that the cached group count stays correct when a group is created concurrently with a
     * {@code getGroupCount()} call whose provider query is artificially delayed to open the race window.
     */
    @Test
    public void testGroupCountNotStaleDespiteConcurrentGroupCreation() throws Exception
    {
        // Setup test fixture.
        groupManager.createGroup("initial-group-1");
        groupManager.createGroup("initial-group-2");
        groupMetaCache.clear(); // force a fresh provider hit on the next call

        // The hook fires AFTER the provider has computed the count (= 2) but BEFORE GroupManager writes that value into
        // the cache. This is the exact moment Thread-B must intervene.
        final CountDownLatch providerCallDone = new CountDownLatch(1);
        final CountDownLatch allowCacheWrite  = new CountDownLatch(1);
        provider.onGetGroupCount(() -> {
            providerCallDone.countDown(); // "I have the answer, about to return"
            try {
                if (!allowCacheWrite.await(1, TimeUnit.MINUTES)) {
                    throw new InterruptedException("Provider call took too long");
                }
            }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        // Thread-A: slow getGroupCount() – will write count=2 to cache after the latch
        final Thread threadA = new Thread(() -> groupManager.getGroupCount());
        threadA.start();

        assertTrue(providerCallDone.await(5, TimeUnit.SECONDS), "Thread-A did not reach the provider");

        // Thread-B: run mutation concurrently on a separate thread.
        final Thread threadB = new Thread(() -> {
            try {
                groupManager.createGroup("new-group-added-during-race");
            } catch (GroupAlreadyExistsException | GroupNameInvalidException e) {
                throw new RuntimeException(e);
            }
        });
        threadB.start();

        // Give Thread-B a chance to complete first.
        threadB.join(1_000);

        // Let Thread-A write the (possibly stale) count into the cache
        allowCacheWrite.countDown();
        threadA.join(5_000);
        threadB.join(5_000);
        assertFalse(threadA.isAlive(), "Thread-A did not finish");
        assertFalse(threadB.isAlive(), "Thread-B did not finish");

        // Execute system under test.
        final int cachedCount   = groupManager.getGroupCount(); // hits the cache written by Thread-A
        final int providerCount = provider.getGroupCount();

        // Verify result.
        assertEquals(providerCount, cachedCount,"Cached group count (" + cachedCount + ") is stale; provider reports " + providerCount + " groups.");
    }

    /**
     * Verifies that {@code getGroupCount()} and {@code getGroups().size()} always agree under concurrent writes.
     */
    @Test
    public void testGroupCountAndGroupNamesAreConsistentUnderConcurrentWrite() throws Exception
    {
        // Setup test fixture.
        groupManager.createGroup("seed-group-1");
        groupManager.createGroup("seed-group-2");
        groupManager.createGroup("seed-group-3");
        groupMetaCache.clear();

        // Hook fires AFTER the provider has built the complete 3-name list, but BEFORE GroupManager stores it in the cache.
        final CountDownLatch namesComputedByProvider = new CountDownLatch(1);
        final CountDownLatch allowNamesWrite         = new CountDownLatch(1);
        provider.onGetGroupNames(() -> {
            namesComputedByProvider.countDown();
            try {
                if (!allowNamesWrite.await(1, TimeUnit.MINUTES)) {
                    throw new InterruptedException("Provider call took too long");
                }
            }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        // Thread-A: slow getGroups() – will write 3 names to cache after the latch
        final Thread threadA = new Thread(() -> groupManager.getGroups());
        threadA.start();

        assertTrue(namesComputedByProvider.await(5, TimeUnit.SECONDS), "Thread-A did not reach the provider");

        // Thread-B: add a fourth group on a separate thread.
        final Thread threadB = new Thread(() -> {
            try {
                groupManager.createGroup("interloper-group");
            } catch (GroupAlreadyExistsException | GroupNameInvalidException e) {
                throw new RuntimeException(e);
            }
        });
        threadB.start();

        // Try to let Thread-B complete first.
        threadB.join(1_000);

        // Thread-A resumes, writes stale 3-name list into GROUP_NAMES cache.
        allowNamesWrite.countDown();
        threadA.join(5_000);
        threadB.join(5_000);
        assertFalse(threadA.isAlive(), "Thread-A did not finish");
        assertFalse(threadB.isAlive(), "Thread-B did not finish");

        // Execute system under test.
        final int cachedNamesCount = groupManager.getGroups().size();
        final int cachedGroupCount = groupManager.getGroupCount();

        // Verify results – the two cache paths must agree.
        assertEquals(cachedGroupCount, cachedNamesCount, "getGroupCount() returned " + cachedGroupCount + " but getGroups().size() returned " + cachedNamesCount);
    }

    /**
     * Verifies that the per-user group-membership cache is not reinstated with stale data after
     * {@code memberRemovedPostProcess()} has already evicted it.
     */
    @Test
    public void testPerUserGroupCacheNotStaleDespiteConcurrentMemberRemoval() throws Exception
    {
        // Setup test fixture: one group containing a single user
        final JID user = new JID("alice", "example.org", null);
        groupManager.createGroup("membership-group");
        provider.addMember("membership-group", user, false);
        groupMetaCache.clear();

        // Verify the user really is a member before we start the race
        assertFalse(groupManager.getGroups(user).isEmpty(), "Pre-condition: user must be in at least one group");
        groupMetaCache.clear(); // evict so the next call actually queries the provider

        // Hook fires AFTER the provider has computed the membership list (user IS a member) but BEFORE GroupManager writes that list into the per-user cache.
        final CountDownLatch membershipComputedByProvider = new CountDownLatch(1);
        final CountDownLatch allowMembershipWrite         = new CountDownLatch(1);
        provider.onGetGroupNamesForUser(() -> {
            membershipComputedByProvider.countDown();
            try {
                if (!allowMembershipWrite.await(1, TimeUnit.MINUTES)) {
                    throw new InterruptedException("Provider call took too long");
                }
            }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        // Thread-A: slow getGroups(user) – will write [membership-group] to cache after latch
        final Thread threadA = new Thread(() -> groupManager.getGroups(user));
        threadA.start();

        assertTrue(membershipComputedByProvider.await(5, TimeUnit.SECONDS), "Thread-A did not reach the provider");

        // Thread-B: remove user from the provider, then notify GroupManager.
        final Thread threadB = new Thread(() -> {
            try {
                provider.deleteMember("membership-group", user);
                final Group groupAfterRemoval = provider.getGroup("membership-group");
                groupManager.memberRemovedPostProcess(groupAfterRemoval, user);
            } catch (GroupNotFoundException e) {
                throw new RuntimeException(e);
            }
        });
        threadB.start();

        // Attempt to let Thread-B complete first.
        threadB.join(1_000);

        // Thread-A resumes and writes stale membership-group.
        allowMembershipWrite.countDown();
        threadA.join(5_000);
        threadB.join(5_000);
        assertFalse(threadA.isAlive(), "Thread-A did not finish");
        assertFalse(threadB.isAlive(), "Thread-B did not finish");

        // Execute system under test: query again; must return empty because the user was removed.
        final Collection<Group> afterRemoval = groupManager.getGroups(user);

        // Verify result.
        assertTrue(afterRemoval.isEmpty(), "After removing user from all groups, getGroups(user) still returned " + afterRemoval.size() + " group(s).");
    }

    /**
     * Verifies that paginated group-name cache entries are not written back with stale data after
     * {@code evictCachedPaginatedGroupNames()} has already cleared them.
     */
    @Test
    public void testPaginatedGroupNamesCacheNotStaleDespiteConcurrentEviction() throws Exception
    {
        // Setup test fixture
        for (int i = 0; i < 4; i++) {
            groupManager.createGroup("page-group-" + i);
        }
        groupMetaCache.clear();

        // Hook fires AFTER the provider computed the 4-group page, BEFORE GroupManager writes it to the cache.
        final CountDownLatch pageComputedByProvider = new CountDownLatch(1);
        final CountDownLatch allowPageWrite         = new CountDownLatch(1);
        provider.onGetGroupNamesPage(() -> {
            pageComputedByProvider.countDown();
            try {
                if (!allowPageWrite.await(1, TimeUnit.MINUTES)) {
                    throw new InterruptedException("Provider call took too long");
                }
            }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        // Thread-A: slow getGroups(0, 10) – will write 4-name page to cache after latch
        final Thread threadA = new Thread(() -> groupManager.getGroups(0, 10));
        threadA.start();

        assertTrue(pageComputedByProvider.await(5, TimeUnit.SECONDS), "Thread-A did not reach the provider");

        // Thread-B: create a new group concurrently.
        final Thread threadB = new Thread(() -> {
            try {
                groupManager.createGroup("fifth-group");
            } catch (GroupAlreadyExistsException | GroupNameInvalidException e) {
                throw new RuntimeException(e);
            }
        });
        threadB.start();

        // Try to let Thread-B complete first to force stale-page reinstatement.
        threadB.join(1_000);

        // Thread-A resumes and writes the stale 4-group page into the now-empty cache slot.
        allowPageWrite.countDown();
        threadA.join(5_000);
        threadB.join(5_000);
        assertFalse(threadA.isAlive(), "Thread-A did not finish");
        assertFalse(threadB.isAlive(), "Thread-B did not finish");

        // Execute system under test.
        final Collection<Group> cachedPage   = groupManager.getGroups(0, 10);
        final int               providerSize = provider.getGroupCount();

        // Verify result.
        assertEquals(providerSize, cachedPage.size(), "getGroups(0,10) returned " + cachedPage.size() + " groups, but the provider holds " + providerSize + ".");
    }

    /**
     * Verifies that {@link GroupManager} throws no exception and leaves the caches in a coherent state when many
     * threads perform mixed group operations simultaneously.
     */
    @Test
    public void stressTestNoConcurrentExceptionsAndCacheRemainsCoherent() throws Exception
    {
        // Setup test fixture.
        final int threadCount = 17;
        final Duration duration = Duration.ofSeconds(2);
        final int groupCount = 5;
        final JID testUser = new JID("stress-user", "example.org", null);

        for (int i = 0; i < groupCount; i++) {
            groupManager.createGroup("stress-seed-" + i);
            provider.addMember("stress-seed-" + i, testUser, false);
        }

        final AtomicInteger exceptionCount = new AtomicInteger(0);
        final AtomicReference<Throwable> firstError = new AtomicReference<>();
        final AtomicInteger nextGroupId = new AtomicInteger(100);

        final CountDownLatch startGate = new CountDownLatch(1);
        final CountDownLatch stopGate  = new CountDownLatch(threadCount);

        // Execute system under test
        for (int t = 0; t < threadCount; t++)
        {
            final int threadIndex = t;
            final Thread thread = new Thread(() -> {
                try {
                    startGate.await();
                    final Instant deadline = Instant.now().plus(duration);
                    while (Instant.now().isBefore(deadline))
                    {
                        try {
                            switch (threadIndex % 4) {
                                case 0 -> {
                                    final int c = groupManager.getGroupCount();
                                    assertTrue(c >= 0, "Group count must be non-negative");
                                }
                                case 1 -> {
                                    final Collection<Group> groups = groupManager.getGroups();
                                    assertNotNull(groups);
                                }
                                case 2 -> {
                                    final Collection<Group> userGroups = groupManager.getGroups(testUser);
                                    assertNotNull(userGroups);
                                }
                                case 3 -> {
                                    // Create a unique transient group, then immediately delete it.
                                    final String name = "stress-transient-" + nextGroupId.getAndIncrement();
                                    final Group created = groupManager.createGroup(name);
                                    groupManager.deleteGroup(created);
                                }
                            }
                        } catch (Throwable e) {
                            exceptionCount.incrementAndGet();
                            firstError.compareAndSet(null, e);
                        }
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } finally {
                    stopGate.countDown();
                }
            });
            thread.setDaemon(true);
            thread.start();
        }

        startGate.countDown();
        assertTrue(stopGate.await(Duration.ofSeconds(5).plus(duration).toMillis(), TimeUnit.MILLISECONDS), "Not all threads finished in time");

        // Verify result.
        if (firstError.get() != null) {
            fail("At least " + exceptionCount.get()
                + " exception(s) were thrown during concurrent group operations. "
                + "First error: " + firstError.get().getMessage(),
                firstError.get());
        }

        // final cache coherence – count and names-list must agree
        final int finalCount = groupManager.getGroupCount();
        final int finalNamesSize = groupManager.getGroups().size();
        assertEquals(finalCount, finalNamesSize, "After the stress test, getGroupCount() (" + finalCount + ") != getGroups().size() (" + finalNamesSize + ").");
    }

    /**
     * A thread-safe, in-memory {@link GroupProvider} that accepts optional {@link Runnable} hooks executed at the end
     * of specific provider calls.
     *
     * A hook fires <em>after</em> the provider has computed its result but <em>before</em> it returns to GroupManager.
     * This is the exact window in which an intervening modification by Thread-B can leave Thread-A's about-to-be-cached
     * value stale.
     *
     * Each hook is a one-shot: it must be set afresh for each test.
     */
    static class ControllableGroupProvider implements GroupProvider
    {
        private final ConcurrentHashMap<String, String>   descriptions = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Set<JID>> members      = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Set<JID>> admins       = new ConcurrentHashMap<>();

        private volatile Runnable onGetGroupCountHook;
        private volatile Runnable onGetGroupNamesHook;
        private volatile Runnable onGetGroupNamesForUserHook;
        private volatile Runnable onGetGroupNamesPageHook;

        void onGetGroupCount(Runnable hook)        { onGetGroupCountHook        = hook; }
        void onGetGroupNames(Runnable hook)        { onGetGroupNamesHook        = hook; }
        void onGetGroupNamesForUser(Runnable hook) { onGetGroupNamesForUserHook = hook; }
        void onGetGroupNamesPage(Runnable hook)    { onGetGroupNamesPageHook    = hook; }

        @Override
        public Group createGroup(String name) throws GroupAlreadyExistsException
        {
            if (descriptions.putIfAbsent(name, "") != null) {
                throw new GroupAlreadyExistsException();
            }
            members.put(name, ConcurrentHashMap.newKeySet());
            admins.put(name,  ConcurrentHashMap.newKeySet());
            return buildGroup(name);
        }

        @Override
        public void deleteGroup(String name) throws GroupNotFoundException
        {
            if (descriptions.remove(name) == null) {
                throw new GroupNotFoundException();
            }
            members.remove(name);
            admins.remove(name);
        }

        @Override
        public Group getGroup(String name) throws GroupNotFoundException
        {
            if (!descriptions.containsKey(name)) {
                throw new GroupNotFoundException();
            }
            return buildGroup(name);
        }

        private Group buildGroup(String name)
        {
            return new Group(
                name,
                descriptions.getOrDefault(name, ""),
                new ArrayList<>(members.getOrDefault(name, Collections.emptySet())),
                new ArrayList<>(admins.getOrDefault(name,  Collections.emptySet())));
        }

        @Override
        public void setName(String oldName, String newName) throws GroupAlreadyExistsException, GroupNameInvalidException, GroupNotFoundException
        {
            if (!descriptions.containsKey(oldName)) {
                throw new GroupNotFoundException();
            }
            if ( descriptions.containsKey(newName)) {
                throw new GroupAlreadyExistsException();
            }
            descriptions.put(newName, descriptions.remove(oldName));
            members.put(newName, members.remove(oldName));
            admins.put(newName,  admins.remove(oldName));
        }

        @Override
        public void setDescription(String name, String description) throws GroupNotFoundException
        {
            if (!descriptions.containsKey(name)) {
                throw new GroupNotFoundException();
            }
            descriptions.put(name, description);
        }

        @Override
        public int getGroupCount()
        {
            final int count = descriptions.size();

            if (onGetGroupCountHook != null) {
                // Hook fires AFTER computing the count, BEFORE returning to GroupManager.
                onGetGroupCountHook.run();
                onGetGroupCountHook = null;
            }

            return count;
        }

        @Override
        public Collection<String> getGroupNames()
        {
            final List<String> names = new ArrayList<>(descriptions.keySet());

            if (onGetGroupNamesHook != null) {
                try {
                    // Hook fires AFTER computing the list, BEFORE returning to GroupManager.
                    onGetGroupNamesHook.run();
                } finally {
                    onGetGroupNamesHook = null;
                }
            }

            return names;
        }

        @Override
        public Collection<String> getGroupNames(int startIndex, int numResults)
        {
            final List<String> page = descriptions.keySet().stream()
                .skip(startIndex)
                .limit(numResults)
                .collect(Collectors.toList());

            if (onGetGroupNamesPageHook != null) {
                try {
                    // Hook fires AFTER computing the page, BEFORE returning to GroupManager.
                    onGetGroupNamesPageHook.run();
                } finally {
                    onGetGroupNamesPageHook = null;
                }
            }

            return page;
        }

        @Override
        public Collection<String> getGroupNames(JID user)
        {
            final JID bare = user.asBareJID();
            final Set<String> result = new HashSet<>();
            members.forEach((group, jids) -> { if (jids.contains(bare)) result.add(group); });
            admins.forEach( (group, jids) -> { if (jids.contains(bare)) result.add(group); });

            if (onGetGroupNamesForUserHook != null) {
                try {
                    // Hook fires AFTER computing the membership set, BEFORE returning to GroupManager.
                    onGetGroupNamesForUserHook.run();
                } finally {
                    onGetGroupNamesForUserHook = null;
                }
            }

            return result;
        }

        @Override
        public void addMember(String groupName, JID user, boolean administrator) throws GroupNotFoundException
        {
            if (!descriptions.containsKey(groupName)) {
                throw new GroupNotFoundException();
            }

            final ConcurrentMap<String, Set<JID>> users = administrator ? admins : members;
            users
                .computeIfAbsent(groupName, k -> ConcurrentHashMap.newKeySet())
                .add(user.asBareJID());
        }

        @Override
        public void updateMember(String groupName, JID user, boolean administrator) throws GroupNotFoundException
        {
            if (!descriptions.containsKey(groupName)) {
                throw new GroupNotFoundException();
            }

            final JID bare = user.asBareJID();
            if (administrator) {
                members.getOrDefault(groupName, Collections.emptySet()).remove(bare);
                admins.computeIfAbsent(groupName, k -> ConcurrentHashMap.newKeySet()).add(bare);
            } else {
                admins.getOrDefault(groupName, Collections.emptySet()).remove(bare);
                members.computeIfAbsent(groupName, k -> ConcurrentHashMap.newKeySet()).add(bare);
            }
        }

        @Override
        public void deleteMember(String groupName, JID user)
        {
            final JID bare = user.asBareJID();
            members.getOrDefault(groupName, Collections.emptySet()).remove(bare);
            admins.getOrDefault( groupName, Collections.emptySet()).remove(bare);
        }

        @Override public boolean            isReadOnly()                           { return false; }
        @Override public boolean            isSharingSupported()                   { return false; }
        @Override public Collection<String> getSharedGroupNames()                  { return Collections.emptyList(); }
        @Override public Collection<String> getSharedGroupNames(JID user)          { return Collections.emptyList(); }
        @Override public Collection<String> getPublicSharedGroupNames()            { return Collections.emptyList(); }
        @Override public Collection<String> getVisibleGroupNames(String userGroup) { return Collections.emptyList(); }
        @Override public Collection<String> search(String query)                   { return Collections.emptyList(); }
        @Override public Collection<String> search(String q, int s, int n)         { return Collections.emptyList(); }
        @Override public Collection<String> search(String key, String value)       { return Collections.emptyList(); }
        @Override public boolean            isSearchSupported()                    { return false; }

        @Override
        public PersistableMap<String, String> loadProperties(Group group)
        {
            return new PersistableMap<>() {
                @Override
                public String put(String key, String value, boolean persist) {
                    // Tests do not exercise persistent property storage; delegate to the backing HashMap so in-memory
                    // writes (e.g. shareWithNobody) succeed without a real database.
                    return super.put(key, value);
                }
            };
        }
    }
}



