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

import java.net.InetSocketAddress;
import java.util.Iterator;

import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.util.AlreadyExistsException;
import org.jivesoftware.util.BasicResultFilter;

/**
 * <p>Manages the server sockets that accept incoming connections.</p>
 *
 * <p>The AcceptManager is responsible for managing AcceptPorts, enforcing
 * any AcceptPolicies that are in place, and passing accepted connections
 * to the ConnectionManager for management.</p>
 *
 * @author Iain Shigeoka
 */
public interface AcceptManager {

    /**
     * <p>Obtain the global accept policy that should be applied to
     * sockets.</p>
     *
     * <p>Connections are accepted or declined by applying this global
     * accept policy, then the accept policy for the accept port
     * the connection was made. The accept port specific policy (if one
     * exists) always overrides the global policy. Thus it is safest to
     * deny access to all incoming connections that could be remotely
     * considered dangerous in the global policy, and then allow
     * the appropriate ones on an per-AcceptPort basis.</p>
     *
     * @return the global accept policy maintained by the accept manager.
     */
    AcceptPolicy getGlobalAcceptPolicy();

    /**
     * <p>Obtain the number of accept ports being managed.</p>
     *
     * @return the number of accept ports managed.
     */
    int getAcceptPortCount();

    /**
     * <p>Obtain an iterator over all the AcceptPorts being managed.</p>
     *
     * @return an iterator of accept ports managed.
     */
    Iterator getAcceptPorts();

    /**
     * <p>Obtain an iterator over the AcceptPorts being managed that
     * comply with the given result filter.</p>
     *
     * @param filter The filter to apply to the port list results
     * @return An iterator of accept ports managed after filtering
     */
    Iterator getAcceptPorts(BasicResultFilter filter);

    /**
     * <p>Obtains an accept port by address and port.</p>
     *
     * <p>If the portAddress is null, and there is more than one
     * accept port bound to the same port (e.g. multi-homed hosts
     * with different accept ports on each interface), one of
     * the matching ports is returned. There is no guarantee
     * which will be returned, or if the same or different ports
     * will be returned on subsequent calls. If it is important
     * to obtain all accept ports bound to a particular port, on any
     * interface, it is better to search the list of accept ports
     * using the getAcceptPorts() method.
     *
     * @param portAddress The address of the accept port to remove or null
     * for any/all addresses.
     * @throws NotFoundException If no matching accept port was found
     */
    AcceptPort getAcceptPort(InetSocketAddress portAddress)
            throws NotFoundException;

    /**
     * <p>Creates a new accept port that will be bound to the given port
     * address.</p>
     *
     * <p>Creation of the accept port does not bind the port
     * or open it for accepting new connections. Adjust the
     * resulting AcceptPort instance in order to configure it's
     * initial setup.</p>
     *
     * @param portAddress The address to set the accept port on or null
     * to accept connections on any/all local addresses.
     * @return The created accept port
     * @throws AlreadyExistsException If an accept port already exists on
     * the given address and port
     */
    AcceptPort createAcceptPort(InetSocketAddress portAddress)
            throws AlreadyExistsException;

    /**
     * <p>Removes the given accept port.</p>
     *
     * @param acceptPort The accept port to remove
     */
    void deleteAcceptPort(AcceptPort acceptPort);
}
