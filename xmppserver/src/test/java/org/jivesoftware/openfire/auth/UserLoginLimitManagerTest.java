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

package org.jivesoftware.openfire.auth;

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.lockout.LockOutFlag;
import org.jivesoftware.openfire.lockout.LockOutManager;
import org.jivesoftware.openfire.lockout.LockOutProvider;
import org.jivesoftware.openfire.security.SecurityAuditManager;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.DefaultLocalCacheStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UserLoginLimitManager}.
 *
 * Each test sets the limit of the tier under test and turns the other tiers off (a limit of 0), unless it is about how
 * tiers interact.
 *
 * @author Matthew Vivian
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class UserLoginLimitManagerTest
{
    private static final Duration PAIR_INTERVAL = Duration.ofMinutes(15);
    private static final Duration ADDRESS_INTERVAL = Duration.ofMinutes(20);
    private static final Duration USERNAME_INTERVAL = Duration.ofHours(1);
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(30);
    private static final Duration PAIR_BLOCK_DURATION = Duration.ofMinutes(10);
    private static final Duration ADDRESS_BLOCK_DURATION = Duration.ofMinutes(25);

    private static final String USER = "alice";
    private static final String OTHER_USER = "bob";
    private static final String ADDRESS = "192.0.2.1";
    private static final String OTHER_ADDRESS = "192.0.2.2";
    private static final String THIRD_ADDRESS = "192.0.2.3";
    private static final String XMPP_DOMAIN = "example.org";

    /**
     * Stands in for the key of a listed block where a test compares blocks without their keys.
     */
    private static final String NO_KEY = "";

    private UserLoginLimitManager manager;
    private MutableClock clock;
    private Set<String> existingUsers;
    private Set<String> administrators;

    private Cache<String, UserLoginLimitManager.FailureWindow> pairFailures;
    private Cache<String, UserLoginLimitManager.FailureWindow> addressFailures;
    private Cache<String, UserLoginLimitManager.FailureWindow> usernameFailures;
    private Cache<String, UserLoginLimitManager.FailureWindow> unknownPairFailures;
    private Cache<String, UserLoginLimitManager.FailureWindow> unknownUsernameFailures;
    private Cache<String, UserLoginLimitManager.AddressBlock> pairBlocks;
    private Cache<String, UserLoginLimitManager.AddressBlock> addressBlocks;
    private Cache<String, UserLoginLimitManager.UsernameBlock> usernameBlocks;

    private boolean originalEnabled;
    private long originalMaxPair;
    private long originalMaxAddress;
    private long originalMaxUsername;
    private Duration originalPairInterval;
    private Duration originalAddressInterval;
    private Duration originalUsernameInterval;
    private Duration originalLockoutDuration;
    private Duration originalPairBlockDuration;
    private Duration originalAddressBlockDuration;

    @Mock
    private LockOutManager lockOutManager;

    @Mock
    private LockOutProvider lockOutProvider;

    @Mock
    private SecurityAuditManager securityAuditManager;

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    public void setUp()
    {
        originalEnabled = UserLoginLimitManager.ENABLED.getValue();
        originalMaxPair = UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.getValue();
        originalMaxAddress = UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.getValue();
        originalMaxUsername = UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.getValue();
        originalPairInterval = UserLoginLimitManager.PER_PAIR_INTERVAL.getValue();
        originalAddressInterval = UserLoginLimitManager.PER_ADDRESS_INTERVAL.getValue();
        originalUsernameInterval = UserLoginLimitManager.PER_USERNAME_INTERVAL.getValue();
        originalLockoutDuration = UserLoginLimitManager.LOCKOUT_DURATION.getValue();
        originalPairBlockDuration = UserLoginLimitManager.PER_PAIR_BLOCK_DURATION.getValue();
        originalAddressBlockDuration = UserLoginLimitManager.PER_ADDRESS_BLOCK_DURATION.getValue();

        UserLoginLimitManager.ENABLED.setValue(true);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(0L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(0L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(0L);
        UserLoginLimitManager.PER_PAIR_INTERVAL.setValue(PAIR_INTERVAL);
        UserLoginLimitManager.PER_ADDRESS_INTERVAL.setValue(ADDRESS_INTERVAL);
        UserLoginLimitManager.PER_USERNAME_INTERVAL.setValue(USERNAME_INTERVAL);
        UserLoginLimitManager.LOCKOUT_DURATION.setValue(LOCKOUT_DURATION);
        UserLoginLimitManager.PER_PAIR_BLOCK_DURATION.setValue(PAIR_BLOCK_DURATION);
        UserLoginLimitManager.PER_ADDRESS_BLOCK_DURATION.setValue(ADDRESS_BLOCK_DURATION);

        when(lockOutProvider.isReadOnly()).thenReturn(false);
        when(lockOutProvider.isTimeoutSupported()).thenReturn(true);

        final DefaultLocalCacheStrategy caches = new DefaultLocalCacheStrategy();
        pairFailures = caches.createCache("test pair failures");
        addressFailures = caches.createCache("test address failures");
        usernameFailures = caches.createCache("test username failures");
        unknownPairFailures = caches.createCache("test unknown pair failures");
        unknownUsernameFailures = caches.createCache("test unknown username failures");
        pairBlocks = caches.createCache("test pair blocks");
        addressBlocks = caches.createCache("test address blocks");
        usernameBlocks = caches.createCache("test username blocks");

        clock = new MutableClock(Instant.parse("2026-11-10T09:00:00Z"));
        existingUsers = new HashSet<>();
        administrators = new HashSet<>();
        existingUsers.add(USER);
        existingUsers.add(OTHER_USER);

        manager = new UserLoginLimitManager(pairFailures, addressFailures, usernameFailures, unknownPairFailures, unknownUsernameFailures, pairBlocks, addressBlocks, usernameBlocks,
            () -> lockOutManager, () -> lockOutProvider, existingUsers::contains, administrators::contains, () -> securityAuditManager, () -> XMPP_DOMAIN, clock);
    }

    @AfterEach
    public void tearDown()
    {
        UserLoginLimitManager.ENABLED.setValue(originalEnabled);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(originalMaxPair);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(originalMaxAddress);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(originalMaxUsername);
        UserLoginLimitManager.PER_PAIR_INTERVAL.setValue(originalPairInterval);
        UserLoginLimitManager.PER_ADDRESS_INTERVAL.setValue(originalAddressInterval);
        UserLoginLimitManager.PER_USERNAME_INTERVAL.setValue(originalUsernameInterval);
        UserLoginLimitManager.LOCKOUT_DURATION.setValue(originalLockoutDuration);
        UserLoginLimitManager.PER_PAIR_BLOCK_DURATION.setValue(originalPairBlockDuration);
        UserLoginLimitManager.PER_ADDRESS_BLOCK_DURATION.setValue(originalAddressBlockDuration);
    }

    /**
     * Makes attempts that end without success: each reserves an attempt (or is refused), and none is refunded.
     */
    private void fail(final String username, final String address, final int times)
    {
        for (int i = 0; i < times; i++) {
            manager.tryAcquireAttempt(username, address);
        }
    }

    /**
     * Makes an attempt that succeeds: it reserves an attempt and then records the success.
     */
    private void succeed(final String username, final String address)
    {
        final UserLoginLimitManager.Reservation reservation = manager.tryAcquireAttempt(username, address);
        assertNotNull(reservation, "a successful attempt must first be admitted");
        manager.recordSuccessfulLogin(reservation);
    }

    /**
     * Verifies the defaults the plan specifies: the feature is on, with only tier 1 active, as it blocks no one but the
     * pair that reached it; tiers 2 and 3 are off until an administrator who knows their network turns them on.
     */
    @Test
    public void testDefaults()
    {
        assertTrue(UserLoginLimitManager.ENABLED.getDefaultValue(), "the login limits must be on by default");
        assertEquals(10L, UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.getDefaultValue(), "tier 1 must allow 10 failures by default");
        assertEquals(Duration.ofMinutes(15), UserLoginLimitManager.PER_PAIR_INTERVAL.getDefaultValue(), "tier 1 must count over 15 minutes by default");
        assertEquals(Duration.ofMinutes(15), UserLoginLimitManager.PER_PAIR_BLOCK_DURATION.getDefaultValue(), "tier 1 must block for 15 minutes by default");
        assertEquals(0L, UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.getDefaultValue(), "tier 2 must be off by default");
        assertEquals(Duration.ofMinutes(15), UserLoginLimitManager.PER_ADDRESS_INTERVAL.getDefaultValue(), "tier 2 must count over 15 minutes by default");
        assertEquals(Duration.ofMinutes(15), UserLoginLimitManager.PER_ADDRESS_BLOCK_DURATION.getDefaultValue(), "tier 2 must block for 15 minutes by default");
        assertEquals(0L, UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.getDefaultValue(), "tier 3 must be off by default");
        assertEquals(Duration.ofHours(1), UserLoginLimitManager.PER_USERNAME_INTERVAL.getDefaultValue(), "tier 3 must count over 1 hour by default");
        assertEquals(Duration.ofMinutes(30), UserLoginLimitManager.LOCKOUT_DURATION.getDefaultValue(), "tier 3 must lock for 30 minutes by default");
    }

    /**
     * Verifies that with the feature disabled, failures are not recorded, nothing is blocked and no account is locked.
     */
    @Test
    public void testDisabledRecordsAndBlocksNothing()
    {
        // Setup test fixture.
        UserLoginLimitManager.ENABLED.setValue(false);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(1L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(1L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(1L);

        // Execute system under test.
        fail(USER, ADDRESS, 5);

        // Verify result.
        assertTrue(manager.isLoginAllowed(USER, ADDRESS), "a disabled manager must not block a login");
        assertTrue(pairFailures.isEmpty(), "a disabled manager must not record pair failures");
        assertTrue(addressFailures.isEmpty(), "a disabled manager must not record address failures");
        assertTrue(usernameFailures.isEmpty(), "a disabled manager must not record username failures");
        assertTrue(usernameBlocks.isEmpty(), "a disabled manager must not block a username");
        verify(lockOutManager, never().description("a disabled manager must not lock an account")).disableAccount(anyString(), any(), any());
        verify(securityAuditManager, never().description("a disabled manager must not write audit entries")).logEvent(anyString(), anyString(), anyString());
    }

    /**
     * Verifies that disabling the feature lifts blocks that applied while it was enabled.
     */
    @Test
    public void testDisablingLiftsBlocks()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);
        fail(USER, ADDRESS, 2);

        // Execute system under test.
        UserLoginLimitManager.ENABLED.setValue(false);

        // Verify result.
        assertTrue(manager.isLoginAllowed(USER, ADDRESS), "a disabled manager must not block a login, even one blocked while it was enabled");
    }

    /**
     * Verifies that a block lifted by disabling the feature comes back when the feature is enabled again before the
     * block would have ended, and still ends when it would have.
     */
    @Test
    public void testReenablingRestoresBlockUntilItsOriginalEnd()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);
        fail(USER, ADDRESS, 2);
        UserLoginLimitManager.ENABLED.setValue(false);
        clock.advance(PAIR_BLOCK_DURATION.minusMinutes(1));

        // Execute system under test.
        UserLoginLimitManager.ENABLED.setValue(true);

        // Verify result.
        assertFalse(manager.isLoginAllowed(USER, ADDRESS), "a block that has not ended must apply again once the feature is enabled again");
        clock.advance(Duration.ofMinutes(1).minusSeconds(1));
        assertFalse(manager.isLoginAllowed(USER, ADDRESS), "the block must apply until its original end");
        clock.advance(Duration.ofSeconds(1));
        assertTrue(manager.isLoginAllowed(USER, ADDRESS), "the block must end at its original end, the block duration after the limit was reached");
    }

    /**
     * Verifies that tier 1 allows N failures for a username from an address, and blocks the attempt after those.
     */
    @Test
    public void testPairBlocksAfterLimitAndNotBefore()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(3L);

        // Execute system under test & verify result.
        fail(USER, ADDRESS, 2);
        assertTrue(manager.isLoginAllowed(USER, ADDRESS), "two failures must not reach a pair limit of three");
        fail(USER, ADDRESS, 1);
        assertFalse(manager.isLoginAllowed(USER, ADDRESS), "the attempt after three failures must be refused for a pair limit of three");
    }

    /**
     * Verifies that a tier 1 block applies only to its (username, address) pair.
     */
    @Test
    public void testPairBlockIsScopedToThePair()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(3L);

        // Execute system under test.
        fail(USER, ADDRESS, 3);

        // Verify result.
        assertFalse(manager.isLoginAllowed(USER, ADDRESS), "the blocked pair must be refused");
        assertTrue(manager.isLoginAllowed(USER, OTHER_ADDRESS), "a pair block must not block the same user from another address");
        assertTrue(manager.isLoginAllowed(OTHER_USER, ADDRESS), "a pair block must not block another user from the same address");
        verify(lockOutManager, never().description("tier 1 must never lock an account")).disableAccount(anyString(), any(), any());
    }

    /**
     * Verifies that tier 2 counts failures from an address across usernames, and blocks only that address.
     */
    @Test
    public void testAddressBlocksAfterLimitAcrossUsernames()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(3L);

        // Execute system under test & verify result.
        fail("user1", ADDRESS, 1);
        fail("user2", ADDRESS, 1);
        assertTrue(manager.isLoginAllowed("user4", ADDRESS), "two failures must not reach an address limit of three");
        fail("user3", ADDRESS, 1);
        assertFalse(manager.isLoginAllowed("user4", ADDRESS), "after three failures from an address, any username from that address must be refused");
        assertTrue(manager.isLoginAllowed("user4", OTHER_ADDRESS), "an address block must not block other addresses");
        verify(lockOutManager, never().description("tier 2 must never lock an account")).disableAccount(anyString(), any(), any());
    }

    /**
     * Verifies that tier 3 admits N attempts for a username from any addresses, and that the attempt after those is
     * refused and locks the existing, unlocked account through LockOutManager for the lockout duration. The lock is
     * not set while the Nth attempt is still being evaluated, so that the lock check after a successful exchange cannot
     * reveal whether that attempt's password was right.
     */
    @Test
    public void testUsernameLimitLocksExistingAccount()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);

        // Execute system under test & verify result.
        assertNotNull(manager.tryAcquireAttempt(USER, ADDRESS), "the first attempt must be admitted");
        assertNotNull(manager.tryAcquireAttempt(USER, OTHER_ADDRESS), "the second attempt must be admitted");
        assertNotNull(manager.tryAcquireAttempt(USER, "192.0.2.3"), "the third attempt must be admitted");
        verify(lockOutManager, never().description("the account must not be locked while the Nth attempt is evaluated")).disableAccount(anyString(), any(), any());
        assertNull(manager.tryAcquireAttempt(USER, "192.0.2.4"), "the attempt after three must be refused");

        final ArgumentCaptor<Date> start = ArgumentCaptor.forClass(Date.class);
        final ArgumentCaptor<Date> end = ArgumentCaptor.forClass(Date.class);
        verify(lockOutManager, times(1).description("the attempt after the limit must lock the account")).disableAccount(eq(USER), start.capture(), end.capture());
        assertEquals(clock.instant(), start.getValue().toInstant(), "the lock must start now");
        assertEquals(clock.instant().plus(LOCKOUT_DURATION), end.getValue().toInstant(), "the lock must end after the lockout duration");
    }

    /**
     * Verifies that tier 3 leaves an existing lock (for example one set by an administrator) alone, and refuses the
     * username itself instead.
     */
    @Test
    public void testUsernameLimitLeavesExistingLockAlone()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);
        when(lockOutManager.getDisabledStatus(USER)).thenReturn(new LockOutFlag(USER, null, null));

        // Execute system under test.
        fail(USER, ADDRESS, 4);

        // Verify result.
        verify(lockOutManager, never().description("an existing lock must not be replaced")).disableAccount(anyString(), any(), any());
        assertFalse(manager.isLoginAllowed(USER, OTHER_ADDRESS), "the username must be refused from any address");
    }

    /**
     * Verifies that tier 3 blocks the username in the cache when no account exists, so the answer is the same as for
     * an existing account.
     */
    @Test
    public void testUsernameLimitBlocksUnknownUserInCache()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);

        // Execute system under test.
        fail("nobody", ADDRESS, 4);

        // Verify result.
        verify(lockOutManager, never().description("an account that does not exist must not be locked")).disableAccount(anyString(), any(), any());
        assertFalse(manager.isLoginAllowed("nobody", OTHER_ADDRESS), "the unknown username must be refused from any address");
        assertTrue(manager.isLoginAllowed(USER, ADDRESS), "a username block must not block other usernames");
    }

    /**
     * Verifies that tier 3 blocks the username in the cache when the lockout provider is read-only.
     */
    @Test
    public void testUsernameLimitBlocksInCacheWhenProviderIsReadOnly()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);
        when(lockOutProvider.isReadOnly()).thenReturn(true);

        // Execute system under test.
        fail(USER, ADDRESS, 4);

        // Verify result.
        verify(lockOutManager, never().description("a read-only provider must not be asked to lock")).disableAccount(anyString(), any(), any());
        assertFalse(manager.isLoginAllowed(USER, OTHER_ADDRESS), "the username must be refused from any address");
    }

    /**
     * Verifies that tier 3 blocks the username in the cache when the lockout provider cannot end a lock, because a lock
     * through such a provider would last until an administrator lifts it.
     */
    @Test
    public void testUsernameLimitBlocksInCacheWhenProviderCannotTimeOut()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);
        when(lockOutProvider.isTimeoutSupported()).thenReturn(false);

        // Execute system under test.
        fail(USER, ADDRESS, 4);

        // Verify result.
        verify(lockOutManager, never().description("a provider without timeouts must not be asked to lock")).disableAccount(anyString(), any(), any());
        assertFalse(manager.isLoginAllowed(USER, OTHER_ADDRESS), "the username must be refused from any address");
    }

    /**
     * Verifies that tier 3 still refuses the username when locking the account fails.
     */
    @Test
    public void testUsernameLimitBlocksInCacheWhenLockingFails()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);
        doThrow(new IllegalStateException("database unavailable")).when(lockOutManager).disableAccount(anyString(), any(), any());

        // Execute system under test.
        assertDoesNotThrow(() -> fail(USER, ADDRESS, 4), "a failure to lock must not escape to the caller");

        // Verify result.
        assertFalse(manager.isLoginAllowed(USER, OTHER_ADDRESS), "the username must be refused when the lock could not be set");
    }

    /**
     * Verifies that a tier 3 limit of 0 turns that tier off for its cache blocks too, as a limit of 0 does for tiers 1
     * and 2: a block in force is neither applied nor listed while the limit is 0, and applies again, until it ends, once
     * the limit is set again. (A lock set through LockOutManager is not affected; this username has no account.)
     */
    @Test
    public void testUsernameLimitOfZeroTurnsOffUsernameBlocks()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);
        fail("nobody", ADDRESS, 4);
        assertFalse(manager.isLoginAllowed("nobody", OTHER_ADDRESS), "the username must be blocked before its limit is set to 0");

        // Execute system under test.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(0L);

        // Verify result.
        assertTrue(manager.isLoginAllowed("nobody", OTHER_ADDRESS), "a username block must not apply while its tier is off");
        assertTrue(activeBlocks().isEmpty(), "a username block must not be listed while its tier is off");
        assertNotNull(manager.tryAcquireAttempt("nobody", OTHER_ADDRESS), "an attempt must be admitted while the tier is off");
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);
        assertFalse(manager.isLoginAllowed("nobody", OTHER_ADDRESS), "a block that has not ended must apply again once the tier is on again");
    }

    /**
     * Verifies that a tier 3 cache block ends after the lockout duration.
     */
    @Test
    public void testUsernameBlockEndsAfterLockoutDuration()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);
        fail("nobody", ADDRESS, 4);

        // Execute system under test & verify result.
        clock.advance(LOCKOUT_DURATION.minusSeconds(1));
        assertFalse(manager.isLoginAllowed("nobody", ADDRESS), "the username must be refused until the lockout duration ends");
        clock.advance(Duration.ofSeconds(1));
        assertTrue(manager.isLoginAllowed("nobody", ADDRESS), "the username must be allowed once the lockout duration ends");
    }

    /**
     * Verifies that reaching the tier 3 limit starts a new count, so that once the lock ends a further N attempts are
     * admitted before the account is locked again.
     */
    @Test
    public void testUsernameLimitRestartsCountAfterLocking()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);
        fail(USER, ADDRESS, 4);
        clock.advance(LOCKOUT_DURATION);

        // Execute system under test & verify result.
        fail(USER, ADDRESS, 3);
        verify(lockOutManager, times(1).description("three attempts after the lock ends must not lock the account again")).disableAccount(eq(USER), any(), any());
        fail(USER, ADDRESS, 1);
        verify(lockOutManager, times(2).description("the attempt after three more must lock the account again")).disableAccount(eq(USER), any(), any());
    }

    /**
     * Verifies that a tier 1 block reached quickly lasts for the block duration from the attempt that reached the
     * limit, and that counting then starts again from zero, although the window in which the limit was reached has not
     * ended (the block duration is shorter than the window here).
     */
    @Test
    public void testFastPairGuesserIsBlockedForTheBlockDurationThenStartsFresh()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(3L);
        fail(USER, ADDRESS, 3);

        // Execute system under test & verify result.
        clock.advance(PAIR_BLOCK_DURATION.minusSeconds(1));
        assertFalse(manager.isLoginAllowed(USER, ADDRESS), "the pair must be refused until the block duration ends");
        clock.advance(Duration.ofSeconds(1));
        assertTrue(manager.isLoginAllowed(USER, ADDRESS), "the pair must be allowed once the block duration ends, before its window would have ended");

        fail(USER, ADDRESS, 2);
        assertTrue(manager.isLoginAllowed(USER, ADDRESS), "after a block, counting must start again from zero");
        fail(USER, ADDRESS, 1);
        assertFalse(manager.isLoginAllowed(USER, ADDRESS), "three failures after a block must reach a limit of three");
    }

    /**
     * Verifies that a tier 1 limit reached just before its window ends still blocks for the whole block duration,
     * measured from the attempt that reached it.
     */
    @Test
    public void testSlowPairGuesserIsBlockedForTheBlockDuration()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(3L);
        fail(USER, ADDRESS, 2);
        clock.advance(PAIR_INTERVAL.minusMinutes(1));
        final Instant reached = clock.instant();
        fail(USER, ADDRESS, 1);

        // Execute system under test & verify result.
        clock.advance(Duration.ofMinutes(1));
        assertFalse(manager.isLoginAllowed(USER, ADDRESS), "the block must not end with the window in which the limit was reached");
        clock.advance(PAIR_BLOCK_DURATION.minusMinutes(1).minusSeconds(1));
        assertFalse(manager.isLoginAllowed(USER, ADDRESS), "the pair must be refused until the block duration has passed since the limit was reached");
        assertEquals(reached.plus(PAIR_BLOCK_DURATION), activeBlocks().get(0).endsAt(), "the block must end the block duration after the limit was reached");
        clock.advance(Duration.ofSeconds(1));
        assertTrue(manager.isLoginAllowed(USER, ADDRESS), "the pair must be allowed once the block duration has passed");
    }

    /**
     * Verifies that a tier 2 block lasts for its block duration even when that is longer than the window, and that
     * attempts refused during it are not counted.
     */
    @Test
    public void testAddressBlockOutlivesItsWindow()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(2L);
        fail(USER, ADDRESS, 1);
        clock.advance(ADDRESS_INTERVAL.minusMinutes(1));
        fail(OTHER_USER, ADDRESS, 1);

        // Execute system under test & verify result.
        clock.advance(Duration.ofMinutes(5));
        assertNull(manager.tryAcquireAttempt("carol", ADDRESS), "the address must be refused after its window has ended while the block lasts");
        clock.advance(ADDRESS_BLOCK_DURATION.minusMinutes(5).minusSeconds(1));
        assertFalse(manager.isLoginAllowed("carol", ADDRESS), "the address must be refused until the block duration ends");
        clock.advance(Duration.ofSeconds(1));
        assertTrue(manager.isLoginAllowed("carol", ADDRESS), "the address must be allowed once the block duration ends");
        fail("carol", ADDRESS, 1);
        assertTrue(manager.isLoginAllowed("dave", ADDRESS), "after a block, counting must start again from zero, without the attempts refused during it");
    }

    /**
     * Verifies that changing the block duration applies to blocks already in force, in both directions.
     */
    @Test
    public void testChangingBlockDurationAppliesToBlocksInForce()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(2L);
        fail(USER, ADDRESS, 2);
        fail(USER, OTHER_ADDRESS, 2);
        clock.advance(Duration.ofMinutes(3));

        // Execute system under test.
        UserLoginLimitManager.PER_PAIR_BLOCK_DURATION.setValue(Duration.ofMinutes(2));
        UserLoginLimitManager.PER_ADDRESS_BLOCK_DURATION.setValue(Duration.ofHours(2));

        // Verify result.
        assertEquals(List.of(UserLoginLimitManager.Tier.ADDRESS, UserLoginLimitManager.Tier.ADDRESS),
            activeBlocks().stream().map(UserLoginLimitManager.ActiveBlock::tier).toList(),
            "a shortened block duration must end the pair blocks in force, and leave the address blocks");
        clock.advance(Duration.ofHours(1));
        assertFalse(manager.isLoginAllowed(OTHER_USER, ADDRESS), "a lengthened block duration must extend the address block in force");
    }

    /**
     * Verifies that a tier 1 or 2 block in force is not lifted by raising the limit above its count: like a tier 3
     * block, it lasts for its duration unless it is cleared, its tier is turned off or the limits are disabled.
     */
    @Test
    public void testRaisingLimitDoesNotLiftBlockInForce()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);
        fail(USER, ADDRESS, 2);

        // Execute system under test.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(5L);

        // Verify result.
        assertFalse(manager.isLoginAllowed(USER, ADDRESS), "raising the limit must not lift a block in force");
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(0L);
        assertTrue(manager.isLoginAllowed(USER, ADDRESS), "turning the tier off must lift its blocks");
    }

    /**
     * Verifies that an entry already at a lowered limit, with no block recorded, is refused, and that its block starts
     * at the next attempt and lasts the block duration from then.
     */
    @Test
    public void testLoweredLimitStartsBlockAtNextAttempt()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(5L);
        fail(USER, ADDRESS, 3);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);
        clock.advance(Duration.ofMinutes(12));

        // Execute system under test.
        assertNull(manager.tryAcquireAttempt(USER, ADDRESS), "an attempt over a lowered limit must be refused");

        // Verify result.
        verify(securityAuditManager, times(1).description("the block started by a lowered limit must be audited")).logEvent(eq(USER), anyString(), anyString());
        clock.advance(PAIR_BLOCK_DURATION.minusSeconds(1));
        assertFalse(manager.isLoginAllowed(USER, ADDRESS), "the block must last the block duration from the refused attempt, beyond the window");
        clock.advance(Duration.ofSeconds(1));
        assertTrue(manager.isLoginAllowed(USER, ADDRESS), "the block must end the block duration after the refused attempt");
    }

    /**
     * Verifies that an entry at a lowered limit that no further attempt reaches is refused until its window ends.
     */
    @Test
    public void testLoweredLimitWithoutFurtherAttemptEndsWithWindow()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(5L);
        fail(USER, ADDRESS, 3);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);

        // Execute system under test & verify result.
        assertEquals(clock.instant().plus(PAIR_INTERVAL), activeBlocks().get(0).endsAt(), "with no block recorded, the entry must be listed until its window ends");
        clock.advance(PAIR_INTERVAL.minusSeconds(1));
        assertFalse(manager.isLoginAllowed(USER, ADDRESS), "an entry over a lowered limit must be refused within its window");
        clock.advance(Duration.ofSeconds(1));
        assertTrue(manager.isLoginAllowed(USER, ADDRESS), "an entry over a lowered limit must be allowed once its window ends");
    }

    /**
     * Verifies that the success of the attempt that reached a tier 2 limit lifts the block it started, as its attempt
     * is given back.
     */
    @Test
    public void testSuccessOfAttemptThatReachedLimitLiftsBlock()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(2L);
        fail(OTHER_USER, ADDRESS, 1);
        final UserLoginLimitManager.Reservation reaching = manager.tryAcquireAttempt(USER, ADDRESS);
        assertFalse(manager.isLoginAllowed("carol", ADDRESS), "the address must be blocked while the reaching attempt is evaluated");

        // Execute system under test.
        manager.recordSuccessfulLogin(reaching);

        // Verify result.
        assertTrue(manager.isLoginAllowed("carol", ADDRESS), "the success of the attempt that reached the limit must lift the block");
        fail("carol", ADDRESS, 1);
        assertFalse(manager.isLoginAllowed("dave", ADDRESS), "the earlier failure must still count");
    }

    /**
     * Verifies that a success reported after the block its attempt was counted towards has ended does not bring back
     * the count of that block.
     */
    @Test
    public void testRefundAfterBlockEndedDoesNotReviveCount()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(2L);
        UserLoginLimitManager.PER_ADDRESS_BLOCK_DURATION.setValue(Duration.ofMinutes(5));
        final UserLoginLimitManager.Reservation earlier = manager.tryAcquireAttempt(USER, ADDRESS);
        fail(OTHER_USER, ADDRESS, 1);
        clock.advance(Duration.ofMinutes(5));

        // Execute system under test.
        manager.recordSuccessfulLogin(earlier);

        // Verify result.
        fail("carol", ADDRESS, 1);
        assertTrue(manager.isLoginAllowed("dave", ADDRESS), "a refund after the block ended must not bring back the attempts counted before it");
    }

    /**
     * Verifies that the audit entry for a tier 1 or tier 2 limit states the block duration.
     */
    @Test
    public void testAuditStatesBlockDuration()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(3L);

        // Execute system under test.
        fail(USER, ADDRESS, 2);
        fail(OTHER_USER, ADDRESS, 1);

        // Verify result.
        final ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(securityAuditManager, times(2).description("each limit reached must be audited once")).logEvent(anyString(), anyString(), details.capture());
        assertTrue(details.getAllValues().get(0).contains("refused for " + PAIR_BLOCK_DURATION + " unless the last of them succeeds"), "the tier 1 entry must state its block duration, and that it applies only if the attempt that reached the limit fails: " + details.getAllValues().get(0));
        assertTrue(details.getAllValues().get(1).contains("refused for " + ADDRESS_BLOCK_DURATION + " unless the last of them succeeds"), "the tier 2 entry must state its block duration, and that it applies only if the attempt that reached the limit fails: " + details.getAllValues().get(1));
        assertFalse(String.join(" ", details.getAllValues()).contains("up to"), "the entries must state the block duration itself");
    }

    /**
     * Verifies that failures spread over more than one window are not added together.
     */
    @Test
    public void testFailuresInEarlierWindowAreNotCounted()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(3L);
        fail(USER, ADDRESS, 2);
        clock.advance(ADDRESS_INTERVAL);

        // Execute system under test.
        fail(USER, ADDRESS, 2);

        // Verify result.
        assertTrue(manager.isLoginAllowed(USER, ADDRESS), "failures from an ended window must not count towards the limit");
    }

    /**
     * Verifies that a limit of 0 turns each tier off.
     */
    @Test
    public void testLimitOfZeroTurnsEachTierOff()
    {
        // Setup test fixture: all three limits are 0 (see setUp).

        // Execute system under test.
        fail(USER, ADDRESS, 100);

        // Verify result.
        assertTrue(manager.isLoginAllowed(USER, ADDRESS), "no tier may block when every limit is 0");
        verify(lockOutManager, never().description("tier 3 must not lock when its limit is 0")).disableAccount(anyString(), any(), any());
        verify(securityAuditManager, never().description("no limit can be crossed when every limit is 0")).logEvent(anyString(), anyString(), anyString());
    }

    /**
     * Verifies that a successful login clears the tier 1 counter for that pair, and gives back only its own attempt
     * to the tier 2 and tier 3 counts.
     */
    @Test
    public void testSuccessClearsOnlyThePairCounter()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(3L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(5L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(6L);
        fail(USER, ADDRESS, 2);

        // Execute system under test.
        succeed(USER, ADDRESS);

        // Verify result.
        fail(USER, ADDRESS, 2);
        assertTrue(manager.isLoginAllowed(USER, ADDRESS), "a success must clear the pair counter");
        fail(OTHER_USER, ADDRESS, 1);
        assertFalse(manager.isLoginAllowed(OTHER_USER, ADDRESS), "a success must not clear the failures already counted for the address");
        fail(USER, OTHER_ADDRESS, 3);
        verify(lockOutManager, times(1).description("a success must not clear the failures already counted for the username")).disableAccount(eq(USER), any(), any());
    }

    /**
     * Verifies that a successful login gives back the attempt it reserved, so that a user who logs in often is not
     * blocked by their own successes.
     */
    @Test
    public void testSuccessGivesBackItsAttempt()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(2L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(2L);

        // Execute system under test.
        for (int i = 0; i < 5; i++) {
            succeed(USER, ADDRESS);
        }

        // Verify result.
        assertNotNull(manager.tryAcquireAttempt(USER, ADDRESS), "successful logins must not use up the limits");
        verify(lockOutManager, never().description("successful logins must not lock the account")).disableAccount(anyString(), any(), any());
    }

    /**
     * Verifies that a refused attempt is not counted, so that a block does not extend itself.
     */
    @Test
    public void testRefusedAttemptIsNotCounted()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);
        fail(USER, ADDRESS, 2);

        // Execute system under test.
        fail(USER, ADDRESS, 5);

        // Verify result.
        assertEquals(2, activeBlocks().get(0).failureCount(), "refused attempts must not be counted");
    }

    /**
     * Verifies that concurrent attempts cannot together exceed a limit: checking and counting an attempt is one step.
     */
    @Test
    public void testConcurrentAttemptsCannotExceedLimit() throws Exception
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(5L);
        final int threads = 32;
        final ExecutorService executor = Executors.newFixedThreadPool(threads);
        final CountDownLatch go = new CountDownLatch(1);
        final List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    go.await();
                    return manager.tryAcquireAttempt(USER, ADDRESS) != null;
                }));
            }

            // Execute system under test.
            go.countDown();
            int admitted = 0;
            for (final Future<Boolean> result : results) {
                if (result.get(10, TimeUnit.SECONDS)) {
                    admitted++;
                }
            }

            // Verify result.
            assertEquals(5, admitted, "no more attempts than the limit may be admitted, however many arrive at once");
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Verifies that lowering a limit below the count of an open window blocks at once, rather than turning the tier
     * off for the rest of the window.
     */
    @Test
    public void testLoweringLimitMidWindowBlocksAtOnce()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(5L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(5L);
        fail(USER, ADDRESS, 3);

        // Execute system under test.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(2L);

        // Verify result.
        assertFalse(manager.isLoginAllowed(USER, ADDRESS), "a pair over its lowered limit must be blocked");
        assertNull(manager.tryAcquireAttempt(USER, OTHER_ADDRESS), "a username over its lowered limit must be refused");
        verify(lockOutManager, times(1).description("a username over its lowered limit must be locked")).disableAccount(eq(USER), any(), any());
    }

    /**
     * Verifies that the success of an attempt admitted before a tier 3 block does not lift the block.
     */
    @Test
    public void testSuccessDoesNotLiftUsernameBlock()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);
        final UserLoginLimitManager.Reservation earlier = manager.tryAcquireAttempt("nobody", ADDRESS);
        fail("nobody", ADDRESS, 3);

        // Execute system under test.
        manager.recordSuccessfulLogin(earlier);

        // Verify result.
        assertFalse(manager.isLoginAllowed("nobody", ADDRESS), "a success must not lift a username block");
    }

    /**
     * Verifies that an administrator's unlock clears the tier 3 cache block.
     */
    @Test
    public void testUnlockClearsUsernameBlock()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);
        fail("nobody", ADDRESS, 4);

        // Execute system under test.
        manager.accountUnlocked("nobody");

        // Verify result.
        assertTrue(manager.isLoginAllowed("nobody", ADDRESS), "an unlock must lift the username block");
    }

    /**
     * Verifies that an administrator's unlock clears the tier 3 counter, so the next failure does not lock the account
     * again at once.
     */
    @Test
    public void testUnlockClearsUsernameCounter()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);
        fail(USER, ADDRESS, 2);

        // Execute system under test.
        manager.accountUnlocked(USER);

        // Verify result.
        fail(USER, ADDRESS, 2);
        verify(lockOutManager, never().description("failures before an unlock must not count after it")).disableAccount(anyString(), any(), any());
    }

    /**
     * Verifies that an unlock leaves the tier 1 and tier 2 counters as they were.
     */
    @Test
    public void testUnlockDoesNotClearPairOrAddressCounters()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(3L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(4L);
        fail(USER, ADDRESS, 3);

        // Execute system under test.
        manager.accountUnlocked(USER);

        // Verify result.
        assertFalse(manager.isLoginAllowed(USER, ADDRESS), "an unlock must not lift a pair block");
        fail(OTHER_USER, ADDRESS, 1);
        assertFalse(manager.isLoginAllowed(OTHER_USER, ADDRESS), "an unlock must not clear the address counter");
    }

    /**
     * Verifies that case variants of a username share their counters and blocks.
     */
    @Test
    public void testUsernamesAreNormalised()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(3L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);

        // Execute system under test.
        fail("Alice", ADDRESS, 1);
        fail("ALICE", ADDRESS, 1);
        fail("alice", ADDRESS, 1);

        // Verify result.
        assertFalse(manager.isLoginAllowed("aLiCe", ADDRESS), "case variants of a username must share a pair counter");
        fail("aLiCe", OTHER_ADDRESS, 1);
        verify(lockOutManager, times(1).description("case variants of a username must share a username counter, and the lock must use the normalised username")).disableAccount(eq(USER), any(), any());
    }

    /**
     * Verifies that a username nodeprep rejects is still counted, lower-cased.
     */
    @Test
    public void testUsernameThatFailsNodeprepIsCountedLowerCased()
    {
        // Setup test fixture: ':' is prohibited in a node.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);

        // Execute system under test.
        fail("Not:Valid", ADDRESS, 1);
        fail("not:valid", ADDRESS, 1);

        // Verify result.
        assertFalse(manager.isLoginAllowed("NOT:VALID", ADDRESS), "an invalid username must be counted case-insensitively");
    }

    /**
     * Verifies that a username given with this server's XMPP domain ('user@domain', which the default authentication
     * provider accepts) shares its counts with the bare username, so a client cannot double its attempts by
     * alternating the two forms.
     */
    @Test
    public void testUsernameWithServerDomainSharesCountsWithBareUsername()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);

        // Execute system under test.
        fail(USER + "@" + XMPP_DOMAIN, ADDRESS, 1);
        fail(USER, ADDRESS, 1);

        // Verify result.
        assertFalse(manager.isLoginAllowed(USER, ADDRESS), "a username with the server's domain must share its count with the bare username");
        assertFalse(manager.isLoginAllowed(" Alice@Example.ORG ", ADDRESS), "surrounding whitespace and the case of the username or domain must not escape the count");
    }

    /**
     * Verifies that a username with another domain is not stripped, as the default authentication provider refuses it
     * rather than treating it as a local user.
     */
    @Test
    public void testUsernameWithOtherDomainIsCountedSeparately()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);

        // Execute system under test.
        fail(USER + "@other.example", ADDRESS, 2);

        // Verify result.
        assertTrue(manager.isLoginAllowed(USER, ADDRESS), "failures for a username with another domain must not count against the local user");
        assertFalse(manager.isLoginAllowed(USER + "@other.example", ADDRESS), "a username with another domain must be counted under its own key");
    }

    /**
     * Verifies that a username too long to be a localpart (more than 1023 bytes, RFC 7622) is counted for its address
     * only, and is never used as a cache key, so that long names cannot inflate the caches.
     */
    @Test
    public void testOverlongUsernameCountsForAddressOnly()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(1L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(2L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(1L);
        final String overlong = "a".repeat(1024);

        // Execute system under test.
        fail(overlong, ADDRESS, 2);

        // Verify result.
        assertTrue(pairFailures.isEmpty(), "an overlong username must not be used as a pair key");
        assertTrue(unknownPairFailures.isEmpty() && unknownUsernameFailures.isEmpty(), "an overlong username must not be used as a key of the counts of usernames without an account");
        assertTrue(usernameFailures.isEmpty(), "an overlong username must not be used as a username key");
        assertTrue(usernameBlocks.isEmpty(), "an overlong username must not be blocked under its own key");
        assertFalse(manager.isLoginAllowed(USER, ADDRESS), "failures for an overlong username must count for the address");
        verify(lockOutManager, never().description("an overlong username cannot name an account, so its lock must not be looked up")).isAccountDisabled(overlong);
    }

    /**
     * Verifies that a username of exactly 1023 bytes is still counted as a username.
     */
    @Test
    public void testUsernameAtLocalpartLimitIsCounted()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(1L);
        final String longest = "a".repeat(1023);

        // Execute system under test.
        fail(longest, ADDRESS, 1);

        // Verify result.
        assertFalse(manager.isLoginAllowed(longest, ADDRESS), "a 1023-byte username must be counted for its pair");
    }

    /**
     * Verifies that the size a cached value reports includes the key it is stored under, so that the caches' size
     * bound limits the memory that keys take up too.
     */
    @Test
    public void testCachedSizeIncludesKey()
    {
        // Setup test fixture.
        final String shortKey = "k";
        final String longKey = "k".repeat(1000);

        // Execute system under test & verify result.
        assertTrue(new UserLoginLimitManager.FailureWindow(longKey, 0, 1, null, false).getCachedSize() - new UserLoginLimitManager.FailureWindow(shortKey, 0, 1, null, false).getCachedSize() >= 999,
            "a failure window's size must grow with the length of its key");
        assertTrue(new UserLoginLimitManager.AddressBlock(longKey, UserLoginLimitManager.Tier.ADDRESS, null, false, ADDRESS, 0, 0, 1).getCachedSize() - new UserLoginLimitManager.AddressBlock(shortKey, UserLoginLimitManager.Tier.ADDRESS, null, false, ADDRESS, 0, 0, 1).getCachedSize() >= 999,
            "an address block's size must grow with the length of its key");
        assertTrue(new UserLoginLimitManager.UsernameBlock(longKey, "u", false, 0, 1).getCachedSize() - new UserLoginLimitManager.UsernameBlock(shortKey, "u", false, 0, 1).getCachedSize() >= 999,
            "a username block's size must grow with the length of its key");
    }

    /**
     * Verifies that with the login limits off, a successful login takes no tier 1 lock and removes nothing, so that
     * the default configuration adds no cluster work to a login.
     */
    @Test
    public void testDisabledSuccessTouchesNoPairCounter()
    {
        // Setup test fixture.
        UserLoginLimitManager.ENABLED.setValue(false);
        final Cache<String, UserLoginLimitManager.FailureWindow> pairCache = mockCache();
        final UserLoginLimitManager disabled = new UserLoginLimitManager(pairCache, addressFailures, usernameFailures, unknownPairFailures, unknownUsernameFailures, pairBlocks, addressBlocks, usernameBlocks,
            () -> lockOutManager, () -> lockOutProvider, existingUsers::contains, administrators::contains, () -> securityAuditManager, () -> XMPP_DOMAIN, clock);

        // Execute system under test.
        final UserLoginLimitManager.Reservation reservation = disabled.tryAcquireAttempt(USER, ADDRESS);
        disabled.recordSuccessfulLogin(reservation);

        // Verify result.
        verify(pairCache, never().description("a success must take no tier 1 lock while the limits are off")).getLock(any());
        verify(pairCache, never().description("a success must remove nothing while the limits are off")).remove(any());
    }

    /**
     * Verifies that with tier 1 turned off (limit 0), a successful login takes no tier 1 lock and removes nothing.
     */
    @Test
    public void testSuccessTouchesNoPairCounterWhenTierOneIsOff()
    {
        // Setup test fixture: every limit is 0 (see setUp) apart from tier 2.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(5L);
        final Cache<String, UserLoginLimitManager.FailureWindow> pairCache = mockCache();
        final UserLoginLimitManager tierOneOff = new UserLoginLimitManager(pairCache, addressFailures, usernameFailures, unknownPairFailures, unknownUsernameFailures, pairBlocks, addressBlocks, usernameBlocks,
            () -> lockOutManager, () -> lockOutProvider, existingUsers::contains, administrators::contains, () -> securityAuditManager, () -> XMPP_DOMAIN, clock);

        // Execute system under test.
        final UserLoginLimitManager.Reservation reservation = tierOneOff.tryAcquireAttempt(USER, ADDRESS);
        tierOneOff.recordSuccessfulLogin(reservation);

        // Verify result.
        verify(pairCache, never().description("a success must take no tier 1 lock while tier 1 is off")).getLock(any());
        verify(pairCache, never().description("a success must remove nothing while tier 1 is off")).remove(any());
    }

    @SuppressWarnings("unchecked")
    private static Cache<String, UserLoginLimitManager.FailureWindow> mockCache()
    {
        return mock(Cache.class);
    }

    /**
     * Verifies that the length of a username does not add to the size of its tier 1 count, so a spray of long made-up
     * usernames, fewer than the counts cache holds, leaves the count of another username that has no account in place;
     * usernames without an account are thus limited as others are, even while such a spray goes on. The cache has the
     * size registered for production (5 MB). It is keyed by the address and the username's digest, and keeps at most
     * 64 characters of the username for display, so each entry is a few hundred bytes whatever the username's length;
     * keyed by the full username, these 5,000 usernames of 1,000 characters would fill it several times over.
     *
     * A spray of more attempts than the cache holds can still evict such a count, but never an account's count (see
     * testFloodOfUnknownUsernamesCannotEvictPairCount); what survives such a flood is a block (see
     * testPairBlockSurvivesFloodThatEvictsCounts).
     */
    @Test
    public void testUnknownPairCountSurvivesSprayOfLongUsernamesSmallerThanTheCache()
    {
        // Setup test fixture.
        final DefaultLocalCacheStrategy strategy = new DefaultLocalCacheStrategy();
        final Cache<String, UserLoginLimitManager.FailureWindow> realUnknownPairFailures = strategy.createCache(UserLoginLimitManager.UNKNOWN_PAIR_FAILURES_CACHE_NAME);
        final UserLoginLimitManager sprayed = new UserLoginLimitManager(pairFailures, addressFailures, usernameFailures, realUnknownPairFailures, unknownUsernameFailures, pairBlocks, addressBlocks, usernameBlocks,
            () -> lockOutManager, () -> lockOutProvider, existingUsers::contains, administrators::contains, () -> securityAuditManager, () -> XMPP_DOMAIN, clock);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(5L);
        for (int i = 0; i < 4; i++) {
            sprayed.tryAcquireAttempt("carol", ADDRESS);
        }

        // Execute system under test.
        final String padding = "j".repeat(1000);
        for (int i = 0; i < 5_000; i++) {
            sprayed.tryAcquireAttempt(padding + i, "198.51.100." + (i % 250));
        }

        // Verify result.
        assertEquals(5_001, realUnknownPairFailures.size(), "every made-up username's count must still be held, for this test to show anything");
        assertNotNull(sprayed.tryAcquireAttempt("carol", ADDRESS), "the fifth attempt must be admitted");
        assertNull(sprayed.tryAcquireAttempt("carol", ADDRESS), "the username's count must survive the spray, so the attempt after the limit is refused");
    }

    /**
     * Verifies that a tier 1 block is kept apart from the counts, so that it still refuses the pair when the pair's
     * count has gone from the counts cache (as when a flood of attempts makes the cache evict it).
     */
    @Test
    public void testPairBlockSurvivesLossOfItsCount()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);
        fail(USER, ADDRESS, 2);

        // Execute system under test.
        pairFailures.clear();

        // Verify result.
        assertFalse(manager.isLoginAllowed(USER, ADDRESS), "a tier 1 block must refuse the pair after its count has gone");
        assertNull(manager.tryAcquireAttempt(USER, ADDRESS), "a tier 1 block must refuse an attempt after its count has gone");
        assertEquals(List.of(new UserLoginLimitManager.ActiveBlock(UserLoginLimitManager.Tier.USERNAME_FROM_ADDRESS, USER, false, ADDRESS, 2, clock.instant().plus(PAIR_BLOCK_DURATION), NO_KEY)),
            withoutKeys(activeBlocks()), "a tier 1 block must be listed after its count has gone");
        clock.advance(PAIR_BLOCK_DURATION);
        assertNotNull(manager.tryAcquireAttempt(USER, ADDRESS), "the block must still end after its block duration");
    }

    /**
     * Verifies that a tier 2 block is kept apart from the counts, so that it still refuses the address when the
     * address's count has gone from the counts cache.
     */
    @Test
    public void testAddressBlockSurvivesLossOfItsCount()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(2L);
        fail(USER, ADDRESS, 1);
        fail(OTHER_USER, ADDRESS, 1);

        // Execute system under test.
        addressFailures.clear();

        // Verify result.
        assertFalse(manager.isLoginAllowed("carol", ADDRESS), "a tier 2 block must refuse the address after its count has gone");
        assertNull(manager.tryAcquireAttempt("carol", ADDRESS), "a tier 2 block must refuse an attempt after its count has gone");
        assertEquals(List.of(new UserLoginLimitManager.ActiveBlock(UserLoginLimitManager.Tier.ADDRESS, null, false, ADDRESS, 2, clock.instant().plus(ADDRESS_BLOCK_DURATION), NO_KEY)),
            withoutKeys(activeBlocks()), "a tier 2 block must be listed after its count has gone");
        clock.advance(ADDRESS_BLOCK_DURATION);
        assertNotNull(manager.tryAcquireAttempt("carol", ADDRESS), "the block must still end after its block duration");
    }

    /**
     * Verifies that the block started by an attempt over a lowered limit is kept apart from the counts as well.
     */
    @Test
    public void testLoweredLimitBlockSurvivesLossOfItsCount()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(5L);
        fail(USER, ADDRESS, 3);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);
        assertNull(manager.tryAcquireAttempt(USER, ADDRESS), "an attempt over a lowered limit must be refused");

        // Execute system under test.
        pairFailures.clear();

        // Verify result.
        assertFalse(manager.isLoginAllowed(USER, ADDRESS), "the block started over a lowered limit must refuse the pair after its count has gone");
    }

    /**
     * Verifies that a flood of attempts that fills a tier 1 counts cache, so that it evicts entries, does not remove a
     * tier 1 block. The caches have the sizes registered for production. The attacker has reached the limit for a
     * username that has no account from its address, then tries enough other made-up usernames from the same address
     * to overflow the counts cache that held that username's count, none of them reaching a limit.
     */
    @Test
    public void testPairBlockSurvivesFloodThatEvictsCounts()
    {
        // Setup test fixture.
        final DefaultLocalCacheStrategy strategy = new DefaultLocalCacheStrategy();
        final Cache<String, UserLoginLimitManager.FailureWindow> realUnknownPairFailures = strategy.createCache(UserLoginLimitManager.UNKNOWN_PAIR_FAILURES_CACHE_NAME);
        final Cache<String, UserLoginLimitManager.AddressBlock> realPairBlocks = strategy.createCache(UserLoginLimitManager.PAIR_BLOCKS_CACHE_NAME);
        final UserLoginLimitManager flooded = new UserLoginLimitManager(pairFailures, addressFailures, usernameFailures, realUnknownPairFailures, unknownUsernameFailures, realPairBlocks, addressBlocks, usernameBlocks,
            () -> lockOutManager, () -> lockOutProvider, existingUsers::contains, administrators::contains, () -> securityAuditManager, () -> XMPP_DOMAIN, clock);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(5L);
        for (int i = 0; i < 5; i++) {
            flooded.tryAcquireAttempt("carol", ADDRESS);
        }
        assertNull(flooded.tryAcquireAttempt("carol", ADDRESS), "the pair must be blocked before the flood");

        // Execute system under test.
        final int flood = 60_000;
        for (int i = 0; i < flood; i++) {
            flooded.tryAcquireAttempt("junk" + i, ADDRESS);
        }

        // Verify result.
        assertTrue(realUnknownPairFailures.size() < flood, "the flood must overflow the counts cache for this test to show anything (it holds " + realUnknownPairFailures.size() + " entries)");
        assertNull(flooded.tryAcquireAttempt("carol", ADDRESS), "the block must survive a flood that overflows the counts cache");
    }

    /**
     * Verifies that a flood of attempts for usernames that have no account cannot evict a real user's tier 1 count, so
     * that repeating the flood does not reset the count and let the guessing go on. The attacker makes nine guesses at
     * the victim's password from one address, then tries 40,000 made-up usernames from the same address (more than a
     * counts cache of the size registered for production holds), and repeats this five times. Only the default limit
     * of ten guesses may be admitted in all.
     */
    @Test
    public void testFloodOfUnknownUsernamesCannotEvictPairCount()
    {
        // Setup test fixture.
        final DefaultLocalCacheStrategy strategy = new DefaultLocalCacheStrategy();
        final Cache<String, UserLoginLimitManager.FailureWindow> realPairFailures = strategy.createCache(UserLoginLimitManager.PAIR_FAILURES_CACHE_NAME);
        final Cache<String, UserLoginLimitManager.FailureWindow> realUnknownPairFailures = strategy.createCache(UserLoginLimitManager.UNKNOWN_PAIR_FAILURES_CACHE_NAME);
        final Cache<String, UserLoginLimitManager.AddressBlock> realPairBlocks = strategy.createCache(UserLoginLimitManager.PAIR_BLOCKS_CACHE_NAME);
        final UserLoginLimitManager flooded = new UserLoginLimitManager(realPairFailures, addressFailures, usernameFailures, realUnknownPairFailures, unknownUsernameFailures, realPairBlocks, addressBlocks, usernameBlocks,
            () -> lockOutManager, () -> lockOutProvider, existingUsers::contains, administrators::contains, () -> securityAuditManager, () -> XMPP_DOMAIN, clock);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(10L);
        final int flood = 40_000;
        int admitted = 0;
        int junk = 0;

        // Execute system under test.
        for (int round = 0; round < 5; round++) {
            for (int guess = 0; guess < 9; guess++) {
                if (flooded.tryAcquireAttempt(USER, ADDRESS) != null) {
                    admitted++;
                }
            }
            for (int i = 0; i < flood; i++) {
                flooded.tryAcquireAttempt("junk" + junk++, ADDRESS);
            }
        }

        // Verify result.
        assertTrue(realUnknownPairFailures.size() < flood, "each flood must overflow the counts cache of made-up usernames for this test to show anything (it holds " + realUnknownPairFailures.size() + " entries)");
        assertEquals(10, admitted, "a flood of made-up usernames must not reset the victim's count, so only the limit of guesses is admitted");
        assertFalse(flooded.isLoginAllowed(USER, ADDRESS), "the victim's username must be blocked from the attacker's address");
    }

    /**
     * Verifies that a username that has no account is counted apart from usernames that have one, and is still
     * limited and blocked exactly as they are, so that the answers do not reveal whether the account exists.
     */
    @Test
    public void testUnknownUsernameIsCountedApartAndBlockedAsAnAccountIs()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(10L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(20L);
        fail("carol", ADDRESS, 5);
        fail(USER, ADDRESS, 5);
        assertEquals(1, unknownPairFailures.size(), "the username without an account must be counted from its address in its own counts cache");
        assertEquals(1, pairFailures.size(), "only the account's username must be counted from its address in the accounts' counts cache");
        assertEquals(1, unknownUsernameFailures.size(), "the username without an account must be counted from any address in its own counts cache");
        assertEquals(1, usernameFailures.size(), "only the account's username must be counted from any address in the accounts' counts cache");

        // Execute system under test.
        fail("carol", ADDRESS, 5);
        fail(USER, ADDRESS, 5);
        final UserLoginLimitManager.Reservation unknownAnswer = manager.tryAcquireAttempt("carol", ADDRESS);
        final UserLoginLimitManager.Reservation knownAnswer = manager.tryAcquireAttempt(USER, ADDRESS);

        // Verify result.
        assertNull(unknownAnswer, "the username without an account must be refused after the limit");
        assertNull(knownAnswer, "the account's username must be refused after the limit");
        assertFalse(manager.isLoginAllowed("carol", ADDRESS), "the username without an account must be blocked from the address");
        assertFalse(manager.isLoginAllowed(USER, ADDRESS), "the account's username must be blocked from the address");
        assertEquals(manager.isLockedOut("carol"), manager.isLockedOut(USER), "a tier 1 block must be answered the same whether or not the account exists");
        final List<UserLoginLimitManager.ActiveBlock> blocks = withoutKeys(activeBlocks());
        assertEquals(Set.of(
                new UserLoginLimitManager.ActiveBlock(UserLoginLimitManager.Tier.USERNAME_FROM_ADDRESS, "carol", false, ADDRESS, 10, clock.instant().plus(PAIR_BLOCK_DURATION), NO_KEY),
                new UserLoginLimitManager.ActiveBlock(UserLoginLimitManager.Tier.USERNAME_FROM_ADDRESS, USER, false, ADDRESS, 10, clock.instant().plus(PAIR_BLOCK_DURATION), NO_KEY)),
            new HashSet<>(blocks), "both usernames must be blocked alike, after the same number of attempts and for the same time");
        assertEquals(2, blocks.size(), "each username must be blocked once");
    }

    /**
     * Verifies that a success for a username that had no account when its attempt was reserved (for example, an
     * account created meanwhile) clears its tier 1 count and gives its attempt back to its tier 3 count, in the counts
     * caches it was counted in.
     */
    @Test
    public void testSuccessGivesBackToTheCountsCachesTheAttemptWasCountedIn()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(5L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(10L);
        fail("carol", ADDRESS, 4);

        // Execute system under test.
        succeed("carol", ADDRESS);

        // Verify result.
        assertTrue(unknownPairFailures.isEmpty(), "the success must clear the username's count from its address");
        assertEquals(4, unknownUsernameFailures.get(UserLoginLimitManager.usernameKey("carol")).count(), "the success must give its attempt back to the username's count from any address");
        assertTrue(pairFailures.isEmpty(), "nothing must be counted in the accounts' counts caches for a username without an account");
        assertTrue(usernameFailures.isEmpty(), "nothing must be counted in the accounts' counts caches for a username without an account");
        for (int i = 0; i < 5; i++) {
            assertNotNull(manager.tryAcquireAttempt("carol", ADDRESS), "after the success, the limit's number of attempts must be admitted again");
        }
    }

    /**
     * Verifies that the lockout page's queries and Clear login limits find and reset the counts and blocks of a
     * username that has no account, as they do an account's.
     */
    @Test
    public void testClearBlocksForUsernameResetsCountsOfUnknownUsername()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(10L);
        fail("carol", ADDRESS, 2);
        fail("carol", OTHER_ADDRESS, 1);
        final UserLoginLimitManager.BlocksForUsername before = manager.getBlocksForUsername("carol", 10);
        assertEquals(1, before.fromAddressTotal(), "the page must list the username's block from one address");
        assertTrue(before.counted(), "the page must say that failures are counted for the username");

        // Execute system under test.
        final UserLoginLimitManager.ClearedBlocks cleared = manager.clearBlocksForUsername("carol");

        // Verify result.
        assertEquals(1, cleared.blocks().size(), "the username's block from one address must be lifted");
        assertTrue(cleared.countsReset(), "the username's counts must be reported as reset");
        assertTrue(unknownPairFailures.isEmpty(), "the username's counts from each address must be gone");
        assertTrue(unknownUsernameFailures.isEmpty(), "the username's count from any address must be gone");
        assertFalse(manager.getBlocksForUsername("carol", 10).counted(), "the page must say that nothing is counted for the username any more");
        assertTrue(manager.isLoginAllowed("carol", ADDRESS), "the username must be admitted again from the address it was blocked from");
    }

    /**
     * Verifies that clearing the blocks of an address lifts those of usernames that have no account as well as those
     * of accounts, and the Login Limits page lists both.
     */
    @Test
    public void testClearBlocksForAddressLiftsBlocksOfUnknownUsernames()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);
        fail("carol", ADDRESS, 2);
        fail(USER, ADDRESS, 2);
        final UserLoginLimitManager.BlocksByAddress listed = manager.getBlocksByAddress(10, 10);
        assertEquals(1, listed.total(), "the page must list the one address");
        assertEquals(2, listed.addresses().get(0).usernameTotal(), "the page must list the blocks of both usernames under the address");

        // Execute system under test.
        final List<UserLoginLimitManager.ActiveBlock> cleared = manager.clearBlocksForAddress(ADDRESS);

        // Verify result.
        assertEquals(2, cleared.size(), "the blocks of both usernames must be lifted");
        assertTrue(manager.isLoginAllowed("carol", ADDRESS), "the username without an account must be admitted again");
        assertTrue(manager.isLoginAllowed(USER, ADDRESS), "the account's username must be admitted again");
    }

    /**
     * Verifies that a count of a username that has no account, over a limit lowered since its last attempt, refuses
     * the username and is listed and can be cleared as an account's is.
     */
    @Test
    public void testUnknownUsernameCountOverLoweredLimitIsListedAndCleared()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(5L);
        fail("carol", ADDRESS, 3);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);

        // Execute system under test.
        final List<UserLoginLimitManager.ActiveBlock> blocks = activeBlocks();
        final boolean allowedBefore = manager.isLoginAllowed("carol", ADDRESS);
        final Optional<UserLoginLimitManager.ActiveBlock> cleared = manager.clearActiveBlock(UserLoginLimitManager.Tier.USERNAME_FROM_ADDRESS, blocks.get(0).key());

        // Verify result.
        assertEquals(1, blocks.size(), "the count over the lowered limit must be listed as a block");
        assertFalse(allowedBefore, "the count over the lowered limit must refuse the username");
        assertTrue(cleared.isPresent(), "the block must be cleared by its key");
        assertTrue(unknownPairFailures.isEmpty(), "clearing the block must remove its count");
        assertTrue(manager.isLoginAllowed("carol", ADDRESS), "the username must be admitted once its block is cleared");
    }

    /**
     * Verifies how a failed lookup of whether an account exists chooses the counts caches: a username that is not a
     * valid node cannot name an account, so it is counted as one without; any other failure is counted as an
     * account's, so that a real user's count is never put where made-up usernames can evict it.
     */
    @Test
    public void testAccountLookupFailureCountsAsAnAccountUnlessTheUsernameIsInvalid()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(5L);
        final UserLoginLimitManager failingLookup = new UserLoginLimitManager(pairFailures, addressFailures, usernameFailures, unknownPairFailures, unknownUsernameFailures, pairBlocks, addressBlocks, usernameBlocks,
            () -> lockOutManager, () -> lockOutProvider,
            username -> { throw username.equals(USER) ? new IllegalStateException("user store unavailable") : new IllegalArgumentException("not a valid node"); },
            administrators::contains, () -> securityAuditManager, () -> XMPP_DOMAIN, clock);

        // Execute system under test.
        failingLookup.tryAcquireAttempt(USER, ADDRESS);
        failingLookup.tryAcquireAttempt("not valid", ADDRESS);

        // Verify result.
        assertEquals(1, pairFailures.size(), "a username whose lookup failed must be counted as an account's");
        assertEquals(1, unknownPairFailures.size(), "a username that is not a valid node must be counted as one without an account");
    }

    /**
     * Verifies that a tier 1 block for a long username is listed with the username shortened to 64 characters and
     * marked so, and can be cleared both from the listing and by username and address.
     */
    @Test
    public void testPairBlockShowsTruncatedNameAndCanBeCleared()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(1L);
        final String longName = "u".repeat(100);
        fail(longName, ADDRESS, 1);
        fail(USER, ADDRESS, 1);

        // Execute system under test.
        final List<UserLoginLimitManager.ActiveBlock> blocks = activeBlocks();

        // Verify result.
        final UserLoginLimitManager.ActiveBlock longBlock = blocks.stream().filter(UserLoginLimitManager.ActiveBlock::usernameTruncated).findFirst().orElseThrow();
        assertEquals("u".repeat(64), longBlock.username(), "a long username must be listed shortened to 64 characters");
        assertEquals(ADDRESS, longBlock.address(), "the block must be listed with its address");
        assertTrue(blocks.stream().anyMatch(b -> USER.equals(b.username()) && !b.usernameTruncated()), "a short username must be listed in full");
        assertTrue(manager.clearActiveBlock(longBlock.tier(), longBlock.key()).isPresent(), "a block listed with a shortened username must be cleared from the listing");
        assertTrue(manager.isLoginAllowed(longName, ADDRESS), "the cleared block must no longer refuse the username");
        manager.clearBlock(UserLoginLimitManager.Tier.USERNAME_FROM_ADDRESS, USER, ADDRESS);
        assertTrue(manager.isLoginAllowed(USER, ADDRESS), "a block must be cleared by its username and address");
    }

    /**
     * Verifies that when releasing one of the cache locks an attempt holds fails, the others are still released.
     */
    @Test
    public void testEveryLockIsReleasedWhenOneUnlockFails()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(5L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(5L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(5L);
        final Lock pairLock = mock(Lock.class);
        final Lock addressLock = mock(Lock.class);
        final Lock usernameLock = mock(Lock.class);
        doThrow(new IllegalMonitorStateException("lease expired")).when(usernameLock).unlock();
        final Cache<String, UserLoginLimitManager.FailureWindow> pairs = spy(pairFailures);
        final Cache<String, UserLoginLimitManager.FailureWindow> addresses = spy(addressFailures);
        final Cache<String, UserLoginLimitManager.FailureWindow> usernames = spy(usernameFailures);
        doReturn(pairLock).when(pairs).getLock(any());
        doReturn(addressLock).when(addresses).getLock(any());
        doReturn(usernameLock).when(usernames).getLock(any());
        final UserLoginLimitManager locking = new UserLoginLimitManager(pairs, addresses, usernames, unknownPairFailures, unknownUsernameFailures, pairBlocks, addressBlocks, usernameBlocks,
            () -> lockOutManager, () -> lockOutProvider, existingUsers::contains, administrators::contains, () -> securityAuditManager, () -> XMPP_DOMAIN, clock);

        // Execute system under test.
        assertDoesNotThrow(() -> locking.tryAcquireAttempt(USER, ADDRESS), "a failure to release a lock must not escape to the login");

        // Verify result.
        verify(addressLock, times(1).description("the address lock must be released although the username lock could not be")).unlock();
        verify(pairLock, times(1).description("the pair lock must be released although the username lock could not be")).unlock();
    }

    /**
     * Verifies that a username that cannot name an account (it is not a valid node) and reaches tier 3 is blocked in
     * the cache, with the reason recorded, rather than the failure escaping.
     */
    @Test
    public void testInvalidUsernameReachingTierThreeIsBlockedInCache()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(2L);
        existingUsers.add("bad name");
        when(lockOutManager.getDisabledStatus("bad name")).thenThrow(new IllegalArgumentException("Node is not valid"));

        // Execute system under test.
        assertDoesNotThrow(() -> fail("bad name", ADDRESS, 3), "an invalid username must not make the attempt fail with an exception");

        // Verify result.
        assertFalse(manager.isLoginAllowed("bad name", OTHER_ADDRESS), "the username must be blocked in the cache");
        final ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(securityAuditManager, times(1).description("the block must be audited once")).logEvent(anyString(), anyString(), details.capture());
        assertTrue(details.getValue().contains("the username is not a valid account name"), "the audit entry must give the reason: " + details.getValue());
    }

    /**
     * Verifies that cached values survive Java serialisation, as clustered caches serialise them between nodes.
     */
    @Test
    public void testCachedValuesSurviveSerialisation() throws Exception
    {
        // Setup test fixture.
        final UserLoginLimitManager.FailureWindow window = new UserLoginLimitManager.FailureWindow("key", 1234L, 5L, "alice", true);
        final UserLoginLimitManager.UsernameBlock block = new UserLoginLimitManager.UsernameBlock("digest", "user", true, 5678L, 7L);
        final UserLoginLimitManager.AddressBlock addressBlock = new UserLoginLimitManager.AddressBlock("pair", UserLoginLimitManager.Tier.USERNAME_FROM_ADDRESS, "carol", true, ADDRESS, 1111L, 2222L, 9L);

        // Execute system under test.
        final UserLoginLimitManager.FailureWindow windowCopy = roundTrip(window);
        final UserLoginLimitManager.UsernameBlock blockCopy = roundTrip(block);
        final UserLoginLimitManager.AddressBlock addressBlockCopy = roundTrip(addressBlock);

        // Verify result.
        assertEquals("key", windowCopy.key(), "the window's key must survive serialisation");
        assertEquals(1234L, windowCopy.start(), "the window's start must survive serialisation");
        assertEquals(5L, windowCopy.count(), "the window's count must survive serialisation");
        assertEquals("pair", addressBlockCopy.key(), "the address block's key must survive serialisation");
        assertEquals(UserLoginLimitManager.Tier.USERNAME_FROM_ADDRESS, addressBlockCopy.tier(), "the address block's tier must survive serialisation");
        assertEquals("carol", addressBlockCopy.display(), "the address block's display username must survive serialisation");
        assertTrue(addressBlockCopy.truncated(), "whether the address block's username was shortened must survive serialisation");
        assertEquals(ADDRESS, addressBlockCopy.address(), "the address block's address must survive serialisation");
        assertEquals(1111L, addressBlockCopy.windowStart(), "the start of the window that led to the address block must survive serialisation");
        assertEquals(2222L, addressBlockCopy.start(), "the address block's start must survive serialisation");
        assertEquals(9L, addressBlockCopy.failures(), "the address block's failure count must survive serialisation");
        assertEquals("alice", windowCopy.display(), "the window's display username must survive serialisation");
        assertTrue(windowCopy.truncated(), "whether the window's username was shortened must survive serialisation");
        assertEquals("digest", blockCopy.key(), "the block's key must survive serialisation");
        assertEquals("user", blockCopy.display(), "the block's display username must survive serialisation");
        assertTrue(blockCopy.truncated(), "whether the block's username was shortened must survive serialisation");
        assertEquals(5678L, blockCopy.end(), "the block's end must survive serialisation");
        assertEquals(7L, blockCopy.failures(), "the block's failure count must survive serialisation");
    }

    /**
     * Verifies that a tier 2 audit entry is filed under the address, not under whichever username was tried last, so
     * that an address block cannot appear under a real user's name.
     */
    @Test
    public void testAddressLimitIsAuditedUnderTheAddress()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(2L);

        // Execute system under test.
        fail(OTHER_USER, ADDRESS, 1);
        fail(USER, ADDRESS, 1);

        // Verify result.
        verify(securityAuditManager, times(1).description("the address block must be audited under the address")).logEvent(eq(ADDRESS), anyString(), anyString());
        verify(securityAuditManager, never().description("the address block must not be audited under a username")).logEvent(eq(USER), anyString(), anyString());
    }

    /**
     * Verifies that the subject of an audit entry is no longer than the audit log's username column (64 characters).
     */
    @Test
    public void testAuditSubjectIsTruncatedToColumnWidth()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(1L);
        final String longName = "b".repeat(100);

        // Execute system under test.
        fail(longName, ADDRESS, 1);

        // Verify result.
        final ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        verify(securityAuditManager, times(1).description("the pair limit must be audited")).logEvent(subject.capture(), anyString(), anyString());
        assertEquals(64, subject.getValue().length(), "the audit subject must be truncated to 64 characters");
    }

    /**
     * Verifies that control characters in a username (which could forge log lines) are escaped in audit entries.
     */
    @Test
    public void testControlCharactersAreEscapedInAudit()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(1L);
        final String forged = "mallory\n2026-11-10 INFO admin logged in";

        // Execute system under test.
        fail(forged, ADDRESS, 1);

        // Verify result.
        final ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(securityAuditManager, times(1).description("the pair limit must be audited")).logEvent(subject.capture(), anyString(), details.capture());
        assertFalse(subject.getValue().contains("\n"), "a newline must not reach the audit subject");
        assertFalse(details.getValue().contains("\n"), "a newline must not reach the audit details");
    }

    /**
     * Verifies that an account locked through LockOutManager is refused while the login limits are disabled, and that
     * the refusal is reported to LockOutManager's listeners as before.
     */
    @Test
    public void testLockedAccountIsRefusedWhileLimitsDisabled()
    {
        // Setup test fixture.
        UserLoginLimitManager.ENABLED.setValue(false);
        when(lockOutManager.isAccountDisabled(USER)).thenReturn(true);

        // Execute system under test.
        final boolean result = manager.tryAcquireAttempt("Alice", ADDRESS) == null;

        // Verify result.
        assertTrue(result, "a locked account must be refused whether or not the limits are enabled");
        verify(lockOutManager, times(1).description("the refusal of a locked account must be reported to lockout listeners")).recordFailedLogin(USER);
    }

    /**
     * Verifies that an unlocked account with no block is not refused.
     */
    @Test
    public void testUnlockedAndUnblockedAccountIsNotRefused()
    {
        assertNotNull(manager.tryAcquireAttempt(USER, ADDRESS), "an unlocked account without a block must be admitted");
        assertNotNull(manager.tryAcquireAttempt(USER, null), "an unlocked account without a block must be admitted when the address is unknown");
    }

    /**
     * Verifies that a blocked username is refused even though the account is not locked.
     */
    @Test
    public void testBlockedAttemptIsRefused()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);
        fail(USER, ADDRESS, 2);

        // Execute system under test & verify result.
        assertNull(manager.tryAcquireAttempt(USER, ADDRESS), "a blocked pair must be refused");
        assertNotNull(manager.tryAcquireAttempt(USER, OTHER_ADDRESS), "the same username from another address must be admitted");
        verify(lockOutManager, never().description("a block is not an account lock, so no lockout event is fired")).recordFailedLogin(anyString());
    }

    /**
     * Verifies that a failure to check the account lock does not refuse the attempt by itself. The lock is checked
     * again after a successful exchange, so a locked account still cannot log in; refusing here would turn a lookup
     * problem into a login outage.
     */
    @Test
    public void testLockCheckFailureDoesNotRefuseOnItsOwn()
    {
        // Setup test fixture.
        when(lockOutManager.isAccountDisabled(anyString())).thenThrow(new IllegalArgumentException("not a valid node"));

        // Execute system under test & verify result.
        assertNotNull(manager.tryAcquireAttempt(USER, ADDRESS), "a failure to check the lock must leave the decision to the later checks");
    }

    /**
     * Verifies that one audit entry is written when tier 1 is crossed, and none for further failures.
     */
    @Test
    public void testOneAuditEntryWhenPairLimitIsCrossed()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);

        // Execute system under test.
        fail(USER, ADDRESS, 1);
        verify(securityAuditManager, never().description("no audit entry before the limit is reached")).logEvent(anyString(), anyString(), anyString());
        fail(USER, ADDRESS, 3);

        // Verify result.
        verify(securityAuditManager, times(1).description("exactly one audit entry when the pair limit is crossed")).logEvent(eq(USER), anyString(), anyString());
    }

    /**
     * Verifies that one audit entry is written when tier 2 is crossed.
     */
    @Test
    public void testOneAuditEntryWhenAddressLimitIsCrossed()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(2L);

        // Execute system under test.
        fail(USER, ADDRESS, 1);
        fail(OTHER_USER, ADDRESS, 3);

        // Verify result.
        verify(securityAuditManager, times(1).description("exactly one audit entry when the address limit is crossed")).logEvent(anyString(), anyString(), anyString());
    }

    /**
     * Verifies that one audit entry is written when tier 3 locks an account.
     */
    @Test
    public void testOneAuditEntryWhenUsernameLimitLocksAccount()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(2L);

        // Execute system under test.
        fail(USER, ADDRESS, 3);

        // Verify result.
        verify(securityAuditManager, times(1).description("exactly one audit entry when the username limit is crossed")).logEvent(eq(USER), anyString(), anyString());
    }

    /**
     * Verifies that one audit entry is written when tier 3 blocks a username in the cache.
     */
    @Test
    public void testOneAuditEntryWhenUsernameLimitBlocksInCache()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(2L);

        // Execute system under test.
        fail("nobody", ADDRESS, 3);

        // Verify result.
        verify(securityAuditManager, times(1).description("exactly one audit entry when the username limit is crossed")).logEvent(eq("nobody"), anyString(), anyString());
    }

    /**
     * Verifies that a failure with no known address is counted for the username only.
     */
    @Test
    public void testFailureWithoutAddressCountsForUsernameOnly()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(1L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(1L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);

        // Execute system under test.
        fail(USER, null, 4);

        // Verify result.
        assertTrue(pairFailures.isEmpty(), "a failure with no address must not be counted for a pair");
        assertTrue(addressFailures.isEmpty(), "a failure with no address must not be counted for an address");
        verify(lockOutManager, times(1).description("a failure with no address must be counted for the username")).disableAccount(eq(USER), any(), any());
    }

    /**
     * Verifies that nothing is listed when no limit has been reached, including counters below their limit.
     */
    @Test
    public void testActiveBlocksExcludesCountersBelowLimit()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(3L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(3L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);

        // Execute system under test.
        fail(USER, ADDRESS, 2);

        // Verify result.
        assertTrue(activeBlocks().isEmpty(), "counters below their limit must not be listed as blocks");
    }

    /**
     * Verifies that a tier 1 block is listed with its username, address, failure count and end.
     */
    @Test
    public void testActiveBlocksListsPairBlock()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(3L);
        final Instant reached = clock.instant();
        fail("Alice", ADDRESS, 3);

        // Execute system under test.
        final List<UserLoginLimitManager.ActiveBlock> result = activeBlocks();

        // Verify result.
        assertEquals(List.of(new UserLoginLimitManager.ActiveBlock(UserLoginLimitManager.Tier.USERNAME_FROM_ADDRESS, USER, false, ADDRESS, 3, reached.plus(PAIR_BLOCK_DURATION), NO_KEY)),
            withoutKeys(result), "the pair block must be listed with the normalised username, the address, the count and its end, the block duration after the limit was reached");
    }

    /**
     * Verifies that a tier 2 block is listed with its address and no username.
     */
    @Test
    public void testActiveBlocksListsAddressBlock()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(2L);
        fail(USER, ADDRESS, 1);
        clock.advance(Duration.ofMinutes(1));
        final Instant reached = clock.instant();
        fail(OTHER_USER, ADDRESS, 1);

        // Execute system under test.
        final List<UserLoginLimitManager.ActiveBlock> result = activeBlocks();

        // Verify result.
        assertEquals(List.of(new UserLoginLimitManager.ActiveBlock(UserLoginLimitManager.Tier.ADDRESS, null, false, ADDRESS, 2, reached.plus(ADDRESS_BLOCK_DURATION), NO_KEY)),
            withoutKeys(result), "the address block must be listed with no username, and end the block duration after the attempt that reached the limit");
    }

    /**
     * Verifies that a tier 3 cache block is listed with its username and no address.
     */
    @Test
    public void testActiveBlocksListsUsernameBlock()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);
        fail("nobody", ADDRESS, 3);
        clock.advance(Duration.ofMinutes(5));
        final Instant blockStart = clock.instant();
        fail("nobody", OTHER_ADDRESS, 1);

        // Execute system under test.
        final List<UserLoginLimitManager.ActiveBlock> result = activeBlocks();

        // Verify result.
        assertEquals(List.of(new UserLoginLimitManager.ActiveBlock(UserLoginLimitManager.Tier.USERNAME, "nobody", false, null, 3, blockStart.plus(LOCKOUT_DURATION), NO_KEY)),
            withoutKeys(result), "the username block must be listed with no address, and end a lockout duration after the limit was reached");
    }

    /**
     * Verifies that blocks are listed across all tiers together.
     */
    @Test
    public void testActiveBlocksListsEveryTier()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(2L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(2L);

        // Execute system under test.
        fail("nobody", ADDRESS, 3);

        // Verify result.
        final Set<UserLoginLimitManager.Tier> tiers = new HashSet<>();
        activeBlocks().forEach(block -> tiers.add(block.tier()));
        assertEquals(Set.of(UserLoginLimitManager.Tier.values()), tiers, "a block from each tier must be listed");
    }

    /**
     * Verifies that blocks whose window or duration has ended are not listed.
     */
    @Test
    public void testActiveBlocksExcludesEndedBlocks()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(2L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(2L);
        fail("nobody", ADDRESS, 3);

        // Execute system under test.
        clock.advance(LOCKOUT_DURATION);

        // Verify result.
        assertTrue(activeBlocks().isEmpty(), "blocks whose window or duration has ended must not be listed");
    }

    /**
     * Verifies that a tier whose limit is 0 lists nothing, as it blocks nothing.
     */
    @Test
    public void testActiveBlocksExcludesTierTurnedOff()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);
        fail(USER, ADDRESS, 2);

        // Execute system under test.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(0L);

        // Verify result.
        assertTrue(activeBlocks().isEmpty(), "a tier that is turned off must not list blocks");
    }

    /**
     * Verifies that nothing is listed while the feature is disabled, as nothing is blocked.
     */
    @Test
    public void testActiveBlocksIsEmptyWhileDisabled()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);
        fail(USER, ADDRESS, 2);

        // Execute system under test.
        UserLoginLimitManager.ENABLED.setValue(false);

        // Verify result.
        assertTrue(activeBlocks().isEmpty(), "a disabled manager must not list blocks");
    }

    /**
     * Verifies that clearing a tier 1 block lifts it and clears its counter, and leaves other pairs alone.
     */
    @Test
    public void testClearPairBlock()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);
        fail(USER, ADDRESS, 2);
        fail(USER, OTHER_ADDRESS, 2);

        // Execute system under test.
        manager.clearBlock(UserLoginLimitManager.Tier.USERNAME_FROM_ADDRESS, "Alice", ADDRESS);

        // Verify result.
        assertTrue(manager.isLoginAllowed(USER, ADDRESS), "the cleared pair must be allowed");
        assertFalse(manager.isLoginAllowed(USER, OTHER_ADDRESS), "clearing one pair must leave another pair blocked");
        fail(USER, ADDRESS, 1);
        assertTrue(manager.isLoginAllowed(USER, ADDRESS), "clearing a pair block must also clear its counter");
    }

    /**
     * Verifies that clearing a tier 2 block lifts it and clears its counter.
     */
    @Test
    public void testClearAddressBlock()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(2L);
        fail(USER, ADDRESS, 2);

        // Execute system under test.
        manager.clearBlock(UserLoginLimitManager.Tier.ADDRESS, null, ADDRESS);

        // Verify result.
        assertTrue(manager.isLoginAllowed(OTHER_USER, ADDRESS), "the cleared address must be allowed");
        fail(USER, ADDRESS, 1);
        assertTrue(manager.isLoginAllowed(OTHER_USER, ADDRESS), "clearing an address block must also clear its counter");
    }

    /**
     * Verifies that clearing a tier 3 block lifts it and clears its counter.
     */
    @Test
    public void testClearUsernameBlock()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(2L);
        fail("nobody", ADDRESS, 2);
        fail("nobody", ADDRESS, 1);

        // Execute system under test.
        manager.clearBlock(UserLoginLimitManager.Tier.USERNAME, "Nobody", null);

        // Verify result.
        assertTrue(manager.isLoginAllowed("nobody", ADDRESS), "the cleared username must be allowed");
        assertTrue(activeBlocks().isEmpty(), "the cleared username block must no longer be listed");
        fail("nobody", ADDRESS, 1);
        assertTrue(manager.isLoginAllowed("nobody", ADDRESS), "clearing a username block must also clear its counter");
    }

    /**
     * Verifies that clearing a block without the key its tier needs is rejected.
     */
    @Test
    public void testClearBlockRequiresTheTierKey()
    {
        assertThrows(IllegalArgumentException.class, () -> manager.clearBlock(UserLoginLimitManager.Tier.USERNAME_FROM_ADDRESS, USER, null), "a pair block needs an address");
        assertThrows(IllegalArgumentException.class, () -> manager.clearBlock(UserLoginLimitManager.Tier.USERNAME_FROM_ADDRESS, null, ADDRESS), "a pair block needs a username");
        assertThrows(IllegalArgumentException.class, () -> manager.clearBlock(UserLoginLimitManager.Tier.ADDRESS, USER, null), "an address block needs an address");
        assertThrows(IllegalArgumentException.class, () -> manager.clearBlock(UserLoginLimitManager.Tier.USERNAME, null, ADDRESS), "a username block needs a username");
    }

    /**
     * Verifies that a listed block can be cleared by its tier and key, and that the block cleared is returned.
     */
    @Test
    public void testClearActiveBlockByKey()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(5L);
        fail(USER, ADDRESS, 2);
        for (int i = 0; i < 6; i++) {
            fail("nobody", "192.0.2." + (10 + i), 1); // One attempt per address, so that only tier 3 is reached.
        }
        final UserLoginLimitManager.ActiveBlock pairBlock = activeBlocks().stream()
            .filter(b -> b.tier() == UserLoginLimitManager.Tier.USERNAME_FROM_ADDRESS && USER.equals(b.username())).findFirst().orElseThrow();

        // Execute system under test.
        final Optional<UserLoginLimitManager.ActiveBlock> result = manager.clearActiveBlock(pairBlock.tier(), pairBlock.key());

        // Verify result.
        assertEquals(Optional.of(pairBlock), result, "the block that was cleared must be returned");
        assertTrue(manager.isLoginAllowed(USER, ADDRESS), "the cleared pair block must be lifted");
        assertFalse(manager.isLoginAllowed("nobody", OTHER_ADDRESS), "other blocks must be left in place");
    }

    /**
     * Verifies that a key that does not name a block in force, or names one under another tier, clears nothing.
     */
    @Test
    public void testClearActiveBlockIgnoresUnknownKey()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);
        fail(USER, ADDRESS, 2);
        final UserLoginLimitManager.ActiveBlock pairBlock = activeBlocks().get(0);

        // Execute system under test.
        final Optional<UserLoginLimitManager.ActiveBlock> unknown = manager.clearActiveBlock(UserLoginLimitManager.Tier.USERNAME_FROM_ADDRESS, "no such key");
        final Optional<UserLoginLimitManager.ActiveBlock> wrongTier = manager.clearActiveBlock(UserLoginLimitManager.Tier.ADDRESS, pairBlock.key());

        // Verify result.
        assertTrue(unknown.isEmpty(), "an unknown key must clear nothing");
        assertTrue(wrongTier.isEmpty(), "a key must only match a block of its own tier");
        assertFalse(manager.isLoginAllowed(USER, ADDRESS), "the block must stay in place");
    }

    /**
     * Verifies that a block whose window has ended since it was listed is not reported as cleared.
     */
    @Test
    public void testClearActiveBlockIgnoresEndedBlock()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);
        fail(USER, ADDRESS, 2);
        final UserLoginLimitManager.ActiveBlock pairBlock = activeBlocks().get(0);
        clock.advance(PAIR_INTERVAL);

        // Execute system under test.
        final Optional<UserLoginLimitManager.ActiveBlock> result = manager.clearActiveBlock(pairBlock.tier(), pairBlock.key());

        // Verify result.
        assertTrue(result.isEmpty(), "a block that has ended must not be reported as cleared");
    }

    /**
     * Verifies that a success gives its attempt back only to the window it was counted in: once that window has ended,
     * the refund must not reduce the count of a later window.
     */
    @Test
    public void testRefundAfterWindowRolledOverIsIgnored()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(3L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);
        final UserLoginLimitManager.Reservation earlier = manager.tryAcquireAttempt(USER, ADDRESS);
        clock.advance(USERNAME_INTERVAL);
        fail(OTHER_USER, ADDRESS, 2);
        fail(USER, OTHER_ADDRESS, 3);

        // Execute system under test.
        manager.recordSuccessfulLogin(earlier);

        // Verify result.
        fail("carol", ADDRESS, 1);
        assertFalse(manager.isLoginAllowed("dave", ADDRESS), "a refund from an ended window must not reduce the address count of the current one");
        assertFalse(manager.isLoginAllowed(USER, "192.0.2.9"), "a refund from an ended window must not reduce the username count of the current one");
    }

    /**
     * Verifies that the check for callers that cannot give an attempt back (no session) refuses a locked account or a
     * blocked attempt, and counts nothing.
     */
    @Test
    public void testIsAttemptAllowedChecksWithoutCounting()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(1L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(1L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(1L);
        when(lockOutManager.isAccountDisabled(OTHER_USER)).thenReturn(true);

        // Execute system under test & verify result.
        for (int i = 0; i < 3; i++) {
            assertTrue(manager.isAttemptAllowed(USER, ADDRESS), "an unlocked, unblocked attempt must be allowed, however often it is checked");
        }
        assertTrue(pairFailures.isEmpty(), "the check must count nothing for the username and address");
        assertTrue(addressFailures.isEmpty(), "the check must count nothing for the address");
        assertTrue(usernameFailures.isEmpty(), "the check must count nothing for the username");
        assertFalse(manager.isAttemptAllowed(OTHER_USER, ADDRESS), "a locked account must not be allowed");
        fail(USER, ADDRESS, 1);
        assertFalse(manager.isAttemptAllowed(USER, ADDRESS), "a blocked attempt must not be allowed");
    }

    /**
     * Verifies that the per-username caches are keyed by a fixed-size digest, whatever the length of the username.
     */
    @Test
    public void testUsernameKeysAreFixedSize()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(1L);

        // Execute system under test.
        fail("x".repeat(1000), ADDRESS, 2);
        fail("y", ADDRESS, 2);

        // Verify result.
        assertEquals(Set.of(44), usernameBlocks.keySet().stream().map(String::length).collect(java.util.stream.Collectors.toSet()), "username block keys must be 44-character digests");
    }

    /**
     * Verifies that a tier 3 block shows its username truncated to 64 characters, and that it can still be cleared
     * through the listed block.
     */
    @Test
    public void testUsernameBlockShowsTruncatedNameAndCanBeCleared()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(1L);
        final String longName = "z".repeat(100);
        fail(longName, ADDRESS, 2);
        final UserLoginLimitManager.ActiveBlock block = activeBlocks().get(0);

        // Execute system under test.
        manager.clearActiveBlock(block.tier(), block.key());

        // Verify result.
        assertEquals("z".repeat(64), block.username(), "the listed username must be truncated to 64 characters");
        assertTrue(manager.isLoginAllowed(longName, ADDRESS), "clearing the listed block must lift it");
    }

    /**
     * Verifies that a flood of attempts for usernames that have no account cannot evict a real user's tier 3 count.
     *
     * The caches here have the sizes registered for production. Each count's reported size is about 130 bytes,
     * whatever the length of the username, as the username caches are keyed by digest, so a 20 MB counts cache holds
     * roughly 160,000 usernames. This test floods 200,000 made-up usernames, more than the counts cache of usernames
     * without an account holds, so that it evicts entries; the victim's count is in the accounts' counts cache, which
     * the flood does not touch.
     */
    @Test
    public void testFloodOfUnknownUsernamesCannotEvictUsernameCount()
    {
        // Setup test fixture.
        final DefaultLocalCacheStrategy strategy = new DefaultLocalCacheStrategy();
        final Cache<String, UserLoginLimitManager.FailureWindow> realUsernameFailures = strategy.createCache(UserLoginLimitManager.USERNAME_FAILURES_CACHE_NAME);
        final Cache<String, UserLoginLimitManager.FailureWindow> realUnknownUsernameFailures = strategy.createCache(UserLoginLimitManager.UNKNOWN_USERNAME_FAILURES_CACHE_NAME);
        final Cache<String, UserLoginLimitManager.UsernameBlock> realUsernameBlocks = strategy.createCache(UserLoginLimitManager.USERNAME_BLOCKS_CACHE_NAME);
        final UserLoginLimitManager flooded = new UserLoginLimitManager(pairFailures, addressFailures, realUsernameFailures, unknownPairFailures, realUnknownUsernameFailures, pairBlocks, addressBlocks, realUsernameBlocks,
            () -> lockOutManager, () -> lockOutProvider, existingUsers::contains, administrators::contains, () -> securityAuditManager, () -> XMPP_DOMAIN, clock);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(5L);
        for (int i = 0; i < 4; i++) {
            flooded.tryAcquireAttempt(USER, null);
        }

        // Execute system under test.
        final int flood = 200_000;
        for (int i = 0; i < flood; i++) {
            flooded.tryAcquireAttempt("junk" + i, null);
        }

        // Verify result.
        assertTrue(realUnknownUsernameFailures.size() < flood, "the flood must overflow the counts cache of made-up usernames for this test to show anything (it holds " + realUnknownUsernameFailures.size() + " entries)");
        assertNotNull(flooded.tryAcquireAttempt(USER, null), "the fifth attempt must be admitted");
        assertNull(flooded.tryAcquireAttempt(USER, null), "the victim's count must survive the flood, so the attempt after the limit is refused");
        verify(lockOutManager, times(1).description("the victim's account must be locked once its count reaches the limit")).disableAccount(eq(USER), any(), any());
    }

    /**
     * Verifies that a username that has no account is counted from any address in its own counts cache, and blocked
     * from any address after the limit as an account's username is, without an account to lock.
     */
    @Test
    public void testUnknownUsernameIsCountedApartAndBlockedFromAnyAddress()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);
        fail("carol", ADDRESS, 2);
        assertEquals(1, unknownUsernameFailures.size(), "the username without an account must be counted in its own counts cache");
        assertTrue(usernameFailures.isEmpty(), "the username without an account must not be counted in the accounts' counts cache");

        // Execute system under test.
        fail("carol", OTHER_ADDRESS, 1);
        final UserLoginLimitManager.Reservation refused = manager.tryAcquireAttempt("carol", THIRD_ADDRESS);

        // Verify result.
        assertNull(refused, "the attempt after the limit must be refused");
        assertTrue(manager.isLockedOut("carol"), "the username must be refused from every address, as an account's would be");
        assertTrue(manager.hasUsernameBlock("carol"), "the username must be blocked from any address");
        verify(lockOutManager, never().description("no account must be locked for a username without one")).disableAccount(anyString(), any(), any());
    }

    /**
     * Verifies that IPv6 addresses are grouped by their /64 prefix for the per-address limits, as one network is
     * typically given a whole /64, while IPv4 and IPv4-mapped addresses are counted individually.
     */
    @Test
    public void testIpv6AddressesAreGroupedBy64()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(2L);

        // Execute system under test.
        fail("u1", "2001:db8:1:2::1", 1);
        fail("u2", "2001:db8:1:2:ffff:ffff:ffff:9", 1);
        fail("u3", "192.0.2.10", 1);
        fail("u4", "::ffff:192.0.2.20", 1);

        // Verify result.
        assertFalse(manager.isLoginAllowed("u5", "2001:db8:1:2::abc"), "addresses in one /64 must share a count");
        assertTrue(manager.isLoginAllowed("u5", "2001:db8:1:3::1"), "another /64 must have its own count");
        assertTrue(manager.isLoginAllowed("u5", "192.0.2.11"), "IPv4 addresses must not be grouped");
        assertTrue(manager.isLoginAllowed("u5", "::ffff:192.0.2.21"), "IPv4-mapped addresses must not be grouped");
        assertEquals("2001:db8:1:2::/64", activeBlocks().get(0).address(), "the block must show the /64 it applies to");
    }

    /**
     * Verifies that address and username blocks can be cleared by their tier and key.
     */
    @Test
    public void testClearActiveBlockByKeyForAddressAndUsername()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(2L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(2L);
        fail("nobody", ADDRESS, 3);
        final List<UserLoginLimitManager.ActiveBlock> blocks = activeBlocks();
        final UserLoginLimitManager.ActiveBlock addressBlock = blocks.stream().filter(b -> b.tier() == UserLoginLimitManager.Tier.ADDRESS).findFirst().orElseThrow();
        fail("nobody", OTHER_ADDRESS, 1);
        final UserLoginLimitManager.ActiveBlock usernameBlock = activeBlocks().stream().filter(b -> b.tier() == UserLoginLimitManager.Tier.USERNAME).findFirst().orElseThrow();

        // Execute system under test.
        final Optional<UserLoginLimitManager.ActiveBlock> clearedAddress = manager.clearActiveBlock(UserLoginLimitManager.Tier.ADDRESS, addressBlock.key());
        final Optional<UserLoginLimitManager.ActiveBlock> clearedUsername = manager.clearActiveBlock(UserLoginLimitManager.Tier.USERNAME, usernameBlock.key());

        // Verify result.
        assertEquals(Optional.of(addressBlock), clearedAddress, "the address block must be cleared and returned");
        assertEquals(Optional.of(usernameBlock), clearedUsername, "the username block must be cleared and returned");
        assertTrue(activeBlocks().isEmpty(), "no block must remain");
    }

    /**
     * Verifies that a counter below its limit is not cleared through clearActiveBlock, as it blocks nothing.
     */
    @Test
    public void testClearActiveBlockIgnoresCounterBelowLimit()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(3L);
        fail(USER, ADDRESS, 2);

        // Execute system under test.
        final Optional<UserLoginLimitManager.ActiveBlock> result = manager.clearActiveBlock(UserLoginLimitManager.Tier.ADDRESS, ADDRESS);

        // Verify result.
        assertTrue(result.isEmpty(), "a counter below its limit is not a block");
        fail(USER, ADDRESS, 1);
        assertFalse(manager.isLoginAllowed(OTHER_USER, ADDRESS), "the counter must have been left as it was");
    }

    /**
     * Verifies that a username of exactly 64 characters is listed whole and not marked as shortened, and that one of
     * 65 is shortened to 64 and marked, for tier 3 and tier 1.
     */
    @Test
    public void testUsernameTruncationIsMarked()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(1L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(1L);
        final String exact = "a".repeat(64);
        final String longer = "b".repeat(65);
        fail(exact, ADDRESS, 1);
        fail(exact, OTHER_ADDRESS, 1);
        fail(longer, ADDRESS, 1);
        fail(longer, OTHER_ADDRESS, 1);

        // Execute system under test.
        final List<UserLoginLimitManager.ActiveBlock> blocks = activeBlocks();

        // Verify result.
        for (final UserLoginLimitManager.Tier tier : List.of(UserLoginLimitManager.Tier.USERNAME_FROM_ADDRESS, UserLoginLimitManager.Tier.USERNAME)) {
            final UserLoginLimitManager.ActiveBlock exactBlock = blocks.stream().filter(b -> b.tier() == tier && b.username().startsWith("a")).findFirst().orElseThrow();
            final UserLoginLimitManager.ActiveBlock longerBlock = blocks.stream().filter(b -> b.tier() == tier && b.username().startsWith("b")).findFirst().orElseThrow();
            assertEquals(exact, exactBlock.username(), "a 64-character username must be listed whole (" + tier + ")");
            assertFalse(exactBlock.usernameTruncated(), "a 64-character username must not be marked as shortened (" + tier + ")");
            assertEquals("b".repeat(64), longerBlock.username(), "a 65-character username must be shortened to 64 (" + tier + ")");
            assertTrue(longerBlock.usernameTruncated(), "a 65-character username must be marked as shortened (" + tier + ")");
        }
    }

    /**
     * Verifies that the compile-time cache lifetime equals the longest duration any window or lock may last.
     */
    @Test
    public void testMaxDurationMillisMatchesMaxDuration()
    {
        // Execute system under test & verify result.
        assertEquals(UserLoginLimitManager.MAX_DURATION.toMillis(), UserLoginLimitManager.MAX_DURATION_MILLIS, "the cache lifetime must equal the longest duration");
    }

    /**
     * Verifies that bidirectional text controls, which can make a logged value read differently from what it is, are
     * escaped along with control characters.
     */
    @Test
    public void testForLogEscapesBidiControls()
    {
        // Setup test fixture.
        final String bidi = "a\u061C\u200E\u200F\u202A\u202B\u202C\u202D\u202E\u2066\u2067\u2068\u2069b";

        // Execute system under test.
        final String result = UserLoginLimitManager.forLog(bidi);

        // Verify result.
        assertEquals("a\\u061c\\u200e\\u200f\\u202a\\u202b\\u202c\\u202d\\u202e\\u2066\\u2067\\u2068\\u2069b", result, "every bidi control must be escaped");
        assertEquals("a\u2010b", UserLoginLimitManager.forLog("a\u2010b"), "characters next to the bidi controls must be left alone");
    }

    /**
     * Verifies that an IP address already at a lowered limit, with no block recorded, is refused, that its block starts
     * at the next attempt with one audit entry, and that it lasts the block duration from then.
     */
    @Test
    public void testLoweredAddressLimitStartsBlockAtNextAttempt()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(5L);
        fail("user1", ADDRESS, 1);
        fail("user2", ADDRESS, 1);
        fail("user3", ADDRESS, 1);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(2L);
        clock.advance(Duration.ofMinutes(12));

        // Execute system under test.
        final UserLoginLimitManager.Reservation next = manager.tryAcquireAttempt("user4", ADDRESS);

        // Verify result.
        assertNull(next, "an attempt from an IP address over a lowered limit must be refused");
        clock.advance(ADDRESS_BLOCK_DURATION.minusSeconds(1));
        assertNull(manager.tryAcquireAttempt("user5", ADDRESS), "the block must last the block duration from the refused attempt, beyond the window");
        clock.advance(Duration.ofSeconds(1));
        assertNotNull(manager.tryAcquireAttempt("user5", ADDRESS), "the block must end the block duration after the refused attempt");
        verify(securityAuditManager, times(1).description("the block started by a lowered limit must be audited once, under the IP address")).logEvent(eq(ADDRESS), anyString(), anyString());
        verify(securityAuditManager, times(1).description("no other audit entry may be written")).logEvent(anyString(), anyString(), anyString());
    }

    /**
     * Verifies that a change to the lockout duration applies only to locks set after it: a username block in force
     * keeps the end it was given, and a block set after the change gets the new duration.
     */
    @Test
    public void testLockoutDurationChangeAppliesOnlyToLaterLocks()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);
        final Instant firstLocked = clock.instant();
        fail("nobody", ADDRESS, 4);

        // Execute system under test.
        UserLoginLimitManager.LOCKOUT_DURATION.setValue(Duration.ofHours(2));
        final Instant secondLocked = clock.instant();
        fail("nobody2", ADDRESS, 4);

        // Verify result.
        final List<UserLoginLimitManager.ActiveBlock> blocks = activeBlocks();
        assertEquals(2, blocks.size(), "both usernames must be blocked");
        assertEquals(firstLocked.plus(LOCKOUT_DURATION), blocks.stream().filter(b -> "nobody".equals(b.username())).findFirst().orElseThrow().endsAt(),
            "a block in force must keep the end it was given when the lockout duration changes");
        assertEquals(secondLocked.plus(Duration.ofHours(2)), blocks.stream().filter(b -> "nobody2".equals(b.username())).findFirst().orElseThrow().endsAt(),
            "a block set after the change must last the new lockout duration");
        clock.advance(LOCKOUT_DURATION);
        assertTrue(manager.isLoginAllowed("nobody", OTHER_ADDRESS), "the earlier block must end at its original end");
        assertFalse(manager.isLoginAllowed("nobody2", OTHER_ADDRESS), "the later block must still apply");
    }

    /**
     * Verifies that when looking up whether the account exists fails, the username stays blocked in the cache for the
     * lockout duration, and the audit entry says that locking the account failed.
     */
    @Test
    public void testUserLookupFailureLeavesUsernameBlockInForce()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);
        final UserLoginLimitManager failingLookup = new UserLoginLimitManager(pairFailures, addressFailures, usernameFailures, unknownPairFailures, unknownUsernameFailures, pairBlocks, addressBlocks, usernameBlocks,
            () -> lockOutManager, () -> lockOutProvider, username -> { throw new IllegalStateException("user store unavailable"); }, administrators::contains,
            () -> securityAuditManager, () -> XMPP_DOMAIN, clock);

        // Execute system under test.
        for (int i = 0; i < 4; i++) {
            assertDoesNotThrow(() -> failingLookup.tryAcquireAttempt(USER, ADDRESS), "a failure to look the account up must not escape to the login");
        }

        // Verify result.
        assertFalse(failingLookup.isLoginAllowed(USER, OTHER_ADDRESS), "the username must stay blocked when the account could not be looked up");
        verify(lockOutManager, never().description("an account that could not be looked up must not be locked")).disableAccount(anyString(), any(), any());
        final ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(securityAuditManager, times(1).description("the block must be audited once")).logEvent(eq(USER), anyString(), details.capture());
        assertTrue(details.getValue().contains("because locking the account failed"), "the audit entry must give the reason: " + details.getValue());
    }

    /**
     * Verifies that an administrator's account that reaches the per-username limit is not locked, so that the
     * administrator can still sign in to the admin console, but its username is refused from any IP address until the
     * lockout duration has passed, and the audit entry says why no lock was set.
     */
    @Test
    public void testAdministratorIsBlockedWithoutLock()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);
        administrators.add(USER);

        // Execute system under test.
        fail(USER, ADDRESS, 4);

        // Verify result.
        verify(lockOutManager, never().description("an administrator's account must not be locked")).disableAccount(anyString(), any(), any());
        assertNull(manager.tryAcquireAttempt(USER, OTHER_ADDRESS), "an administrator's username must be refused from any IP address while the block lasts");
        final ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(securityAuditManager, times(1).description("the block must be audited once")).logEvent(eq(USER), anyString(), details.capture());
        assertTrue(details.getValue().contains("without locking the account because the account is an administrator's"), "the audit entry must say why no lock was set: " + details.getValue());
        clock.advance(LOCKOUT_DURATION.minusSeconds(1));
        assertNull(manager.tryAcquireAttempt(USER, OTHER_ADDRESS), "the username must be refused until the lockout duration has passed");
        clock.advance(Duration.ofSeconds(1));
        assertNotNull(manager.tryAcquireAttempt(USER, OTHER_ADDRESS), "the username must be admitted once the lockout duration has passed");
    }

    /**
     * Verifies that a user who is not an administrator is still locked when another account is an administrator.
     */
    @Test
    public void testNonAdministratorIsStillLocked()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);
        administrators.add(OTHER_USER);

        // Execute system under test.
        fail(USER, ADDRESS, 4);

        // Verify result.
        verify(lockOutManager, times(1).description("an account that is not an administrator's must be locked")).disableAccount(eq(USER), any(), any());
    }

    /**
     * Verifies that when whether the account is an administrator's cannot be determined, the username is blocked in
     * the cache without a lock, so that a failing lookup cannot lock an administrator out of the admin console.
     */
    @Test
    public void testAdministratorLookupFailureBlocksWithoutLock()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);
        final UserLoginLimitManager failingLookup = new UserLoginLimitManager(pairFailures, addressFailures, usernameFailures, unknownPairFailures, unknownUsernameFailures, pairBlocks, addressBlocks, usernameBlocks,
            () -> lockOutManager, () -> lockOutProvider, existingUsers::contains, username -> { throw new IllegalStateException("admin list unavailable"); },
            () -> securityAuditManager, () -> XMPP_DOMAIN, clock);

        // Execute system under test.
        for (int i = 0; i < 4; i++) {
            assertDoesNotThrow(() -> failingLookup.tryAcquireAttempt(USER, ADDRESS), "a failure to determine whether the account is an administrator's must not escape to the login");
        }

        // Verify result.
        verify(lockOutManager, never().description("an account that may be an administrator's must not be locked")).disableAccount(anyString(), any(), any());
        assertFalse(failingLookup.isLoginAllowed(USER, OTHER_ADDRESS), "the username must be blocked in the cache instead");
    }

    /**
     * Verifies that a very long username made of combining marks in reverse canonical order (for which nodeprep's cost
     * grows with the square of the length) is handled quickly, and is not treated as an account name.
     */
    @Test
    public void testOverlongUsernameIsNotPreparedWithNodeprep()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(1L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(5L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(1L);
        final String costly = "a" + "́".repeat(100_000) + "̖".repeat(100_000);

        // Execute system under test.
        final UserLoginLimitManager.Reservation reservation = assertTimeoutPreemptively(Duration.ofSeconds(5),
            () -> manager.tryAcquireAttempt(costly, ADDRESS), "an overlong username must be normalised without nodeprep's quadratic cost");
        final boolean allowed = assertTimeoutPreemptively(Duration.ofSeconds(5),
            () -> manager.isAttemptAllowed(costly, ADDRESS), "an overlong username must be checked without nodeprep's quadratic cost");

        // Verify result.
        assertNotNull(reservation, "the attempt must be admitted, as no limit has been reached");
        assertTrue(allowed, "a later check must allow it, as no limit has been reached");
        assertTrue(pairFailures.isEmpty(), "an overlong username must not be counted for a username and IP address");
        assertTrue(unknownPairFailures.isEmpty() && unknownUsernameFailures.isEmpty(), "an overlong username must not be counted as a username without an account either");
        assertTrue(usernameFailures.isEmpty(), "an overlong username must not be counted for its username");
        assertEquals(1, addressFailures.size(), "an overlong username must be counted for its IP address");
        verify(lockOutManager, never().description("an overlong username cannot name an account, so its lock must not be looked up")).isAccountDisabled(anyString());
    }

    /**
     * Verifies that whitespace between a username and this server's domain is removed, so that 'alice  @domain'
     * shares its counts with 'alice'.
     */
    @Test
    public void testWhitespaceBeforeServerDomainIsRemoved()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);

        // Execute system under test.
        fail(USER + "  @" + XMPP_DOMAIN, ADDRESS, 1);
        fail(USER, ADDRESS, 1);

        // Verify result.
        assertFalse(manager.isLoginAllowed(USER, ADDRESS), "a username with whitespace before the server's domain must share its count with the bare username");
    }

    /**
     * Verifies that the audit details of a limit reached by a long username give it in shortened form, marked so,
     * whatever its length: for the IP address limit with a username too long to name an account, and for the limit per
     * username from one IP address with one that can.
     */
    @Test
    public void testAuditDetailsStayBoundedForLongUsernames()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(1L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(1L);
        final String overlong = "x".repeat(100_000);
        final String longest = "y".repeat(1023);

        // Execute system under test.
        fail(overlong, ADDRESS, 1);
        fail(longest, OTHER_ADDRESS, 1);

        // Verify result.
        final ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(securityAuditManager, times(3).description("each limit reached must be audited once")).logEvent(anyString(), anyString(), details.capture());
        for (final String detail : details.getAllValues()) {
            assertTrue(detail.length() < 500, "audit details must stay bounded, whatever the length of the username: " + detail.length() + " characters");
        }
        assertTrue(details.getAllValues().get(0).contains("'" + "x".repeat(64) + "' (shortened)"), "the IP address limit's entry must give the username shortened and marked: " + details.getAllValues().get(0));
        assertTrue(details.getAllValues().get(1).contains("'" + "y".repeat(64) + "' (shortened)"), "the per-username limit's entry must give the username shortened and marked: " + details.getAllValues().get(1));
    }

    /**
     * Verifies that an account locked through LockOutManager counts as locked out, whether or not the limits are
     * enabled, and that asking does not report a refused login to LockOutManager's listeners.
     */
    @Test
    public void testLockedAccountIsLockedOut()
    {
        // Setup test fixture.
        when(lockOutManager.isAccountDisabled(USER)).thenReturn(true);

        // Execute system under test & verify result.
        assertTrue(manager.isLockedOut("Alice"), "a locked account must count as locked out");
        UserLoginLimitManager.ENABLED.setValue(false);
        assertTrue(manager.isLockedOut(USER), "a locked account must count as locked out while the limits are disabled");
        assertFalse(manager.isLockedOut(OTHER_USER), "an account that is not locked and has no block must not count as locked out");
        verify(lockOutManager, never().description("asking must not report a refused login")).recordFailedLogin(anyString());
    }

    /**
     * Verifies that a username blocked by the per-username limit counts as locked out, including one whose account is
     * not locked (an administrator's), so that its attempts get the same answer as those for a locked account; and that
     * it no longer does once the block has ended.
     */
    @Test
    public void testUsernameBlockIsLockedOut()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);
        administrators.add(USER);
        fail(USER, ADDRESS, 4);

        // Execute system under test & verify result.
        assertTrue(manager.hasUsernameBlock(USER), "the per-username limit must hold a block for the username");
        assertTrue(manager.isLockedOut(USER), "a username blocked by the per-username limit must count as locked out");
        clock.advance(LOCKOUT_DURATION);
        assertFalse(manager.hasUsernameBlock(USER), "the block must end after the lockout duration");
        assertFalse(manager.isLockedOut(USER), "a username whose block has ended must not count as locked out");
    }

    /**
     * Verifies that blocks of a username from one IP address and of an IP address do not count as locked out, as they
     * do not apply to the account from everywhere.
     */
    @Test
    public void testPairAndAddressBlocksAreNotLockedOut()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(3L);
        fail(USER, ADDRESS, 2);
        fail(OTHER_USER, OTHER_ADDRESS, 3);

        // Execute system under test & verify result.
        assertFalse(manager.isLoginAllowed(USER, ADDRESS), "the pair must be blocked");
        assertFalse(manager.isLoginAllowed(OTHER_USER, OTHER_ADDRESS), "the address must be blocked");
        assertFalse(manager.isLockedOut(USER), "a block from one IP address must not count as locked out");
        assertFalse(manager.isLockedOut(OTHER_USER), "a block of an IP address must not count as locked out");
        assertFalse(manager.hasUsernameBlock(USER), "the per-username limit must hold no block");
    }

    /**
     * Verifies that no username block is reported while the limits are disabled or the per-username limit is off, as
     * a block left in the cache then refuses nothing.
     */
    @Test
    public void testUsernameBlockIsNotReportedWhileOff()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);
        administrators.add(USER);
        fail(USER, ADDRESS, 4);

        // Execute system under test & verify result.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(0L);
        assertFalse(manager.hasUsernameBlock(USER), "no block must be reported while the per-username limit is off");
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);
        UserLoginLimitManager.ENABLED.setValue(false);
        assertFalse(manager.hasUsernameBlock(USER), "no block must be reported while the limits are disabled");
        assertFalse(manager.isLockedOut(USER), "a username must not count as locked out by a block while the limits are disabled");
    }

    /**
     * Verifies that a lock set by the per-username limit is recognised as such, so that the user's Lock Out page can
     * say where it came from, and that the username's block in force is returned with its end and failure count.
     */
    @Test
    public void testLockSetByLoginLimitsIsRecognised()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);
        fail(USER, ADDRESS, 4);
        final ArgumentCaptor<Date> start = ArgumentCaptor.forClass(Date.class);
        final ArgumentCaptor<Date> end = ArgumentCaptor.forClass(Date.class);
        verify(lockOutManager, times(1).description("the attempt after the limit must lock the account")).disableAccount(eq(USER), start.capture(), end.capture());
        final LockOutFlag flag = new LockOutFlag(USER, start.getValue(), end.getValue());

        // Execute system under test.
        final Optional<UserLoginLimitManager.ActiveBlock> block = manager.getUsernameBlock("Alice");
        final boolean setByLimits = manager.isLockSetByLoginLimits(flag);

        // Verify result.
        assertTrue(block.isPresent(), "the per-username limit must hold a block for the username");
        assertEquals(end.getValue().toInstant(), block.get().endsAt(), "the block must end when the lock does");
        assertEquals(3L, block.get().failureCount(), "the block must give the number of attempts that led to it");
        assertTrue(setByLimits, "a lock set by the per-username limit must be recognised as set by the login limits");
    }

    /**
     * Verifies that a lock not set by the login limits is not taken for one: a lock set by hand while the username is
     * blocked (which the limits leave in place), a lock without an end, and any lock once the block has ended or while
     * the limits are disabled.
     */
    @Test
    public void testOtherLocksAreNotTakenForLoginLimitLocks()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);
        administrators.add(USER);
        fail(USER, ADDRESS, 4);
        final Date blockEnd = Date.from(manager.getUsernameBlock(USER).orElseThrow().endsAt());

        // Execute system under test & verify result.
        assertFalse(manager.isLockSetByLoginLimits(new LockOutFlag(USER, null, null)), "a lock without an end must not be taken for one set by the login limits");
        assertFalse(manager.isLockSetByLoginLimits(new LockOutFlag(USER, null, Date.from(blockEnd.toInstant().plus(Duration.ofHours(1))))), "a lock ending at another time must not be taken for one set by the login limits");
        assertFalse(manager.isLockSetByLoginLimits(new LockOutFlag(OTHER_USER, null, blockEnd)), "a lock of a username without a block must not be taken for one set by the login limits");
        UserLoginLimitManager.ENABLED.setValue(false);
        assertFalse(manager.isLockSetByLoginLimits(new LockOutFlag(USER, null, blockEnd)), "no lock must be taken for one set by the login limits while they are disabled");
        UserLoginLimitManager.ENABLED.setValue(true);
        clock.advance(LOCKOUT_DURATION);
        assertTrue(manager.getUsernameBlock(USER).isEmpty(), "no block must be returned once it has ended");
        assertFalse(manager.isLockSetByLoginLimits(new LockOutFlag(USER, null, blockEnd)), "no lock must be taken for one set by the login limits once the block has ended");
    }

    /**
     * Verifies that the blocks for one username are its tier 1 block from each IP address and its tier 3 block, found
     * whichever form of the username is given, and that another username's blocks are left out.
     */
    @Test
    public void testBlocksForUsernameListsEveryBlockOfThatUsername()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(1L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(2L);
        fail(USER, ADDRESS, 1);
        clock.advance(Duration.ofMinutes(1));
        fail("Alice", OTHER_ADDRESS, 1);
        clock.advance(Duration.ofMinutes(1));
        fail(USER, THIRD_ADDRESS, 1); // Reaches tier 3.
        fail(OTHER_USER, ADDRESS, 1);

        // Execute system under test.
        final UserLoginLimitManager.BlocksForUsername result = manager.getBlocksForUsername("alice@" + XMPP_DOMAIN, 10);

        // Verify result.
        assertNotNull(result.fromAnyAddress(), "the username's block from any IP address must be returned");
        assertEquals(UserLoginLimitManager.Tier.USERNAME, result.fromAnyAddress().tier(), "the block from any IP address must be the tier 3 block");
        assertEquals(2L, result.fromAnyAddress().failureCount(), "the block from any IP address must give the attempts that led to it");
        assertEquals(2, result.fromAddressTotal(), "every block of the username from one IP address must be counted");
        assertEquals(List.of(OTHER_ADDRESS, ADDRESS), result.fromAddresses().stream().map(UserLoginLimitManager.ActiveBlock::address).toList(),
            "the username's blocks from one IP address must be listed, the latest to end first, without another username's");
        assertTrue(result.fromAddresses().stream().allMatch(b -> USER.equals(b.username())), "only blocks of the username must be listed");
        assertEquals(0, result.refusingAddressTotal(), "no IP address block refused the username");
    }

    /**
     * Verifies that the blocks of one username from one IP address are bounded, the latest to end first, with the total.
     */
    @Test
    public void testBlocksForUsernameAreBounded()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(1L);
        for (int i = 0; i < 5; i++) {
            fail(USER, "198.51.100." + i, 1);
            clock.advance(Duration.ofSeconds(1));
        }

        // Execute system under test.
        final UserLoginLimitManager.BlocksForUsername result = manager.getBlocksForUsername(USER, 2);

        // Verify result.
        assertEquals(5, result.fromAddressTotal(), "the total must count every block of the username from one IP address");
        assertEquals(List.of("198.51.100.4", "198.51.100.3"), result.fromAddresses().stream().map(UserLoginLimitManager.ActiveBlock::address).toList(),
            "only the requested number of blocks must be listed, the latest to end first");
        assertNull(result.fromAnyAddress(), "no block from any IP address applies");
    }

    /**
     * Verifies that a username longer than is shown is found, and its blocks show it shortened and marked as such.
     */
    @Test
    public void testBlocksForUsernameShowShortenedUsername()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(1L);
        final String longer = "b".repeat(65);
        fail(longer, ADDRESS, 1);

        // Execute system under test.
        final UserLoginLimitManager.BlocksForUsername result = manager.getBlocksForUsername(longer, 10);

        // Verify result.
        assertEquals(1, result.fromAddresses().size(), "the long username's block must be found");
        assertEquals("b".repeat(64), result.fromAddresses().get(0).username(), "the username must be shortened to 64 characters");
        assertTrue(result.fromAddresses().get(0).usernameTruncated(), "the username must be marked as shortened");
    }

    /**
     * Verifies that a count at a limit lowered since its last attempt, which refuses the username from that IP address,
     * is returned with the username's blocks.
     */
    @Test
    public void testBlocksForUsernameIncludeCountOverLoweredLimit()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(3L);
        fail(USER, ADDRESS, 2);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);

        // Execute system under test.
        final UserLoginLimitManager.BlocksForUsername result = manager.getBlocksForUsername(USER, 10);

        // Verify result.
        assertEquals(1, result.fromAddresses().size(), "a count at a lowered limit must be returned as a block");
        assertEquals(2L, result.fromAddresses().get(0).failureCount(), "the block must give the attempts counted");
        assertEquals(clock.instant().plus(PAIR_INTERVAL), result.fromAddresses().get(0).endsAt(), "the block must end with its window");
    }

    /**
     * Verifies that no blocks are returned for a username while the feature is disabled.
     */
    @Test
    public void testBlocksForUsernameAreEmptyWhileDisabled()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(1L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(1L);
        fail(USER, ADDRESS, 2);
        UserLoginLimitManager.ENABLED.setValue(false);

        // Execute system under test.
        final UserLoginLimitManager.BlocksForUsername result = manager.getBlocksForUsername(USER, 10);

        // Verify result.
        assertNull(result.fromAnyAddress(), "no block from any IP address must be returned while disabled");
        assertTrue(result.fromAddresses().isEmpty(), "no block from one IP address must be returned while disabled");
        assertEquals(0, result.fromAddressTotal(), "no block from one IP address must be counted while disabled");
    }

    /**
     * Verifies that a username's blocks include the IP address blocks that refused it, and not those that did not.
     */
    @Test
    public void testBlocksForUsernameIncludeAddressBlocksThatRefusedIt()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(1L);
        fail("carol", ADDRESS, 1); // Reaches the limit for the address.
        fail("carol", OTHER_ADDRESS, 1);
        fail("Alice", ADDRESS, 1); // Refused by the address block.

        // Execute system under test.
        final UserLoginLimitManager.BlocksForUsername alice = manager.getBlocksForUsername(USER, 10);
        final UserLoginLimitManager.BlocksForUsername bob = manager.getBlocksForUsername(OTHER_USER, 10);

        // Verify result.
        assertEquals(1, alice.refusingAddressTotal(), "the IP address block that refused the username must be counted");
        assertEquals(List.of(ADDRESS), alice.refusingAddresses().stream().map(UserLoginLimitManager.ActiveBlock::address).toList(),
            "only the IP address block that refused the username must be listed");
        assertEquals(UserLoginLimitManager.Tier.ADDRESS, alice.refusingAddresses().get(0).tier(), "the block must be an IP address block");
        assertEquals(0, bob.refusingAddressTotal(), "no IP address block refused the other username");
    }

    /**
     * Verifies that an IP address block records the usernames it refused, newest first and each once, and not the
     * username of the attempt that reached the limit, which was admitted.
     */
    @Test
    public void testAddressBlockRecordsRefusedUsernamesNewestFirstWithoutDuplicates()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(1L);
        fail("carol", ADDRESS, 1);
        fail(USER, ADDRESS, 1);
        fail(OTHER_USER, ADDRESS, 1);
        fail("Alice", ADDRESS, 1);

        // Execute system under test.
        final List<UserLoginLimitManager.RefusedUsername> refused = manager.getBlocksByAddress(10, 10).addresses().get(0).refusedUsernames();

        // Verify result.
        assertEquals(List.of(USER, OTHER_USER), refused.stream().map(UserLoginLimitManager.RefusedUsername::username).toList(),
            "the refused usernames must be listed newest first, each once, normalised");
    }

    /**
     * Verifies that an IP address block records at most a fixed number of refused usernames, keeping the newest.
     */
    @Test
    public void testAddressBlockRecordsAtMostTenRefusedUsernames()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(1L);
        fail("carol", ADDRESS, 1);
        for (int i = 0; i < 12; i++) {
            fail("user" + i, ADDRESS, 1);
        }

        // Execute system under test.
        final List<UserLoginLimitManager.RefusedUsername> refused = manager.getBlocksByAddress(10, 10).addresses().get(0).refusedUsernames();

        // Verify result.
        assertEquals(10, UserLoginLimitManager.MAX_REFUSED_USERNAMES, "an address block must record at most ten refused usernames");
        assertEquals(List.of("user11", "user10", "user9", "user8", "user7", "user6", "user5", "user4", "user3", "user2"),
            refused.stream().map(UserLoginLimitManager.RefusedUsername::username).toList(), "only the newest refused usernames must be kept");
    }

    /**
     * Verifies that a refused attempt for the username an IP address block refused last does not write the block again,
     * so that a client retrying one username from a blocked address adds no cluster traffic.
     */
    @Test
    public void testRepeatedRefusalOfNewestUsernameDoesNotWriteBlock()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(1L);
        final Cache<String, UserLoginLimitManager.AddressBlock> spiedBlocks = spy(addressBlocks);
        manager = new UserLoginLimitManager(pairFailures, addressFailures, usernameFailures, unknownPairFailures, unknownUsernameFailures, pairBlocks, spiedBlocks, usernameBlocks,
            () -> lockOutManager, () -> lockOutProvider, existingUsers::contains, administrators::contains, () -> securityAuditManager, () -> XMPP_DOMAIN, clock);
        fail("carol", ADDRESS, 1); // Starts the block: one write.
        fail(USER, ADDRESS, 1); // Records the refused username: one write.

        // Execute system under test.
        fail(USER, ADDRESS, 1);
        fail("Alice", ADDRESS, 1);

        // Verify result.
        verify(spiedBlocks, times(2).description("refusing the newest refused username again must not write the block")).put(anyString(), any());
    }

    /**
     * Verifies that a refused username is recorded in the form shown to administrators: shortened and marked as such.
     */
    @Test
    public void testAddressBlockRecordsShortenedRefusedUsername()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(1L);
        fail("carol", ADDRESS, 1);
        fail("d".repeat(65), ADDRESS, 1);

        // Execute system under test.
        final UserLoginLimitManager.RefusedUsername refused = manager.getBlocksByAddress(10, 10).addresses().get(0).refusedUsernames().get(0);

        // Verify result.
        assertEquals("d".repeat(64), refused.username(), "the refused username must be shortened to 64 characters");
        assertTrue(refused.usernameTruncated(), "the refused username must be marked as shortened");
    }

    /**
     * Verifies that the attempt that starts an IP address block over a lowered limit, which it refuses, is recorded.
     */
    @Test
    public void testLoweredAddressLimitRecordsTheRefusedUsername()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(3L);
        fail("carol", ADDRESS, 2);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(2L);

        // Execute system under test.
        fail(USER, ADDRESS, 1);

        // Verify result.
        assertEquals(List.of(USER), manager.getBlocksByAddress(10, 10).addresses().get(0).refusedUsernames().stream().map(UserLoginLimitManager.RefusedUsername::username).toList(),
            "the attempt that starts the block over a lowered limit must be recorded as refused");
    }

    /**
     * Verifies that an IP address block with refused usernames survives serialisation and accounts for them in its size.
     */
    @Test
    public void testAddressBlockWithRefusedUsernamesSurvivesSerialisation() throws Exception
    {
        // Setup test fixture.
        final List<UserLoginLimitManager.RefusedUsername> refused = List.of(new UserLoginLimitManager.RefusedUsername("digest", "alice", false));
        final UserLoginLimitManager.AddressBlock block = new UserLoginLimitManager.AddressBlock("key", UserLoginLimitManager.Tier.ADDRESS, null, false, ADDRESS, 1L, 2L, 3L, refused);
        final UserLoginLimitManager.AddressBlock without = new UserLoginLimitManager.AddressBlock("key", UserLoginLimitManager.Tier.ADDRESS, null, false, ADDRESS, 1L, 2L, 3L);

        // Execute system under test.
        final UserLoginLimitManager.AddressBlock copy = roundTrip(block);

        // Verify result.
        assertEquals(refused, copy.refused(), "the refused usernames must survive serialisation");
        assertTrue(block.getCachedSize() > without.getCachedSize(), "the refused usernames must count towards the block's cached size");
    }

    /**
     * Verifies that blocks are grouped by IP address, the latest to end first: for each address its IP address block,
     * if any, and its blocks for one username, the latest to end first.
     */
    @Test
    public void testBlocksByAddressGroupsBlocksLatestEndingFirst()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(1L);
        fail(USER, ADDRESS, 1);
        clock.advance(Duration.ofMinutes(1));
        fail(OTHER_USER, ADDRESS, 1);
        clock.advance(Duration.ofMinutes(1));
        fail(USER, OTHER_ADDRESS, 1);
        clock.advance(Duration.ofMinutes(1));
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(1L);
        fail("carol", THIRD_ADDRESS, 1); // Blocks carol from the address, and the address for every username.
        final Instant addressBlockEnd = clock.instant().plus(ADDRESS_BLOCK_DURATION);

        // Execute system under test.
        final UserLoginLimitManager.BlocksByAddress result = manager.getBlocksByAddress(10, 10);

        // Verify result.
        assertEquals(3, result.total(), "every IP address with a block must be counted");
        assertEquals(List.of(THIRD_ADDRESS, OTHER_ADDRESS, ADDRESS), result.addresses().stream().map(UserLoginLimitManager.BlocksForAddress::address).toList(),
            "the IP addresses must be listed, the latest block to end first");
        final UserLoginLimitManager.BlocksForAddress third = result.addresses().get(0);
        assertNotNull(third.addressBlock(), "the IP address block must be returned");
        assertEquals(1L, third.addressBlock().failureCount(), "the IP address block must give the attempts that led to it");
        assertEquals(addressBlockEnd, third.latestEnd(), "the latest end must be that of the IP address block");
        assertEquals(List.of("carol"), third.usernameBlocks().stream().map(UserLoginLimitManager.ActiveBlock::username).toList(),
            "the blocks for one username from the address must be returned alongside its IP address block");
        final UserLoginLimitManager.BlocksForAddress first = result.addresses().get(2);
        assertNull(first.addressBlock(), "an IP address without an IP address block must have none");
        assertEquals(2, first.usernameTotal(), "every username blocked from the address must be counted");
        assertEquals(List.of(OTHER_USER, USER), first.usernameBlocks().stream().map(UserLoginLimitManager.ActiveBlock::username).toList(),
            "the usernames blocked from the address must be listed, the latest to end first");
        assertEquals(first.usernameBlocks().get(0).endsAt(), first.latestEnd(), "the latest end must be that of the latest block");
    }

    /**
     * Verifies that the number of IP addresses and of usernames listed for each is bounded, the latest to end first,
     * with the totals.
     */
    @Test
    public void testBlocksByAddressAreBounded()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(1L);
        for (int i = 0; i < 5; i++) {
            fail(USER, "198.51.100." + i, 1);
            clock.advance(Duration.ofSeconds(1));
        }
        for (int i = 0; i < 4; i++) {
            fail("user" + i, ADDRESS, 1);
            clock.advance(Duration.ofSeconds(1));
        }

        // Execute system under test.
        final UserLoginLimitManager.BlocksByAddress result = manager.getBlocksByAddress(2, 2);

        // Verify result.
        assertEquals(6, result.total(), "every IP address with a block must be counted");
        assertEquals(List.of(ADDRESS, "198.51.100.4"), result.addresses().stream().map(UserLoginLimitManager.BlocksForAddress::address).toList(),
            "only the requested number of IP addresses must be listed, the latest to end first");
        assertEquals(4, result.addresses().get(0).usernameTotal(), "every username blocked from the address must be counted");
        assertEquals(List.of("user3", "user2"), result.addresses().get(0).usernameBlocks().stream().map(UserLoginLimitManager.ActiveBlock::username).toList(),
            "only the requested number of usernames must be listed, the latest to end first");
    }

    /**
     * Verifies that blocks grouped by IP address keep the shortened form of a long username, and include counts at a
     * lowered limit, which refuse until their window ends.
     */
    @Test
    public void testBlocksByAddressShowShortenedUsernamesAndCountsOverLoweredLimit()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(1L);
        fail("b".repeat(65), ADDRESS, 1);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(0L); // So that both attempts below are counted for the address.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(3L);
        fail(USER, OTHER_ADDRESS, 2);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(2L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(1L);

        // Execute system under test.
        final UserLoginLimitManager.BlocksByAddress result = manager.getBlocksByAddress(10, 10);

        // Verify result.
        final UserLoginLimitManager.BlocksForAddress address = result.addresses().stream().filter(a -> a.address().equals(ADDRESS)).findFirst().orElseThrow();
        assertEquals("b".repeat(64), address.usernameBlocks().get(0).username(), "the username must be shortened to 64 characters");
        assertTrue(address.usernameBlocks().get(0).usernameTruncated(), "the username must be marked as shortened");
        final UserLoginLimitManager.BlocksForAddress other = result.addresses().stream().filter(a -> a.address().equals(OTHER_ADDRESS)).findFirst().orElseThrow();
        assertNotNull(other.addressBlock(), "a count at a lowered IP address limit must be returned as an IP address block");
        assertEquals(2L, other.addressBlock().failureCount(), "the block must give the attempts counted");
    }

    /**
     * Verifies that nothing is grouped by IP address while the feature is disabled.
     */
    @Test
    public void testBlocksByAddressAreEmptyWhileDisabled()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(1L);
        fail(USER, ADDRESS, 1);
        UserLoginLimitManager.ENABLED.setValue(false);

        // Execute system under test.
        final UserLoginLimitManager.BlocksByAddress result = manager.getBlocksByAddress(10, 10);

        // Verify result.
        assertEquals(0, result.total(), "no IP address must be counted while disabled");
        assertTrue(result.addresses().isEmpty(), "no IP address must be listed while disabled");
    }

    /**
     * Verifies that clearing a username's blocks lifts its block from every IP address, its block from any IP address
     * and the lock the login limits set, and leaves another username's blocks alone.
     */
    @Test
    public void testClearBlocksForUsernameLiftsLockSetByLoginLimits()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);
        fail(USER, ADDRESS, 2);
        fail(USER, OTHER_ADDRESS, 2); // The second reaches tier 3, which locks the account.
        fail(OTHER_USER, ADDRESS, 2);
        final ArgumentCaptor<Date> start = ArgumentCaptor.forClass(Date.class);
        final ArgumentCaptor<Date> end = ArgumentCaptor.forClass(Date.class);
        verify(lockOutManager, times(1).description("the attempt after the limit must lock the account")).disableAccount(eq(USER), start.capture(), end.capture());
        when(lockOutManager.getDisabledStatus(USER)).thenReturn(new LockOutFlag(USER, start.getValue(), end.getValue()));

        // Execute system under test.
        final UserLoginLimitManager.ClearedBlocks result = manager.clearBlocksForUsername("Alice");

        // Verify result.
        verify(lockOutManager, times(1).description("the lock the login limits set must be lifted")).enableAccount(USER);
        assertTrue(result.lockLifted(), "the result must say the lock was lifted");
        assertFalse(result.lockLiftFailed(), "lifting the lock must not be reported as failed");
        assertEquals(List.of(UserLoginLimitManager.Tier.USERNAME_FROM_ADDRESS, UserLoginLimitManager.Tier.USERNAME),
            result.blocks().stream().map(UserLoginLimitManager.ActiveBlock::tier).sorted().toList(), "both blocks of the username must be returned as cleared");
        assertTrue(manager.getUsernameBlock(USER).isEmpty(), "the block from any IP address must be cleared");
        assertTrue(manager.isLoginAllowed(USER, ADDRESS), "the block from the IP address must be cleared");
        assertFalse(manager.isLoginAllowed(OTHER_USER, ADDRESS), "another username's block must be left in force");
    }

    /**
     * Verifies that clearing a username's blocks never lifts a lock set by hand, although it clears the blocks.
     */
    @Test
    public void testClearBlocksForUsernameLeavesLockSetByHand()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);
        administrators.add(USER); // Blocked without a lock.
        fail(USER, ADDRESS, 4);
        when(lockOutManager.getDisabledStatus(USER)).thenReturn(new LockOutFlag(USER, null, null));

        // Execute system under test.
        final UserLoginLimitManager.ClearedBlocks result = manager.clearBlocksForUsername(USER);

        // Verify result.
        verify(lockOutManager, never().description("a lock set by hand must not be lifted")).enableAccount(anyString());
        assertFalse(result.lockLifted(), "the result must not say a lock was lifted");
        assertEquals(List.of(UserLoginLimitManager.Tier.USERNAME), result.blocks().stream().map(UserLoginLimitManager.ActiveBlock::tier).toList(),
            "the block from any IP address must be cleared");
        assertTrue(manager.getUsernameBlock(USER).isEmpty(), "the block from any IP address must be gone");
    }

    /**
     * Verifies that when the lock the login limits set cannot be lifted, the block from any IP address is kept, so that
     * the lock is still recognised as set by the login limits, and the failure is reported.
     */
    @Test
    public void testClearBlocksForUsernameKeepsBlockWhenLockCannotBeLifted()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);
        fail(USER, ADDRESS, 2);
        fail(USER, OTHER_ADDRESS, 2);
        final ArgumentCaptor<Date> end = ArgumentCaptor.forClass(Date.class);
        verify(lockOutManager).disableAccount(eq(USER), any(), end.capture());
        when(lockOutManager.getDisabledStatus(USER)).thenReturn(new LockOutFlag(USER, null, end.getValue()));
        doThrow(new UnsupportedOperationException()).when(lockOutManager).enableAccount(USER);

        // Execute system under test.
        final UserLoginLimitManager.ClearedBlocks result = manager.clearBlocksForUsername(USER);

        // Verify result.
        assertFalse(result.lockLifted(), "the result must not say the lock was lifted");
        assertTrue(result.lockLiftFailed(), "the result must say lifting the lock failed");
        assertTrue(manager.getUsernameBlock(USER).isPresent(), "the block from any IP address must be kept while the lock stays");
        assertTrue(manager.getBlocksForUsername(USER, 10).fromAddresses().isEmpty(), "the blocks from one IP address must still be cleared");
        assertEquals(List.of(UserLoginLimitManager.Tier.USERNAME_FROM_ADDRESS), result.blocks().stream().map(UserLoginLimitManager.ActiveBlock::tier).toList(),
            "only the block from the IP address must be returned as cleared");
    }

    /**
     * Verifies that clearing a username's login limits resets its failure counts from every IP address and from any
     * IP address, although none has reached its limit, and leaves another username's counts alone.
     */
    @Test
    public void testClearBlocksForUsernameResetsCountsFromEveryAddressAndFromAnyAddress()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(5L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(10L);
        fail(USER, ADDRESS, 2);
        fail(USER, OTHER_ADDRESS, 2);
        fail(OTHER_USER, ADDRESS, 1);

        // Execute system under test.
        final UserLoginLimitManager.ClearedBlocks result = manager.clearBlocksForUsername("Alice");

        // Verify result.
        assertTrue(result.blocks().isEmpty(), "no block was in force, so none must be returned as cleared");
        assertTrue(result.countsReset(), "the result must say the username's failure counts were reset");
        assertEquals(1, pairFailures.size(), "only another username's count from one IP address must be left");
        assertTrue(pairFailures.keySet().stream().noneMatch(key -> key.startsWith(OTHER_ADDRESS)), "the username's counts from every IP address must be reset");
        assertEquals(1, usernameFailures.size(), "only another username's count from any IP address must be left");
        assertFalse(manager.getBlocksForUsername(USER, 10).counted(), "nothing must be counted for the username any more");
        assertTrue(manager.getBlocksForUsername(OTHER_USER, 10).counted(), "another username's counts must be left");
    }

    /**
     * Verifies that clearing a username's block from one IP address also resets its count from any IP address, so
     * that one more wrong password does not reach that limit at once.
     */
    @Test
    public void testClearBlocksForUsernameResetsCountFromAnyAddressWithBlockFromOneAddress()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(2L);
        fail(USER, ADDRESS, 2); // Blocks the username from the address; the count from any IP address is at its limit.

        // Execute system under test.
        final UserLoginLimitManager.ClearedBlocks result = manager.clearBlocksForUsername(USER);
        fail(USER, OTHER_ADDRESS, 1);

        // Verify result.
        assertEquals(List.of(UserLoginLimitManager.Tier.USERNAME_FROM_ADDRESS), result.blocks().stream().map(UserLoginLimitManager.ActiveBlock::tier).toList(),
            "the block from the IP address must be returned as cleared");
        assertTrue(result.countsReset(), "the result must say the count from any IP address was reset");
        verify(lockOutManager, never().description("one more failure after the clear must not lock the account")).disableAccount(anyString(), any(), any());
        assertTrue(manager.getUsernameBlock(USER).isEmpty(), "one more failure after the clear must not block the username from any IP address");
    }

    /**
     * Verifies that clearing a username's login limits leaves an IP address block, which refuses everyone at that
     * address, and the counts of IP addresses, in place.
     */
    @Test
    public void testClearBlocksForUsernameLeavesAddressBlockAndAddressCounts()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(5L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(3L);
        fail(USER, ADDRESS, 3); // Blocks the address.
        fail(USER, OTHER_ADDRESS, 1);

        // Execute system under test.
        final UserLoginLimitManager.ClearedBlocks result = manager.clearBlocksForUsername(USER);

        // Verify result.
        assertTrue(result.blocks().isEmpty(), "an IP address block must not be returned as cleared");
        assertTrue(result.countsReset(), "the username's counts from each IP address must be reset");
        assertTrue(pairFailures.isEmpty(), "the username's counts from each IP address must be gone");
        assertFalse(manager.isLoginAllowed(USER, ADDRESS), "the IP address block must still refuse the username");
        assertEquals(1, addressBlocks.size(), "the IP address block must be left in place");
        assertEquals(1, addressFailures.size(), "the count of another IP address must be left in place");
    }

    /**
     * Verifies that when the lock the login limits set cannot be lifted, the block from any IP address is kept, while
     * the counts from each IP address are still reset.
     */
    @Test
    public void testClearBlocksForUsernameResetsCountsFromEachAddressWhenLockCannotBeLifted()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(10L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);
        fail(USER, ADDRESS, 4); // The fourth locks the account.
        final ArgumentCaptor<Date> end = ArgumentCaptor.forClass(Date.class);
        verify(lockOutManager).disableAccount(eq(USER), any(), end.capture());
        when(lockOutManager.getDisabledStatus(USER)).thenReturn(new LockOutFlag(USER, null, end.getValue()));
        doThrow(new UnsupportedOperationException()).when(lockOutManager).enableAccount(USER);

        // Execute system under test.
        final UserLoginLimitManager.ClearedBlocks result = manager.clearBlocksForUsername(USER);

        // Verify result.
        assertTrue(result.lockLiftFailed(), "the result must say lifting the lock failed");
        assertTrue(manager.getUsernameBlock(USER).isPresent(), "the block from any IP address must be kept while the lock stays");
        assertTrue(pairFailures.isEmpty(), "the counts from each IP address must still be reset");
    }

    /**
     * Verifies that unlocking an account the login limits locked clears the user's login limits as Clear login limits
     * does: the lock is lifted and the user's counts from each IP address are reset, so that one more wrong password
     * from an IP address that had failures counted does not block the user again at once.
     */
    @Test
    public void testUnlockAccountSetByLoginLimitsResetsCountsFromEachAddress()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(3L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);
        fail(USER, ADDRESS, 2);
        fail(USER, OTHER_ADDRESS, 2); // The fourth failure from any IP address locks the account.
        final ArgumentCaptor<Date> start = ArgumentCaptor.forClass(Date.class);
        final ArgumentCaptor<Date> end = ArgumentCaptor.forClass(Date.class);
        verify(lockOutManager, times(1).description("the attempt after the limit must lock the account")).disableAccount(eq(USER), start.capture(), end.capture());
        when(lockOutManager.getDisabledStatus(USER)).thenReturn(new LockOutFlag(USER, start.getValue(), end.getValue()));

        // Execute system under test.
        final Optional<UserLoginLimitManager.ClearedBlocks> result = manager.unlockAccount(USER);
        fail(USER, ADDRESS, 1);

        // Verify result.
        verify(lockOutManager, times(1).description("the lock the login limits set must be lifted once")).enableAccount(USER);
        assertTrue(result.isPresent(), "the result must say the login limits were cleared");
        assertTrue(result.get().lockLifted(), "the result must say the lock was lifted");
        assertTrue(result.get().countsReset(), "the result must say the user's failure counts were reset");
        assertTrue(manager.isLoginAllowed(USER, ADDRESS), "one more wrong password after the unlock must not block the user from that IP address");
        assertTrue(manager.getUsernameBlock(USER).isEmpty(), "the block from any IP address must be cleared");
    }

    /**
     * Verifies that unlocking an account locked by hand lifts the lock and nothing else: the login limits' counts and
     * blocks for the user are left as they are.
     */
    @Test
    public void testUnlockAccountLockedByHandLeavesLoginLimits()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(3L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(10L);
        fail(USER, ADDRESS, 2);
        fail(OTHER_USER, ADDRESS, 3); // Blocks another user from the address.
        when(lockOutManager.getDisabledStatus(USER)).thenReturn(new LockOutFlag(USER, null, null));

        // Execute system under test.
        final Optional<UserLoginLimitManager.ClearedBlocks> result = manager.unlockAccount(USER);

        // Verify result.
        verify(lockOutManager, times(1).description("a lock set by hand must be lifted")).enableAccount(USER);
        assertTrue(result.isEmpty(), "the result must not say the login limits were cleared");
        assertEquals(1, pairFailures.size(), "the user's count from the IP address must be left in place");
        assertEquals(1, pairBlocks.size(), "another user's block from the IP address must be left in place");
        assertTrue(manager.getBlocksForUsername(USER, 10).counted(), "the user's counts must be left in place");
        fail(USER, ADDRESS, 1);
        assertFalse(manager.isLoginAllowed(USER, ADDRESS), "the count kept from the IP address must still reach its limit");
    }

    /**
     * Verifies that unlocking an account the login limits locked never lifts a block of an IP address for all
     * usernames, which concerns everyone at that address.
     */
    @Test
    public void testUnlockAccountSetByLoginLimitsLeavesAddressBlock()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(3L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);
        fail(OTHER_USER, ADDRESS, 3); // Blocks the address for all usernames.
        fail(USER, OTHER_ADDRESS, 2);
        fail(USER, THIRD_ADDRESS, 2); // The fourth failure from any IP address locks the account.
        final ArgumentCaptor<Date> end = ArgumentCaptor.forClass(Date.class);
        verify(lockOutManager).disableAccount(eq(USER), any(), end.capture());
        when(lockOutManager.getDisabledStatus(USER)).thenReturn(new LockOutFlag(USER, null, end.getValue()));

        // Execute system under test.
        final Optional<UserLoginLimitManager.ClearedBlocks> result = manager.unlockAccount(USER);

        // Verify result.
        assertTrue(result.isPresent() && result.get().lockLifted(), "the lock the login limits set must be lifted");
        assertEquals(1, addressBlocks.size(), "the IP address block must be left in place");
        assertFalse(manager.isLoginAllowed(USER, ADDRESS), "the IP address block must still refuse the user");
    }

    /**
     * Verifies that when the lock the login limits set cannot be lifted, the unlock reports the failure and does not
     * try to lift the lock a second time.
     */
    @Test
    public void testUnlockAccountReportsLockThatCannotBeLifted()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(3L);
        fail(USER, ADDRESS, 4);
        final ArgumentCaptor<Date> end = ArgumentCaptor.forClass(Date.class);
        verify(lockOutManager).disableAccount(eq(USER), any(), end.capture());
        when(lockOutManager.getDisabledStatus(USER)).thenReturn(new LockOutFlag(USER, null, end.getValue()));
        doThrow(new UnsupportedOperationException()).when(lockOutManager).enableAccount(USER);

        // Execute system under test.
        final Optional<UserLoginLimitManager.ClearedBlocks> result = manager.unlockAccount(USER);

        // Verify result.
        assertTrue(result.isPresent() && result.get().lockLiftFailed(), "the result must say lifting the lock failed");
        verify(lockOutManager, times(1).description("a lock that could not be lifted must not be lifted again")).enableAccount(USER);
        assertTrue(manager.getUsernameBlock(USER).isPresent(), "the block from any IP address must be kept while the lock stays");
    }

    /**
     * Verifies that whether failures are counted for a username, from one IP address or from any, is reported with
     * its blocks, so that the user's page can offer to reset them.
     */
    @Test
    public void testBlocksForUsernameSaysWhetherFailuresAreCounted()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(5L);
        fail(USER, ADDRESS, 1);

        // Execute system under test.
        final boolean counted = manager.getBlocksForUsername(USER, 10).counted();
        final boolean otherCounted = manager.getBlocksForUsername(OTHER_USER, 10).counted();
        clock.advance(PAIR_INTERVAL.plusSeconds(1));
        final boolean countedAfterWindow = manager.getBlocksForUsername(USER, 10).counted();

        // Verify result.
        assertTrue(counted, "a failure counted from one IP address must be reported");
        assertFalse(otherCounted, "a username without failures must not be reported as counted");
        assertFalse(countedAfterWindow, "a count whose window has ended must not be reported");
    }

    /**
     * Verifies that a count from any IP address alone is reported as counted.
     */
    @Test
    public void testBlocksForUsernameSaysCountedFromAnyAddress()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(5L);
        fail(USER, null, 1);

        // Execute system under test.
        final UserLoginLimitManager.BlocksForUsername result = manager.getBlocksForUsername(USER, 10);

        // Verify result.
        assertTrue(result.counted(), "a failure counted from any IP address must be reported");
        assertNull(result.fromAnyAddress(), "a count below its limit must not be listed as a block");
    }

    /**
     * Verifies that clearing a username's login limits clears nothing while the feature is disabled.
     */
    @Test
    public void testClearBlocksForUsernameClearsNothingWhileDisabled()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(1L);
        fail(USER, ADDRESS, 1);
        UserLoginLimitManager.ENABLED.setValue(false);

        // Execute system under test.
        final UserLoginLimitManager.ClearedBlocks result = manager.clearBlocksForUsername(USER);

        // Verify result.
        assertTrue(result.blocks().isEmpty(), "nothing must be cleared while disabled");
        assertFalse(result.countsReset(), "nothing must be reset while disabled");
        UserLoginLimitManager.ENABLED.setValue(true);
        assertFalse(manager.isLoginAllowed(USER, ADDRESS), "the block must still be in force");
    }

    /**
     * Verifies that clearing an IP address's blocks lifts its IP address block and its blocks for each username, and
     * leaves other addresses' blocks alone.
     */
    @Test
    public void testClearBlocksForAddressLiftsEveryBlockFromThatAddress()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(1L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(2L);
        fail(USER, ADDRESS, 1);
        fail(OTHER_USER, ADDRESS, 1); // Also reaches the limit for the address.
        fail(USER, OTHER_ADDRESS, 1);

        // Execute system under test.
        final List<UserLoginLimitManager.ActiveBlock> cleared = manager.clearBlocksForAddress(ADDRESS);

        // Verify result.
        assertEquals(List.of(UserLoginLimitManager.Tier.USERNAME_FROM_ADDRESS, UserLoginLimitManager.Tier.USERNAME_FROM_ADDRESS, UserLoginLimitManager.Tier.ADDRESS),
            cleared.stream().map(UserLoginLimitManager.ActiveBlock::tier).sorted().toList(), "every block from the address must be returned as cleared");
        assertTrue(cleared.stream().allMatch(b -> ADDRESS.equals(b.address())), "only blocks from the address must be cleared");
        assertTrue(manager.isLoginAllowed(USER, ADDRESS), "the blocks from the address must be lifted");
        assertTrue(manager.isLoginAllowed(OTHER_USER, ADDRESS), "the blocks from the address must be lifted for every username");
        assertFalse(manager.isLoginAllowed(USER, OTHER_ADDRESS), "another address's block must be left in force");
    }

    /**
     * Verifies that an IPv6 address's blocks are cleared by its /64, as listed, or by any address within it.
     */
    @Test
    public void testClearBlocksForAddressAcceptsIpv6PrefixOrAddress()
    {
        // Setup test fixture.
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(1L);
        fail(USER, "2001:db8::1", 1);
        fail(OTHER_USER, "2001:db8::2", 1);

        // Execute system under test.
        final List<UserLoginLimitManager.ActiveBlock> byPrefix = manager.clearBlocksForAddress("2001:db8::/64");
        fail(USER, "2001:db8::1", 1);
        final List<UserLoginLimitManager.ActiveBlock> byAddress = manager.clearBlocksForAddress("2001:db8::99");

        // Verify result.
        assertEquals(2, byPrefix.size(), "both blocks from the /64 must be cleared by the prefix");
        assertEquals(1, byAddress.size(), "the block from the /64 must be cleared by an address within it");
        assertTrue(manager.isLoginAllowed(USER, "2001:db8::1"), "no block from the /64 must remain");
    }

    /**
     * Returns every block in force, ordered by tier and then by when it ends.
     */
    private List<UserLoginLimitManager.ActiveBlock> activeBlocks()
    {
        return Arrays.stream(UserLoginLimitManager.Tier.values()).flatMap(tier -> manager.activeBlocks(tier).stream())
            .sorted(Comparator.comparing(UserLoginLimitManager.ActiveBlock::tier).thenComparing(UserLoginLimitManager.ActiveBlock::endsAt))
            .toList();
    }

    private static List<UserLoginLimitManager.ActiveBlock> withoutKeys(final List<UserLoginLimitManager.ActiveBlock> blocks)
    {
        return blocks.stream().map(b -> new UserLoginLimitManager.ActiveBlock(b.tier(), b.username(), b.usernameTruncated(), b.address(), b.failureCount(), b.endsAt(), NO_KEY)).toList();
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(final T value) throws Exception
    {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (final ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        try (final ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) in.readObject();
        }
    }

    /**
     * A clock that tests can move forward.
     */
    private static final class MutableClock extends Clock
    {
        private Instant now;

        MutableClock(final Instant now)
        {
            this.now = now;
        }

        void advance(final Duration duration)
        {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone()
        {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(final java.time.ZoneId zone)
        {
            return this;
        }

        @Override
        public Instant instant()
        {
            return now;
        }
    }
}
