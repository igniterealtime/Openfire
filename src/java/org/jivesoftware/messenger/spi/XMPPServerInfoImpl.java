/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.spi;

import org.jivesoftware.util.Version;
import org.jivesoftware.messenger.XMPPServerInfo;
import org.jivesoftware.messenger.JiveGlobals;

import java.util.Date;
import java.util.Iterator;

/**
 * Implements the server info for a basic server. Optimization opportunities
 * in reusing this object the data is relatively static.
 *
 * @author Iain Shigeoka
 */
public class XMPPServerInfoImpl implements XMPPServerInfo {

    private Date startDate;
    private Date stopDate;
    private String name;
    private Version ver;
    private Iterator ports;

    /**
     * Simple constructor
     *
     * @param serverName the server's serverName (e.g. example.org).
     * @param version the server's version number.
     * @param startDate the server's last start time (can be null indicating
     *      it hasn't been started).
     * @param stopDate the server's last stop time (can be null indicating it
     *      is running or hasn't been started).
     * @param portIter the portIter active on the server.
     */
    public XMPPServerInfoImpl(String serverName, Version version, Date startDate, Date stopDate,
            Iterator portIter)
    {
        this.name = serverName;
        this.ver = version;
        this.startDate = startDate;
        this.stopDate = stopDate;
        this.ports = portIter;
    }

    public Version getVersion() {
        return ver;
    }

    public String getName() {
        return name;
    }

    public void setName(String serverName) {
        name = serverName;
        if (serverName == null) {
            JiveGlobals.deleteProperty("xmpp.domain");
        }
        else {
            JiveGlobals.setProperty("xmpp.domain", serverName);
        }
    }

    public Date getLastStarted() {
        return startDate;
    }

    public Date getLastStopped() {
        return stopDate;
    }

    public Iterator getServerPorts() {
        return ports;
    }
}