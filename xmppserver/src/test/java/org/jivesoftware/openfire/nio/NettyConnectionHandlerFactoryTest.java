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

import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.openfire.spi.ConnectionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NettyConnectionHandlerFactory}.
 */
public class NettyConnectionHandlerFactoryTest
{
    @Test
    public void quicC2sShouldThrowUnsupportedOperationException()
    {
        final ConnectionConfiguration config = configFor(ConnectionType.QUIC_C2S);
        assertThrows(UnsupportedOperationException.class,
            () -> NettyConnectionHandlerFactory.createConnectionHandler(config),
            "QUIC_C2S must not be created via the factory — it requires a QuicSessionStreamRouter");
    }

    @Test
    public void socketC2sShouldReturnClientConnectionHandler()
    {
        final ConnectionConfiguration config = configFor(ConnectionType.SOCKET_C2S);
        final NettyConnectionHandler handler = NettyConnectionHandlerFactory.createConnectionHandler(config);
        assertInstanceOf(NettyClientConnectionHandler.class, handler);
    }

    @Test
    public void socketS2sShouldReturnServerConnectionHandler()
    {
        final ConnectionConfiguration config = configFor(ConnectionType.SOCKET_S2S);
        final NettyConnectionHandler handler = NettyConnectionHandlerFactory.createConnectionHandler(config);
        assertInstanceOf(NettyServerConnectionHandler.class, handler);
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private ConnectionConfiguration configFor(final ConnectionType type)
    {
        final ConnectionConfiguration config = mock(ConnectionConfiguration.class);
        when(config.getType()).thenReturn(type);
        return config;
    }
}
