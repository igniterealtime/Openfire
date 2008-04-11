/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.component;

import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

/**
 * Interface to listen for component events. Use the
 * {@link InternalComponentManager#addListener(ComponentEventListener)}
 * method to register for events.<p>
 *
 * The registered event will be triggered only once no matter how many
 * times a component is physically connected to the server or to how
 * many cluster nodes is connected. Likewise, the unregistered event
 * will be triggered only when the last connection of the component
 * is no longer available.<p>
 *
 * When running inside of a cluster each cluster node will get these
 * event notifications. For instance, if you have a cluster of two nodes
 * and a component connects to a node then both nodes will get the
 * event notification. 
 *
 * @author Gaston Dombiak
 */
public interface ComponentEventListener {

    /**
     * A component was registered with the Component Manager. At this point the
     * component has been intialized and started. XMPP entities can exchange packets
     * with the component. However, the component is still not listed as a disco#items
     * of the server since the component has not answered the disco#info request sent
     * by the server.
     *
     * @param componentJID address where the component can be located (e.g. search.myserver.com)
     */
    public void componentRegistered(JID componentJID);

    /**
     * A component was removed. This means that no other cluster node has this component
     * and this was the last connection of the component.
     *
     * @param componentJID address where the component was located (e.g. search.myserver.com)
     */
    public void componentUnregistered(JID componentJID);

    /**
     * The server has received a disco#info response from the component. Once a component
     * is registered with the server, the server will send a disco#info request to the
     * component to discover if service discover is supported by the component. This event
     * is triggered when the server received the response of the component.
     *
     * @param iq the IQ packet with the disco#info sent by the component.
     */
    public void componentInfoReceived(IQ iq);
}
