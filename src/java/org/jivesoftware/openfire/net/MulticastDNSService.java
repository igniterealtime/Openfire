/*
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

import org.jivesoftware.openfire.ConnectionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;
import org.jivesoftware.util.TaskEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger Log = LoggerFactory.getLogger(MulticastDNSService.class);

    private JmDNS jmdns;

    public MulticastDNSService() {
        super("Multicast DNS Service");

        PropertyEventDispatcher.addListener(new PropertyEventListener() {

            @Override
            public void propertySet(String property, Map params) {
                // Restart the service if component settings changes.
                if (property.equals("xmpp.component.socket.active") ||
                        property.equals(" xmpp.component.socket.port"))
                {
                    stop();
                    start();
                }
            }

            @Override
            public void propertyDeleted(String property, Map params) {
                // Restart the service if component settings changes.
                if (property.equals("xmpp.component.socket.active") ||
                        property.equals(" xmpp.component.socket.port"))
                {
                    stop();
                    start();
                }
            }

            @Override
            public void xmlPropertySet(String property, Map params) {
            }

            @Override
            public void xmlPropertyDeleted(String property, Map params) {
            }
        });
    }

    @Override
    public void initialize(XMPPServer server) {
       
    }

    @Override
    public void start() throws IllegalStateException {
        // If the service isn't enabled, return.
        if (!JiveGlobals.getBooleanProperty("multicastDNS.enabled", false) ) {
            return;     
        }
        TimerTask startService = new TimerTask() {
            @Override
            public void run() {
                int clientPortNum = -1;
                int componentPortNum = -1;
                final ConnectionManager connectionManager = XMPPServer.getInstance().getConnectionManager();
                if ( connectionManager != null )
                {
                    clientPortNum = connectionManager.getClientListenerPort();
                    componentPortNum = connectionManager.getComponentListenerPort();
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
                    Log.error(ioe.getMessage(), ioe);
                }
            }
        };
        // Schedule the task to run in 5 seconds, to give Wildire time to start the ports. 
        TaskEngine.getInstance().schedule(startService, 5000);
    }


    @Override
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

    @Override
    public void destroy() {
        if (jmdns != null) {
            jmdns = null;
        }
    }
}
