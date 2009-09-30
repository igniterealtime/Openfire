/**
 * $RCSfile$
 * $Revision: 19282 $
 * $Date: 2005-07-11 20:03:44 -0700 (Mon, 11 Jul 2005) $
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

import org.jivesoftware.util.Log;
import org.xmpp.packet.Packet;

/**
 * Manages the packet interceptors that will be invoked when sending packets for creating and
 * configuring a room, when sending room invitations to the agent and the user or when detecting
 * room activity. Notice that none of these packets may be rejected but they may be modified.
 *
 * @author Gaston Dombiak
 */
public class RoomInterceptorManager extends InterceptorManager {

    private static RoomInterceptorManager instance = new RoomInterceptorManager();

    /**
     * Returns a singleton instance of RoomInterceptorManager.
     *
     * @return an instance of RoomInterceptorManager.
     */
    public static RoomInterceptorManager getInstance() {
        return instance;
    }

    protected String getPropertySuffix() {
        return "room";
    }

    public void invokeInterceptors(String workgroup, Packet packet, boolean read, boolean processed) {
        try {
            super.invokeInterceptors(workgroup, packet, read, processed);
        }
        catch (PacketRejectedException e) {
            Log.error("Cannot reject " +
                    "room packet",e);
        }
    }

    protected Collection<Class> getBuiltInInterceptorClasses() {
        return Arrays.asList((Class) TrafficMonitor.class);
    }
}
