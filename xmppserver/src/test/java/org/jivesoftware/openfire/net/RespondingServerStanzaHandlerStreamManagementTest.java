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
import org.dom4j.QName;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.session.DomainPair;
import org.jivesoftware.openfire.session.LocalOutgoingServerSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.session.ServerSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.openfire.streammanagement.RequestingStreamManager;
import org.jivesoftware.openfire.streammanagement.StreamManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.StreamError;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies that Openfire enables Stream Management (XEP-0198) on connections that it opens to other servers, by passing
 * the data that the remote server sends to a {@link RespondingServerStanzaHandler}, and inspecting what is delivered on
 * the connection in return.
 *
 * The connection is a mock. The remote server is simulated by the data passed to the handler: it authenticates
 * Openfire with dialback (a valid dialback result, without verification of the key) or with SASL EXTERNAL (a SASL
 * success, followed by a restart of the stream). On a connection that is opened to resume the stream of a detached
 * session, Openfire asks the remote server to resume that stream instead of enabling Stream Management.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class RespondingServerStanzaHandlerStreamManagementTest
{
    private static final String REMOTE_DOMAIN = "remote.example.org";

    private static final String SM_FEATURE_V2 = "<sm xmlns='" + StreamManager.NAMESPACE_V2 + "'/>";
    private static final String SM_FEATURE_V3 = "<sm xmlns='" + StreamManager.NAMESPACE_V3 + "'/>";

    private Connection connection;

    private RespondingServerStanzaHandler handler;

    private XMPPPacketReader reader;

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    public void setUp() throws Exception
    {
        Fixtures.clearExistingProperties();

        final XMPPServer xmppServer = Fixtures.mockXMPPServer();
        doReturn(mock(RoutingTable.class)).when(xmppServer).getRoutingTable();
        //noinspection deprecation
        XMPPServer.setInstance(xmppServer);

        final ConnectionConfiguration configuration = mock(ConnectionConfiguration.class, withSettings().lenient());
        doReturn(Connection.TLSPolicy.optional).when(configuration).getTlsPolicy();
        connection = mock(Connection.class, withSettings().lenient());
        doReturn(configuration).when(connection).getConfiguration();
        // The connection stays open: a completed close future would make the handler abandon a SASL negotiation (OF-3447).
        doReturn(new CompletableFuture<Void>()).when(connection).getCloseFuture();

        handler = new RespondingServerStanzaHandler(mock(PacketRouter.class), connection, new DomainPair(Fixtures.XMPP_DOMAIN, REMOTE_DOMAIN));
        reader = newPacketReader();
    }

    @AfterEach
    public void tearDown()
    {
        Fixtures.clearExistingProperties();
        //noinspection deprecation
        XMPPServer.setInstance(null);
    }

    /**
     * Verifies that Openfire asks a remote server that offers Stream Management to enable it once Openfire is
     * authenticated, without requesting resumption while resumption on server connections is disabled, for either method
     * of authentication.
     */
    @ParameterizedTest
    @EnumSource(value = ServerSession.AuthenticationMethod.class, names = {"DIALBACK", "SASL_EXTERNAL"})
    public void enableIsSentAfterAuthenticationWhenOffered(final ServerSession.AuthenticationMethod method) throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(true);

        // Execute system under test.
        authenticate(method, SM_FEATURE_V3);

        // Verify result.
        final List<Element> enables = deliveredEnableElements();
        assertEquals(1, enables.size(), "Exactly one request to enable Stream Management was expected, but these were delivered: " + enables);
        assertEquals(QName.get("enable", StreamManager.NAMESPACE_V3), enables.get(0).getQName(), "The request must use the offered version of Stream Management.");
        assertNull(enables.get(0).attributeValue("resume"), "Resumption must not be requested, but the request was: " + enables.get(0).asXML());
        assertEquals(RequestingStreamManager.EnableRequestStatus.PENDING, streamManager().getEnableRequestStatus(), "The request must await an answer of the remote server.");
        assertFalse(streamManager().isEnabled(), "Stream Management must not be enabled before the remote server agrees to it.");
    }

    /**
     * Verifies that Openfire requests a stream that can be resumed when resumption on server connections is enabled,
     * and that the stream can be resumed once the remote server allows that, for either method of authentication.
     */
    @ParameterizedTest
    @EnumSource(value = ServerSession.AuthenticationMethod.class, names = {"DIALBACK", "SASL_EXTERNAL"})
    public void resumptionIsRequestedWhenEnabled(final ServerSession.AuthenticationMethod method) throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(true);
        StreamManager.SERVER_RESUME_ENABLED.setValue(true);
        authenticate(method, SM_FEATURE_V3);

        // Execute system under test.
        handler.process("<enabled xmlns='" + StreamManager.NAMESPACE_V3 + "' resume='true' id='some-id' max='60'/>", reader);

        // Verify result.
        final List<Element> enables = deliveredEnableElements();
        assertEquals(1, enables.size(), "Exactly one request to enable Stream Management was expected, but these were delivered: " + enables);
        assertEquals("true", enables.get(0).attributeValue("resume"), "Resumption must be requested, but the request was: " + enables.get(0).asXML());
        assertTrue(streamManager().getResume(), "The stream must be resumable once the remote server allows it.");
        assertEquals(Duration.ofSeconds(60), streamManager().getPeerMaximumResumptionTime(), "The preferred maximum resumption time of the remote server must be recorded.");
    }

    /**
     * Verifies that Openfire does not ask a remote server to enable Stream Management before Openfire is
     * authenticated, even though dialback does not restart the stream after authentication, so that the offer is made
     * in the features that precede it.
     */
    @Test
    public void enableIsNotSentBeforeDialbackAuthentication() throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(true);
        handler.process(streamHeader("stream-1"), reader);

        // Execute system under test.
        handler.process(features("<dialback xmlns='urn:xmpp:features:dialback'/>" + SM_FEATURE_V3), reader);

        // Verify result.
        assertTrue(allDelivered().stream().anyMatch(text -> text.startsWith("<db:result")), "A dialback key was expected to be sent; without it this test proves nothing. Delivered: " + allDelivered());
        assertTrue(deliveredEnableElements().isEmpty(), "Stream Management must not be requested before authentication, but these were delivered: " + allDelivered());
        assertEquals(RequestingStreamManager.EnableRequestStatus.NOT_SENT, streamManager().getEnableRequestStatus(), "No request must have been made before authentication.");
    }

    /**
     * Verifies that Openfire uses version 2 of Stream Management with a remote server that offers only that version.
     */
    @Test
    public void enableUsesVersionTwoWhenOnlyThatIsOffered() throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(true);

        // Execute system under test.
        authenticate(ServerSession.AuthenticationMethod.DIALBACK, SM_FEATURE_V2);

        // Verify result.
        final List<Element> enables = deliveredEnableElements();
        assertEquals(1, enables.size(), "Exactly one request to enable Stream Management was expected, but these were delivered: " + enables);
        assertEquals(QName.get("enable", StreamManager.NAMESPACE_V2), enables.get(0).getQName(), "The request must use the only version that the remote server offers.");
    }

    /**
     * Verifies that Openfire does not try to enable Stream Management with a remote server that does not offer it, for
     * either method of authentication.
     */
    @ParameterizedTest
    @EnumSource(value = ServerSession.AuthenticationMethod.class, names = {"DIALBACK", "SASL_EXTERNAL"})
    public void enableIsNotSentWhenNotOffered(final ServerSession.AuthenticationMethod method) throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(true);

        // Execute system under test.
        authenticate(method, "");

        // Verify result.
        assertTrue(deliveredEnableElements().isEmpty(), "Stream Management must not be requested from a server that does not offer it, but these were delivered: " + allDelivered());
        assertEquals(RequestingStreamManager.EnableRequestStatus.SKIPPED, streamManager().getEnableRequestStatus(), "No request must have been made, and none must be made later.");
    }

    /**
     * Verifies that Openfire does not try to enable Stream Management with a remote server that offers it, when
     * Stream Management is not enabled for servers, for either method of authentication.
     */
    @ParameterizedTest
    @EnumSource(value = ServerSession.AuthenticationMethod.class, names = {"DIALBACK", "SASL_EXTERNAL"})
    public void enableIsNotSentWhenDisabledForServers(final ServerSession.AuthenticationMethod method) throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(false);

        // Execute system under test.
        authenticate(method, SM_FEATURE_V3);

        // Verify result.
        assertTrue(deliveredEnableElements().isEmpty(), "Stream Management must not be requested while it is disabled for servers, but these were delivered: " + allDelivered());
        assertEquals(RequestingStreamManager.EnableRequestStatus.SKIPPED, streamManager().getEnableRequestStatus(), "No request must have been made, and none must be made later.");
    }

    /**
     * Verifies that negotiation of Stream Management is reported as finished, without delay, when the remote server does
     * not offer Stream Management, for either method of authentication, so that the session can be used right away.
     */
    @ParameterizedTest
    @EnumSource(value = ServerSession.AuthenticationMethod.class, names = {"DIALBACK", "SASL_EXTERNAL"})
    public void negotiationIsFinishedWhenNotOffered(final ServerSession.AuthenticationMethod method) throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(true);

        // Execute system under test.
        authenticate(method, "");

        // Verify result.
        assertTrue(streamManager().getNegotiationOutcome().isDone(), "Negotiation must be reported as finished when the remote server does not offer Stream Management.");
    }

    /**
     * Verifies that negotiation of Stream Management is reported as finished when Stream Management is not enabled for
     * servers, even when the remote server offers it, for either method of authentication.
     */
    @ParameterizedTest
    @EnumSource(value = ServerSession.AuthenticationMethod.class, names = {"DIALBACK", "SASL_EXTERNAL"})
    public void negotiationIsFinishedWhenDisabledForServers(final ServerSession.AuthenticationMethod method) throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(false);

        // Execute system under test.
        authenticate(method, SM_FEATURE_V3);

        // Verify result.
        assertTrue(streamManager().getNegotiationOutcome().isDone(), "Negotiation must be reported as finished when Stream Management is not enabled for servers.");
    }

    /**
     * Verifies that negotiation of Stream Management is not reported as finished while the request to enable it awaits
     * an answer of the remote server, for either method of authentication, and is once the remote server answers.
     */
    @ParameterizedTest
    @EnumSource(value = ServerSession.AuthenticationMethod.class, names = {"DIALBACK", "SASL_EXTERNAL"})
    public void negotiationIsFinishedOnlyWhenAnswered(final ServerSession.AuthenticationMethod method) throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(true);
        authenticate(method, SM_FEATURE_V3);
        final CompletableFuture<RequestingStreamManager.EnableRequestStatus> outcome = streamManager().getNegotiationOutcome();
        assertFalse(outcome.isDone(), "Negotiation must not be reported as finished while the request awaits an answer.");

        // Execute system under test.
        handler.process("<enabled xmlns='" + StreamManager.NAMESPACE_V3 + "'/>", reader);

        // Verify result.
        assertTrue(outcome.isDone(), "Negotiation must be reported as finished once the remote server answered.");
        assertEquals(RequestingStreamManager.EnableRequestStatus.ENABLED, outcome.join(), "The outcome must report that Stream Management was enabled.");
    }

    /**
     * Verifies that, with SASL EXTERNAL, negotiation of Stream Management is not reported as finished when Openfire is
     * authenticated, but before the features of the restarted stream tell whether the remote server offers Stream
     * Management.
     */
    @Test
    public void negotiationIsNotFinishedBeforeFeaturesOfRestartedStream() throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(true);
        handler.process(streamHeader("stream-1"), reader);
        handler.process(features("<mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'><mechanism>EXTERNAL</mechanism></mechanisms>"), reader);
        handler.process("<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>", reader);

        // Execute system under test.
        handler.process(streamHeader("stream-2"), reader);

        // Verify result.
        assertTrue(handler.isSessionAuthenticated().isDone(), "Openfire was expected to be authenticated; without that this test proves nothing.");
        assertFalse(streamManager().getNegotiationOutcome().isDone(), "Negotiation must not be reported as finished before the features of the restarted stream are received.");
    }

    /**
     * Verifies that Stream Management is enabled on the session when the remote server agrees to the request, without
     * resumption, even when the remote server offers it, as resumption on server connections is disabled, for either
     * method of authentication.
     */
    @ParameterizedTest
    @EnumSource(value = ServerSession.AuthenticationMethod.class, names = {"DIALBACK", "SASL_EXTERNAL"})
    public void enabledIsRecorded(final ServerSession.AuthenticationMethod method) throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(true);
        authenticate(method, SM_FEATURE_V3);
        assertEquals(1, deliveredEnableElements().size(), "A request to enable Stream Management was expected; without it this test proves nothing.");
        final int deliveredBefore = allDelivered().size();

        // Execute system under test.
        handler.process("<enabled xmlns='" + StreamManager.NAMESPACE_V3 + "' resume='true' id='some-id'/>", reader);

        // Verify result.
        assertEquals(RequestingStreamManager.EnableRequestStatus.ENABLED, streamManager().getEnableRequestStatus(), "The agreement of the remote server must be recorded.");
        assertTrue(streamManager().isEnabled(), "Stream Management must be enabled once the remote server agrees to it.");
        assertFalse(streamManager().getResume(), "The stream must not be resumable, as resumption was not requested.");
        assertEquals(deliveredBefore, allDelivered().size(), "Nothing must be sent in answer to 'enabled', but these were delivered: " + allDelivered());
        verify(connection, never().description("The connection must not be closed.")).close(any(StreamError.class));
        verify(connection, never().description("The connection must not be closed.")).close();
    }

    /**
     * Verifies that Stream Management is not enabled on the session when the remote server refuses the request, and
     * that the refusal does not break the connection, for either method of authentication.
     */
    @ParameterizedTest
    @EnumSource(value = ServerSession.AuthenticationMethod.class, names = {"DIALBACK", "SASL_EXTERNAL"})
    public void failedIsRecorded(final ServerSession.AuthenticationMethod method) throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(true);
        authenticate(method, SM_FEATURE_V3);
        assertEquals(1, deliveredEnableElements().size(), "A request to enable Stream Management was expected; without it this test proves nothing.");
        final int deliveredBefore = allDelivered().size();

        // Execute system under test.
        handler.process("<failed xmlns='" + StreamManager.NAMESPACE_V3 + "'><unexpected-request xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/></failed>", reader);

        // Verify result.
        assertEquals(RequestingStreamManager.EnableRequestStatus.REFUSED, streamManager().getEnableRequestStatus(), "The refusal of the remote server must be recorded.");
        assertFalse(streamManager().isEnabled(), "Stream Management must not be enabled when the remote server refuses it.");
        assertEquals(deliveredBefore, allDelivered().size(), "Nothing must be sent in answer to 'failed', but these were delivered: " + allDelivered());
        verify(connection, never().description("The connection must not be closed.")).close(any(StreamError.class));
        verify(connection, never().description("The connection must not be closed.")).close();
    }

    /**
     * Verifies that a request for acknowledgement from the remote server, which sends no stanzas on this connection,
     * is answered without breaking the connection, for either method of authentication.
     */
    @ParameterizedTest
    @EnumSource(value = ServerSession.AuthenticationMethod.class, names = {"DIALBACK", "SASL_EXTERNAL"})
    public void requestIsAnsweredWhenEnabled(final ServerSession.AuthenticationMethod method) throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(true);
        authenticate(method, SM_FEATURE_V3);
        handler.process("<enabled xmlns='" + StreamManager.NAMESPACE_V3 + "'/>", reader);
        assertTrue(streamManager().isEnabled(), "Stream Management was expected to be enabled; without it this test proves nothing.");

        // Execute system under test.
        handler.process("<r xmlns='" + StreamManager.NAMESPACE_V3 + "'/>", reader);

        // Verify result.
        final Element ack = lastDelivered();
        assertEquals(QName.get("a", StreamManager.NAMESPACE_V3), ack.getQName(), "A request for acknowledgement must be answered with an 'a' element, but the response was: " + ack.asXML());
        assertEquals("0", ack.attributeValue("h"), "The remote server sent no stanzas, so none can have been handled.");
        verify(connection, never().description("The connection must not be closed.")).close(any(StreamError.class));
        verify(connection, never().description("The connection must not be closed.")).close();
    }

    /**
     * Verifies that a request for acknowledgement from a remote server that refused to enable Stream Management does
     * not break the connection.
     */
    @Test
    public void requestDoesNotBreakConnectionWhenRefused() throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(true);
        authenticate(ServerSession.AuthenticationMethod.DIALBACK, SM_FEATURE_V3);
        handler.process("<failed xmlns='" + StreamManager.NAMESPACE_V3 + "'/>", reader);
        assertEquals(RequestingStreamManager.EnableRequestStatus.REFUSED, streamManager().getEnableRequestStatus(), "Stream Management was expected to be refused; without it this test proves nothing.");
        final int deliveredBefore = allDelivered().size();

        // Execute system under test.
        handler.process("<r xmlns='" + StreamManager.NAMESPACE_V3 + "'/>", reader);

        // Verify result.
        assertEquals(deliveredBefore, allDelivered().size(), "Nothing must be sent in answer to a request for acknowledgement while Stream Management is not enabled, but these were delivered: " + allDelivered());
        verify(connection, never().description("The connection must not be closed.")).close(any(StreamError.class));
        verify(connection, never().description("The connection must not be closed.")).close();
    }

    /**
     * Verifies that a remote server cannot enable Stream Management on a connection that Openfire opened, as only
     * Openfire asks for it on such a connection, even when Stream Management is enabled for servers.
     */
    @Test
    public void enableFromRemoteServerIsRefused() throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(true);
        authenticate(ServerSession.AuthenticationMethod.DIALBACK, "");

        // Execute system under test.
        handler.process("<enable xmlns='" + StreamManager.NAMESPACE_V3 + "'/>", reader);

        // Verify result.
        final Element failed = lastDelivered();
        assertEquals(QName.get("failed", StreamManager.NAMESPACE_V3), failed.getQName(), "The request must be refused with a 'failed' element in the namespace of the request, but the response was: " + failed.asXML());
        assertNotNull(failed.element(QName.get("unexpected-request", "urn:ietf:params:xml:ns:xmpp-stanzas")), "The response must report the 'unexpected-request' condition, but was: " + failed.asXML());
        assertFalse(streamManager().isEnabled(), "Stream Management must not be enabled at the request of the remote server.");
        verify(connection, never().description("The connection must not be closed.")).close(any(StreamError.class));
        verify(connection, never().description("The connection must not be closed.")).close();
    }

    /**
     * Verifies that refusing a request of the remote server to enable Stream Management leaves Stream Management that
     * Openfire enabled on the connection enabled.
     */
    @Test
    public void enableFromRemoteServerAfterEnabledLeavesItEnabled() throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(true);
        authenticate(ServerSession.AuthenticationMethod.DIALBACK, SM_FEATURE_V3);
        handler.process("<enabled xmlns='" + StreamManager.NAMESPACE_V3 + "'/>", reader);
        assertTrue(streamManager().isEnabled(), "Stream Management was expected to be enabled; without it this test proves nothing.");

        // Execute system under test.
        handler.process("<enable xmlns='" + StreamManager.NAMESPACE_V3 + "'/>", reader);

        // Verify result.
        assertEquals(QName.get("failed", StreamManager.NAMESPACE_V3), lastDelivered().getQName(), "The request must be refused, but the response was: " + lastDelivered().asXML());
        assertTrue(streamManager().isEnabled(), "Stream Management that Openfire enabled must remain enabled.");
        assertEquals(RequestingStreamManager.EnableRequestStatus.ENABLED, streamManager().getEnableRequestStatus(), "The outcome of the request of Openfire must remain recorded.");
    }

    /**
     * Verifies that a Stream Management element that the end that asks does not process, other than a request to
     * enable Stream Management, is refused, and leaves Stream Management that Openfire enabled on the connection
     * enabled.
     */
    @ParameterizedTest
    @ValueSource(strings = {"resume", "resumed", "enabled", "failed", "unknown"})
    public void elementThatIsNotProcessedAfterEnabledLeavesItEnabled(final String name) throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(true);
        authenticate(ServerSession.AuthenticationMethod.DIALBACK, SM_FEATURE_V3);
        handler.process("<enabled xmlns='" + StreamManager.NAMESPACE_V3 + "'/>", reader);
        assertTrue(streamManager().isEnabled(), "Stream Management was expected to be enabled; without it this test proves nothing.");

        // Execute system under test.
        handler.process("<" + name + " xmlns='" + StreamManager.NAMESPACE_V3 + "'/>", reader);

        // Verify result.
        final Element failed = lastDelivered();
        assertEquals(QName.get("failed", StreamManager.NAMESPACE_V3), failed.getQName(), "The '" + name + "' element must be refused with a 'failed' element, but the response was: " + failed.asXML());
        assertNotNull(failed.element(QName.get("unexpected-request", "urn:ietf:params:xml:ns:xmpp-stanzas")), "The response must report the 'unexpected-request' condition, but was: " + failed.asXML());
        assertTrue(streamManager().isEnabled(), "Stream Management that Openfire enabled must remain enabled after a '" + name + "' element.");
        assertEquals(RequestingStreamManager.EnableRequestStatus.ENABLED, streamManager().getEnableRequestStatus(), "The outcome of the request of Openfire must remain recorded after a '" + name + "' element.");
        verify(connection, never().description("The connection must not be closed.")).close(any(StreamError.class));
        verify(connection, never().description("The connection must not be closed.")).close();
    }

    /**
     * Verifies that an 'enabled' element that does not answer a request of Openfire is refused, and does not enable
     * Stream Management.
     */
    @Test
    public void enabledWithoutRequestIsRefused() throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(true);
        authenticate(ServerSession.AuthenticationMethod.DIALBACK, "");

        // Execute system under test.
        handler.process("<enabled xmlns='" + StreamManager.NAMESPACE_V3 + "'/>", reader);

        // Verify result.
        assertEquals(QName.get("failed", StreamManager.NAMESPACE_V3), lastDelivered().getQName(), "The element must be refused, but the response was: " + lastDelivered().asXML());
        assertFalse(streamManager().isEnabled(), "Stream Management must not be enabled without a request of Openfire.");
        assertEquals(RequestingStreamManager.EnableRequestStatus.SKIPPED, streamManager().getEnableRequestStatus(), "No request must be recorded.");
    }

    /**
     * Verifies that, on a connection that is opened to resume the stream of a detached session, Openfire asks the remote
     * server to resume that stream once Openfire is authenticated, instead of enabling Stream Management, for either
     * method of authentication.
     */
    @ParameterizedTest
    @EnumSource(value = ServerSession.AuthenticationMethod.class, names = {"DIALBACK", "SASL_EXTERNAL"})
    public void resumeIsSentInsteadOfEnableOnConnectionForResumption(final ServerSession.AuthenticationMethod method) throws Exception
    {
        // Setup test fixture.
        final LocalOutgoingServerSession target = detachedSession();
        handler = new RespondingServerStanzaHandler(mock(PacketRouter.class), connection, new DomainPair(Fixtures.XMPP_DOMAIN, REMOTE_DOMAIN), target);

        // Execute system under test.
        authenticate(method, SM_FEATURE_V3);

        // Verify result.
        assertEquals(List.of(), deliveredEnableElements(), "Stream Management must not be enabled on a connection that is opened to resume a stream.");
        final Element resume = lastDelivered();
        assertEquals(QName.get("resume", StreamManager.NAMESPACE_V3), resume.getQName(), "The remote server must be asked to resume the stream, but this was delivered: " + resume.asXML());
        assertEquals("sm-id-former", resume.attributeValue("previd"), "The request must name the SM-ID of the stream of the detached session.");
        assertEquals(RequestingStreamManager.ResumeRequestStatus.PENDING, streamManager().getResumeRequestStatus(), "The request must await an answer of the remote server.");
        assertNotSame(target, handler.getSession(), "The connection must not be attached to the detached session before the remote server resumed the stream.");
    }

    /**
     * Verifies that Openfire does not ask the remote server to resume a stream of which the version of Stream
     * Management is not offered on the new connection, and does not enable Stream Management on it either.
     */
    @Test
    public void resumeIsNotSentWhenVersionOfStreamIsNotOffered() throws Exception
    {
        // Setup test fixture.
        final LocalOutgoingServerSession target = detachedSession();
        handler = new RespondingServerStanzaHandler(mock(PacketRouter.class), connection, new DomainPair(Fixtures.XMPP_DOMAIN, REMOTE_DOMAIN), target);

        // Execute system under test.
        authenticate(ServerSession.AuthenticationMethod.DIALBACK, SM_FEATURE_V2);

        // Verify result.
        assertTrue(allDelivered().stream().noneMatch(text -> text.startsWith("<resume") || text.startsWith("<enable")), "Neither resumption nor Stream Management must be requested, but these were delivered: " + allDelivered());
        assertEquals(RequestingStreamManager.ResumeRequestStatus.NOT_SENT, streamManager().getResumeRequestStatus(), "No request must be recorded.");
        assertTrue(streamManager().getResumeRequestFinished().isDone(), "A request that is not sent must be reported as finished, so that the detached session can be ended without waiting.");
    }

    /**
     * Verifies that, when the remote server resumes the stream, the connection is attached to the detached session,
     * which sends the stanzas that the remote server did not receive, followed by the stanzas that were queued, in
     * order, for either method of authentication.
     */
    @ParameterizedTest
    @EnumSource(value = ServerSession.AuthenticationMethod.class, names = {"DIALBACK", "SASL_EXTERNAL"})
    public void resumedAttachesConnectionAndSendsMissingStanzasInOrder(final ServerSession.AuthenticationMethod method) throws Exception
    {
        // Setup test fixture.
        final LocalOutgoingServerSession target = detachedSession();
        handler = new RespondingServerStanzaHandler(mock(PacketRouter.class), connection, new DomainPair(Fixtures.XMPP_DOMAIN, REMOTE_DOMAIN), target);
        // As NettyConnection#reinit does, the stanza handler continues with the session that takes over the connection.
        doAnswer(invocation -> { handler.setSession(invocation.getArgument(0)); return null; }).when(connection).reinit(any());
        authenticate(method, SM_FEATURE_V3);

        // Execute system under test.
        handler.process("<resumed xmlns='" + StreamManager.NAMESPACE_V3 + "' previd='sm-id-former' h='1'/>", reader);

        // Verify result.
        final ArgumentCaptor<Packet> captor = ArgumentCaptor.forClass(Packet.class);
        verify(connection, atLeastOnce().description("Stanzas were expected to be delivered on the connection.")).deliver(captor.capture());
        assertEquals(List.of("unacked-1", "unacked-2", "queued"), captor.getAllValues().stream().map(Packet::getID).toList(), "The stanzas that the remote server did not receive must be sent first, then the queued stanza, in order.");
        assertSame(target, handler.getSession(), "The connection must be attached to the session of which the stream is resumed.");
        assertSame(connection, target.getConnection(), "The resumed session must use the connection.");
        assertFalse(XMPPServer.getInstance().getSessionManager().isDetached(target), "The resumed session must no longer be detached.");
        assertEquals(method, target.getAuthenticationMethod(), "The resumed session must report the authentication method of the connection.");
        verify(connection, never().description("The connection must not be closed.")).close(any(StreamError.class));
        verify(connection, never().description("The connection must not be closed.")).close();
    }

    /**
     * Creates a detached outgoing server session, on a connection of its own, of which the stream can be resumed. It
     * tracks three stanzas, of which the remote server acknowledged the first, and queued one. A session manager is used
     * that holds the detached sessions.
     */
    private static LocalOutgoingServerSession detachedSession() throws Exception
    {
        StreamManager.SERVER_ACTIVE.setValue(true);
        StreamManager.SERVER_RESUME_ENABLED.setValue(true);
        final XMPPServer xmppServer = XMPPServer.getInstance();
        final SessionManager sessionManager = new SessionManager();
        sessionManager.initialize(xmppServer);
        doReturn(sessionManager).when(xmppServer).getSessionManager();

        final Connection formerConnection = mock(Connection.class, withSettings().lenient());
        doReturn(new CompletableFuture<Void>()).when(formerConnection).getCloseFuture();
        final LocalOutgoingServerSession target = new LocalOutgoingServerSession(Fixtures.XMPP_DOMAIN, formerConnection, BasicStreamIDFactory.createStreamID("stream-former"));
        target.setAddress(new JID(null, REMOTE_DOMAIN, null));
        target.setAuthenticationMethod(ServerSession.AuthenticationMethod.SASL_EXTERNAL);
        final RequestingStreamManager streamManager = target.getStreamManager();
        streamManager.requestEnable(StreamManager.NAMESPACE_V3);
        streamManager.process(DocumentHelper.createElement(QName.get("enabled", StreamManager.NAMESPACE_V3)).addAttribute("resume", "true").addAttribute("id", "sm-id-former"));
        streamManager.sentStanza(message("acked"));
        streamManager.sentStanza(message("unacked-1"));
        streamManager.sentStanza(message("unacked-2"));
        streamManager.process(DocumentHelper.createElement(QName.get("a", StreamManager.NAMESPACE_V3)).addAttribute("h", "1"));
        target.setStatus(Session.Status.CLOSED); // As the connection does when it closes, which clears the authentication method.
        target.setDetached();
        streamManager.queue(message("queued"), true);
        assertTrue(sessionManager.isDetached(target), "Test fixture expects the session to be detached.");
        return target;
    }

    private static Message message(final String id)
    {
        final Message message = new Message();
        message.setID(id);
        return message;
    }

    /**
     * Opens a stream on the handler, and lets the remote server authenticate Openfire with the provided method. The
     * features of the stream on which Stream Management can be enabled include the provided ones: for dialback, those
     * of the first stream, and for SASL EXTERNAL, those of the stream that is restarted after authentication.
     */
    private void authenticate(final ServerSession.AuthenticationMethod method, final String extraFeatures) throws Exception
    {
        handler.process(streamHeader("stream-1"), reader);
        if (method == ServerSession.AuthenticationMethod.DIALBACK) {
            handler.process(features("<dialback xmlns='urn:xmpp:features:dialback'/>" + extraFeatures), reader);
            handler.process("<db:result xmlns:db='jabber:server:dialback' from='" + REMOTE_DOMAIN + "' to='" + Fixtures.XMPP_DOMAIN + "' type='valid'/>", reader);
        } else {
            handler.process(features("<mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'><mechanism>EXTERNAL</mechanism></mechanisms>"), reader);
            handler.process("<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>", reader);
            handler.process(streamHeader("stream-2"), reader);
            handler.process(features(extraFeatures), reader);
        }
        assertTrue(handler.isSessionAuthenticated().isDone(), "Openfire was expected to be authenticated; without that this test proves nothing.");
        assertEquals(method, ((ServerSession) handler.getSession()).getAuthenticationMethod(), "Openfire was expected to be authenticated with " + method + "; without that this test proves nothing.");
    }

    private static String streamHeader(final String streamId)
    {
        return "<stream:stream xmlns:stream='http://etherx.jabber.org/streams' xmlns='jabber:server' xmlns:db='jabber:server:dialback' from='" + REMOTE_DOMAIN + "' to='" + Fixtures.XMPP_DOMAIN + "' id='" + streamId + "' version='1.0'>";
    }

    private static String features(final String content)
    {
        return "<stream:features>" + content + "</stream:features>";
    }

    private RequestingStreamManager streamManager()
    {
        final LocalSession session = handler.getSession();
        assertInstanceOf(LocalOutgoingServerSession.class, session, "Expected the handler to have an outgoing server session.");
        return ((LocalOutgoingServerSession) session).getStreamManager();
    }

    /**
     * Returns everything that was delivered on the connection, in order.
     */
    private List<String> allDelivered()
    {
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(connection, atLeast(0)).deliverRawText(captor.capture());
        return captor.getAllValues();
    }

    /**
     * Returns the requests to enable Stream Management that were delivered on the connection, in order.
     */
    private List<Element> deliveredEnableElements() throws Exception
    {
        final List<Element> result = new ArrayList<>();
        for (final String text : allDelivered()) {
            if (text.startsWith("<enable")) {
                result.add(DocumentHelper.parseText(text).getRootElement());
            }
        }
        return result;
    }

    /**
     * Returns the last element that was delivered on the connection.
     */
    private Element lastDelivered() throws Exception
    {
        final List<String> delivered = allDelivered();
        assertFalse(delivered.isEmpty(), "Expected something to be delivered on the connection.");
        return DocumentHelper.parseText(delivered.get(delivered.size() - 1)).getRootElement();
    }

    /**
     * Returns a namespace-aware packet reader, configured as the production code configures its own.
     */
    private static XMPPPacketReader newPacketReader() throws Exception
    {
        final XmlPullParserFactory factory = XmlPullParserFactory.newInstance(MXParser.class.getName(), null);
        factory.setNamespaceAware(true);
        final XMPPPacketReader reader = new XMPPPacketReader();
        reader.setXPPFactory(factory);
        return reader;
    }
}
