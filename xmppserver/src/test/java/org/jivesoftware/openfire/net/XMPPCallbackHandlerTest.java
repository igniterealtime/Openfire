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
import org.jivesoftware.openfire.auth.AuthorizationManager;
import org.jivesoftware.openfire.auth.UserLoginLimitManager;
import org.jivesoftware.openfire.session.LocalSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.RealmChoiceCallback;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link XMPPCallbackHandler} applies the user login limits (OF-2279) when a mechanism asks it for the
 * username, using DIGEST-MD5 (the JDK's implementation, which reads the password through the callback handler).
 *
 * @author Matthew Vivian
 */
public class XMPPCallbackHandlerTest
{
    private static final String USER = "alice";
    private static final String PASSWORD = "secret";
    private static final String ADDRESS = "192.0.2.1";

    private MockedStatic<UserLoginLimitManager> loginLimitsStatic;
    private MockedStatic<AuthFactory> authFactory;
    private MockedStatic<AuthorizationManager> authorizationManager;
    private UserLoginLimitManager loginLimits;
    private LocalSession session;

    @BeforeAll
    public static void setupClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    public void setup() throws Exception
    {
        XMPPServer.setInstance(Fixtures.mockXMPPServer());

        loginLimits = mock(UserLoginLimitManager.class);
        loginLimitsStatic = mockStatic(UserLoginLimitManager.class);
        loginLimitsStatic.when(UserLoginLimitManager::getInstance).thenReturn(loginLimits);
        when(loginLimits.tryAcquireAttempt(anyString(), any())).thenReturn(mock(UserLoginLimitManager.Reservation.class));

        authFactory = mockStatic(AuthFactory.class);
        authFactory.when(() -> AuthFactory.getPassword(USER)).thenReturn(PASSWORD);

        authorizationManager = mockStatic(AuthorizationManager.class);
        authorizationManager.when(() -> AuthorizationManager.map(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        authorizationManager.when(() -> AuthorizationManager.authorize(anyString(), anyString())).thenReturn(true);

        session = mock(LocalSession.class);
        when(session.getHostAddress()).thenReturn(ADDRESS);
    }

    @AfterEach
    public void tearDown()
    {
        authorizationManager.close();
        authFactory.close();
        loginLimitsStatic.close();
    }

    /**
     * Verifies the test set-up: an attempt that is not refused authenticates with the correct password, and its
     * username is recorded on the session for the failure and success handling.
     */
    @Test
    public void testDigestMd5AllowedAttemptAuthenticatesAndRecordsUsername() throws Exception
    {
        // Execute system under test.
        final SaslServer server = digestMd5Exchange(PASSWORD);

        // Verify result.
        assertTrue(server.isComplete(), "an attempt that is not refused must authenticate with the correct password");
        verify(session, times(1).description("the attempt's reservation must be recorded on the session")).setSessionData(eq(SASLAuthentication.SASL_LOGIN_ATTEMPT), any(UserLoginLimitManager.Reservation.class));
    }

    /**
     * Verifies that a refused DIGEST-MD5 attempt fails with the correct password and with a wrong one, without the
     * password being read.
     */
    @Test
    public void testDigestMd5RefusedAttemptFailsWithoutReadingPassword()
    {
        // Setup test fixture.
        when(loginLimits.tryAcquireAttempt(USER, ADDRESS)).thenReturn(null);

        // Execute system under test & verify result.
        assertThrows(SaslException.class, () -> digestMd5Exchange(PASSWORD), "a refused attempt must fail with the correct password");
        assertThrows(SaslException.class, () -> digestMd5Exchange("wrong"), "a refused attempt must fail with a wrong password");
        authFactory.verify(() -> AuthFactory.getPassword(anyString()), never().description("the password of a refused attempt must not be read"));
        verify(session, never().description("a refused attempt must not be recorded")).setSessionData(eq(SASLAuthentication.SASL_LOGIN_ATTEMPT), any());
    }

    /**
     * Verifies that a handler created without a session still refuses a locked or blocked username, and does not count
     * the attempt, as without a session a success could not give it back.
     */
    @Test
    public void testHandlerWithoutSessionStillRefuses()
    {
        // Setup test fixture.
        when(loginLimits.isAttemptAllowed(USER, null)).thenReturn(false);
        final XMPPCallbackHandler handler = new XMPPCallbackHandler();

        // Execute system under test & verify result.
        assertThrows(SaslException.class, () -> handler.handle(new Callback[]{new NameCallback("name", USER), new PasswordCallback("password", false)}), "a refused username must be refused without a session");
        authFactory.verify(() -> AuthFactory.getPassword(anyString()), never().description("the password of a refused attempt must not be read"));
        verify(loginLimits, never().description("without a session, an attempt must not be counted")).tryAcquireAttempt(any(), any());
    }

    /**
     * Verifies that a successful login through a handler without a session counts nothing.
     */
    @Test
    public void testHandlerWithoutSessionDoesNotCountSuccess() throws Exception
    {
        // Setup test fixture.
        when(loginLimits.isAttemptAllowed(USER, null)).thenReturn(true);
        final XMPPCallbackHandler handler = new XMPPCallbackHandler();
        final NameCallback name = new NameCallback("name", USER);
        final org.jivesoftware.openfire.sasl.VerifyPasswordCallback password = new org.jivesoftware.openfire.sasl.VerifyPasswordCallback(PASSWORD.toCharArray());
        authFactory.when(() -> AuthFactory.authenticate(USER, PASSWORD)).thenReturn(mock(org.jivesoftware.openfire.auth.AuthToken.class));

        // Execute system under test.
        handler.handle(new Callback[]{name, password});

        // Verify result.
        assertTrue(password.getVerified(), "the correct password must be verified without a session");
        verify(loginLimits, never().description("without a session, an attempt must not be counted")).tryAcquireAttempt(any(), any());
    }

    /**
     * Runs a DIGEST-MD5 exchange between the JDK's client and server, the server using an XMPPCallbackHandler.
     *
     * @return the server, after the exchange.
     */
    private SaslServer digestMd5Exchange(final String clientPassword) throws Exception
    {
        final Map<String, Object> props = new HashMap<>();
        props.put("com.sun.security.sasl.digest.realm", Fixtures.XMPP_DOMAIN);
        final SaslServer server = Sasl.createSaslServer("DIGEST-MD5", "xmpp", Fixtures.XMPP_DOMAIN, props, new XMPPCallbackHandler(session));
        final SaslClient client = Sasl.createSaslClient(new String[]{"DIGEST-MD5"}, null, "xmpp", Fixtures.XMPP_DOMAIN, null, callbacks -> {
            for (final Callback callback : callbacks) {
                if (callback instanceof NameCallback nameCallback) {
                    nameCallback.setName(USER);
                } else if (callback instanceof PasswordCallback passwordCallback) {
                    passwordCallback.setPassword(clientPassword.toCharArray());
                } else if (callback instanceof RealmCallback realmCallback) {
                    realmCallback.setText(Fixtures.XMPP_DOMAIN);
                } else if (callback instanceof RealmChoiceCallback realmChoiceCallback) {
                    realmChoiceCallback.setSelectedIndex(0);
                }
            }
        });

        final byte[] challenge = server.evaluateResponse(new byte[0]);
        final byte[] response = client.evaluateChallenge(challenge);
        server.evaluateResponse(response);
        return server;
    }
}
