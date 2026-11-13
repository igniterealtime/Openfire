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

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.auth.AuthorizationManager;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.auth.UserLoginLimitManager;
import org.jivesoftware.openfire.lockout.LockOutManager;
import org.jivesoftware.openfire.sasl.SaslMechanismCatalog;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import javax.security.sasl.Sasl;
import javax.security.sasl.SaslServer;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.jivesoftware.openfire.net.SASLAuthentication.SASL2_NAMESPACE;
import static org.jivesoftware.openfire.net.SASLAuthentication.SASL_NAMESPACE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
 * Verifies how {@link SASLAuthentication} applies the user login limits (OF-2279): a password attempt is reserved
 * against the limits as soon as its username is known, and refused before its credentials are evaluated when the
 * account is locked or a limit blocks it; a success gives the attempt back; only password mechanisms reserve attempts.
 *
 * {@link UserLoginLimitManager} is replaced by a mock here; its decisions are tested in its own test class.
 *
 * @author Matthew Vivian
 */
public class SASLAuthenticationLoginLimitTest
{
    private static final String USER = "alice";
    private static final String PASSWORD = "secret";
    private static final String ADDRESS = "192.0.2.1";

    private static List<String> originalEnabledMechanisms;
    private boolean originalSasl2Enabled;
    private boolean originalSasl2RequireTls;

    private MockedStatic<UserLoginLimitManager> loginLimitsStatic;
    private MockedStatic<AuthFactory> authFactory;
    private MockedStatic<AuthorizationManager> authorizationManager;
    private MockedStatic<LockOutManager> lockOutStatic;

    private UserLoginLimitManager loginLimits;
    private UserLoginLimitManager.Reservation reservation;
    private LockOutManager lockOutManager;
    private Connection connection;
    private LocalClientSession session;

    @BeforeAll
    public static void setupClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
        originalEnabledMechanisms = SaslMechanismCatalog.getEnabledMechanisms();
    }

    @AfterAll
    public static void tearDownClass()
    {
        SaslMechanismCatalog.setEnabledMechanisms(originalEnabledMechanisms);
        Fixtures.clearExistingProperties();
    }

    @BeforeEach
    public void setup() throws Exception
    {
        Fixtures.clearExistingProperties();
        JiveGlobals.setProperty("xmpp.domain", Fixtures.XMPP_DOMAIN);
        XMPPServer.setInstance(Fixtures.mockXMPPServer());
        SaslMechanismCatalog.setEnabledMechanisms(Arrays.asList("PLAIN", "EXTERNAL"));
        originalSasl2Enabled = SASLAuthentication.ENABLE_SASL2.getValue();
        originalSasl2RequireTls = SASLAuthentication.SASL2_REQUIRE_TLS.getValue();
        SASLAuthentication.ENABLE_SASL2.setValue(true);
        SASLAuthentication.SASL2_REQUIRE_TLS.setValue(false);

        loginLimits = mock(UserLoginLimitManager.class);
        loginLimitsStatic = mockStatic(UserLoginLimitManager.class);
        loginLimitsStatic.when(UserLoginLimitManager::getInstance).thenReturn(loginLimits);
        reservation = mock(UserLoginLimitManager.Reservation.class);
        when(loginLimits.tryAcquireAttempt(anyString(), any())).thenReturn(reservation);

        lockOutManager = mock(LockOutManager.class);
        lockOutStatic = mockStatic(LockOutManager.class);
        lockOutStatic.when(LockOutManager::getInstance).thenReturn(lockOutManager);

        authFactory = mockStatic(AuthFactory.class);
        authFactory.when(() -> AuthFactory.authenticate(USER, PASSWORD)).thenReturn(mock(AuthToken.class));
        authFactory.when(() -> AuthFactory.authenticate(eq(USER), eq("wrong"))).thenThrow(new UnauthorizedException());

        authorizationManager = mockStatic(AuthorizationManager.class);
        authorizationManager.when(() -> AuthorizationManager.map(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        authorizationManager.when(() -> AuthorizationManager.authorize(anyString(), anyString())).thenReturn(true);

        connection = mock(Connection.class);
        when(connection.getHostAddress()).thenReturn(ADDRESS);
        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);
        SASLAuthentication.setAdvertisedSASLMechanisms(session, Set.of("PLAIN", "EXTERNAL"));
    }

    @AfterEach
    public void tearDown()
    {
        authorizationManager.close();
        authFactory.close();
        lockOutStatic.close();
        loginLimitsStatic.close();
        SASLAuthentication.ENABLE_SASL2.setValue(originalSasl2Enabled);
        SASLAuthentication.SASL2_REQUIRE_TLS.setValue(originalSasl2RequireTls);
    }

    /**
     * Verifies that a PLAIN attempt with a wrong password is counted against the attempted username and the
     * session's address, and is not given back.
     */
    @Test
    public void testWrongPlainPasswordIsCounted()
    {
        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(session, plainAuth(USER, "wrong"), false);

        // Verify result.
        assertEquals(SASLAuthentication.Status.failed, status, "a wrong password must fail");
        verify(loginLimits, times(1).description("the attempt must be counted against the username and address")).tryAcquireAttempt(USER, ADDRESS);
        verify(loginLimits, never().description("a failed attempt must not be given back")).recordSuccessfulLogin(any());
        assertNull(session.getSessionData(SASLAuthentication.SASL_LOGIN_ATTEMPT), "the attempt's reservation must be cleared after a failure");
    }

    /**
     * Verifies that a SASL2 attempt with a wrong password is counted too.
     */
    @Test
    public void testWrongPlainPasswordOverSasl2IsCounted()
    {
        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(session, sasl2PlainAuthenticate(USER, "wrong"), true);

        // Verify result.
        assertEquals(SASLAuthentication.Status.failed, status, "a wrong password must fail over SASL2");
        verify(loginLimits, times(1).description("a SASL2 attempt must be counted against the username and address")).tryAcquireAttempt(USER, ADDRESS);
        verify(loginLimits, never().description("a failed SASL2 attempt must not be given back")).recordSuccessfulLogin(any());
        assertNull(session.getSessionData(SASLAuthentication.SASL_LOGIN_ATTEMPT), "the attempt's reservation must be cleared after a SASL2 failure");
    }

    /**
     * Verifies that an attempt refused by a block of its username from its IP address, or of its IP address, answers
     * not-authorized, whatever the password, without evaluating the credentials or being counted.
     */
    @Test
    public void testRefusedAttemptAnswersNotAuthorizedWithoutCheckingCredentials()
    {
        // Setup test fixture.
        when(loginLimits.tryAcquireAttempt(USER, ADDRESS)).thenReturn(null);

        // Execute system under test.
        final SASLAuthentication.Status correct = SASLAuthentication.handle(session, plainAuth(USER, PASSWORD), false);
        final SASLAuthentication.Status wrong = SASLAuthentication.handle(session, plainAuth(USER, "wrong"), false);

        // Verify result.
        assertEquals(SASLAuthentication.Status.failed, correct, "a refused attempt with the correct password must fail");
        assertEquals(SASLAuthentication.Status.failed, wrong, "a refused attempt with a wrong password must fail");
        final ArgumentCaptor<String> responses = ArgumentCaptor.forClass(String.class);
        verify(connection, times(2).description("each attempt must be answered")).deliverRawText(responses.capture());
        assertEquals(responses.getAllValues().get(0), responses.getAllValues().get(1), "the answer must not depend on the password");
        assertTrue(responses.getAllValues().get(0).contains("<not-authorized"), "a refused attempt must answer not-authorized");
        authFactory.verify(() -> AuthFactory.authenticate(anyString(), anyString()), never().description("the credentials of a refused attempt must not be evaluated"));
        verify(loginLimits, never().description("a refused attempt must not be given back")).recordSuccessfulLogin(any());
    }

    /**
     * Verifies that an attempt refused because its username is locked out (its account is locked, or the per-username
     * limit has blocked it) answers account-disabled, whatever the password, without evaluating the credentials.
     */
    @Test
    public void testLockedOutAttemptAnswersAccountDisabledWithoutCheckingCredentials()
    {
        // Setup test fixture.
        when(loginLimits.tryAcquireAttempt(USER, ADDRESS)).thenReturn(null);
        when(loginLimits.isLockedOut(USER)).thenReturn(true);

        // Execute system under test.
        final SASLAuthentication.Status correct = SASLAuthentication.handle(session, plainAuth(USER, PASSWORD), false);
        final SASLAuthentication.Status wrong = SASLAuthentication.handle(session, plainAuth(USER, "wrong"), false);

        // Verify result.
        assertEquals(SASLAuthentication.Status.failed, correct, "a locked-out attempt with the correct password must fail");
        assertEquals(SASLAuthentication.Status.failed, wrong, "a locked-out attempt with a wrong password must fail");
        final ArgumentCaptor<String> responses = ArgumentCaptor.forClass(String.class);
        verify(connection, times(2).description("each attempt must be answered")).deliverRawText(responses.capture());
        assertEquals(responses.getAllValues().get(0), responses.getAllValues().get(1), "the answer must not depend on the password");
        assertTrue(responses.getAllValues().get(0).contains("<account-disabled"), "a locked-out attempt must answer account-disabled: " + responses.getAllValues().get(0));
        authFactory.verify(() -> AuthFactory.authenticate(anyString(), anyString()), never().description("the credentials of a locked-out attempt must not be evaluated"));
    }

    /**
     * Verifies that a successful PLAIN login gives back the attempt it reserved, and clears the attempted username.
     */
    @Test
    public void testSuccessGivesBackItsAttempt()
    {
        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(session, plainAuth(USER, PASSWORD), false);

        // Verify result.
        assertEquals(SASLAuthentication.Status.authenticated, status, "the correct password must authenticate");
        verify(loginLimits, times(1).description("the attempt must be counted when it starts")).tryAcquireAttempt(USER, ADDRESS);
        verify(loginLimits, times(1).description("the success must give the attempt back")).recordSuccessfulLogin(reservation);
        assertNull(session.getSessionData(SASLAuthentication.SASL_LOGIN_ATTEMPT), "the attempt's reservation must be cleared after a success");
    }

    /**
     * Verifies that the lock check after a successful exchange stays as a backstop: if the account turns out to be
     * locked then (for example, locked while the exchange was in progress), the login still fails, with
     * account-disabled, and the attempt is not given back.
     */
    @Test
    public void testLockCheckAfterSuccessRemainsAsBackstop()
    {
        // Setup test fixture.
        when(lockOutManager.isAccountDisabled(USER)).thenReturn(true);

        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(session, plainAuth(USER, PASSWORD), false);

        // Verify result.
        assertEquals(SASLAuthentication.Status.failed, status, "a locked account must not authenticate");
        final ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
        verify(connection, times(1).description("the refused login must be answered once")).deliverRawText(response.capture());
        assertTrue(response.getValue().contains("<account-disabled"), "a lock found after a correct password must be answered with account-disabled");
        verify(loginLimits, never().description("an attempt refused after the exchange must not be given back")).recordSuccessfulLogin(any());
    }

    /**
     * Verifies that when the authorization identity differs from the username whose lock was checked when the attempt
     * was admitted (here only in case), the lock of the authorization identity is still looked up after the exchange,
     * and a lock found then refuses the login with account-disabled.
     */
    @Test
    public void testLockCheckAfterExchangeCatchesLockedAuthorizationIdentity()
    {
        // Setup test fixture: the attempt found 'alice' not locked; 'Alice' has a lock of its own.
        when(loginLimits.isKnownNotLocked(reservation, USER)).thenReturn(true);
        when(lockOutManager.isAccountDisabled("Alice")).thenReturn(true);

        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(session, authElement("PLAIN", plainInitialResponse("Alice", USER, PASSWORD)), false);

        // Verify result.
        assertEquals(SASLAuthentication.Status.failed, status, "a locked authorization identity must not authenticate");
        verify(lockOutManager, times(1).description("the lock of the authorization identity must be looked up after the exchange")).isAccountDisabled("Alice");
        final ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
        verify(connection, times(1).description("the refused login must be answered once")).deliverRawText(response.capture());
        assertTrue(response.getValue().contains("<account-disabled"), "a lock found after the exchange must be answered with account-disabled");
        verify(loginLimits, never().description("an attempt refused after the exchange must not be given back")).recordSuccessfulLogin(any());
    }

    /**
     * Verifies that a completed EXTERNAL (certificate) login is not refused by the login limits, even when they would
     * refuse every password attempt for its username and IP address, and is neither counted nor checked against them.
     */
    @Test
    public void testExternalLoginIsNotRefusedByLoginLimits() throws Exception
    {
        // Setup test fixture.
        when(loginLimits.tryAcquireAttempt(anyString(), any())).thenReturn(null);
        when(loginLimits.isAttemptAllowed(anyString(), any())).thenReturn(false);
        when(loginLimits.isLoginAllowed(anyString(), any())).thenReturn(false);
        session.setSessionData("SaslServer", completedExternal(USER));

        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(session, responseElement(""), false);

        // Verify result.
        assertEquals(SASLAuthentication.Status.authenticated, status, "an EXTERNAL login must not be refused by the login limits");
        verify(loginLimits, never().description("an EXTERNAL login must not be counted")).tryAcquireAttempt(anyString(), any());
        verify(loginLimits, never().description("an EXTERNAL login must not be checked against the limits")).isAttemptAllowed(anyString(), any());
        verify(loginLimits, never().description("an EXTERNAL login must not be checked against the limits")).isLoginAllowed(anyString(), any());
    }

    /**
     * Verifies that a completed EXTERNAL (certificate) login is refused, with account-disabled, when the account is
     * locked: the lock check after the exchange applies to every mechanism.
     */
    @Test
    public void testExternalLoginIsRefusedForLockedAccount() throws Exception
    {
        // Setup test fixture.
        when(lockOutManager.isAccountDisabled(USER)).thenReturn(true);
        session.setSessionData("SaslServer", completedExternal(USER));

        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(session, responseElement(""), false);

        // Verify result.
        assertEquals(SASLAuthentication.Status.failed, status, "an EXTERNAL login for a locked account must fail");
        final ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
        verify(connection, times(1).description("the refused login must be answered once")).deliverRawText(response.capture());
        assertTrue(response.getValue().contains("<account-disabled"), "a locked account must be answered with account-disabled over EXTERNAL too");
    }

    /**
     * Returns a SASL server for EXTERNAL whose exchange completes with the given authorization identity, as it does
     * for a trusted client certificate.
     */
    private static SaslServer completedExternal(final String authorizationId) throws Exception
    {
        final SaslServer saslServer = mock(SaslServer.class);
        when(saslServer.evaluateResponse(any())).thenReturn(null);
        when(saslServer.isComplete()).thenReturn(true);
        when(saslServer.getAuthorizationID()).thenReturn(authorizationId);
        when(saslServer.getMechanismName()).thenReturn("EXTERNAL");
        return saslServer;
    }

    /**
     * Verifies that when the session's address cannot be determined, the attempt is still counted, with no address
     * (which counts it for the username only).
     */
    @Test
    public void testAttemptWithUnknownAddressIsCountedWithoutAddress() throws Exception
    {
        // Setup test fixture.
        when(connection.getHostAddress()).thenThrow(new UnknownHostException("detached"));

        // Execute system under test.
        SASLAuthentication.handle(session, plainAuth(USER, "wrong"), false);

        // Verify result.
        verify(loginLimits, times(1).description("an attempt with an unknown address must be counted without one")).tryAcquireAttempt(eq(USER), isNull());
    }

    /**
     * Verifies that a failed EXTERNAL attempt is not counted.
     */
    @Test
    public void testExternalFailureIsNotCounted()
    {
        // Setup test fixture.
        when(connection.isEncrypted()).thenReturn(true);

        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(session, authElement("EXTERNAL", "="), false);

        // Verify result.
        assertEquals(SASLAuthentication.Status.failed, status, "EXTERNAL without a client certificate must fail");
        verify(loginLimits, never().description("an EXTERNAL attempt must not be counted")).tryAcquireAttempt(anyString(), any());
    }

    /**
     * Verifies that a new attempt does not inherit the reservation an earlier password attempt on the same session
     * left behind: a successful EXTERNAL (certificate) login that follows it must not give that attempt back, as the
     * password attempt did not succeed.
     */
    @Test
    public void testSuccessfulExternalLoginDoesNotGiveBackAnEarlierAttempt() throws Exception
    {
        // Setup test fixture: an earlier password attempt left its reservation, and EXTERNAL completes at once, as it
        // does for a trusted client certificate.
        session.setSessionData(SASLAuthentication.SASL_LOGIN_ATTEMPT, reservation);
        final SaslServer external = completedExternal(USER);

        // Execute system under test.
        final SASLAuthentication.Status status;
        try (final MockedStatic<Sasl> sasl = mockStatic(Sasl.class)) {
            sasl.when(() -> Sasl.createSaslServer(eq("EXTERNAL"), anyString(), anyString(), any(), any())).thenReturn(external);
            status = SASLAuthentication.handle(session, authElement("EXTERNAL", "="), false);
        }

        // Verify result.
        assertEquals(SASLAuthentication.Status.authenticated, status, "the EXTERNAL login must succeed");
        verify(loginLimits, never().description("a successful EXTERNAL login must not give back an earlier password attempt")).recordSuccessfulLogin(any());
        verify(loginLimits, never().description("an EXTERNAL attempt must not be counted")).tryAcquireAttempt(anyString(), any());
        assertNull(session.getSessionData(SASLAuthentication.SASL_LOGIN_ATTEMPT), "no reservation must be left on the session");
    }

    /**
     * Verifies that aborting a real SCRAM exchange after its client-first message (when the username is known and the
     * attempt has been counted) clears the attempt's reservation and does not give the attempt back: an attempt that
     * ends without success stays counted.
     */
    @Test
    public void testAbortedAttemptStaysCounted() throws Exception
    {
        // Setup test fixture: a SCRAM exchange that has received its client-first message.
        enableScram();
        final ScramSha1TestClient client = new ScramSha1TestClient(USER, "clientnonce");
        assertEquals(SASLAuthentication.Status.needResponse, SASLAuthentication.handle(session, scramAuth(client), false), "the exchange must continue after client-first");
        verify(loginLimits, times(1).description("the attempt must be counted once its username is known")).tryAcquireAttempt(USER, ADDRESS);

        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(session, DocumentHelper.createElement(new QName("abort", Namespace.get("", SASL_NAMESPACE))), false);

        // Verify result.
        assertEquals(SASLAuthentication.Status.failed, status, "an abort ends the attempt");
        verify(loginLimits, never().description("an aborted attempt must not be given back")).recordSuccessfulLogin(any());
        assertNull(session.getSessionData(SASLAuthentication.SASL_LOGIN_ATTEMPT), "an abort must clear the attempt's reservation");
    }

    /**
     * Verifies that a successful SCRAM-SHA-1 exchange gives back the attempt it reserved.
     */
    @Test
    public void testScramSuccessGivesBackItsAttempt() throws Exception
    {
        // Setup test fixture.
        enableScram();
        final ScramSha1TestClient client = new ScramSha1TestClient(USER, "clientnonce");
        SASLAuthentication.handle(session, scramAuth(client), false);
        final String serverFirst = lastText();

        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(session, responseElement(client.clientFinal(serverFirst, PASSWORD)), false);

        // Verify result.
        assertEquals(SASLAuthentication.Status.authenticated, status, "the correct password must authenticate over SCRAM");
        verify(loginLimits, times(1).description("the SCRAM attempt must be counted once")).tryAcquireAttempt(USER, ADDRESS);
        verify(loginLimits, times(1).description("a SCRAM success must give its attempt back")).recordSuccessfulLogin(reservation);
        assertNull(session.getSessionData(SASLAuthentication.SASL_LOGIN_ATTEMPT), "the attempt's reservation must be cleared after a success");
    }

    /**
     * Verifies that a successful SASL2 login gives back the attempt it reserved.
     */
    @Test
    public void testSasl2SuccessGivesBackItsAttempt()
    {
        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(session, sasl2PlainAuthenticate(USER, PASSWORD), true);

        // Verify result.
        assertEquals(SASLAuthentication.Status.authenticated, status, "the correct password must authenticate over SASL2");
        verify(loginLimits, times(1).description("a SASL2 success must give its attempt back")).recordSuccessfulLogin(reservation);
        assertNull(session.getSessionData(SASLAuthentication.SASL_LOGIN_ATTEMPT), "the attempt's reservation must be cleared after a SASL2 success");
    }

    private void enableScram() throws Exception
    {
        SaslMechanismCatalog.setEnabledMechanisms(Arrays.asList("PLAIN", "EXTERNAL", "SCRAM-SHA-1"));
        SASLAuthentication.setAdvertisedSASLMechanisms(session, Set.of("PLAIN", "EXTERNAL", "SCRAM-SHA-1"));
        SASLAuthentication.setAdvertisedChannelBindingTypes(session, Set.of());
        final byte[] salt = "saltsaltsaltsalt".getBytes(StandardCharsets.UTF_8);
        final byte[][] keys = ScramSha1TestClient.keys(PASSWORD, salt, 4096);
        authFactory.when(() -> AuthFactory.getSalt(USER, "SCRAM-SHA-1")).thenReturn(Base64.getEncoder().encodeToString(salt));
        authFactory.when(() -> AuthFactory.getIterations(USER, "SCRAM-SHA-1")).thenReturn(4096);
        authFactory.when(() -> AuthFactory.getStoredKey(USER, "SCRAM-SHA-1")).thenReturn(Base64.getEncoder().encodeToString(keys[1]));
        authFactory.when(() -> AuthFactory.getServerKey(USER, "SCRAM-SHA-1")).thenReturn(Base64.getEncoder().encodeToString(keys[2]));
    }

    private static Element scramAuth(final ScramSha1TestClient client)
    {
        return authElement("SCRAM-SHA-1", client.clientFirst());
    }

    private static Element responseElement(final String value)
    {
        final Element response = DocumentHelper.createElement(new QName("response", Namespace.get("", SASL_NAMESPACE)));
        response.setText(value);
        return response;
    }

    private String lastText() throws Exception
    {
        final ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(connection, org.mockito.Mockito.atLeastOnce().description("the server must have answered the peer")).deliverRawText(sent.capture());
        final List<String> all = sent.getAllValues();
        return DocumentHelper.parseText(all.get(all.size() - 1)).getRootElement().getTextTrim();
    }

    private static Element plainAuth(final String username, final String password)
    {
        return authElement("PLAIN", plainInitialResponse(username, password));
    }

    private static Element sasl2PlainAuthenticate(final String username, final String password)
    {
        final Element authenticate = DocumentHelper.createElement(new QName("authenticate", Namespace.get("", SASL2_NAMESPACE)));
        authenticate.addAttribute("mechanism", "PLAIN");
        authenticate.addElement("initial-response", SASL2_NAMESPACE).setText(plainInitialResponse(username, password));
        return authenticate;
    }

    private static String plainInitialResponse(final String username, final String password)
    {
        return plainInitialResponse("", username, password);
    }

    private static String plainInitialResponse(final String authorizationId, final String username, final String password)
    {
        return Base64.getEncoder().encodeToString((authorizationId + "\0" + username + "\0" + password).getBytes(StandardCharsets.UTF_8));
    }

    private static Element authElement(final String mechanism, final String initialResponse)
    {
        final Element auth = DocumentHelper.createElement(new QName("auth", Namespace.get("", SASL_NAMESPACE)));
        auth.addAttribute("mechanism", mechanism);
        if (initialResponse != null) {
            auth.setText(initialResponse);
        }
        return auth;
    }
}
