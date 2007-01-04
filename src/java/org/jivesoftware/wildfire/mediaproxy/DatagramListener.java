package org.jivesoftware.wildfire.mediaproxy;

import java.net.DatagramPacket;

/**
 * Interface to listener datagramReceived events
 */
public interface DatagramListener {
    /**
     * Called when a datagram is received.
     * If method return false the packet MUST NOT be resent from the received Channel.
     * @param datagramPacket The received datagram
     */
    public boolean datagramReceived(DatagramPacket datagramPacket);
}
