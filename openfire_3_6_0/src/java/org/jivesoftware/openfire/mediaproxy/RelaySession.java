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

/**
 * A Session Class will control "receive and relay" proccess.
 * It creates UDP channels from Host A to Host B and from Host B to Host A using or NOT the specified
 * hosts and ports.
 * The IP and port pairs can change depending of the Senders IP and port.
 * Which means that the IP and port values of the points can dynamic change after the Channel is opened.
 * When the agent receives a packet from Point A, the channel set the point A IP and port according to the
 * received packet sender IP and port.
 * Every packet received from Point B will be relayed to the new Point A IP and port.
 * When the agent receives a packet from Point B, the channel set the point B IP and port according to the
 * received packet sender IP and port.
 * Every packet received from Point A will be relayed to the new Point B IP and port.
 * Create a dynamic channel between two IPs. ( Dynamic Point A - Dynamic Point B )
 * It has 4 Channels. 2 for data and 2 for control.
 *
 * @author Thiago Camargo
 */

public class RelaySession extends MediaProxySession {

    /**
     * Creates a new Smart Session to provide connectivity between Host A and Host B.
     *
     * @param id        of the Session (Could be a Jingle session ID)
     * @param localhost The localhost IP that will listen for UDP packets
     * @param hostA     the hostname or IP of the point A of the Channel
     * @param portA     the port number point A of the Channel
     * @param hostB     the hostname or IP of the point B of the Channel
     * @param portB     the port number point B of the Channel
     * @param creator   the created name or description of the Channel
     * @param minPort   the minimal port number to be used by the proxy
     * @param maxPort   the maximun port number to be used by the proxy
     */
    public RelaySession(String id, String creator, String localhost, String hostA, int portA, String hostB, int portB,
                        int minPort, int maxPort) {
        super(id, creator, localhost, hostA, portA, hostB, portB, minPort, maxPort);
    }

    /**
     * Creates a new Smart Session to provide connectivity between Host A and Host B.
     *
     * @param id        of the Session (Could be a Jingle session ID)
     * @param localhost The localhost IP that will listen for UDP packets
     * @param hostA     the hostname or IP of the point A of the Channel
     * @param portA     the port number point A of the Channel
     * @param hostB     the hostname or IP of the point B of the Channel
     * @param portB     the port number point B of the Channel
     * @param creator   the created name or description of the Channel
     */
    public RelaySession(String id, String creator, String localhost, String hostA, int portA, String hostB, int portB) {
        super(id, creator, localhost, hostA, portA, hostB, portB, 10000, 20000);
    }

    void createChannels() {
        channelAtoB = new DynamicAddressChannel(socketA, hostB, portB);
        channelAtoBControl = new DynamicAddressChannel(socketAControl, hostB, portB + 1);
        channelBtoA = new DynamicAddressChannel(socketB, hostA, portA);
        channelBtoAControl = new DynamicAddressChannel(socketBControl, hostA, portA + 1);
    }


    void addChannelListeners() {
        super.addChannelListeners();
        // Add channel as listeners
        channelAtoB.addListener((DynamicAddressChannel) channelBtoA);
        channelAtoBControl.addListener((DynamicAddressChannel) channelBtoAControl);
        channelBtoA.addListener((DynamicAddressChannel) channelAtoB);
        channelBtoAControl.addListener((DynamicAddressChannel) channelAtoBControl);
    }
}
