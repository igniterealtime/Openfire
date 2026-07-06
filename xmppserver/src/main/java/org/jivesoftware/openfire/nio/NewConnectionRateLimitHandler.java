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

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;
import org.jivesoftware.openfire.ratelimit.NewConnectionLimiterRegistry;
import org.jivesoftware.openfire.spi.ConnectionType;
import org.jivesoftware.util.TokenBucketRateLimiter;

import javax.annotation.Nonnull;

/**
 * A Netty pipeline handler that enforces a rate limit on newly accepted inbound connections.
 *
 * For maximum efficiency, this handler should be placed at the head of the server-side child channel pipeline (via
 * {@code addFirst}) so that it intercepts {@code channelActive} before any other handler runs. When a new connection
 * arrives and the rate limit is exhausted, the channel is closed immediately and the event is not propagated further,
 * preventing all downstream handlers from performing any work (TLS negotiation, XML parser allocation,
 * session scaffolding, etc.) for the rejected connection.
 *
 * Rate limits are looked up per {@link ConnectionType} from {@link NewConnectionLimiterRegistry}, which maintains one
 * {@link TokenBucketRateLimiter} per logical connection category (e.g. C2S, S2S). Connection types that do not have an
 * active limit configured will receive an unlimited limiter and are always passed through.
 *
 * This handler is {@link io.netty.channel.ChannelHandler.Sharable} because it holds no per-channel state.
 * A single instance may be shared across all child channels created by the same server socket acceptor.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @see NewConnectionLimiterRegistry
 * @see TokenBucketRateLimiter
 */
@ChannelHandler.Sharable
public class NewConnectionRateLimitHandler extends ChannelInboundHandlerAdapter
{
    /**
     * Attribute used on a channel to guard against duplicate execution of this handler on the same channel.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3331">OF-3331: Guard head-side pipeline handlers against duplicate channelActive execution after TLS handshake</a>
     */
    private static final AttributeKey<Boolean> HAS_BEEN_EVALUATED = AttributeKey.valueOf(NewConnectionRateLimitHandler.class, "evaluated");

    private final ConnectionType connectionType;

    public NewConnectionRateLimitHandler(@Nonnull final ConnectionType connectionType)
    {
        this.connectionType = connectionType;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception
    {
        if (ctx.channel().attr(HAS_BEEN_EVALUATED).getAndSet(Boolean.TRUE) != null)
        {
            // Guard against duplicate execution (OF-3331).
            super.channelActive(ctx);
            return;
        }

        // Note that the NewConnectionLimiterRegistry lookup is intentionally repeated on every call to ensure that
        // dynamic configuration changes (permits per second, burst size, enabled flag) take effect without requiring
        // a server restart or a new handler instance.
        final TokenBucketRateLimiter limiter = NewConnectionLimiterRegistry.getLimiter(connectionType);
        if (!limiter.tryAcquire())
        {
            NewConnectionLimiterRegistry.maybeLogRejection(connectionType);
            ctx.close();
            return; // Do NOT call super — kills the event here, nothing downstream fires
        }
        super.channelActive(ctx); // propagate normally
    }
}
