/*
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
    void componentRegistered( JID componentJID );

    /**
     * A component was removed. This means that no other cluster node has this component
     * and this was the last connection of the component.
     *
     * @param componentJID address where the component was located (e.g. search.myserver.com)
     */
    void componentUnregistered( JID componentJID );

    /**
     * The server has received a disco#info response from the component. Once a component
     * is registered with the server, the server will send a disco#info request to the
     * component to discover if service discover is supported by the component. This event
     * is triggered when the server received the response of the component.
     *
     * @param iq the IQ packet with the disco#info sent by the component.
     */
    void componentInfoReceived( IQ iq );
}
