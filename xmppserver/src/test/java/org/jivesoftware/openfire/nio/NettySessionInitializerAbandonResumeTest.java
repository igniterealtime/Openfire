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
package org.jivesoftware.openfire.nio;

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.net.RespondingServerStanzaHandler;
import org.jivesoftware.openfire.session.DomainPair;
import org.jivesoftware.openfire.session.LocalOutgoingServerSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link NettySessionInitializer#abandonResume()}, which gives up on resuming the stream of a detached
 * session through the stanza handler of the connection that was opened most recently (OF-2425).
 */
public class NettySessionInitializerAbandonResumeTest
{
    private static final DomainPair DOMAIN_PAIR = new DomainPair(Fixtures.XMPP_DOMAIN, "remote.example.org");

    private NettySessionInitializer initialiser;

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
        NettySessionInitializer.initializeSharedResources();
    }

    @AfterAll
    public static void tearDownClass()
    {
        NettySessionInitializer.destroySharedResources();
    }

    @BeforeEach
    public void setUp()
    {
        initialiser = new NettySessionInitializer(DOMAIN_PAIR, 5269, mock(LocalOutgoingServerSession.class));
    }

    /**
     * Verifies that, before a connection is opened, there is nothing to give up, so that the caller handles the detached
     * session itself.
     */
    @Test
    public void withoutConnectionReportsAbandoned()
    {
        // Setup test fixture.
        initialiser.connectionHandler = null;

        // Execute system under test.
        final boolean result = initialiser.abandonResume();

        // Verify result.
        assertTrue(result, "Without a connection, no request to resume can have been answered, so giving up must be reported as done.");
    }

    /**
     * Verifies that, while the connection has no stanza handler yet, there is nothing to give up, as no request to
     * resume can have been sent.
     */
    @Test
    public void withoutStanzaHandlerYetReportsAbandoned()
    {
        // Setup test fixture.
        final NettyOutboundConnectionHandler connectionHandler = mock(NettyOutboundConnectionHandler.class);
        doReturn(new CompletableFuture<RespondingServerStanzaHandler>()).when(connectionHandler).getStanzaHandlerFuture();
        initialiser.connectionHandler = connectionHandler;

        // Execute system under test.
        final boolean result = initialiser.abandonResume();

        // Verify result.
        assertTrue(result, "Without a stanza handler, no request to resume can have been answered, so giving up must be reported as done.");
    }

    /**
     * Verifies that, when the connection failed before it got a stanza handler, there is nothing to give up.
     */
    @Test
    public void withFailedStanzaHandlerReportsAbandoned()
    {
        // Setup test fixture.
        final NettyOutboundConnectionHandler connectionHandler = mock(NettyOutboundConnectionHandler.class);
        doReturn(CompletableFuture.failedFuture(new IllegalStateException("The connection closed."))).when(connectionHandler).getStanzaHandlerFuture();
        initialiser.connectionHandler = connectionHandler;

        // Execute system under test.
        final boolean result = initialiser.abandonResume();

        // Verify result.
        assertTrue(result, "When the connection got no stanza handler, no request to resume can have been answered, so giving up must be reported as done.");
    }

    /**
     * Verifies that the stanza handler of the connection is asked to give up on resuming, and that its answer that it
     * did so is returned.
     */
    @Test
    public void stanzaHandlerThatAbandonsReportsAbandoned()
    {
        // Setup test fixture.
        final RespondingServerStanzaHandler stanzaHandler = mock(RespondingServerStanzaHandler.class);
        doReturn(true).when(stanzaHandler).abandonResume();
        final NettyOutboundConnectionHandler connectionHandler = mock(NettyOutboundConnectionHandler.class);
        doReturn(CompletableFuture.completedFuture(stanzaHandler)).when(connectionHandler).getStanzaHandlerFuture();
        initialiser.connectionHandler = connectionHandler;

        // Execute system under test.
        final boolean result = initialiser.abandonResume();

        // Verify result.
        assertTrue(result, "The answer of the stanza handler that it gave up on resuming must be returned.");
        verify(stanzaHandler, times(1).description("The stanza handler of the connection must be asked to give up on resuming.")).abandonResume();
    }

    /**
     * Verifies that, when the stanza handler of the connection reports that the remote server answered the request to
     * resume already, that is returned, so that the processing of that answer takes care of the detached session.
     */
    @Test
    public void stanzaHandlerThatWasAnsweredReportsAnswered()
    {
        // Setup test fixture.
        final RespondingServerStanzaHandler stanzaHandler = mock(RespondingServerStanzaHandler.class);
        doReturn(false).when(stanzaHandler).abandonResume();
        final NettyOutboundConnectionHandler connectionHandler = mock(NettyOutboundConnectionHandler.class);
        doReturn(CompletableFuture.completedFuture(stanzaHandler)).when(connectionHandler).getStanzaHandlerFuture();
        initialiser.connectionHandler = connectionHandler;

        // Execute system under test.
        final boolean result = initialiser.abandonResume();

        // Verify result.
        assertFalse(result, "The answer of the stanza handler that the request to resume was answered already must be returned.");
        verify(stanzaHandler, times(1).description("The stanza handler of the connection must be asked to give up on resuming.")).abandonResume();
    }
}
