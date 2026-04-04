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
package org.jivesoftware.openfire.spi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Placeholder implementation for QUIC-based connection acceptance.
 *
 * This class intentionally does not (yet) create a QUIC server endpoint. It provides lifecycle integration that keeps
 * connection listener behavior consistent while QUIC transport support is incrementally implemented.
 */
public class QuicConnectionAcceptor extends ConnectionAcceptor
{
    private static final Logger Log = LoggerFactory.getLogger(QuicConnectionAcceptor.class);
    private volatile boolean running = false;

    public QuicConnectionAcceptor(final ConnectionConfiguration configuration)
    {
        super(configuration);
    }

    @Override
    public synchronized void start()
    {
        if (running) {
            return;
        }

        running = true;
        Log.warn("QUIC listener is enabled on port {}, but QUIC transport runtime is not yet implemented.", configuration.getPort());
    }

    @Override
    public synchronized void stop()
    {
        running = false;
    }

    @Override
    public boolean isIdle()
    {
        return !running;
    }

    @Override
    public synchronized void reconfigure(final ConnectionConfiguration configuration)
    {
        this.configuration = configuration;
    }
}
