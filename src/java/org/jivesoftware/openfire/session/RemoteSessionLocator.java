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

package org.jivesoftware.openfire.session;

import org.jivesoftware.openfire.StreamID;
import org.xmpp.packet.JID;

/**
 * Locator of sessions that are being hosted by other cluster nodes. Use
 * {@link org.jivesoftware.openfire.XMPPServer#setRemoteSessionLocator(RemoteSessionLocator)} to
 * set the session locator to use. When not running inside of a cluster
 * {@link org.jivesoftware.openfire.XMPPServer#getRemoteSessionLocator()} will always return null.
 *
 * @author Gaston Dombiak
 */
public interface RemoteSessionLocator {

    /**
     * Returns a session surrogate of a client session hosted by a remote cluster node. It is
     * assumed that the client session exists in a remote node. Anyway, if the remote session
     * was closed or its connection was lost or if the connection to the cluster is lost then
     * any remote invocation will fail and a proper error will be returned.
     *
     * @param nodeID the ID of the node hosting the session.
     * @param address the address that uniquely identifies the session.
     * @return a session surrogate of a client session hosted by a remote cluster node.
     */
    ClientSession getClientSession(byte[] nodeID, JID address);

    /**
     * Returns a session surrogate of a component session hosted by a remote cluster node. It is
     * assumed that the component session exists in a remote node. Anyway, if the remote session
     * was closed or its connection was lost or if the connection to the cluster is lost then
     * any remote invocation will fail and a proper error will be returned.
     *
     * @param nodeID the ID of the node hosting the session.
     * @param address the address that uniquely identifies the session.
     * @return a session surrogate of a component session hosted by a remote cluster node.
     */
    ComponentSession getComponentSession(byte[] nodeID, JID address);

    /**
     * Returns a session surrogate of a Connection Multiplexer session hosted by a remote cluster node. It is
     * assumed that the ConnectionMultiplexer session exists in a remote node. Anyway, if the remote session
     * was closed or its connection was lost or if the connection to the cluster is lost then
     * any remote invocation will fail and a proper error will be returned.
     *
     * @param nodeID the ID of the node hosting the session.
     * @param address the address that uniquely identifies the session.
     * @return a session surrogate of a ConnectionMultiplexer session hosted by a remote cluster node.
     */
    ConnectionMultiplexerSession getConnectionMultiplexerSession(byte[] nodeID, JID address);

    /**
     * Returns a session surrogate of an incoming server session hosted by a remote cluster node. It is
     * assumed that the incoming server session exists in a remote node. Anyway, if the remote session
     * was closed or its connection was lost or if the connection to the cluster is lost then
     * any remote invocation will fail and a proper error will be returned.
     *
     * @param nodeID the ID of the node hosting the session.
     * @param streamID the stream ID that uniquely identifies the session.
     * @return a session surrogate of an incoming server session hosted by a remote cluster node.
     */
    IncomingServerSession getIncomingServerSession(byte[] nodeID, StreamID streamID);

    /**
     * Returns a session surrogate of an outgoing server session hosted by a remote cluster node. It is
     * assumed that the outgoing server session exists in a remote node. Anyway, if the remote session
     * was closed or its connection was lost or if the connection to the cluster is lost then
     * any remote invocation will fail and a proper error will be returned.
     *
     * @param nodeID the ID of the node hosting the session.
     * @param address the address that uniquely identifies the session.
     * @return a session surrogate of an incoming server session hosted by a remote cluster node.
     */
    OutgoingServerSession getOutgoingServerSession(byte[] nodeID, DomainPair address);
}
