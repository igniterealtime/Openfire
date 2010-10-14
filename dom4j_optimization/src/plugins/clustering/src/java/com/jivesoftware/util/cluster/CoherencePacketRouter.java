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
import org.jivesoftware.util.Log;
import org.jivesoftware.util.cache.CacheFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

/**
 * Route packets to other nodes of the cluster. If the remote node was not found or failed
 * to be reached then depending on the type of packet an error packet will be returned. In case
 * the remote node is reached but the remote node fails to route the packet to the recipient (e.g.
 * the receipient just left) then an error packet may be created from the remote node and send it
 * back to this node.<p>
 * 
 * TODO For optimization reasons, this class instead of sending an InvocationService for each
 * packet to route to a remote node may use a smarter logic that would group a few packets into
 * a single InvocationService thus reducing network traffic. Moreover, bnux can be used as a way
 * to encode packets to send so that XML parsing is optimized on the other side.
 *
 * @author Gaston Dombiak
 */
public class CoherencePacketRouter implements RemotePacketRouter {

    public boolean routePacket(byte[] nodeID, JID receipient, Packet packet) {
        // Send the packet to the specified node and let the remote node deliver the packet to the receipient
        try {
            CacheFactory.doClusterTask(new RemotePacketExecution(receipient, packet), nodeID);
            return true;
        } catch (IllegalStateException  e) {
            Log.warn("Error while routing packet to remote node", e);
            return false;
        }
    }

    public void broadcastPacket(Message packet) {
        // Execute the broadcast task across the cluster
        CacheFactory.doClusterTask(new BroadcastMessage(packet));
    }
}
