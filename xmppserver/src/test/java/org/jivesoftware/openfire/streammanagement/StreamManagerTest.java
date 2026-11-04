/*
 * Copyright (C) 2023-2026 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.streammanagement;

import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketDeliverer;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.StreamError;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests that verify the implementation of {@link StreamManager}.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class StreamManagerTest
{
    private static final JID FULL_JID = new JID("johndoe", Fixtures.XMPP_DOMAIN, "test-resource", true);
    private static final JID SERVER_ADDRESS = new JID(Fixtures.XMPP_DOMAIN);
    private static final String STREAM_ID = "test-stream-id";
    private static final int DETACH_TIME_MILLIS = 120_000;

    // OF-3433: Names of private fields, used to reach the roll-over at 2^32 without sending that many stanzas.
    private static final String HANDLED_COUNTER_FIELD = "handledInboundStanzas";
    private static final String ACKNOWLEDGED_COUNTER_FIELD = "unacknowledgedOutboundStanzas.acknowledged";

    private SessionManager sessionManager;

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    public void setUp()
    {
        final XMPPServer server = Fixtures.mockXMPPServer();
        sessionManager = server.getSessionManager();
        doReturn(DETACH_TIME_MILLIS).when(sessionManager).getSessionDetachTime();

        //noinspection deprecation
        XMPPServer.setInstance(server);
    }

    @AfterEach
    public void tearDown()
    {
        Fixtures.clearExistingProperties();
        //noinspection deprecation
        XMPPServer.setInstance(null);
    }

    @Test
    public void testValidateClientAcknowledgement() throws Exception
    {
        // Setup test fixture.
        final long h = 0;
        final long oldH = 0;
        final Long lastUnackedX = null;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateClientAcknowledgement_clientAcksSentStanza() throws Exception
    {
        // Setup test fixture.
        final long h = 1;
        final long oldH = 0;
        final Long lastUnackedX = 1L;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateClientAcknowledgement_clientAcksSentStanzaWithMoreInflight() throws Exception
    {
        // Setup test fixture.
        final long h = 10;
        final long oldH = 4;
        final Long lastUnackedX = 12L;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateClientAcknowledgement_clientAcksUnsentStanza() throws Exception
    {
        // Setup test fixture.
        final long h = 1;
        final long oldH = 0;
        final Long lastUnackedX = null;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertFalse(result);
    }

    @Test
    public void testValidateClientAcknowledgement_clientAcksUnsentStanzaA() throws Exception
    {
        // Setup test fixture.
        final long h = 3;
        final long oldH = 1;
        final Long lastUnackedX = 2L;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertFalse(result);
    }

    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = MAX - 1;
        final long oldH = MAX - 1;
        final Long lastUnackedX = null;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase_unsent() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = MAX;
        final long oldH = MAX - 1;
        final Long lastUnackedX = null;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertFalse(result);
    }


    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase_sent() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = MAX;
        final long oldH = MAX - 1;
        final Long lastUnackedX = MAX;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase1() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = MAX;
        final long oldH = MAX;
        final Long lastUnackedX = null;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase2() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = MAX;
        final long oldH = MAX-2;
        final Long lastUnackedX = MAX;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }
    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase3() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 0;
        final long oldH = MAX-2;
        final Long lastUnackedX = 0L;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase3a() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 0;
        final long oldH = MAX-2;
        final Long lastUnackedX = 4L;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase3_unsent() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 0;
        final long oldH = MAX - 2;
        final Long lastUnackedX = null;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertFalse(result);
    }

    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase4() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 0;
        final long oldH = MAX;
        final Long lastUnackedX = 0L;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase4a() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 0;
        final long oldH = MAX;
        final Long lastUnackedX = 3L;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }
    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase4_unsent() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 0;
        final long oldH = MAX;
        final Long lastUnackedX = null;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertFalse(result);
    }

    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase5() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 3;
        final long oldH = MAX - 2;
        final Long lastUnackedX = 4L;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase5a() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 4;
        final long oldH = MAX - 2;
        final Long lastUnackedX = 4L;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase5_unsent() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 5;
        final long oldH = MAX - 2;
        final Long lastUnackedX = 4L;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Verifies that {@link StreamManager#detachIfNeeded(LocalSession)} closes the pre-existing session's
     * connection with a 'conflict' {@link StreamError}, as recommended by XEP-0198 §5 for a former stream that is still
     * open when it is superseded by a resumed session.
     */
    @Test
    void closesOldConnectionWithAConflictStreamError()
    {
        // Setup test fixture.
        final LocalClientSession otherSession = mock(LocalClientSession.class);
        final Connection oldConnection = mock(Connection.class);
        when(otherSession.isDetached()).thenReturn(false);
        when(otherSession.getConnection()).thenReturn(oldConnection);
        final LocalSession tempSession = mock(LocalSession.class); // owns this StreamManager
        final StreamManager sm = new StreamManager(tempSession);

        // Execute system under test.
        sm.detachIfNeeded(otherSession);

        // Verify results.
        verify(otherSession, times(1).description("The pre-existing session must be marked detached before its connection is handed off to the resumed session.")).setDetached();

        final ArgumentCaptor<StreamError> errorCaptor = ArgumentCaptor.forClass(StreamError.class);
        verify(oldConnection, times(1).description("The pre-existing connection must be closed exactly once as part of the hand-off.")).close(errorCaptor.capture());
        final StreamError closeError = errorCaptor.getValue();
        assertNotNull(closeError, "The pre-existing connection must be closed with a StreamError, as recommended by XEP-0198 §5 for a former stream that is still open when superseded by a resumed session.");
        assertEquals(StreamError.Condition.conflict, closeError.getCondition(), "The StreamError used to close a superseded connection must use the 'conflict' condition, per the example in XEP-0198 §5.");
    }

    /**
     * Verifies that {@link StreamManager#detachIfNeeded(LocalSession)} is a no-op for a session
     * that is already detached, leaving its (already absent) connection untouched.
     */
    @Test
    void isANoOpWhenAlreadyDetached()
    {
        // Setup test fixture.
        final LocalClientSession otherSession = mock(LocalClientSession.class);
        when(otherSession.isDetached()).thenReturn(true);
        final StreamManager sm = new StreamManager(mock(LocalSession.class));

        // Execute system under test.
        sm.detachIfNeeded(otherSession);

        // Verify results.
        verify(otherSession, never().description("An already-detached session has no connection to obtain; detachIfNeeded() must not attempt to read it.")).getConnection();
        verify(otherSession, never().description("An already-detached session must not be detached again.")).setDetached();
    }

    /**
     * Verifies that {@link StreamManager#detachIfNeeded(LocalSession)} marks the pre-existing session as detached
     * strictly before closing its connection. This ordering is not incidental: it is what lets
     * NettyConnection#close(StreamError) tell a benign resumption hand-off (session already detached when close() runs)
     * apart from a genuine stream failure (session not yet detached), and so decide whether to disable resumption
     * (OF-2751). If this ordering were ever reversed, that distinction (and with it, the safety of resuming a session
     * across a hand-off) would silently break.
     */
    @Test
    void detachesSessionBeforeClosingItsConnection()
    {
        // Setup test fixture.
        final LocalClientSession otherSession = mock(LocalClientSession.class);
        final Connection oldConnection = mock(Connection.class);
        when(otherSession.isDetached()).thenReturn(false);
        when(otherSession.getConnection()).thenReturn(oldConnection);
        final StreamManager sm = new StreamManager(mock(LocalSession.class));

        // Execute system under test.
        sm.detachIfNeeded(otherSession);

        // Verify results.
        final InOrder inOrder = inOrder(otherSession, oldConnection);
        inOrder.verify(otherSession).setDetached();
        inOrder.verify(oldConnection).close(any(StreamError.class));
    }

    /**
     * Verifies that enabling Stream Management without requesting resumption results in an {@code <enabled/>} element
     * in the namespace of the request, without any of the attributes that relate to resumption.
     */
    @ParameterizedTest
    @ValueSource(strings = {StreamManager.NAMESPACE_V2, StreamManager.NAMESPACE_V3})
    void enableWithoutResume(final String namespace) throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = new StreamManager(session);

        // Execute system under test.
        sm.process(smElement("enable", namespace));

        // Verify result.
        final Element enabled = lastDelivered(session);
        assertEquals(QName.get("enabled", namespace), enabled.getQName(), "The response must be an 'enabled' element in the namespace of the request.");
        assertTrue(enabled.attributes().isEmpty(), "No resumption attributes are expected when resumption was not requested, but got: " + enabled.asXML());
        assertTrue(sm.isEnabled(), "Stream Management must be enabled after a successful request.");
        assertFalse(sm.getResume(), "The stream must not be resumable when resumption was not requested.");
    }

    /**
     * Verifies that enabling Stream Management with resumption results in an {@code <enabled/>} element that contains
     * a resumption ID (based on the resource and stream ID), the cluster node to resume on, and the maximum resumption
     * time in seconds.
     */
    @Test
    void enableWithResumeV3() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = new StreamManager(session);
        final Element enable = smElement("enable", StreamManager.NAMESPACE_V3);
        enable.addAttribute("resume", "true");

        // Execute system under test.
        sm.process(enable);

        // Verify result.
        final Element enabled = lastDelivered(session);
        assertEquals(QName.get("enabled", StreamManager.NAMESPACE_V3), enabled.getQName(), "The response must be an 'enabled' element in the namespace of the request.");
        assertEquals("true", enabled.attributeValue("resume"), "The response must confirm that the stream is resumable.");
        assertEquals(Base64.getEncoder().encodeToString((FULL_JID.getResource() + "\0" + STREAM_ID).getBytes(StandardCharsets.UTF_8)), enabled.attributeValue("id"), "The resumption ID must be based on the resource and stream ID.");
        assertEquals(Fixtures.HOSTNAME, enabled.attributeValue("location"), "The response must identify the cluster node to resume on.");
        assertEquals(String.valueOf(DETACH_TIME_MILLIS / 1000), enabled.attributeValue("max"), "The response must report the session detach time, in seconds.");
        assertTrue(sm.isEnabled(), "Stream Management must be enabled after a successful request.");
        assertTrue(sm.getResume(), "The stream must be resumable.");
    }

    /**
     * Verifies that the location hint is not sent in the older version of the protocol, while the other attributes
     * that relate to resumption are.
     */
    @Test
    void enableWithResumeV2HasNoLocation() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = new StreamManager(session);
        final Element enable = smElement("enable", StreamManager.NAMESPACE_V2);
        enable.addAttribute("resume", "true");

        // Execute system under test.
        sm.process(enable);

        // Verify result.
        final Element enabled = lastDelivered(session);
        assertEquals(QName.get("enabled", StreamManager.NAMESPACE_V2), enabled.getQName(), "The response must be an 'enabled' element in the namespace of the request.");
        assertEquals("true", enabled.attributeValue("resume"), "The response must confirm that the stream is resumable.");
        assertNotNull(enabled.attributeValue("id"), "The response must contain a resumption ID.");
        assertNull(enabled.attributeValue("location"), "The location hint must not be sent in version 2 of the protocol.");
        assertEquals(String.valueOf(DETACH_TIME_MILLIS / 1000), enabled.attributeValue("max"), "The response must report the session detach time, in seconds.");
        assertTrue(sm.getResume(), "The stream must be resumable.");
    }

    /**
     * Verifies that the location hint is left out when it is disabled by configuration.
     */
    @Test
    void enableWithResumeOmitsLocationWhenDisabled() throws Exception
    {
        // Setup test fixture.
        StreamManager.LOCATION_ENABLED.setValue(false);
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = new StreamManager(session);
        final Element enable = smElement("enable", StreamManager.NAMESPACE_V3);
        enable.addAttribute("resume", "true");

        // Execute system under test.
        sm.process(enable);

        // Verify result.
        assertNull(lastDelivered(session).attributeValue("location"), "The location hint must not be sent when it is disabled by configuration.");
    }

    /**
     * Verifies that the maximum resumption time is left out when it is disabled by configuration.
     */
    @Test
    void enableWithResumeOmitsMaxWhenDisabled() throws Exception
    {
        // Setup test fixture.
        StreamManager.MAX_SERVER_ENABLED.setValue(false);
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = new StreamManager(session);
        final Element enable = smElement("enable", StreamManager.NAMESPACE_V3);
        enable.addAttribute("resume", "true");

        // Execute system under test.
        sm.process(enable);

        // Verify result.
        assertNull(lastDelivered(session).attributeValue("max"), "The maximum resumption time must not be sent when it is disabled by configuration.");
    }

    /**
     * Verifies which values of the 'resume' attribute request resumption.
     */
    @ParameterizedTest
    @CsvSource({"true,true", "TRUE,true", "yes,true", "YES,true", "1,true", "false,false", "0,false", "no,false", "foobar,false"})
    void enableInterpretsResumeAttribute(final String value, final boolean expected) throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = new StreamManager(session);
        final Element enable = smElement("enable", StreamManager.NAMESPACE_V3);
        enable.addAttribute("resume", value);

        // Execute system under test.
        sm.process(enable);

        // Verify result.
        assertEquals(expected, sm.getResume(), "Unexpected interpretation of resume attribute value '" + value + "'.");
        assertEquals(expected ? "true" : null, lastDelivered(session).attributeValue("resume"), "The response must reflect the interpretation of resume attribute value '" + value + "'.");
    }

    /**
     * Verifies that an anonymous session can enable Stream Management, but does not get a resumable stream.
     */
    @Test
    void enableWithResumeForAnonymousSessionIsNotResumable() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final AuthToken authToken = session.getAuthToken();
        doReturn(true).when(authToken).isAnonymous();
        final StreamManager sm = new StreamManager(session);
        final Element enable = smElement("enable", StreamManager.NAMESPACE_V3);
        enable.addAttribute("resume", "true");

        // Execute system under test.
        sm.process(enable);

        // Verify result.
        final Element enabled = lastDelivered(session);
        assertEquals("enabled", enabled.getName(), "An anonymous session must be able to enable Stream Management.");
        assertTrue(enabled.attributes().isEmpty(), "No resumption attributes are expected for an anonymous session, but got: " + enabled.asXML());
        assertTrue(sm.isEnabled(), "Stream Management must be enabled after a successful request.");
        assertFalse(sm.getResume(), "The stream of an anonymous session must not be resumable.");
    }

    /**
     * Verifies that a session that is not a client session can enable Stream Management, but does not get a resumable
     * stream.
     */
    @Test
    void enableWithResumeForNonClientSessionIsNotResumable() throws Exception
    {
        // Setup test fixture.
        final LocalSession session = mock(LocalSession.class);
        doReturn(true).when(session).isAuthenticated();
        final StreamManager sm = new StreamManager(session);
        final Element enable = smElement("enable", StreamManager.NAMESPACE_V3);
        enable.addAttribute("resume", "true");

        // Execute system under test.
        sm.process(enable);

        // Verify result.
        final Element enabled = lastDelivered(session);
        assertEquals("enabled", enabled.getName(), "A session that is not a client session must be able to enable Stream Management.");
        assertTrue(enabled.attributes().isEmpty(), "No resumption attributes are expected for a session that is not a client session, but got: " + enabled.asXML());
        assertFalse(sm.getResume(), "The stream of a session that is not a client session must not be resumable.");
    }

    /**
     * Verifies that Stream Management cannot be enabled before the session is authenticated.
     */
    @ParameterizedTest
    @ValueSource(strings = {StreamManager.NAMESPACE_V2, StreamManager.NAMESPACE_V3})
    void enableBeforeAuthenticationFails(final String namespace) throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        doReturn(false).when(session).isAuthenticated();
        final StreamManager sm = new StreamManager(session);

        // Execute system under test.
        sm.process(smElement("enable", namespace));

        // Verify result.
        assertFailedWithUnexpectedRequest(lastDelivered(session), namespace);
        assertFalse(sm.isEnabled(), "Stream Management must not be enabled on a session that is not authenticated.");
    }

    /**
     * Verifies that a second request to enable Stream Management fails, leaving the state from the first request intact.
     */
    @Test
    void enableTwiceFails() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.incrementHandledInboundStanzas();

        // Execute system under test.
        sm.process(smElement("enable", StreamManager.NAMESPACE_V2));

        // Verify result.
        assertFailedWithUnexpectedRequest(lastDelivered(session), StreamManager.NAMESPACE_V2);
        assertTrue(sm.isEnabled(), "Stream Management must remain enabled after a second request failed.");

        clearInvocations(session);
        sm.process(smElement("r", StreamManager.NAMESPACE_V3));
        final Element ack = lastDelivered(session);
        assertEquals(QName.get("a", StreamManager.NAMESPACE_V3), ack.getQName(), "The namespace of the first request must remain in use.");
        assertEquals("1", ack.attributeValue("h"), "The count of handled stanzas must not be affected by a second request.");
    }

    /**
     * Verifies that an {@code <r/>} is answered with the number of stanzas that were handled since enabling.
     */
    @ParameterizedTest
    @ValueSource(strings = {StreamManager.NAMESPACE_V2, StreamManager.NAMESPACE_V3})
    void requestIsAnsweredWithCountOfHandledStanzas(final String namespace) throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, namespace);
        sm.incrementHandledInboundStanzas();
        sm.incrementHandledInboundStanzas();
        sm.incrementHandledInboundStanzas();

        // Execute system under test.
        sm.process(smElement("r", namespace));

        // Verify result.
        final Element ack = lastDelivered(session);
        assertEquals(QName.get("a", namespace), ack.getQName(), "An 'a' element must be sent, in the namespace of the stream.");
        assertEquals("3", ack.attributeValue("h"), "The count must equal the number of handled stanzas.");
    }

    /**
     * Verifies that an {@code <r/>} is answered with zero when no stanzas have been handled.
     */
    @Test
    void requestIsAnsweredWithZeroWhenNoStanzasWereHandled() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);

        // Execute system under test.
        sm.process(smElement("r", StreamManager.NAMESPACE_V3));

        // Verify result.
        assertEquals("0", lastDelivered(session).attributeValue("h"), "The count must be zero when no stanzas were handled.");
    }

    /**
     * Verifies that the count of handled stanzas is cumulative: it is not reset by reporting it.
     */
    @Test
    void countOfHandledStanzasIsCumulative() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.incrementHandledInboundStanzas();
        sm.process(smElement("r", StreamManager.NAMESPACE_V3));
        sm.incrementHandledInboundStanzas();

        // Execute system under test.
        sm.process(smElement("r", StreamManager.NAMESPACE_V3));

        // Verify result.
        assertEquals("2", lastDelivered(session).attributeValue("h"), "The count must include stanzas handled before the previous acknowledgement.");
    }

    /**
     * Verifies that stanzas handled before Stream Management was enabled are not counted.
     */
    @Test
    void stanzasHandledBeforeEnablingAreNotCounted() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = new StreamManager(session);
        sm.incrementHandledInboundStanzas();
        sm.process(smElement("enable", StreamManager.NAMESPACE_V3));

        // Execute system under test.
        sm.process(smElement("r", StreamManager.NAMESPACE_V3));

        // Verify result.
        assertEquals("0", lastDelivered(session).attributeValue("h"), "Stanzas handled before enabling Stream Management must not be counted.");
    }

    /**
     * Verifies that an {@code <r/>} is silently ignored when Stream Management is not enabled.
     */
    @Test
    void requestIsIgnoredWhenNotEnabled()
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = new StreamManager(session);

        // Execute system under test.
        sm.process(smElement("r", StreamManager.NAMESPACE_V3));

        // Verify result.
        verify(session, never().description("Nothing is to be sent in response to a request when Stream Management is not enabled.")).deliverRawText(anyString());
    }

    /**
     * Verifies that no acknowledgement is sent on a detached session.
     */
    @Test
    void requestIsNotAnsweredOnDetachedSession()
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        doReturn(true).when(session).isDetached();

        // Execute system under test.
        sm.process(smElement("r", StreamManager.NAMESPACE_V3));

        // Verify result.
        verify(session, never().description("Nothing can be sent on a detached session.")).deliverRawText(anyString());
    }

    /**
     * Verifies that an {@code <a/>} removes the stanzas that it acknowledges, and no others.
     */
    @Test
    void acknowledgementRemovesAcknowledgedStanzas() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        sm.sentStanza(message("3"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "2"));

        // Verify result.
        assertEquals(List.of("3"), unacknowledgedMessageIds(sm), "Only the stanza that was not acknowledged must remain.");
        assertFalse(session.isClosed(), "A valid acknowledgement must not close the session.");
    }

    /**
     * Verifies that an {@code <a/>} that covers all stanzas that were sent leaves nothing unacknowledged.
     */
    @Test
    void acknowledgementOfAllStanzasRemovesAll() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "2"));

        // Verify result.
        assertEquals(List.of(), unacknowledgedMessageIds(sm), "No stanzas must remain after all of them were acknowledged.");
        assertFalse(session.isClosed(), "A valid acknowledgement must not close the session.");
    }

    /**
     * Verifies that an {@code <a/>} with a value of zero does not remove any stanzas.
     */
    @Test
    void acknowledgementOfZeroRemovesNothing() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "0"));

        // Verify result.
        assertEquals(List.of("1"), unacknowledgedMessageIds(sm), "A stanza must remain when no stanzas were acknowledged.");
        assertFalse(session.isClosed(), "A valid acknowledgement must not close the session.");
    }

    /**
     * Verifies that stanzas are numbered on from the last acknowledged value, after all earlier stanzas were
     * acknowledged.
     */
    @Test
    void stanzasAreNumberedOnAfterAcknowledgement() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "2"));
        sm.sentStanza(message("3"));
        sm.sentStanza(message("4"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "3"));

        // Verify result.
        assertEquals(List.of("4"), unacknowledgedMessageIds(sm), "The stanzas that were sent after an acknowledgement must be numbered on from its value.");
    }

    /**
     * Verifies that repeating the last acknowledgement has no effect, also when no stanzas remain.
     */
    @Test
    void repeatedAcknowledgementIsAccepted() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "1"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "1"));

        // Verify result.
        assertFalse(session.isClosed(), "Repeating an acknowledgement must not close the session.");
    }

    /**
     * Verifies that stanzas that are sent before Stream Management is enabled are not tracked.
     */
    @Test
    void stanzasSentBeforeEnablingAreNotTracked() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = new StreamManager(session);
        sm.sentStanza(message("before"));
        sm.process(smElement("enable", StreamManager.NAMESPACE_V3));

        // Execute system under test.
        sm.sentStanza(message("after"));

        // Verify result.
        assertEquals(List.of("after"), unacknowledgedMessageIds(sm), "Only stanzas sent after enabling Stream Management must be tracked.");
    }

    /**
     * Verifies that an {@code <a/>} is silently ignored when Stream Management is not enabled, whatever its value.
     */
    @Test
    void acknowledgementIsIgnoredWhenNotEnabled()
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = new StreamManager(session);

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "not-a-number"));

        // Verify result.
        verify(session, never().description("Nothing is to be sent in response to an acknowledgement when Stream Management is not enabled.")).deliverRawText(anyString());
        assertFalse(session.isClosed(), "An acknowledgement must not close the session when Stream Management is not enabled.");
    }

    /**
     * Verifies that an {@code <a/>} without a 'h' attribute is silently ignored.
     */
    @Test
    void acknowledgementWithoutValueIsIgnored() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));

        // Execute system under test.
        sm.process(smElement("a", StreamManager.NAMESPACE_V3));

        // Verify result.
        verify(session, never().description("An acknowledgement without a value does not cause anything to be sent.")).deliverRawText(anyString());
        assertFalse(session.isClosed(), "An acknowledgement without a value does not close the session.");
        assertEquals(List.of("1"), unacknowledgedMessageIds(sm), "An acknowledgement without a value must not remove any stanzas.");
    }

    /**
     * Verifies that acknowledging a stanza while none were sent closes the session with a stream error.
     */
    @Test
    void acknowledgementOfStanzaWhenNoneWereSentClosesSession()
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "1"));

        // Verify result.
        assertSessionClosedWithStreamError(session, StreamError.Condition.undefined_condition, "You acknowledged stanzas that we didn't send.");
    }

    /**
     * Verifies that acknowledging more stanzas than were sent closes the session with a stream error, without removing
     * any stanzas.
     */
    @Test
    void acknowledgementOfMoreStanzasThanSentClosesSession() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "3"));

        // Verify result.
        assertSessionClosedWithStreamError(session, StreamError.Condition.undefined_condition, "You acknowledged stanzas that we didn't send.");
        assertEquals(List.of("1", "2"), unacknowledgedMessageIds(sm), "An invalid acknowledgement must not remove any stanzas.");
    }

    /**
     * Verifies that the stream error for a value of 'h' that is too high contains the element that XEP-0198 § 6 defines
     * for it, which reports that value and the number of stanzas that were sent.
     */
    @Test
    void acknowledgementOfMoreStanzasThanSentReportsHandledCountTooHigh() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "3"));

        // Verify result.
        assertHandledCountTooHigh(session, "3", "2");
    }

    /**
     * Verifies that a value of 'h' that is far above the number of stanzas that were sent is recognised as too high
     * (XEP-0198 § 6), rather than being mistaken for a value that is lower than an earlier one.
     */
    @Test
    void acknowledgementFarAboveNumberOfStanzasSentClosesSession() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "3000000000"));

        // Verify result.
        assertHandledCountTooHigh(session, "3000000000", "1");
        assertEquals(List.of("1"), unacknowledgedMessageIds(sm), "An invalid acknowledgement must not remove any stanzas.");
    }

    /**
     * Verifies that a value of 'h' that is not a number that fits a long closes the session with a stream error.
     */
    @ParameterizedTest
    @ValueSource(strings = {"not-a-number", "", "1.5", "9223372036854775808"})
    void acknowledgementWithNonNumericValueClosesSession(final String h) throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, h));

        // Verify result.
        assertSessionClosedWithStreamError(session, StreamError.Condition.undefined_condition, "is not a number");
        assertEquals(List.of("1"), unacknowledgedMessageIds(sm), "An invalid acknowledgement must not remove any stanzas.");
    }

    /**
     * Verifies that a negative value of 'h' closes the session with a stream error.
     */
    @Test
    void acknowledgementWithNegativeValueClosesSession() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "-1"));

        // Verify result.
        assertSessionClosedWithStreamError(session, StreamError.Condition.undefined_condition, "negative value");
        assertEquals(List.of("1"), unacknowledgedMessageIds(sm), "An invalid acknowledgement must not remove any stanzas.");
    }

    /**
     * Verifies that a value of 'h' above 2^32-1 closes the session with a stream error, like other invalid values. XEP-0198
     * § 13 defines 'h' as an xs:unsignedInt, and § 6 allows for misuse of stream management to terminate the stream. The
     * session is closed with the error, which also makes it non-resumable (OF-3443).
     */
    @Test
    void acknowledgementWithValueAboveLegalMaximumClosesSession() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, String.valueOf(StreamManager.MASK + 1)));

        // Verify result.
        assertSessionClosedWithStreamError(session, StreamError.Condition.undefined_condition);
        assertEquals(List.of("1"), unacknowledgedMessageIds(sm), "An invalid acknowledgement must not remove any stanzas.");
    }

    /**
     * Verifies that an acknowledgement with a value lower than an earlier one does not close the session or bring back
     * stanzas. XEP-0198 § 4 states that an {@code <a/>} only indicates that new stanzas were processed if 'h' has been
     * incremented, and § 6 only requires closing the stream for a value that is too high.
     */
    @Test
    void lowerAcknowledgementIsNotAnErrorWhileStanzasAreUnacknowledged() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        sm.sentStanza(message("3"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "2"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "1"));

        // Verify result.
        assertFalse(session.isClosed(), "A lower acknowledgement is not too high, so it must not close the session.");
        assertEquals(List.of("3"), unacknowledgedMessageIds(sm), "Stanzas that were acknowledged earlier must not come back.");
    }

    /**
     * Verifies that an acknowledgement with a value lower than an earlier one is also no error when no stanzas are
     * unacknowledged, and that it does not affect the numbers of the stanzas that follow (XEP-0198 § 4 and § 6).
     */
    @Test
    void lowerAcknowledgementIsNotAnErrorWhenNoStanzasAreUnacknowledged() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "2"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "1"));
        sm.sentStanza(message("3"));
        sm.sentStanza(message("4"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "3"));

        // Verify result.
        assertFalse(session.isClosed(), "A lower acknowledgement is not too high, so it must not close the session.");
        assertEquals(List.of("4"), unacknowledgedMessageIds(sm), "A lower acknowledgement must not affect the numbers of the stanzas that follow.");
    }

    /**
     * Verifies that an {@code <r/>} is sent each time the number of unacknowledged stanzas reaches a multiple of the
     * default request frequency.
     */
    @Test
    void acknowledgementIsRequestedAtDefaultFrequency() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        for (int i = 1; i <= 4; i++) {
            sm.sentStanza(message(String.valueOf(i)));
        }
        verify(session, never().description("No acknowledgement is to be requested below the default frequency of 5.")).deliverRawText(anyString());

        // Execute system under test.
        sm.sentStanza(message("5"));

        // Verify result.
        assertEquals(QName.get("r", StreamManager.NAMESPACE_V3), lastDelivered(session).getQName(), "An acknowledgement is to be requested at the default frequency of 5, in the namespace of the stream.");
    }

    /**
     * Verifies that the frequency at which an {@code <r/>} is sent can be configured.
     */
    @Test
    void acknowledgementIsRequestedAtConfiguredFrequency()
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.requestFrequency", "2");
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);

        // Execute system under test.
        for (int i = 1; i <= 4; i++) {
            sm.sentStanza(message(String.valueOf(i)));
        }

        // Verify result.
        verify(session, times(2).description("An acknowledgement is to be requested for every second unacknowledged stanza.")).deliverRawText(anyString());
    }

    /**
     * Verifies that the request frequency is based on the number of stanzas that are unacknowledged, rather than the
     * number of stanzas that were sent.
     */
    @Test
    void acknowledgementRequestDependsOnNumberOfUnacknowledgedStanzas()
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.requestFrequency", "2");
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "1"));

        // Execute system under test.
        sm.sentStanza(message("2"));

        // Verify result.
        verify(session, never().description("No acknowledgement is to be requested when only one stanza is unacknowledged.")).deliverRawText(anyString());
    }

    /**
     * Verifies that no {@code <r/>} is sent on a detached session, while stanzas are still tracked.
     */
    @Test
    void acknowledgementIsNotRequestedOnDetachedSession() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.requestFrequency", "1");
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        doReturn(true).when(session).isDetached();

        // Execute system under test.
        sm.sentStanza(message("1"));

        // Verify result.
        verify(session, never().description("Nothing can be sent on a detached session.")).deliverRawText(anyString());
        assertEquals(List.of("1"), unacknowledgedMessageIds(sm), "A stanza must be tracked on a detached session.");
    }

    /**
     * Verifies that the reported count of handled stanzas rolls over to zero after 2^32-1, as defined by XEP-0198 § 4.
     */
    @Test
    void countOfHandledStanzasRollsOver() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        setCounter(sm, HANDLED_COUNTER_FIELD, StreamManager.MASK);

        // Execute system under test.
        sm.process(smElement("r", StreamManager.NAMESPACE_V3));
        final String before = lastDelivered(session).attributeValue("h");
        sm.incrementHandledInboundStanzas();
        clearInvocations(session);
        sm.process(smElement("r", StreamManager.NAMESPACE_V3));
        final String after = lastDelivered(session).attributeValue("h");
        sm.incrementHandledInboundStanzas();
        clearInvocations(session);
        sm.process(smElement("r", StreamManager.NAMESPACE_V3));
        final String afterNext = lastDelivered(session).attributeValue("h");

        // Verify result.
        assertEquals(String.valueOf(StreamManager.MASK), before, "2^32-1 is the largest value that can be reported.");
        assertEquals("0", after, "The reported count must roll over to zero after 2^32-1.");
        assertEquals("1", afterNext, "The reported count must continue from zero after rolling over.");
    }

    /**
     * Verifies that the count in a {@code <resumed/>} element rolls over like the count in {@code <a/>}, as XEP-0198 § 4
     * requires 'h' to be reset from 2^32-1 back to zero.
     */
    @Test
    void countInResumedElementRollsOver() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        setCounter(sm, HANDLED_COUNTER_FIELD, StreamManager.MASK);
        sm.incrementHandledInboundStanzas();

        // Execute system under test.
        final Element result = sm.buildResumedElement();

        // Verify result.
        assertEquals("0", result.attributeValue("h"), "The count in a 'resumed' element must roll over to zero after 2^32-1.");
    }

    /**
     * Verifies that a stanza can be acknowledged with the largest legal value of 'h'.
     */
    @Test
    void acknowledgementWithLargestLegalValueIsAccepted() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        setCounter(sm, ACKNOWLEDGED_COUNTER_FIELD, StreamManager.MASK - 1);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, String.valueOf(StreamManager.MASK)));

        // Verify result.
        assertFalse(session.isClosed(), "An acknowledgement with the largest legal value of 'h' must not close the session.");
        assertEquals(List.of("2"), unacknowledgedMessageIds(sm), "Only the stanza that was not acknowledged must remain.");
    }

    /**
     * Verifies that an acknowledgement that rolled over removes the stanzas that were sent around the roll-over, when
     * all of them are acknowledged. Numbering then restarts at the value that was acknowledged.
     */
    @Test
    void acknowledgementAfterRollOverRemovesAcknowledgedStanzas() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        setCounter(sm, ACKNOWLEDGED_COUNTER_FIELD, StreamManager.MASK - 1);
        sm.sentStanza(message("peer-counts-2^32-1"));
        sm.sentStanza(message("peer-counts-0"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "0"));
        sm.sentStanza(message("peer-counts-1"));
        sm.sentStanza(message("peer-counts-2"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "1"));

        // Verify result.
        assertFalse(session.isClosed(), "Acknowledgements that rolled over must not close the session.");
        assertEquals(List.of("peer-counts-2"), unacknowledgedMessageIds(sm), "Only the stanza that was not acknowledged must remain.");
    }

    /**
     * Verifies that an acknowledgement that rolled over only removes the stanzas that it acknowledges. XEP-0198 § 4 leaves
     * a stanza the responsibility of the sender until it is acknowledged.
     */
    @Test
    void acknowledgementAfterRollOverKeepsUnacknowledgedStanzas() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        setCounter(sm, ACKNOWLEDGED_COUNTER_FIELD, StreamManager.MASK - 1);
        sm.sentStanza(message("peer-counts-2^32-1"));
        sm.sentStanza(message("peer-counts-0"));
        sm.sentStanza(message("peer-counts-1"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "0"));

        // Verify result.
        assertEquals(List.of("peer-counts-1"), unacknowledgedMessageIds(sm), "The stanza that was not acknowledged must remain.");
    }

    /**
     * Verifies that stanzas that are sent after an acknowledgement that rolled over are numbered on from the stanzas that
     * are still unacknowledged, as XEP-0198 § 4 has both parties count every stanza.
     */
    @Test
    void stanzasSentAfterRollOverAreNumberedOnFromUnacknowledgedStanzas() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        setCounter(sm, ACKNOWLEDGED_COUNTER_FIELD, StreamManager.MASK - 1);
        sm.sentStanza(message("peer-counts-2^32-1"));
        sm.sentStanza(message("peer-counts-0"));
        sm.sentStanza(message("peer-counts-1"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "0"));

        // Execute system under test.
        sm.sentStanza(message("peer-counts-2"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "1"));

        // Verify result.
        assertEquals(List.of("peer-counts-2"), unacknowledgedMessageIds(sm), "An acknowledgement must not cover a stanza that the peer counts after it.");
    }

    /**
     * Verifies that successive valid acknowledgements around the roll-over are accepted. XEP-0198 § 6 only calls for
     * closing the stream when a value is higher than the number of stanzas that were sent.
     */
    @Test
    void successiveAcknowledgementsAfterRollOverAreAccepted() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        setCounter(sm, ACKNOWLEDGED_COUNTER_FIELD, StreamManager.MASK - 1);
        sm.sentStanza(message("peer-counts-2^32-1"));
        sm.sentStanza(message("peer-counts-0"));
        sm.sentStanza(message("peer-counts-1"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "0"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "1"));

        // Verify result.
        assertFalse(session.isClosed(), "A valid acknowledgement must not close the session.");
        assertEquals(List.of(), unacknowledgedMessageIds(sm), "No stanzas must remain after all of them were acknowledged.");
    }

    /**
     * Verifies that an acknowledgement with a value from before the roll-over, which is lower than an earlier one that
     * rolled over, is no error and does not remove any stanzas (XEP-0198 § 4 and § 6).
     */
    @Test
    void lowerAcknowledgementFromBeforeRollOverIsNotAnError() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        setCounter(sm, ACKNOWLEDGED_COUNTER_FIELD, StreamManager.MASK - 1);
        sm.sentStanza(message("peer-counts-2^32-1"));
        sm.sentStanza(message("peer-counts-0"));
        sm.sentStanza(message("peer-counts-1"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "0"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, String.valueOf(StreamManager.MASK)));

        // Verify result.
        assertFalse(session.isClosed(), "A lower acknowledgement is not too high, so it must not close the session.");
        assertEquals(List.of("peer-counts-1"), unacknowledgedMessageIds(sm), "A lower acknowledgement must not remove any stanzas.");
    }

    /**
     * Verifies that acknowledging a stanza that was never sent is detected around the roll-over, as XEP-0198 § 6 requires
     * at any other time. The value of 'send-count' has to roll over too, as it is an xs:unsignedInt (§ 13).
     */
    @Test
    void acknowledgementOfUnsentStanzaIsRejectedAroundRollOver() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        setCounter(sm, ACKNOWLEDGED_COUNTER_FIELD, StreamManager.MASK - 1);
        sm.sentStanza(message("peer-counts-2^32-1"));
        sm.sentStanza(message("peer-counts-0"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "5"));

        // Verify result.
        assertHandledCountTooHigh(session, "5", "0");
        assertEquals(List.of("peer-counts-2^32-1", "peer-counts-0"), unacknowledgedMessageIds(sm), "An invalid acknowledgement must not remove any stanzas.");
    }

    /**
     * Verifies that a resume request with a value of 'h' lower than the last acknowledged value is accepted when no
     * stanzas are unacknowledged. A client can send such a value when it restores a count that is older than its last
     * acknowledgement. XEP-0198 § 5 has 'h' in a resume request handled as in an {@code <a/>}, for which a lower value
     * does not acknowledge any new stanzas (§ 4).
     */
    @Test
    void resumeWithLowerValueIsAcceptedWhenNoStanzasAreUnacknowledged() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession original = resumableClientSession();
        final StreamManager sm = streamManagerWithResumption(original);
        final String previd = lastDelivered(original).attributeValue("id");
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "2"));
        final LocalClientSession resuming = resumingClientSession();

        // Execute system under test.
        new StreamManager(resuming).process(resume(previd, 1));

        // Verify result.
        verify(original, times(1).description("The session must be resumed, as a lower value of 'h' is not too high.")).reattach(resuming, 1L);
        verify(resuming, never().description("No failure is to be reported for a lower value of 'h'.")).deliverRawText(anyString());
    }

    /**
     * Verifies that a resume request with a value of 'h' lower than the last acknowledged value is accepted when stanzas
     * are unacknowledged, and that the stanzas that remain unacknowledged are sent again on resumption, without the lower
     * value affecting the stanzas that the client acknowledges after that.
     */
    @Test
    void resumeWithLowerValueRedeliversUnacknowledgedStanzas() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession original = resumableClientSession();
        final StreamManager sm = streamManagerWithResumption(original);
        final String previd = lastDelivered(original).attributeValue("id");
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        sm.sentStanza(message("3"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "2"));
        final LocalClientSession resuming = resumingClientSession();
        new StreamManager(resuming).process(resume(previd, 1));
        verify(original, times(1).description("Test fixture expects the session to be resumed.")).reattach(resuming, 1L);
        final Connection newConnection = mock(Connection.class);
        doReturn(newConnection).when(original).getConnection();

        // Execute system under test.
        sm.redeliverUnackedStanzas(SERVER_ADDRESS, 1);
        sm.process(ack(StreamManager.NAMESPACE_V3, "3"));

        // Verify result.
        final ArgumentCaptor<Packet> captor = ArgumentCaptor.forClass(Packet.class);
        verify(newConnection, times(1).description("Only the stanza that remained unacknowledged is to be sent again.")).deliver(captor.capture());
        assertEquals("3", captor.getValue().getID(), "The stanza that remained unacknowledged is to be sent again.");
        assertFalse(original.isClosed(), "Acknowledging the stanza that was sent again must not close the session.");
        assertEquals(List.of(), unacknowledgedMessageIds(sm), "No stanzas must remain after the stanza that was sent again is acknowledged.");
    }

    /**
     * Verifies that a resume request with a value of 'h' that is too high is refused after the acknowledged count rolled
     * over, as it is at any other time.
     */
    @Test
    void resumeWithValueAboveNumberOfStanzasSentIsRefusedAfterRollOver() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession original = resumableClientSession();
        final StreamManager sm = streamManagerWithResumption(original);
        final String previd = lastDelivered(original).attributeValue("id");
        setCounter(sm, ACKNOWLEDGED_COUNTER_FIELD, StreamManager.MASK - 1);
        sm.sentStanza(message("peer-counts-2^32-1"));
        sm.sentStanza(message("peer-counts-0"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "0"));
        final LocalClientSession resuming = resumingClientSession();

        // Execute system under test.
        new StreamManager(resuming).process(resume(previd, 5));

        // Verify result.
        verify(original, never().description("The session must not be resumed with a value of 'h' that is too high.")).reattach(any(), anyLong());
        assertFailedWithUnexpectedRequest(lastDelivered(resuming), StreamManager.NAMESPACE_V3);
    }

    /**
     * Verifies that a resume request with a value of 'h' from before the roll-over, which is lower than an earlier
     * acknowledgement that rolled over, is accepted.
     */
    @Test
    void resumeWithLowerValueFromBeforeRollOverIsAccepted() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession original = resumableClientSession();
        final StreamManager sm = streamManagerWithResumption(original);
        final String previd = lastDelivered(original).attributeValue("id");
        setCounter(sm, ACKNOWLEDGED_COUNTER_FIELD, StreamManager.MASK - 1);
        sm.sentStanza(message("peer-counts-2^32-1"));
        sm.sentStanza(message("peer-counts-0"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "0"));
        final LocalClientSession resuming = resumingClientSession();

        // Execute system under test.
        new StreamManager(resuming).process(resume(previd, StreamManager.MASK));

        // Verify result.
        verify(original, times(1).description("The session must be resumed, as a value from before the roll-over is not too high.")).reattach(resuming, StreamManager.MASK);
        verify(resuming, never().description("No failure is to be reported for a value from before the roll-over.")).deliverRawText(anyString());
    }

    /**
     * Verifies that the stream remains open, with all stanzas tracked, at the maximum number of unacknowledged stanzas.
     */
    @Test
    void reachingMaximumOfUnacknowledgedStanzasKeepsStreamOpen() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.max-unacked", "3");
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);

        // Execute system under test.
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        sm.sentStanza(message("3"));

        // Verify result.
        assertFalse(session.getConnection().isClosed(), "The stream must not be closed at the maximum number of unacknowledged stanzas.");
        assertFalse(session.isClosed(), "The session must not be closed at the maximum number of unacknowledged stanzas.");
        assertTrue(sm.isEnabled(), "Stream Management must remain enabled at the maximum number of unacknowledged stanzas.");
        assertEquals(List.of("1", "2", "3"), unacknowledgedMessageIds(sm), "All stanzas must be tracked at the maximum number of unacknowledged stanzas.");
    }

    /**
     * Verifies that exceeding the maximum number of unacknowledged stanzas closes the stream with a 'policy-violation'
     * stream error, so that the peer learns why. XEP-0198 defines no such maximum, and no way to stop tracking stanzas on
     * an open stream.
     */
    @Test
    void exceedingMaximumOfUnacknowledgedStanzasClosesStream()
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.max-unacked", "3");
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        sm.sentStanza(message("3"));

        // Execute system under test.
        sm.sentStanza(message("4"));

        // Verify result.
        final ArgumentCaptor<StreamError> captor = ArgumentCaptor.forClass(StreamError.class);
        verify(session.getConnection(), times(1).description("The stream must be closed when the maximum number of unacknowledged stanzas is exceeded.")).close(captor.capture());
        assertNotNull(captor.getValue(), "The stream must be closed with a stream error, so that the peer learns why.");
        assertEquals(StreamError.Condition.policy_violation, captor.getValue().getCondition(), "The stream error must use the 'policy-violation' condition, as a configured limit was exceeded.");
    }

    /**
     * Verifies that exceeding the maximum number of unacknowledged stanzas does not discard them. XEP-0198 § 4 leaves a
     * stanza the responsibility of the sender until it is acknowledged, and states that a server should treat it as a
     * stanza for an unavailable resource when the stream ends.
     */
    @Test
    void exceedingMaximumOfUnacknowledgedStanzasDoesNotDiscardThem() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.max-unacked", "3");
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        sm.sentStanza(message("3"));

        // Execute system under test.
        sm.sentStanza(message("4"));

        // Verify result.
        assertEquals(List.of("1", "2", "3", "4"), unacknowledgedMessageIds(sm), "Stanzas that were not acknowledged must still be routed when the session closes.");
    }

    /**
     * Verifies that exceeding the maximum number of unacknowledged stanzas disables resumption, so that the session closes
     * and its unacknowledged stanzas are handled, rather than being kept for a peer that does not acknowledge them.
     */
    @Test
    void exceedingMaximumOfUnacknowledgedStanzasDisablesResumption()
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.max-unacked", "1");
        final LocalClientSession session = authenticatedClientSession();
        final AuthToken authToken = mock(AuthToken.class);
        doReturn(authToken).when(session).getAuthToken();
        final StreamManager sm = new StreamManager(session);
        final Element enable = smElement("enable", StreamManager.NAMESPACE_V3);
        enable.addAttribute("resume", "true");
        sm.process(enable);
        assertTrue(sm.getResume(), "Test fixture expects a resumable stream.");
        sm.sentStanza(message("1"));

        // Execute system under test.
        sm.sentStanza(message("2"));

        // Verify result.
        assertFalse(sm.getResume(), "The stream must not be resumable after the maximum number of unacknowledged stanzas was exceeded.");
    }

    /**
     * Verifies that the stream is closed once, rather than for every stanza that is sent after exceeding the maximum
     * number of unacknowledged stanzas, and that stanzas that are sent after that are not tracked. The connection is
     * closed, so it hands those stanzas to its fallback, which would deliver them a second time if they were also routed
     * again when the session closes.
     */
    @Test
    void exceedingMaximumOfUnacknowledgedStanzasClosesStreamOnce() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.max-unacked", "1");
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));

        // Execute system under test.
        sm.sentStanza(message("2"));
        sm.sentStanza(message("3"));

        // Verify result.
        verify(session.getConnection(), times(1).description("The stream must be closed once.")).close(any());
        assertEquals(List.of("1", "2"), unacknowledgedMessageIds(sm), "Stanzas that are sent after the stream was closed must not be tracked, as the closed connection hands them to its fallback.");
    }

    /**
     * Verifies that stanzas that are sent to a detached session after exceeding the maximum number of unacknowledged
     * stanzas are still tracked. A detached session has no connection to hand them to, so they would otherwise be lost.
     */
    @Test
    void stanzasSentToDetachedSessionAfterExceedingMaximumAreTracked() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.max-unacked", "1");
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        doReturn(true).when(session).isDetached();
        doReturn(null).when(session).getConnection();
        final CountDownLatch terminated = new CountDownLatch(1);
        final CountDownLatch terminatedAgain = new CountDownLatch(2);
        doAnswer(invocation -> { terminated.countDown(); terminatedAgain.countDown(); return null; }).when(sessionManager).terminateDetached(session);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        assertTrue(terminated.await(5, TimeUnit.SECONDS), "Test fixture expects the detached session to be ended.");

        // Execute system under test.
        sm.sentStanza(message("3"));

        // Verify result.
        assertEquals(List.of("1", "2", "3"), unacknowledgedMessageIds(sm), "Stanzas that are sent to a detached session must be tracked, so that they are routed when the session closes.");
        // OF-3434: Wait with a latch, as verify() with a timeout holds the lock of the synchronized terminateDetached() on the mock.
        assertFalse(terminatedAgain.await(500, TimeUnit.MILLISECONDS), "The detached session must be ended once, not again for every stanza that is sent after exceeding the maximum.");
    }

    /**
     * Verifies that a message in a batch that is delivered after the stream was closed for exceeding the maximum number of
     * unacknowledged stanzas is handed to the connection only, which stores it offline, and not tracked to be routed again.
     */
    @Test
    void stanzasInBatchAfterExceedingMaximumAreNotTracked() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.max-unacked", "1");
        final Connection connection = closableConnection();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, BasicStreamIDFactory.createStreamID(STREAM_ID), Locale.ENGLISH);
        session.setStatus(Session.Status.AUTHENTICATED);
        session.getStreamManager().process(smElement("enable", StreamManager.NAMESPACE_V3));
        assertTrue(session.getStreamManager().isEnabled(), "Test fixture expects Stream Management to be enabled.");

        // Execute system under test.
        session.pushPackets(List.of(message("1"), message("2"), message("3")));

        // Verify result.
        verify(connection, times(1).description("The stream must be closed once.")).close(any());
        verify(connection, times(3).description("Each stanza in the batch is to be handed to the connection, which hands it to its fallback once it is closed.")).deliver(any());
        assertEquals(List.of("1", "2"), unacknowledgedMessageIds(session.getStreamManager()), "A message that is handed to the closed connection must not also be tracked, as it would then be delivered twice.");
    }

    /**
     * Verifies that a stanza in a batch that is delivered after the stream was closed for exceeding the maximum number of
     * unacknowledged stanzas is still tracked when the connection has no fallback, as the closed connection then drops
     * the stanza, and it would otherwise be lost.
     */
    @Test
    void stanzasInBatchAfterExceedingMaximumAreTrackedWithoutFallback() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.max-unacked", "1");
        final Connection connection = closableConnection();
        doReturn(null).when(connection).getPacketDeliverer();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, BasicStreamIDFactory.createStreamID(STREAM_ID), Locale.ENGLISH);
        session.setStatus(Session.Status.AUTHENTICATED);
        session.getStreamManager().process(smElement("enable", StreamManager.NAMESPACE_V3));
        assertTrue(session.getStreamManager().isEnabled(), "Test fixture expects Stream Management to be enabled.");

        // Execute system under test.
        session.pushPackets(List.of(message("1"), message("2"), message("3")));

        // Verify result.
        assertTrue(connection.isClosed(), "Test fixture expects the stream to be closed for exceeding the maximum.");
        assertEquals(List.of("1", "2", "3"), unacknowledgedMessageIds(session.getStreamManager()), "A stanza that the closed connection drops, as it has no fallback, must be tracked, so that it is routed when the session closes.");
    }

    /**
     * Verifies that an IQ request in a batch that is delivered after the stream was closed for exceeding the maximum
     * number of unacknowledged stanzas is answered with an error when the session closes. The fallback of the closed
     * connection drops IQ stanzas, so the request must remain tracked, as RFC 6120 § 8.2.3 requires a response to every
     * IQ request.
     */
    @Test
    void iqRequestInBatchAfterExceedingMaximumIsAnsweredWhenSessionCloses() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.max-unacked", "1");
        final Connection connection = closableConnection();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, BasicStreamIDFactory.createStreamID(STREAM_ID), Locale.ENGLISH);
        session.setStatus(Session.Status.AUTHENTICATED);
        session.getStreamManager().process(smElement("enable", StreamManager.NAMESPACE_V3));
        assertTrue(session.getStreamManager().isEnabled(), "Test fixture expects Stream Management to be enabled.");
        final IQ request = new IQ(IQ.Type.get, "3");
        request.setFrom(new JID("contact", Fixtures.XMPP_DOMAIN, "desktop"));
        request.setTo(new JID("user", Fixtures.XMPP_DOMAIN, "mobile"));
        request.setChildElement("query", "jabber:iq:version");

        // Execute system under test.
        session.pushPackets(List.of(message("1"), message("2"), request));

        // Verify result.
        assertTrue(connection.isClosed(), "Test fixture expects the stream to be closed for exceeding the maximum.");
        final PacketRouter router = mock(PacketRouter.class);
        session.getStreamManager().onClose(router, SERVER_ADDRESS);
        final ArgumentCaptor<IQ> captor = ArgumentCaptor.forClass(IQ.class);
        verify(router, atLeast(0)).route(captor.capture());
        assertEquals(1, captor.getAllValues().size(), "Exactly one response must be routed for the IQ request, but these were routed: " + captor.getAllValues());
        final IQ error = captor.getValue();
        assertEquals(IQ.Type.error, error.getType(), "The response to the IQ request must be an error.");
        assertEquals("3", error.getID(), "The response must have the ID of the IQ request.");
        assertEquals(request.getFrom(), error.getTo(), "The response must be addressed to the sender of the IQ request.");
        assertEquals(PacketError.Condition.service_unavailable, error.getError().getCondition(), "The response must report the condition for an unavailable resource.");
    }

    /**
     * Verifies that a session is ended when its close listener detached it while it was closed for exceeding the
     * maximum number of unacknowledged stanzas. That happens when the connection was already closing, and its close
     * listener found the session to be resumable before resumption was disabled.
     */
    @Test
    void sessionDetachedWhileClosingForExceedingMaximumIsEnded() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.max-unacked", "1");
        final LocalClientSession session = authenticatedClientSession();
        final Connection connection = session.getConnection();
        final CompletableFuture<Void> closeFuture = new CompletableFuture<>();
        doReturn(closeFuture).when(connection).getCloseFuture();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        final CountDownLatch terminated = new CountDownLatch(1);
        doAnswer(invocation -> { terminated.countDown(); return null; }).when(sessionManager).terminateDetached(session);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));

        // Execute system under test.
        doReturn(true).when(session).isDetached();
        closeFuture.complete(null);

        // Verify result.
        assertTrue(terminated.await(5, TimeUnit.SECONDS), "A session that was detached while it was closed for exceeding the maximum must be ended.");
    }

    /**
     * Verifies that a session that is not detached when its connection finished closing, after it was closed for
     * exceeding the maximum number of unacknowledged stanzas, is not ended as a detached session, as it has been closed.
     */
    @Test
    void sessionNotDetachedWhenClosedForExceedingMaximumIsNotEndedAgain() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.max-unacked", "1");
        final LocalClientSession session = authenticatedClientSession();
        final Connection connection = session.getConnection();
        final CompletableFuture<Void> closeFuture = new CompletableFuture<>();
        doReturn(closeFuture).when(connection).getCloseFuture();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        final CountDownLatch terminated = new CountDownLatch(1);
        doAnswer(invocation -> { terminated.countDown(); return null; }).when(sessionManager).terminateDetached(session);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));

        // Execute system under test.
        closeFuture.complete(null);

        // Verify result.
        assertFalse(terminated.await(500, TimeUnit.MILLISECONDS), "A session that is not detached must not be ended as a detached session.");
    }

    /**
     * Verifies that a detached session, which has no stream to close, is ended when the maximum number of unacknowledged
     * stanzas is exceeded, rather than after its detach timeout.
     */
    @Test
    void exceedingMaximumOfUnacknowledgedStanzasOnDetachedSessionEndsSession() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.max-unacked", "1");
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        doReturn(true).when(session).isDetached();
        doReturn(null).when(session).getConnection();
        sm.sentStanza(message("1"));

        // OF-3434: Wait with a latch, as verify() with a timeout holds the lock of the synchronized terminateDetached() on the mock.
        final CountDownLatch terminated = new CountDownLatch(1);
        doAnswer(invocation -> { terminated.countDown(); return null; }).when(sessionManager).terminateDetached(session);

        // Execute system under test.
        sm.sentStanza(message("2"));

        // Verify result.
        assertTrue(terminated.await(5, TimeUnit.SECONDS), "A detached session must be ended when the maximum number of unacknowledged stanzas is exceeded.");
        verify(sessionManager, times(1).description("A detached session must be ended once.")).terminateDetached(session);
    }

    /**
     * Verifies that a stanza is still tracked when it is delivered on a connection that is not closed, after the maximum
     * number of unacknowledged stanzas was exceeded. That happens when a detached session that exceeded the maximum is
     * resumed: its new connection delivers the stanza before the resumed stream is closed, so no fallback handles it.
     */
    @Test
    void stanzaDeliveredOnResumedConnectionAfterExceedingMaximumIsTracked() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.max-unacked", "1");
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        doReturn(true).when(session).isDetached();
        doReturn(null).when(session).getConnection();
        final CountDownLatch terminated = new CountDownLatch(1);
        doAnswer(invocation -> { terminated.countDown(); return null; }).when(sessionManager).terminateDetached(session);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        assertTrue(terminated.await(5, TimeUnit.SECONDS), "Test fixture expects the detached session to be ended.");
        doReturn(false).when(session).isDetached();
        final Connection resumedConnection = closableConnection();
        doReturn(resumedConnection).when(session).getConnection();

        // Execute system under test.
        sm.sentStanza(message("3"));

        // Verify result.
        assertEquals(List.of("1", "2", "3"), unacknowledgedMessageIds(sm), "A stanza that is delivered on a connection that is not closed must be tracked, as no fallback handles it.");
        assertFalse(resumedConnection.isClosed(), "A stanza that is sent after the maximum was exceeded must not close the stream again; closing a resumed stream is up to its resumption.");
    }

    /**
     * Verifies that a detached session that exceeded the maximum number of unacknowledged stanzas is closed when it is
     * resumed before it could be ended, rather than continuing on the new connection without a limit.
     */
    @Test
    void resumptionAfterExceedingMaximumOfUnacknowledgedStanzasClosesStream() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.max-unacked", "1");
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        doReturn(true).when(session).isDetached();
        doReturn(null).when(session).getConnection();
        // Hold the task that ends the detached session until the session was resumed, so that the resumption runs first.
        final CountDownLatch resumed = new CountDownLatch(1);
        final CountDownLatch terminated = new CountDownLatch(1);
        doAnswer(invocation -> {
            resumed.await(5, TimeUnit.SECONDS);
            terminated.countDown();
            return null;
        }).when(sessionManager).terminateDetached(session);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        final Connection newConnection = closableConnection();
        doReturn(false).when(session).isDetached();
        doReturn(newConnection).when(session).getConnection();

        // Execute system under test.
        sm.redeliverUnackedStanzas(SERVER_ADDRESS, 0);
        resumed.countDown();

        // Verify result.
        assertTrue(terminated.await(5, TimeUnit.SECONDS), "Test fixture expects the task that ends the detached session to complete.");
        final ArgumentCaptor<StreamError> captor = ArgumentCaptor.forClass(StreamError.class);
        verify(newConnection, times(1).description("The resumed stream must be closed, as the maximum number of unacknowledged stanzas was exceeded.")).close(captor.capture());
        assertEquals(StreamError.Condition.policy_violation, captor.getValue().getCondition(), "The stream error must use the 'policy-violation' condition, as a configured limit was exceeded.");
        verify(newConnection, never().description("Stanzas must not be sent again on a stream that is being closed.")).deliver(any());
        assertEquals(List.of("1", "2"), unacknowledgedMessageIds(sm), "Stanzas that were not acknowledged must still be routed when the session closes.");
    }

    /**
     * Verifies that a resumed stream that is closed for exceeding the maximum number of unacknowledged stanzas is closed
     * while the lock of the stream manager is held. The session delivers and tracks stanzas under that lock. Closing the
     * stream between those two steps would leave a stanza that was delivered on the open stream untracked.
     */
    @Test
    void resumedStreamIsClosedForExceedingMaximumWhileHoldingLock() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.max-unacked", "1");
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        doReturn(true).when(session).isDetached();
        doReturn(null).when(session).getConnection();
        final CountDownLatch terminated = new CountDownLatch(1);
        doAnswer(invocation -> { terminated.countDown(); return null; }).when(sessionManager).terminateDetached(session);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        assertTrue(terminated.await(5, TimeUnit.SECONDS), "Test fixture expects the detached session to be ended.");
        final Connection newConnection = mock(Connection.class);
        final AtomicBoolean closedWhileHoldingLock = new AtomicBoolean(false);
        doAnswer(invocation -> { closedWhileHoldingLock.set(Thread.holdsLock(sm)); return null; }).when(newConnection).close(any());
        doReturn(false).when(session).isDetached();
        doReturn(newConnection).when(session).getConnection();

        // Execute system under test.
        sm.redeliverUnackedStanzas(SERVER_ADDRESS, 0);

        // Verify result.
        verify(newConnection, times(1).description("The resumed stream must be closed, as the maximum number of unacknowledged stanzas was exceeded.")).close(any());
        assertTrue(closedWhileHoldingLock.get(), "The resumed stream must be closed while the lock of the stream manager is held, so that no stanza is delivered on it without being tracked.");
    }

    /**
     * Verifies that a stanza that is sent while a session is resumed, and that exceeds the maximum number of
     * unacknowledged stanzas, cannot close the resumed stream before the unacknowledged stanzas are re-delivered on it.
     * The closed stream would hand the re-delivered stanzas to its fallback, while they are also routed again when the
     * session closes.
     */
    @Test
    void stanzaExceedingMaximumDuringResumptionDoesNotCloseStreamBeforeRedelivery() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.max-unacked", "2");
        final LocalClientSession session = authenticatedClientSession();
        final Connection connection = session.getConnection();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        final List<String> deliveredWhileClosed = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            if (connection.isClosed()) {
                deliveredWhileClosed.add(((Packet) invocation.getArgument(0)).getID());
            }
            return null;
        }).when(connection).deliver(any());
        // Send a stanza from another thread, as LocalClientSession#pushPackets() does, when the connection is obtained for the re-delivery.
        final AtomicBoolean sending = new AtomicBoolean(false);
        final Thread[] sender = new Thread[1];
        doAnswer(invocation -> {
            if (sending.compareAndSet(false, true)) {
                sender[0] = new Thread(() -> { synchronized (sm) { sm.sentStanza(message("3")); } });
                sender[0].start();
                sender[0].join(500);
            }
            return connection;
        }).when(session).getConnection();

        // Execute system under test.
        sm.redeliverUnackedStanzas(SERVER_ADDRESS, 0);

        // Verify result.
        sender[0].join(5000);
        assertTrue(connection.isClosed(), "Test fixture expects the stream to be closed for exceeding the maximum.");
        assertEquals(List.of(), deliveredWhileClosed, "Unacknowledged stanzas must be re-delivered before the stream is closed, as the stanzas on a closed stream go to its fallback, and are also routed again when the session closes.");
    }

    /**
     * A mock of a connection that reports itself as closed once it is closed, through either close() or
     * close(StreamError), and that has a fallback for stanzas that it cannot deliver.
     */
    private static Connection closableConnection()
    {
        final Connection connection = mock(Connection.class);
        doReturn(mock(PacketDeliverer.class)).when(connection).getPacketDeliverer();
        final AtomicBoolean closed = new AtomicBoolean(false);
        doAnswer(invocation -> { closed.set(true); return null; }).when(connection).close();
        doAnswer(invocation -> { closed.set(true); return null; }).when(connection).close(nullable(StreamError.class));
        doAnswer(invocation -> closed.get()).when(connection).isClosed();
        return connection;
    }

    /**
     * A mock of an authenticated, non-anonymous client session that has a connection, a full JID and a stream ID.
     *
     * The session will set its closed flag when it is closed through invocation of either close() or close(StreamError)
     */
    private static LocalClientSession authenticatedClientSession()
    {
        final LocalClientSession session = mock(LocalClientSession.class);
        final AuthToken authToken = mock(AuthToken.class);
        doReturn(false).when(authToken).isAnonymous();
        doReturn(authToken).when(session).getAuthToken();
        doReturn(closableConnection()).when(session).getConnection();
        doReturn(true).when(session).isAuthenticated();
        doReturn(FULL_JID).when(session).getAddress();
        doReturn(BasicStreamIDFactory.createStreamID(STREAM_ID)).when(session).getStreamID();
        mockCloseTracking(session);
        return session;
    }

    /**
     * Creates a stream manager with Stream Management enabled, and clears the interaction with the session that resulted
     * from enabling it.
     */
    private static StreamManager enabledStreamManager(final LocalSession session, final String namespace)
    {
        final StreamManager sm = new StreamManager(session);
        sm.process(smElement("enable", namespace));
        assertTrue(sm.isEnabled(), "Test fixture expects Stream Management to be enabled.");
        clearInvocations(session);
        return sm;
    }

    /**
     * A mock of a detached client session that can be resumed: it has a non-anonymous authentication token, and is the
     * route for its full JID in the routing table of a mocked server.
     */
    private static LocalClientSession resumableClientSession()
    {
        final LocalClientSession session = authenticatedClientSession();
        final AuthToken authToken = mock(AuthToken.class);
        doReturn(FULL_JID.getNode()).when(authToken).getUsername();
        doReturn(authToken).when(session).getAuthToken();
        doReturn(true).when(session).isDetached();

        final XMPPServer server = Fixtures.mockXMPPServer();
        final RoutingTable routingTable = mock(RoutingTable.class);
        doReturn(session).when(routingTable).getClientRoute(FULL_JID);
        doReturn(routingTable).when(server).getRoutingTable();
        //noinspection deprecation
        XMPPServer.setInstance(server);
        return session;
    }

    /**
     * A mock of a new client session from which a resumption is requested: it authenticated as the same user, but did
     * not bind a resource.
     */
    private static LocalClientSession resumingClientSession()
    {
        final LocalClientSession session = mock(LocalClientSession.class);
        final AuthToken authToken = mock(AuthToken.class);
        doReturn(FULL_JID.getNode()).when(authToken).getUsername();
        doReturn(authToken).when(session).getAuthToken();
        doReturn(false).when(session).isAuthenticated();
        doReturn(Fixtures.XMPP_DOMAIN).when(session).getServerName();
        doReturn(BasicStreamIDFactory.createStreamID("resuming-stream-id")).when(session).getStreamID();
        mockCloseTracking(session);
        return session;
    }

    /**
     * Creates the stream manager of a session, with Stream Management enabled with resumption. The {@code <enabled/>}
     * element remains the last element that was delivered to the session.
     */
    private static StreamManager streamManagerWithResumption(final LocalClientSession session)
    {
        final StreamManager sm = new StreamManager(session);
        doReturn(sm).when(session).getStreamManager();
        final Element enable = smElement("enable", StreamManager.NAMESPACE_V3);
        enable.addAttribute("resume", "true");
        sm.process(enable);
        assertTrue(sm.getResume(), "Test fixture expects a resumable stream.");
        return sm;
    }

    private static Element resume(final String previd, final long h)
    {
        return smElement("resume", StreamManager.NAMESPACE_V3).addAttribute("previd", previd).addAttribute("h", String.valueOf(h));
    }

    private static Element smElement(final String name, final String namespace)
    {
        return DocumentHelper.createElement(QName.get(name, namespace));
    }

    private static Element ack(final String namespace, final String h)
    {
        return smElement("a", namespace).addAttribute("h", h);
    }

    private static Message message(final String id)
    {
        final Message message = new Message();
        message.setID(id);
        return message;
    }

    /**
     * Returns the last element that was delivered to the session.
     */
    private static Element lastDelivered(final LocalSession session) throws DocumentException
    {
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(session, atLeastOnce().description("Expected something to be delivered to the session.")).deliverRawText(captor.capture());
        return DocumentHelper.parseText(captor.getValue()).getRootElement();
    }

    /**
     * Returns the IDs of the messages that are unacknowledged, in order. This closes the stream manager, as it uses the
     * routing of unacknowledged messages on close to observe them.
     */
    private static List<String> unacknowledgedMessageIds(final StreamManager sm)
    {
        final PacketRouter router = mock(PacketRouter.class);
        sm.onClose(router, SERVER_ADDRESS);

        final ArgumentCaptor<Packet> captor = ArgumentCaptor.forClass(Packet.class);
        verify(router, atLeast(0)).route(captor.capture());
        return captor.getAllValues().stream().map(Packet::getID).toList();
    }

    private static void assertFailedWithUnexpectedRequest(final Element failed, final String namespace)
    {
        assertEquals(QName.get("failed", namespace), failed.getQName(), "The response must be a 'failed' element in the namespace of the request.");
        assertNotNull(failed.element(QName.get("unexpected-request", "urn:ietf:params:xml:ns:xmpp-stanzas")), "The response must report the 'unexpected-request' condition, but was: " + failed.asXML());
    }

    /**
     * Verifies that the session was closed with one stream error of the specified condition. Returns that stream
     * error's XML representation.
     *
     * @param expectedCondition the condition of the stream error
     * @return the XML representation of the stream error
     */
    private static String assertSessionClosedWithStreamError(final LocalSession session, final StreamError.Condition expectedCondition)
    {
        final ArgumentCaptor<StreamError> captor = ArgumentCaptor.forClass(StreamError.class);
        verify(session, times(1).description("The session must be closed with exactly one stream error.")).close(captor.capture());
        assertTrue(session.isClosed(), "The session must report itself as closed.");
        assertEquals(expectedCondition, captor.getValue().getCondition(), "The stream error must use the '" + expectedCondition + "' condition, but was: " + captor.getValue());
        return captor.getValue().toXML();
    }

    /**
     * Verifies that the session was closed with one stream error of the specified condition, containing the specified
     * text.
     *
     * @param expectedCondition the condition of the stream error
     * @param expectedText      the text that must be contained in the stream error
     */
    private static void assertSessionClosedWithStreamError(final LocalSession session, final StreamError.Condition expectedCondition, final String expectedText)
    {
        final ArgumentCaptor<StreamError> captor = ArgumentCaptor.forClass(StreamError.class);
        verify(session, times(1).description("The session must be closed with exactly one stream error.")).close(captor.capture());
        assertTrue(session.isClosed(), "The session must report itself as closed.");
        assertEquals(expectedCondition, captor.getValue().getCondition(), "The stream error must use the '" + expectedCondition + "' condition, but was: " + captor.getValue());
        final String streamError = captor.getValue().toXML();
        assertTrue(streamError.contains(expectedText), "The stream error must contain the text '" + expectedText + "', but was: " + streamError);
    }

    private static void assertHandledCountTooHigh(final LocalSession session, final String expectedH, final String expectedSendCount) throws DocumentException
    {
        final String streamError = assertSessionClosedWithStreamError(session, StreamError.Condition.undefined_condition);
        final Element element = DocumentHelper.parseText(streamError).getRootElement().element(QName.get("handled-count-too-high", StreamManager.NAMESPACE_V3));
        assertNotNull(element, "The stream error must include a 'handled-count-too-high' element, but was: " + streamError);
        assertEquals(expectedH, element.attributeValue("h"), "The element must report the value of 'h' that was received.");
        assertEquals(expectedSendCount, element.attributeValue("send-count"), "The element must report the number of stanzas that were sent.");
    }

    /**
     * Mocks the session to track whether it is closed.
     *
     * @param session the session to mock.
     */
    private static void mockCloseTracking(final LocalClientSession session)
    {
        final AtomicBoolean closed = new AtomicBoolean(false);

        doAnswer(invocation -> {
            closed.set(true);
            return null;
        }).when(session).close();

        doAnswer(invocation -> {
            closed.set(true);
            return null;
        }).when(session).close(nullable(StreamError.class));

        doAnswer(invocation -> closed.get()).when(session).isClosed();
    }

    /**
     * Sets a private counter of a stream manager, as sending 2^32 stanzas is not feasible in a unit test. The name of the
     * field can be a path of field names separated by dots, to reach a counter in an object that the stream manager holds.
     */
    private static void setCounter(final StreamManager sm, final String fieldName, final long value) throws Exception
    {
        Object holder = sm;
        final String[] names = fieldName.split("\\.");
        for (int i = 0; i < names.length - 1; i++) {
            holder = field(holder, names[i]).get(holder);
        }
        final Field counter = field(holder, names[names.length - 1]);
        if (counter.getType() == long.class) {
            counter.setLong(holder, value);
        } else {
            ((AtomicLong) counter.get(holder)).set(value);
        }
    }

    private static Field field(final Object holder, final String name) throws NoSuchFieldException
    {
        final Field field = holder.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
