/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger;



/**
 * Interface for Components.
 *
 * @see ComponentManager
 * @author Derek DeMoro
 */
public interface Component extends RoutableChannelHandler {

    /**
     * Returns the service name of this component. The service name is usually the part before the
     * dot before the server address in a JID. For example, given this JID jdoe@broadcast.localhost
     * the service name would be broadcast.<p>
     *
     * This information is useful when adding or removing the component from the ComponentManager.
     *
     * @return the service name of this component.
     */
    public String getServiceName();
}
