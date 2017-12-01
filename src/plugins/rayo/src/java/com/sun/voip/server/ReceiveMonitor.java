/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of jVoiceBridge.
 *
 * jVoiceBridge is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License version 2 as 
 * published by the Free Software Foundation and distributed hereunder 
 * to you.
 *
 * jVoiceBridge is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the License file that accompanied this 
 * code. 
 */

package com.sun.voip.server;

import com.sun.voip.Logger;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * Receive Monitor
 *
 * Listen on a TCP socket for connections.
 *
 * Send receive statistics.
 */
public class ReceiveMonitor extends Thread {

    public static final int RECEIVE_MONITOR_PORT = 7777;

    private ServerSocket serverSocket;

    public ReceiveMonitor() throws IOException {
    Logger.init();

    String s = System.getProperty("com.sun.voip.server.RECEIVE_MONITOR_PORT");

    int port = RECEIVE_MONITOR_PORT;

    if (s != null) {
        try {
        port = Integer.parseInt(s);	
        } catch (NumberFormatException e) {
        Logger.println("Invalid ReceiveMonitor port:  " 
            + e.getMessage() + ".  Defaulting to " + port);
        }
    }

    serverSocket = new ServerSocket(port);

    start();
    }

    public void run() {
    while (true) {
        Socket socket;

        try {
        socket = serverSocket.accept(); // wait for a connection
        } catch (IOException e) {
        Logger.println("accept failed:  " + e.getMessage());
        break;
        }

        InetAddress inetAddress = socket.getInetAddress();

        String host;

        try {
            host = inetAddress.getHostName();
        } catch (Exception e) {
            host = inetAddress.toString();
        }

        Logger.println("New connection accepted from " 
            + host + ":" + socket.getPort());

        try {
            new Monitor(socket);
        } catch (IOException e) {
        Logger.println("Unable to create Monitor:  " 
            + e.getMessage());
        }
    } 
    }

    class Monitor extends Thread {

    private Socket socket;

    private BufferedReader reader;
    private DataOutputStream output;

    public Monitor(Socket socket) throws IOException {
        this.socket = socket;

        reader = new BufferedReader(
        new InputStreamReader(socket.getInputStream()));

            output = new DataOutputStream(socket.getOutputStream());

        start();
    }

    public void run() {
        String callId;

        try {
        callId = reader.readLine();
        } catch (IOException e) {
        Logger.error("unable to read line from " + socket
            + e.getMessage());
        close();
        return;
        }

        if (callId == null) {
        close();
        return;
        }

        CallHandler callHandler = CallHandler.findCall(callId);

        if (callHandler == null) {
        try {
            Logger.println("Invalid callId:  " + callId);
                write("Invalid callId:  " + callId);
        } catch (IOException e) {
        }

        close();
        return;
        }

        MemberReceiver memberReceiver = 
        callHandler.getMember().getMemberReceiver();

        while (true) {
        try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                }

        String s;

        try {
            s = memberReceiver.getPerformanceData();
        } catch (IOException e) {
            try {
            write("CallEnded");
            } catch (IOException ee) {
            }
            break;
        }

        try {
            write(s);
        } catch (IOException e) {
            break;
        }
        }
    }

    private void close() {
        try {
        socket.close();
        } catch (IOException e) {
        }
    }

    private void write(String s) throws IOException {
        s += "\n";

        try {
            output.write(s.getBytes());
        } catch (IOException e) {
        Logger.error("unable to write to " + socket
            + e.getMessage());
        close();
        throw e;
        }
    }
    }

}
