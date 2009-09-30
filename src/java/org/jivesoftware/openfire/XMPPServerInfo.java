/**
 * $RCSfile$
 * $Revision: 128 $
 * $Date: 2004-10-25 20:42:00 -0300 (Mon, 25 Oct 2004) $
 *
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

import org.jivesoftware.util.Version;

import java.util.Collection;
import java.util.Date;

/**
 * Information 'snapshot' of a server's state. Useful for statistics
 * gathering and administration display.
 *
 * @author Iain Shigeoka
 */
public interface XMPPServerInfo {

    /**
     * Obtain the server's version information. Typically used for iq:version
     * and logging information.
     *
     * @return the version of the server.
     */
    public Version getVersion();

    /**
     * Obtain the server name (IP address or hostname).
     *
     * @return the server's name as an IP address or host name.
     * @deprecated replaced by {@link #getXMPPDomain()}
     */
    @Deprecated
    public String getName();

    /**
     * Set the server name (IP address or hostname). The server
     * must be restarted for this change to take effect.
     *
     * @param serverName the server's name as an IP address or host name.
     * @deprecated replaced by {@link #setXMPPDomain(String)}
     */
    @Deprecated
    public void setName(String serverName);

    /**
     * Obtain the host name (IP address or hostname) of this server node.
     *
     * @return the server's host name as an IP address or host name.
     */
    public String getHostname();

    /**
     * Obtain the server XMPP domain name. Note that, if unconfigured, the
     * returned value will equal the hostname or IP address of the server.
     *
     * @return the name of the XMPP domain that this server is part of.
     */
    public String getXMPPDomain();

    /**
     * Set the server XMPP domain name. The server must be
     * restarted for this change to take effect.
     *
     * @param domainName
     *             the XMPP domain that this server is part of.
     */
    public void setXMPPDomain(String domainName);

    /**
     * Obtain the date when the server was last started.
     *
     * @return the date the server was started or null if server has not been started.
     */
    public Date getLastStarted();

    /**
     * Obtain the server ports active on this server.
     *
     * @return an iterator over the server ports for this server.
     */
    public Collection<ServerPort> getServerPorts();
}