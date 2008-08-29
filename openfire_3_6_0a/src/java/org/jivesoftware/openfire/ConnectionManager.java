/**
 * $RCSfile$
 * $Revision: 1583 $
 * $Date: 2005-07-03 17:55:39 -0300 (Sun, 03 Jul 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire;

import org.jivesoftware.openfire.net.SocketReader;

import java.io.IOException;
import java.net.Socket;
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
    final int DEFAULT_PORT = 5222;
    /**
     * The default legacy Jabber port for SSL traffic. This old method, and soon
     * to be deprecated, uses encrypted connections as soon as they are created.
     */
    final int DEFAULT_SSL_PORT = 5223;
    /**
     * The default XMPP port for external components.
     */
    final int DEFAULT_COMPONENT_PORT = 5275;
    /**
     * The default XMPP port for server2server communication.
     */
    final int DEFAULT_SERVER_PORT = 5269;
    /**
     * The default XMPP port for connection multiplex.
     */
    final int DEFAULT_MULTIPLEX_PORT = 5262;

    /**
     * Returns an array of the ports managed by this connection manager.
     *
     * @return an iterator of the ports managed by this connection manager
     *      (can be an empty but never null).
     */
    public Collection<ServerPort> getPorts();

    /**
     * Creates a new socket reader for the new accepted socket to be managed
     * by the connection manager.
     *
     * @param socket the new accepted socket by this manager.
     * @param isSecure true if the connection is secure.
     * @param serverPort holds information about the port on which the server is listening for
     *        connections.
     * @param useBlockingMode true means that the server will use a thread per connection.
     * @return the created socket reader.
     * @throws java.io.IOException when there is an error creating the socket reader.
     */
    public SocketReader createSocketReader(Socket socket, boolean isSecure, ServerPort serverPort,
            boolean useBlockingMode) throws IOException;

    /**
     * Sets if the port listener for unsecured clients will be available or not. When disabled
     * there won't be a port listener active. Therefore, new clients won't be able to connect to
     * the server.
     *
     * @param enabled true if new unsecured clients will be able to connect to the server.
     */
    public void enableClientListener(boolean enabled);

    /**
     * Returns true if the port listener for unsecured clients is available. When disabled
     * there won't be a port listener active. Therefore, new clients won't be able to connect to
     * the server.
     *
     * @return true if the port listener for unsecured clients is available.
     */
    public boolean isClientListenerEnabled();

    /**
     * Sets if the port listener for secured clients will be available or not. When disabled
     * there won't be a port listener active. Therefore, new secured clients won't be able to
     * connect to the server.
     *
     * @param enabled true if new secured clients will be able to connect to the server.
     */
    public void enableClientSSLListener(boolean enabled);

    /**
     * Returns true if the port listener for secured clients is available. When disabled
     * there won't be a port listener active. Therefore, new secured clients won't be able to
     * connect to the server.
     *
     * @return true if the port listener for unsecured clients is available.
     */
    public boolean isClientSSLListenerEnabled();

    /**
     * Sets if the port listener for external components will be available or not. When disabled
     * there won't be a port listener active. Therefore, new external components won't be able to
     * connect to the server.
     *
     * @param enabled true if new external components will be able to connect to the server.
     */
    public void enableComponentListener(boolean enabled);

    /**
     * Returns true if the port listener for external components is available. When disabled
     * there won't be a port listener active. Therefore, new external components won't be able to
     * connect to the server.
     *
     * @return true if the port listener for external components is available.
     */
    public boolean isComponentListenerEnabled();

    /**
     * Sets if the port listener for remote servers will be available or not. When disabled
     * there won't be a port listener active. Therefore, new remote servers won't be able to
     * connect to the server.
     *
     * @param enabled true if new remote servers will be able to connect to the server.
     */
    public void enableServerListener(boolean enabled);

    /**
     * Returns true if the port listener for remote servers is available. When disabled
     * there won't be a port listener active. Therefore, new remote servers won't be able to
     * connect to the server.
     *
     * @return true if the port listener for remote servers is available.
     */
    public boolean isServerListenerEnabled();

    /**
     * Sets if the port listener for connection managers will be available or not. When disabled
     * there won't be a port listener active. Therefore, clients will need to connect directly
     * to the server.
     *
     * @param enabled true if new connection managers will be able to connect to the server.
     */
    public void enableConnectionManagerListener(boolean enabled);

    /**
     * Returns true if the port listener for connection managers is available. When disabled
     * there won't be a port listener active. Therefore, clients will need to connect directly
     * to the server.
     *
     * @return true if the port listener for connection managers is available.
     */
    public boolean isConnectionManagerListenerEnabled();

    /**
     * Sets the port to use for unsecured clients. Default port: 5222.
     *
     * @param port the port to use for unsecured clients.
     */
    public void setClientListenerPort(int port);

    /**
     * Returns the port to use for unsecured clients. Default port: 5222.
     *
     * @return the port to use for unsecured clients.
     */
    public int getClientListenerPort();

    /**
     * Sets the port to use for secured clients. Default port: 5223.
     *
     * @param port the port to use for secured clients.
     */
    public void setClientSSLListenerPort(int port);

    /**
     * Returns the port to use for secured clients. Default port: 5223.
     *
     * @return the port to use for secured clients.
     */
    public int getClientSSLListenerPort();

    /**
     * Sets the port to use for external components.
     *
     * @param port the port to use for external components.
     */
    public void setComponentListenerPort(int port);

    /**
     * Returns the port to use for external components.
     *
     * @return the port to use for external components.
     */
    public int getComponentListenerPort();

    /**
     * Sets the port to use for remote servers. This port is used for remote servers to connect
     * to this server. Default port: 5269.
     *
     * @param port the port to use for remote servers.
     */
    public void setServerListenerPort(int port);

    /**
     * Returns the port to use for remote servers. This port is used for remote servers to connect
     * to this server. Default port: 5269.
     *
     * @return the port to use for remote servers.
     */
    public int getServerListenerPort();

    /**
     * Sets the port to use for connection managers. This port is used for connection managers
     * to connect to this server. Default port: 5262.
     *
     * @param port the port to use for connection managers.
     */
    public void setConnectionManagerListenerPort(int port);

    /**
     * Returns the port to use for remote servers. This port is used for connection managers
     * to connect to this server. Default port: 5262.
     *
     * @return the port to use for connection managers.
     */
    public int getConnectionManagerListenerPort();
}
