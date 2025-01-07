/*
 * Copyright (C) 2007-2009 Jive Software, 2021-2025 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire.session;

import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.XMPPServer;
import org.xmpp.packet.JID;

import java.nio.charset.StandardCharsets;

/**
 * Locator of sessions that know how to talk to Hazelcast cluster nodes.
 *
 * @author Gaston Dombiak
 */
public class RemoteSessionLocatorImpl implements RemoteSessionLocator {

    // TODO Keep a cache for a brief moment so we can reuse same instances (that use their own cache)

    public ClientSession getClientSession(byte[] nodeID, JID address) {
        if (XMPPServer.getInstance().getNodeID().equals(nodeID)) {
            throw new IllegalStateException("Asked to return a RemoteClientSession for '" + address + " on node " + new String(nodeID, StandardCharsets.UTF_8) + ". That node, however, is the local node (not a remote one).");
        }
        return new RemoteClientSession(nodeID, address);
    }

    public ComponentSession getComponentSession(byte[] nodeID, JID address) {
        if (XMPPServer.getInstance().getNodeID().equals(nodeID)) {
            throw new IllegalStateException("Asked to return a RemoteComponentSession for '" + address + " on node " + new String(nodeID, StandardCharsets.UTF_8) + ". That node, however, is the local node (not a remote one).");
        }
        return new RemoteComponentSession(nodeID, address);
    }

    public ConnectionMultiplexerSession getConnectionMultiplexerSession(byte[] nodeID, JID address) {
        if (XMPPServer.getInstance().getNodeID().equals(nodeID)) {
            throw new IllegalStateException("Asked to return a RemoteConnectionMultiplexerSession for '" + address + " on node " + new String(nodeID, StandardCharsets.UTF_8) + ". That node, however, is the local node (not a remote one).");
        }
        return new RemoteConnectionMultiplexerSession(nodeID, address);
    }

    public IncomingServerSession getIncomingServerSession(byte[] nodeID, StreamID streamID) {
        if (XMPPServer.getInstance().getNodeID().equals(nodeID)) {
            throw new IllegalStateException("Asked to return a RemoteIncomingServerSession for '" + streamID + " on node " + new String(nodeID, StandardCharsets.UTF_8) + ". That node, however, is the local node (not a remote one).");
        }
        return new RemoteIncomingServerSession(nodeID, streamID);
    }

    public OutgoingServerSession getOutgoingServerSession(byte[] nodeID, DomainPair address) {
        if (XMPPServer.getInstance().getNodeID().equals(nodeID)) {
            throw new IllegalStateException("Asked to return a RemoteIncomingServerSession for '" + address + " on node " + new String(nodeID, StandardCharsets.UTF_8) + ". That node, however, is the local node (not a remote one).");
        }
        return new RemoteOutgoingServerSession(nodeID, address);
    }
}
