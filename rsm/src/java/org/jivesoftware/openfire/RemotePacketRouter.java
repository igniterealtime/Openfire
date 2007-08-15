/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
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
