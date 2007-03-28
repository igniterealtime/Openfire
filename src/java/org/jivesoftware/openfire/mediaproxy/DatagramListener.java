/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.mediaproxy;

import java.net.DatagramPacket;

/**
 * Listener for datagram packets received.
 *
 * @author Thiago Camargo
 */
public interface DatagramListener {

    /**
     * Called when a datagram is received. If the method returns false, the
     * packet MUST NOT be resent from the received Channel.
     *
     * @param datagramPacket the datagram packet received.
     * @return ?
     */
    public boolean datagramReceived(DatagramPacket datagramPacket);
}