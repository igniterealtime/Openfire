/**
 * $RCSfile$
 * $Revision: 1379 $
 * $Date: 2005-05-23 15:38:09 -0300 (Mon, 23 May 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.net;

import org.jivesoftware.util.*;
import org.jivesoftware.openfire.ServerPort;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.XMPPServerInfo;
import org.jivesoftware.openfire.container.BasicModule;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.util.Map;
import java.util.TimerTask;

/**
 * Publishes Openfire information as a service using the Multicast DNS (marketed by Apple
 * as Rendezvous) protocol. This lets other nodes on the local network to discover
 * the name and port of Openfire.<p>
 *
 * The multicast DNS entries published:<ul>
 *  <li>Client connections: type of "_xmpp-client._tcp.local.".
 *  <li>Component connections: type of "_xmpp-component._tcp.local.".
 * </ul>
 *
 * @author Matt Tucker
 */
public class MulticastDNSService extends BasicModule {

    private JmDNS jmdns;

    public MulticastDNSService() {
        super("Multicast DNS Service");

        PropertyEventDispatcher.addListener(new PropertyEventListener() {

            public void propertySet(String property, Map params) {
                // Restart the service if component settings changes.
                if (property.equals("xmpp.component.socket.active") ||
                        property.equals(" xmpp.component.socket.port"))
                {
                    stop();
                    start();
                }
            }

            public void propertyDeleted(String property, Map params) {
                // Restart the service if component settings changes.
                if (property.equals("xmpp.component.socket.active") ||
                        property.equals(" xmpp.component.socket.port"))
                {
                    stop();
                    start();
                }
            }

            public void xmlPropertySet(String property, Map params) {
            }

            public void xmlPropertyDeleted(String property, Map params) {
            }
        });
    }

    public void initialize(XMPPServer server) {
       
    }

    public void start() throws IllegalStateException {
        // If the service isn't enabled, return.
        if (!JiveGlobals.getBooleanProperty("multicastDNS.enabled", false) ) {
            return;     
        }
        TimerTask startService = new TimerTask() {
            public void run() {
                XMPPServerInfo info = XMPPServer.getInstance().getServerInfo();
                int clientPortNum = -1;
                int componentPortNum = -1;
                for (ServerPort port : info.getServerPorts()) {
                    if (port.isClientPort()) {
                        clientPortNum = port.getPort();
                    }
                    else if (port.isComponentPort()) {
                        componentPortNum = port.getPort();
                    }
                }
                try {
                    if (jmdns == null) {
                        jmdns = new JmDNS();
                    }
                    String serverName = XMPPServer.getInstance().getServerInfo().getXMPPDomain();

                    if (clientPortNum != -1) {
                        ServiceInfo clientService = new ServiceInfo("_xmpp-client._tcp.local.",
                                serverName + "._xmpp-client._tcp.local.", clientPortNum, "XMPP Server");
                        jmdns.registerService(clientService);
                    }
                    if (componentPortNum != -1) {
                        ServiceInfo componentService = new ServiceInfo("_xmpp-component._tcp.local.",
                                serverName +  "._xmpp-component._tcp.local.", componentPortNum, "XMPP Component Server");
                        jmdns.registerService(componentService);
                    }
                }
                 catch (IOException ioe) {
                    Log.error(ioe);
                }
            }
        };
        // Schedule the task to run in 5 seconds, to give Wildire time to start the ports. 
        TaskEngine.getInstance().schedule(startService, 5000);
    }


    public void stop() {
        if (jmdns != null) {
            try {
                jmdns.close();
            }
            catch (Exception e) {
                // Ignore.
            }
        }
    }

    public void destroy() {
        if (jmdns != null) {
            jmdns = null;
        }
    }
}