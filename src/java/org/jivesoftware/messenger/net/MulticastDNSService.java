/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.net;

import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.XMPPServer;
import org.jivesoftware.messenger.XMPPServerInfo;
import org.jivesoftware.messenger.ServerPort;
import org.jivesoftware.util.Log;
import org.jivesoftware.admin.AdminConsole;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.util.Iterator;

/**
 * Publishes Jive Messenger as a service using the Multicast DNS (marketed by Apple
 * as Rendezvous) protocol. This lets other nodes on the local network to discover
 * the name and port of Jive Messenger.<p>
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