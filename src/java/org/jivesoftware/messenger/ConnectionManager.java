/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger;

import java.net.Socket;
import java.util.Iterator;
import javax.xml.stream.XMLStreamException;

/**
 * <p>Coordinates connections (accept, read, termination) on the server.</p>
 *
 * @author Iain Shigeoka
 */
public interface ConnectionManager {
    /**
     * <p>Obtain an array of the ports managed by this connection manager.</p>
     *
     * @return Iterator of the ports managed by this connection manager (can be an empty but never null)
     */
    public Iterator getPorts();

    /**
     * <p>Adds a socket to be managed by the connection manager.</p>
     *
     * @param sock     The socket to add to this manager for management
     * @param isSecure True if this is a secure connection
     */
    public void addSocket(Socket sock, boolean isSecure) throws XMLStreamException;
}
