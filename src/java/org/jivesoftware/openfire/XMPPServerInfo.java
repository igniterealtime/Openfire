/**
 * $RCSfile$
 * $Revision: 128 $
 * $Date: 2004-10-25 20:42:00 -0300 (Mon, 25 Oct 2004) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
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
     * Obtain the server name (ip address or hostname).
     *
     * @return the server's name as an ip address or host name.
     */
    public String getName();

    /**
     * Set the server name (ip address or hostname). The server
     * must be restarted for this change to take effect.
     *
     * @param serverName the server's name as an ip address or host name.
     */
    public void setName(String serverName);

    /**
     * Obtain the date when the server was last started.
     *
     * @return the date the server was started or null if server has not been started.
     */
    public Date getLastStarted();

    /**
     * Obtain the date when the server was last stopped.
     *
     * @return the date the server was stopped or null if server has not been
     *      started or is still running
     */
    public Date getLastStopped();

    /**
     * Obtain the server ports active on this server.
     *
     * @return an iterator over the server ports for this server.
     */
    public Collection<ServerPort> getServerPorts();
}