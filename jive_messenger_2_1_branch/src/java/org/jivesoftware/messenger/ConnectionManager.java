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

import java.net.Socket;
import java.util.Iterator;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Coordinates connections (accept, read, termination) on the server.
 *
 * @author Iain Shigeoka
 */
public interface ConnectionManager {

    /**
     * Returns an array of the ports managed by this connection manager.
     *
     * @return an iterator of the ports managed by this connection manager
     *      (can be an empty but never null).
     */
    public Iterator<ServerPort> getPorts();

    /**
     * Adds a socket to be managed by the connection manager.
     *
     * @param socket the socket to add to this manager for management.
     * @param isSecure true if the connection is secure.
     */
    public void addSocket(Socket socket, boolean isSecure) throws XmlPullParserException;
}
