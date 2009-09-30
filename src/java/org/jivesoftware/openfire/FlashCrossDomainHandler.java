/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
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

package org.jivesoftware.openfire;

import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class FlashCrossDomainHandler extends BasicModule {
    private ServerSocket serverSocket;

    public static String CROSS_DOMAIN_TEXT = "<?xml version=\"1.0\"?>" +
            "<!DOCTYPE cross-domain-policy SYSTEM \"http://www.macromedia.com/xml/dtds/cross-domain-policy.dtd\">" +
            "<cross-domain-policy>" +
            "<allow-access-from domain=\"*\" to-ports=\"";

    public static String CROSS_DOMAIN_END_TEXT = "\" /></cross-domain-policy>";

    public FlashCrossDomainHandler() {
        super("Flash CrossDomain Handler");
    }

    public void start() {
        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    startServer();
                }
                catch (Exception e) {
                    Log.error(e);
                }
            }
        }, "Flash Cross Domain");

        thread.start();
    }

    public void stop() {
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        }
        catch (IOException e) {
            Log.error(e);
        }
    }

    public int getPort() {
        return serverSocket != null ? serverSocket.getLocalPort() : 0;
    }

    private void startServer() throws Exception {
        if(!JiveGlobals.getBooleanProperty("flash.crossdomain.enabled",true)){
            Log.debug("Flash cross domain listener is disabled");
            return;
        }
        
        int port = JiveGlobals.getIntProperty("flash.crossdomain.port",5229);
        try {
            // Listen on a specific network interface if it has been set.
            String interfaceName = JiveGlobals.getXMLProperty("network.interface");
            InetAddress bindInterface = null;
            if (interfaceName != null) {
                if (interfaceName.trim().length() > 0) {
                    bindInterface = InetAddress.getByName(interfaceName);
                }
            }
            serverSocket = new ServerSocket(port, -1, bindInterface);
            Log.debug("Flash cross domain is listening on " + interfaceName + " on port " + port);
        }
        catch (IOException e) {
            Log.error("Could not listen on port: " + port, e);
            return;
        }

        while (true) {
            Socket clientSocket = null;
            PrintWriter out = null;
            BufferedReader in = null;
            try {
                clientSocket = serverSocket.accept();
                clientSocket.setSoTimeout(10000); // 10 second timeout

                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                
                String request = "";
            	request = read(in);
                
                if (request.contains("<policy-file-request/>") || request.contains("GET /crossdomain.xml")) {
	                out.write(CROSS_DOMAIN_TEXT +
	                        XMPPServer.getInstance().getConnectionManager().getClientListenerPort() +
	                        CROSS_DOMAIN_END_TEXT+"\u0000");
                }
            }
            catch (IOException e) {
                if (XMPPServer.getInstance().isShuttingDown()) {
                    break;
                }
                Log.error(e);
            }
            finally {
            	if (out != null) {
            		out.flush();
            		out.close();
            	}
            	if (in != null) {
            		in.close();
            	}
            	if (clientSocket != null) {
            		clientSocket.close();
            	}
            }
        }
    }
    
    /**
     * Safely read a string from the reader until a zero character or a newline is received o
r the 200 character is reached.
     *
     * @return the string read from the reader.
     */
    protected String read(BufferedReader in) {
        StringBuffer buffer = new StringBuffer();
        int codePoint;
        boolean zeroByteRead = false;
        
        try {
            do {
                codePoint = in.read();

                if (codePoint == 0 || codePoint == '\n') {
                    zeroByteRead = true;
                }
                else if (Character.isValidCodePoint(codePoint)) {
                    buffer.appendCodePoint(codePoint);
                }
            }
            while (!zeroByteRead && buffer.length() < 200);
        }
        catch (Exception e) {
            Log.debug("Exception (read): " + e.getMessage());
        }
        
        return buffer.toString();
    }
    
}
