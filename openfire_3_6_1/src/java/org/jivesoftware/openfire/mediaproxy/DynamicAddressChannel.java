/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.mediaproxy;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Listen packets from defined dataSocket and send packets to the defined host.
 * But also provides a mechanism to dynamic bind host and port implementing DatagramListener methods to
 * change the host and port values according to the received packets.
 *
 * @author Thiago Camargo
 */
public class DynamicAddressChannel extends Channel implements Runnable, DatagramListener {
    private int c = 0;

    /**
     * Default Channel Constructor
     *
     * @param dataSocket datasocket to used to send and receive packets
     * @param host       default destination host for received packets
     * @param port       default destination port for received packets
     */
    public DynamicAddressChannel(DatagramSocket dataSocket, InetAddress host, int port) {
        super(dataSocket, host, port);
    }

    boolean handle(DatagramPacket packet) {
        // Relay Destination
        if (c++ < 100) { // 100 packets are enough to discover relay address
            this.setHost(packet.getAddress());
            this.setPort(packet.getPort());
            return true;
        } else {
            c = 1000; // Prevents long overflow
            // Check Source Address. If it's different, discard packet.
            return this.getHost().equals(packet.getAddress());
        }
    }

    /**
     * Implement DatagramListener method.
     * Set the host and port value to the host and port value from the received packet.
     *
     * @param datagramPacket the received packet
     */
    public boolean datagramReceived(DatagramPacket datagramPacket) {
        this.relayPacket(datagramPacket);
        return false;
    }
}