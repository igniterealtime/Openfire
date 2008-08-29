/**
 * $RCSfile$
 * $Revision: 3144 $
 * $Date: 2005-12-01 14:20:11 -0300 (Thu, 01 Dec 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */
package org.jivesoftware.xmpp.workgroup.spi.routers;

import org.jivesoftware.xmpp.workgroup.RequestQueue;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.request.UserRequest;
import org.jivesoftware.xmpp.workgroup.routing.RequestRouter;

/**
 * <p>Routes all incoming requests to any 'available' request queue
 * trying its best not to allow routing loops and minimize request queue time.</p>
 * <p>Most workgroups should install a default router as the last router in their
 * router chain to make sure that all requests are routed to at least one queue.</p>
 * <p>Routing meta-data is expected to be a subpacket in iq-join-queue with a root
 * element 'info' in the 'http://jivesoftware.com/live/metadata' namespace and consist
 * of item-name-value pairs. For example</p>
 * <code><pre>
 * &lt;iq&gt;
 *   &lt;join-queue&gt;
 *     &lt;info xmlns='http://jivesoftware.com/live/metadata'&gt;
 *       &lt;item&gt;
 *         &lt;name&gt;product&lt;/name&gt;
 *         &lt;value&gt;Winframe&lt;/name&gt;
 *       &lt;/item&gt;
 *       &lt;item&gt;
 *         &lt;name&gt;platform&lt;/name&gt;
 *         &lt;value&gt;Windows&lt;/value&gt;
 *       &lt;/item&gt;
 *     &lt;/info&gt;
 *   &lt;/join-queue&gt;
 * &lt;/iq&gt;
 * </pre></code>
 *
 * @author Derek DeMoro
 */
public class DefaultRouter extends RequestRouter {

    /**
     * <p>Create a default router that routes all requests to the 'best'
     * queue possible.</p>
     * <p/>
     */
    public DefaultRouter() {
    }

    public String getTitle(){
        return "Default Live Assistant Router";
    }

    public String getDescription(){
        return "This router is used to route to the best queue based solely on availability of agents. This is to be used for fail-over";
    }

    /**
     * Routes the request to the 'best' queue possible based solely on
     * availability of the queues.<p>
     *
     * Currently the routing algorithm is:
     * <ol>
     * <li>Find the first queue of the workgroup.</li>
     * <li>Route to that queue.</li>
     * </ol>
     *
     * Ya, that's pretty lame.
     *
     * @param workgroup The workgroup being routed on
     * @param request   The request being routed
     * @return True if the router was able to route the request
     */
    public boolean handleRequest(Workgroup workgroup, UserRequest request) {
        for (RequestQueue requestQueue : workgroup.getRequestQueues()) {
            // Skip queues that doesn't have agents at the moment
            if (requestQueue != null && requestQueue.isOpened()) {
                requestQueue.addRequest(request);
                return true;
            }
        }
        return false;
    }
}
