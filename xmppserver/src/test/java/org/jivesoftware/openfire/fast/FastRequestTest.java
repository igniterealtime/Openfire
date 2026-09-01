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
package org.jivesoftware.openfire.fast;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.sasl.Failure;
import org.jivesoftware.openfire.sasl.SaslFailureException;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link FastRequest}, which parses the XEP-0484 content of a SASL2 authenticate element.
 *
 * Two properties are defended here. The first is that every value a client can put in that element is validated
 * before it is acted on: the mechanism it asks a token for, the replay counter, the invalidation flag, and the
 * identity prerequisites of XEP-0484 § 4.1. The second is that nothing at all is recorded on the session when any of
 * that validation fails, so that a rejected attempt cannot leave state behind for a later one on the same stream to
 * pick up.
 *
 * The mechanism named in the authenticate element's own 'mechanism' attribute is passed separately by the caller, so
 * these tests vary it independently of the element.
 */
class FastRequestTest
{
    private static final String USERNAME = "test-user";
    private static final String USER_AGENT_ID = "123e4567-e89b-42d3-a456-426614174000";
    private static final String OFFERED = FastTokenManager.HT_SHA_256_NONE;
    private static final String NOT_OFFERED = FastTokenManager.HT_SHA_512_NONE;
    private static final String PASSWORD_MECHANISM = "PLAIN";

    @BeforeAll
    public static void setupClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @AfterAll
    public static void tearDownClass()
    {
        Fixtures.clearExistingProperties();
    }

    @BeforeEach
    public void setup()
    {
        Fixtures.clearExistingProperties();
        XMPPServer.setInstance(Fixtures.mockXMPPServer());
    }

    @AfterEach
    void tearDown()
    {
        FastTokenManager.ENABLE_FAST.setValue(FastTokenManager.ENABLE_FAST.getDefaultValue());
    }

    // -------------------------------------------------------------------------
    // Nothing to parse
    // -------------------------------------------------------------------------

    /**
     * An element with no FAST content, on a mechanism that is not a FAST mechanism, must yield nothing for the caller
     * to apply.
     */
    @Test
    void anElementWithoutFastContentYieldsNothing() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = session(USERNAME);

        // Execute system under test.
        final FastRequest result = FastRequest.from(authenticate(""), PASSWORD_MECHANISM, USER_AGENT_ID, session);

        // Verify result.
        assertNull(result, "An authenticate element carrying no FAST content must not produce a request.");
    }

    // -------------------------------------------------------------------------
    // Identity prerequisites (XEP-0484 § 4.1)
    // -------------------------------------------------------------------------

    /**
     * A token request must record the identity that a later FAST authentication will be checked against.
     */
    @Test
    void aTokenRequestRecordsTheClientIdentity() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = session(USERNAME);
        FastSessionState.setAdvertisedMechanisms(session, Set.of(OFFERED));

        // Execute system under test.
        final FastRequest result = FastRequest.from(authenticate(requestToken(OFFERED)), PASSWORD_MECHANISM, USER_AGENT_ID, session);
        assertNotNull(result, "A <request-token/> element must produce a request.");
        result.applyTo(session);

        // Verify result.
        assertEquals(OFFERED, FastSessionState.getRequestedMechanism(session), "The requested mechanism must be recorded.");
        assertEquals(USERNAME, FastSessionState.getExpectedUsername(session), "The username claimed by the stream must be recorded.");
        assertEquals(USER_AGENT_ID, FastSessionState.getClientId(session), "The user-agent id must be recorded, as tokens are issued per client.");
    }

    /**
     * A client that supplies no user-agent id cannot be issued or matched against a token, as tokens are held per
     * client.
     *
     * @param mechanismName the mechanism named in the authenticate element
     */
    @ParameterizedTest
    @ValueSource(strings = {PASSWORD_MECHANISM, FastTokenManager.HT_SHA_256_NONE})
    void anAbsentUserAgentIdIsRejected(final String mechanismName) throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = session(USERNAME);
        FastSessionState.setAdvertisedMechanisms(session, Set.of(OFFERED));
        final Element doc = authenticate(requestToken(OFFERED) + fast(""));

        // Execute system under test.
        final SaslFailureException error = assertThrows(SaslFailureException.class,
            () -> FastRequest.from(doc, mechanismName, null, session));

        // Verify result.
        assertEquals(Failure.MALFORMED_REQUEST, error.getFailure(),
            "A FAST request without a user-agent id must be rejected as malformed.");
    }

    /**
     * A client that claims no local identity in the stream's 'from' attribute cannot be matched against a token.
     *
     * @param mechanismName the mechanism named in the authenticate element
     */
    @ParameterizedTest
    @ValueSource(strings = {PASSWORD_MECHANISM, FastTokenManager.HT_SHA_256_NONE})
    void anAbsentClaimedIdentityIsRejected(final String mechanismName) throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = session(null);
        FastSessionState.setAdvertisedMechanisms(session, Set.of(OFFERED));
        final Element doc = authenticate(requestToken(OFFERED) + fast(""));

        // Execute system under test.
        final SaslFailureException error = assertThrows(SaslFailureException.class,
            () -> FastRequest.from(doc, mechanismName, USER_AGENT_ID, session));

        // Verify result.
        assertEquals(Failure.MALFORMED_REQUEST, error.getFailure(),
            "A FAST request without a local authenticating JID in the stream's 'from' attribute must be rejected as malformed.");
    }

    // -------------------------------------------------------------------------
    // <request-token/>
    // -------------------------------------------------------------------------

    /**
     * A token request that names no mechanism cannot be honoured, as a token is bound to the mechanism it was issued
     * for.
     */
    @Test
    void aTokenRequestWithoutAMechanismIsRejected() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = session(USERNAME);
        FastSessionState.setAdvertisedMechanisms(session, Set.of(OFFERED));
        final Element doc = authenticate("<request-token xmlns='" + FastTokenManager.NAMESPACE + "'/>");

        // Execute system under test.
        final SaslFailureException error = assertThrows(SaslFailureException.class,
            () -> FastRequest.from(doc, PASSWORD_MECHANISM, USER_AGENT_ID, session));

        // Verify result.
        assertEquals(Failure.MALFORMED_REQUEST, error.getFailure(),
            "A <request-token/> element without a 'mechanism' attribute must be rejected as malformed.");
    }

    /**
     * A token request naming something that is not a FAST mechanism at all is malformed, rather than a request for a
     * mechanism that happens not to be available.
     */
    @Test
    void aTokenRequestNamingAnUnknownMechanismIsRejected() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = session(USERNAME);
        FastSessionState.setAdvertisedMechanisms(session, Set.of(OFFERED));
        final Element doc = authenticate(requestToken("NOT-A-FAST-MECHANISM"));

        // Execute system under test.
        final SaslFailureException error = assertThrows(SaslFailureException.class,
            () -> FastRequest.from(doc, PASSWORD_MECHANISM, USER_AGENT_ID, session));

        // Verify result.
        assertEquals(Failure.MALFORMED_REQUEST, error.getFailure(),
            "A <request-token/> element naming something outside the HT families must be rejected as malformed.");
    }

    /**
     * A token request naming a real FAST mechanism that this session was not offered must be refused, so that a peer
     * cannot obtain a credential for a mechanism it was never eligible to use.
     */
    @Test
    void aTokenRequestNamingAnUnadvertisedMechanismIsRejected() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = session(USERNAME);
        FastSessionState.setAdvertisedMechanisms(session, Set.of(OFFERED));
        final Element doc = authenticate(requestToken(NOT_OFFERED));

        // Execute system under test.
        final SaslFailureException error = assertThrows(SaslFailureException.class,
            () -> FastRequest.from(doc, PASSWORD_MECHANISM, USER_AGENT_ID, session));

        // Verify result.
        assertEquals(Failure.INVALID_MECHANISM, error.getFailure(),
            "A <request-token/> element naming a mechanism that was not offered to this session must be refused.");
    }

    /**
     * A token request made to a session that was offered nothing at all must be refused, rather than treated as a
     * request that simply names no mechanism.
     */
    @Test
    void aTokenRequestIsRejectedWhenNothingWasAdvertised() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = session(USERNAME);
        final Element doc = authenticate(requestToken(OFFERED));

        // Execute system under test.
        final SaslFailureException error = assertThrows(SaslFailureException.class,
            () -> FastRequest.from(doc, PASSWORD_MECHANISM, USER_AGENT_ID, session));

        // Verify result.
        assertEquals(Failure.INVALID_MECHANISM, error.getFailure(),
            "A <request-token/> element must be refused on a session that was advertised no FAST mechanisms.");
    }

    /**
     * A token request must be refused while FAST is disabled, even for a mechanism that a session was offered before
     * the setting changed.
     */
    @Test
    void aTokenRequestIsRejectedWhenFastIsDisabled() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = session(USERNAME);
        FastSessionState.setAdvertisedMechanisms(session, Set.of(OFFERED));
        FastTokenManager.ENABLE_FAST.setValue(false);
        final Element doc = authenticate(requestToken(OFFERED));

        // Execute system under test.
        final SaslFailureException error = assertThrows(SaslFailureException.class,
            () -> FastRequest.from(doc, PASSWORD_MECHANISM, USER_AGENT_ID, session));

        // Verify result.
        assertEquals(Failure.INVALID_MECHANISM, error.getFailure(),
            "A <request-token/> element must be refused while FAST is disabled.");
    }

    /**
     * The requested mechanism must be normalized to upper case, as that is the form that tokens are stored and looked
     * up under.
     */
    @Test
    void theRequestedMechanismIsNormalizedToUpperCase() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = session(USERNAME);
        FastSessionState.setAdvertisedMechanisms(session, Set.of(OFFERED));
        final Element doc = authenticate(requestToken(OFFERED.toLowerCase()));

        // Execute system under test.
        final FastRequest result = FastRequest.from(doc, PASSWORD_MECHANISM, USER_AGENT_ID, session);

        // Verify result.
        assertNotNull(result, "A <request-token/> element naming a mechanism in lower case must be accepted.");
        assertEquals(OFFERED, result.getRequestedMechanism(),
            "The requested mechanism must be normalized to the form that tokens are stored under.");
    }

    // -------------------------------------------------------------------------
    // <fast/>
    // -------------------------------------------------------------------------

    /**
     * A FAST authentication must carry the marker element that says a token is being presented.
     */
    @Test
    void aFastAuthenticationWithoutTheFastElementIsRejected() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = session(USERNAME);
        FastSessionState.setAdvertisedMechanisms(session, Set.of(OFFERED));
        final Element doc = authenticate("");

        // Execute system under test.
        final SaslFailureException error = assertThrows(SaslFailureException.class,
            () -> FastRequest.from(doc, OFFERED, USER_AGENT_ID, session));

        // Verify result.
        assertEquals(Failure.MALFORMED_REQUEST, error.getFailure(),
            "A FAST authentication without a <fast/> element must be rejected as malformed.");
    }

    /**
     * A valid replay counter must be recorded, as it is compared against the counter stored with the token.
     */
    @Test
    void aPositiveReplayCounterIsRecorded() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = session(USERNAME);
        FastSessionState.setAdvertisedMechanisms(session, Set.of(OFFERED));

        // Execute system under test.
        final FastRequest result = FastRequest.from(authenticate(fast(" count='7'")), OFFERED, USER_AGENT_ID, session);
        assertNotNull(result, "A FAST authentication must produce a request.");
        result.applyTo(session);

        // Verify result.
        assertEquals(7L, result.getReplayCount(), "The replay counter must be parsed from the 'count' attribute.");
        assertEquals(7L, FastSessionState.getReplayCount(session), "The replay counter must be recorded on the session.");
    }

    /**
     * XEP-0484 requires the counter to be a positive integer. A value that is not one must be refused rather than
     * silently ignored, which would leave the client believing it had replay protection that it does not have.
     *
     * @param countAttribute the value of the 'count' attribute
     */
    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "not-a-number", "", "1.5", "9999999999999999999999"})
    void aCounterThatIsNotAPositiveIntegerIsRejected(final String countAttribute) throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = session(USERNAME);
        FastSessionState.setAdvertisedMechanisms(session, Set.of(OFFERED));
        final Element doc = authenticate(fast(" count='" + countAttribute + "'"));

        // Execute system under test.
        final SaslFailureException error = assertThrows(SaslFailureException.class,
            () -> FastRequest.from(doc, OFFERED, USER_AGENT_ID, session));

        // Verify result.
        assertEquals(Failure.MALFORMED_REQUEST, error.getFailure(),
            "A 'count' attribute that is not a positive integer must be rejected as malformed.");
    }

    /**
     * An absent counter is permitted: XEP-0484 requires one only for requests carried in TLS 0-RTT early data, which
     * Openfire does not accept.
     */
    @Test
    void anAbsentReplayCounterIsPermitted() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = session(USERNAME);
        FastSessionState.setAdvertisedMechanisms(session, Set.of(OFFERED));

        // Execute system under test.
        final FastRequest result = FastRequest.from(authenticate(fast("")), OFFERED, USER_AGENT_ID, session);

        // Verify result.
        assertNotNull(result, "A FAST authentication without a counter must be accepted.");
        assertNull(result.getReplayCount(), "No counter may be reported when the client supplied none.");
    }

    /**
     * Both spellings of the invalidation flag that XEP-0484 § 3.6 permits must be honoured.
     *
     * @param invalidateAttribute the value of the 'invalidate' attribute
     */
    @ParameterizedTest
    @ValueSource(strings = {"true", "TRUE", "True", "1"})
    void invalidationIsRecognizedInEveryPermittedSpelling(final String invalidateAttribute) throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = session(USERNAME);
        FastSessionState.setAdvertisedMechanisms(session, Set.of(OFFERED));

        // Execute system under test.
        final FastRequest result = FastRequest.from(
            authenticate(fast(" invalidate='" + invalidateAttribute + "'")), OFFERED, USER_AGENT_ID, session);
        assertNotNull(result, "A FAST authentication must produce a request.");
        result.applyTo(session);

        // Verify result.
        assertTrue(result.isInvalidate(), "The 'invalidate' attribute must be honoured in every permitted spelling.");
        assertTrue(FastSessionState.isInvalidateRequested(session), "Invalidation must be recorded on the session.");
    }

    /**
     * Anything other than a permitted affirmative spelling leaves the token in place. Invalidating a token that the
     * client did not ask to invalidate would log out a working client.
     *
     * @param invalidateAttribute the value of the 'invalidate' attribute
     */
    @ParameterizedTest
    @ValueSource(strings = {"false", "0", "", "yes"})
    void anythingElseDoesNotInvalidate(final String invalidateAttribute) throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = session(USERNAME);
        FastSessionState.setAdvertisedMechanisms(session, Set.of(OFFERED));

        // Execute system under test.
        final FastRequest result = FastRequest.from(
            authenticate(fast(" invalidate='" + invalidateAttribute + "'")), OFFERED, USER_AGENT_ID, session);
        assertNotNull(result, "A FAST authentication must produce a request.");
        result.applyTo(session);

        // Verify result.
        assertFalse(result.isInvalidate(), "Only the spellings that XEP-0484 permits may request invalidation.");
        assertFalse(FastSessionState.isInvalidateRequested(session), "Invalidation must not be recorded on the session.");
    }

    /**
     * A {@code <fast/>} element on a password authentication carries no token, so the identity prerequisites do not
     * apply to it and no client is recorded. Its attributes are still parsed.
     */
    @Test
    void aFastElementOnAPasswordAuthenticationNeedsNoIdentity() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = session(null);

        // Execute system under test.
        final FastRequest result = FastRequest.from(
            authenticate(fast(" count='3' invalidate='true'")), PASSWORD_MECHANISM, null, session);
        assertNotNull(result, "A <fast/> element must produce a request even on a password authentication.");
        result.applyTo(session);

        // Verify result.
        assertEquals(3L, result.getReplayCount(), "The counter must be parsed regardless of the selected mechanism.");
        assertTrue(result.isInvalidate(), "The invalidation flag must be parsed regardless of the selected mechanism.");
        assertNull(result.getClientId(), "No client may be recorded for a request that carried no identity prerequisites.");
        assertNull(FastSessionState.getClientId(session), "No client may be recorded on the session.");
        assertNull(FastSessionState.getExpectedUsername(session), "No expected username may be recorded on the session.");
    }

    // -------------------------------------------------------------------------
    // Parsing does not write
    // -------------------------------------------------------------------------

    /**
     * A rejected request must leave nothing behind on the session.
     *
     * SASL2 permits more than one authenticate element on a stream, so state written by an attempt that then failed
     * could otherwise be read by the next one, which supplied no such value itself.
     */
    @Test
    void aRejectedRequestRecordsNothing() throws Exception
    {
        // Setup test fixture: identity prerequisites are met, so parsing reaches the invalid mechanism and fails there.
        final LocalClientSession session = session(USERNAME);
        FastSessionState.setAdvertisedMechanisms(session, Set.of(OFFERED));
        final Element doc = authenticate(requestToken(NOT_OFFERED) + fast(" count='4' invalidate='true'"));

        // Execute system under test.
        assertThrows(SaslFailureException.class, () -> FastRequest.from(doc, PASSWORD_MECHANISM, USER_AGENT_ID, session));

        // Verify result.
        assertNull(FastSessionState.getExpectedUsername(session), "A rejected request must not record an expected username.");
        assertNull(FastSessionState.getClientId(session), "A rejected request must not record a client.");
        assertNull(FastSessionState.getRequestedMechanism(session), "A rejected request must not record a requested mechanism.");
        assertNull(FastSessionState.getReplayCount(session), "A rejected request must not record a replay counter.");
        assertFalse(FastSessionState.isInvalidateRequested(session), "A rejected request must not record an invalidation.");
    }

    /**
     * Parsing alone must record nothing; the caller decides when to apply what was parsed.
     */
    @Test
    void parsingWithoutApplyingRecordsNothing() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = session(USERNAME);
        FastSessionState.setAdvertisedMechanisms(session, Set.of(OFFERED));

        // Execute system under test.
        final FastRequest result = FastRequest.from(
            authenticate(requestToken(OFFERED) + fast(" count='2'")), OFFERED, USER_AGENT_ID, session);

        // Verify result.
        assertNotNull(result, "A valid request must be produced.");
        assertNull(FastSessionState.getRequestedMechanism(session), "Parsing must not record the requested mechanism.");
        assertNull(FastSessionState.getClientId(session), "Parsing must not record the client.");
        assertNull(FastSessionState.getReplayCount(session), "Parsing must not record the replay counter.");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns a SASL2 authenticate element wrapping the given inline XML.
     *
     * The 'mechanism' attribute is irrelevant, as the selected mechanism is passed to the parser separately.
     *
     * @param inlineXml the FAST content, possibly empty
     * @return an authenticate element
     */
    private static Element authenticate(final String inlineXml) throws Exception
    {
        return DocumentHelper.parseText("<authenticate xmlns='urn:xmpp:sasl:2' mechanism='PLAIN'>"
            + "<initial-response/>" + inlineXml + "</authenticate>").getRootElement();
    }

    private static String requestToken(final String mechanismName)
    {
        return "<request-token xmlns='" + FastTokenManager.NAMESPACE + "' mechanism='" + mechanismName + "'/>";
    }

    private static String fast(final String attributes)
    {
        return "<fast xmlns='" + FastTokenManager.NAMESPACE + "'" + attributes + "/>";
    }

    /**
     * Returns a session whose stream claims the given username, and whose session data is backed by a map.
     *
     * @param expectedUsername the username the stream's 'from' attribute resolves to, or null if it resolves to none
     * @return a session usable for parsing
     */
    private static LocalClientSession session(final String expectedUsername)
    {
        final LocalClientSession session = mock(LocalClientSession.class);
        final Map<String, Object> data = new HashMap<>();
        doAnswer(invocation -> data.put(invocation.getArgument(0), invocation.getArgument(1)))
            .when(session).setSessionData(anyString(), any());
        when(session.getSessionData(anyString())).thenAnswer(invocation -> data.get(invocation.getArgument(0)));
        when(session.removeSessionData(anyString())).thenAnswer(invocation -> data.remove(invocation.getArgument(0)));
        when(session.getExpectedUsername()).thenReturn(Optional.ofNullable(expectedUsername));
        return session;
    }
}
