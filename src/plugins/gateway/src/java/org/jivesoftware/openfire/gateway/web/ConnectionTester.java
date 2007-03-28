/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006-2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.gateway.web;

import java.net.Socket;

/**
 * Connection Tester
 *
 * This class tests a connection with a legacy service (just simple open port tcp check).
 *
 * @author Daniel Henninger
 */
public class ConnectionTester {

    /**
     * Tests a tcp connection to a host and port.
     *
     * @param host Hostname (or ip address) to try to connect to.
     * @param port Port to try to connect to.
     * @return True or false if the connection succeeded.
     */
    public boolean testConnection(String host, String port) {
        try {
            Socket sock = new Socket(host, Integer.parseInt(port));
            sock.close();
            return true;
        }
        catch (Exception e) {
            return false;
        }
    }

    /**
     * Cheesy DWR 'ping' to make sure session stays alive.
     * Literally does nothing.
     */
    public void pingSession() {
    }

}
