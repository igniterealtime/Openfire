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

import org.jivesoftware.util.Log;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Listen packets from defined dataSocket and send packets to the defined host.
 *
 * @author Thiago Camargo
 */
abstract class Channel implements Runnable {
    protected byte[] buf = new byte[5000];
    protected DatagramSocket dataSocket;
    protected DatagramPacket packet;
    protected boolean enabled = true;

    List<DatagramListener> listeners = new ArrayList<DatagramListener>();

    protected InetAddress host;
    protected int port;

    /**
     * Creates a Channel according to the parameters.
     *
     * @param dataSocket
     * @param host
     * @param port
     */
    public Channel(DatagramSocket dataSocket, InetAddress host, int port) {
        this.dataSocket = dataSocket;
        this.host = host;
        this.port = port;
    }

    /**
     * Get the host that the packet will be sent to.
     *
     * @return remote host address
     */
    public InetAddress getHost() {
        return host;
    }

    /**
     * Set the host that the packet will be sent to.
     */
    protected void setHost(InetAddress host) {
        this.host = host;
    }

    /**
     * Get the port that the packet will be sent to.
     *
     * @return The remote port number
     */
    public int getPort() {
        return port;
    }

    /**
     * Set the port that the packet will be sent to.
     *
     * @param port
     */
    protected void setPort(int port) {
        this.port = port;
    }

    /**
     * Adds a DatagramListener to the Channel
     *
     * @param datagramListener
     */
    public void addListener(DatagramListener datagramListener) {
        listeners.add(datagramListener);
    }

    /**
     * Remove a DatagramListener from the Channel
     *
     * @param datagramListener
     */
    public void removeListener(DatagramListener datagramListener) {
        listeners.remove(datagramListener);
    }

    /**
     * Remove every Listeners
     */
    public void removeListeners() {
        listeners.removeAll(listeners);
    }

    public void cancel() {
        this.enabled = false;
        if (dataSocket != null){
            dataSocket.close();
        }
    }

    /**
     * Thread override method
     */
    public void run() {
        try {
            while (enabled) {
                // Block until a datagram appears:
                packet = new DatagramPacket(buf, buf.length);
                dataSocket.receive(packet);

                if (handle(packet)) {
                    boolean resend = true;

                    for (DatagramListener dl : listeners) {
                        boolean send = dl.datagramReceived(packet);
                        if (resend && !send) {
                            resend = false;
                        }
                    }

                    if (resend) {
                        relayPacket(packet);
                    }
                }
            }
        }
        catch (UnknownHostException uhe) {
            if (enabled) {
                Log.error("Unknown Host", uhe);
            }
        }
        catch (SocketException se) {
            if (enabled) {
                Log.error("Socket closed", se);
            }
        }
        catch (IOException ioe) {
            if (enabled) {
                Log.error("Communication error", ioe);
            }
        }
    }

    public void relayPacket(DatagramPacket packet) {
        try {
            DatagramPacket echo = new DatagramPacket(packet.getData(), packet.getLength(), host, port);
            dataSocket.send(echo);
        }
        catch (IOException e) {
            Log.error(e);
        }
    }

    /**
     * Handles received packet and returns true if the packet should be processed by the channel.
     *
     * @param packet received datagram packet
     * @return true if listeners will be alerted that a new packet was received.
     */
    abstract boolean handle(DatagramPacket packet);
}