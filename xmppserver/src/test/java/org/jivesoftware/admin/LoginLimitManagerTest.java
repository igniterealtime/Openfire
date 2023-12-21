/*
 * Copyright (C) 2018-2023 Ignite Realtime Foundation. All rights reserved.
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
        verify(securityAuditManager, times(1)).logEvent(username, "Failed admin console login attempt", "A failed login attempt to the admin console was made from address a.b.c.f. Future login attempts from this address will be temporarily locked out. Future login attempts for this user will be temporarily locked out. ");
    }
}
