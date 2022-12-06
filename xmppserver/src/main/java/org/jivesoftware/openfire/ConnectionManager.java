/*
 * Copyright (C) 2004-2008 Jive Software, 2022 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire;

import org.jivesoftware.openfire.spi.ConnectionType;

/**
 * Coordinates connections (accept, read, termination) on the server.
 *
 * @author Iain Shigeoka
 */
public interface ConnectionManager {

    /**
     * The default XMPP port for clients. This port can be used with encrypted
     * and unencrypted connections. Clients will initially connect using an unencrypted
     * connection and may encrypt it by using StartTLS.
     */
    int DEFAULT_PORT = 5222;
    /**
     * The default legacy Jabber port for Direct TLS traffic. This method uses connections that are encrypted as soon as
     * they are created.
     */
    int DEFAULT_SSL_PORT = 5223;
    /**
     * The default XMPP port for external components.
     */
    int DEFAULT_COMPONENT_PORT = 5275;

    /**
     * The XMPP port for external components using SSL traffic.
     */
    int DEFAULT_COMPONENT_SSL_PORT = 5276;

    /**
     * The default XMPP port for server2server communication, optionally using StartTLS.
     */
    int DEFAULT_SERVER_PORT = 5269;

    /**
     * The default XMPP port for server2server communication using Direct TLS.
     */
    int DEFAULT_SERVER_SSL_PORT = 5270;

    /**
     * The default XMPP port for connection multiplex.
     */
    int DEFAULT_MULTIPLEX_PORT = 5262;

    /**
     * The default XMPP port for connection multiplex.
     */
    int DEFAULT_MULTIPLEX_SSL_PORT = 5263;

    /**
     * Return if the configuration allows this listener to be enabled (but does not verify that the listener is
     * indeed active)
     *
     * The #startInSslMode parameter is used to distinguish between listeners that expect to receive SSL encrypted data
     * immediately, as opposed to connections that initially accept plain text data (the latter are typically subject to
     * StartTLS for in-band encryption configuration). When for a particular connection type only one of these options
     * is implemented, the parameter value is ignored.
     *
     * @param type The connection type for which a listener is to be configured.
     * @param startInSslMode true when the listener to be configured is in legacy SSL mode, otherwise false.
     * @return true if configuration allows this listener to be enabled, otherwise false.
     */
    boolean isEnabled(ConnectionType type, boolean startInSslMode);

    /**
     * Enables or disables a connection listener. Does nothing if the particular listener is already in the requested
     * state.
     *
     * The #startInSslMode parameter is used to distinguish between listeners that expect to receive SSL encrypted data
     * immediately, as opposed to connections that initially accept plain text data (the latter are typically subject to
     * StartTLS for in-band encryption configuration). When for a particular connection type only one of these options
     * is implemented, the parameter value is ignored.
     *
     * @param type The connection type for which a listener is to be configured.
     * @param startInSslMode true when the listener to be configured is in legacy SSL mode, otherwise false.
     * @param enabled true if the listener is to be enabled, otherwise false.
     */
    void enable(ConnectionType type, boolean startInSslMode, boolean enabled);

    /**
     * Retrieves the configured TCP port on which a listener accepts connections.
     *
     * @param type The connection type for which a listener is to be configured.
     * @param startInSslMode true when the listener to be configured is in legacy SSL mode, otherwise false.
     * @return a port number.
     */
    int getPort(ConnectionType type, boolean startInSslMode);

    /**
     * Sets the TCP port on which a listener accepts connections.
     *
     * @param type The connection type for which a listener is to be configured.
     * @param startInSslMode true when the listener to be configured is in legacy SSL mode, otherwise false.
     * @param port a port number.
     */
    void setPort(ConnectionType type, boolean startInSslMode, int port);
}
