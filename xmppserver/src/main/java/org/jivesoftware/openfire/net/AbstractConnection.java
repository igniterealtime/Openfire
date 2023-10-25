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
package org.jivesoftware.openfire.net;

import org.dom4j.Namespace;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.ConnectionCloseListener;
import org.jivesoftware.openfire.session.LocalSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
    final protected Map<ConnectionCloseListener, Object> closeListeners = new HashMap<>();

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
            listener.onConnectionClose(callback);
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
     * Notifies all close listeners that the connection has been closed. Used by subclasses to properly finish closing
     * the connection.
     */
    protected void notifyCloseListeners() {
        for( final Map.Entry<ConnectionCloseListener, Object> entry : closeListeners.entrySet() )
        {
            if (entry.getKey() != null) {
                try {
                    entry.getKey().onConnectionClose(entry.getValue());
                } catch (Exception e) {
                    Log.error("Error notifying listener: " + entry.getKey(), e);
                }
            }
        }
    }
}
