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

import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.PacketError;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests that verify {@link Resumption}, {@link ClientResumption} and {@link Resumption.Unsupported}, apart from
 * the {@link StreamManager} that uses them.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class ResumptionTest
{
    /**
     * A 'previd' that decodes to a resource and a stream ID, so that a check that refuses it cannot be confused with
     * the refusal of a 'previd' that cannot be decoded.
     */
    private static final String WELL_FORMED_PREVID = Base64.getEncoder().encodeToString("test-resource\0test-stream-id".getBytes(StandardCharsets.UTF_8));

    /**
     * Verifies that a client session gets an implementation that supports resumption.
     */
    @Test
    void clientSessionGetsClientResumption()
    {
        // Setup test fixture.
        final LocalClientSession session = mock(LocalClientSession.class);

        // Execute system under test.
        final Resumption<?> result = Resumption.forSession(session, LoggerFactory.getLogger(ResumptionTest.class));

        // Verify result.
        assertInstanceOf(ClientResumption.class, result, "A client session must get the implementation for client sessions.");
    }

    /**
     * Verifies that a session that is not a client session gets the implementation that does not support resumption.
     */
    @Test
    void nonClientSessionGetsUnsupported()
    {
        // Setup test fixture.
        final LocalSession session = mock(LocalSession.class);

        // Execute system under test.
        final Resumption<?> result = Resumption.forSession(session, LoggerFactory.getLogger(ResumptionTest.class));

        // Verify result.
        assertSame(Resumption.Unsupported.INSTANCE, result, "A session that is not a client session must get the implementation that does not support resumption.");
    }

    /**
     * Verifies that a client session that authenticated as a user may resume.
     */
    @Test
    void authenticatedClientSessionIsAllowed()
    {
        // Setup test fixture.
        final LocalClientSession session = mock(LocalClientSession.class);
        doReturn(mock(AuthToken.class)).when(session).getAuthToken();

        // Execute system under test.
        final boolean result = new ClientResumption(session).isAllowed();

        // Verify result.
        assertTrue(result, "A client session that authenticated as a user must be allowed to resume.");
    }

    /**
     * Verifies that a client session that did not authenticate may not resume.
     */
    @Test
    void clientSessionWithoutAuthTokenIsNotAllowed()
    {
        // Setup test fixture.
        final LocalClientSession session = mock(LocalClientSession.class);
        doReturn(null).when(session).getAuthToken();

        // Execute system under test.
        final boolean result = new ClientResumption(session).isAllowed();

        // Verify result.
        assertFalse(result, "A client session that did not authenticate must not be allowed to resume.");
    }

    /**
     * Verifies that an anonymous client session may not resume.
     */
    @Test
    void anonymousClientSessionIsNotAllowed()
    {
        // Setup test fixture.
        final LocalClientSession session = mock(LocalClientSession.class);
        final AuthToken authToken = mock(AuthToken.class);
        doReturn(true).when(authToken).isAnonymous();
        doReturn(authToken).when(session).getAuthToken();

        // Execute system under test.
        final boolean result = new ClientResumption(session).isAllowed();

        // Verify result.
        assertFalse(result, "An anonymous client session must not be allowed to resume.");
    }

    /**
     * Verifies that no session is found for a resume request that is received on a client session that did not
     * authenticate, as the session to resume must belong to the same user.
     */
    @Test
    void locateFromClientSessionWithoutAuthTokenFails()
    {
        // Setup test fixture.
        final LocalClientSession session = mock(LocalClientSession.class);
        doReturn(null).when(session).getAuthToken();

        // Execute system under test.
        final ResumeRequestValidationResult<LocalClientSession> result = new ClientResumption(session).locate(WELL_FORMED_PREVID);

        // Verify result.
        assertFalse(result.isSuccess(), "No session must be found from a client session that did not authenticate.");
        assertEquals(PacketError.Condition.unexpected_request, result.getFailureCondition(), "The condition for a client session that did not authenticate is expected to be 'unexpected-request'.");
    }

    /**
     * Verifies that no session is found for a resume request that is received on an anonymous client session.
     */
    @Test
    void locateFromAnonymousClientSessionFails()
    {
        // Setup test fixture.
        final LocalClientSession session = mock(LocalClientSession.class);
        final AuthToken authToken = mock(AuthToken.class);
        doReturn(true).when(authToken).isAnonymous();
        doReturn(authToken).when(session).getAuthToken();

        // Execute system under test.
        final ResumeRequestValidationResult<LocalClientSession> result = new ClientResumption(session).locate(WELL_FORMED_PREVID);

        // Verify result.
        assertFalse(result.isSuccess(), "No session must be found from an anonymous client session.");
        assertEquals(PacketError.Condition.unexpected_request, result.getFailureCondition(), "The condition for an anonymous client session is expected to be 'unexpected-request'.");
    }

    /**
     * Verifies that a session that does not support resumption may not resume.
     */
    @Test
    void unsupportedIsNotAllowed()
    {
        // Setup test fixture.
        final Resumption<LocalSession> resumption = Resumption.Unsupported.INSTANCE;

        // Execute system under test.
        final boolean result = resumption.isAllowed();

        // Verify result.
        assertFalse(result, "A session that does not support resumption must not be allowed to resume.");
    }

    /**
     * Verifies that no SM-ID is created for a session that does not support resumption.
     */
    @Test
    void unsupportedCreatesNoId()
    {
        // Setup test fixture.
        final Resumption<LocalSession> resumption = Resumption.Unsupported.INSTANCE;

        // Execute system under test & verify result.
        assertThrows(UnsupportedOperationException.class, resumption::createId, "No SM-ID must be created for a session that does not support resumption.");
    }

    /**
     * Verifies that no session is found for a resume request that is received on a session that does not support
     * resumption.
     */
    @Test
    void unsupportedLocatesNothing()
    {
        // Setup test fixture.
        final Resumption<LocalSession> resumption = Resumption.Unsupported.INSTANCE;

        // Execute system under test.
        final ResumeRequestValidationResult<LocalSession> result = resumption.locate("any");

        // Verify result.
        assertFalse(result.isSuccess(), "No session must be found from a session that does not support resumption.");
        assertEquals(PacketError.Condition.unexpected_request, result.getFailureCondition(), "The condition for a session that does not support resumption is expected to be 'unexpected-request'.");
    }
}
