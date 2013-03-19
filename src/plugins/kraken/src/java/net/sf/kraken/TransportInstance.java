/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken;

import net.sf.kraken.roster.TransportBuddy;
import net.sf.kraken.session.cluster.TransportSessionRouter;
import net.sf.kraken.type.TransportType;

import org.apache.log4j.Logger;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;
import org.xmpp.component.ComponentManager;

import java.util.Map;

/**
 * Transport Instance
 *
 * Represents all information that needs to be tracked about a gateway instance.
 *
 * @author Daniel Henninger
 */
public class TransportInstance<B extends TransportBuddy> implements PropertyEventListener {

    final static Logger Log = Logger.getLogger(TransportInstance.class);

    private final ComponentManager componentManager;
    private final String description;
    private final String nameOfClass;
    public BaseTransport<B> transport = null;
    private final TransportType type;
    private boolean enabled = false;
    private boolean running = false;
    private String subDomain;
    private final TransportSessionRouter sessionRouter;

    /**
     *  Creates a new transport instance.
     *
     *  @param type Type of transport.
     *  @param description Short description of transport.
     *  @param classname Full name/path of class associated with instance.
     *  @param componentManager Component manager managing this instance.
     *  @param sessionRouter Session router managing this instance.
     */
    public TransportInstance(TransportType type, String description, String classname, ComponentManager componentManager, TransportSessionRouter sessionRouter) {
        this.description = description;
        this.type = type;
        this.nameOfClass = classname;
        this.componentManager = componentManager;
        this.sessionRouter = sessionRouter;
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
    public boolean isEnabled() {
        return enabled;
    }

    /**
     *  Returns whether this transport instance is currently running.
     *
     *  @return true or false if instance is currently running
     */
    public boolean isRunning() {
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
            transport.setup(this.type, this.description, sessionRouter);
        }
        catch (ClassNotFoundException e) {
            Log.error("Unable to find class: "+nameOfClass);
            return;
        }
        catch (InstantiationException e) {
            Log.error("Unable to instantiate class: "+nameOfClass);
            return;
        }
        catch (IllegalAccessException e) {
            Log.error("Unable to access class: "+nameOfClass);
            return;
        }

        // Automatically kill any current s2s connections with the JID we want to use.
        SessionManager sessionManager = SessionManager.getInstance();
        String fullJID = this.subDomain+"."+XMPPServer.getInstance().getServerInfo().getXMPPDomain();
        boolean pause = false;
        try {
            for (Session sess : sessionManager.getIncomingServerSessions(fullJID)) {
                sess.close();
                pause = true;
            }
        }
        catch (Exception ignored) {
            // Session might have disappeared on its own
        }

        try {
            Session sess = sessionManager.getOutgoingServerSession(fullJID);
            if (sess != null) {
                sess.close();
                pause = true;
            }
        }
        catch (Exception ignored) {
            // Session might have disappeared on its own
        }

        try {
            // Wait one second if we closed something.
            if (pause) {
                Thread.sleep(1000L);
            }
        }
        catch (Exception ignored) {
            // Hrm, interrupted?  That's odd.
        }

        try {
            componentManager.addComponent(this.subDomain, transport);
            PropertyEventDispatcher.addListener(this);
            running = true;
        }
        catch (Exception e) {
            Log.error("Error while adding component "+this.subDomain+": ", e);
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

        transport.shutdown();
        PropertyEventDispatcher.removeListener(this);
        try {
            componentManager.removeComponent(this.subDomain);
        }
        catch (Exception e) {
            Log.error("Error while removing component "+this.subDomain+": ", e);
        }
        transport = null;
        running = false;
    }

    /**
     * Retrieves actual transport associated with this instance.
     *
     * @return Transport that the instance is associated with.
     */
    public BaseTransport<B> getTransport() {
        return transport;
    }

    @SuppressWarnings("unchecked")
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

    @SuppressWarnings("unchecked")
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

    @SuppressWarnings("unchecked")
    public void xmlPropertySet(String property, Map params) {
        propertySet(property, params);
    }

    @SuppressWarnings("unchecked")
    public void xmlPropertyDeleted(String property, Map params) {
        propertyDeleted(property, params);
    }

}
