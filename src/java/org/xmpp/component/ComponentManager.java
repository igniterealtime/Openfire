/**
 * Copyright (C) 2004-2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.xmpp.component;

import org.jivesoftware.openfire.IQResultListener;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;

/**
 * Manages components.
 *
 * @see Component
 * @author Matt Tucker
 */
public interface ComponentManager {

    /**
     * Adds a component. The {@link Component#initialize(org.xmpp.packet.JID, ComponentManager)}
     * method will be called on the component. The subdomain specifies the address of
     * the component on a server. For example, if the subdomain is "test" and the XMPP
     * server is at "example.com", then the component's address would be "test.example.com".
     *
     * @param subdomain the subdomain of the component's address.
     * @param component the component.
     */
    public void addComponent(String subdomain, Component component) throws ComponentException;

    /**
     * Removes a component. The {@link Component#shutdown} method will be called on the
     * component.
     *
     * @param subdomain the subdomain of the component's address.
     */
    public void removeComponent(String subdomain) throws ComponentException;

    /**
     * Sends a packet to the XMPP server. The "from" value of the packet must not be null.
     * An <tt>IllegalArgumentException</tt> will be thrown when the "from" value is null.<p>
     *
     * Components are trusted by the server and may use any value in from address. Usually
     * the from address uses the component's address as the domain but this is not required.
     *
     * @param component the component sending the packet.
     * @param packet the packet to send.
     */
    public void sendPacket(Component component, Packet packet) throws ComponentException;

    /**
     * Sends an IQ packet to the XMPP server and waits to get an IQ of type result or error.
     * The "from" value of the packet must not be null. An <tt>IllegalArgumentException</tt>
     * will be thrown when the "from" value is null.<p>
     *
     * Components are trusted by the server and may use any value in from address. Usually
     * the from address uses the component's address as the domain but this is not required.
     *
     * @param component the component sending the packet.
     * @param packet the IQ packet to send.
     * @param timeout the number of milliseconds to wait before returning an IQ error.
     * @return the answer sent by the server. The answer could be an IQ of type result or
     *         error or null if nothing was received.
     */
    public IQ query(Component component, IQ packet, int timeout) throws ComponentException;

    /**
     * Sends an IQ packet to the server and returns immediately. The specified IQResultListener
     * will be invoked when an answer is received.
     *
     * @param component the component sending the packet.
     * @param packet the IQ packet to send.
     * @param listener the listener that will be invoked when an answer is received.
     */
    public void query(Component component, IQ packet, IQResultListener listener) throws ComponentException;

    /**
     * Returns a property value specified by name. Properties can be used by
     * components to store configuration data. It is recommended that each
     * component qualify property names to prevent overlap. For example a
     * component that broadcasts messages to groups of users, might prepend
     * all property names it uses with "broadcast.".
     *
     * @param name the property name.
     * @return the property value.
     */
    public String getProperty(String name);

    /**
     * Sets a property value. Properties can be used by components to
     * store configuration data. It is recommended that each component
     * qualify property names to prevent overlap. For example a component
     * that broadcasts messages to groups of users, might prepend all
     * property names it uses with "broadcast.".
     *
     * @param name the property name.
     * @param value the property value.
     */
    public void setProperty(String name, String value);

    /**
     * Returns the domain of the XMPP server. The domain name may be the IP address or the host
     * name.
     *
     * @return the domain of the XMPP server.
     */
    public String getServerName();
    
    /**
     * Returns true if components managed by this component manager are external
     * components connected to the server over a network connection. Otherwise,
     * the components are internal to the server.
     *
     * @return true if the managed components are external components.
     */
    public boolean isExternalMode();

    /**
     * Returns a Log instance, which can be used by components for logging error,
     * warning, info, and debug messages.
     *
     * @return a Log instance.
     */
    public Log getLog();
}