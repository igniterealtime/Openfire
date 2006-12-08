/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway.web;

import org.jivesoftware.wildfire.container.PluginManager;
import org.jivesoftware.wildfire.XMPPServer;
import org.jivesoftware.wildfire.gateway.GatewayPlugin;

/**
 * Transport Instance Manager
 *
 * Handles web interface interactions with the transport instances, such as enabling or
 * disabling them, configuring options, etc.
 *
 * @author Daniel Henninger
 */
public class TransportInstanceManager {

    /**
     * Toggles whether a transport is enabled or disabled.
     *
     * @param transportName Name of the transport to be enabled or disabled (type of transport)
     * @return True or false if the transport is enabled after this call.
     */
    public boolean toggleTransport(String transportName) {
        PluginManager pluginManager = XMPPServer.getInstance().getPluginManager();
        GatewayPlugin plugin = (GatewayPlugin)pluginManager.getPlugin("gateway");
        if (!plugin.serviceEnabled(transportName)) {
            plugin.enableService(transportName);
            return true;
        }
        else {
            plugin.disableService(transportName);
            return false;
        }
    }
    
}
