/*
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.spi;

import org.jivesoftware.messenger.container.ModuleContext;
import org.jivesoftware.util.Version;
import org.jivesoftware.messenger.XMPPServerInfo;
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
    private ModuleContext modContext;

    /**
     * Simple constructor
     *
     * @param context    the context for the server.
     * @param serverName The server's serverName (e.g. jivesoftware.com)
     * @param version    The server's version number
     * @param startDate  The server's last start time (can be null indicating it hasn't been started)
     * @param stopDate   The server's last stop time (can be null indicating it is running or hasn't been started)
     * @param portIter   The portIter active on the server
     */
    public XMPPServerInfoImpl(ModuleContext context,
                              String serverName,
                              Version version,
                              Date startDate,
                              Date stopDate,
                              Iterator portIter) {
        modContext = context;
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
            modContext.deleteProperty("xmpp.domain");
        }
        else {
            modContext.setProperty("xmpp.domain", serverName);
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
