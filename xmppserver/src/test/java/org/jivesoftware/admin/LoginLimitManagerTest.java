/*
 * Copyright (C) 2018-2026 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.admin;

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.security.SecurityAuditManager;
import org.jivesoftware.util.TaskEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.TimerTask;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.AdditionalMatchers.and;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link LoginLimitManager}.
 */
@ExtendWith(MockitoExtension.class)
public class LoginLimitManagerTest
{
    private static final long DEFAULT_MAX_PAIR = 10L;
    private static final long DEFAULT_MAX_USERNAME = 10L;
    private static final long DEFAULT_MAX_IP = 40L;

    private LoginLimitManager loginLimitManager;
    private Map<String, TimerTask> scheduledResetTasks;
    private long originalMaxAttemptsPerPair;
    private long originalMaxAttemptsPerUsername;
    private long originalMaxAttemptsPerIP;

    @Mock
    private SecurityAuditManager securityAuditManager;

    @Mock
    private TaskEngine taskEngine;

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    public void setUp()
    {
        // Capture the tasks registered by the system under test, so that they can be invoked immediately without waiting.
        scheduledResetTasks = new HashMap<>();
        doAnswer(invocation -> {
            final TimerTask task = invocation.getArgument(0);
            scheduledResetTasks.put(task.getClass().getSimpleName(), task);
            return null;
        }).when(taskEngine).scheduleAtFixedRate(any(TimerTask.class), any(Duration.class), any(Duration.class));

        originalMaxAttemptsPerPair = LoginLimitManager.MAX_ATTEMPTS_PER_PAIR.getValue();
        originalMaxAttemptsPerUsername = LoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.getValue();
        originalMaxAttemptsPerIP = LoginLimitManager.MAX_ATTEMPTS_PER_IP.getValue();

        LoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(DEFAULT_MAX_PAIR);
        LoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(DEFAULT_MAX_USERNAME);
        LoginLimitManager.MAX_ATTEMPTS_PER_IP.setValue(DEFAULT_MAX_IP);

        loginLimitManager = new LoginLimitManager(securityAuditManager, taskEngine);
    }

    @AfterEach
    public void tearDown()
    {
        LoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(originalMaxAttemptsPerPair);
        LoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(originalMaxAttemptsPerUsername);
        LoginLimitManager.MAX_ATTEMPTS_PER_IP.setValue(originalMaxAttemptsPerIP);
    }

    /**
     * Verifies that successful admin logins are written to the security audit log.
     */
    @Test
    public void aSuccessfulLoginWillBeAudited()
    {
        // Setup test fixture.
        final String username = "test-user-a";
        final String address = "a.b.c.d";

        // Execute system under test.
        loginLimitManager.recordSuccessfulAttempt(username, address);

        // Verify result.
        verify(securityAuditManager).logEvent(
            eq(username),
            eq("Successful admin console login attempt"),
            and(contains(address), not(contains("temporarily locked out")))
        );
    }

    /**
     * Verifies that failed admin logins are written to the security audit log.
     */
    @Test
    public void aFailedLoginWillBeAudited()
    {
        // Setup test fixture.
        final String username = "test-user-b";
        final String address = "a.b.c.e";

        // Execute system under test.
        loginLimitManager.recordFailedAttempt(username, address);

        // Verify result.
        verify(securityAuditManager).logEvent(
            eq(username),
            eq("Failed admin console login attempt"),
            and(contains(address), not(contains("temporarily locked out")))
        );
    }

    /**
     * Verifies that a lockout failure audit entry includes lockout wording.
     */
    @Test
    public void lockoutsWillBeAudited()
    {
        // Setup test fixture.
        final String username = "test-user-c";
        final String address = "a.b.c.f";
        LoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(1L);

        // Execute system under test.
        failAttempts(username, address, 2);

        // Verify result.
        verify(securityAuditManager, times(1)).logEvent(
            eq(username),
            eq("Failed admin console login attempt"),
            and(contains(address), not(contains("temporarily locked out")))
        );
        verify(securityAuditManager, times(1)).logEvent(
            eq(username),
            eq("Failed admin console login attempt"),
            and(contains(address), contains("temporarily locked out"))
        );
    }

    /**
     * Verifies that a successful login clears only that user's lockout for the shared IP.
     */
    @Test
    public void successfulLoginDoesNotClearOtherUserOnSameIP()
    {
        // Setup test fixture.
        final String firstUsername = "test-user-e";
        final String secondUsername = "test-user-f";
        final String address = "a.b.c.i";

        // Execute system under test.
        failAttempts(firstUsername, address, DEFAULT_MAX_PAIR + 1);
        failAttempts(secondUsername, address, DEFAULT_MAX_PAIR + 1);
        final boolean firstLockedBeforeSuccess = loginLimitManager.hasHitConnectionLimit(firstUsername, address);
        final boolean secondLockedBeforeSuccess = loginLimitManager.hasHitConnectionLimit(secondUsername, address);

        loginLimitManager.recordSuccessfulAttempt(firstUsername, address);

        final boolean firstLockedAfterSuccess = loginLimitManager.hasHitConnectionLimit(firstUsername, address);
        final boolean secondLockedAfterSuccess = loginLimitManager.hasHitConnectionLimit(secondUsername, address);

        // Verify result.
        assertTrue(firstLockedBeforeSuccess, "First user should be locked before successful login clears their counters.");
        assertTrue(secondLockedBeforeSuccess, "Second user should be locked before successful login of first user.");
        assertFalse(firstLockedAfterSuccess, "Successful login should clear lockout for the matching user/address pair.");
        assertTrue(secondLockedAfterSuccess, "Successful login of first user should not clear second user's lockout state.");
    }

    /**
     * Verifies that IP-based lockout still blocks after a successful login clears username and pair counters.
     */
    @Test
    public void successfulLoginStillSubjectToSecondaryIPGate()
    {
        // Setup test fixture.
        final String username = "test-user-g";
        final String address = "a.b.c.j";
        final String otherAddress = "a.b.c.k";

        // Execute system under test.
        failAttempts(username, address, DEFAULT_MAX_PAIR + 1);
        for (int i = 0; i < DEFAULT_MAX_IP + 1; i++) {
            loginLimitManager.recordFailedAttempt("other-user-" + i, address);
        }
        final boolean lockedBeforeSuccess = loginLimitManager.hasHitConnectionLimit(username, address);

        loginLimitManager.recordSuccessfulAttempt(username, address);

        final boolean lockedAfterSuccessOnSameAddress = loginLimitManager.hasHitConnectionLimit(username, address);
        final boolean lockedAfterSuccessOnOtherAddress = loginLimitManager.hasHitConnectionLimit(username, otherAddress);

        // Verify result.
        assertTrue(lockedBeforeSuccess, "User should be locked before the successful login is recorded.");
        assertTrue(lockedAfterSuccessOnSameAddress, "IP-based lockout should still deny access on the blocked address.");
        assertFalse(lockedAfterSuccessOnOtherAddress, "Successful login should clear username lockout globally for different addresses.");
    }

    /**
     * Verifies that the per-pair gate blocks only after exceeding its threshold.
     */
    @Test
    public void perPairLockoutAtExactThreshold()
    {
        // Setup test fixture.
        final String username = "test-user-h";
        final String address = "a.b.c.l";
        final String secondAddress = "a.b.c.l2";
        final long pairLimit = 3L;

        LoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(pairLimit);
        LoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(100L);

        // Execute system under test.
        final boolean[] withinLimitStatuses = new boolean[(int) pairLimit];
        for (int i = 1; i <= pairLimit; i++) {
            loginLimitManager.recordFailedAttempt(username, address);
            withinLimitStatuses[i - 1] = loginLimitManager.hasHitConnectionLimit(username, address);
        }

        loginLimitManager.recordFailedAttempt(username, address);
        final boolean lockedAfterLimitExceeded = loginLimitManager.hasHitConnectionLimit(username, address);
        final boolean lockedOnDifferentAddress = loginLimitManager.hasHitConnectionLimit(username, secondAddress);

        // Verify result.
        for (int i = 0; i < withinLimitStatuses.length; i++) {
            assertFalse(withinLimitStatuses[i], "Per-pair lockout should not trigger at or below attempt " + (i + 1) + ".");
        }
        assertTrue(lockedAfterLimitExceeded, "Per-pair lockout should trigger after exceeding the configured pair limit.");
        assertFalse(lockedOnDifferentAddress, "Per-pair lockout should not affect the same username from a different address.");
    }

    /**
     * Verifies that the per-username gate blocks after exceeding its threshold across addresses.
     */
    @Test
    public void perUsernameLockoutAtExactThreshold()
    {
        // Setup test fixture.
        final String username = "test-user-i";
        final String firstAddress = "a.b.c.m";
        final String secondAddress = "a.b.c.m2";
        final long usernameLimit = 3L;

        LoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(usernameLimit);
        LoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(100L);

        // Execute system under test.
        final boolean[] withinLimitStatuses = new boolean[(int) usernameLimit];
        for (int i = 1; i <= usernameLimit; i++) {
            final String currentAddress = i % 2 == 0 ? secondAddress : firstAddress;
            loginLimitManager.recordFailedAttempt(username, currentAddress);
            withinLimitStatuses[i - 1] = loginLimitManager.hasHitConnectionLimit(username, currentAddress);
        }

        loginLimitManager.recordFailedAttempt(username, firstAddress);
        final boolean lockedOnFirstAddress = loginLimitManager.hasHitConnectionLimit(username, firstAddress);
        final boolean lockedOnSecondAddress = loginLimitManager.hasHitConnectionLimit(username, secondAddress);
        final boolean otherUsernameLocked = loginLimitManager.hasHitConnectionLimit("other-username", firstAddress);

        // Verify result.
        for (int i = 0; i < withinLimitStatuses.length; i++) {
            assertFalse(withinLimitStatuses[i], "Per-username lockout should not trigger at or below attempt " + (i + 1) + ".");
        }
        assertTrue(lockedOnFirstAddress, "Per-username lockout should trigger after exceeding the username threshold.");
        assertTrue(lockedOnSecondAddress, "Per-username lockout should apply to the same username on other addresses.");
        assertFalse(otherUsernameLocked, "Per-username lockout should not affect unrelated usernames.");
    }

    /**
     * Verifies that the per-IP gate blocks only after exceeding its threshold.
     */
    @Test
    public void perIPLockoutAtExactThreshold()
    {
        // Setup test fixture.
        final String address = "a.b.c.n";
        final long ipLimit = 3L;

        LoginLimitManager.MAX_ATTEMPTS_PER_IP.setValue(ipLimit);
        LoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(100L);
        LoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(100L);

        // Execute system under test.
        final boolean[] withinLimitStatuses = new boolean[(int) ipLimit];
        for (int i = 1; i <= ipLimit; i++) {
            final String username = "test-user-j-" + i;
            loginLimitManager.recordFailedAttempt(username, address);
            withinLimitStatuses[i - 1] = loginLimitManager.hasHitConnectionLimit(username, address);
        }

        final String username = "test-user-j-" + (ipLimit + 1);
        loginLimitManager.recordFailedAttempt(username, address);
        final boolean lockedAfterLimitExceeded = loginLimitManager.hasHitConnectionLimit(username, address);

        // Verify result.
        for (int i = 0; i < withinLimitStatuses.length; i++) {
            assertFalse(withinLimitStatuses[i], "Per-IP lockout should not trigger at or below attempt " + (i + 1) + ".");
        }
        assertTrue(lockedAfterLimitExceeded, "Per-IP lockout should trigger after exceeding the IP threshold.");
    }

    /**
     * Verifies that pair lockout for one username does not block another username on the same IP.
     */
    @Test
    public void pairGateDoesNotBlockDifferentUsername()
    {
        // Setup test fixture.
        final String firstUsername = "test-user-l";
        final String secondUsername = "test-user-m";
        final String address = "192.168.1.1";

        // Execute system under test.
        failAttempts(firstUsername, address, DEFAULT_MAX_PAIR + 1);
        final boolean firstUserLockedBeforeSecondUserAttempts = loginLimitManager.hasHitConnectionLimit(firstUsername, address);
        final boolean secondUserLockedBeforeOwnAttempts = loginLimitManager.hasHitConnectionLimit(secondUsername, address);

        failAttempts(secondUsername, address, DEFAULT_MAX_PAIR + 1);
        final boolean secondUserLockedAfterOwnAttempts = loginLimitManager.hasHitConnectionLimit(secondUsername, address);

        // Verify result.
        assertTrue(firstUserLockedBeforeSecondUserAttempts, "First user should be locked after exceeding pair threshold.");
        assertFalse(secondUserLockedBeforeOwnAttempts, "Second user should not be blocked by first user's pair lockout.");
        assertTrue(secondUserLockedAfterOwnAttempts, "Second user should be locked only after their own attempts exceed pair threshold.");
    }

    /**
     * Verifies that only the IP gate causes lockout when username and pair thresholds are not reached.
     */
    @Test
    public void onlyIPGateCausesLockoutWhenOtherGatesRemainBelowThreshold()
    {
        // Setup test fixture.
        final String address = "172.16.0.10";
        final String otherAddress = "172.16.0.11";
        LoginLimitManager.MAX_ATTEMPTS_PER_IP.setValue(2L);
        LoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(100L);
        LoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(100L);

        // Execute system under test.
        loginLimitManager.recordFailedAttempt("ip-only-user-1", address);
        loginLimitManager.recordFailedAttempt("ip-only-user-2", address);
        loginLimitManager.recordFailedAttempt("ip-only-user-3", address);

        final boolean blockedOnAddress = loginLimitManager.hasHitConnectionLimit("ip-only-user-3", address);
        final boolean blockedOnOtherAddressForSameUser = loginLimitManager.hasHitConnectionLimit("ip-only-user-3", otherAddress);
        final boolean unrelatedUserBlockedOnOtherAddress = loginLimitManager.hasHitConnectionLimit("ip-only-unrelated", otherAddress);

        // Verify result.
        assertTrue(blockedOnAddress, "IP gate should block access on the saturated address.");
        assertFalse(blockedOnOtherAddressForSameUser, "Username gate should remain below threshold in this IP-only scenario.");
        assertFalse(unrelatedUserBlockedOnOtherAddress, "Pair gate should remain below threshold in this IP-only scenario.");
    }

    /**
     * Verifies that only the username gate causes lockout when IP and pair thresholds are not reached.
     */
    @Test
    public void onlyUsernameGateCausesLockoutWhenOtherGatesRemainBelowThreshold()
    {
        // Setup test fixture.
        final String username = "username-only-user";
        final String firstAddress = "172.16.1.10";
        final String secondAddress = "172.16.1.11";
        final String thirdAddress = "172.16.1.12";
        LoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(2L);
        LoginLimitManager.MAX_ATTEMPTS_PER_IP.setValue(100L);
        LoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(100L);

        // Execute system under test.
        loginLimitManager.recordFailedAttempt(username, firstAddress);
        loginLimitManager.recordFailedAttempt(username, secondAddress);
        loginLimitManager.recordFailedAttempt(username, thirdAddress);

        final boolean blockedForUsername = loginLimitManager.hasHitConnectionLimit(username, firstAddress);
        final boolean blockedForOtherUsernameOnSameAddress = loginLimitManager.hasHitConnectionLimit("other-user", firstAddress);

        // Verify result.
        assertTrue(blockedForUsername, "Username gate should block after username attempts exceed threshold across addresses.");
        assertFalse(blockedForOtherUsernameOnSameAddress, "IP and pair gates should remain below threshold in this username-only scenario.");
    }

    /**
     * Verifies that only the pair gate causes lockout when IP and username thresholds are not reached.
     */
    @Test
    public void onlyPairGateCausesLockoutWhenOtherGatesRemainBelowThreshold()
    {
        // Setup test fixture.
        final String username = "pair-only-user";
        final String address = "172.16.2.10";
        final String otherAddress = "172.16.2.11";
        LoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);
        LoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(100L);
        LoginLimitManager.MAX_ATTEMPTS_PER_IP.setValue(100L);

        // Execute system under test.
        loginLimitManager.recordFailedAttempt(username, address);
        loginLimitManager.recordFailedAttempt(username, address);
        loginLimitManager.recordFailedAttempt(username, address);

        final boolean blockedForPair = loginLimitManager.hasHitConnectionLimit(username, address);
        final boolean blockedForSameUserOtherAddress = loginLimitManager.hasHitConnectionLimit(username, otherAddress);
        final boolean blockedForOtherUserSameAddress = loginLimitManager.hasHitConnectionLimit("other-pair-user", address);

        // Verify result.
        assertTrue(blockedForPair, "Pair gate should block the saturated username/address combination.");
        assertFalse(blockedForSameUserOtherAddress, "Username gate should remain below threshold in this pair-only scenario.");
        assertFalse(blockedForOtherUserSameAddress, "IP gate should remain below threshold in this pair-only scenario.");
    }

    /**
     * Verifies that executing the scheduled username reset task clears username-wide lockouts.
     */
    @Test
    public void usernameResetTaskClearsUsernameLockouts()
    {
        // Setup test fixture.
        final String username = "test-user-reset-username";
        final String firstAddress = "10.0.0.1";
        final String secondAddress = "10.0.0.2";
        LoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(1L);
        LoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(100L);
        LoginLimitManager.MAX_ATTEMPTS_PER_IP.setValue(100L);

        // Execute system under test.
        loginLimitManager.recordFailedAttempt(username, firstAddress);
        loginLimitManager.recordFailedAttempt(username, secondAddress);
        final boolean lockedBeforeReset = loginLimitManager.hasHitConnectionLimit(username, firstAddress);
        runScheduledResetTask("PerUsernameTask");
        final boolean lockedAfterReset = loginLimitManager.hasHitConnectionLimit(username, firstAddress);

        // Verify result.
        assertTrue(lockedBeforeReset, "Username should be locked before the username reset task runs.");
        assertFalse(lockedAfterReset, "Username reset task should clear username lockout counters.");
    }

    /**
     * Verifies that executing the scheduled IP reset task clears IP-based lockouts.
     */
    @Test
    public void ipResetTaskClearsIPLockouts()
    {
        // Setup test fixture.
        final String address = "10.0.1.1";
        LoginLimitManager.MAX_ATTEMPTS_PER_IP.setValue(1L);
        LoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(100L);
        LoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(100L);

        // Execute system under test.
        loginLimitManager.recordFailedAttempt("test-user-reset-ip-a", address);
        loginLimitManager.recordFailedAttempt("test-user-reset-ip-b", address);
        final boolean lockedBeforeReset = loginLimitManager.hasHitConnectionLimit("test-user-reset-ip-a", address);
        runScheduledResetTask("PerIPAddressTask");
        final boolean lockedAfterReset = loginLimitManager.hasHitConnectionLimit("test-user-reset-ip-a", address);

        // Verify result.
        assertTrue(lockedBeforeReset, "Address should be locked before the IP reset task runs.");
        assertFalse(lockedAfterReset, "IP reset task should clear IP lockout counters.");
    }

    /**
     * Verifies that executing the scheduled pair reset task clears pair-specific lockouts.
     */
    @Test
    public void pairResetTaskClearsPairLockouts()
    {
        // Setup test fixture.
        final String username = "test-user-reset-pair";
        final String address = "10.0.2.1";
        LoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(1L);
        LoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(100L);
        LoginLimitManager.MAX_ATTEMPTS_PER_IP.setValue(100L);

        // Execute system under test.
        loginLimitManager.recordFailedAttempt(username, address);
        loginLimitManager.recordFailedAttempt(username, address);
        final boolean lockedBeforeReset = loginLimitManager.hasHitConnectionLimit(username, address);
        runScheduledResetTask("PerPairTask");
        final boolean lockedAfterReset = loginLimitManager.hasHitConnectionLimit(username, address);

        // Verify result.
        assertTrue(lockedBeforeReset, "Username/address pair should be locked before the pair reset task runs.");
        assertFalse(lockedAfterReset, "Pair reset task should clear pair-specific lockout counters.");
    }

    /**
     * Immediately executes a scheduled reset task.
     *
     * @param taskSimpleName the name of the task to run
     */
    private void runScheduledResetTask(final String taskSimpleName)
    {
        final TimerTask task = scheduledResetTasks.get(taskSimpleName);
        assertNotNull(task, "Expected TaskEngine to schedule reset task '" + taskSimpleName + "'.");
        task.run();
    }

    /**
     * Record a number of failed login attempts for a given username and address.
     *
     * @param username the username for which to record the attempts
     * @param address  the address for which to record the attempts
     * @param count    the amount of attempts to record
     */
    private void failAttempts(String username, String address, long count)
    {
        for (int i = 0; i < count; i++) {
            loginLimitManager.recordFailedAttempt(username, address);
        }
    }
}
