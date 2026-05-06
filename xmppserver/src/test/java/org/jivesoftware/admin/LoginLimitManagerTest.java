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

import org.jivesoftware.openfire.security.SecurityAuditManager;
import org.jivesoftware.util.TaskEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.AdditionalMatchers.and;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class LoginLimitManagerTest {

    // Should correspond to LoginLimitManager.MAX_ATTEMPTS_PER_PAIR, LoginLimitManager.MAX_ATTEMPTS_PER_USERNAME, and LoginLimitManager.MAX_ATTEMPTS_PER_IP.
    private static final int MAX_PAIR = 10;
    private static final int MAX_USERNAME = 10;
    private static final int MAX_IP = 40;

    private LoginLimitManager loginLimitManager;

    @Mock private SecurityAuditManager securityAuditManager;
    @Mock private TaskEngine taskEngine;

    @BeforeEach
    public void setUp() {
        loginLimitManager = new LoginLimitManager(securityAuditManager, taskEngine);
    }

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

    @Test
    public void lockoutsWillBeAudited()
    {
        // Setup test fixture.
        final String username = "test-user-c";
        final String address = "a.b.c.f";

        // Execute system under test.
        for(int i = 0; i < MAX_PAIR + 1; i ++) {
            loginLimitManager.recordFailedAttempt(username, address);
        }

        // Verify result.
        verify(securityAuditManager, times(MAX_PAIR)).logEvent(
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

    @Test
    public void successfulLoginDoesNotClearOtherUserOnSameIP()
    {
        // Setup test fixture.
        final String firstUsername = "test-user-e";
        final String secondUsername = "test-user-f";
        final String address = "a.b.c.i";

        for (int i = 0; i < MAX_PAIR + 1; i++) {
            loginLimitManager.recordFailedAttempt(firstUsername, address);
            loginLimitManager.recordFailedAttempt(secondUsername, address);
        }

        assertTrue(loginLimitManager.hasHitConnectionLimit(firstUsername, address));
        assertTrue(loginLimitManager.hasHitConnectionLimit(secondUsername, address));

        // Execute system under test.
        loginLimitManager.recordSuccessfulAttempt(firstUsername, address);

        // Verify result.
        assertFalse(loginLimitManager.hasHitConnectionLimit(firstUsername, address));
        assertTrue(loginLimitManager.hasHitConnectionLimit(secondUsername, address));
    }

    @Test
    public void successfulLoginStillSubjectToSecondaryIPGate()
    {
        // Setup test fixture.
        final String username = "test-user-g";
        final String address = "a.b.c.j";
        final String otherAddress = "a.b.c.k";

        for (int i = 0; i < MAX_PAIR + 1; i++) {
            loginLimitManager.recordFailedAttempt(username, address);
        }

        for (int i = 0; i < MAX_IP + 1; i++) {
            loginLimitManager.recordFailedAttempt("other-user-" + i, address);
        }

        assertTrue(loginLimitManager.hasHitConnectionLimit(username, address));

        // Execute system under test.
        loginLimitManager.recordSuccessfulAttempt(username, address);

        // Verify result.
        assertTrue(loginLimitManager.hasHitConnectionLimit(username, address));
        assertFalse(loginLimitManager.hasHitConnectionLimit(username, otherAddress)); // Username lockout is cleared globally, but the shared IP gate can still block this address.
    }

    @Test
    public void perPairLockoutAtExactThreshold()
    {
        // Setup test fixture.
        final String username = "test-user-h";
        final String address = "a.b.c.l";

        int i;
        for (i = 1; i <= MAX_PAIR; i++)
        {
            // Execute system under test.
            loginLimitManager.recordFailedAttempt(username, address);

            // Verify result.
            assertFalse(loginLimitManager.hasHitConnectionLimit(username, address), "Should not be locked out at attempt " + i + " (within limit of " + MAX_PAIR + ")");
        }

        // Next attempt should trigger lockout (> semantics).

        // Execute system under test.
        loginLimitManager.recordFailedAttempt(username, address);

        // Verify result.
        assertTrue(loginLimitManager.hasHitConnectionLimit(username, address), "Should be locked out after " + i + " attempts (exceeds limit of " + MAX_PAIR + ")");
    }

    @Test
    public void perUsernameLockoutAtExactThreshold()
    {
        // Setup test fixture.
        final String username = "test-user-i";
        final String address = "a.b.c.m";

        int i;
        for (i = 1; i <= MAX_USERNAME; i++)
        {
            // Execute system under test.
            loginLimitManager.recordFailedAttempt(username, address);

            // Verify result.
            assertFalse(loginLimitManager.hasHitConnectionLimit(username, address), "Should not be locked out at attempt " + i + " (within limit of " + MAX_USERNAME + ")");
        }

        // 11th attempt should trigger lockout.

        // Execute system under test.
        loginLimitManager.recordFailedAttempt(username, address);
        i++;

        // Verify result.
        assertTrue(loginLimitManager.hasHitConnectionLimit(username, address), "Should be locked out after " + i + " attempts (exceeds limit of " + MAX_USERNAME + ")");
    }

    @Test
    public void perIPLockoutAtExactThreshold()
    {
        // Setup test fixture.
        final String address = "a.b.c.n";

        int i;
        for (i = 1; i <= MAX_IP; i++) {
            final String username = "test-user-j-" + i;

            // Execute system under test.
            loginLimitManager.recordFailedAttempt(username, address);

            // Verify result.
            assertFalse(loginLimitManager.hasHitConnectionLimit(username, address), "User " + i + " should not be locked out at attempt " + i + " (within limit of " + MAX_IP + ")");
        }

        // Next attempt (MAX_IP + 1) should trigger per-IP lockout.
        final String username = "test-user-j-" + i;

        // Execute system under test.
        loginLimitManager.recordFailedAttempt(username, address);

        // Verify result.
        assertTrue(loginLimitManager.hasHitConnectionLimit(username, address), "Should be locked out after " + i + " attempts (exceeds limit of " + MAX_IP + ")");
    }

    @Test
    public void gatesCanActivateSimultaneously()
    {
        // Setup test fixture.
        final String username = "test-user-k";
        final String address = "a.b.c.o";

        // Execute system under test.

        // Trigger at least two gates at once.
        for (int i = 0; i < Math.max(MAX_USERNAME + 1, MAX_PAIR + 1); i++) {
            loginLimitManager.recordFailedAttempt(username, address);
        }

        // Verify result.
        assertTrue(loginLimitManager.hasHitConnectionLimit(username, address), "When multiple gates are triggered, access should be denied.");
    }

    @Test
    public void pairGateDoesNotBlockDifferentUsername()
    {
        // Setup test fixture.
        final String firstUsername = "test-user-l";
        final String secondUsername = "test-user-m";
        final String address = "192.168.1.1"; // Shared address (e.g., NAT, reverse proxy)

        // Execute system under test.

        // User 1 hits pair lockout on this address.
        for (int i = 0; i < MAX_PAIR + 1; i++) {
            loginLimitManager.recordFailedAttempt(firstUsername, address);
        }

        assertTrue(loginLimitManager.hasHitConnectionLimit(firstUsername, address), "User 1 should be locked out");

        // User 2 is not locked out (separate pair key).
        assertFalse(loginLimitManager.hasHitConnectionLimit(secondUsername, address), "User 2 should not be blocked by User 1's pair lockout");

        // But once User 2 accumulates 11 failed attempts on the same address, User 2 also locked out.
        for (int i = 0; i < MAX_PAIR + 1; i++) {
            loginLimitManager.recordFailedAttempt(secondUsername, address);
        }

        assertTrue(loginLimitManager.hasHitConnectionLimit(secondUsername, address), "User 2 should also be locked out");
    }
}
