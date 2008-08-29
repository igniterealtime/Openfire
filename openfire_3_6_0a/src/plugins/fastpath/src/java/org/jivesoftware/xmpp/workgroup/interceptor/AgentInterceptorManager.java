/**
 * $RCSfile$
 * $Revision: 19263 $
 * $Date: 2005-07-08 15:30:05 -0700 (Fri, 08 Jul 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.xmpp.workgroup.interceptor;

import java.util.Arrays;
import java.util.Collection;

/**
 * Manages the packet interceptors that will be invoked every time an agent sends a presence
 * to the workgroup. This includes presences for joining or leaving the workgroup as well as
 * presences sent when the status of the agent has changed. Therefore, rejection of packets should
 * be done with extremely caution to ensure the agents are able to join or leave the workgroup.
 *
 * @author Gaston Dombiak
 */
public class AgentInterceptorManager extends InterceptorManager {

    private static InterceptorManager instance = new AgentInterceptorManager();

    /**
     * Returns a singleton instance of AgentInterceptorManager.
     *
     * @return an instance of AgentInterceptorManager.
     */
    public static InterceptorManager getInstance() {
        return instance;
    }

    protected String getPropertySuffix() {
        return "agent";
    }

    protected Collection<Class> getBuiltInInterceptorClasses() {
        return Arrays.asList((Class) TrafficMonitor.class);
    }
}
