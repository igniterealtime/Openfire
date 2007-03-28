/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006-2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.gateway;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.PropertyEventListener;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.xmpp.component.ComponentManager;
import java.util.Map;

/**
 * Transport Instance
 *
 * Represents all information that needs to be tracked about a gateway instance.
 *
 * @author Daniel Henninger
 */
public class TransportInstance implements PropertyEventListener {

    private ComponentManager componentManager;
    private String description = null;
    private String nameOfClass = null;
    public BaseTransport transport = null;
    private TransportType type = null;
    private Boolean enabled = false;
    private Boolean running = false;
    private String subDomain;

    /**
     *  Creates a new transport instance.
     *
     *  @param type Type of transport.
     *  @param description Short description of transport.
     *  @param classname Full name/path of class associated with instance.
     *  @param componentManager Component manager managing this instance.
     */
    public TransportInstance(TransportType type, String description, String classname, ComponentManager componentManager) {
        this.description = description;
        this.type = type;
        this.nameOfClass = classname;
        this.componentManager = componentManager;
        enabled = JiveGlobals.getBooleanProperty("plugin.gateway."+this.type.toString()+".enabled", false);
        subDomain = JiveGlobals.getProperty("plugin.gateway."+this.type.toString()+".subdomain", this.type.toString());
    }

    /**
     *  Retrieves the name of the service (aka, subdomain)
     *
     *  @return name of the service
     */
    public String getName() {
        return this.type.toString();
    }

    /**
     *  Returns whether this transport instance is enabled.
     *
     *  @return true or false if instance is enabled
     */
    public Boolean isEnabled() {
        return enabled;
    }

    /**
     *  Returns whether this transport instance is currently running.
     *
     *  @return true or false if instance is currently running
     */
    public Boolean isRunning() {
        return running;
    }

    /**
     *  Enables the transport instance and starts it if it's not already running.
     */
    public void enable() {
        enabled = true;
        JiveGlobals.setProperty("plugin.gateway."+this.type.toString()+".enabled", "true");
        if (!running) {
            startInstance();
        }
    }

    /**
     *  Disables the transport instance and stops it if it's running.
     */
    public void disable() {
        enabled = false;
        JiveGlobals.setProperty("plugin.gateway."+this.type.toString()+".enabled", "false");
        if (running) {
            stopInstance();
        }
    }

    /**
     *  Starts the transport instance if it's enabled and not already running.
     */
    public void startInstance() {
        if (!enabled || running) {
            return;
        }

        Log.info("Starting transport service: "+type.toString());

        transport = null;

        //Log.debug("Loading class "+nameOfClass);

        try {
            transport = (BaseTransport)Class.forName(nameOfClass).newInstance();
            transport.setup(this.type, this.description);
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

        try {
            componentManager.addComponent(this.subDomain, transport);
            PropertyEventDispatcher.addListener(this);
            running = true;
        }
        catch (Exception e) {
            componentManager.getLog().error(e);
        }
    }

    /**
     *  Stops the transport instance if it's running.
     */
    public void stopInstance() {
        if (!running) {
            return;
        }

        Log.info("Stopping transport service: "+type.toString());

        PropertyEventDispatcher.removeListener(this);
        try {
            componentManager.removeComponent(this.subDomain);
        }
        catch (Exception e) {
            componentManager.getLog().error(e);
        }
        transport = null;
        running = false;
    }

    /**
     * Retrieves actual transport associated with this instance.
     *
     * @return Transport that the instance is associated with.
     */
    public BaseTransport getTransport() {
        return transport;
    }

    public void propertySet(String property, Map params) {
        if (property.startsWith("plugin.gateway.")) {
            if (property.equals("plugin.gateway."+this.type.toString()+".enabled")) {
                enabled = Boolean.parseBoolean((String)params.get("value"));
                if (enabled) {
                    if (!running) {
                        startInstance();
                    }
                }
                else {
                    if (running) {
                        stopInstance();
                    }
                }
            }
            else if (property.equals("plugin.gateway."+this.type.toString()+".subdomain")) {
                String newSubDomain = (String)params.get("value");
                if (!newSubDomain.equals(this.subDomain)) {
                    if (running) {
                        stopInstance();
                        this.subDomain = newSubDomain;
                        startInstance();
                    }
                }
            }
        }
    }

    public void propertyDeleted(String property, Map params) {
        if (property.startsWith("plugin.gateway.")) {
            if (property.equals("plugin.gateway."+this.type.toString()+".enabled")) {
                if (running) {
                    stopInstance();
                }
            }
            else if (property.equals("plugin.gateway."+this.type.toString()+".subdomain")) {
                String newSubDomain = this.type.toString();
                if (!newSubDomain.equals(this.subDomain)) {
                    if (running) {
                        stopInstance();
                        this.subDomain = newSubDomain;
                        startInstance();
                    }
                }
            }
        }
    }

    public void xmlPropertySet(String property, Map params) {
        propertySet(property, params);
    }

    public void xmlPropertyDeleted(String property, Map params) {
        propertyDeleted(property, params);
    }

}
