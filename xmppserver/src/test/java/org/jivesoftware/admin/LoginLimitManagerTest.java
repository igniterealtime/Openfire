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
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.TaskEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class LoginLimitManagerTest {

    private LoginLimitManager loginLimitManager;

    @Mock private SecurityAuditManager securityAuditManager;
    @Mock private TaskEngine taskEngine;

    @BeforeEach
    public void setUp() {
        loginLimitManager = new LoginLimitManager(securityAuditManager, taskEngine);
    }

    @Test
    public void aSuccessfulLoginWillBeAudited() {

        final String username = "test-user-a-" + StringUtils.randomString(10);
        loginLimitManager.recordSuccessfulAttempt(username, "a.b.c.d");

        verify(securityAuditManager).logEvent(username, "Successful admin console login attempt", "The user logged in successfully to the admin console from address a.b.c.d. ");
    }

    @Test
    public void aFailedLoginWillBeAudited() {

        final String username = "test-user-b-" + StringUtils.randomString(10);
        loginLimitManager.recordFailedAttempt(username, "a.b.c.e");

        verify(securityAuditManager).logEvent(username, "Failed admin console login attempt", "A failed login attempt to the admin console was made from address a.b.c.e. ");
    }

    @Test
    public void lockoutsWillBeAudited() {

        final String username = "test-user-c-" + StringUtils.randomString(10);
        for(int i = 0; i < 11; i ++) {
            loginLimitManager.recordFailedAttempt(username, "a.b.c.f");
        }

        verify(securityAuditManager, times(10)).logEvent(username, "Failed admin console login attempt", "A failed login attempt to the admin console was made from address a.b.c.f. ");
        verify(securityAuditManager, times(1)).logEvent(username, "Failed admin console login attempt", "A failed login attempt to the admin console was made from address a.b.c.f. Future login attempts for this user will be temporarily locked out. Future login attempts for this user/address combination will be temporarily locked out. ");
    }

    @Test
    public void successfulLoginClearsUsernameFailuresAcrossAllIPs() {
        final String username = "test-user-d-" + StringUtils.randomString(10);
        final String firstAddress = "a.b.c.g";
        final String secondAddress = "a.b.c.h";

        for (int i = 0; i < 11; i++) {
            loginLimitManager.recordFailedAttempt(username, firstAddress);
            loginLimitManager.recordFailedAttempt(username, secondAddress);
        }

        assertTrue(loginLimitManager.hasHitConnectionLimit(username, firstAddress));
        assertTrue(loginLimitManager.hasHitConnectionLimit(username, secondAddress));

        loginLimitManager.recordSuccessfulAttempt(username, firstAddress);

        assertFalse(loginLimitManager.hasHitConnectionLimit(username, firstAddress));
        assertFalse(loginLimitManager.hasHitConnectionLimit(username, secondAddress));
    }

    @Test
    public void successfulLoginDoesNotClearOtherUserOnSameIP() {
        final String firstUsername = "test-user-e-" + StringUtils.randomString(10);
        final String secondUsername = "test-user-f-" + StringUtils.randomString(10);
        final String address = "a.b.c.i";

        for (int i = 0; i < 11; i++) {
            loginLimitManager.recordFailedAttempt(firstUsername, address);
            loginLimitManager.recordFailedAttempt(secondUsername, address);
        }

        assertTrue(loginLimitManager.hasHitConnectionLimit(firstUsername, address));
        assertTrue(loginLimitManager.hasHitConnectionLimit(secondUsername, address));

        loginLimitManager.recordSuccessfulAttempt(firstUsername, address);

        assertFalse(loginLimitManager.hasHitConnectionLimit(firstUsername, address));
        assertTrue(loginLimitManager.hasHitConnectionLimit(secondUsername, address));
    }

    @Test
    public void successfulLoginStillSubjectToSecondaryIPGate() {
        final String username = "test-user-g-" + StringUtils.randomString(10);
        final String address = "a.b.c.j";
        final String otherAddress = "a.b.c.k";

        for (int i = 0; i < 11; i++) {
            loginLimitManager.recordFailedAttempt(username, address);
        }

        for (int i = 0; i < 41; i++) {
            loginLimitManager.recordFailedAttempt("other-user-" + i + '-' + StringUtils.randomString(4), address);
        }

        assertTrue(loginLimitManager.hasHitConnectionLimit(username, address));
        loginLimitManager.recordSuccessfulAttempt(username, address);

        // Username lockout is cleared globally, but the shared IP gate can still block this address.
        assertTrue(loginLimitManager.hasHitConnectionLimit(username, address));
        assertFalse(loginLimitManager.hasHitConnectionLimit(username, otherAddress));
    }
}
