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

package org.jivesoftware.openfire.handler;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.JID;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a directed presence sent from a session hosted in a cluster node
 * to another entity (e.g. user or MUC service) hosted in some other cluster
 * node.<p>
 *
 * This information needs to be shared by all cluster nodes so that if a
 * cluster node goes down then directed presences can be correctly cleaned
 * up.<p>
 *
 * Note that an instance of this class will be created and kept in the clustered
 * cache only when entities hosted by different cluster nodes are involved.
 *
 * @author Gaston Dombiak
 */
public class DirectedPresence implements Externalizable {
    /**
     * ID of the node that received the request to send a directed presence. This is the
     * node ID that hosts the sender.
     */
    private byte[] nodeID;
    /**
     * Full JID of the entity that received the directed presence.
     * E.g.: paul@js.com/Spark or conference.js.com
     */
    private JID handler;
    /**
     * List of JIDs with the TO value of the directed presences.
     * E.g.: paul@js.com or room1@conference.js.com
     */
    private Set<String> receivers = new HashSet<>();

    public DirectedPresence() {
    }

    public DirectedPresence(JID handlerJID) {
        this.handler = handlerJID;
        this.nodeID = XMPPServer.getInstance().getNodeID().toByteArray();
    }

    public byte[] getNodeID() {
        return nodeID;
    }

    public JID getHandler() {
        return handler;
    }

    public Set<String> getReceivers() {
        return receivers;
    }

    public void addReceiver(String receiver) {
        receivers.add(receiver);
    }

    public void removeReceiver(String receiver) {
        receivers.remove(receiver);
    }

    public boolean isEmpty() {
        return receivers.isEmpty();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeByteArray(out, nodeID);
        ExternalizableUtil.getInstance().writeSerializable(out, handler);
        ExternalizableUtil.getInstance().writeStrings(out, receivers);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        nodeID = ExternalizableUtil.getInstance().readByteArray(in);
        handler = (JID) ExternalizableUtil.getInstance().readSerializable(in);
        ExternalizableUtil.getInstance().readStrings(in, receivers);
    }
}
