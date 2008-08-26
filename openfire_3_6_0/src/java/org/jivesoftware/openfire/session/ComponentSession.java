/**
 * $RCSfile: ComponentSession.java,v $
 * $Revision: 3174 $
 * $Date: 2005-12-08 17:41:00 -0300 (Thu, 08 Dec 2005) $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
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