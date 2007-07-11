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

package org.jivesoftware.openfire.cluster;

import org.jivesoftware.util.cache.ExternalizableUtil;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Arrays;

/**
 * Class which wraps the byte[] we use to identify cluster members. The main reason
 * for this class is that byte[] cannot be directly compared so having a collection
 * of byte[] is not possible since you cannot remove to equivalent byte[] from the
 * collection.<p>
 *
 * @author Pete Matern
 * @author Gaston Dombiak
 */
public class NodeID implements Externalizable {
    private byte[] nodeID;

    public NodeID() {
    }

    public NodeID(byte[] nodeIdBytes) {
        this.nodeID = nodeIdBytes;
    }

    public boolean equals(byte[] anotherID) {
        return Arrays.equals(nodeID, anotherID);
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NodeID that = (NodeID) o;

        return Arrays.equals(nodeID, that.nodeID);
    }

    public int hashCode() {
        return Arrays.hashCode(nodeID);
    }

    public byte[] toByteArray() {
        return nodeID;
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeByteArray(out, nodeID);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        nodeID = ExternalizableUtil.getInstance().readByteArray(in);
    }
}
