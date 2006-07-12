/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway;

import org.xmpp.component.ComponentException;
import org.xmpp.packet.Packet;

/**
 * An endpoint represents a server or gateway and can forward the message to the
 * underlying implementation, providing translation if necessary.
 * 
 * @author Noah Campbell
 * @version 1.0
 */
public interface Endpoint {

    /**
     * Send a packet to the underlying messaging services
     * 
     * @param packet
     * @throws ComponentException
     */
    public void sendPacket(Packet packet) throws ComponentException;

    /**
     * Return the <code>EndpointValve</code>.  This provides the ability of the
     * caller to open or close the valve to control the follow of packets to the
     * destination.
     * 
     * @return valve The <code>EndpointValve</code> associated with this <code>Endpoint</code>
     */
    public EndpointValve getValve();

}
