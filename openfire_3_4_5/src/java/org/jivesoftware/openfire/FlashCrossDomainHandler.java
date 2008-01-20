/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire;

import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;

import java.io.IOException;
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
            Socket clientSocket;
            try {
                clientSocket = serverSocket.accept();

                // Validate that we have a license
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

                out.println(CROSS_DOMAIN_TEXT +
                        XMPPServer.getInstance().getConnectionManager().getClientListenerPort() +
                        CROSS_DOMAIN_END_TEXT);
                out.println("\n");
                out.flush();
                out.close();
            }
            catch (IOException e) {
                if (XMPPServer.getInstance().isShuttingDown()) {
                    break;
                }
                Log.error(e);
            }
        }
    }
}
