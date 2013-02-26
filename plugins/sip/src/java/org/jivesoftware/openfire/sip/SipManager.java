/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.sip;

import java.io.File;
import java.util.Map;

import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.sip.log.LogComponent;
import org.jivesoftware.openfire.sip.log.LogListenerImpl;
import org.jivesoftware.openfire.sip.sipaccount.SipComponent;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.component.ComponentManager;
import org.xmpp.component.ComponentManagerFactory;

/**
 * Remote management for users SIP account for Spark SIP Plugin
 *
 * @author Thiago Rocha Camargo
 */
public class SipManager implements Plugin, PropertyEventListener {

	private static final Logger Log = LoggerFactory.getLogger(SipManager.class);
	
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
            Log.error(e.getMessage(), e);
        }
        try {
            componentManager.addComponent(LogComponent.NAME, logComponent);

        } catch (Exception e) {
            Log.error(e.getMessage(), e);
        }

        PropertyEventDispatcher.addListener(this);
        Log.debug("SIPARK STARTED");
    }

    public void destroyPlugin() {
        PropertyEventDispatcher.removeListener(this);
        // Unregister component.
        if (componentManager != null) {
            try {
                componentManager.removeComponent(serviceName);
            } catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
            try {
                componentManager.removeComponent(LogComponent.NAME);
            } catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
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
            Log.error(e.getMessage(), e);
        }
        try {
            componentManager.addComponent(serviceName, sipComponent);
        } catch (Exception e) {
            Log.error(e.getMessage(), e);
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