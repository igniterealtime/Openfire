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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a port on which the server will listen for connections.
 * Used to aggregate information that the rest of the system needs
 * regarding the port while hiding implementation details.
 *
 * @author Iain Shigeoka
 * @deprecated
 */
@Deprecated
public class ServerPort {

    private int port;
    private List<String> names = new ArrayList<>(1);
    private String address;
    private boolean secure;
    private String algorithm;
    private Type type;

    public ServerPort(int port, String name, String address,
                      boolean isSecure, String algorithm, Type type)
    {
        this.port = port;
        this.names.add(name);
        this.address = address;
        this.secure = isSecure;
        this.algorithm = algorithm;
        this.type = type;
    }

    /**
     * Returns the port number that is being used.
     *
     * @return the port number this server port is listening on.
     */
    public int getPort() {
        return port;
    }

    /**
     * Returns the logical domains for this server port. As multiple
     * domains may point to the same server, this helps to define what
     * the server considers "local".
     *
     * @return the server domain name(s) as Strings.
     */
    public List<String> getDomainNames() {
        return Collections.unmodifiableList(names);
    }

    /**
     * Returns the dot separated IP address for the server.
     *
     * @return The dot separated IP address for the server
     */
    public String getIPAddress() {
        return address;
    }

    /**
     * Determines if the connection is secure.
     *
     * @return True if the connection is secure
     */
    public boolean isSecure() {
        return secure;
    }

    /**
     * Returns the basic protocol/algorithm being used to secure
     * the port connections. An example would be "SSL" or "TLS".
     *
     * @return The protocol used or null if this is not a secure server port
     */
    public String getSecurityType() {
        return algorithm;
    }

    /**
     * Returns true if other servers can connect to this port for s2s communication.
     *
     * @return true if other servers can connect to this port for s2s communication.
     */
    public boolean isServerPort() {
        return type == Type.server;
    }

    /**
     * Returns true if clients can connect to this port.
     *
     * @return true if clients can connect to this port.
     */
    public boolean isClientPort() {
        return type == Type.client;
    }

    /**
     * Returns true if external components can connect to this port.
     *
     * @return true if external components can connect to this port.
     */
    public boolean isComponentPort() {
        return type == Type.component;
    }

    /**
     * Returns true if connection managers can connect to this port.
     *
     * @return true if connection managers can connect to this port.
     */
    public boolean isConnectionManagerPort() {
        return type == Type.connectionManager;
    }

    public Type getType() {
        return type;
    }

    public static enum Type {
        client,

        server,

        component,

        connectionManager
    }
}
