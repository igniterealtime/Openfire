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

package org.jivesoftware.openfire.net;

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.auth.AuthFactoryTest;
import org.jivesoftware.openfire.auth.AuthorizationManager;
import org.jivesoftware.openfire.auth.UserLoginLimitManager;
import org.jivesoftware.openfire.lockout.LockOutManager;
import org.jivesoftware.openfire.sasl.SaslServerPlainImpl;
import org.jivesoftware.openfire.sasl.VerifyPasswordCallback;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.util.cache.CacheFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.sasl.SaslException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Verifies SASL PLAIN logins that give the username with this server's domain ('alice@domain'), which the default
 * authentication provider accepts: such a login succeeds with the right password, is refused for a locked account,
 * and with the user login limits on, is not counted as a failure once it succeeds.
 *
 * @author Matthew Vivian
 */
public class PlainUserAtDomainLoginTest
{
    private static final String DOMAIN = Fixtures.XMPP_DOMAIN;
    private static final String PASSWORD = "secret";
    private static final String ADDRESS = "192.0.2.77";

    private Class<?> originalProvider;
    private boolean originalEnabled;
    private long originalMaxPair;
    private long originalMaxUsername;

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
        CacheFactory.initialize();
    }

    @BeforeEach
    public void setUp()
    {
        XMPPServer.setInstance(Fixtures.mockXMPPServer());
        originalProvider = AuthFactory.AUTH_PROVIDER.getValue();
        AuthFactory.AUTH_PROVIDER.setValue(AuthFactoryTest.DomainStrippingAuthProvider.class);
        AuthFactoryTest.DomainStrippingAuthProvider.calls.clear();
        clearLoginLimits();
        originalEnabled = UserLoginLimitManager.ENABLED.getValue();
        originalMaxPair = UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.getValue();
        originalMaxUsername = UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.getValue();
    }

    @AfterEach
    public void tearDown()
    {
        clearLoginLimits();
        UserLoginLimitManager.ENABLED.setValue(originalEnabled);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(originalMaxPair);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(originalMaxUsername);
        AuthFactory.AUTH_PROVIDER.setValue((Class) originalProvider);
    }

    /**
     * Clears the counts and blocks of this test's username and IP address in the shared login limits, so that neither
     * this test nor another that uses the singleton sees counts left behind.
     */
    private static void clearLoginLimits()
    {
        final UserLoginLimitManager loginLimits = UserLoginLimitManager.getInstance();
        loginLimits.clearBlock(UserLoginLimitManager.Tier.USERNAME_FROM_ADDRESS, "alice", ADDRESS);
        loginLimits.clearBlock(UserLoginLimitManager.Tier.ADDRESS, null, ADDRESS);
        loginLimits.clearBlock(UserLoginLimitManager.Tier.USERNAME, "alice", null);
    }

    /**
     * Returns a session mock that keeps session data, as the login limits store an attempt's reservation there.
     */
    private static LocalClientSession sessionAt(final String address) throws Exception
    {
        final Map<String, Object> data = new HashMap<>();
        final LocalClientSession session = mock(LocalClientSession.class);
        when(session.getHostAddress()).thenReturn(address);
        when(session.setSessionData(anyString(), any())).thenAnswer(i -> data.put(i.getArgument(0), i.getArgument(1)));
        when(session.getSessionData(anyString())).thenAnswer(i -> data.get(i.<String>getArgument(0)));
        when(session.removeSessionData(anyString())).thenAnswer(i -> data.remove(i.<String>getArgument(0)));
        return session;
    }

    private static boolean verify(final XMPPCallbackHandler handler, final String username, final String password) throws Exception
    {
        final VerifyPasswordCallback callback = new VerifyPasswordCallback(password.toCharArray());
        handler.handle(new Callback[] {new NameCallback("PLAIN authentication ID: ", username), callback});
        return callback.getVerified();
    }

    /**
     * Verifies that the PLAIN mechanism completes for a username with this server's domain and the right password.
     * Authorisation (which looks the account up in the user store) is stubbed to accept alice; this test is about the
     * password check that precedes it.
     */
    @Test
    public void testPlainSucceedsForUsernameWithServerDomain() throws Exception
    {
        try (final MockedStatic<AuthorizationManager> authorization = mockStatic(AuthorizationManager.class)) {
            // Setup test fixture.
            authorization.when(() -> AuthorizationManager.map("alice@" + DOMAIN)).thenReturn("alice");
            authorization.when(() -> AuthorizationManager.authorize("alice", "alice@" + DOMAIN)).thenReturn(true);
            final SaslServerPlainImpl server = new SaslServerPlainImpl("xmpp", DOMAIN, Collections.emptyMap(), new XMPPCallbackHandler());

            // Execute system under test.
            server.evaluateResponse(("\0alice@" + DOMAIN + "\0" + PASSWORD).getBytes(StandardCharsets.UTF_8));

            // Verify result.
            assertTrue(server.isComplete(), "a PLAIN login as alice@domain with the right password must complete");
            assertEquals("alice", server.getAuthorizationID(), "the login must be for the account alice");
        }
    }

    /**
     * Verifies that the callback handler verifies the password of a username with this server's domain, and refuses a
     * wrong one.
     */
    @Test
    public void testCallbackHandlerVerifiesUsernameWithServerDomain() throws Exception
    {
        // Execute system under test & verify result.
        assertTrue(verify(new XMPPCallbackHandler(), "alice@" + DOMAIN, PASSWORD), "the right password for alice@domain must be verified");
        assertFalse(verify(new XMPPCallbackHandler(), "alice@" + DOMAIN, "wrong"), "a wrong password for alice@domain must not be verified");
    }

    /**
     * Verifies that a locked account is refused over PLAIN when the username is given with this server's domain.
     */
    @Test
    public void testLockedAccountIsRefusedForUsernameWithServerDomain() throws Exception
    {
        // Setup test fixture.
        final LockOutManager lockOutManager = mock(LockOutManager.class);
        when(lockOutManager.isAccountDisabled("alice")).thenReturn(true);
        try (final MockedStatic<LockOutManager> lockOutStatic = mockStatic(LockOutManager.class)) {
            lockOutStatic.when(LockOutManager::getInstance).thenReturn(lockOutManager);
            final SaslServerPlainImpl server = new SaslServerPlainImpl("xmpp", DOMAIN, Collections.emptyMap(), new XMPPCallbackHandler());

            // Execute system under test & verify result.
            assertThrows(SaslException.class, () -> server.evaluateResponse(("\0alice@" + DOMAIN + "\0" + PASSWORD).getBytes(StandardCharsets.UTF_8)),
                "a locked account must be refused when its username is given with the domain");
            assertFalse(server.isComplete(), "a refused login must not complete");
            assertTrue(AuthFactoryTest.DomainStrippingAuthProvider.calls.isEmpty(), "the password of a locked account must not be checked");
        }
    }

    /**
     * Verifies that with the login limits on, a successful login as alice@domain gives its attempt back, as a
     * successful login as alice does: three such logins with limits of two attempts neither block alice from the
     * address nor lock the account.
     */
    @Test
    public void testSuccessfulLoginWithServerDomainLeavesNoCount() throws Exception
    {
        // Setup test fixture.
        UserLoginLimitManager.ENABLED.setValue(true);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(2L);
        final UserLoginLimitManager loginLimits = UserLoginLimitManager.getInstance();

        for (int i = 0; i < 3; i++) {
            final LocalClientSession session = sessionAt(ADDRESS);

            // Execute system under test: the exchange, then what SASLAuthentication does on its success.
            assertTrue(verify(new XMPPCallbackHandler(session), "alice@" + DOMAIN, PASSWORD), "login " + (i + 1) + " as alice@domain must succeed");
            final Object reservation = session.removeSessionData(SASLAuthentication.SASL_LOGIN_ATTEMPT);
            assertTrue(reservation instanceof UserLoginLimitManager.Reservation, "the attempt must have been reserved against the limits");
            loginLimits.recordSuccessfulLogin((UserLoginLimitManager.Reservation) reservation);

            // Verify result.
            assertTrue(loginLimits.isLoginAllowed("alice", ADDRESS), "a successful login as alice@domain must leave no count against alice from the IP address");
            assertTrue(loginLimits.isLoginAllowed("alice", "192.0.2.78"), "a successful login as alice@domain must leave no count against alice from any IP address");
        }
    }
}
