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

import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.ConnectionCloseListener;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.XMPPServerInfo;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.keystore.IdentityStore;
import org.jivesoftware.openfire.net.MXParser;
import org.jivesoftware.openfire.net.ServerStanzaHandler;
import org.jivesoftware.openfire.server.ServerDialback;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.openfire.streammanagement.StreamManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.mockito.MockedConstruction;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmpp.packet.Message;
import org.xmpp.packet.StreamError;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies that a server that connected to Openfire can resume the stream of a session that Openfire kept after its
 * connection broke (XEP-0198 § 5), on a new connection on which it authenticated.
 *
 * These tests use sessions with mocked connections and a session manager that keeps track of the sessions (a spy, so
 * that a test can change what one of its methods does), and pass Stream Management elements to the stream manager of
 * a session directly, as the stanza handler does.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class LocalIncomingServerSessionResumptionTest
{
    private static final String REMOTE_DOMAIN = "remote.example.org";
    private static final String OTHER_DOMAIN = "other.example.org";
    private static final String EXTRA_DOMAIN = "extra.example.org";
    private static final String NEW_DOMAIN = "new.example.org";
    private static final String NODE_HOSTNAME = "node1.example.com";

    private static final String STREAM_HEADER = "<stream:stream xmlns:stream='http://etherx.jabber.org/streams' xmlns='jabber:server' xmlns:db='jabber:server:dialback' from='" + REMOTE_DOMAIN + "' to='" + Fixtures.XMPP_DOMAIN + "' version='1.0'>";

    private XMPPServer server;
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
        Fixtures.clearExistingProperties();

        server = Fixtures.mockXMPPServer();
        doReturn(mock(RoutingTable.class, withSettings().lenient())).when(server).getRoutingTable();
        doReturn(mock(PacketRouter.class, withSettings().lenient())).when(server).getPacketRouter();
        // Initialised before it is spied on, so that its close listeners, which belong to the original, share its state.
        final SessionManager original = new SessionManager();
        original.initialize(server);
        sessionManager = spy(original);
        doReturn(sessionManager).when(server).getSessionManager();

        //noinspection deprecation
        XMPPServer.setInstance(server);

        StreamManager.SERVER_ACTIVE.setValue(true);
        StreamManager.SERVER_RESUME_ENABLED.setValue(true);
    }

    @AfterEach
    public void tearDown()
    {
        Fixtures.clearExistingProperties();
        //noinspection deprecation
        XMPPServer.setInstance(null);
    }

    /**
     * Verifies that a resume request for a kept session moves the new connection to that session, which is no longer
     * detached, and that the session confirms the resumption on that connection with the number of stanzas that it
     * handled on its former stream.
     */
    @Test
    void resumeReattachesKeptSessionToNewConnection() throws Exception
    {
        // Setup test fixture.
        final Connection formerConnection = connection();
        final LocalIncomingServerSession original = authenticatedSession(formerConnection);
        final String previd = enableResumption(original, formerConnection);
        original.incrementClientPacketCount();
        original.incrementClientPacketCount();
        original.incrementClientPacketCount();
        original.setDetached();
        final Connection newConnection = connection();
        final LocalIncomingServerSession resuming = authenticatedSession(newConnection);

        // Execute system under test.
        resuming.getStreamManager().process(resume(previd, 0));

        // Verify result.
        assertSame(newConnection, original.getConnection(), "The kept session must use the connection on which its stream is resumed.");
        assertFalse(original.isDetached(), "A session of which the stream is resumed must no longer be detached.");
        final Element resumed = rawTextDelivered(newConnection).get(0);
        assertEquals(QName.get("resumed", StreamManager.NAMESPACE_V3), resumed.getQName(), "The resumption must be confirmed with a 'resumed' element, but got: " + resumed.asXML());
        assertEquals(previd, resumed.attributeValue("previd"), "The 'resumed' element must identify the former stream with its SM-ID.");
        assertEquals("3", resumed.attributeValue("h"), "The 'resumed' element must report the number of stanzas that were handled on the former stream.");
    }

    /**
     * Verifies that the count of handled stanzas continues from the former stream after a resumption, so that an
     * acknowledgement on the new connection covers the stanzas of both.
     */
    @Test
    void countOfHandledStanzasContinuesAfterResumption() throws Exception
    {
        // Setup test fixture.
        final Connection formerConnection = connection();
        final LocalIncomingServerSession original = authenticatedSession(formerConnection);
        final String previd = enableResumption(original, formerConnection);
        original.incrementClientPacketCount();
        original.incrementClientPacketCount();
        original.incrementClientPacketCount();
        original.setDetached();
        final Connection newConnection = connection();
        final LocalIncomingServerSession resuming = authenticatedSession(newConnection);
        resuming.getStreamManager().process(resume(previd, 0));
        original.incrementClientPacketCount();
        original.incrementClientPacketCount();

        // Execute system under test.
        original.getStreamManager().process(DocumentHelper.createElement(QName.get("r", StreamManager.NAMESPACE_V3)));

        // Verify result.
        final List<Element> delivered = rawTextDelivered(newConnection);
        final Element ack = delivered.get(delivered.size() - 1);
        assertEquals(QName.get("a", StreamManager.NAMESPACE_V3), ack.getQName(), "A request for acknowledgement must be answered with an 'a' element, but got: " + ack.asXML());
        assertEquals("5", ack.attributeValue("h"), "The count must include the stanzas handled on the former stream and on the new connection.");
    }

    /**
     * Verifies that the session that provided the new connection is unregistered after the resumption, with the
     * domains that were validated for it, while the resumed session and its domains stay registered.
     */
    @Test
    void sessionThatProvidedConnectionIsUnregistered() throws Exception
    {
        // Setup test fixture.
        final Connection formerConnection = connection();
        final LocalIncomingServerSession original = authenticatedSession(formerConnection);
        final String previd = enableResumption(original, formerConnection);
        original.setDetached();
        final LocalIncomingServerSession resuming = authenticatedSession(connection());

        // Execute system under test.
        resuming.getStreamManager().process(resume(previd, 0));

        // Verify result.
        assertNull(sessionManager.getIncomingServerSession(resuming.getStreamID()), "The session that provided the connection must be unregistered after the resumption.");
        assertSame(original, sessionManager.getIncomingServerSession(original.getStreamID()), "The resumed session must stay registered.");
        assertEquals(List.of(original), sessionManager.getIncomingServerSessions(REMOTE_DOMAIN), "The validated domain must be registered for the resumed session only.");
    }

    /**
     * Verifies that a resume request for a session of which the former connection is still open closes that connection
     * with a 'conflict' stream error, as XEP-0198 § 5 recommends, and resumes the session.
     */
    @Test
    void resumeOfSessionWithOpenConnectionClosesThatConnection() throws Exception
    {
        // Setup test fixture.
        final Connection formerConnection = connection();
        final LocalIncomingServerSession original = authenticatedSession(formerConnection);
        final String previd = enableResumption(original, formerConnection);
        final Connection newConnection = connection();
        final LocalIncomingServerSession resuming = authenticatedSession(newConnection);

        // Execute system under test.
        resuming.getStreamManager().process(resume(previd, 0));

        // Verify result.
        final ArgumentCaptor<StreamError> captor = ArgumentCaptor.forClass(StreamError.class);
        verify(formerConnection, times(1).description("The former connection that is still open must be closed.")).close(captor.capture());
        assertEquals(StreamError.Condition.conflict, captor.getValue().getCondition(), "The former connection must be closed with a 'conflict' stream error (XEP-0198 § 5).");
        assertSame(newConnection, original.getConnection(), "The session must use the connection on which its stream is resumed.");
        assertFalse(original.isDetached(), "A session of which the stream is resumed must no longer be detached.");
    }

    /**
     * Verifies that the close listener does not detach a session of which the stream is resumed on a new connection
     * after the listener checked the connection that closed, and before it would detach the session: the session keeps
     * using the new connection, and is not detached.
     */
    @Test
    void sessionResumedWhileCloseIsHandledIsNotDetached() throws Exception
    {
        // Setup test fixture.
        final Connection formerConnection = connection();
        final LocalIncomingServerSession original = authenticatedSession(formerConnection);
        final String previd = enableResumption(original, formerConnection);
        final Connection newConnection = connection();
        final LocalIncomingServerSession resuming = authenticatedSession(newConnection);
        final AtomicBoolean resumedDuringCheck = new AtomicBoolean(false);
        // The stream is resumed, as on another thread, while the close listener checks whether the connection is closed.
        doAnswer(invocation -> {
            if (resumedDuringCheck.compareAndSet(false, true)) {
                resuming.getStreamManager().process(resume(previd, 0));
            }
            return true;
        }).when(formerConnection).isClosed();

        // Execute system under test.
        fireIncomingServerConnectionClose(original);

        // Verify result.
        assertTrue(resumedDuringCheck.get(), "Test fixture expects the stream to be resumed while the close listener checks the connection.");
        assertSame(newConnection, original.getConnection(), "A session that was resumed while the close of its former connection was handled must keep using the new connection.");
        assertFalse(original.isDetached(), "A session that was resumed while the close of its former connection was handled must not be detached.");
    }

    /**
     * Verifies that a resume request for a kept session that was ended is refused with 'item-not-found', as for a
     * session that Openfire does not recognise (XEP-0198 § 5), and that the session that requested it stays as it was.
     */
    @Test
    void resumeOfEndedSessionIsRefusedWithItemNotFound() throws Exception
    {
        // Setup test fixture.
        final Connection formerConnection = connection();
        final LocalIncomingServerSession original = authenticatedSession(formerConnection);
        final String previd = enableResumption(original, formerConnection);
        original.setDetached();
        sessionManager.terminateDetached(original);
        final Connection newConnection = connection();
        final LocalIncomingServerSession resuming = authenticatedSession(newConnection);

        // Execute system under test.
        resuming.getStreamManager().process(resume(previd, 0));

        // Verify result.
        assertFailed(rawTextDelivered(newConnection).get(0), "item-not-found");
        assertNull(original.getConnection(), "A session that was ended must not be given the new connection.");
        assertSame(newConnection, resuming.getConnection(), "The session that requested the resumption must keep its connection.");
        assertSame(resuming, sessionManager.getIncomingServerSession(resuming.getStreamID()), "The session that requested the resumption must stay registered.");
    }

    /**
     * Verifies that a session of which the remote server ends the stream with a closing stream tag is not kept when its
     * connection closes, so that its stream cannot be resumed. Only a session of which the connection breaks is kept.
     */
    @Test
    void sessionIsNotKeptWhenRemoteServerEndsStream() throws Exception
    {
        // Setup test fixture.
        final ResumableStream stream = resumableStreamOnNewConnection();

        // Execute system under test.
        stream.handler().process("</stream:stream>", stream.reader());

        // Verify result.
        assertEndedWithoutBeingKept(stream);
    }

    /**
     * Verifies that a session of which the remote server ends the stream with a stream error is not kept when its
     * connection closes, so that its stream cannot be resumed. Only a session of which the connection breaks is kept.
     */
    @Test
    void sessionIsNotKeptWhenRemoteServerEndsStreamWithStreamError() throws Exception
    {
        // Setup test fixture.
        final ResumableStream stream = resumableStreamOnNewConnection();

        // Execute system under test.
        stream.handler().process("<stream:error xmlns:stream='http://etherx.jabber.org/streams'><undefined-condition xmlns='urn:ietf:params:xml:ns:xmpp-streams'/></stream:error>", stream.reader());

        // Verify result.
        assertEndedWithoutBeingKept(stream);
    }

    /**
     * Verifies that a resume request for a detached session that can no longer be taken from the detached sessions is
     * refused with 'item-not-found'. This is the case when another thread ends the session, or resumes it on another
     * connection, after the request was checked and before the session is resumed.
     */
    @Test
    void resumeOfSessionThatCannotBeTakenIsRefusedWithItemNotFound() throws Exception
    {
        // Setup test fixture.
        final Connection formerConnection = connection();
        final LocalIncomingServerSession original = authenticatedSession(formerConnection);
        final String previd = enableResumption(original, formerConnection);
        original.setDetached();
        doReturn(false).when(sessionManager).removeDetached(original); // As when another thread took it first.
        final Connection newConnection = connection();
        final LocalIncomingServerSession resuming = authenticatedSession(newConnection);

        // Execute system under test.
        resuming.getStreamManager().process(resume(previd, 0));

        // Verify result.
        assertFailed(rawTextDelivered(newConnection).get(0), "item-not-found");
        assertNull(original.getConnection(), "A session that cannot be taken from the detached sessions must not be given the new connection.");
        assertSame(newConnection, resuming.getConnection(), "The session that requested the resumption must keep its connection.");
    }

    /**
     * Verifies that a resume request for a session that is still registered, but is neither detached nor has a
     * connection, is refused with 'item-not-found', and that the session is not detached again. This is the case when
     * the session is being ended (SessionManager#terminateDetached takes it from the detached sessions before it
     * unregisters its domains), or is being resumed on another connection.
     */
    @Test
    void resumeOfSessionThatWasTakenIsRefusedWithItemNotFound() throws Exception
    {
        // Setup test fixture.
        final Connection formerConnection = connection();
        final LocalIncomingServerSession original = authenticatedSession(formerConnection);
        final String previd = enableResumption(original, formerConnection);
        original.setDetached();
        assertTrue(sessionManager.removeDetached(original), "Test fixture expects the session to be taken from the detached sessions.");
        final Connection newConnection = connection();
        final LocalIncomingServerSession resuming = authenticatedSession(newConnection);

        // Execute system under test.
        resuming.getStreamManager().process(resume(previd, 0));

        // Verify result.
        assertFailed(rawTextDelivered(newConnection).get(0), "item-not-found");
        assertNull(original.getConnection(), "A session that was taken from the detached sessions must not be given the new connection.");
        assertFalse(original.isDetached(), "A session that was taken from the detached sessions must not be detached again.");
        assertSame(newConnection, resuming.getConnection(), "The session that requested the resumption must keep its connection.");
    }

    /**
     * Verifies that a resume request is refused while resumption is disabled for servers, also for a session that was
     * kept while it was enabled.
     */
    @Test
    void resumeIsRefusedWhenServerResumptionIsDisabled() throws Exception
    {
        // Setup test fixture.
        final Connection formerConnection = connection();
        final LocalIncomingServerSession original = authenticatedSession(formerConnection);
        final String previd = enableResumption(original, formerConnection);
        original.setDetached();
        StreamManager.SERVER_RESUME_ENABLED.setValue(false);
        final Connection newConnection = connection();
        final LocalIncomingServerSession resuming = authenticatedSession(newConnection);

        // Execute system under test.
        resuming.getStreamManager().process(resume(previd, 0));

        // Verify result.
        assertFailed(rawTextDelivered(newConnection).get(0), "unexpected-request");
        assertTrue(original.isDetached(), "A session that is not resumed must stay detached.");
    }

    /**
     * Verifies that a session that was authenticated with a certificate can be resumed by the same domain with a
     * certificate.
     */
    @Test
    void resumeWithSameDomainAndMethodIsAccepted() throws Exception
    {
        // Setup test fixture.
        final Connection formerConnection = connection();
        final LocalIncomingServerSession original = authenticatedSession(formerConnection, REMOTE_DOMAIN, REMOTE_DOMAIN, ServerSession.AuthenticationMethod.SASL_EXTERNAL);
        final String previd = enableResumption(original, formerConnection);
        original.setDetached();
        final Connection newConnection = connection();
        final LocalIncomingServerSession resuming = authenticatedSession(newConnection, REMOTE_DOMAIN, REMOTE_DOMAIN, ServerSession.AuthenticationMethod.SASL_EXTERNAL);

        // Execute system under test.
        resuming.getStreamManager().process(resume(previd, 0));

        // Verify result.
        assertResumed(rawTextDelivered(newConnection).get(0));
        assertSame(newConnection, original.getConnection(), "The kept session must use the connection on which the same domain resumed it with the same method.");
    }

    /**
     * Verifies that a session that was authenticated with dialback can be resumed by the same domain with a
     * certificate, which is a stronger method.
     */
    @Test
    void resumeFromDialbackToCertificateIsAccepted() throws Exception
    {
        // Setup test fixture.
        final Connection formerConnection = connection();
        final LocalIncomingServerSession original = authenticatedSession(formerConnection, REMOTE_DOMAIN, REMOTE_DOMAIN, ServerSession.AuthenticationMethod.DIALBACK);
        final String previd = enableResumption(original, formerConnection);
        original.setDetached();
        final Connection newConnection = connection();
        final LocalIncomingServerSession resuming = authenticatedSession(newConnection, REMOTE_DOMAIN, REMOTE_DOMAIN, ServerSession.AuthenticationMethod.SASL_EXTERNAL);

        // Execute system under test.
        resuming.getStreamManager().process(resume(previd, 0));

        // Verify result.
        assertResumed(rawTextDelivered(newConnection).get(0));
        assertSame(newConnection, original.getConnection(), "The kept session must use the connection on which the same domain resumed it with a certificate.");
    }

    /**
     * Verifies that a resumed session reports the authentication method with which the remote server authenticated on
     * the new connection, also after the method was cleared when the former connection closed.
     */
    @Test
    void resumedSessionReportsAuthenticationMethodOfNewConnection() throws Exception
    {
        // Setup test fixture.
        final Connection formerConnection = connection();
        final LocalIncomingServerSession original = authenticatedSession(formerConnection, REMOTE_DOMAIN, REMOTE_DOMAIN, ServerSession.AuthenticationMethod.DIALBACK);
        final String previd = enableResumption(original, formerConnection);
        original.setStatus(Session.Status.CLOSED); // As the former connection does when it closes.
        original.setDetached();
        assertNull(original.getAuthenticationMethod(), "Test fixture expects the authentication method to be cleared when the former connection closes.");
        final LocalIncomingServerSession resuming = authenticatedSession(connection(), REMOTE_DOMAIN, REMOTE_DOMAIN, ServerSession.AuthenticationMethod.SASL_EXTERNAL);

        // Execute system under test.
        resuming.getStreamManager().process(resume(previd, 0));

        // Verify result.
        assertEquals(ServerSession.AuthenticationMethod.SASL_EXTERNAL, original.getAuthenticationMethod(), "A resumed session must report the authentication method used on the new connection.");
        assertTrue(original.isUsingSaslExternal(), "A resumed session must report that it uses the authentication method of the new connection.");
        assertEquals(Session.Status.AUTHENTICATED, original.getStatus(), "A resumed session must be authenticated.");
    }

    /**
     * Verifies that a session that was authenticated with a certificate cannot be resumed by the same domain with
     * dialback, which is a weaker method, and that this is refused with 'item-not-found'.
     */
    @Test
    void resumeFromCertificateToDialbackIsRefusedWithItemNotFound() throws Exception
    {
        // Setup test fixture.
        final Connection formerConnection = connection();
        final LocalIncomingServerSession original = authenticatedSession(formerConnection, REMOTE_DOMAIN, REMOTE_DOMAIN, ServerSession.AuthenticationMethod.SASL_EXTERNAL);
        final String previd = enableResumption(original, formerConnection);
        original.setDetached();
        final Connection newConnection = connection();
        final LocalIncomingServerSession resuming = authenticatedSession(newConnection, REMOTE_DOMAIN, REMOTE_DOMAIN, ServerSession.AuthenticationMethod.DIALBACK);

        // Execute system under test.
        resuming.getStreamManager().process(resume(previd, 0));

        // Verify result.
        assertFailed(rawTextDelivered(newConnection).get(0), "item-not-found");
        assertNull(original.getConnection(), "A session that was authenticated with a certificate must not be given a connection that used dialback.");
        assertTrue(original.isDetached(), "A session that is not resumed must stay detached.");
        assertSame(newConnection, resuming.getConnection(), "The session that requested the resumption must keep its connection.");
    }

    /**
     * Verifies that a session that was authenticated with a certificate, and on which another domain was authenticated
     * with dialback after that, still cannot be resumed with dialback: the method of the first domain counts.
     */
    @Test
    void resumeWithDialbackIsRefusedWhenOnlyLaterDomainUsedDialback() throws Exception
    {
        // Setup test fixture.
        final Connection formerConnection = connection();
        final LocalIncomingServerSession original = authenticatedSession(formerConnection, REMOTE_DOMAIN, REMOTE_DOMAIN, ServerSession.AuthenticationMethod.SASL_EXTERNAL);
        original.setAuthenticationMethod(ServerSession.AuthenticationMethod.DIALBACK);
        original.addValidatedDomain(OTHER_DOMAIN, ServerSession.AuthenticationMethod.DIALBACK);
        final String previd = enableResumption(original, formerConnection);
        original.setDetached();
        final Connection newConnection = connection();
        final LocalIncomingServerSession resuming = authenticatedSession(newConnection, REMOTE_DOMAIN, REMOTE_DOMAIN, ServerSession.AuthenticationMethod.DIALBACK);

        // Execute system under test.
        resuming.getStreamManager().process(resume(previd, 0));

        // Verify result.
        assertFailed(rawTextDelivered(newConnection).get(0), "item-not-found");
        assertNull(original.getConnection(), "A session of which the first domain was authenticated with a certificate must not be given a connection that used dialback.");
    }

    /**
     * Verifies that a session cannot be resumed by another domain, also not by one that was authenticated with a
     * stronger method, and that this is refused with 'item-not-found'.
     */
    @Test
    void resumeByOtherDomainIsRefusedWithItemNotFound() throws Exception
    {
        // Setup test fixture.
        final Connection formerConnection = connection();
        final LocalIncomingServerSession original = authenticatedSession(formerConnection, REMOTE_DOMAIN, REMOTE_DOMAIN, ServerSession.AuthenticationMethod.DIALBACK);
        final String previd = enableResumption(original, formerConnection);
        original.setDetached();
        final Connection newConnection = connection();
        final LocalIncomingServerSession resuming = authenticatedSession(newConnection, OTHER_DOMAIN, OTHER_DOMAIN, ServerSession.AuthenticationMethod.SASL_EXTERNAL);

        // Execute system under test.
        resuming.getStreamManager().process(resume(previd, 0));

        // Verify result.
        assertFailed(rawTextDelivered(newConnection).get(0), "item-not-found");
        assertNull(original.getConnection(), "A session must not be given a connection on which another domain authenticated.");
        assertTrue(original.isDetached(), "A session that is not resumed must stay detached.");
        assertSame(resuming, sessionManager.getIncomingServerSession(resuming.getStreamID()), "The session that requested the resumption must stay registered.");
    }

    /**
     * Verifies that the response to a resume request by another domain is the same as the response to a previd that
     * identifies no session, so that it does not reveal that the session exists.
     */
    @Test
    void refusalForOtherDomainIsSameAsForUnknownSession() throws Exception
    {
        // Setup test fixture.
        final Connection formerConnection = connection();
        final LocalIncomingServerSession original = authenticatedSession(formerConnection, REMOTE_DOMAIN, REMOTE_DOMAIN, ServerSession.AuthenticationMethod.DIALBACK);
        final String previd = enableResumption(original, formerConnection);
        original.incrementClientPacketCount();
        original.setDetached();
        final Connection connectionForKnown = connection();
        final LocalIncomingServerSession resumingKnown = authenticatedSession(connectionForKnown, OTHER_DOMAIN, OTHER_DOMAIN, ServerSession.AuthenticationMethod.DIALBACK);
        final Connection connectionForUnknown = connection();
        final LocalIncomingServerSession resumingUnknown = authenticatedSession(connectionForUnknown, OTHER_DOMAIN, OTHER_DOMAIN, ServerSession.AuthenticationMethod.DIALBACK);
        final String unknownPrevid = Base64.getEncoder().encodeToString("unknown-stream-id".getBytes(StandardCharsets.UTF_8));

        // Execute system under test.
        resumingKnown.getStreamManager().process(resume(previd, 0));
        resumingUnknown.getStreamManager().process(resume(unknownPrevid, 0));

        // Verify result.
        final Element refusedKnown = rawTextDelivered(connectionForKnown).get(0);
        final Element refusedUnknown = rawTextDelivered(connectionForUnknown).get(0);
        assertFailed(refusedKnown, "item-not-found");
        assertEquals(refusedUnknown.asXML(), refusedKnown.asXML(), "The response for a session that exists, but that another domain may not resume, must be the same as for a previd that identifies no session.");
    }

    /**
     * Verifies that a session is not resumed by a server that claims the domain of the session in its stream header,
     * but authenticated another domain.
     */
    @Test
    void resumeByServerThatClaimedDomainOfSessionButAuthenticatedAnotherIsRefused() throws Exception
    {
        // Setup test fixture.
        final Connection formerConnection = connection();
        final LocalIncomingServerSession original = authenticatedSession(formerConnection, REMOTE_DOMAIN, REMOTE_DOMAIN, ServerSession.AuthenticationMethod.DIALBACK);
        final String previd = enableResumption(original, formerConnection);
        original.setDetached();
        final Connection newConnection = connection();
        final LocalIncomingServerSession resuming = authenticatedSession(newConnection, REMOTE_DOMAIN, OTHER_DOMAIN, ServerSession.AuthenticationMethod.DIALBACK);

        // Execute system under test.
        resuming.getStreamManager().process(resume(previd, 0));

        // Verify result.
        assertFailed(rawTextDelivered(newConnection).get(0), "item-not-found");
        assertNull(original.getConnection(), "A session must not be given a connection on which another domain authenticated than the one claimed in the stream header.");
    }

    /**
     * Verifies that a session is resumed by the server that authenticated the same domain, when the domain that was
     * claimed in the stream header of the session differs from the one that was authenticated on it.
     */
    @Test
    void resumeComparesAuthenticatedDomainRatherThanClaimedDomainOfSession() throws Exception
    {
        // Setup test fixture.
        final Connection formerConnection = connection();
        final LocalIncomingServerSession original = authenticatedSession(formerConnection, OTHER_DOMAIN, REMOTE_DOMAIN, ServerSession.AuthenticationMethod.DIALBACK);
        final String previd = enableResumption(original, formerConnection);
        original.setDetached();
        final Connection newConnection = connection();
        final LocalIncomingServerSession resuming = authenticatedSession(newConnection, REMOTE_DOMAIN, REMOTE_DOMAIN, ServerSession.AuthenticationMethod.DIALBACK);

        // Execute system under test.
        resuming.getStreamManager().process(resume(previd, 0));

        // Verify result.
        assertResumed(rawTextDelivered(newConnection).get(0));
        assertSame(newConnection, original.getConnection(), "The kept session must use the connection on which the domain that was authenticated on it resumed it.");
    }

    /**
     * Verifies that stanzas from every domain that was validated on the original connection are accepted on the new
     * connection after resumption, without the remote server authenticating any of them again (decision 2 of
     * OF-2425).
     */
    @Test
    void stanzasFromAllValidatedDomainsAreAcceptedAfterResumption() throws Exception
    {
        // Setup test fixture.
        final LocalIncomingServerSession original = keptSessionWithExtraDomains();
        final ResumedConnection resumed = resumeOnNewConnection(enabledPrevid);

        // Execute system under test.
        resumed.handler().process(message(REMOTE_DOMAIN), resumed.reader());
        resumed.handler().process(message(OTHER_DOMAIN), resumed.reader());
        resumed.handler().process(message(EXTRA_DOMAIN), resumed.reader());

        // Verify result.
        assertEquals(Set.of(REMOTE_DOMAIN, OTHER_DOMAIN, EXTRA_DOMAIN), sendersOfRoutedMessages(resumed.router()), "Stanzas from all domains that were validated on the original connection must be accepted after resumption.");
        assertSame(resumed.connection(), original.getConnection(), "The kept session must use the connection on which its stream is resumed.");
        verify(resumed.connection(), never().description("The connection must not be closed when stanzas arrive from domains that were validated on the original connection.")).close(any(StreamError.class));
    }

    /**
     * Verifies that a stanza from a domain that was not validated on the original connection, nor on the new one, is
     * still refused after resumption.
     */
    @Test
    void stanzaFromDomainThatWasNotValidatedIsRefusedAfterResumption() throws Exception
    {
        // Setup test fixture.
        keptSessionWithExtraDomains();
        final ResumedConnection resumed = resumeOnNewConnection(enabledPrevid);

        // Execute system under test.
        UnauthorizedException refusal = null;
        try {
            resumed.handler().process(message(NEW_DOMAIN), resumed.reader());
        } catch (UnauthorizedException e) {
            refusal = e;
        }

        // Verify result.
        assertNotNull(refusal, "A stanza from a domain that was never validated must be refused after resumption.");
        assertTrue(rawTextsDelivered(resumed.connection()).stream().anyMatch(text -> text.contains("invalid-from")), "A stanza from a domain that was never validated must be answered with an 'invalid-from' stream error.");
        verify(resumed.router(), never().description("A stanza from a domain that was never validated must not be routed.")).route(any(Message.class));
    }

    /**
     * Verifies that the remote server can authenticate a domain that was validated on the original connection again
     * after resumption, which Openfire accepts and confirms, and that stanzas from that domain are accepted after that
     * (decision 2 of OF-2425).
     */
    @Test
    void domainAuthenticatedAgainAfterResumptionIsAccepted() throws Exception
    {
        // Setup test fixture.
        final LocalIncomingServerSession original = keptSessionWithExtraDomains();
        final ResumedConnection resumed = resumeOnNewConnection(enabledPrevid);

        try (final MockedConstruction<ServerDialback> ignored = mockConstruction(ServerDialback.class, (mock, context) -> doReturn(Fixtures.XMPP_DOMAIN).when(mock).validateRemoteDomain(any(), any())))
        {
            // Execute system under test.
            resumed.handler().process(dialbackResult(OTHER_DOMAIN), resumed.reader());
            resumed.handler().process(message(OTHER_DOMAIN), resumed.reader());
        }

        // Verify result.
        final List<Element> results = dialbackResultsDelivered(resumed.connection());
        assertEquals(1, results.size(), "Exactly one dialback result must be reported to the remote server, but got: " + results);
        assertEquals("valid", results.get(0).attributeValue("type"), "Authenticating a domain again after resumption must be reported as valid, but got: " + results.get(0).asXML());
        assertEquals(OTHER_DOMAIN, results.get(0).attributeValue("to"), "The dialback result must be addressed to the domain that was authenticated again.");
        assertEquals(Set.of(OTHER_DOMAIN), sendersOfRoutedMessages(resumed.router()), "Stanzas from the domain that was authenticated again must be accepted.");
        assertEquals(Set.of(REMOTE_DOMAIN, OTHER_DOMAIN, EXTRA_DOMAIN), Set.copyOf(original.getValidatedDomains()), "Authenticating a domain again must not change the validated domains of the resumed session.");
        assertEquals(List.of(original), sessionManager.getIncomingServerSessions(OTHER_DOMAIN), "The domain that was authenticated again must remain registered for the resumed session only.");
    }

    /**
     * Verifies that dialback on the connection on which a stream is resumed verifies keys with the stream ID that
     * Openfire sent in the stream header on that connection, with which the remote server generated them (XEP-0185,
     * XEP-0220 § 2.1.2), and not with the stream ID of the resumed session.
     */
    @Test
    void dialbackAfterResumptionUsesStreamIdOfStreamHeaderOnNewConnection() throws Exception
    {
        // Setup test fixture.
        final LocalIncomingServerSession original = keptSessionWithExtraDomains();
        final ResumedConnection resumed = resumeOnNewConnection(enabledPrevid);
        final String streamIdInHeader = streamIdOfLastStreamHeader(resumed.connection());

        final ServerDialback dialback;
        try (final MockedConstruction<ServerDialback> constructed = mockConstruction(ServerDialback.class, (mock, context) -> doReturn(Fixtures.XMPP_DOMAIN).when(mock).validateRemoteDomain(any(), any())))
        {
            // Execute system under test.
            resumed.handler().process(dialbackResult(OTHER_DOMAIN), resumed.reader());
            dialback = constructed.constructed().get(0);
        }

        // Verify result.
        final ArgumentCaptor<StreamID> captor = ArgumentCaptor.forClass(StreamID.class);
        verify(dialback, description("The dialback key was expected to be verified; without that this test proves nothing.")).validateRemoteDomain(any(), captor.capture());
        final StreamID used = captor.getValue();
        assertNotEquals(original.getStreamID().getID(), streamIdInHeader, "Test fixture expects the stream header on the new connection to carry another stream ID than that of the resumed session.");
        assertEquals(streamIdInHeader, used.getID(), "The dialback key must be verified with the stream ID of the stream header on the connection on which it was sent.");
    }

    /**
     * Verifies that a domain that the remote server authenticates for the first time after resumption is validated for,
     * and registered with, the resumed session.
     */
    @Test
    void domainAuthenticatedForFirstTimeAfterResumptionIsRegisteredForResumedSession() throws Exception
    {
        // Setup test fixture.
        final LocalIncomingServerSession original = keptSessionWithExtraDomains();
        final ResumedConnection resumed = resumeOnNewConnection(enabledPrevid);

        try (final MockedConstruction<ServerDialback> ignored = mockConstruction(ServerDialback.class, (mock, context) -> doReturn(Fixtures.XMPP_DOMAIN).when(mock).validateRemoteDomain(any(), any())))
        {
            // Execute system under test.
            resumed.handler().process(dialbackResult(NEW_DOMAIN), resumed.reader());
            resumed.handler().process(message(NEW_DOMAIN), resumed.reader());
        }

        // Verify result.
        assertTrue(original.isValidDomain(NEW_DOMAIN), "A domain that is authenticated after resumption must be validated for the resumed session.");
        assertEquals(List.of(original), sessionManager.getIncomingServerSessions(NEW_DOMAIN), "A domain that is authenticated after resumption must be registered for the resumed session.");
        assertEquals(Set.of(NEW_DOMAIN), sendersOfRoutedMessages(resumed.router()), "Stanzas from a domain that is authenticated after resumption must be accepted.");
    }

    /**
     * Verifies that a resumed session keeps its own stream ID, by which it and all of its validated domains stay
     * registered, while the stream ID of the stream header on its new connection is that of the session that provided
     * the connection.
     */
    @Test
    void resumedSessionKeepsItsStreamIdAndRegistrations() throws Exception
    {
        // Setup test fixture.
        final LocalIncomingServerSession original = keptSessionWithExtraDomains();
        final StreamID streamIdBefore = original.getStreamID();

        // Execute system under test.
        final ResumedConnection resumed = resumeOnNewConnection(enabledPrevid);

        // Verify result.
        assertEquals(streamIdBefore, original.getStreamID(), "A resumed session must keep its own stream ID.");
        assertEquals(streamIdOfLastStreamHeader(resumed.connection()), original.getConnectionStreamID().getID(), "The stream ID of the connection of a resumed session must be the one in the stream header on that connection.");
        assertEquals(resumed.provider().getStreamID(), original.getConnectionStreamID(), "The stream ID of the stream header on the new connection must be that of the session that provided the connection.");
        assertSame(original, sessionManager.getIncomingServerSession(streamIdBefore), "The resumed session must stay registered by its own stream ID.");
        assertNull(sessionManager.getIncomingServerSession(resumed.provider().getStreamID()), "Nothing must be registered by the stream ID of the session that provided the connection.");
        for (final String domain : List.of(REMOTE_DOMAIN, OTHER_DOMAIN, EXTRA_DOMAIN)) {
            assertEquals(List.of(original), sessionManager.getIncomingServerSessions(domain), "Domain '" + domain + "' must be registered for the resumed session only.");
        }
    }

    /**
     * Verifies that the stream ID of the connection of a session that was not resumed is the stream ID of the session.
     */
    @Test
    void connectionStreamIdOfSessionThatWasNotResumedIsItsOwn()
    {
        // Setup test fixture.
        final LocalIncomingServerSession session = authenticatedSession(connection());

        // Execute system under test.
        final StreamID result = session.getConnectionStreamID();

        // Verify result.
        assertEquals(session.getStreamID(), result, "The stream ID of the connection of a session that was not resumed must be the stream ID of the session.");
    }

    /**
     * Verifies that the 'enabled' element for a resumable stream of a server tells it to resume on the host name of
     * this cluster node (XEP-0198 § 3 and § 5), which is the node that holds the session.
     */
    @Test
    void enabledElementTellsServerWhichClusterNodeToResumeOn() throws Exception
    {
        // Setup test fixture.
        final XMPPServerInfo serverInfo = server.getServerInfo();
        doReturn(NODE_HOSTNAME).when(serverInfo).getHostname();
        final Connection connection = connection();
        final LocalIncomingServerSession session = authenticatedSession(connection);

        // Execute system under test.
        enableResumption(session, connection);

        // Verify result.
        final List<Element> delivered = rawTextDelivered(connection);
        final Element enabled = delivered.get(delivered.size() - 1);
        assertEquals(NODE_HOSTNAME, enabled.attributeValue("location"), "The 'enabled' element must name the host of the cluster node that holds the session, but got: " + enabled.asXML());
    }

    /**
     * Verifies that the 'enabled' element for a resumable stream of a server has no location when the location hint is
     * disabled by configuration, as for clients.
     */
    @Test
    void enabledElementHasNoLocationWhenDisabled() throws Exception
    {
        // Setup test fixture.
        StreamManager.LOCATION_ENABLED.setValue(false);
        final Connection connection = connection();
        final LocalIncomingServerSession session = authenticatedSession(connection);

        // Execute system under test.
        enableResumption(session, connection);

        // Verify result.
        final List<Element> delivered = rawTextDelivered(connection);
        final Element enabled = delivered.get(delivered.size() - 1);
        assertNull(enabled.attributeValue("location"), "The 'enabled' element must have no location when the location hint is disabled, but got: " + enabled.asXML());
    }

    /**
     * Verifies that a resume request for a session that another cluster node holds is refused with 'item-not-found',
     * as for a session that Openfire does not recognise (XEP-0198 § 5), and that both sessions stay as they were.
     */
    @Test
    void resumeOfSessionOnOtherClusterNodeIsRefusedWithItemNotFound() throws Exception
    {
        // Setup test fixture.
        final KeptOnOtherNode kept = keptSessionOnOtherClusterNode();
        final Connection newConnection = connection();
        final LocalIncomingServerSession resuming = authenticatedSession(newConnection);

        // Execute system under test.
        resuming.getStreamManager().process(resume(kept.previd(), 0));

        // Verify result.
        assertFailed(rawTextDelivered(newConnection).get(0), "item-not-found");
        assertTrue(kept.session().isDetached(), "The session that the other cluster node holds must stay detached.");
        assertNull(kept.session().getConnection(), "The session that the other cluster node holds must not be given the new connection.");
        assertSame(newConnection, resuming.getConnection(), "The session that requested the resumption must keep its connection.");
        verify(newConnection, never().description("The connection on which the resumption was refused must stay open.")).close(any());
        assertSame(resuming, sessionManager.getIncomingServerSession(resuming.getStreamID()), "The session that requested the resumption must stay registered.");
    }

    /**
     * Verifies that a server of which a resume request for a session on another cluster node was refused can enable
     * Stream Management on the same stream, without negotiating it again, and is then told to resume on this node.
     */
    @Test
    void streamManagementCanBeEnabledAfterResumeOfSessionOnOtherClusterNodeIsRefused() throws Exception
    {
        // Setup test fixture.
        final KeptOnOtherNode kept = keptSessionOnOtherClusterNode();
        final XMPPServerInfo serverInfo = server.getServerInfo();
        doReturn(NODE_HOSTNAME).when(serverInfo).getHostname();
        final Connection newConnection = connection();
        final LocalIncomingServerSession resuming = authenticatedSession(newConnection);
        resuming.getStreamManager().process(resume(kept.previd(), 0));

        // Execute system under test.
        final String previd = enableResumption(resuming, newConnection);

        // Verify result.
        final List<Element> delivered = rawTextDelivered(newConnection);
        final Element enabled = delivered.get(delivered.size() - 1);
        assertNotEquals(kept.previd(), previd, "The stream must get an SM-ID of its own, not that of the session that the other cluster node holds.");
        assertEquals(NODE_HOSTNAME, enabled.attributeValue("location"), "The 'enabled' element must name the host of the cluster node on which the stream was enabled, but got: " + enabled.asXML());
    }

    /**
     * A detached session that another cluster node holds, and the SM-ID of its stream.
     */
    private record KeptOnOtherNode(LocalIncomingServerSession session, String previd) {}

    /**
     * Creates a detached session of which the stream can be resumed, that is held by another cluster node. That node is
     * a session manager of its own, which shares its caches with the session manager of this node, as cluster nodes do,
     * but not the sessions that it holds.
     */
    private KeptOnOtherNode keptSessionOnOtherClusterNode() throws DocumentException
    {
        final SessionManager otherNode = new SessionManager();
        otherNode.initialize(server);
        doReturn(otherNode).when(server).getSessionManager();
        try {
            final Connection formerConnection = connection();
            final LocalIncomingServerSession original = authenticatedSession(formerConnection);
            final String previd = enableResumption(original, formerConnection);
            original.setDetached();
            assertSame(original, otherNode.getIncomingServerSession(original.getStreamID()), "Test fixture expects the other cluster node to hold the session.");
            return new KeptOnOtherNode(original, previd);
        } finally {
            doReturn(sessionManager).when(server).getSessionManager();
        }
    }

    /**
     * Invokes the private close listener for incoming server sessions of the session manager for the provided session,
     * and waits for it to complete.
     */
    private void fireIncomingServerConnectionClose(final LocalIncomingServerSession session) throws Exception
    {
        final Field listenerField = SessionManager.class.getDeclaredField("incomingServerListener");
        listenerField.setAccessible(true);
        final Object listener = listenerField.get(sessionManager);

        final Method method = listener.getClass().getDeclaredMethod("onConnectionClosing", Object.class);
        method.setAccessible(true);

        final Object result = method.invoke(listener, session);
        if (result instanceof CompletableFuture) {
            ((CompletableFuture<?>) result).join();
        }
    }

    /**
     * A mocked connection that is open.
     */
    private static Connection connection()
    {
        final IdentityStore identityStore = mock(IdentityStore.class, withSettings().lenient());
        try {
            doReturn(Collections.emptyMap()).when(identityStore).getAllCertificates();
        } catch (KeyStoreException e) {
            throw new IllegalStateException(e); // Not thrown by a mock.
        }
        final ConnectionConfiguration configuration = mock(ConnectionConfiguration.class, withSettings().lenient());
        doReturn(Connection.TLSPolicy.optional).when(configuration).getTlsPolicy();
        doReturn(Connection.CompressionPolicy.disabled).when(configuration).getCompressionPolicy();
        doReturn(identityStore).when(configuration).getIdentityStore();
        final Connection connection = mock(Connection.class, withSettings().lenient());
        doReturn(configuration).when(connection).getConfiguration();
        doReturn(false).when(connection).isClosed();
        return connection;
    }

    /**
     * The SM-ID of the stream of the session that {@link #keptSessionWithExtraDomains()} created.
     */
    private String enabledPrevid;

    /**
     * Creates a kept (detached) session of which the stream can be resumed, on which the domain of the remote server
     * and two extra domains were authenticated with dialback. Its SM-ID is stored in {@link #enabledPrevid}.
     */
    private LocalIncomingServerSession keptSessionWithExtraDomains() throws DocumentException
    {
        final Connection formerConnection = connection();
        final LocalIncomingServerSession original = authenticatedSession(formerConnection);
        original.addValidatedDomain(OTHER_DOMAIN, ServerSession.AuthenticationMethod.DIALBACK);
        original.addValidatedDomain(EXTRA_DOMAIN, ServerSession.AuthenticationMethod.DIALBACK);
        enabledPrevid = enableResumption(original, formerConnection);
        original.setDetached();
        return original;
    }

    /**
     * A new connection, with the stanza handler that processes its data, the router to which that handler routes
     * stanzas, and the session that the handler created for the stream on that connection, which provided the
     * connection for the resumption.
     */
    private record ResumedConnection(Connection connection, ServerStanzaHandler handler, XMPPPacketReader reader, PacketRouter router, LocalIncomingServerSession provider) {}

    /**
     * Opens a stream on a new connection, authenticates the domain of the remote server on it with dialback, and
     * resumes the stream that the SM-ID identifies, all through the stanza handler of the connection, as a remote
     * server would.
     */
    private static ResumedConnection resumeOnNewConnection(final String previd) throws Exception
    {
        final Connection connection = connection();
        final PacketRouter router = mock(PacketRouter.class);
        final ServerStanzaHandler handler = new ServerStanzaHandler(router, connection, false);
        // As the connection does when it is moved to another session.
        doAnswer(invocation -> { handler.setSession(invocation.getArgument(0)); return null; }).when(connection).reinit(any());
        final XMPPPacketReader reader = newPacketReader();

        handler.process(STREAM_HEADER, reader);
        final ArgumentCaptor<LocalSession> created = ArgumentCaptor.forClass(LocalSession.class);
        verify(connection, description("Test fixture expects the handler to create a session for the stream.")).init(created.capture());
        final LocalIncomingServerSession provider = (LocalIncomingServerSession) created.getValue();
        provider.addValidatedDomain(REMOTE_DOMAIN, ServerSession.AuthenticationMethod.DIALBACK);
        provider.setAuthenticationMethod(ServerSession.AuthenticationMethod.DIALBACK);

        handler.process("<resume xmlns='" + StreamManager.NAMESPACE_V3 + "' previd='" + previd + "' h='0'/>", reader);
        assertTrue(rawTextDelivered(connection).stream().anyMatch(element -> element.getQName().equals(QName.get("resumed", StreamManager.NAMESPACE_V3))), "Test fixture expects the stream to be resumed on the new connection.");
        return new ResumedConnection(connection, handler, reader, router, provider);
    }

    /**
     * A stream that a remote server opened on a connection, with the stanza handler that processes the data of that
     * connection, the session that the handler created for the stream, and the SM-ID of the stream.
     */
    private record ResumableStream(Connection connection, ServerStanzaHandler handler, XMPPPacketReader reader, LocalIncomingServerSession session, String previd) {}

    /**
     * Opens a stream on a new connection, authenticates the domain of the remote server on it with dialback, and enables
     * Stream Management with resumption, all through the stanza handler of the connection, as a remote server would.
     * When closed, the connection reports that it is closed, and invokes the close listeners that were registered on it,
     * as a real connection does.
     */
    private static ResumableStream resumableStreamOnNewConnection() throws Exception
    {
        final Connection connection = connection();
        final Map<ConnectionCloseListener, Object> closeListeners = new LinkedHashMap<>();
        doAnswer(invocation -> { closeListeners.put(invocation.getArgument(0), invocation.getArgument(1)); return null; }).when(connection).registerCloseListener(any(), any());
        final AtomicBoolean closed = new AtomicBoolean(false);
        final Answer<Void> close = invocation -> {
            if (closed.compareAndSet(false, true)) {
                closeListeners.forEach((listener, handback) -> listener.onConnectionClosing(handback).join());
            }
            return null;
        };
        doAnswer(close).when(connection).close();
        doAnswer(close).when(connection).close(any());
        doAnswer(invocation -> closed.get()).when(connection).isClosed();
        final ServerStanzaHandler handler = new ServerStanzaHandler(mock(PacketRouter.class), connection, false);
        final XMPPPacketReader reader = newPacketReader();

        handler.process(STREAM_HEADER, reader);
        final ArgumentCaptor<LocalSession> created = ArgumentCaptor.forClass(LocalSession.class);
        verify(connection, description("Test fixture expects the handler to create a session for the stream.")).init(created.capture());
        final LocalIncomingServerSession session = (LocalIncomingServerSession) created.getValue();
        session.setAuthenticationMethod(ServerSession.AuthenticationMethod.DIALBACK);
        session.addValidatedDomain(REMOTE_DOMAIN);

        handler.process("<enable xmlns='" + StreamManager.NAMESPACE_V3 + "' resume='true'/>", reader);
        final ArgumentCaptor<String> delivered = ArgumentCaptor.forClass(String.class);
        verify(connection, atLeastOnce().description("Test fixture expects the handler to deliver data to the connection.")).deliverRawText(delivered.capture());
        final Element enabled = DocumentHelper.parseText(delivered.getValue()).getRootElement();
        assertEquals("true", enabled.attributeValue("resume"), "Test fixture expects a resumable stream, but got: " + enabled.asXML());
        return new ResumableStream(connection, handler, reader, session, enabled.attributeValue("id"));
    }

    /**
     * Asserts that the connection of the stream was closed, that its session was not kept but unregistered, and that a
     * request to resume its stream on another connection is refused with 'item-not-found'.
     */
    private void assertEndedWithoutBeingKept(final ResumableStream stream) throws Exception
    {
        assertTrue(stream.connection().isClosed(), "The connection of a stream that the remote server ended must be closed.");
        assertFalse(stream.session().isDetached(), "A session of which the remote server ended the stream must not be kept to be resumed.");
        assertNull(sessionManager.getIncomingServerSession(stream.session().getStreamID()), "A session of which the remote server ended the stream must be unregistered.");

        final Connection newConnection = connection();
        final LocalIncomingServerSession resuming = authenticatedSession(newConnection);
        resuming.getStreamManager().process(resume(stream.previd(), 0));
        assertFailed(rawTextDelivered(newConnection).get(0), "item-not-found");
    }

    /**
     * An incoming server session on the provided connection, on which the domain of the remote server is authenticated
     * with dialback, and which is registered with the session manager for that domain.
     */
    private static LocalIncomingServerSession authenticatedSession(final Connection connection)
    {
        final LocalIncomingServerSession session = new LocalIncomingServerSession(Fixtures.XMPP_DOMAIN, connection, new BasicStreamIDFactory().createStreamID(), REMOTE_DOMAIN);
        session.setAuthenticationMethod(ServerSession.AuthenticationMethod.DIALBACK);
        session.addValidatedDomain(REMOTE_DOMAIN);
        return session;
    }

    /**
     * An incoming server session on the provided connection, of which the stream header claimed one domain, and on
     * which a domain is authenticated with the provided method, and which is registered with the session manager for
     * that domain.
     */
    private static LocalIncomingServerSession authenticatedSession(final Connection connection, final String claimedDomain, final String authenticatedDomain, final ServerSession.AuthenticationMethod method)
    {
        final LocalIncomingServerSession session = new LocalIncomingServerSession(Fixtures.XMPP_DOMAIN, connection, new BasicStreamIDFactory().createStreamID(), claimedDomain);
        session.addValidatedDomain(authenticatedDomain, method);
        session.setAuthenticationMethod(method);
        return session;
    }

    /**
     * Enables Stream Management with resumption on a session, and returns the SM-ID from the {@code <enabled/>} element.
     */
    private static String enableResumption(final LocalIncomingServerSession session, final Connection connection) throws DocumentException
    {
        final Element enable = DocumentHelper.createElement(QName.get("enable", StreamManager.NAMESPACE_V3)).addAttribute("resume", "true");
        session.getStreamManager().process(enable);
        final List<Element> delivered = rawTextDelivered(connection);
        final Element enabled = delivered.get(delivered.size() - 1);
        assertEquals("true", enabled.attributeValue("resume"), "Test fixture expects a resumable stream, but got: " + enabled.asXML());
        return enabled.attributeValue("id");
    }

    private static Element resume(final String previd, final long h)
    {
        return DocumentHelper.createElement(QName.get("resume", StreamManager.NAMESPACE_V3)).addAttribute("previd", previd).addAttribute("h", String.valueOf(h));
    }

    /**
     * Returns the elements that were delivered to the connection as raw text, in order. Stream headers and dialback
     * elements, which are not complete XML documents on their own, are left out.
     */
    private static List<Element> rawTextDelivered(final Connection connection) throws DocumentException
    {
        final List<Element> result = new ArrayList<>();
        for (final String text : rawTextsDelivered(connection)) {
            if (text.contains("<stream:stream") || text.startsWith("<db:")) {
                continue;
            }
            result.add(DocumentHelper.parseText(text).getRootElement());
        }
        return result;
    }

    /**
     * Returns everything that was delivered to the connection as raw text, in order.
     */
    private static List<String> rawTextsDelivered(final Connection connection)
    {
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(connection, atLeastOnce().description("Expected something to be delivered to the connection.")).deliverRawText(captor.capture());
        return captor.getAllValues();
    }

    /**
     * Returns the stream ID in the last stream header that was delivered to the connection.
     */
    private static String streamIdOfLastStreamHeader(final Connection connection) throws DocumentException
    {
        final List<String> headers = rawTextsDelivered(connection).stream().filter(text -> text.contains("<stream:stream")).toList();
        assertFalse(headers.isEmpty(), "Test fixture expects a stream header to be delivered to the connection.");
        return DocumentHelper.parseText(headers.get(headers.size() - 1) + "</stream:stream>").getRootElement().attributeValue("id");
    }

    /**
     * Returns the dialback results that were delivered to the connection, in order. These are delivered without a
     * declaration of their namespace prefix, which the stream header declares.
     */
    private static List<Element> dialbackResultsDelivered(final Connection connection) throws DocumentException
    {
        final List<Element> result = new ArrayList<>();
        for (final String text : rawTextsDelivered(connection)) {
            if (text.startsWith("<db:result")) {
                final Element root = DocumentHelper.parseText("<root xmlns:db='jabber:server:dialback'>" + text + "</root>").getRootElement();
                result.add(root.elements().get(0));
            }
        }
        return result;
    }

    /**
     * Returns the domains of the senders of the messages that were routed.
     */
    private static Set<String> sendersOfRoutedMessages(final PacketRouter router)
    {
        final ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(router, atLeastOnce().description("Expected messages to be routed.")).route(captor.capture());
        return captor.getAllValues().stream().map(message -> message.getFrom().getDomain()).collect(Collectors.toSet());
    }

    private static String message(final String fromDomain)
    {
        return "<message xmlns='jabber:server' from='user@" + fromDomain + "/resource' to='user@" + Fixtures.XMPP_DOMAIN + "' id='message-from-" + fromDomain + "'><body>test</body></message>";
    }

    private static String dialbackResult(final String fromDomain)
    {
        return "<db:result xmlns:db='jabber:server:dialback' from='" + fromDomain + "' to='" + Fixtures.XMPP_DOMAIN + "'>key</db:result>";
    }

    private static XMPPPacketReader newPacketReader() throws Exception
    {
        final XmlPullParserFactory factory = XmlPullParserFactory.newInstance(MXParser.class.getName(), null);
        factory.setNamespaceAware(true);
        final XMPPPacketReader reader = new XMPPPacketReader();
        reader.setXPPFactory(factory);
        return reader;
    }

    private static void assertResumed(final Element resumed)
    {
        assertEquals(QName.get("resumed", StreamManager.NAMESPACE_V3), resumed.getQName(), "The resumption must be confirmed with a 'resumed' element, but got: " + resumed.asXML());
    }

    private static void assertFailed(final Element failed, final String condition)
    {
        assertEquals(QName.get("failed", StreamManager.NAMESPACE_V3), failed.getQName(), "The response must be a 'failed' element in the namespace of the request, but was: " + failed.asXML());
        assertNotNull(failed.element(QName.get(condition, "urn:ietf:params:xml:ns:xmpp-stanzas")), "The response must report the '" + condition + "' condition, but was: " + failed.asXML());
    }
}
