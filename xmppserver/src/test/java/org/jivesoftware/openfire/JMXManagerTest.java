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

package org.jivesoftware.openfire;

import org.jivesoftware.openfire.admin.AdminManager;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.auth.UserLoginLimitManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import javax.management.remote.JMXPrincipal;
import javax.security.auth.Subject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the JMX authenticator of {@link JMXManager}, including how it applies the user login limits (OF-2279): a JMX
 * login is counted against tier 3 only (the authenticator is not given the client's address), refused without its
 * password being checked when the account is locked or the username is blocked, and given back when it succeeds.
 *
 * @author Matthew Vivian
 */
public class JMXManagerTest
{
    private static final String USER = "admin";
    private static final String PASSWORD = "secret";

    private MockedStatic<UserLoginLimitManager> loginLimitsStatic;
    private MockedStatic<AuthFactory> authFactory;
    private MockedStatic<AdminManager> adminManagerStatic;
    private UserLoginLimitManager loginLimits;
    private UserLoginLimitManager.Reservation reservation;
    private AdminManager adminManager;

    @BeforeEach
    public void setUp()
    {
        loginLimits = mock(UserLoginLimitManager.class);
        reservation = mock(UserLoginLimitManager.Reservation.class);
        when(loginLimits.tryAcquireAttempt(anyString(), any())).thenReturn(reservation);
        loginLimitsStatic = mockStatic(UserLoginLimitManager.class);
        loginLimitsStatic.when(UserLoginLimitManager::getInstance).thenReturn(loginLimits);
        loginLimitsStatic.when(() -> UserLoginLimitManager.forLog(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        authFactory = mockStatic(AuthFactory.class);
        authFactory.when(() -> AuthFactory.authenticate(USER, PASSWORD)).thenReturn(mock(AuthToken.class));
        authFactory.when(() -> AuthFactory.authenticate(USER, "wrong")).thenThrow(new UnauthorizedException());

        adminManager = mock(AdminManager.class);
        when(adminManager.isUserAdmin(USER, true)).thenReturn(true);
        adminManagerStatic = mockStatic(AdminManager.class);
        adminManagerStatic.when(AdminManager::getInstance).thenReturn(adminManager);
    }

    @AfterEach
    public void tearDown()
    {
        adminManagerStatic.close();
        authFactory.close();
        loginLimitsStatic.close();
    }

    /**
     * Verifies that an administrator with the correct password is authenticated, and that the attempt is counted
     * against the username only and then given back.
     */
    @Test
    public void testSuccessIsCountedForUsernameOnlyAndGivenBack()
    {
        // Execute system under test.
        final Subject subject = JMXManager.authenticate(new String[]{USER, PASSWORD});

        // Verify result.
        assertEquals(USER, subject.getPrincipals(JMXPrincipal.class).iterator().next().getName(), "the subject must name the authenticated administrator");
        verify(loginLimits, times(1).description("the attempt must be counted without an address, so only the per-username limit applies")).tryAcquireAttempt(eq(USER), isNull());
        verify(loginLimits, times(1).description("a successful login must give its attempt back")).recordSuccessfulLogin(reservation);
    }

    /**
     * Verifies that a wrong password is refused and its attempt stays counted.
     */
    @Test
    public void testWrongPasswordStaysCounted()
    {
        // Execute system under test & verify result.
        assertThrows(SecurityException.class, () -> JMXManager.authenticate(new String[]{USER, "wrong"}), "a wrong password must be refused");
        verify(loginLimits, times(1).description("the attempt must be counted")).tryAcquireAttempt(eq(USER), isNull());
        verify(loginLimits, never().description("a failed attempt must not be given back")).recordSuccessfulLogin(any());
    }

    /**
     * Verifies that an attempt the login limits refuse (a locked account or a blocked username) is refused without
     * the password being checked, whatever the password.
     */
    @Test
    public void testRefusedAttemptIsRefusedWithoutCheckingPassword()
    {
        // Setup test fixture.
        when(loginLimits.tryAcquireAttempt(eq(USER), isNull())).thenReturn(null);

        // Execute system under test & verify result.
        assertThrows(SecurityException.class, () -> JMXManager.authenticate(new String[]{USER, PASSWORD}), "a refused attempt must be refused with the correct password");
        assertThrows(SecurityException.class, () -> JMXManager.authenticate(new String[]{USER, "wrong"}), "a refused attempt must be refused with a wrong password");
        authFactory.verify(() -> AuthFactory.authenticate(anyString(), anyString()), never().description("the password of a refused attempt must not be checked"));
        verify(loginLimits, never().description("a refused attempt must not be given back")).recordSuccessfulLogin(any());
    }

    /**
     * Verifies that a user who is not an administrator is refused even with the correct password; the attempt is given
     * back, as the password was right.
     */
    @Test
    public void testNonAdministratorIsRefused()
    {
        // Setup test fixture.
        when(adminManager.isUserAdmin(USER, true)).thenReturn(false);

        // Execute system under test & verify result.
        assertThrows(SecurityException.class, () -> JMXManager.authenticate(new String[]{USER, PASSWORD}), "a user who is not an administrator must be refused");
        verify(loginLimits, times(1).description("a correct password must give its attempt back")).recordSuccessfulLogin(reservation);
    }

    /**
     * Verifies that malformed credentials are refused before anything is counted.
     */
    @Test
    public void testMalformedCredentialsAreRefusedWithoutCounting()
    {
        // Execute system under test & verify result.
        assertThrows(SecurityException.class, () -> JMXManager.authenticate(null), "missing credentials must be refused");
        assertThrows(SecurityException.class, () -> JMXManager.authenticate("admin:secret"), "credentials that are not a String array must be refused");
        assertThrows(SecurityException.class, () -> JMXManager.authenticate(new String[]{USER}), "credentials without a password must be refused");
        assertThrows(SecurityException.class, () -> JMXManager.authenticate(new String[]{null, PASSWORD}), "credentials without a username must be refused");
        assertThrows(SecurityException.class, () -> JMXManager.authenticate(new String[]{USER, null}), "credentials with a null password must be refused");
        verify(loginLimits, never().description("malformed credentials must not be counted")).tryAcquireAttempt(any(), any());
    }
}
