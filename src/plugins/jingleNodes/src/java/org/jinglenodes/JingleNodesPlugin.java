/**
 * $Revision $
 * $Date $
 *
 * Copyright (C) 2005-2010 Jive Software. All rights reserved.
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

package org.jinglenodes;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.net.InetSocketAddress;

import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.component.ComponentException;
import org.xmpp.component.ComponentManager;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.jnodes.RelayChannel;
import org.xmpp.jnodes.nio.LocalIPResolver;
import org.xmpp.jnodes.nio.PublicIPResolver;

public class JingleNodesPlugin implements Plugin {

    private static final Logger Log = LoggerFactory.getLogger(JingleNodesPlugin.class);

    public static final String JN_LOCAL_IP_PROPERTY = "jinglenodes.localIP";
    public static final String JN_PUBLIC_IP_PROPERTY = "jinglenodes.publicIP";
    public static final String JN_MIN_PORT_PROPERTY = "jinglenodes.minPort";
    public static final String JN_MAX_PORT_PROPERTY = "jinglenodes.maxPort";
    public static final String JN_TEST_STUN_SERVER_PROPERTY = "jinglenodes.testSTUNServer";
    public static final String JN_TEST_STUN_PORT_PROPERTY = "jinglenodes.testSTUNPort";

    private ComponentManager componentManager;

    private final ConcurrentHashMap<String, RelayChannel> channels = new ConcurrentHashMap<String, RelayChannel>();
    private final long timeout = 60000;
    private final AtomicInteger ids = new AtomicInteger(0);
    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    private final String serviceName = "relay";

    private final boolean bindAllInterfaces;

    private boolean hasPublicIP = false;

    public JingleNodesPlugin() {
        final String os = System.getProperty("os.name");
        bindAllInterfaces = !(os != null && os.toLowerCase().indexOf("win") > -1);
    }

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        componentManager = ComponentManagerFactory.getComponentManager();
        JingleNodesComponent component = new JingleNodesComponent(this);
        try {
            componentManager.addComponent(serviceName, component);
        } catch (ComponentException e) {
            Log.error("Could NOT load " + component.getName());
        }
        setup();
    }

    private void setup() {
        executor.scheduleWithFixedDelay(new Runnable() {
            public void run() {
                for (final RelayChannel c : channels.values()) {
                    final long current = System.currentTimeMillis();
                    final long da = current - c.getLastReceivedTimeA();
                    final long db = current - c.getLastReceivedTimeB();

                    if (da > timeout || db > timeout) {
                        removeChannel(c);
                    }
                }
            }
        }, timeout, timeout, TimeUnit.MILLISECONDS);
        Log.info("Jingle Nodes Loaded.");

        verifyNetwork();
    }

    public void verifyNetwork() {
        final String localAddress = JiveGlobals.getProperty(JN_LOCAL_IP_PROPERTY, LocalIPResolver.getLocalIP());
        LocalIPResolver.setOverrideIp(localAddress);
        final InetSocketAddress publicAddress = PublicIPResolver.getPublicAddress(getTestSTUNServer(), getTestSTUNPort());
        hasPublicIP = publicAddress != null && publicAddress.getAddress().getHostAddress().equals(getPublicIP());

        // Provide a more detailed log message to help users reasonably debug this situation
        if (!hasPublicIP) {
            if (publicAddress != null) {
                Log.error("verifyNetwork Failed - public IP address from test STUN server [" + publicAddress.getAddress().getHostAddress() + "] does not match server configuration [" + getPublicIP() + "]");
            }
            else {
                Log.error("verifyNetwork Failed - failed to retrieve public address from test STUN server " + getTestSTUNServer() + ":" + Integer.toString(getTestSTUNPort()));
            }
        }
    }

    private void closeAllChannels() {
        for (final RelayChannel c : channels.values()) {
            removeChannel(c);
        }
    }

    public RelayChannel createRelayChannel() {
        RelayChannel rc = null;
        try {

            rc = RelayChannel.createLocalRelayChannel(bindAllInterfaces ? "0.0.0.0" : LocalIPResolver.getLocalIP(), getMinPort(), getMaxPort());
            final int id = ids.incrementAndGet();
            final String sId = String.valueOf(id);
            rc.setAttachment(sId);

            channels.put(sId, rc);
        } catch (IOException e) {
            Log.error("Could Not Create Channel.", e);
        }

        return rc;
    }

    private void removeChannel(final RelayChannel c) {
        channels.remove((String) c.getAttachment());
        c.close();
    }

    public String getServiceName() {
        return serviceName;
    }

    public void destroyPlugin() {
        try {
            componentManager.removeComponent(serviceName);
        } catch (ComponentException e) {
            Log.error("Could NOT Remove " + serviceName + " Component");
        }
        closeAllChannels();
        executor.shutdownNow();
    }

    public boolean hasPublicIP() {
        return hasPublicIP;
    }

    public int getActiveChannelCount() {
        return channels.size();
    }

    // Changed all previous hardcoded values to be properties that can be overridden
    public String getPublicIP() {
        return JiveGlobals.getProperty(JN_PUBLIC_IP_PROPERTY, LocalIPResolver.getLocalIP());
    }

    public int getMinPort() {
        return Integer.parseInt(JiveGlobals.getProperty(JN_MIN_PORT_PROPERTY, "30000"));
    }

    public int getMaxPort() {
        return Integer.parseInt(JiveGlobals.getProperty(JN_MAX_PORT_PROPERTY, "50000"));
    }

    public String getTestSTUNServer() {
        return JiveGlobals.getProperty(JN_TEST_STUN_SERVER_PROPERTY, "stun.l.google.com");
    }

    public int getTestSTUNPort() {
        return Integer.parseInt(JiveGlobals.getProperty(JN_TEST_STUN_PORT_PROPERTY, "19302"));
    }
}
