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

package org.jivesoftware.openfire.spi;

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

    private byte[] nodeID;
    private boolean available;

    public ClientRoute() {
    }


    public byte[] getNodeID() {
        return nodeID;
    }


    public boolean isAvailable() {
        return available;
    }

    public ClientRoute(NodeID nodeID, boolean available) {
        this.nodeID = nodeID.toByteArray();
        this.available = available;
    }

    public int getCachedSize() {
        // Approximate the size of the object in bytes by calculating the size
        // of each field.
        int size = 0;
        size += CacheSizes.sizeOfObject();      // overhead of object
        size += nodeID.length;                  // Node ID
        size += CacheSizes.sizeOfBoolean();     // available
        return size;
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeByteArray(out, nodeID);
        ExternalizableUtil.getInstance().writeBoolean(out, available);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        nodeID = ExternalizableUtil.getInstance().readByteArray(in);
        available = ExternalizableUtil.getInstance().readBoolean(in);
    }
}
