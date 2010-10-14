/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

    @Override
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