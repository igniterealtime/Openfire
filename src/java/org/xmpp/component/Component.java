/**
 * Copyright (C) 2004-2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.xmpp.component;

import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

/**
 * Component enhance the functionality of an XMPP server.
 *
 * Components are JavaBeans and will have their properties exposed as ad-hoc commands.
 *
 * @author Matt Tucker
 */
public interface Component {

    /**
     * Returns the name of this component.
     *
     * @return the name of this component.
     */
    public String getName();

    /**
     * Returns the description of this component.
     *
     * @return the description of this component.
     */
    public String getDescription();

    /**
     * Processes a packet sent to this Component.
     *
     * @param packet the packet.
     * @see ComponentManager#sendPacket(Component, Packet)
     */
    public void processPacket(Packet packet);

    /**
     * Initializes this component with a ComponentManager and the JID
     * that this component is available at (e.g. <tt>service.example.com</tt>). If a
     * ComponentException is thrown then the component will not be loaded.<p>
     *
     * The initialization code must not rely on receiving packets from the server since
     * the component has not been fully initialized yet. This means that at this point the
     * component must not rely on information that is obtained from the server such us
     * discovered items.
     *
     * @param jid the XMPP address that this component is available at.
     * @param componentManager the component manager.
     * @throws ComponentException if an error occured while initializing the component.
     */
    public void initialize(JID jid, ComponentManager componentManager) throws ComponentException;

    /**
     * Notification message indicating that the component will start receiving incoming
     * packets. At this time the component may finish pending initialization issues that
     * require information obtained from the server.<p>
     *
     * It is likely that most of the component will leave this method empty.
     */
    public void start();

    /**
     * Shuts down this component. All component resources must be released as
     * part of shutdown.
     */
    public void shutdown();
}