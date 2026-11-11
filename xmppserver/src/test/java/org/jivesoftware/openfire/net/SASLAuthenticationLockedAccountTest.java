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

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.RealmChoiceCallback;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.jivesoftware.openfire.net.SASLAuthentication.SASL_NAMESPACE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies, through {@link SASLAuthentication} with the real {@link UserLoginLimitManager} and the login limits turned
 * off, that an account locked by an administrator gets the same not-authorized answer whether the password is right or
 * wrong, for SCRAM-SHA-1 and DIGEST-MD5, without its password being checked (OF-2279, decision 6). Before, both
 * mechanisms answered account-disabled to a correct password only.
 *
 * @author Matthew Vivian
 */
public class SASLAuthenticationLockedAccountTest
{
    private static final String USER = "alice";
    private static final String PASSWORD = "secret";

    private static List<String> originalEnabledMechanisms;

    private MockedStatic<LockOutManager> lockOutStatic;
    private MockedStatic<AuthFactory> authFactory;
    private LockOutManager lockOutManager;

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
    public void setup()
    {
        Fixtures.clearExistingProperties();
        JiveGlobals.setProperty("xmpp.domain", Fixtures.XMPP_DOMAIN);
        XMPPServer.setInstance(Fixtures.mockXMPPServer());
        SaslMechanismCatalog.setEnabledMechanisms(List.of("SCRAM-SHA-1", "DIGEST-MD5"));

        lockOutManager = mock(LockOutManager.class);
        when(lockOutManager.isAccountDisabled(USER)).thenReturn(true);
        lockOutStatic = mockStatic(LockOutManager.class);
        lockOutStatic.when(LockOutManager::getInstance).thenReturn(lockOutManager);

        // Every credential lookup is recorded by this mock, so that the tests can show which happened.
        authFactory = mockStatic(AuthFactory.class);
    }

    @AfterEach
    public void tearDown()
    {
        authFactory.close();
        lockOutStatic.close();
    }

    /**
     * Verifies that a locked account answers SCRAM-SHA-1 with not-authorized at the end of the exchange, the same for
     * the right and a wrong password, and that no key is looked up, so no proof is verified. (The salt and iteration
     * count are looked up and discarded, as for an unknown user, so that a refusal takes as long.)
     */
    @Test
    public void testLockedAccountOverScramAnswersTheSameForRightAndWrongPassword() throws Exception
    {
        // Setup test fixture.
        assertFalse(UserLoginLimitManager.ENABLED.getValue(), "this test is about locks with the login limits turned off");

        // Execute system under test.
        final String right = scramFailure(PASSWORD);
        final String wrong = scramFailure("wrong");

        // Verify result.
        assertTrue(right.contains("<not-authorized"), "a locked account must answer not-authorized to the right password");
        assertEquals(wrong, right, "a locked account must answer the right and a wrong password the same way");
        authFactory.verify(() -> AuthFactory.getStoredKey(anyString(), anyString()), never().description("a locked account's stored key must not be looked up"));
        authFactory.verify(() -> AuthFactory.getServerKey(anyString(), anyString()), never().description("a locked account's server key must not be looked up"));
        verify(lockOutManager, atLeastOnce().description("the refusal of a locked account must be reported to lockout listeners")).recordFailedLogin(USER);
    }

    /**
     * Verifies that a locked account answers DIGEST-MD5 with not-authorized, the same for the right and a wrong
     * password, and that its password is not read.
     */
    @Test
    public void testLockedAccountOverDigestMd5AnswersTheSameForRightAndWrongPassword() throws Exception
    {
        // Setup test fixture.
        assertFalse(UserLoginLimitManager.ENABLED.getValue(), "this test is about locks with the login limits turned off");

        // Execute system under test.
        final String right = digestMd5Failure(PASSWORD);
        final String wrong = digestMd5Failure("wrong");

        // Verify result.
        assertTrue(right.contains("<not-authorized"), "a locked account must answer not-authorized to the right password");
        assertEquals(wrong, right, "a locked account must answer the right and a wrong password the same way");
        authFactory.verifyNoInteractions();
    }

    /**
     * Verifies that a successful SCRAM-SHA-1 login, with the login limits off, looks the account's lock up only once:
     * the check after the exchange relies on the check made when the username became known, as LockOutManager does not
     * cache that an account is not locked.
     */
    @Test
    public void testSuccessfulScramLoginLooksUpLockOnce() throws Exception
    {
        // Setup test fixture.
        assertFalse(UserLoginLimitManager.ENABLED.getValue(), "this test is about the cost of a login with the login limits turned off");
        final String user = "bob";
        final byte[] salt = "saltsaltsaltsalt".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        final byte[][] keys = ScramSha1TestClient.keys(PASSWORD, salt, 4096);
        authFactory.when(() -> AuthFactory.getSalt(user, "SCRAM-SHA-1")).thenReturn(Base64.getEncoder().encodeToString(salt));
        authFactory.when(() -> AuthFactory.getIterations(user, "SCRAM-SHA-1")).thenReturn(4096);
        authFactory.when(() -> AuthFactory.getStoredKey(user, "SCRAM-SHA-1")).thenReturn(Base64.getEncoder().encodeToString(keys[1]));
        authFactory.when(() -> AuthFactory.getServerKey(user, "SCRAM-SHA-1")).thenReturn(Base64.getEncoder().encodeToString(keys[2]));
        final Connection connection = mock(Connection.class);
        final LocalClientSession session = newSession(connection, "SCRAM-SHA-1");
        final ScramSha1TestClient client = new ScramSha1TestClient(user, "clientnonce");
        final Element auth = element("auth", client.clientFirst());
        auth.addAttribute("mechanism", "SCRAM-SHA-1");
        SASLAuthentication.handle(session, auth, false);

        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(session, element("response", client.clientFinal(lastText(connection), PASSWORD)), false);

        // Verify result.
        assertEquals(SASLAuthentication.Status.authenticated, status, "the correct password must authenticate");
        verify(lockOutManager, times(1).description("a successful login must look the account's lock up exactly once")).isAccountDisabled(anyString());
    }

    /**
     * Runs a SCRAM-SHA-1 exchange on a new session and returns the last stanza the server sent.
     */
    private String scramFailure(final String password) throws Exception
    {
        final Connection connection = mock(Connection.class);
        final LocalClientSession session = newSession(connection, "SCRAM-SHA-1");
        final ScramSha1TestClient client = new ScramSha1TestClient(USER, "clientnonce");
        // The locked account exists, with a SCRAM salt and iteration count, which a refused attempt is also sent.
        authFactory.when(() -> AuthFactory.getSalt(USER, "SCRAM-SHA-1")).thenReturn(Base64.getEncoder().encodeToString("saltsaltsaltsalt".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        authFactory.when(() -> AuthFactory.getIterations(USER, "SCRAM-SHA-1")).thenReturn(4096);

        final Element auth = element("auth", client.clientFirst());
        auth.addAttribute("mechanism", "SCRAM-SHA-1");
        assertEquals(SASLAuthentication.Status.needResponse, SASLAuthentication.handle(session, auth, false), "a locked account must be sent a server-first message, as any other username is");
        final String serverFirst = lastText(connection);

        assertEquals(SASLAuthentication.Status.failed, SASLAuthentication.handle(session, element("response", client.clientFinal(serverFirst, password)), false), "a locked account must not authenticate");
        return lastStanza(connection);
    }

    /**
     * Runs a DIGEST-MD5 exchange on a new session, with the JDK's client, and returns the last stanza the server sent.
     */
    private String digestMd5Failure(final String password) throws Exception
    {
        final Connection connection = mock(Connection.class);
        final LocalClientSession session = newSession(connection, "DIGEST-MD5");
        final SaslClient client = Sasl.createSaslClient(new String[]{"DIGEST-MD5"}, null, "xmpp", Fixtures.XMPP_DOMAIN, null, callbacks -> {
            for (final Callback callback : callbacks) {
                if (callback instanceof NameCallback nameCallback) {
                    nameCallback.setName(USER);
                } else if (callback instanceof PasswordCallback passwordCallback) {
                    passwordCallback.setPassword(password.toCharArray());
                } else if (callback instanceof RealmCallback realmCallback) {
                    realmCallback.setText(Fixtures.XMPP_DOMAIN);
                } else if (callback instanceof RealmChoiceCallback realmChoiceCallback) {
                    realmChoiceCallback.setSelectedIndex(0);
                }
            }
        });

        final Element auth = element("auth", null);
        auth.addAttribute("mechanism", "DIGEST-MD5");
        assertEquals(SASLAuthentication.Status.needResponse, SASLAuthentication.handle(session, auth, false), "DIGEST-MD5 must start with a challenge");
        final byte[] response = client.evaluateChallenge(Base64.getDecoder().decode(lastText(connection)));

        assertEquals(SASLAuthentication.Status.failed, SASLAuthentication.handle(session, element("response", Base64.getEncoder().encodeToString(response)), false), "a locked account must not authenticate");
        return lastStanza(connection);
    }

    private static LocalClientSession newSession(final Connection connection, final String mechanism) throws Exception
    {
        when(connection.getHostAddress()).thenReturn("192.0.2.1");
        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);
        SASLAuthentication.setAdvertisedSASLMechanisms(session, Set.of(mechanism));
        SASLAuthentication.setAdvertisedChannelBindingTypes(session, Set.of());
        return session;
    }

    private static Element element(final String name, final String text)
    {
        final Element element = DocumentHelper.createElement(new QName(name, Namespace.get("", SASL_NAMESPACE)));
        if (text != null) {
            element.setText(text);
        }
        return element;
    }

    private static String lastStanza(final Connection connection)
    {
        final ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(connection, atLeastOnce().description("the server must have answered the peer")).deliverRawText(sent.capture());
        final List<String> all = new ArrayList<>(sent.getAllValues());
        return all.get(all.size() - 1);
    }

    private static String lastText(final Connection connection) throws Exception
    {
        return DocumentHelper.parseText(lastStanza(connection)).getRootElement().getTextTrim();
    }
}
