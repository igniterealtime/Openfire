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

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.timeout.IdleStateEvent;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.LocalIncomingServerSession;
import org.jivesoftware.openfire.session.LocalOutgoingServerSession;
import org.jivesoftware.openfire.streammanagement.AnsweringStreamManager;
import org.jivesoftware.openfire.streammanagement.RequestingStreamManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xmpp.packet.StreamError;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link NettyIdleStateKeepAliveHandler} on connections between Openfire and other servers.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class NettyIdleStateKeepAliveHandlerTest
{
    private EmbeddedChannel channel;

    private NettyConnection connection;

    private RequestingStreamManager streamManager;

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
        streamManager = mock(RequestingStreamManager.class);
        final LocalOutgoingServerSession session = mock(LocalOutgoingServerSession.class);
        doReturn(streamManager).when(session).getStreamManager();
        connection = mock(NettyConnection.class);
        doReturn(session).when(connection).getSession();

        channel = new EmbeddedChannel(new NettyIdleStateKeepAliveHandler(false));
        channel.attr(NettyConnectionHandler.CONNECTION).set(connection);
    }

    @AfterEach
    public void tearDown()
    {
        channel.finishAndReleaseAll();
        Fixtures.clearExistingProperties();
    }

    /**
     * Verifies that the first time an outgoing server connection has gone quiet, the remote server is asked to
     * acknowledge the stanzas that it received, without closing the connection, so that the stanzas that it received
     * are not sent again or returned to their senders when the connection is closed for being idle.
     */
    @Test
    public void acknowledgementIsRequestedWhenOutgoingServerConnectionGoesQuiet()
    {
        // Execute system under test.
        channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);

        // Verify result.
        verify(streamManager, times(1).description("An acknowledgement must be requested when the connection has gone quiet.")).requestAcknowledgementOfUnacknowledgedStanzas();
        verify(connection, never().description("The connection must not be closed when it has gone quiet the first time.")).close(any(StreamError.class));
    }

    /**
     * Verifies that no acknowledgement is requested when an outgoing server connection that is closed already goes
     * quiet, as the session would write that request to another connection.
     */
    @Test
    public void noRequestWhenOutgoingServerConnectionThatIsClosedAlreadyGoesQuiet()
    {
        // Setup test fixture.
        doReturn(true).when(connection).isClosed();

        // Execute system under test.
        channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);

        // Verify result.
        verify(streamManager, never().description("No acknowledgement must be requested for a connection that is closed already.")).requestAcknowledgementOfUnacknowledgedStanzas();
    }

    /**
     * Verifies that an outgoing server connection that stays quiet, and on which no stanzas are unacknowledged, is
     * closed, as before.
     */
    @Test
    public void outgoingServerConnectionThatStaysQuietIsClosed()
    {
        // Setup test fixture.
        channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);

        // Execute system under test.
        channel.pipeline().fireUserEventTriggered(IdleStateEvent.READER_IDLE_STATE_EVENT);

        // Verify result.
        verify(connection, times(1).description("A connection that stays quiet must be closed.")).close(argThat((StreamError error) -> error.getCondition() == StreamError.Condition.connection_timeout));
    }

    /**
     * Verifies that when an outgoing server connection would be closed for being idle while stanzas on it are
     * unacknowledged, such as stanzas that were sent after it went quiet, an acknowledgement is requested and the close
     * is left to the next event, and that the connection is closed then when no answer arrived, so that a remote server
     * that does not answer cannot keep it open.
     */
    @Test
    public void closeIsDeferredOnceWhileStanzasAreUnacknowledged()
    {
        // Setup test fixture.
        doReturn(true).when(streamManager).requestAcknowledgementOfUnacknowledgedStanzas();
        channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);
        clearInvocations(streamManager);

        // Execute system under test.
        channel.pipeline().fireUserEventTriggered(IdleStateEvent.READER_IDLE_STATE_EVENT);

        // Verify result.
        verify(streamManager, times(1).description("An acknowledgement must be requested when the connection would be closed while stanzas are unacknowledged.")).requestAcknowledgementOfUnacknowledgedStanzas();
        verify(connection, never().description("The connection must not be closed before the remote server could answer.")).close(any(StreamError.class));

        // Execute system under test.
        clearInvocations(streamManager);
        channel.pipeline().fireUserEventTriggered(IdleStateEvent.READER_IDLE_STATE_EVENT);

        // Verify result.
        verify(connection, times(1).description("The connection must be closed when no answer arrived.")).close(argThat((StreamError error) -> error.getCondition() == StreamError.Condition.connection_timeout));
        verify(streamManager, never().description("No further acknowledgement must be requested once the close was left to the next event.")).requestAcknowledgementOfUnacknowledgedStanzas();
    }

    /**
     * Verifies that before a connection that another server opened to Openfire is closed for being idle, Openfire tells
     * that server which stanzas it handled, with an acknowledgement that was not requested, so that the server does not
     * send those stanzas again after the close, and that this is not done when the connection merely went quiet.
     */
    @Test
    public void handledStanzasAreAcknowledgedBeforeIdleIncomingServerConnectionIsClosed()
    {
        // Setup test fixture.
        final AnsweringStreamManager answeringStreamManager = mock(AnsweringStreamManager.class);
        final LocalIncomingServerSession incomingSession = mock(LocalIncomingServerSession.class);
        doReturn(answeringStreamManager).when(incomingSession).getStreamManager();
        doReturn(incomingSession).when(connection).getSession();
        final List<String> calls = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> calls.add("acknowledge")).when(answeringStreamManager).acknowledgeInboundStanzas();
        doAnswer(invocation -> calls.add("close")).when(connection).close(any(StreamError.class));

        // Execute system under test.
        channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);

        // Verify result.
        verify(answeringStreamManager, never().description("Nothing must be acknowledged when the connection merely went quiet.")).acknowledgeInboundStanzas();

        // Execute system under test.
        channel.pipeline().fireUserEventTriggered(IdleStateEvent.READER_IDLE_STATE_EVENT);

        // Verify result.
        verify(connection, times(1).description("The connection must be closed for being idle.")).close(argThat((StreamError error) -> error.getCondition() == StreamError.Condition.connection_timeout));
        assertEquals(List.of("acknowledge", "close"), calls, "The handled stanzas must be acknowledged once, before the connection is closed.");
    }

    /**
     * Verifies that a client connection that is closed for being idle is not sent an acknowledgement that it did not
     * request, which is sent only to servers.
     */
    @Test
    public void noUnrequestedAcknowledgementBeforeIdleClientConnectionIsClosed()
    {
        // Setup test fixture.
        final AnsweringStreamManager answeringStreamManager = mock(AnsweringStreamManager.class);
        final LocalClientSession clientSession = mock(LocalClientSession.class);
        doReturn(answeringStreamManager).when(clientSession).getStreamManager();
        doReturn(clientSession).when(connection).getSession();
        channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);

        // Execute system under test.
        channel.pipeline().fireUserEventTriggered(IdleStateEvent.READER_IDLE_STATE_EVENT);

        // Verify result.
        verify(connection, times(1).description("The connection must be closed for being idle.")).close(argThat((StreamError error) -> error.getCondition() == StreamError.Condition.connection_timeout));
        verify(answeringStreamManager, never().description("A client must not be sent an acknowledgement that it did not request.")).acknowledgeInboundStanzas();
    }

    /**
     * Verifies that nothing is acknowledged before an incoming server connection that is closed already is closed for
     * being idle, as the session would write that acknowledgement to the connection that it was resumed on.
     */
    @Test
    public void noAcknowledgementForIncomingServerConnectionThatIsClosedAlready()
    {
        // Setup test fixture.
        final AnsweringStreamManager answeringStreamManager = mock(AnsweringStreamManager.class);
        final LocalIncomingServerSession incomingSession = mock(LocalIncomingServerSession.class);
        doReturn(answeringStreamManager).when(incomingSession).getStreamManager();
        doReturn(incomingSession).when(connection).getSession();
        channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);
        doReturn(true).when(connection).isClosed();

        // Execute system under test.
        channel.pipeline().fireUserEventTriggered(IdleStateEvent.READER_IDLE_STATE_EVENT);

        // Verify result.
        verify(answeringStreamManager, never().description("Nothing must be acknowledged for a connection that is closed already.")).acknowledgeInboundStanzas();
    }

    /**
     * Verifies that no acknowledgement is requested, and the close is not left to the next event, when an outgoing
     * server connection that is closed already would be closed for being idle, as the session would write that request
     * to another connection.
     */
    @Test
    public void noRequestForOutgoingServerConnectionThatIsClosedAlready()
    {
        // Setup test fixture.
        doReturn(true).when(streamManager).requestAcknowledgementOfUnacknowledgedStanzas();
        channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);
        clearInvocations(streamManager);
        doReturn(true).when(connection).isClosed();

        // Execute system under test.
        channel.pipeline().fireUserEventTriggered(IdleStateEvent.READER_IDLE_STATE_EVENT);

        // Verify result.
        verify(streamManager, never().description("No acknowledgement must be requested for a connection that is closed already.")).requestAcknowledgementOfUnacknowledgedStanzas();
        verify(connection, times(1).description("The close must not be left to the next event for a connection that is closed already.")).close(any(StreamError.class));
    }
}
