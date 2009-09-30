/**
 * $RCSfile: IncomingServerSession.java,v $
 * $Revision: 3174 $
 * $Date: 2005-12-08 17:41:00 -0300 (Thu, 08 Dec 2005) $
 *
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

import java.util.Collection;

/**
 * Server-to-server communication is done using two TCP connections between the servers. One
 * connection is used for sending packets while the other connection is used for receiving packets.
 * The <tt>IncomingServerSession</tt> represents the connection to a remote server that will only
 * be used for receiving packets.<p>
 *
 * Currently only the Server Dialback method is being used for authenticating the remote server.
 * Once the remote server has been authenticated incoming packets will be processed by this server.
 * It is also possible for remote servers to authenticate more domains once the session has been
 * established. For optimization reasons the existing connection is used between the servers.
 * Therefore, the incoming server session holds the list of authenticated domains which are allowed
 * to send packets to this server.<p>
 *
 * Using the Server Dialback method it is possible that this server may also act as the
 * Authoritative Server. This implies that an incoming connection will be established with this
 * server for authenticating a domain. This incoming connection will only last for a brief moment
 * and after the domain has been authenticated the connection will be closed and no session will
 * exist.
 *
 * @author Gaston Dombiak
 */
public interface IncomingServerSession extends Session {

    /**
     * Returns a collection with all the domains, subdomains and virtual hosts that where
     * validated. The remote server is allowed to send packets from any of these domains,
     * subdomains and virtual hosts.
     *
     * @return domains, subdomains and virtual hosts that where validated.
     */
    public Collection<String> getValidatedDomains();

    /**
     * Returns the domain or subdomain of the local server used by the remote server
     * when validating the session. This information is only used to prevent many
     * connections from the same remote server to the same domain or subdomain of
     * the local server.
     *
     * @return the domain or subdomain of the local server used by the remote server
     *         when validating the session.
     */
    public String getLocalDomain();
}
