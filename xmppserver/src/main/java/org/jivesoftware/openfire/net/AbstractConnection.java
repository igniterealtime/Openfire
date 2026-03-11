/*
 * Copyright (C) 2023-2026 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.net;

import org.dom4j.Namespace;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.ConnectionCloseListener;
import org.jivesoftware.openfire.session.LocalSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * A partial implementation of the {@link org.jivesoftware.openfire.Connection} interface, implementing functionality
 * that's commonly shared by Connection implementations.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public abstract class AbstractConnection implements Connection
{
    private static final Logger Log = LoggerFactory.getLogger(AbstractConnection.class);

    /**
     * The major version of XMPP being used by this connection (major_version.minor_version). In most cases, the version
     * should be "1.0". However, older clients using the "Jabber" protocol do not set a version. In that case, the
     * version is "0.0".
     */
    private int majorVersion = 1;

    /**
     * The minor version of XMPP being used by this connection (major_version.minor_version). In most cases, the version
     * should be "1.0". However, older clients using the "Jabber" protocol do not set a version. In that case, the
     * version is "0.0".
     */
    private int minorVersion = 0;

    /**
     * When a connection is used to transmit an XML data, the root element of that data can define XML namespaces other
     * than the ones that are default (eg: 'jabber:client', 'jabber:server', etc). For an XML parser to be able to parse
     * stanzas or other elements that are defined in that namespace (eg: are prefixed), these namespaces are recorded
     * here.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2556">Issue OF-2556</a>
     */
    private final Set<Namespace> additionalNamespaces = new HashSet<>();

    /**
     * Contains all registered listener for close event notification. Registrations after the Session is closed will be
     * immediately notified <em>before</em> the registration call returns (within the context of the registration call).
     * An optional handback object can be associated with the registration if the same listener is registered to listen
     * for multiple connection closures.
     */
    final protected LinkedHashMap<ConnectionCloseListener, Object> closeListeners = new LinkedHashMap<>();

    private final CompletableFuture<Void> closeFuture = new CompletableFuture<>();

    /**
     * The session that owns this connection.
     */
    protected LocalSession session;

    @Override
    public void init(LocalSession owner) {
        session = owner;
    }

    @Override
    public void reinit(final LocalSession owner)
    {
        this.session = owner;

        // ConnectionCloseListeners are registered with their session instance as a callback object. When re-initializing,
        // this object needs to be replaced with the new session instance (or otherwise, the old session will be used
        // during the callback. OF-2014
        closeListeners.entrySet().stream()
            .filter(entry -> entry.getValue() instanceof LocalSession)
            .forEach(entry -> entry.setValue(owner));
    }

    /**
     * Returns the session that owns this connection, if the connection has been initialized.
     *
     * @return session that owns this connection.
     */
    public LocalSession getSession() {
        // TODO is it needed to expose this publicly? This smells.
        return session;
    }

    @Override
    public int getMajorXMPPVersion() {
        return majorVersion;
    }

    @Override
    public int getMinorXMPPVersion() {
        return minorVersion;
    }

    @Override
    public void setXMPPVersion(int majorVersion, int minorVersion) {
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
    }

    @Override
    @Nonnull
    public Set<Namespace> getAdditionalNamespaces() {
        return additionalNamespaces;
    }

    @Override
    public void setAdditionalNamespaces(@Nonnull final Set<Namespace> additionalNamespaces) {
        this.additionalNamespaces.clear();
        this.additionalNamespaces.addAll(additionalNamespaces);
    }

    @Override
    public void registerCloseListener(ConnectionCloseListener listener, Object callback) {
        if (isClosed()) {
            listener.onConnectionClosing(callback).join();
        }
        else {
            closeListeners.put( listener, callback );
        }
    }

    @Override
    public void removeCloseListener(ConnectionCloseListener listener) {
        closeListeners.remove( listener );
    }

    /**
     * Notifies all registered close listeners that this connection has been closed.
     *
     * Listeners are executed sequentially in descending priority order. Built-in listeners (such as those for client,
     * server, or component sessions) use high-priority values to ensure critical cleanup logic (e.g. session removal
     * from routing tables) occurs before any lower-priority or third-party listeners are invoked. For listeners sharing
     * the same priority, the registration order is preserved.
     *
     * Each listener may return a {@link CompletableFuture} representing asynchronous work. The next listener is not
     * invoked until the previous listener's future has completed. The future returned by this method completes only
     * after all listeners have been invoked and all listener-provided futures have completed.
     *
     * Exceptions thrown by listeners, whether synchronously during invocation or asynchronously via their returned
     * future, are captured and propagated through the returned future rather than being swallowed or thrown directly.
     *
     * Subclasses must call {@link #completeCloseFuture()} within the {@code whenComplete} handler of the future
     * returned by this method, after all remaining teardown work for that connection type has finished. It must be
     * called unconditionally — regardless of whether listeners completed normally or exceptionally.
     *
     * @return a future that completes when all close listeners and their asynchronous work have finished.
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3179">OF-3179: Ensure deterministic execution order for ConnectionCloseListeners</a>
     */
    protected CompletableFuture<?> notifyCloseListeners()
    {
        Log.debug("Notifying close listeners of connection {}", this);

        // Sort listeners by priority (highest first), then by insertion order for ties.
        final List<Map.Entry<ConnectionCloseListener, Object>> sortedListeners = new ArrayList<>(closeListeners.entrySet());
        sortedListeners.sort((e1, e2) -> {
            int priorityCompare = Integer.compare(e2.getKey().getPriority(), e1.getKey().getPriority());
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            // For listeners with the same priority, preserve insertion order.
            return 0;
        });

        // Execute listeners sequentially, in priority order.
        CompletableFuture<?> result = CompletableFuture.completedFuture(null);
        for (final Map.Entry<ConnectionCloseListener, Object> entry : sortedListeners)
        {
            final ConnectionCloseListener listener = entry.getKey();
            if (listener != null) {
                final Object handback = entry.getValue();
                result = result.thenCompose(v -> {
                    try {
                        final CompletableFuture<?> listenerFuture = listener.onConnectionClosing(handback);
                        // Flatten the listener-provided future, treating null as an already-completed future.
                        return listenerFuture != null ? listenerFuture : CompletableFuture.completedFuture(null);
                    } catch (Exception e) {
                        // Capture synchronous exceptions and propagate via the returned future.
                        return CompletableFuture.failedFuture(e);
                    }
                });
            }
        }

        return result;
    }

    @Override
    public CompletionStage<Void> getCloseFuture() {
        return closeFuture;
    }

    /**
     * Signals that all close operations for this connection have finished, completing the stage returned by
     * {@link #getCloseFuture()}.
     *
     * Must be called by subclasses at the very end of their close sequence, after the physical transport has been
     * closed and all {@link ConnectionCloseListener} instances have been notified. Calling this method more than once
     * is safe. Subsequent calls have no effect.
     *
     * Subclasses that fail to call this method will leave any callers awaiting {@link #getCloseFuture()} suspended
     * indefinitely.
     */
    protected void completeCloseFuture() {
        closeFuture.complete(null);
    }
}
