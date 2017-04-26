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
