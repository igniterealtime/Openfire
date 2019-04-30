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

import java.util.Collection;

/**
 * Server-to-server communication is done using two TCP connections between the servers. One
 * connection is used for sending packets while the other connection is used for receiving packets.
 * The {@code OutgoingServerSession} represents the connection to a remote server that will only
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
public interface OutgoingServerSession extends ServerSession {
    /**
     * Authenticates a subdomain of this server with the specified remote server over an exsiting
     * outgoing connection. If the existing session was using server dialback then a new db:result
     * is going to be sent to the remote server. But if the existing session was TLS+SASL based
     * then just assume that the subdomain was authenticated by the remote server.
     *
     * @param domain the locally domain to authenticate with the remote server.
     * @param hostname the domain of the remote server.
     * @return True if the domain was authenticated by the remote server.
     */
    boolean authenticateSubdomain(String domain, String hostname);

    /**
     * Checks to see if a pair of domains has previously been authenticated.
     *
     * Since domains are authenticated as pairs, authenticating A-&gt;B does
     *  not imply anything about A--&gt;C or D-&gt;B.
     *
     * @param local the local domain (previously: authenticated domain)
     * @param remote the remote domain (previous: hostname)
     * @return True if the pair of domains has been authenticated.
     */
    boolean checkOutgoingDomainPair(String local, String remote);

    /**
     * Marks a domain pair as being authenticated.
     *
     * @param local the locally hosted domain.
     * @param remote the remote domain.
     */
    void addOutgoingDomainPair(String local, String remote);

    /**
     * Obtains all authenticated domain pairs.
     *
     * Most callers should avoid accessing this and use a simple check as above.
     *
     * @return collection of authenticated DomainPairs
     */
    Collection<DomainPair> getOutgoingDomainPairs();
}
