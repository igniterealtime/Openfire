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
import org.jivesoftware.openfire.lockout.LockOutManager;
import org.jivesoftware.openfire.sasl.SaslServerPlainImpl;
import org.jivesoftware.openfire.sasl.VerifyPasswordCallback;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Verifies SASL PLAIN logins that give the username with this server's domain ('alice@domain'), which the default
 * authentication provider accepts: such a login succeeds with the right password, and is refused for a locked
 * account.
 *
 * @author Matthew Vivian
 */
public class PlainUserAtDomainLoginTest
{
    private static final String DOMAIN = Fixtures.XMPP_DOMAIN;
    private static final String PASSWORD = "secret";

    private Class<?> originalProvider;

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
    }

    @AfterEach
    public void tearDown()
    {
        AuthFactory.AUTH_PROVIDER.setValue((Class) originalProvider);
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
}
