/**
 * $RCSfile: OutgoingServerSession.java,v $
 * $Revision: 3188 $
 * $Date: 2005-12-12 00:28:19 -0300 (Mon, 12 Dec 2005) $
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
 * The <tt>OutgoingServerSession</tt> represents the connection to a remote server that will only
 * be used for sending packets.<p>
 *
 * Once the connection has been established with the remote server and at least a domain has been
 * authenticated then a new route will be added to the routing table for this connection. For
 * optimization reasons the same outgoing connection will be used even if the remote server has
 * several hostnames. However, different routes will be created in the routing table for each
 * hostname of the remote server.
 *
 * @author Gaston Dombiak
 */
public interface OutgoingServerSession extends Session {

    /**
     * Returns a collection with all the domains, subdomains and virtual hosts that where
     * authenticated. The remote server will accept packets sent from any of these domains,
     * subdomains and virtual hosts.
     *
     * @return domains, subdomains and virtual hosts that where validated.
     */
    Collection<String> getAuthenticatedDomains();

    /**
     * Adds a new authenticated domain, subdomain or virtual host to the list of
     * authenticated domains for the remote server. The remote server will accept packets
     * sent from this new authenticated domain.
     *
     * @param domain the new authenticated domain, subdomain or virtual host to add.
     */
    void addAuthenticatedDomain(String domain);

    /**
     * Returns the list of hostnames related to the remote server. This tracking is useful for
     * reusing the same session for the same remote server even if the server has many names.
     *
     * @return the list of hostnames related to the remote server.
     */
    Collection<String> getHostnames();

    /**
     * Adds a new hostname to the list of known hostnames of the remote server. This tracking is
     * useful for reusing the same session for the same remote server even if the server has
     * many names.
     *
     * @param hostname the new known name of the remote server
     */
    void addHostname(String hostname);

    /**
     * Authenticates a subdomain of this server with the specified remote server over an exsiting
     * outgoing connection. If the existing session was using server dialback then a new db:result
     * is going to be sent to the remote server. But if the existing session was TLS+SASL based
     * then just assume that the subdomain was authenticated by the remote server.
     *
     * @param domain the local subdomain to authenticate with the remote server.
     * @param hostname the hostname of the remote server.
     * @return True if the subdomain was authenticated by the remote server.
     */
    boolean authenticateSubdomain(String domain, String hostname);

    /**
     * Returns true if this outgoing session was established using server dialback.
     *
     * @return true if this outgoing session was established using server dialback.
     */
    boolean isUsingServerDialback();
}
