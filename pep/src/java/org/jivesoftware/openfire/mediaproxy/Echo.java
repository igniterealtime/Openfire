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

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Echo implements Runnable {

    DatagramSocket socket = null;
    byte password[] = null;
    List<DatagramListener> listeners = new ArrayList<DatagramListener>();
    boolean enabled = true;

    public Echo(int port) throws UnknownHostException, SocketException {
        this.socket = new DatagramSocket(port, InetAddress.getByName("0.0.0.0"));
    }

    public void run() {
        try {
            //System.out.println("Listening for ECHO: " + socket.getLocalAddress().getHostAddress() + ":" + socket.getLocalPort());
            while (true) {

                DatagramPacket packet = new DatagramPacket(new byte[8], 8);

                socket.receive(packet);

                System.out.println("ECHO Packet Received in: " + socket.getLocalAddress().getHostAddress() + ":" + socket.getLocalPort() + " From: " + packet.getAddress().getHostAddress() + ":" + packet.getPort());

                for (DatagramListener listener : listeners) {
                    listener.datagramReceived(packet);
                }

                packet.setAddress(packet.getAddress());
                packet.setPort(packet.getPort());
                if (!Arrays.equals(packet.getData(), password))
                    for (int i = 0; i < 3; i++)
                        socket.send(packet);
            }
        }
        catch (UnknownHostException uhe) {
            if (enabled) {
            }
        }
        catch (SocketException se) {
            if (enabled) {
            }
        }
        catch (IOException ioe) {
            if (enabled) {
            }
        }
    }

    public void cancel() {
        this.enabled = false;
        socket.close();
    }
}
