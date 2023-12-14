/*
 * Copyright (C) 2017-2023 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.session;

import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.StreamID;

import javax.annotation.Nonnull;
import java.util.Locale;

/**
 * @author dwd
 *
 */
public abstract class LocalServerSession extends LocalSession implements ServerSession {

    /**
     * The method that was used to authenticate this session. Null when the session is not authenticated.
     */
    protected AuthenticationMethod authenticationMethod = null;

    public LocalServerSession(String serverName, Connection connection,
            StreamID streamID) {
        super(serverName, connection, streamID, Locale.getDefault());
    }

    @Override
    public void setDetached() {
        // TODO Implement stream management for s2s (OF-2425). Remove this override when it is.
        throw new UnsupportedOperationException("Stream management is not supported for server-to-server connections");
    }

    @Override
    public void reattach(LocalSession connectionProvider, long h) {
        // TODO Implement stream management for s2s (OF-2425). Remove this override when it is.
        throw new UnsupportedOperationException("Stream management is not supported for server-to-server connections");
    }

    /**
     * Returns the connection associated with this Session.
     *
     * @return The connection for this session
     */
    @Nonnull
    @Override
    public Connection getConnection() {
        final Connection connection = super.getConnection();
        // valid only as long as stream management for s2s is not implemented (OF-2425). Remove this override when it is.
        assert connection != null; // Openfire does not implement stream management for s2s (OF-2425). Therefor, the connection cannot be null.
        return connection;
    }

    @Override
    public void setStatus(Status status) {
        super.setStatus(status);
        if (status != Status.AUTHENTICATED) {
            authenticationMethod = null;
        }
    }

    /**
     * Obtain method that was used to authenticate this session. Null when the session is not authenticated.
     *
     * @return the method used for authentication (possibly null).
     */
    @Override
    public AuthenticationMethod getAuthenticationMethod() {
        return authenticationMethod;
    }

    /**
     * Set the method that was used to authenticate this session. Setting a value will cause the status of this session
     * to be updated to 'Authenticated'.
     *
     * @param authenticationMethod The new authentication method for this session
     */
    public void setAuthenticationMethod(@Nonnull final AuthenticationMethod authenticationMethod) {
        this.authenticationMethod = authenticationMethod;
        setStatus(Status.AUTHENTICATED);
    }

    @Override
    public String toString()
    {
        return this.getClass().getSimpleName() +"{" +
            "address=" + address +
            ", streamID=" + streamID +
            ", status=" + status +
            ", isEncrypted=" + isEncrypted() +
            ", isDetached=" + isDetached() +
            ", authenticationMethod=" + authenticationMethod +
            '}';
    }
}
