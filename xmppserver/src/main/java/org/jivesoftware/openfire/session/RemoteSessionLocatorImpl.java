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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import java.nio.charset.StandardCharsets;

/**
 * Locator of sessions that know how to talk to Hazelcast cluster nodes.
 *
 * @author Gaston Dombiak
 */
public class RemoteSessionLocatorImpl implements RemoteSessionLocator {

    private static final Logger Log = LoggerFactory.getLogger(RemoteSessionLocatorImpl.class);

    // TODO Keep a cache for a brief moment so we can reuse same instances (that use their own cache)

    public ClientSession getClientSession(byte[] nodeID, JID address) {
        if (XMPPServer.getInstance().getNodeID().equals(nodeID)) {
            Log.warn("Illegal State: Asked to return a RemoteClientSession for '{} on node {}. That node, however, is the local node (not a remote one). This is likely a bug in Openfire or one of its plugins.", address, new String(nodeID, StandardCharsets.UTF_8));
            return null;
        }
        return new RemoteClientSession(nodeID, address);
    }

    public ComponentSession getComponentSession(byte[] nodeID, JID address) {
        if (XMPPServer.getInstance().getNodeID().equals(nodeID)) {
            Log.warn("Illegal State: Asked to return a RemoteComponentSession for '{} on node {}. That node, however, is the local node (not a remote one). This is likely a bug in Openfire or one of its plugins.", address, new String(nodeID, StandardCharsets.UTF_8));
            return null;
        }
        return new RemoteComponentSession(nodeID, address);
    }

    public ConnectionMultiplexerSession getConnectionMultiplexerSession(byte[] nodeID, JID address) {
        if (XMPPServer.getInstance().getNodeID().equals(nodeID)) {
            Log.warn("Illegal State: Asked to return a RemoteConnectionMultiplexerSession for '{} on node {}. That node, however, is the local node (not a remote one). This is likely a bug in Openfire or one of its plugins.", address, new String(nodeID, StandardCharsets.UTF_8));
            return null;
        }
        return new RemoteConnectionMultiplexerSession(nodeID, address);
    }

    public IncomingServerSession getIncomingServerSession(byte[] nodeID, StreamID streamID) {
        if (XMPPServer.getInstance().getNodeID().equals(nodeID)) {
            Log.warn("Illegal State: Asked to return a RemoteIncomingServerSession for '{} on node {}. That node, however, is the local node (not a remote one). This is likely a bug in Openfire or one of its plugins.", streamID, new String(nodeID, StandardCharsets.UTF_8));
            return null;
        }
        return new RemoteIncomingServerSession(nodeID, streamID);
    }

    public OutgoingServerSession getOutgoingServerSession(byte[] nodeID, DomainPair address) {
        if (XMPPServer.getInstance().getNodeID().equals(nodeID)) {
            Log.warn("Illegal State: Asked to return a RemoteOutgoingServerSession for '{} on node {}. That node, however, is the local node (not a remote one). This is likely a bug in Openfire or one of its plugins.", address, new String(nodeID, StandardCharsets.UTF_8));
            return null;
        }
        return new RemoteOutgoingServerSession(nodeID, address);
    }
}
