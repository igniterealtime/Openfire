/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway;

import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.container.Plugin;
import org.jivesoftware.wildfire.container.PluginManager;
import org.jivesoftware.wildfire.gateway.TransportInstance;
import org.xmpp.component.ComponentManager;
import org.xmpp.component.ComponentManagerFactory;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.defaults.DefaultPicoContainer;

import java.io.File;

import java.util.Hashtable;

/**
 * IM Gateway plugin, which provides connectivity to IM networks that
 * don't support the XMPP protocol. 
 *
 * The entire plugin is referred to as the gateway, while individual
 * IM network mappings are referred to as transports.
 *
 * @author Daniel Henninger
 */
public class GatewayPlugin implements Plugin {

    private MutablePicoContainer picoContainer;

    /**
     *  Represents all configured transport handlers.
     */
    private Hashtable<String,TransportInstance> transports; 

    /**
     *  Represents the base component manager.
     */
    private ComponentManager componentManager;

    public GatewayPlugin() {
        picoContainer = new DefaultPicoContainer();

        picoContainer.registerComponentImplementation(RegistrationManager.class);
    }

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        picoContainer.start();

        transports = new Hashtable<String,TransportInstance>();
        componentManager = ComponentManagerFactory.getComponentManager();

        /* Set up AIM transport. */
        transports.put("aim", new TransportInstance(TransportType.aim, "AIM Transport", "org.jivesoftware.wildfire.gateway.protocols.oscar.OSCARGateway", componentManager));
        maybeStartService("aim");

        /* Set up ICQ transport. */
        transports.put("icq", new TransportInstance(TransportType.icq, "ICQ Transport", "org.jivesoftware.wildfire.gateway.protocols.oscar.OSCARGateway", componentManager));
        maybeStartService("icq");

        /* Set up Yahoo transport. */
        transports.put("yahoo", new TransportInstance(TransportType.yahoo, "Yahoo! Transport", "org.jivesoftware.wildfire.gateway.protocols.yahoo.YahooGateway", componentManager));
        maybeStartService("yahoo");
    }

    public void destroyPlugin() {
        for (TransportInstance trInstance : transports.values()) {
            trInstance.stopInstance();
        }
        picoContainer.stop();
        picoContainer.dispose();
        picoContainer = null;
    }

    /**
     * Returns the instance of a module registered with the plugin.
     *
     * @param clazz the module class.
     * @return the instance of the module.
     */
    public Object getModule(Class clazz) {
        return picoContainer.getComponentInstanceOfType(clazz);
    }

    /**
     *  Starts a transport service, identified by subdomain.  The transport
     *  service will only start if it is enabled.
     */
    private void maybeStartService(String serviceName) {
        TransportInstance trInstance = transports.get(serviceName);
        trInstance.startInstance();
        Log.debug("Starting transport service: "+serviceName);
    }

    /**
     *  Enables a transport service, identified by subdomain.
     */
    public void enableService(String serviceName) {
        TransportInstance trInstance = transports.get(serviceName);
        trInstance.enable();
        Log.debug("Enabling transport service: "+serviceName);
    }

    /**
     *  Disables a transport service, identified by subdomain.
     */
    public void disableService(String serviceName) {
        TransportInstance trInstance = transports.get(serviceName);
        trInstance.disable();
        Log.debug("Disabling transport service: "+serviceName);
    }

    /**
     *  Returns the state of a transport service, identified by subdomain.
     */
    public Boolean serviceEnabled(String serviceName) {
        TransportInstance trInstance = transports.get(serviceName);
        return trInstance.isEnabled();
    }
}
