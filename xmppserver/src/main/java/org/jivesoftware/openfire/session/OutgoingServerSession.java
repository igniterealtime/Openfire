/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2021 Ignite Realtime Foundation. All rights reserved.
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

import javax.annotation.Nonnull;
import java.util.Collection;

/**
 * Server-to-server communication is done using two TCP connections between the servers. One
 * connection is used for sending packets while the other connection is used for receiving packets.
 * The {@code OutgoingServerSession} represents the connection to a remote server that will only
 * be used for sending packets.<p>
 *
 * Once the connection has been established with the remote server and at least a domain has been
 * authenticated then a new route will be added to the routing table for this connection. Each pair
 * of a local and a remote domain gets a connection of its own: a connection is not used to
 * authenticate more domain pairs.
 *
 * @author Gaston Dombiak
 */
public interface OutgoingServerSession extends ServerSession {
    /**
     * Was used to authenticate a further domain pair over the existing connection of this session (dialback
     * multiplexing). A session is no longer used to authenticate more domain pairs, so implementations return false.
     *
     * @param domainPair the local (sub) and remote domain for which authentication was requested.
     * @return false
     * @deprecated A session is no longer used to authenticate more domain pairs. Use
     * {@link LocalOutgoingServerSession#authenticateDomain(DomainPair)}, which creates a session for the domain pair.
     */
    @Deprecated(forRemoval = true, since = "5.2.0") // Remove in or after Openfire 5.3.0
    boolean authenticateSubdomain(@Nonnull final DomainPair domainPair);

    /**
     * Checks to see if a pair of domains has previously been authenticated.
     *
     * Since domains are authenticated as pairs, authenticating A-&gt;B does
     *  not imply anything about A--&gt;C or D-&gt;B.
     *
     * @param domainPair the local and remote domain for which the check is executed.
     * @return True if the pair of domains has been authenticated.
     */
    boolean checkOutgoingDomainPair(@Nonnull final DomainPair domainPair);

    /**
     * Marks a domain pair as being authenticated.
     *
     * @param domainPair the local and remote domain for which authentication has been established.
     */
    void addOutgoingDomainPair(@Nonnull final DomainPair domainPair);

    /**
     * Obtains all authenticated domain pairs.
     *
     * Most callers should avoid accessing this and use a simple check as above.
     *
     * @return collection of authenticated DomainPairs
     */
    Collection<DomainPair> getOutgoingDomainPairs();
}
