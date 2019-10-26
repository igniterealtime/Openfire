/*
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

package org.jivesoftware.openfire.spi;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.util.cache.CacheSizes;
import org.jivesoftware.util.cache.Cacheable;
import org.jivesoftware.util.cache.ExternalizableUtil;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Internal object used by RoutingTableImpl to keep track of the node that own a ClientSession
 * and whether the session is available or not.
 *
 * @author Gaston Dombiak
 */
public class ClientRoute implements Cacheable, Externalizable {

    private NodeID nodeID;
    private boolean available;

    public ClientRoute() {
    }


    public NodeID getNodeID() {
        return nodeID;
    }

    public void setNodeID( final NodeID nodeID )
    {
        this.nodeID = nodeID;
    }

    public boolean isAvailable() {
        return available;
    }

    public ClientRoute(NodeID nodeID, boolean available) {
        this.nodeID = nodeID;
        this.available = available;
    }

    @Override
    public int getCachedSize() {
        // Approximate the size of the object in bytes by calculating the size
        // of each field.
        int size = 0;
        size += CacheSizes.sizeOfObject();      // overhead of object
        size += nodeID.toByteArray().length;                  // Node ID
        size += CacheSizes.sizeOfBoolean();     // available
        return size;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeByteArray(out, nodeID.toByteArray());
        ExternalizableUtil.getInstance().writeBoolean(out, available);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        byte[] bytes = ExternalizableUtil.getInstance().readByteArray(in);
        // Retrieve the NodeID but try to use the singleton instance
        if (XMPPServer.getInstance().getNodeID().equals(bytes)) {
            nodeID = XMPPServer.getInstance().getNodeID();
        }
        else {
            nodeID = NodeID.getInstance(bytes);
        }
        available = ExternalizableUtil.getInstance().readBoolean(in);
    }

    @Override
    public String toString()
    {
        return "ClientRoute{" +
            "nodeID=" + nodeID +
            ", available=" + available +
            '}';
    }
}
