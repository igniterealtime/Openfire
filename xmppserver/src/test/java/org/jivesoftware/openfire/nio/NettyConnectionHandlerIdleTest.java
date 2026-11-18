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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.net.StanzaHandler;
import org.jivesoftware.openfire.session.DomainPair;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.openfire.spi.NettyServerInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for how {@link NettyConnectionHandler} marks a connection as no longer idle when data arrives on it.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class NettyConnectionHandlerIdleTest
{
    private EmbeddedChannel channel;

    private ChannelHandlerContext ctx;

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
        channel = new EmbeddedChannel();
        channel.attr(NettyConnectionHandler.HANDLER).set(mock(StanzaHandler.class));
        ctx = mock(ChannelHandlerContext.class);
        doReturn(channel).when(ctx).channel();
    }

    @AfterEach
    public void tearDown()
    {
        channel.finishAndReleaseAll();
        Fixtures.clearExistingProperties();
    }

    /**
     * Verifies that data that arrives on a connection that Openfire opened to another server, of which the pipeline has
     * no traffic handler, marks the connection as no longer idle, so that an acknowledgement that the remote server
     * sends keeps the connection from being closed for being idle, and that a later close can again be deferred to wait
     * for an acknowledgement.
     */
    @Test
    public void inboundDataOnOutgoingServerConnectionClearsIdleFlag()
    {
        // Setup test fixture.
        final NettyOutboundConnectionHandler handler = new NettyOutboundConnectionHandler(mock(ConnectionConfiguration.class), new DomainPair(Fixtures.XMPP_DOMAIN, "remote.example.org"), 5269);
        channel.attr(NettyConnectionHandler.IDLE_FLAG).set(true);
        channel.attr(NettyConnectionHandler.IDLE_CLOSE_DEFERRED).set(true);
        assertNull(channel.pipeline().get(NettyServerInitializer.TRAFFIC_HANDLER_NAME), "Test fixture expects a pipeline without a traffic handler, as on a connection that Openfire opens to another server.");

        // Execute system under test.
        handler.channelRead0(ctx, "<a xmlns='urn:xmpp:sm:3' h='1'/>");

        // Verify result.
        assertNull(channel.attr(NettyConnectionHandler.IDLE_FLAG).get(), "Data that arrives on the connection must mark it as no longer idle.");
        assertNull(channel.attr(NettyConnectionHandler.IDLE_CLOSE_DEFERRED).get(), "Data that arrives on the connection must allow a later close for being idle to be deferred again.");
    }
}
