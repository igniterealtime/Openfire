/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

import java.util.Collection;

/**
 * Coordinates connections (accept, read, termination) on the server.
 *
 * @author Iain Shigeoka
 */
public interface ConnectionManager {

    /**
     * The default XMPP port for clients. This port can be used with secured
     * and unsecured connections. Clients will initially connect using an unsecure
     * connection and may secure it by using StartTLS.
     */
    int DEFAULT_PORT = 5222;
    /**
     * The default legacy Jabber port for SSL traffic. This old method, and soon
     * to be deprecated, uses encrypted connections as soon as they are created.
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
     * Returns an array of the ports managed by this connection manager.
     *
     * @return an iterator of the ports managed by this connection manager
     *      (can be an empty but never null).
     */
    Collection<ServerPort> getPorts();

    /**
     * Sets if the port listener for unsecured clients will be available or not. When disabled
     * there won't be a port listener active. Therefore, new clients won't be able to connect to
     * the server.
     *
     * @param enabled true if new unsecured clients will be able to connect to the server.
     */
    void enableClientListener( boolean enabled );

    /**
     * Returns true if the port listener for unsecured clients is available. When disabled
     * there won't be a port listener active. Therefore, new clients won't be able to connect to
     * the server.
     *
     * @return true if the port listener for unsecured clients is available.
     */
    boolean isClientListenerEnabled();

    /**
     * Sets if the port listener for secured clients will be available or not. When disabled
     * there won't be a port listener active. Therefore, new secured clients won't be able to
     * connect to the server.
     *
     * @param enabled true if new secured clients will be able to connect to the server.
     */
    void enableClientSSLListener( boolean enabled );

    /**
     * Returns true if the port listener for secured clients is available. When disabled
     * there won't be a port listener active. Therefore, new secured clients won't be able to
     * connect to the server.
     *
     * @return true if the port listener for unsecured clients is available.
     */
    boolean isClientSSLListenerEnabled();

    /**
     * Sets if the port listener for external components will be available or not. When disabled
     * there won't be a port listener active. Therefore, new external components won't be able to
     * connect to the server.
     *
     * @param enabled true if new external components will be able to connect to the server.
     */
    void enableComponentListener( boolean enabled );

    /**
     * Returns true if the port listener for external components is available. When disabled
     * there won't be a port listener active. Therefore, new external components won't be able to
     * connect to the server.
     *
     * @return true if the port listener for external components is available.
     */
    boolean isComponentListenerEnabled();

    /**
     * Sets if the port listener for remote servers will be available or not. When disabled
     * there won't be a port listener active. Therefore, new remote servers won't be able to
     * connect to the server.
     *
     * @param enabled true if new remote servers will be able to connect to the server.
     */
    void enableServerListener( boolean enabled );

    /**
     * Returns true if the port listener for remote servers is available. When disabled
     * there won't be a port listener active. Therefore, new remote servers won't be able to
     * connect to the server.
     *
     * @return true if the port listener for remote servers is available.
     */
    boolean isServerListenerEnabled();

    /**
     * Sets if the port listener for connection managers will be available or not. When disabled
     * there won't be a port listener active. Therefore, clients will need to connect directly
     * to the server.
     *
     * @param enabled true if new connection managers will be able to connect to the server.
     */
    void enableConnectionManagerListener( boolean enabled );

    /**
     * Returns true if the port listener for connection managers is available. When disabled
     * there won't be a port listener active. Therefore, clients will need to connect directly
     * to the server.
     *
     * @return true if the port listener for connection managers is available.
     */
    boolean isConnectionManagerListenerEnabled();

    /**
     * Sets the port to use for unsecured clients. Default port: 5222.
     *
     * @param port the port to use for unsecured clients.
     */
    void setClientListenerPort( int port );

    /**
     * Returns the port to use for unsecured clients. Default port: 5222.
     *
     * @return the port to use for unsecured clients.
     */
    int getClientListenerPort();

    /**
     * Sets the port to use for secured clients. Default port: 5223.
     *
     * @param port the port to use for secured clients.
     */
    void setClientSSLListenerPort( int port );

    /**
     * Returns the port to use for secured clients. Default port: 5223.
     *
     * @return the port to use for secured clients.
     */
    int getClientSSLListenerPort();

    /**
     * Sets the port to use for external components.
     *
     * @param port the port to use for external components.
     */
    void setComponentListenerPort( int port );

    /**
     * Returns the port to use for external components.
     *
     * @return the port to use for external components.
     */
    int getComponentListenerPort();

    /**
     * Sets the port to use for remote servers. This port is used for remote servers to connect
     * to this server. Default port: 5269.
     *
     * @param port the port to use for remote servers.
     */
    void setServerListenerPort( int port );

    /**
     * Returns the port to use for remote servers. This port is used for remote servers to connect
     * to this server. Default port: 5269.
     *
     * @return the port to use for remote servers.
     */
    int getServerListenerPort();

    /**
     * Sets the port to use for remote servers. This port is used for remote servers to connect
     * to this server, using direct TLS. Default port: 5270.
     *
     * @param port the port to use for remote servers.
     */
    void setServerSslListenerPort( int port );

    /**
     * Returns the port to use for remote servers. This port is used for remote servers to connect
     * to this server, using direct TLS. Default port: 5270.
     *
     * @return the port to use for remote servers.
     */
    int getServerSslListenerPort();

    /**
     * Sets the port to use for connection managers. This port is used for connection managers
     * to connect to this server. Default port: 5262.
     *
     * @param port the port to use for connection managers.
     */
    void setConnectionManagerListenerPort( int port );

    /**
     * Returns the port to use for remote servers. This port is used for connection managers
     * to connect to this server. Default port: 5262.
     *
     * @return the port to use for connection managers.
     */
    int getConnectionManagerListenerPort();
}
