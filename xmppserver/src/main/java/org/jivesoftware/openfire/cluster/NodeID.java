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

package org.jivesoftware.openfire.cluster;

import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.cache.ExternalizableUtil;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
    private static List<NodeID> instances = new ArrayList<>();

    private byte[] nodeID;

    public static synchronized NodeID getInstance(byte[] nodeIdBytes) {
        for (NodeID nodeID : instances) {
            if (nodeID.equals(nodeIdBytes)) {
                return nodeID;
            }
        }
        NodeID answer = new NodeID(nodeIdBytes);
        instances.add(answer);
        return answer;
    }

    public static synchronized void deleteInstance(byte[] nodeIdBytes) {
        NodeID toDelete = null;
        for (NodeID nodeID : instances) {
            if (nodeID.equals(nodeIdBytes)) {
                toDelete = nodeID;
                break;
            }
        }
        if (toDelete != null) {
            instances.remove(toDelete);
        }
    }

    public NodeID() {
    }

    private NodeID(byte[] nodeIdBytes) {
        this.nodeID = nodeIdBytes;
    }

    public boolean equals(byte[] anotherID) {
        return Arrays.equals(nodeID, anotherID);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NodeID that = (NodeID) o;

        return Arrays.equals(nodeID, that.nodeID);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(nodeID);
    }
    
    @Override
    public String toString() {
        return StringUtils.getString(nodeID);
    }

    public byte[] toByteArray() {
        return nodeID;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeByteArray(out, nodeID);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        nodeID = ExternalizableUtil.getInstance().readByteArray(in);
    }
}
