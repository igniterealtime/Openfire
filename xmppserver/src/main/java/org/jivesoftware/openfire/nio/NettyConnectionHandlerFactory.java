/*
 * Copyright (C) 2023 Ignite Realtime Foundation. All rights reserved.
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

/**
 * Responsible for creating the NettyConnectionHandler for the connection type.
 *
 * @author Alex Gidman
 */
public class NettyConnectionHandlerFactory {

    /**
     * Creates a new NettyConnectionHandler based on the type of connection set in the configuration.
     * @param configuration options for how the connection is configured
     * @return a new NettyConnectionHandler
     */
    public static NettyConnectionHandler createConnectionHandler(ConnectionConfiguration configuration) {
        switch (configuration.getType()) {
            case SOCKET_S2S:
                return new NettyServerConnectionHandler(configuration);
            case SOCKET_C2S:
                return new NettyClientConnectionHandler(configuration);
            case COMPONENT:
                return new NettyComponentConnectionHandler(configuration);
            case CONNECTION_MANAGER:
                return new NettyMultiplexerConnectionHandler(configuration);
            default:
                throw new IllegalStateException("This implementation does not support the connection type as defined in the provided configuration: " + configuration.getType());
        }
    }
}
