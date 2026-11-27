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

package org.jivesoftware.openfire.lockout;

import org.jivesoftware.Fixtures;
import org.jivesoftware.util.CacheableOptional;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.DefaultLocalCacheStrategy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the caching in {@link LockOutManager}: that it remembers an account is not locked as well as that it
 * is, and that setting or lifting a lock replaces what it remembers.
 *
 * @author Matthew Vivian
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class LockOutManagerTest
{
    private static final String USER = "alice";

    @Mock
    private LockOutProvider provider;

    private Cache<String, CacheableOptional<LockOutFlag>> lockOuts;
    private LockOutManager manager;

    /**
     * What the provider stores, by username, so that a lookup returns the last lock set or lifted through the provider.
     */
    private Map<String, LockOutFlag> stored;

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    public void setUp()
    {
        stored = new ConcurrentHashMap<>();
        when(provider.isReadOnly()).thenReturn(false);
        when(provider.shouldNotBeCached()).thenReturn(false);
        when(provider.getDisabledStatus(anyString())).thenAnswer(invocation -> stored.get(invocation.<String>getArgument(0)));
        doAnswer(invocation -> { final LockOutFlag flag = invocation.getArgument(0); stored.put(flag.getUsername(), flag); return null; }).when(provider).setDisabledStatus(any());
        doAnswer(invocation -> { stored.remove(invocation.<String>getArgument(0)); return null; }).when(provider).unsetDisabledStatus(anyString());

        final DefaultLocalCacheStrategy caches = new DefaultLocalCacheStrategy();
        lockOuts = caches.createCache("test locked out accounts");
        manager = new LockOutManager(lockOuts, () -> provider);
    }

    /**
     * Verifies that a second lookup of an account that is not locked is answered from the cache, without querying the
     * provider again.
     */
    @Test
    public void testNotLockedIsCached()
    {
        // Act
        final boolean first = manager.isAccountDisabled(USER);
        final boolean second = manager.isAccountDisabled(USER);

        // Assert
        assertFalse(first, "an account with no lock must not be disabled");
        assertFalse(second, "an account with no lock must still not be disabled when looked up again");
        verify(provider, times(1).description("the second lookup of an account that is not locked must be answered from the cache")).getDisabledStatus(USER);
    }

    /**
     * Verifies that an account's state is one cache entry, whether it is locked or not, and that setting and lifting a
     * lock replace that entry.
     */
    @Test
    public void testAccountStateIsOneCacheEntry()
    {
        // Act and assert
        manager.isAccountDisabled(USER);
        assertEquals(1, lockOuts.size(), "an account found not locked must be one cache entry");
        assertTrue(lockOuts.get(USER).isAbsent(), "an account found not locked must be cached as having no lock");

        manager.disableAccount(USER, null, null);
        assertEquals(1, lockOuts.size(), "setting a lock must replace the account's entry rather than add one");
        assertEquals(USER, lockOuts.get(USER).get().getUsername(), "a locked account must be cached with its lock");

        manager.enableAccount(USER);
        assertEquals(1, lockOuts.size(), "lifting a lock must replace the account's entry rather than remove it");
        assertTrue(lockOuts.get(USER).isAbsent(), "an account whose lock is lifted must be cached as having no lock");
    }

    /**
     * Verifies that a lock set after the account was found not locked replaces that knowledge, so that the next lookup
     * returns the lock.
     */
    @Test
    public void testDisableAccountAfterNotLockedLookup()
    {
        // Arrange
        assertFalse(manager.isAccountDisabled(USER), "the account must start without a lock");

        // Act
        manager.disableAccount(USER, null, null);

        // Assert
        assertTrue(manager.isAccountDisabled(USER), "a lock set after the account was found not locked must be seen by the next lookup");
        assertNotNull(manager.getDisabledStatus(USER), "the lock must be returned after the account was found not locked");
    }

    /**
     * Verifies that lifting a lock is seen by the next lookup.
     */
    @Test
    public void testEnableAccountReportsNotLocked()
    {
        // Arrange
        manager.disableAccount(USER, null, null);
        assertTrue(manager.isAccountDisabled(USER), "the account must start locked");

        // Act
        manager.enableAccount(USER);

        // Assert
        assertFalse(manager.isAccountDisabled(USER), "an account must not be disabled once its lock is lifted");
        assertNull(manager.getDisabledStatus(USER), "no lock must be returned once it is lifted");
    }

    /**
     * Verifies that a lock that has ended is removed, and that the account is then remembered as not locked.
     */
    @Test
    public void testExpiredLockIsRemovedAndNotLockedIsCached()
    {
        // Arrange
        final Date past = new Date(System.currentTimeMillis() - Duration.ofMinutes(1).toMillis());
        stored.put(USER, new LockOutFlag(USER, null, past));

        // Act
        final boolean first = manager.isAccountDisabled(USER);
        final boolean second = manager.isAccountDisabled(USER);

        // Assert
        assertFalse(first, "an account whose lock has ended must not be disabled");
        assertFalse(second, "an account whose lock has ended must still not be disabled when looked up again");
        verify(provider, times(1).description("a lock that has ended must be removed from the provider")).unsetDisabledStatus(USER);
        verify(provider, times(1).description("the account must be remembered as not locked once its ended lock is removed")).getDisabledStatus(USER);
    }

    /**
     * Verifies that a username that cannot name an account is refused rather than queried and cached as not locked.
     */
    @Test
    public void testInvalidUsernameIsRefused()
    {
        assertThrows(IllegalArgumentException.class, () -> manager.isAccountDisabled("alice@example.org"), "a username that is not a valid node must be refused");
        assertTrue(lockOuts.isEmpty(), "a username that is not a valid node must not be cached");
    }

    /**
     * Verifies that a provider whose data must not be cached is queried on every lookup, whether or not the account is
     * locked.
     */
    @Test
    public void testUncachedProviderIsQueriedEveryTime()
    {
        // Arrange
        when(provider.shouldNotBeCached()).thenReturn(true);

        // Act
        manager.isAccountDisabled(USER);
        final boolean notLocked = manager.isAccountDisabled(USER);
        manager.disableAccount(USER, null, null);
        final boolean locked = manager.isAccountDisabled(USER);
        manager.isAccountDisabled(USER);

        // Assert
        assertFalse(notLocked, "an account with no lock must not be disabled");
        assertTrue(locked, "a locked account must be disabled");
        verify(provider, times(4).description("a provider whose data must not be cached must be queried on every lookup")).getDisabledStatus(USER);
        assertTrue(lockOuts.isEmpty(), "nothing must be cached for a provider whose data must not be cached");
    }

    /**
     * Verifies that a lookup answered from the cache, either way, does not wait for the lock that guards loading and
     * changing an account's entry.
     */
    @Test
    public void testCachedLookupTakesNoLock() throws Exception
    {
        // Arrange
        final String lockedUser = "bob";
        manager.disableAccount(lockedUser, null, null);
        manager.isAccountDisabled(USER);
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final CountDownLatch held = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        try {
            final Future<?> holder = executor.submit(() -> {
                final Lock userLock = lockOuts.getLock(USER);
                final Lock lockedUserLock = lockOuts.getLock(lockedUser);
                userLock.lock();
                lockedUserLock.lock();
                try {
                    held.countDown();
                    release.await();
                } finally {
                    lockedUserLock.unlock();
                    userLock.unlock();
                }
                return null;
            });
            assertTrue(held.await(5, TimeUnit.SECONDS), "the locks must be taken by the other thread");

            // Act and assert
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                assertFalse(manager.isAccountDisabled(USER), "a cached not-locked account must be answered while its lock is held");
                assertTrue(manager.isAccountDisabled(lockedUser), "a cached lock must be answered while the account's lock is held");
            });
            release.countDown();
            holder.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    /**
     * Verifies that a lookup that has to query the provider waits while a lock is being set for the account, so that
     * it cannot record the account as not locked from a read made before the lock was stored.
     */
    @Test
    public void testLookupWaitsForLockBeingSet() throws Exception
    {
        // Arrange: the provider pauses while storing the lock.
        final CountDownLatch writing = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            writing.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS), "the test must release the provider write");
            final LockOutFlag flag = invocation.getArgument(0);
            stored.put(flag.getUsername(), flag);
            return null;
        }).when(provider).setDisabledStatus(any());
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final Future<?> disabling = executor.submit(() -> manager.disableAccount(USER, null, null));
            assertTrue(writing.await(5, TimeUnit.SECONDS), "the lock must be being stored");

            // Act
            final Future<Boolean> lookup = executor.submit(() -> manager.isAccountDisabled(USER));
            Thread.sleep(200);
            final boolean finishedDuringWrite = lookup.isDone();
            release.countDown();
            disabling.get(5, TimeUnit.SECONDS);

            // Assert
            assertFalse(finishedDuringWrite, "a lookup that queries the provider must wait while a lock is being set for the account");
            assertTrue(lookup.get(5, TimeUnit.SECONDS), "the lookup must return the lock set while it waited");
            assertTrue(manager.isAccountDisabled(USER), "the account must not be remembered as not locked once the lock is set");
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
