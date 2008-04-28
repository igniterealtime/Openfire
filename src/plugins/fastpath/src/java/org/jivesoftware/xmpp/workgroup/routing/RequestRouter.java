/**
 * $RCSfile$
 * $Revision: 19223 $
 * $Date: 2005-07-05 17:46:53 -0700 (Tue, 05 Jul 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.xmpp.workgroup.routing;

import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.request.UserRequest;

/**
 * Routers take incoming user chat requests and routes them to the 'best' workgroup
 * request queue. A single router handles all chat requests in a workgroup. You can
 * change the workgroup request queue routing behavior by implementing and installing
 * your own, custom router.
 *
 * @author Derek DeMoro
 */
public abstract class RequestRouter {
    
    private long routingQueue = -1;

    /**
     * Empty constructor
     */
    protected RequestRouter() {

    }

    /**
     * Returns the ID of the Queue to route to if this Router evalutes to true.
     *
     * @return the queue id to route to.
     */
    public long getRoutingQueue() {
        return routingQueue;
    }

    /**
     * Sets the ID of the Queue to route to when this router evalutes to true.
     *
     * @param routingQueue the ID of the Queue to route to.
     */
    public void setRoutingQueue(long routingQueue) {
        this.routingQueue = routingQueue;
    }

    /**
     * Returns true if the admin select the queue to route to, otherwise false to
     * only have the Router select the queue itself. By default, this returns true.
     *
     * @return true if the Queue can be selected via admin.
     */
    public boolean isConfigurable() {
        return true;
    }


    /**
     * Route the given request to one of the workgroup's request queues.
     * This method will never be called concurrently. Implementations
     * should feel free to sleep(), block, or otherwise take their time in making
     * sure the routing happens properly. Routers may be chained by calling
     * several in sequence and stopping when the first router returns true.
     *
     * @param workgroup The workgroup the request belongs to
     * @param request   The request being routed
     * @return True if the router was able to route the request
     */
    public abstract boolean handleRequest(Workgroup workgroup, UserRequest request);
}
