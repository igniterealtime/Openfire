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

package com.sun.voip.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;

import java.net.InetSocketAddress;
import java.net.Socket;

import java.util.Vector;

import com.sun.voip.CallEvent;
import com.sun.voip.CallEventListener;
import com.sun.voip.Logger;

/**
 * Connect to the bridge
 */
public class BridgeConnector extends Thread {

    private Socket socket;
    private OutputStream output;
    private BufferedReader bufferedReader;
    private CallEvent event;

    private boolean connected;

    public BridgeConnector() throws IOException {
        this(null, 0);
    }

    public BridgeConnector(String serverName, int serverPort) 
	    throws IOException {

	this(serverName, serverPort, 0);
    }

    public BridgeConnector(String serverName, int serverPort, int timeout) 
	    throws IOException {

	if (serverName == null) {
	    serverName = System.getProperty(
		"com.sun.voip.server.BRIDGE_SERVER_NAME", 
		"escher.east.sun.com");
	}

	if (serverPort == 0) {
	    serverPort = Integer.getInteger(
	        "com.sun.voip.server.Bridge.PORT", 6666).intValue();
	}

	InetSocketAddress isa = new InetSocketAddress(serverName, serverPort);

	if (isa.isUnresolved()) {
	    throw new IOException("BridgeConnector can't resolve hostname " 
		+ serverName);
	}

	Logger.println("Connecting to remote host " + serverName
	    + ", port " + serverPort);

	//
	// Open a tcp connection to the remote host at the well-known port.
	//
	socket = new Socket();

	socket.connect(isa, timeout);

        output = socket.getOutputStream();

        bufferedReader = new BufferedReader(
	    new InputStreamReader(socket.getInputStream()));

	start();

	synchronized(this) {
	    try {
		wait();
	    } catch(InterruptedException e) {
	    }
	}
    }

    public Socket getSocket() {
	return socket;
    }

    public void sendCommand(String command) throws IOException {
	if (socket == null || connected == false) {
	    throw new IOException("BridgeConnector:  not connected");
	}

	command += "\n";

	output.write(command.getBytes());
    }

    private Vector listeners = new Vector();

    public void addCallEventListener(CallEventListener listener) {
	synchronized(listeners) {
	    listeners.add(listener);
	}
    }

    public void removeCallEventListener(CallEventListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    private void callEventNotification(CallEvent event) {
	this.event = event;

	synchronized(this) {
	    notifyAll();
	}

	Logger.println(event.toString());

	synchronized(listeners) {
	    for (int i = 0; i < listeners.size(); i++) {
		CallEventListener listener = (CallEventListener)
		    listeners.get(i);

		listener.callEventNotification(event);
	    }
	}
    }

    private boolean done;

    public void done() {
	if (done) {
	    return;
	}

	done = true;

	if (socket != null) {
	    try {
	        socket.close();
	    } catch (IOException e) {
		Logger.println("Close failed for socket " + socket
		    + " " + e.getMessage());
	    }

	    socket = null;
	}
	
	if (bufferedReader != null) {
	    try {
	        bufferedReader.close();
	    } catch (IOException e) {
		Logger.println("Close failed for bufferedReader for socket " 
		    + socket + " " + e.getMessage());
	    }

	    bufferedReader = null;
	}
    }
	
    public void run() {
	connected = true;

	while (!done) {
	    String s = null;

	    try {
                s = bufferedReader.readLine();
	    } catch (IOException e) {
		if (done == false) {
	 	    System.err.println("can't read socket! " 
			+ socket + " " + e.getMessage());
		}
		break;
	    }

	    if (s == null && done == false) {
	 	Logger.println("can't read socket! " + socket);
		break;
	    }

	    callEventNotification(new CallEvent(s));
        }

	connected = false;
    }

    public String toString() {
	if (socket != null && connected) {
	    return socket.toString();
	}

	return "not connected";
    }

}
