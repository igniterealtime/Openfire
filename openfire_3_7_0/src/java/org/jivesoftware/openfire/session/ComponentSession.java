/**
 * $RCSfile: ComponentSession.java,v $
 * $Revision: 3174 $
 * $Date: 2005-12-08 17:41:00 -0300 (Thu, 08 Dec 2005) $
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
package org.jivesoftware.openfire.session;

import org.xmpp.component.Component;

import java.util.Collection;

/**
 * Represents a session between the server and an external component.
 *
 * @author Gaston Dombiak
 */
public interface ComponentSession extends Session {

    public ExternalComponent getExternalComponent();

    /**
     * The ExternalComponent acts as a proxy of the remote connected component. Any Packet that is
     * sent to this component will be delivered to the real component on the other side of the
     * connection.<p>
     *
     * An ExternalComponent will be added as a route in the RoutingTable for each connected
     * external component. This implies that when the server receives a packet whose domain matches
     * the external component services address then a route to the external component will be used
     * and the packet will be forwarded to the component on the other side of the connection.
     *
     * @author Gaston Dombiak
     */
    public interface ExternalComponent extends Component {
        void setName(String name);

        String getType();

        void setType(String type);

        String getCategory();

        void setCategory(String category);

        String getInitialSubdomain();

        Collection<String> getSubdomains();
    }
}