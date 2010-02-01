/**
 * $RCSfile$
 * $Revision: 19268 $
 * $Date: 2005-07-08 18:26:08 -0700 (Fri, 08 Jul 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.xmpp.workgroup.interceptor;

import java.util.Arrays;
import java.util.Collection;

/**
 * Manages the packet interceptors that will be invoked every time a packet is sent to a
 * workgroup or a workgroup is sending a packet to a user or an agent. This includes packets
 * sent from a room where the workgroup is an occupant, room creation packets, room invitations
 * packets, users joining a queue, etc.. Therefore, rejection of packets should be done with
 * extremely caution since rejecting a packet that is needed by the workgroup to function
 * correctly (eg. detecting when occupants left the room) may result in unexpected problems.
 *
 * @author Gaston Dombiak
 */
public class WorkgroupInterceptorManager extends InterceptorManager {

    private static InterceptorManager instance = new WorkgroupInterceptorManager();

    /**
     * Returns a singleton instance of WorkgroupInterceptorManager.
     *
     * @return an instance of WorkgroupInterceptorManager.
     */
    public static InterceptorManager getInstance() {
        return instance;
    }

    protected String getPropertySuffix() {
        return "workgroup";
    }

    protected Collection<Class> getBuiltInInterceptorClasses() {
        return Arrays.asList((Class)TrafficMonitor.class, (Class)UserInterceptor.class);
    }
}
