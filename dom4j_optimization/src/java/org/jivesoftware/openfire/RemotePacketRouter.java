/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire;

import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

/**
 * A RemotePacketRouter is responsible for deliverying packets to entities hosted
 * in remote nodes of the cluster.
 *
 * @author Gaston Dombiak
 */
public interface RemotePacketRouter {

    /**
     * Routes packet to specified receipient hosted in the specified node.
     *
     * @param nodeID the ID of the node hosting the receipient.
     * @param receipient the target entity that will get the packet.
     * @param packet the packet to send.
     * @return true if the remote node was found.
     */
    boolean routePacket(byte[] nodeID, JID receipient, Packet packet);

    /**
     * Brodcasts the specified message to all local client sessions of each cluster node.
     * The current cluster node is not going to be included.
     *
     * @param packet the message to broadcast.
     */
    void broadcastPacket(Message packet);
}
