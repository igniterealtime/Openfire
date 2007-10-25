/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.sip;

import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.sip.log.LogComponent;
import org.jivesoftware.openfire.sip.log.LogListenerImpl;
import org.jivesoftware.openfire.sip.sipaccount.SipComponent;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;
import org.xmpp.component.ComponentManager;
import org.xmpp.component.ComponentManagerFactory;

import java.util.Map;
import java.io.File;

/**
 * Remote management for users SIP account for Spark SIP Plugin
 *
 * @author Thiago Rocha Camargo
 */
public class SipManager implements Plugin, PropertyEventListener {

    private String serviceName;

    private ComponentManager componentManager;

    private SipComponent sipComponent;

    private LogComponent logComponent;

    /**
     * Constructs a new SIP Controller plugin.
     */
    public SipManager() {
        serviceName = JiveGlobals.getProperty(SipComponent.PROPNAME,
                SipComponent.NAME);
    }

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        // Register as a component.
        componentManager = ComponentManagerFactory.getComponentManager();

        sipComponent = new SipComponent();
        LogListenerImpl logListener = new LogListenerImpl(componentManager);
        logComponent = new LogComponent(logListener);

        // Register the logger and SIP components. Both components are cluster-safe
        try {
            componentManager.addComponent(serviceName, sipComponent);

        } catch (Exception e) {
            componentManager.getLog().error(e);
        }
        try {
            componentManager.addComponent(LogComponent.NAME, logComponent);

        } catch (Exception e) {
            componentManager.getLog().error(e);
        }

        PropertyEventDispatcher.addListener(this);
        componentManager.getLog().debug("SIPARK STARTED");
    }

    public void destroyPlugin() {
        PropertyEventDispatcher.removeListener(this);
        // Unregister component.
        try {
            componentManager.removeComponent(serviceName);
        } catch (Exception e) {
            componentManager.getLog().error(e);
        }
        try {
            componentManager.removeComponent(LogComponent.NAME);
        } catch (Exception e) {
            componentManager.getLog().error(e);
        }
        sipComponent = null;
        logComponent = null;
        componentManager = null;
    }

    /**
     * Returns the service name of this component, which is "sipark" by default.
     *
     * @return the service name of this component.
     */
    public String getServiceName() {
        return serviceName;
    }

    /**
     * Sets the service name of this component, which is "sipark" by default.
     *
     * @param serviceName the service name of this component.
     */
    public void setServiceName(String serviceName) {
        JiveGlobals.setProperty(SipComponent.PROPNAME, serviceName);
    }

    /**
     * Changes the service name to a new value.
     *
     * @param serviceName the service name.
     */
    private void changeServiceName(String serviceName) {
        if (serviceName == null) {
            throw new NullPointerException("Service name cannot be null");
        }
        if (this.serviceName.equals(serviceName)) {
            return;
        }

        // Re-register the service.
        try {
            componentManager.removeComponent(this.serviceName);
        } catch (Exception e) {
            componentManager.getLog().error(e);
        }
        try {
            componentManager.addComponent(serviceName, sipComponent);
        } catch (Exception e) {
            componentManager.getLog().error(e);
        }
        this.serviceName = serviceName;
    }

    public void propertySet(String property, Map params) {
        if (property.equals(SipComponent.NAMESPACE)) {
            changeServiceName((String) params.get("value"));
        }
    }

    public void propertyDeleted(String property, Map params) {
        if (property.equals(serviceName)) {
            changeServiceName(SipComponent.NAME);
        }
    }

    public void xmlPropertySet(String property, Map params) {
        // not used
    }

    public void xmlPropertyDeleted(String property, Map params) {
        // not used
    }

    public ComponentManager getComponentManager() {
        return componentManager;
    }

}