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

package org.jivesoftware.openfire.session;

import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.SessionManager;

/**
 * Represents a session between the server and a connection manager.<p>
 *
 * Each Connection Manager has its own domain. Each connection from the same connection manager
 * uses a different resource. Unlike any other session, connection manager sessions are not
 * present in the routing table. This means that connection managers are not reachable entities.
 * In other words, entities cannot send packets to connection managers but clients being hosted
 * by them. The main reason behind this design decision is that connection managers are private
 * components of the server so they can only be contacted by the server. Connection Manager
 * sessions are present in {@link SessionManager} but not in {@link RoutingTable}. Use
 * {@link SessionManager#getConnectionMultiplexerSessions(String)} to get all sessions or
 * {@link org.jivesoftware.openfire.multiplex.ConnectionMultiplexerManager#getMultiplexerSession(String)}
 * to get a random session to a given connection manager.
 *
 * @author Gaston Dombiak
 */
public interface ConnectionMultiplexerSession extends Session {

}
