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

package org.jivesoftware.net;

import org.jivesoftware.net.AcceptPolicy;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * <p>Represents and manages an network accept port.</p>
 *
 * <p>Accept ports know what port/address to bind to, have and verify
 * their accept policies, and allow a generic mechanism for opening
 * and closing ports for accepting connections. The accept port
 * implementation accepts incoming connections but never reads from them.</p>
 *
 * @author Iain Shigeoka
 */
public interface AcceptPort {

    /**
     * <p>Determines if this accept port generates natively secure connections.</p>
     *
     * @return True if the port is secure (e.g. SSL/TLS)
     */
    boolean isSecure();

    /**
     * <p>Sets the security status of this connection.</p>
     *
     * <p>Essentially determines if an SSLServerSocket should be used.
     * Secure accept ports natively create SSL/TLS secured sockets.
     * However, since many protocols support SASL, connections that
     * are not created securely, can be later secured by negotiating
     * TLS.</p>
     *
     * @param secure True if the port is secure (e.g. SSL/TLS)
     */
    void setSecure(boolean secure);

    /**
     * <p>Obtain the inet address that will be used.</p>
     *
     * <p>The inet address can be either a particular address representing
     * a network interface the accept port binds to, or null if any/all
     * interfaces should be used. Although the inet address becomes set
     * once the underlying server socket is bound, this method will continue
     * to return null if the target is any/all. Use the Connections produced
     * by the accept port to determine the local interface any particular
     * connection is attached to.</p>
     *
     * @return The inet address this port is bound to, or null if any/all
     */
    InetSocketAddress getInetSocketAddress();

    /**
     * <p>Sets the inet address that will be used.</p>
     *
     * <p>The inet address can be either a particular address representing
     * a network interface the accept port binds to, or null if any/all
     * interfaces should be used. Although the inet address becomes set
     * once the underlying server socket is bound, this method will continue
     * to return null if the target is any/all. Use the Connections produced
     * by the accept port to determine the local interface any particular
     * connection is attached to.</p>
     *
     * <p>If the port is open the port will automatically be
     * closed before changing the address. The port will
     * NOT be automatically re-opened once the address is changed.</p>
     *
     * @param address The inet address this port is bound to, or null if any/all
     */
    void setInetSocketAddress(InetSocketAddress address);

    /**
     * <p>Returns the number of milliseconds the accept port has been open
     * or -1 if the accept port is closed.</p>
     *
     * @return The number of milliseconds the accept port has been open or -1 if closed
     */
    long getUptime();

    /**
     * <p>Binds and opens the accept port for accepting new connections.</p>
     *
     * @throws IOException If the the port is already bound or otherwise failed to open
     * @throws SecurityException If the security manager refuses the operation
     * @throws IllegalArgumentException If the InetAddress is not valid on this machine
     */
    void open()
            throws IOException, SecurityException, IllegalArgumentException;

    /**
     * <p>Closes the accept port and no longer accept connections.</p>
     *
     * @throws IOException If the the port could not be closed
     */
    void close() throws IOException;

    /**
     * <p>Closes the AcceptPort and removes it from service.</p>
     *
     * @throws IOException If there was a problem closing the connection
     */
    void remove() throws IOException;

    /**
     * <p>The accept policy specific to this accept port.</p>
     *
     * <p>Incoming accept requests are evaluated with the AcceptPort's
     * AcceptPolicy. The AcceptManager only checks this accept policy
     * when deciding whether to connect or reject incoming sockets.
     * Most accept port specific accept policies should use the
     * results of the global AcceptManager AcceptPolicy
     * in determining the overall accept response.</p>
     *
     * @return The accept policy for the accept port
     */
    AcceptPolicy getAcceptPolicy();

    /**
     * <p>Obtain the monitor watching the basic behavior of this AcceptPort.</p>
     *
     * <p>Counts the raw connections accepted before the accept policy is applied
     * and the sockets are added to the ConnectionManager or they are disconnected
     * because they fail to pass the accept policy.</p>
     *
     * @return The accept monitor watching this accept port
     */
    ConnectionMonitor getAcceptMonitor();

    /**
     * <p>Obtain the monitor watching the accepted connection
     * behavior of this AcceptPort.</p>
     *
     * @return The connection monitor watching this accept port
     */
    ConnectionMonitor getConnectMonitor();

    /**
     * <p>Obtain the monitor watching the rejected connection
     * behavior of this AcceptPort.</p>
     *
     * @return The connection monitor watching this accept port
     */
    ConnectionMonitor getDisconnectMonitor();
}
