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
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.fast.FastToken;
import org.jivesoftware.openfire.fast.FastTokenManager;
import org.jivesoftware.openfire.sasl.Failure;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link SaslOutcome}, which renders and delivers the elements that conclude or advance a SASL negotiation.
 *
 * These assertions parse what was delivered rather than matching substrings of it, so that they cover the namespace a
 * child element ends up in as well as its name. That distinction matters most for a SASL2 failure, whose wrapper is in
 * the SASL2 namespace while the error condition it carries stays in the namespace that RFC 6120 defines.
 *
 * The failure path also counts consecutive failures and eventually closes the session. That policy cannot be reached
 * from {@code authenticationSuccessful}, and reaching it through {@code handle} would mean driving a negotiation to
 * failure repeatedly, so it is exercised here directly.
 */
class SaslOutcomeTest
{
    private static final String SASL_NAMESPACE = "urn:ietf:params:xml:ns:xmpp-sasl";
    private static final String SASL2_NAMESPACE = "urn:xmpp:sasl:2";
    private static final String IDENTITY = "user@example.org";
    private static final byte[] PAYLOAD = "payload".getBytes(StandardCharsets.UTF_8);

    private LocalSession session;

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

        session = mock(LocalSession.class);
        final Map<String, Object> sessionData = new HashMap<>();
        doAnswer(invocation -> sessionData.put(invocation.getArgument(0), invocation.getArgument(1)))
            .when(session).setSessionData(anyString(), any());
        when(session.getSessionData(anyString())).thenAnswer(invocation -> sessionData.get(invocation.getArgument(0)));
    }

    @AfterEach
    void tearDown()
    {
        JiveGlobals.deleteProperty("xmpp.auth.retries");
    }

    // -------------------------------------------------------------------------
    // Challenges
    // -------------------------------------------------------------------------

    /**
     * A challenge carries its mechanism data as base64 text, in the namespace of the profile in use.
     */
    @Test
    void aSasl1ChallengeCarriesBase64DataInTheSaslNamespace() throws Exception
    {
        // Setup test fixture.
        // (none required)

        // Execute system under test.
        SaslOutcome.sendChallenge(session, PAYLOAD, false);

        // Verify result.
        final Element challenge = delivered();
        assertEquals("challenge", challenge.getName(), "A challenge must be delivered as a <challenge/> element.");
        assertEquals(SASL_NAMESPACE, challenge.getNamespaceURI(), "A SASL1 challenge must be in the RFC 6120 SASL namespace.");
        assertEquals(Base64.getEncoder().encodeToString(PAYLOAD), challenge.getTextTrim(),
            "The challenge must carry the mechanism data as base64.");
    }

    /**
     * The SASL2 profile reuses the same element in its own namespace.
     */
    @Test
    void aSasl2ChallengeIsInTheSasl2Namespace() throws Exception
    {
        // Setup test fixture.
        // (none required)

        // Execute system under test.
        SaslOutcome.sendChallenge(session, PAYLOAD, true);

        // Verify result.
        final Element challenge = delivered();
        assertEquals("challenge", challenge.getName(), "A challenge must be delivered as a <challenge/> element.");
        assertEquals(SASL2_NAMESPACE, challenge.getNamespaceURI(), "A SASL2 challenge must be in the SASL2 namespace.");
    }

    /**
     * A mechanism that challenges without data produces an empty element, not one carrying the empty-payload
     * sentinel. RFC 6120 § 6.4.2 uses that sentinel to mean an empty string, which is a different thing from no data.
     */
    @Test
    void aChallengeWithoutDataCarriesNoText() throws Exception
    {
        // Setup test fixture.
        // (none required)

        // Execute system under test.
        SaslOutcome.sendChallenge(session, null, false);

        // Verify result.
        assertEquals("", delivered().getTextTrim(), "A challenge with no data must carry no text at all.");
    }

    // -------------------------------------------------------------------------
    // RFC 6120 success
    // -------------------------------------------------------------------------

    /**
     * A SASL1 success carries its mechanism data as base64 text.
     */
    @Test
    void aSasl1SuccessCarriesBase64Data() throws Exception
    {
        // Setup test fixture.
        // (none required)

        // Execute system under test.
        SaslOutcome.sendSuccess(session, PAYLOAD);

        // Verify result.
        final Element success = delivered();
        assertEquals("success", success.getName(), "A SASL1 success must be delivered as a <success/> element.");
        assertEquals(SASL_NAMESPACE, success.getNamespaceURI(), "A SASL1 success must be in the RFC 6120 SASL namespace.");
        assertEquals(Base64.getEncoder().encodeToString(PAYLOAD), success.getTextTrim(),
            "The success must carry the mechanism data as base64.");
    }

    /**
     * A mechanism that completes with an empty string, rather than with no data, must be distinguishable from one that
     * completes with none. RFC 6120 § 6.4.2 spells the former as a single equals sign.
     */
    @Test
    void aSasl1SuccessWithEmptyDataUsesTheEmptyPayloadSentinel() throws Exception
    {
        // Setup test fixture.
        final byte[] emptyButPresent = new byte[0];

        // Execute system under test.
        SaslOutcome.sendSuccess(session, emptyButPresent);

        // Verify result.
        assertEquals("=", delivered().getTextTrim(),
            "Success data that is present but empty must be spelled as the '=' sentinel, so that it remains " +
                "distinguishable from success data that is absent.");
    }

    /**
     * A mechanism that completes with no data at all produces an empty element.
     */
    @Test
    void aSasl1SuccessWithoutDataCarriesNoText() throws Exception
    {
        // Setup test fixture.
        // (none required)

        // Execute system under test.
        SaslOutcome.sendSuccess(session, null);

        // Verify result.
        assertEquals("", delivered().getTextTrim(), "A success with no data must carry no text at all.");
    }

    // -------------------------------------------------------------------------
    // SASL2 success
    // -------------------------------------------------------------------------

    /**
     * The minimal SASL2 success is the authorization identity alone. XEP-0388 § 2.6.1 makes that element mandatory.
     */
    @Test
    void aMinimalSasl2SuccessCarriesOnlyTheAuthorizationIdentifier()
    {
        // Setup test fixture.
        // (none required)

        // Execute system under test.
        final Element success = SaslOutcome.buildSasl2SuccessElement(null, IDENTITY, null, null);

        // Verify result.
        assertEquals("success", success.getName(), "The element must be a <success/>.");
        assertEquals(SASL2_NAMESPACE, success.getNamespaceURI(), "A SASL2 success must be in the SASL2 namespace.");
        assertEquals(IDENTITY, success.elementText("authorization-identifier"),
            "The negotiated identity must be reported.");
        assertNull(success.element("additional-data"), "No additional data may be reported when the mechanism supplied none.");
    }

    /**
     * Mechanism success data is carried in an additional-data element, ahead of the identity, as XEP-0388's schema
     * requires.
     */
    @Test
    void mechanismSuccessDataPrecedesTheAuthorizationIdentifier()
    {
        // Setup test fixture.
        // (none required)

        // Execute system under test.
        final Element success = SaslOutcome.buildSasl2SuccessElement(PAYLOAD, IDENTITY, null, null);

        // Verify result.
        assertEquals(Base64.getEncoder().encodeToString(PAYLOAD), success.elementTextTrim("additional-data"),
            "Mechanism success data must be carried as base64 in an <additional-data/> element.");
        final List<Element> children = success.elements();
        assertEquals("additional-data", children.get(0).getName(),
            "Additional data must precede the authorization identifier, as the SASL2 schema requires.");
        assertEquals("authorization-identifier", children.get(1).getName(),
            "The authorization identifier must follow the additional data.");
    }

    /**
     * A mechanism that completes with an empty byte array reports no additional data. Unlike the RFC 6120 profile,
     * SASL2 carries the data in an element that can simply be omitted, so no sentinel is needed.
     */
    @Test
    void emptySuccessDataIsReportedAsNoAdditionalData()
    {
        // Setup test fixture.
        final byte[] emptyButPresent = new byte[0];

        // Execute system under test.
        final Element success = SaslOutcome.buildSasl2SuccessElement(emptyButPresent, IDENTITY, null, null);

        // Verify result.
        assertNull(success.element("additional-data"),
            "Empty success data must be reported by omitting the element, rather than as an empty or sentinel value.");
    }

    /**
     * When a resource was bound inline, the reported identity is the full JID rather than the bare one.
     */
    @Test
    void aBoundResourceIsAppendedToTheAuthorizationIdentifier()
    {
        // Setup test fixture.
        final String resource = "AwesomeXMPP.4232f4d4";

        // Execute system under test.
        final Element success = SaslOutcome.buildSasl2SuccessElement(null, IDENTITY, resource, null);

        // Verify result.
        assertEquals(IDENTITY + "/" + resource, success.elementText("authorization-identifier"),
            "A bound resource must be reported as part of the authorization identity.");
    }

    /**
     * A FAST token that was issued or rotated is reported alongside the identity, as XEP-0484 § 3.3 requires.
     */
    @Test
    void anIssuedFastTokenIsReported()
    {
        // Setup test fixture.
        final Instant expiry = Instant.now().plusSeconds(86400).truncatedTo(ChronoUnit.MILLIS);
        final FastToken token = new FastToken("user", FastTokenManager.HT_SHA_256_NONE,
            "issued-token".getBytes(StandardCharsets.UTF_8), expiry);

        // Execute system under test.
        final Element success = SaslOutcome.buildSasl2SuccessElement(null, IDENTITY, null, token);

        // Verify result.
        final Element tokenEl = success.element("token");
        assertNotNull(tokenEl, "An issued FAST token must be reported in the success response.");
        assertEquals(FastTokenManager.NAMESPACE, tokenEl.getNamespaceURI(), "The token must be in the FAST namespace.");
        assertEquals("issued-token", tokenEl.attributeValue("token"),
            "The token attribute must carry the token exactly as the client is to present it.");
        assertEquals(XMPPDateTimeFormat.format(Date.from(expiry)), tokenEl.attributeValue("expiry"),
            "The expiry of the issued token must be reported, formatted as a XEP-0082 DateTime.");
    }

    /**
     * No token element appears when none was issued, so that a client is not led to replace a working token.
     */
    @Test
    void noTokenIsReportedWhenNoneWasIssued()
    {
        // Setup test fixture.
        // (none required)

        // Execute system under test.
        final Element success = SaslOutcome.buildSasl2SuccessElement(PAYLOAD, IDENTITY, "resource", null);

        // Verify result.
        assertNull(success.element("token"), "No FAST token may be reported when none was issued.");
    }

    // -------------------------------------------------------------------------
    // Failure
    // -------------------------------------------------------------------------

    /**
     * A SASL1 failure carries its error condition in the RFC 6120 SASL namespace.
     */
    @Test
    void aSasl1FailureCarriesItsConditionInTheSaslNamespace() throws Exception
    {
        // Setup test fixture.
        // (none required)

        // Execute system under test.
        SaslOutcome.authenticationFailed(session, Failure.NOT_AUTHORIZED, false);

        // Verify result.
        final Element failure = delivered();
        assertEquals("failure", failure.getName(), "A failure must be delivered as a <failure/> element.");
        assertEquals(SASL_NAMESPACE, failure.getNamespaceURI(), "A SASL1 failure must be in the RFC 6120 SASL namespace.");
        final Element condition = (Element) failure.elements().get(0);
        assertEquals("not-authorized", condition.getName(), "The error condition must be reported.");
        assertEquals(SASL_NAMESPACE, condition.getNamespaceURI(), "The condition must be in the RFC 6120 SASL namespace.");
    }

    /**
     * A SASL2 failure is wrapped in the SASL2 namespace but continues to report the error condition in the namespace
     * that RFC 6120 defines, which XEP-0388 § 2.6.2 requires it to carry.
     */
    @Test
    void aSasl2FailureWrapsAConditionThatStaysInTheSaslNamespace() throws Exception
    {
        // Setup test fixture.
        // (none required)

        // Execute system under test.
        SaslOutcome.authenticationFailed(session, Failure.INVALID_MECHANISM, true);

        // Verify result.
        final Element failure = delivered();
        assertEquals(SASL2_NAMESPACE, failure.getNamespaceURI(), "A SASL2 failure must be wrapped in the SASL2 namespace.");
        final Element condition = (Element) failure.elements().get(0);
        assertEquals("invalid-mechanism", condition.getName(), "The error condition must be reported.");
        assertEquals(SASL_NAMESPACE, condition.getNamespaceURI(),
            "SASL2 continues to use the RFC 6120 SASL namespace for error conditions, so a condition in the SASL2 " +
                "namespace would not be recognized by a peer.");
    }

    /**
     * No additional-data element appears when the mechanism supplied none.
     */
    @Test
    void noFailureDataIsReportedWhenTheMechanismSuppliedNone() throws Exception
    {
        // Setup test fixture.
        // (none required)

        // Execute system under test.
        SaslOutcome.authenticationFailed(session, Failure.NOT_AUTHORIZED, true);

        // Verify result.
        assertNull(delivered().element("additional-data"),
            "No additional data may be reported when the mechanism supplied none.");
    }

    // -------------------------------------------------------------------------
    // Retry policy
    // -------------------------------------------------------------------------

    /**
     * A single failure leaves the session open, so that the peer can try another mechanism or another password.
     */
    @Test
    void aFirstFailureLeavesTheSessionOpen()
    {
        // Setup test fixture.
        // (none required)

        // Execute system under test.
        SaslOutcome.authenticationFailed(session, Failure.NOT_AUTHORIZED, true);

        // Verify result.
        assertEquals(1, session.getSessionData("authRetries"), "The first failure must be counted as one.");
        verify(session, never().description("A single failure must not close the session.")).close();
    }

    /**
     * Failures accumulate across attempts on the same session.
     */
    @Test
    void failuresAccumulateUntilTheLimitIsReached()
    {
        // Setup test fixture.
        // (none required)

        // Execute system under test.
        SaslOutcome.authenticationFailed(session, Failure.NOT_AUTHORIZED, true);
        SaslOutcome.authenticationFailed(session, Failure.NOT_AUTHORIZED, true);

        // Verify result.
        assertEquals(2, session.getSessionData("authRetries"), "Failures must accumulate on the session.");
        verify(session, never().description("The session must stay open below the retry limit.")).close();
    }

    /**
     * Once the limit is reached the session is closed, and marked as not resumable so that stream management cannot
     * be used to recover it.
     */
    @Test
    void reachingTheLimitClosesTheSession()
    {
        // Setup test fixture: three failures reaches the default limit.

        // Execute system under test.
        SaslOutcome.authenticationFailed(session, Failure.NOT_AUTHORIZED, true);
        SaslOutcome.authenticationFailed(session, Failure.NOT_AUTHORIZED, true);
        SaslOutcome.authenticationFailed(session, Failure.NOT_AUTHORIZED, true);

        // Verify result.
        verify(session, times(1).description("The session must be closed once the retry limit is reached."))
            .close();
        verify(session, times(1).description("A session closed for repeated authentication failures must be marked " +
            "non-resumable, or stream management would let the peer resume it."))
            .markNonResumable();
    }

    /**
     * The failure is still delivered on the attempt that closes the session, so that the peer learns why.
     */
    @Test
    void theFinalFailureIsStillDelivered() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("xmpp.auth.retries", "1");

        // Execute system under test.
        SaslOutcome.authenticationFailed(session, Failure.NOT_AUTHORIZED, true);

        // Verify result.
        assertEquals("not-authorized", ((Element) delivered().elements().get(0)).getName(),
            "The failure that closes the session must still be delivered to the peer.");
        verify(session).close();
    }

    /**
     * The limit is configurable, and is inclusive: reaching it closes the session.
     */
    @Test
    void theRetryLimitIsConfigurable()
    {
        // Setup test fixture.
        JiveGlobals.setProperty("xmpp.auth.retries", "2");

        // Execute system under test.
        SaslOutcome.authenticationFailed(session, Failure.NOT_AUTHORIZED, true);

        // Verify result.
        verify(session, never().description("One failure is below a configured limit of two.")).close();

        // Execute system under test.
        SaslOutcome.authenticationFailed(session, Failure.NOT_AUTHORIZED, true);

        // Verify result.
        verify(session, times(1).description("Reaching the configured limit must close the session.")).close();
    }

    /**
     * A count already recorded on the session is carried forward, so that failures counted elsewhere in the
     * negotiation are not discarded.
     */
    @Test
    void anExistingCountIsCarriedForward()
    {
        // Setup test fixture.
        session.setSessionData("authRetries", 2);

        // Execute system under test.
        SaslOutcome.authenticationFailed(session, Failure.NOT_AUTHORIZED, true);

        // Verify result.
        assertEquals(3, session.getSessionData("authRetries"), "An existing failure count must be incremented, not reset.");
        verify(session, times(1).description("A carried-forward count must be able to reach the limit.")).close();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the single element that was delivered to the session, parsed.
     *
     * @return the delivered element
     */
    private Element delivered() throws Exception
    {
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(session).deliverRawText(captor.capture());
        final String xml = captor.getValue();
        assertTrue(xml != null && !xml.isEmpty(), "Nothing was delivered to the session.");
        return DocumentHelper.parseText(xml).getRootElement();
    }
}
