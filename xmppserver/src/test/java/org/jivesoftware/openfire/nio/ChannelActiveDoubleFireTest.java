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

import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import org.jivesoftware.openfire.net.ServerStanzaHandler;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.jivesoftware.openfire.nio.NettyConnectionHandler.CONNECTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Verifies that {@code channelActive()} is not replayed through handlers that precede {@link NettyConnectionHandler} in
 * the pipeline after a successful TLS handshake.
 *
 * When a connection is accepted, Netty emits a single {@code channelActive()} event that traverses the pipeline from
 * the head. After the TLS handshake completes, {@link NettyConnectionHandler} must notify downstream business handlers
 * that the connection is ready for use without restarting {@code channelActive()} from the pipeline head. Restarting
 * the event from the head causes every upstream handler to observe {@code channelActive()} a second time, implicitly
 * requiring those handlers to be idempotent.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3330">OF-3330: Stop replaying channelActive through head-side handlers after TLS handshake</a>
 */
public class ChannelActiveDoubleFireTest
{
    /**
     * Verifies that a head-sided handler is activated only once per connection.
     */
    @Test
    void headSideHandlersReceiveChannelActiveOnlyOnce()
    {
        // Setup test fixture.
        final CountingHandler head = new CountingHandler();

        // Real connection handler at the tail, exactly as in production.
        final NettyConnectionHandler<ServerStanzaHandler> real = spy(new NettyServerConnectionHandler(mock(ConnectionConfiguration.class)));

        final EmbeddedChannel ch = new EmbeddedChannel();
        try {
            // Satisfy userEventTriggered's precondition: setEncrypted() dereferences the CONNECTION attr.
            ch.attr(CONNECTION).set(mock(NettyConnection.class));
            ch.pipeline().addLast(head).addLast(real);

            // Execute system under test.
            ch.pipeline().fireChannelActive(); // accept-time
            ch.pipeline().fireUserEventTriggered(SslHandshakeCompletionEvent.SUCCESS); // handshake

            assertEquals(1, head.count.get(), "Head-side handler must be activated once per connection; the post-handshake head restart re-fires it a second time.");
        } finally {
            // Tear down test fixture.
            ch.finishAndReleaseAll();
        }
    }

    private static class CountingHandler extends ChannelInboundHandlerAdapter {
        final AtomicInteger count = new AtomicInteger();
        @Override public void channelActive(ChannelHandlerContext ctx) {
            count.incrementAndGet();
            ctx.fireChannelActive();
        }
    }
}
