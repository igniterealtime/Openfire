/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.component;

import org.xmpp.component.Component;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

/**
 * Interface to listen for component events. Use the
 * {@link InternalComponentManager#addListener(ComponentEventListener)}
 * method to register for events.
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
     * @param component the newly added component.
     * @param componentJID address where the component can be located (e.g. search.myserver.com)
     */
    public void componentRegistered(Component component, JID componentJID);

    /**
     * A component was removed.
     *
     * @param component the removed component.
     * @param componentJID address where the component was located (e.g. search.myserver.com)
     */
    public void componentUnregistered(Component component, JID componentJID);

    /**
     * The server has received a disco#info response from the component. Once a component
     * is registered with the server, the server will send a disco#info request to the
     * component to discover if service discover is supported by the component. This event
     * is triggered when the server received the response of the component.
     *
     * @param component the component that answered the disco#info request.
     * @param iq the IQ packet with the disco#info sent by the component.
     */
    public void componentInfoReceived(Component component, IQ iq);
}
