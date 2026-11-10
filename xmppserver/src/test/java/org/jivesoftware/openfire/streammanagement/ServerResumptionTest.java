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
package org.jivesoftware.openfire.streammanagement;

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.session.LocalIncomingServerSession;
import org.jivesoftware.openfire.session.ServerSession.AuthenticationMethod;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xmpp.packet.PacketError;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests that verify {@link ServerResumption}, apart from the {@link StreamManager} that uses it.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class ServerResumptionTest
{
    private static final String STREAM_ID = "test-stream-id";
    private static final String OTHER_STREAM_ID = "other-stream-id";

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
        final XMPPServer server = Fixtures.mockXMPPServer();
        sessionManager = server.getSessionManager();

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

    /**
     * Verifies that a server of which a domain is authenticated may resume, when Stream Management and resumption are
     * enabled for servers.
     */
    @Test
    void authenticatedServerSessionIsAllowed()
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(true);
        StreamManager.SERVER_RESUME_ENABLED.setValue(true);
        final LocalIncomingServerSession session = serverSession(STREAM_ID, List.of("remote.example.org"));

        // Execute system under test.
        final boolean result = new ServerResumption(session).isAllowed();

        // Verify result.
        assertTrue(result, "A server of which a domain is authenticated must be allowed to resume.");
    }

    /**
     * Verifies that a server of which no domain is authenticated may not resume.
     */
    @Test
    void unauthenticatedServerSessionIsNotAllowed()
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(true);
        StreamManager.SERVER_RESUME_ENABLED.setValue(true);
        final LocalIncomingServerSession session = serverSession(STREAM_ID, List.of());

        // Execute system under test.
        final boolean result = new ServerResumption(session).isAllowed();

        // Verify result.
        assertFalse(result, "A server of which no domain is authenticated must not be allowed to resume.");
    }

    /**
     * Verifies that a server may not resume while resumption is disabled for servers, which is the default.
     */
    @Test
    void serverSessionIsNotAllowedWhenServerResumptionIsDisabled()
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(true);
        final LocalIncomingServerSession session = serverSession(STREAM_ID, List.of("remote.example.org"));

        // Execute system under test.
        final boolean result = new ServerResumption(session).isAllowed();

        // Verify result.
        assertFalse(result, "A server must not be allowed to resume while resumption is disabled for servers.");
    }

    /**
     * Verifies that a server may not resume while Stream Management is not used with servers, even when resumption is
     * enabled for them.
     */
    @Test
    void serverSessionIsNotAllowedWhenStreamManagementIsNotActiveForServers()
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(false);
        StreamManager.SERVER_RESUME_ENABLED.setValue(true);
        final LocalIncomingServerSession session = serverSession(STREAM_ID, List.of("remote.example.org"));

        // Execute system under test.
        final boolean result = new ServerResumption(session).isAllowed();

        // Verify result.
        assertFalse(result, "A server must not be allowed to resume while Stream Management is not used with servers.");
    }

    /**
     * Verifies that a server session can resume after it authenticated, as servers resume after authentication.
     */
    @Test
    void authenticatedServerSessionIsNotTooLateToResume()
    {
        // Setup test fixture.
        final LocalIncomingServerSession session = serverSession(STREAM_ID, List.of("remote.example.org"));
        doReturn(true).when(session).isAuthenticated();

        // Execute system under test.
        final boolean result = new ServerResumption(session).isTooLateToResume();

        // Verify result.
        assertFalse(result, "A server session that authenticated must still be able to resume.");
    }

    /**
     * Verifies that the SM-ID of a server session identifies it by its stream ID only, without a resource or domain.
     */
    @Test
    void idIsBasedOnStreamId()
    {
        // Setup test fixture.
        final LocalIncomingServerSession session = serverSession(STREAM_ID, List.of("remote.example.org"));

        // Execute system under test.
        final String result = new ServerResumption(session).createId();

        // Verify result.
        assertEquals(STREAM_ID, new String(Base64.getDecoder().decode(result), StandardCharsets.UTF_8), "The SM-ID must be the Base64 encoding of the stream ID of the session.");
    }

    /**
     * Verifies that the SM-ID of a server session finds that session, when it is used in a resume request on another
     * server session.
     */
    @Test
    void locateFindsSessionOfId()
    {
        // Setup test fixture.
        final LocalIncomingServerSession original = serverSession(STREAM_ID, List.of("remote.example.org"));
        final LocalIncomingServerSession resuming = serverSession(OTHER_STREAM_ID, List.of("remote.example.org"));
        final String previd = new ServerResumption(original).createId();

        // Execute system under test.
        final ResumeRequestValidationResult<LocalIncomingServerSession> result = new ServerResumption(resuming).locate(previd);

        // Verify result.
        assertTrue(result.isSuccess(), "The session that the SM-ID identifies must be found.");
        assertSame(original, result.getTarget(), "The session that is found must be the one that the SM-ID identifies.");
    }

    /**
     * Verifies that a previd that does not identify a session is refused with 'item-not-found', as XEP-0198 § 5
     * recommends for a previd that the server does not recognise.
     */
    @Test
    void locateOfUnknownSessionFailsWithItemNotFound()
    {
        // Setup test fixture.
        final LocalIncomingServerSession resuming = serverSession(OTHER_STREAM_ID, List.of("remote.example.org"));
        final String previd = Base64.getEncoder().encodeToString("unknown-stream-id".getBytes(StandardCharsets.UTF_8));

        // Execute system under test.
        final ResumeRequestValidationResult<LocalIncomingServerSession> result = new ServerResumption(resuming).locate(previd);

        // Verify result.
        assertFalse(result.isSuccess(), "No session must be found for a previd that does not identify one.");
        assertEquals(PacketError.Condition.item_not_found, result.getFailureCondition(), "The condition for a previd that does not identify a session must be 'item-not-found'.");
    }

    /**
     * Verifies that a previd that cannot be decoded is refused with 'item-not-found', as for any other previd that is
     * not recognised.
     */
    @Test
    void locateOfMalformedPrevidFailsWithItemNotFound()
    {
        // Setup test fixture.
        final LocalIncomingServerSession resuming = serverSession(OTHER_STREAM_ID, List.of("remote.example.org"));

        // Execute system under test.
        final ResumeRequestValidationResult<LocalIncomingServerSession> result = new ServerResumption(resuming).locate("not Base64!");

        // Verify result.
        assertFalse(result.isSuccess(), "No session must be found for a previd that cannot be decoded.");
        assertEquals(PacketError.Condition.item_not_found, result.getFailureCondition(), "The condition for a previd that cannot be decoded must be 'item-not-found'.");
    }

    /**
     * Verifies that a session cannot resume itself, which would move its connection to itself.
     */
    @Test
    void locateOfOwnSessionFailsWithItemNotFound()
    {
        // Setup test fixture.
        final LocalIncomingServerSession resuming = serverSession(OTHER_STREAM_ID, List.of("remote.example.org"));
        final String previd = new ServerResumption(resuming).createId();

        // Execute system under test.
        final ResumeRequestValidationResult<LocalIncomingServerSession> result = new ServerResumption(resuming).locate(previd);

        // Verify result.
        assertFalse(result.isSuccess(), "A session must not be found for a previd that identifies the session on which the request is received.");
        assertEquals(PacketError.Condition.item_not_found, result.getFailureCondition(), "The condition for a previd that identifies the session itself must be 'item-not-found'.");
    }

    /**
     * Verifies that a session cannot resume a session of which another domain was authenticated first, and that this is
     * reported as 'item-not-found', as for a previd that identifies no session, so that the response does not reveal
     * that the session exists.
     */
    @Test
    void locateOfSessionOfOtherDomainFailsWithItemNotFound()
    {
        // Setup test fixture.
        final LocalIncomingServerSession original = serverSession(STREAM_ID, "remote.example.org", AuthenticationMethod.DIALBACK);
        final LocalIncomingServerSession resuming = serverSession(OTHER_STREAM_ID, "other.example.org", AuthenticationMethod.DIALBACK);
        final String previd = new ServerResumption(original).createId();

        // Execute system under test.
        final ResumeRequestValidationResult<LocalIncomingServerSession> result = new ServerResumption(resuming).locate(previd);

        // Verify result.
        assertFalse(result.isSuccess(), "A session of which another domain was authenticated first must not be found.");
        assertEquals(PacketError.Condition.item_not_found, result.getFailureCondition(), "The condition for a session that may not be resumed must be 'item-not-found', as for a previd that identifies no session.");
    }

    /**
     * Verifies that a session cannot resume a session that was authenticated with a stronger method.
     */
    @Test
    void locateOfSessionWithStrongerMethodFailsWithItemNotFound()
    {
        // Setup test fixture.
        final LocalIncomingServerSession original = serverSession(STREAM_ID, "remote.example.org", AuthenticationMethod.SASL_EXTERNAL);
        final LocalIncomingServerSession resuming = serverSession(OTHER_STREAM_ID, "remote.example.org", AuthenticationMethod.DIALBACK);
        final String previd = new ServerResumption(original).createId();

        // Execute system under test.
        final ResumeRequestValidationResult<LocalIncomingServerSession> result = new ServerResumption(resuming).locate(previd);

        // Verify result.
        assertFalse(result.isSuccess(), "A session that was authenticated with a certificate must not be found for a session that used dialback.");
        assertEquals(PacketError.Condition.item_not_found, result.getFailureCondition(), "The condition for a session that may not be resumed must be 'item-not-found'.");
    }

    /**
     * Verifies that a session can resume a session of the same domain that was authenticated with a weaker method.
     */
    @Test
    void locateOfSessionWithWeakerMethodSucceeds()
    {
        // Setup test fixture.
        final LocalIncomingServerSession original = serverSession(STREAM_ID, "remote.example.org", AuthenticationMethod.DIALBACK);
        final LocalIncomingServerSession resuming = serverSession(OTHER_STREAM_ID, "remote.example.org", AuthenticationMethod.SASL_EXTERNAL);
        final String previd = new ServerResumption(original).createId();

        // Execute system under test.
        final ResumeRequestValidationResult<LocalIncomingServerSession> result = new ServerResumption(resuming).locate(previd);

        // Verify result.
        assertTrue(result.isSuccess(), "A session that used dialback must be found for a session of the same domain that was authenticated with a certificate.");
        assertSame(original, result.getTarget(), "The session that is found must be the one that the SM-ID identifies.");
    }

    /**
     * Verifies that the first authenticated domains are compared regardless of case.
     */
    @Test
    void locateComparesDomainsRegardlessOfCase()
    {
        // Setup test fixture.
        final LocalIncomingServerSession original = serverSession(STREAM_ID, "remote.example.org", AuthenticationMethod.DIALBACK);
        final LocalIncomingServerSession resuming = serverSession(OTHER_STREAM_ID, "Remote.Example.ORG", AuthenticationMethod.DIALBACK);
        final String previd = new ServerResumption(original).createId();

        // Execute system under test.
        final ResumeRequestValidationResult<LocalIncomingServerSession> result = new ServerResumption(resuming).locate(previd);

        // Verify result.
        assertTrue(result.isSuccess(), "A session must be found for a session that authenticated the same domain in other case.");
    }

    /**
     * Verifies that a session for which the first domain was authenticated with an unknown method cannot resume, nor be
     * resumed.
     */
    @Test
    void locateFailsWhenMethodIsUnknown()
    {
        // Setup test fixture.
        final LocalIncomingServerSession unknownOriginal = serverSession(STREAM_ID, "remote.example.org", null);
        final LocalIncomingServerSession unknownResuming = serverSession(OTHER_STREAM_ID, "remote.example.org", null);
        final LocalIncomingServerSession known = serverSession("third-stream-id", "remote.example.org", AuthenticationMethod.SASL_EXTERNAL);

        // Execute system under test.
        final ResumeRequestValidationResult<LocalIncomingServerSession> resumedByKnown = new ServerResumption(known).locate(new ServerResumption(unknownOriginal).createId());
        final ResumeRequestValidationResult<LocalIncomingServerSession> resumedByUnknown = new ServerResumption(unknownResuming).locate(new ServerResumption(known).createId());

        // Verify result.
        assertFalse(resumedByKnown.isSuccess(), "A session for which the authentication method of the first domain is unknown must not be found.");
        assertFalse(resumedByUnknown.isSuccess(), "A session for which the authentication method of the first domain is unknown must not resume another session.");
    }

    /**
     * Verifies that a session on which no domain is recorded as authenticated cannot resume.
     */
    @Test
    void locateFailsWhenNoDomainIsRecorded()
    {
        // Setup test fixture.
        final LocalIncomingServerSession original = serverSession(STREAM_ID, "remote.example.org", AuthenticationMethod.DIALBACK);
        final LocalIncomingServerSession resuming = serverSession(OTHER_STREAM_ID, List.of("remote.example.org"));
        doReturn(null).when(resuming).getFirstAuthentication();
        final String previd = new ServerResumption(original).createId();

        // Execute system under test.
        final ResumeRequestValidationResult<LocalIncomingServerSession> result = new ServerResumption(resuming).locate(previd);

        // Verify result.
        assertFalse(result.isSuccess(), "A session on which no domain is recorded as authenticated must not resume another session.");
        assertEquals(PacketError.Condition.item_not_found, result.getFailureCondition(), "The condition for a session that may not be resumed must be 'item-not-found'.");
    }

    /**
     * Verifies which authentication methods are the same as, or stronger than, others: a certificate is stronger than
     * dialback, each method is the same as itself, and nothing else holds.
     */
    @Test
    void sameOrStrongerAuthenticationMethods()
    {
        // Setup test fixture.
        final AuthenticationMethod[] methods = { AuthenticationMethod.DIALBACK, AuthenticationMethod.SASL_EXTERNAL, AuthenticationMethod.OTHER, null };

        // Execute system under test.
        final List<String> accepted = new ArrayList<>();
        for (final AuthenticationMethod method : methods) {
            for (final AuthenticationMethod otherMethod : methods) {
                if (ServerResumption.isSameOrStronger(method, otherMethod)) {
                    accepted.add(method + ">=" + otherMethod);
                }
            }
        }

        // Verify result.
        assertEquals(List.of("DIALBACK>=DIALBACK", "SASL_EXTERNAL>=DIALBACK", "SASL_EXTERNAL>=SASL_EXTERNAL", "OTHER>=OTHER"), accepted, "Only the same method, and a certificate over dialback, must be the same or stronger.");
    }

    /**
     * Verifies that a detached session is taken from the detached sessions for its resumption, when it is still there.
     */
    @Test
    void claimTakesDetachedSession()
    {
        // Setup test fixture.
        final LocalIncomingServerSession resuming = serverSession(OTHER_STREAM_ID, List.of("remote.example.org"));
        final LocalIncomingServerSession target = serverSession(STREAM_ID, List.of("remote.example.org"));
        doReturn(true).when(sessionManager).removeDetached(target);

        // Execute system under test.
        final boolean result = new ServerResumption(resuming).claim(target);

        // Verify result.
        assertTrue(result, "A session that is still detached must be taken for its resumption.");
        verify(sessionManager, times(1).description("The session must be removed from the detached sessions.")).removeDetached(target);
    }

    /**
     * Verifies that a session that is no longer detached, because it was ended or resumed on another connection, is
     * not taken for a resumption.
     */
    @Test
    void claimFailsForSessionThatIsNoLongerDetached()
    {
        // Setup test fixture.
        final LocalIncomingServerSession resuming = serverSession(OTHER_STREAM_ID, List.of("remote.example.org"));
        final LocalIncomingServerSession target = serverSession(STREAM_ID, List.of("remote.example.org"));
        doReturn(false).when(sessionManager).removeDetached(target);

        // Execute system under test.
        final boolean result = new ServerResumption(resuming).claim(target);

        // Verify result.
        assertFalse(result, "A session that is no longer detached must not be taken for a resumption.");
    }

    /**
     * A mock of an incoming server session with a stream ID and validated domains, that the session manager finds by
     * its stream ID. The first of the validated domains, if any, is recorded as the first one authenticated, with
     * dialback.
     */
    private LocalIncomingServerSession serverSession(final String streamId, final List<String> validatedDomains)
    {
        final LocalIncomingServerSession session = mock(LocalIncomingServerSession.class, withSettings().lenient());
        doReturn(BasicStreamIDFactory.createStreamID(streamId)).when(session).getStreamID();
        doReturn(validatedDomains).when(session).getValidatedDomains();
        final LocalIncomingServerSession.DomainAuthentication first = validatedDomains.isEmpty() ? null : new LocalIncomingServerSession.DomainAuthentication(validatedDomains.get(0), AuthenticationMethod.DIALBACK);
        doReturn(first).when(session).getFirstAuthentication();
        doReturn(session).when(sessionManager).getIncomingServerSession(BasicStreamIDFactory.createStreamID(streamId));
        return session;
    }

    /**
     * A mock of an incoming server session with a stream ID, that the session manager finds by its stream ID, of which
     * one domain is validated, and recorded as the first one authenticated, with the provided method.
     */
    private LocalIncomingServerSession serverSession(final String streamId, final String domain, final AuthenticationMethod method)
    {
        final LocalIncomingServerSession session = serverSession(streamId, List.of(domain));
        doReturn(new LocalIncomingServerSession.DomainAuthentication(domain, method)).when(session).getFirstAuthentication();
        return session;
    }
}
