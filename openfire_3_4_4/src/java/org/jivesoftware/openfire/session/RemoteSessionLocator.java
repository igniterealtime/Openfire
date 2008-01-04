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

package org.jivesoftware.openfire.session;

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
    IncomingServerSession getIncomingServerSession(byte[] nodeID, String streamID);

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
    OutgoingServerSession getOutgoingServerSession(byte[] nodeID, JID address);
}
