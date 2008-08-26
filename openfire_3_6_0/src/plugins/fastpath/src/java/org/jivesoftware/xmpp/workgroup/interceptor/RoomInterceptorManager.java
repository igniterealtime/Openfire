/**
 * $RCSfile$
 * $Revision: 19282 $
 * $Date: 2005-07-11 20:03:44 -0700 (Mon, 11 Jul 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.xmpp.workgroup.interceptor;

import org.xmpp.packet.Packet;
import org.xmpp.component.ComponentManagerFactory;

import java.util.Arrays;
import java.util.Collection;

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
            ComponentManagerFactory.getComponentManager().getLog().error("Cannot reject " +
                    "room packet",e);
        }
    }

    protected Collection<Class> getBuiltInInterceptorClasses() {
        return Arrays.asList((Class) TrafficMonitor.class);
    }
}
