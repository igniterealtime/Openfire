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

/**
 * A {@link ConnectionAcceptor} wrapper that delegates {@link #isIdle()} and
 * {@link #reconfigure(ConnectionConfiguration)} to an underlying acceptor, but makes
 * {@link #start()} and {@link #stop()} no-ops.
 *
 * <p>This is used when multiple {@link org.jivesoftware.openfire.spi.ConnectionListener}
 * instances share a single underlying acceptor (e.g. a
 * {@link QuicMultiplexedConnectionAcceptor} shared between the QUIC C2S and S2S listeners).
 * The primary listener owns the real acceptor and manages its lifecycle; secondary listeners
 * receive a {@code SharedConnectionAcceptor} so that their {@code start()} and {@code stop()}
 * calls do not double-start or prematurely stop the shared acceptor.</p>
 */
class SharedConnectionAcceptor extends ConnectionAcceptor
{
    private final ConnectionAcceptor delegate;

    SharedConnectionAcceptor(final ConnectionAcceptor delegate)
    {
        super(delegate.configuration);
        this.delegate = delegate;
    }

    /** No-op: lifecycle is managed by the primary listener that owns the delegate. */
    @Override
    public void start()
    {
        // intentionally empty
    }

    /** No-op: lifecycle is managed by the primary listener that owns the delegate. */
    @Override
    public void stop()
    {
        // intentionally empty
    }

    @Override
    public boolean isIdle()
    {
        return delegate.isIdle();
    }

    @Override
    public void reconfigure(final ConnectionConfiguration configuration)
    {
        delegate.reconfigure(configuration);
    }
}
