/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway.util;

import org.jivesoftware.util.Log;
import org.xmpp.component.ComponentManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.wildfire.gateway.BaseGateway;
import org.jivesoftware.util.PropertyEventDispatcher;

/**
 * Gateway Instance
 *
 * Represents all information that needs to be tracked about a gateway instance.
 *
 * @author Daniel Henninger
 */
public class GatewayInstance {
    private ComponentManager componentManager;
    private String serviceName = null;
    private String nameOfClass = null;
    private BaseGateway gateway = null;
    private Boolean enabled = false;
    private Boolean running = false;

    public GatewayInstance(String subdomain, String classname, ComponentManager componentManager) {
        this.serviceName = subdomain;
        this.nameOfClass = classname;
        this.componentManager = componentManager;
        enabled = JiveGlobals.getBooleanProperty("plugin.gateway."+serviceName+"Enabled", false);
    }

    public String getName() {
        return serviceName;
    }

    public Boolean isEnabled() {
        return enabled;
    }

    public Boolean isRunning() {
        return running;
    }

    public void enable() {
        enabled = true;
        JiveGlobals.setProperty("plugin.gateway."+serviceName+"Enabled", "true");
        if (!running) {
            startInstance();
        }
    }

    public void disable() {
        enabled = false;
        JiveGlobals.setProperty("plugin.gateway."+serviceName+"Enabled", "false");
        if (running) {
            stopInstance();
        }
    }

    public void startInstance() {
        if (!enabled || running) {
            return;
        }

        BaseGateway gateway = null;

        Log.debug("Loading class "+nameOfClass);

        try {
            gateway = (BaseGateway)Class.forName(nameOfClass).newInstance();
        }
        catch (ClassNotFoundException e) {
            Log.error("Unable to find class: "+nameOfClass);
        }
        catch (InstantiationException e) {
            Log.error("Unable to instantiate class: "+nameOfClass);
        }
        catch (IllegalAccessException e) {
            Log.error("Unable to access class: "+nameOfClass);
        }
        gateway.setName(serviceName);

        //componentManager = ComponentManagerFactory.getComponentManager();
        try {
            componentManager.addComponent(serviceName, gateway);
            //PropertyEventDispatcher.addListener(gateway);
            running = true;
            Log.debug("Started class "+nameOfClass);
        }
        catch (Exception e) {
            componentManager.getLog().error(e);
        }
    }

    public void stopInstance() {
        if (!running) {
            return;
        }

        //PropertyEventDispatcher.removeListener(gateway);
        try {
            componentManager.removeComponent(serviceName);
            //componentManager = null;
        }
        catch (Exception e) {
            componentManager.getLog().error(e);
        }
        gateway = null;
        running = false;
    }
}
