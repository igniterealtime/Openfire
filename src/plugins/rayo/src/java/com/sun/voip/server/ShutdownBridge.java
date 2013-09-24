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

import java.io.IOException;
import java.net.Socket;
import java.net.InetAddress;

/**
 * This class tells the Bridge server to shutdown
 *
 * and initiates calls as specified by the clients' request.
 */
public class ShutdownBridge {

    public static void main(String[] args) {
	String serverName = null;

        if (args.length == 1) {
            serverName = args[0];
        } else {
            try {
                serverName = InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                Logger.error(
		    "ShudownBridge:  Can't get Local Host's IP Address!  "
                    + e.getMessage());
                System.exit(0);
            }
        }

	int serverPort = Integer.getInteger(
	    "com.sun.voip.server.BRIDGE_SERVER_PORT", 6666).intValue();

	try {
	    Logger.println("Connecting to " + serverName + ":" + serverPort);

	    Socket socket = new Socket(serverName, serverPort);

	    String request = new String("SHUTDOWN\n\n");
	    socket.getOutputStream().write(request.getBytes());
	    socket.getOutputStream().flush();
	    Thread.sleep(1000);	  // give command a chance to happen
	} catch (IOException e) {
	} catch (Exception e) {
	    System.err.println("ShutdownBridge:  Can't create socket " 
		+ serverName + ":" + serverPort + e.getMessage());
	    return;
	}
    }

}
