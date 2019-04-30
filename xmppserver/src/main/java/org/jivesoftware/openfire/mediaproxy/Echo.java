/*
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

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Echo implements Runnable {
    private static final Logger Log = LoggerFactory.getLogger(Echo.class);
    DatagramSocket socket = null;
    byte password[] = null;
    List<DatagramListener> listeners = new ArrayList<>();
    boolean enabled = true;

    public Echo(int port) throws UnknownHostException, SocketException {
        this.socket = new DatagramSocket(port, InetAddress.getByName("0.0.0.0"));
    }

    @Override
    public void run() {
        try {
            //System.out.println("Listening for ECHO: " + socket.getLocalAddress().getHostAddress() + ":" + socket.getLocalPort());
            while (true) {

                DatagramPacket packet = new DatagramPacket(new byte[8], 8);

                socket.receive(packet);

                System.out.println("ECHO Packet Received in: " + socket.getLocalAddress().getHostAddress() + ":" + socket.getLocalPort() + " From: " + packet.getAddress().getHostAddress() + ":" + packet.getPort());

                for (DatagramListener listener : listeners) {
                    try {
                        listener.datagramReceived(packet);
                    } catch (Exception e) {
                        Log.warn("An exception occurred while dispatching a 'datagramReceived' event!", e);
                    }
                }

                packet.setAddress(packet.getAddress());
                packet.setPort(packet.getPort());
                if (!Arrays.equals(packet.getData(), password))
                    for (int i = 0; i < 3; i++)
                        socket.send(packet);
            }
        } catch (IOException ioe) {
            if (enabled) {
            }
        }
    }

    public void cancel() {
        this.enabled = false;
        socket.close();
    }
}
