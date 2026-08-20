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
package org.jivesoftware.openfire.session;

import org.jivesoftware.openfire.auth.AuthToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.xmpp.packet.JID;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Verifies how a session records the (unverified) identity that a peer claims in the 'from' attribute of a stream
 * header, and how {@link LocalClientSession#getExpectedUsername()} reduces that claim to a username.
 *
 * Two properties matter beyond the plain storage:
 *
 * <ul>
 *     <li>A claim is scoped to the stream on which it was made. Recording a null claim <em>clears</em> a previously
 *         recorded one. Making the value 'sticky' would allow a claim made on an unprotected pre-TLS stream to
 *         influence what is advertised on the protected stream that follows it.</li>
 *     <li>An authenticated identity always wins over a claim, and an anonymous session yields no username at all: a
 *         claim must never outlive the negotiation phase in which it is a useful hint.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class LocalClientSessionClaimedIdentityTest
{
    /**
     * Asserts that a session on which no stream header has been processed reports no claimed identity.
     */
    @Test
    public void testFreshSessionHasNoClaim()
    {
        // Setup test fixture.
        final LocalClientSession session = sessionStubFor("example.org");

        // Execute system under test.
        final Optional<JID> result = session.getClaimedIdentity();

        // Verify result.
        assertTrue(result.isEmpty(), "A session on which no stream header has been processed must not report a claimed identity.");
    }

    /**
     * Asserts that a recorded claim is returned unaltered.
     */
    @Test
    public void testRecordedClaimIsReturned()
    {
        // Setup test fixture.
        final LocalClientSession session = sessionStubFor("example.org");

        // Execute system under test.
        session.setClaimedIdentity(new JID("juliet@example.org"));

        // Verify result.
        assertEquals(Optional.of(new JID("juliet@example.org")), session.getClaimedIdentity(), "A recorded claim must be returned unaltered.");
    }

    /**
     * Asserts that the claim made on the most recent stream replaces the one made on an earlier stream.
     */
    @Test
    public void testRecordedClaimReplacesEarlierClaim()
    {
        // Setup test fixture.
        final LocalClientSession session = sessionStubFor("example.org");
        session.setClaimedIdentity(new JID("juliet@example.org"));

        // Execute system under test.
        session.setClaimedIdentity(new JID("romeo@example.org"));

        // Verify result.
        assertEquals(Optional.of(new JID("romeo@example.org")), session.getClaimedIdentity(), "The claim made on the most recent stream must replace the one made on an earlier stream.");
    }

    /**
     * Asserts that recording a null claim clears a claim made on an earlier stream. This is the property that keeps a
     * claim scoped to a single stream: a header that omits the attribute is a peer making no claim at all, which must
     * not be silently backfilled with a claim made earlier.
     */
    @Test
    public void testNullClaimClearsEarlierClaim()
    {
        // Setup test fixture.
        final LocalClientSession session = sessionStubFor("example.org");
        session.setClaimedIdentity(new JID("juliet@example.org"));

        // Execute system under test.
        session.setClaimedIdentity(null);

        // Verify result.
        assertTrue(session.getClaimedIdentity().isEmpty(), "Recording a null claim must clear a claim made on an earlier stream, rather than retaining it.");
    }

    /**
     * Asserts that the node of a claim for the local domain is reported as the expected username.
     */
    @Test
    public void testExpectedUsernameFromLocalClaim()
    {
        // Setup test fixture.
        final LocalClientSession session = sessionStubFor("example.org");
        session.setClaimedIdentity(new JID("juliet@example.org"));

        // Execute system under test.
        final Optional<String> result = session.getExpectedUsername();

        // Verify result.
        assertEquals(Optional.of("juliet"), result, "The node of a claim for the local domain must be reported as the expected username.");
    }

    /**
     * Asserts that a resource in the claim does not affect the expected username.
     */
    @Test
    public void testExpectedUsernameIgnoresResource()
    {
        // Setup test fixture.
        final LocalClientSession session = sessionStubFor("example.org");
        session.setClaimedIdentity(new JID("juliet@example.org/balcony"));

        // Execute system under test.
        final Optional<String> result = session.getExpectedUsername();

        // Verify result.
        assertEquals(Optional.of("juliet"), result, "A resource in the claim must not affect the expected username.");
    }

    /**
     * Asserts that a claim naming a domain that this server does not serve is discarded. Credentials are looked up per
     * local user, so such a claim cannot identify one and its node must not be used as if it were local.
     */
    @Test
    public void testExpectedUsernameForForeignDomainClaim()
    {
        // Setup test fixture.
        final LocalClientSession session = sessionStubFor("example.org");
        session.setClaimedIdentity(new JID("juliet@example.com"));

        // Execute system under test.
        final Optional<String> result = session.getExpectedUsername();

        // Verify result.
        assertTrue(result.isEmpty(), "A claim for a domain other than that of the session must not yield an expected username.");
    }

    /**
     * Asserts that a claim without a node part yields no expected username, as it identifies no user.
     */
    @Test
    public void testExpectedUsernameForDomainOnlyClaim()
    {
        // Setup test fixture.
        final LocalClientSession session = sessionStubFor("example.org");
        session.setClaimedIdentity(new JID("example.org"));

        // Execute system under test.
        final Optional<String> result = session.getExpectedUsername();

        // Verify result.
        assertTrue(result.isEmpty(), "A claim without a node part identifies no user and must not yield an expected username.");
    }

    /**
     * Asserts that a session for which no claim was made reports no expected username.
     */
    @Test
    public void testExpectedUsernameWithoutClaim()
    {
        // Setup test fixture.
        final LocalClientSession session = sessionStubFor("example.org");

        // Execute system under test.
        final Optional<String> result = session.getExpectedUsername();

        // Verify result.
        assertTrue(result.isEmpty(), "A session for which no claim was made must not report an expected username.");
    }

    /**
     * Asserts that the authenticated username takes precedence over an identity that the peer merely claimed. Once
     * authentication has completed the verified identity is authoritative, and a peer must not be able to make the
     * server report a different username by having claimed one earlier.
     */
    @Test
    public void testExpectedUsernamePrefersAuthenticatedIdentity()
    {
        // Setup test fixture.
        final LocalClientSession session = sessionStubFor("example.org");
        session.setClaimedIdentity(new JID("romeo@example.org"));
        session.setAuthToken(authenticatedTokenFor("juliet"));

        // Execute system under test.
        final Optional<String> result = session.getExpectedUsername();

        // Verify result.
        assertEquals(Optional.of("juliet"), result, "The authenticated username must take precedence over an identity the peer merely claimed.");
    }

    /**
     * Asserts that an anonymously authenticated session reports no expected username. There is no user account behind
     * such a session, so an earlier claim has no meaning and must not outlive the negotiation phase.
     */
    @Test
    public void testExpectedUsernameForAnonymousSession()
    {
        // Setup test fixture.
        final LocalClientSession session = sessionStubFor("example.org");
        session.setClaimedIdentity(new JID("juliet@example.org"));
        session.setAuthToken(anonymousToken());

        // Execute system under test.
        final Optional<String> result = session.getExpectedUsername();

        // Verify result.
        assertTrue(result.isEmpty(), "An anonymously authenticated session must not report an expected username, claim or no claim.");
    }

    /**
     * Returns a {@link LocalClientSession} that executes its real method bodies without having run a constructor, so
     * that the claim-related logic can be exercised without the {@link org.jivesoftware.openfire.SessionManager} and
     * {@link org.jivesoftware.openfire.streammanagement.StreamManager} collaborators that a real session requires.
     *
     * Only the accessors that the code under test reads from otherwise-uninitialised final fields are stubbed.
     *
     * @param serverName the domain that the session is to report as its own.
     * @return a session on which the claim-related methods can be invoked.
     */
    private static LocalClientSession sessionStubFor(final String serverName)
    {
        final LocalClientSession session = mock(LocalClientSession.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        doReturn(serverName).when(session).getServerName();
        // The real toString() dereferences fields that no constructor has populated here. Stub it so that a failing
        // assertion reports a useful message instead of a NullPointerException raised while formatting one.
        doReturn("LocalClientSession[test stub]").when(session).toString();
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

    /**
     * Returns an authentication token for an anonymous user.
     *
     * @return an authentication token.
     */
    private static AuthToken anonymousToken()
    {
        final AuthToken token = mock(AuthToken.class);
        when(token.isAnonymous()).thenReturn(true);
        return token;
    }
}
