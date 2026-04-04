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
import org.jivesoftware.openfire.spi.ConnectionConfiguration;

/**
 * C2S business-logic handler for QUIC channels.
 */
public class QuicClientConnectionHandler extends NettyClientConnectionHandler
{
    public QuicClientConnectionHandler(final ConnectionConfiguration configuration)
    {
        super(configuration);
    }

    @Override
    NettyConnection createNettyConnection(final ChannelHandlerContext ctx)
    {
        final NettyConnection connection = super.createNettyConnection(ctx);
        connection.setEncrypted(true); // QUIC always runs on top of TLS.
        return connection;
    }
}
