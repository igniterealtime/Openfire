/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2009 Jive Software. All rights reserved.
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

package com.jivesoftware.util.cluster;

import org.jivesoftware.openfire.RemotePacketRouter;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

/**
 * Route packets to other nodes of the cluster. If the remote node was not found or failed
 * to be reached then depending on the type of packet an error packet will be returned. In case
 * the remote node is reached but the remote node fails to route the packet to the recipient (e.g.
 * the recipient just left) then an error packet may be created from the remote node and send it
 * back to this node.<p>
 * 
 * @author Gaston Dombiak
 */
public class ClusterPacketRouter implements RemotePacketRouter {

    private static Logger logger = LoggerFactory.getLogger(ClusterPacketRouter.class);

    public boolean routePacket(byte[] nodeID, JID receipient, Packet packet) {
        // Send the packet to the specified node and let the remote node deliver the packet to the recipient
        try {
            CacheFactory.doClusterTask(new RemotePacketExecution(receipient, packet), nodeID);
            return true;
        } catch (IllegalStateException  e) {
            logger.warn("Error while routing packet to remote node", e);
            return false;
        }
    }

    public void broadcastPacket(Message packet) {
        // Execute the broadcast task across the cluster
        CacheFactory.doClusterTask(new BroadcastMessage(packet));
    }
}
