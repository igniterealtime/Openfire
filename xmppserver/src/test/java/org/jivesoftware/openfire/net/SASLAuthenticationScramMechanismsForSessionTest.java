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
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.sasl.ScramSha1SaslServer;
import org.jivesoftware.openfire.sasl.ScramSha256SaslServer;
import org.jivesoftware.openfire.sasl.ScramSha512SaslServer;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.xmpp.packet.JID;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Verifies the session-scoped caching of SCRAM mechanism lookups performed by
 * {@link SASLAuthentication#getScramMechanismsForSession(LocalClientSession)}.
 *
 * Determining these mechanisms requires a credential lookup that is driven by a username which an unauthenticated peer
 * supplies. A stream is typically opened more than once before authentication completes, and each of those regenerates
 * the stream features, so the outcome is cached for the duration of the session.
 *
 * The result is keyed by the expected username rather than cleared explicitly: a change of that username makes the
 * cached value a miss. That covers a peer restating, changing or omitting its claimed identity on a new stream, as well
 * as the transition to an authenticated identity. When {@link SASLAuthentication#SCRAM_MECHANISMS_PER_USER} is
 * disabled, no username is used at all and every session is answered with the same mechanisms.
 */
public class SASLAuthenticationScramMechanismsForSessionTest
{
    private static final String SERVER_NAME = "example.org";

    private static final Set<String> SHA1_ONLY = Set.of(ScramSha1SaslServer.MECHANISM_NAME);

    private static final Set<String> ALL_MECHANISMS = Set.of(
        ScramSha1SaslServer.MECHANISM_NAME,
        ScramSha256SaslServer.MECHANISM_NAME,
        ScramSha512SaslServer.MECHANISM_NAME);

    private MockedStatic<AuthFactory> authFactory;

    @BeforeAll
    public static void beforeAll() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();

        // Force class initialization here, while no statics are mocked. The static initializer of this class registers
        // a security provider and reads configuration; left to happen on first use, that would occur inside the
        // mocked-static scope of a test.
        Class.forName(SASLAuthentication.class.getName());
    }

    @BeforeEach
    public void setUp()
    {
        authFactory = Mockito.mockStatic(AuthFactory.class);
        authFactory.when(AuthFactory::getFallbackScramMechanisms).thenReturn(SHA1_ONLY);
    }

    @AfterEach
    public void tearDown()
    {
        authFactory.close();
        Fixtures.clearExistingProperties();
    }

    /**
     * Verifies that the channel binding variant of every reported mechanism is included, as it shares the credentials
     * of the mechanism it is derived from.
     */
    @Test
    void getScramMechanismsForSession_expandsToChannelBindingVariants()
    {
        // Setup test fixture.
        final LocalClientSession session = sessionStub();
        session.setClaimedIdentity(new JID("juliet@" + SERVER_NAME));
        authFactory.when(() -> AuthFactory.getScramMechanisms("juliet")).thenReturn(SHA1_ONLY);

        // Execute system under test.
        final Set<String> result = SASLAuthentication.getScramMechanismsForSession(session);

        // Verify result.
        assertEquals(Set.of(ScramSha1SaslServer.MECHANISM_NAME, ScramSha1SaslServer.MECHANISM_NAME + "-PLUS"), result, "The channel binding variant of a reported mechanism must be included, as it shares that mechanism's credentials.");
    }

    /**
     * Verifies that a repeated invocation for an unchanged expected username does not repeat the credential lookup.
     */
    @Test
    void getScramMechanismsForSession_cachesResultForUnchangedUsername()
    {
        // Setup test fixture.
        final LocalClientSession session = sessionStub();
        session.setClaimedIdentity(new JID("juliet@" + SERVER_NAME));
        authFactory.when(() -> AuthFactory.getScramMechanisms("juliet")).thenReturn(ALL_MECHANISMS);
        final Set<String> first = SASLAuthentication.getScramMechanismsForSession(session);

        // Execute system under test.
        final Set<String> second = SASLAuthentication.getScramMechanismsForSession(session);

        // Verify result.
        assertEquals(first, second, "A repeated invocation for an unchanged expected username must yield the same mechanisms.");
        authFactory.verify(() -> AuthFactory.getScramMechanisms("juliet"), times(1));
    }

    /**
     * Verifies that a changed claim causes the credential lookup to be performed again. A peer can restate a different
     * identity on a new stream, and the mechanisms that are advertised on that stream must reflect it.
     */
    @Test
    void getScramMechanismsForSession_repeatsLookupWhenClaimChanges()
    {
        // Setup test fixture.
        final LocalClientSession session = sessionStub();
        session.setClaimedIdentity(new JID("juliet@" + SERVER_NAME));
        authFactory.when(() -> AuthFactory.getScramMechanisms("juliet")).thenReturn(SHA1_ONLY);
        authFactory.when(() -> AuthFactory.getScramMechanisms("romeo")).thenReturn(ALL_MECHANISMS);
        SASLAuthentication.getScramMechanismsForSession(session);

        // Execute system under test.
        session.setClaimedIdentity(new JID("romeo@" + SERVER_NAME));
        final Set<String> result = SASLAuthentication.getScramMechanismsForSession(session);

        // Verify result.
        assertEquals(withChannelBindingVariants(ALL_MECHANISMS), result, "A changed claim must yield the mechanisms of the newly claimed user.");
        authFactory.verify(() -> AuthFactory.getScramMechanisms("romeo"), times(1));
    }

    /**
     * Verifies that a cleared claim causes the credential lookup to be performed again. A stream header that omits the
     * attribute leaves the session without a claimed identity, which must not be answered from a value that was
     * derived from the claim made on an earlier stream.
     */
    @Test
    void getScramMechanismsForSession_repeatsLookupWhenClaimIsCleared()
    {
        // Setup test fixture.
        final LocalClientSession session = sessionStub();
        session.setClaimedIdentity(new JID("juliet@" + SERVER_NAME));
        authFactory.when(() -> AuthFactory.getScramMechanisms("juliet")).thenReturn(ALL_MECHANISMS);
        SASLAuthentication.getScramMechanismsForSession(session);

        // Execute system under test.
        session.setClaimedIdentity(null);
        final Set<String> result = SASLAuthentication.getScramMechanismsForSession(session);

        // Verify result.
        assertEquals(withChannelBindingVariants(SHA1_ONLY), result, "A cleared claim must yield the fallback mechanisms, not those that were derived from the claim made on an earlier stream.");
        authFactory.verify(AuthFactory::getFallbackScramMechanisms, times(1));
    }

    /**
     * Verifies that a session for which no claim was made is answered with the fallback mechanisms.
     */
    @Test
    void getScramMechanismsForSession_usesFallbackWhenNoClaimIsMade()
    {
        // Setup test fixture.
        final LocalClientSession session = sessionStub();

        // Execute system under test.
        final Set<String> result = SASLAuthentication.getScramMechanismsForSession(session);

        // Verify result.
        assertEquals(withChannelBindingVariants(SHA1_ONLY), result, "A session for which no claim was made must be answered with the fallback mechanisms.");
    }

    /**
     * Verifies that the fallback is cached too. A client that never identifies itself opens a stream more than once as
     * well, and must not cause a repeated lookup on each of them.
     */
    @Test
    void getScramMechanismsForSession_cachesFallbackResult()
    {
        // Setup test fixture.
        final LocalClientSession session = sessionStub();
        final Set<String> first = SASLAuthentication.getScramMechanismsForSession(session);

        // Execute system under test.
        final Set<String> second = SASLAuthentication.getScramMechanismsForSession(session);

        // Verify result.
        assertEquals(first, second, "A repeated invocation for a session without a claim must yield the same mechanisms.");
        authFactory.verify(AuthFactory::getFallbackScramMechanisms, times(1));
    }

    /**
     * Verifies that the lookup is performed again once a session has authenticated. The expected username is then the
     * authenticated one, so a value that was derived from an unverified claim must no longer be used.
     */
    @Test
    void getScramMechanismsForSession_repeatsLookupOnceAuthenticated()
    {
        // Setup test fixture.
        final LocalClientSession session = sessionStub();
        session.setClaimedIdentity(new JID("juliet@" + SERVER_NAME));
        authFactory.when(() -> AuthFactory.getScramMechanisms("juliet")).thenReturn(SHA1_ONLY);
        authFactory.when(() -> AuthFactory.getScramMechanisms("romeo")).thenReturn(ALL_MECHANISMS);
        SASLAuthentication.getScramMechanismsForSession(session);

        // Execute system under test.
        session.setAuthToken(authenticatedTokenFor("romeo"));
        final Set<String> result = SASLAuthentication.getScramMechanismsForSession(session);

        // Verify result.
        assertEquals(withChannelBindingVariants(ALL_MECHANISMS), result, "Once authenticated, the mechanisms must be those of the authenticated user, not those derived from the identity that was merely claimed.");
        authFactory.verify(() -> AuthFactory.getScramMechanisms("romeo"), times(1));
    }

    /**
     * Verifies that no per-user lookup is performed when tailoring is disabled by configuration. A session that claims
     * an identity must then be answered with the same mechanisms as any other.
     */
    @Test
    void getScramMechanismsForSession_usesFallbackWhenTailoringIsDisabled()
    {
        // Setup test fixture.
        JiveGlobals.setProperty(SASLAuthentication.SCRAM_MECHANISMS_PER_USER.getKey(), "false");
        final LocalClientSession session = sessionStub();
        session.setClaimedIdentity(new JID("juliet@" + SERVER_NAME));
        authFactory.when(() -> AuthFactory.getScramMechanisms("juliet")).thenReturn(ALL_MECHANISMS);

        // Execute system under test.
        final Set<String> result = SASLAuthentication.getScramMechanismsForSession(session);

        // Verify result.
        assertEquals(withChannelBindingVariants(SHA1_ONLY), result, "With tailoring disabled, a session that claims an identity must be answered with the fallback mechanisms.");
        authFactory.verify(() -> AuthFactory.getScramMechanisms(anyString()), never());
    }

    /**
     * Returns the provided mechanism names, together with the channel binding variant of each.
     *
     * @param mechanisms the mechanism names to expand.
     * @return mechanism names.
     */
    private static Set<String> withChannelBindingVariants(final Set<String> mechanisms)
    {
        final Set<String> result = new HashSet<>();
        mechanisms.forEach(mechanism -> {
            result.add(mechanism);
            result.add(mechanism + "-PLUS");
        });
        return result;
    }

    /**
     * Returns a {@link LocalClientSession} that executes its real method bodies without having run a constructor, so
     * that the claim-related logic can be exercised without the collaborators that a real session requires.
     *
     * The session data accessors are backed by a map supplied here, as the field that normally backs them is populated
     * by a constructor that is not run.
     *
     * @return a session on which the claim- and session-data-related methods can be invoked.
     */
    private static LocalClientSession sessionStub()
    {
        final LocalClientSession session = mock(LocalClientSession.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        doReturn(SERVER_NAME).when(session).getServerName();
        // The real toString() dereferences fields that no constructor has populated here. Stub it so that a failing
        // assertion reports a useful message instead of a NullPointerException raised while formatting one.
        doReturn("LocalClientSession[test stub]").when(session).toString();

        final Map<String, Object> sessionData = new HashMap<>();
        doAnswer(invocation -> sessionData.put(invocation.getArgument(0), invocation.getArgument(1))).when(session).setSessionData(anyString(), any());
        doAnswer(invocation -> sessionData.get(invocation.getArgument(0))).when(session).getSessionData(anyString());

        return session;
    }

    /**
     * Returns an authentication token for a non-anonymous user.
     *
     * @param username the username that the token is to report.
     * @return an authentication token.
     */
    private static AuthToken authenticatedTokenFor(final String username)
    {
        final AuthToken token = mock(AuthToken.class);
        when(token.isAnonymous()).thenReturn(false);
        when(token.getUsername()).thenReturn(username);
        return token;
    }
}
