/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
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
    private Set<String> receivers = new HashSet<String>();

    public DirectedPresence() {
    }

    public DirectedPresence(JID handlerJID) {
        this.handler = handlerJID;
        this.nodeID = XMPPServer.getInstance().getNodeID();
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

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeByteArray(out, nodeID);
        ExternalizableUtil.getInstance().writeSafeUTF(out, handler.toString());
        ExternalizableUtil.getInstance().writeStrings(out, receivers);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        nodeID = ExternalizableUtil.getInstance().readByteArray(in);
        handler = new JID(ExternalizableUtil.getInstance().readSafeUTF(in));
        ExternalizableUtil.getInstance().readStrings(in, receivers);
    }
}
