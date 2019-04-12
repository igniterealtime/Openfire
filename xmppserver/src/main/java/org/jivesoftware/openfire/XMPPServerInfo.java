/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire;

import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.Version;

import java.util.Date;

/**
 * Information 'snapshot' of a server's state. Useful for statistics
 * gathering and administration display.
 *
 * @author Iain Shigeoka
 */
public interface XMPPServerInfo {

    SystemProperty<String> XMPP_DOMAIN = SystemProperty.Builder.ofType(String.class)
        .setKey("xmpp.domain")
        .setDynamic(false)
        .build();

    /**
     * Obtain the server's version information. Typically used for iq:version
     * and logging information.
     *
     * @return the version of the server.
     */
    Version getVersion();

    /**
     * Obtain the fully qualified domain name (hostname or IP address) of this server node.
     *
     * @return the server's host name.
     */
    String getHostname();

    /**
     * Sets the fully qualified domain name of this server node. Preferrably, this is a network name, but can be an
     * IP address.
     *
     * Note that some SASL implementations depend on the client sending the same FQDN value as the one that is
     * configured in the server.
     *
     * When setting a new host name, the server note must be restarted.
     *
     * @param fqdn The hostname. When null or empty, a system default will be used instead.
     */
    void setHostname( String fqdn );

    /**
     * Obtain the server XMPP domain name, which is equal for all server nodes in an Openfire cluster.
     *
     * @return the name of the XMPP domain that this server is part of.
     */
    String getXMPPDomain();

    /**
     * Obtain the date when the server was last started.
     *
     * @return the date the server was started or null if server has not been started.
     */
    Date getLastStarted();
}
