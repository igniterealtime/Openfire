/**
 * $RCSfile$
 * $Revision: 1379 $
 * $Date: 2005-05-23 15:38:09 -0300 (Mon, 23 May 2005) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.net;

import org.jivesoftware.wildfire.container.BasicModule;
import org.jivesoftware.wildfire.XMPPServer;
import org.jivesoftware.wildfire.XMPPServerInfo;
import org.jivesoftware.wildfire.ServerPort;
import org.jivesoftware.util.Log;
import org.jivesoftware.admin.AdminConsole;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.util.Iterator;

/**
 * Publishes Wildfire as a service using the Multicast DNS (marketed by Apple
 * as Rendezvous) protocol. This lets other nodes on the local network to discover
 * the name and port of Wildfire.<p>
 *
 * The multicast DNS entry is published with a type of "_xmpp-client._tcp.local.".
 *
 * @author Matt Tucker
 */
public class MulticastDNSService extends BasicModule {

    private JmDNS jmdns;
    private ServiceInfo serviceInfo;

    public MulticastDNSService() {
        super("Multicast DNS Service");
    }

    public void initialize(XMPPServer server) {
       
    }

    public void start() throws IllegalStateException {
        if (jmdns != null) {
            Runnable startService = new Runnable() {
                public void run() {
                    XMPPServerInfo info = XMPPServer.getInstance().getServerInfo();
                    Iterator ports = info.getServerPorts();
                    int portNum = -1;
                    while (ports.hasNext()) {
                        ServerPort port = (ServerPort)ports.next();
                        if (port.isClientPort() && !port.isSecure()) {
                            portNum = port.getPort();
                        }
                    }
                    try {
                        if (jmdns == null) {
                            jmdns = new JmDNS();
                        }
                        if (portNum != -1) {
                            String serverName = AdminConsole.getAppName();
                            serviceInfo = new ServiceInfo("_xmpp-client._tcp.local.",
                                    serverName, portNum, 0, 0, "XMPP Server");
                            jmdns.registerService(serviceInfo);
                        }
                    }
                     catch (IOException ioe) {
                        Log.error(ioe);
                    }
                }
            };
            new Thread(startService).start();
        }
    }


    public void stop() {
        if (jmdns != null) {
            try {
                jmdns.close();
            }
            catch (Exception e) { }
        }
    }

    public void destroy() {
        if (jmdns != null) {
            jmdns = null;
        }
    }
}