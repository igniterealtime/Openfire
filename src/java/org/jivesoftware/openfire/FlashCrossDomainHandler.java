/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire;

import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;

import com.openbase.jdbc.i;

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
        try {
            // Listen on a specific network interface if it has been set.
            String interfaceName = JiveGlobals.getXMLProperty("network.interface");
            InetAddress bindInterface = null;
            int port = 5229;
            if (interfaceName != null) {
                if (interfaceName.trim().length() > 0) {
                    bindInterface = InetAddress.getByName(interfaceName);
                }
            }
            serverSocket = new ServerSocket(port, -1, bindInterface);
            Log.debug("Flash cross domain is listening on " + interfaceName + " on port " + port);
        }
        catch (IOException e) {
            Log.error("Could not listen on port: 5229.", e);
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
