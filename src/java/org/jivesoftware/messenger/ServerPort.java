/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger;

import java.util.Iterator;

/**
 * Represents a port on which the server will listen for connections.
 * Used to aggregate information that the rest of the system needs
 * regarding the port while hiding implementation details.
 *
 * @author Iain Shigeoka
 */
public interface ServerPort {
    /**
     * Obtain the port number that is being used
     *
     * @return The port number this server port is listening on
     */
    public int getPort();

    /**
     * Obtain the logical domains for this server port. As multiple
     * domains may point to the same server, this helps to define what
     * the server considers "local".
     *
     * @return The server domain name(s) as Strings
     */
    public Iterator getDomainNames();

    /**
     * Obtains the dot separated IP address for the server.
     *
     * @return The dot separated IP address for the server
     */
    public String getIPAddress();

    /**
     * Determines if the connection is secure.
     *
     * @return True if the connection is secure
     */
    public boolean isSecure();

    /**
     * Obtain the basic protocol/algorithm being used to secure
     * the port connections. An example would be "SSL" or "TLS".
     *
     * @return The protocol used or null if this is not a secure server port
     */
    public String getSecurityType();
}
