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
import org.jivesoftware.openfire.auth.AuthorizationManager;
import org.jivesoftware.openfire.auth.UserLoginLimitManager;
import org.jivesoftware.openfire.lockout.LockOutManager;
import org.jivesoftware.openfire.sasl.SaslMechanismCatalog;
import org.jivesoftware.openfire.security.SecurityAuditManager;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.openfire.user.UserNotFoundException;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jivesoftware.openfire.net.SASLAuthentication.SASL_NAMESPACE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies, through {@link SASLAuthentication} with the real {@link UserLoginLimitManager} and the login limits on,
 * how attempts that give the username with this server's domain ('alice@domain') are counted.
 *
 * SCRAM looks the credentials up under the username as sent, and the SCRAM username is the localpart (RFC 6120 section
 * 6.3.7), so such an attempt can never succeed; it must therefore not be counted against 'alice', or a client set up
 * this way would block or lock the account with the right password. DIGEST-MD5 reads the password through the
 * authentication provider, which accepts 'user@domain', so such an attempt can succeed and gives its attempt back.
 *
 * @author Matthew Vivian
 */
public class SASLAuthenticationUserAtDomainTest
{
    private static final String USER = "alice";
    private static final String USER_AT_DOMAIN = USER + "@" + Fixtures.XMPP_DOMAIN;
    private static final String PASSWORD = "secret";
    private static final String ADDRESS = "192.0.2.88";
    private static final String OTHER_USER = "bob";
    private static final String OTHER_ADDRESS = "192.0.2.89";
    private static final byte[] SALT = "saltsaltsaltsalt".getBytes(StandardCharsets.UTF_8);
    private static final Pattern SALT_IN_SERVER_FIRST = Pattern.compile(",s=([^,]+),");

    private static List<String> originalEnabledMechanisms;

    private MockedStatic<LockOutManager> lockOutStatic;
    private MockedStatic<AuthFactory> authFactory;
    private MockedStatic<SecurityAuditManager> auditStatic;
    private boolean originalEnabled;
    private long originalMaxPair;
    private long originalMaxAddress;
    private long originalMaxUsername;

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
        SaslMechanismCatalog.setEnabledMechanisms(List.of("SCRAM-SHA-1", "DIGEST-MD5"));

        clearLoginLimits();
        originalEnabled = UserLoginLimitManager.ENABLED.getValue();
        originalMaxPair = UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.getValue();
        originalMaxAddress = UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.getValue();
        originalMaxUsername = UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.getValue();
        UserLoginLimitManager.ENABLED.setValue(true);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(2L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(2L);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(2L);

        final LockOutManager lockOutManager = mock(LockOutManager.class);
        lockOutStatic = mockStatic(LockOutManager.class);
        lockOutStatic.when(LockOutManager::getInstance).thenReturn(lockOutManager);
        auditStatic = mockStatic(SecurityAuditManager.class);
        auditStatic.when(SecurityAuditManager::getInstance).thenReturn(mock(SecurityAuditManager.class));

        // As the default provider answers: 'alice' has SCRAM credentials; 'alice@domain' names no account.
        final byte[][] keys = ScramSha1TestClient.keys(PASSWORD, SALT, 4096);
        authFactory = mockStatic(AuthFactory.class);
        authFactory.when(() -> AuthFactory.getSalt(USER, "SCRAM-SHA-1")).thenReturn(Base64.getEncoder().encodeToString(SALT));
        authFactory.when(() -> AuthFactory.getIterations(USER, "SCRAM-SHA-1")).thenReturn(4096);
        authFactory.when(() -> AuthFactory.getStoredKey(USER, "SCRAM-SHA-1")).thenReturn(Base64.getEncoder().encodeToString(keys[1]));
        authFactory.when(() -> AuthFactory.getServerKey(USER, "SCRAM-SHA-1")).thenReturn(Base64.getEncoder().encodeToString(keys[2]));
        authFactory.when(() -> AuthFactory.getSalt(USER_AT_DOMAIN, "SCRAM-SHA-1")).thenThrow(new UserNotFoundException());
        authFactory.when(() -> AuthFactory.getIterations(USER_AT_DOMAIN, "SCRAM-SHA-1")).thenThrow(new UserNotFoundException());
        authFactory.when(() -> AuthFactory.getStoredKey(USER_AT_DOMAIN, "SCRAM-SHA-1")).thenThrow(new UserNotFoundException());
        authFactory.when(() -> AuthFactory.getServerKey(USER_AT_DOMAIN, "SCRAM-SHA-1")).thenThrow(new UserNotFoundException());
        // DefaultAuthProvider.getPassword removes this server's domain.
        authFactory.when(() -> AuthFactory.getPassword(USER_AT_DOMAIN)).thenReturn(PASSWORD);
    }

    @AfterEach
    public void tearDown()
    {
        clearLoginLimits();
        UserLoginLimitManager.ENABLED.setValue(originalEnabled);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_PAIR.setValue(originalMaxPair);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_ADDRESS.setValue(originalMaxAddress);
        UserLoginLimitManager.MAX_ATTEMPTS_PER_USERNAME.setValue(originalMaxUsername);
        authFactory.close();
        auditStatic.close();
        lockOutStatic.close();
    }

    /**
     * Verifies that SCRAM-SHA-1 attempts as alice@domain, with the right password, fail without being counted: three
     * of them, with every limit at two attempts, leave no block for alice, for the address or for the account.
     */
    @Test
    public void testScramAsUserAtDomainIsNotCounted() throws Exception
    {
        // Execute system under test.
        for (int i = 0; i < 3; i++) {
            assertEquals(SASLAuthentication.Status.failed, scram(USER_AT_DOMAIN, PASSWORD).status(), "SCRAM as alice@domain must fail, as no account has that name");
        }

        // Verify result.
        final UserLoginLimitManager loginLimits = UserLoginLimitManager.getInstance();
        assertTrue(loginLimits.isLoginAllowed(USER, ADDRESS), "SCRAM attempts as alice@domain must not be counted for alice from the IP address");
        assertTrue(loginLimits.isLoginAllowed(OTHER_USER, ADDRESS), "SCRAM attempts as alice@domain must not be counted for the IP address");
        assertTrue(loginLimits.isLoginAllowed(USER, OTHER_ADDRESS), "SCRAM attempts as alice@domain must not be counted for alice from any IP address");
        authFactory.verify(() -> AuthFactory.getStoredKey(anyString(), anyString()), never().description("no stored key may be looked up for an attempt that is not admitted"));
        authFactory.verify(() -> AuthFactory.getServerKey(anyString(), anyString()), never().description("no server key may be looked up for an attempt that is not admitted"));
    }

    /**
     * Verifies that a SCRAM-SHA-1 attempt as alice@domain cannot be told from a wrong password: the server-first message
     * carries the same salt either way, and the failure is the same.
     */
    @Test
    public void testScramAsUserAtDomainLooksLikeWrongPassword() throws Exception
    {
        // Execute system under test.
        final ScramResult right = scram(USER_AT_DOMAIN, PASSWORD);
        final ScramResult wrong = scram(USER_AT_DOMAIN, "wrong");

        // Verify result.
        assertEquals(wrong.salt(), right.salt(), "the salt must not depend on the password");
        assertEquals(wrong.failure(), right.failure(), "the right and a wrong password must get the same failure");
        assertTrue(right.failure().contains("<not-authorized"), "the failure must be not-authorized");
    }

    /**
     * Verifies that a bare username still logs in over SCRAM-SHA-1, and that its failed attempts are still counted.
     */
    @Test
    public void testScramAsBareUsernameStillLogsInAndCountsFailures() throws Exception
    {
        // Execute system under test & verify result.
        assertEquals(SASLAuthentication.Status.authenticated, scram(USER, PASSWORD).status(), "SCRAM as alice with the right password must authenticate");
        scram(USER, "wrong-1");
        scram(USER, "wrong-2");
        assertFalse(UserLoginLimitManager.getInstance().isLoginAllowed(USER, ADDRESS), "failed SCRAM attempts as alice must still be counted");
    }

    /**
     * Verifies that DIGEST-MD5 as alice@domain with the right password logs in, as the provider reads alice's password
     * for that name, and so gives its attempt back: three such logins, with every limit at two attempts, leave no
     * block. (Authorisation, which looks the account up in the user store, is stubbed to accept alice.)
     */
    @Test
    public void testDigestMd5AsUserAtDomainLogsInAndIsNotCounted() throws Exception
    {
        try (final MockedStatic<AuthorizationManager> authorization = mockStatic(AuthorizationManager.class)) {
            // Setup test fixture.
            authorization.when(() -> AuthorizationManager.map(anyString())).thenAnswer(i -> i.getArgument(0));
            authorization.when(() -> AuthorizationManager.authorize(anyString(), anyString())).thenReturn(true);

            // Execute system under test.
            for (int i = 0; i < 3; i++) {
                assertEquals(SASLAuthentication.Status.authenticated, digestMd5(USER_AT_DOMAIN, PASSWORD), "DIGEST-MD5 as alice@domain with the right password must authenticate");
            }

            // Verify result.
            final UserLoginLimitManager loginLimits = UserLoginLimitManager.getInstance();
            assertTrue(loginLimits.isLoginAllowed(USER, ADDRESS), "successful DIGEST-MD5 logins as alice@domain must leave no count for alice from the IP address");
            assertTrue(loginLimits.isLoginAllowed(OTHER_USER, ADDRESS), "successful DIGEST-MD5 logins as alice@domain must leave no count for the IP address");
            assertTrue(loginLimits.isLoginAllowed(USER, OTHER_ADDRESS), "successful DIGEST-MD5 logins as alice@domain must leave no count for alice from any IP address");
        }
    }

    /**
     * Clears the counts and blocks of this test's usernames and IP addresses in the shared login limits, so that
     * neither this test nor another that uses the singleton sees counts left behind.
     */
    private static void clearLoginLimits()
    {
        final UserLoginLimitManager loginLimits = UserLoginLimitManager.getInstance();
        for (final String address : List.of(ADDRESS, OTHER_ADDRESS)) {
            loginLimits.clearBlock(UserLoginLimitManager.Tier.USERNAME_FROM_ADDRESS, USER, address);
            loginLimits.clearBlock(UserLoginLimitManager.Tier.USERNAME_FROM_ADDRESS, OTHER_USER, address);
            loginLimits.clearBlock(UserLoginLimitManager.Tier.ADDRESS, null, address);
        }
        loginLimits.clearBlock(UserLoginLimitManager.Tier.USERNAME, USER, null);
        loginLimits.clearBlock(UserLoginLimitManager.Tier.USERNAME, OTHER_USER, null);
    }

    private record ScramResult(SASLAuthentication.Status status, String salt, String failure) {}

    /**
     * Runs a SCRAM-SHA-1 exchange on a new session from {@link #ADDRESS}.
     */
    private ScramResult scram(final String username, final String password) throws Exception
    {
        final Connection connection = mock(Connection.class);
        final LocalClientSession session = newSession(connection, "SCRAM-SHA-1");
        final ScramSha1TestClient client = new ScramSha1TestClient(username, "clientnonce");
        final Element auth = element("auth", client.clientFirst());
        auth.addAttribute("mechanism", "SCRAM-SHA-1");
        assertEquals(SASLAuthentication.Status.needResponse, SASLAuthentication.handle(session, auth, false), "every username must be sent a server-first message");
        final String serverFirst = new String(Base64.getDecoder().decode(lastText(connection)), StandardCharsets.UTF_8);
        final Matcher salt = SALT_IN_SERVER_FIRST.matcher(serverFirst);
        assertTrue(salt.find(), "the server-first message must carry a salt: " + serverFirst);

        final SASLAuthentication.Status status = SASLAuthentication.handle(session, element("response", client.clientFinal(lastText(connection), password)), false);
        return new ScramResult(status, salt.group(1), lastStanza(connection));
    }

    /**
     * Runs a DIGEST-MD5 exchange on a new session from {@link #ADDRESS}, with the JDK's client.
     */
    private SASLAuthentication.Status digestMd5(final String username, final String password) throws Exception
    {
        final Connection connection = mock(Connection.class);
        final LocalClientSession session = newSession(connection, "DIGEST-MD5");
        final SaslClient client = Sasl.createSaslClient(new String[]{"DIGEST-MD5"}, null, "xmpp", Fixtures.XMPP_DOMAIN, null, callbacks -> {
            for (final Callback callback : callbacks) {
                if (callback instanceof NameCallback nameCallback) {
                    nameCallback.setName(username);
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
        SASLAuthentication.handle(session, auth, false);
        byte[] response = client.evaluateChallenge(Base64.getDecoder().decode(lastText(connection)));
        SASLAuthentication.Status status = SASLAuthentication.handle(session, element("response", Base64.getEncoder().encodeToString(response)), false);
        if (status == SASLAuthentication.Status.needResponse) {
            // The server's rspauth challenge; the client's empty answer completes the exchange.
            response = client.evaluateChallenge(Base64.getDecoder().decode(lastText(connection)));
            status = SASLAuthentication.handle(session, element("response", response == null || response.length == 0 ? null : Base64.getEncoder().encodeToString(response)), false);
        }
        return status;
    }

    private static LocalClientSession newSession(final Connection connection, final String mechanism) throws Exception
    {
        when(connection.getHostAddress()).thenReturn(ADDRESS);
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
