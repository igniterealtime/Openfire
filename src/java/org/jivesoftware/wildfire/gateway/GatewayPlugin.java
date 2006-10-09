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

import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.wildfire.container.Plugin;
import org.jivesoftware.wildfire.container.PluginManager;
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
    public Hashtable<String,TransportInstance> transports;

    public GatewayPlugin() {
        picoContainer = new DefaultPicoContainer();

        picoContainer.registerComponentImplementation(RegistrationManager.class);
    }

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        picoContainer.start();

        transports = new Hashtable<String,TransportInstance>();
        ComponentManager componentManager = ComponentManagerFactory.getComponentManager();

        /* Set up AIM transport. */
        transports.put("aim", new TransportInstance(TransportType.aim, LocaleUtils.getLocalizedString("gateway.aim.name", "gateway"), "org.jivesoftware.wildfire.gateway.protocols.oscar.OSCARTransport", componentManager));
        maybeStartService("aim");

        /* Set up ICQ transport. */
        transports.put("icq", new TransportInstance(TransportType.icq, LocaleUtils.getLocalizedString("gateway.icq.name", "gateway"), "org.jivesoftware.wildfire.gateway.protocols.oscar.OSCARTransport", componentManager));
        maybeStartService("icq");

        /* Set up IRC transport. */
        transports.put("irc", new TransportInstance(TransportType.irc, LocaleUtils.getLocalizedString("gateway.irc.name", "gateway"), "org.jivesoftware.wildfire.gateway.protocols.irc.IRCTransport", componentManager));
        maybeStartService("irc");

        /* Set up Yahoo transport. */
        transports.put("yahoo", new TransportInstance(TransportType.yahoo, LocaleUtils.getLocalizedString("gateway.yahoo.name", "gateway"), "org.jivesoftware.wildfire.gateway.protocols.yahoo.YahooTransport", componentManager));
        maybeStartService("yahoo");

        /* Set up MSN transport. */
        transports.put("msn", new TransportInstance(TransportType.msn, LocaleUtils.getLocalizedString("gateway.msn.name", "gateway"), "org.jivesoftware.wildfire.gateway.protocols.msn.MSNTransport", componentManager));
        maybeStartService("msn");
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
     * Starts a transport service, identified by subdomain.  The transport
     * service will only start if it is enabled.
     *
     * @param serviceName name of service to start.
     */
    private void maybeStartService(String serviceName) {
        TransportInstance trInstance = transports.get(serviceName);
        trInstance.startInstance();
    }

    /**
     * Enables a transport service, identified by subdomain.
     *
     * @param serviceName name of service to enable.
     */
    public void enableService(String serviceName) {
        TransportInstance trInstance = transports.get(serviceName);
        trInstance.enable();
    }

    /**
     *  Disables a transport service, identified by subdomain.
     *
     * @param serviceName name of service to disable.
     */
    public void disableService(String serviceName) {
        TransportInstance trInstance = transports.get(serviceName);
        trInstance.disable();
    }

    /**
     *  Returns the state of a transport service, identified by subdomain.
     *
     * @param serviceName name of service to check.
     * @return True of false if service is enabled.
     */
    public Boolean serviceEnabled(String serviceName) {
        TransportInstance trInstance = transports.get(serviceName);
        return trInstance.isEnabled();
    }

    /**
     *  Returns the transport instance, identified by subdomain.
     *
     * @param serviceName name of service to get instance of.
     * @return Instance of service requested.
     */
    public TransportInstance getTransportInstance(String serviceName) {
        return transports.get(serviceName);
    }

}
