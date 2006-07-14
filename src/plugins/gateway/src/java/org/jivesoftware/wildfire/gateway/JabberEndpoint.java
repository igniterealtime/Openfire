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

import java.util.concurrent.ConcurrentLinkedQueue;

import org.jivesoftware.util.Log;
import org.jivesoftware.util.LocaleUtils;

import org.xmpp.component.Component;
import org.xmpp.component.ComponentException;
import org.xmpp.component.ComponentManager;
import org.xmpp.packet.Packet;

import java.util.Arrays;

/**
 * The <code>JabberEndpoint</code> implements the <code>Endpoint</code> for an
 * XMPP server.
 * 
 * @author Noah Campbell
 */
public class JabberEndpoint implements Endpoint {

    /**
     * The componentManager
     *
     * @see ComponentManager
     */
    private final ComponentManager componentManager;

    /**
     * The component
     *
     * @see Component
     */
    private final Component component;

    /** 
     * The value. 
     * @see EndpointValve 
     */
    private final EndpointValve valve;

    /**
     * Construct a new <code>JabberEndpoint</code>.
     * @param componentManager The componentManager.
     * @param component The component.
     */
    public JabberEndpoint(ComponentManager componentManager, Component component) {
        this(componentManager, component, new EndpointValve());
    }

    /**
     * Construct a new <code>JabberEndpoint</code>.
     * @param componentManager 
     * @param component 
     * @param valve
     */
    public JabberEndpoint(ComponentManager componentManager, Component component, EndpointValve valve) {
        this.componentManager = componentManager;
        this.component = component;
        this.valve= valve;
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.Endpoint#sendPacket(Packet)
     */
    public void sendPacket(Packet packet) throws ComponentException {
        if (valve.isOpen()) {
            /**
             * Push all pending packets to the XMPP Server.
             */
            while (!queue.isEmpty()) {
                this.componentManager.sendPacket(this.component, queue.poll());
            }
            this.componentManager.sendPacket(this.component, packet);
        }
        else {
            queue.add(packet);
            Log.debug(LocaleUtils.getLocalizedString("jabberendpoint.sendpacketenqueue", "gateway", Arrays.asList(packet.getFrom())));
        }
    }

    /** The backlog queue. */
    private final ConcurrentLinkedQueue<Packet> queue = new ConcurrentLinkedQueue<Packet>();

    /**
     * @see org.jivesoftware.wildfire.gateway.Endpoint#getValve()
     */
    public EndpointValve getValve() {
        return this.valve;
    }

}
