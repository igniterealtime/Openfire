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

package org.jivesoftware.openfire.spi;

import org.jivesoftware.openfire.XMPPServerInfo;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;

/**
 * Implements the server info for a basic server. Optimization opportunities
 * in reusing this object the data is relatively static.
 *
 * @author Iain Shigeoka
 */
public class XMPPServerInfoImpl implements XMPPServerInfo {

    private static final Logger Log = LoggerFactory.getLogger( XMPPServerInfoImpl.class );

    private final Date startDate;

    public static final Version VERSION = new Version(4, 2, 3, Version.ReleaseStatus.Release, -1 );

    /**
     * Simple constructor
     *
     * @param startDate the server's last start time (can be null indicating
     *      it hasn't been started).
     */
    public XMPPServerInfoImpl(Date startDate) {
        this.startDate = startDate;
    }

    @Override
    public Version getVersion() {
        return VERSION;
    }

    @Override
    public String getHostname()
    {
        final String fqdn = JiveGlobals.getProperty( "xmpp.fqdn" );
        if ( fqdn != null && !fqdn.trim().isEmpty() )
        {
            return fqdn.trim().toLowerCase();
        }

        try
        {
            return InetAddress.getLocalHost().getCanonicalHostName().toLowerCase();
        }
        catch (UnknownHostException ex)
        {
            Log.warn( "Unable to determine local hostname.", ex );
            return "localhost";
        }
    }

    @Override
    public void setHostname( String fqdn )
    {
        if ( fqdn == null || fqdn.isEmpty() )
        {
            JiveGlobals.deleteProperty( "xmpp.fqdn" );
        }
        else
        {
            JiveGlobals.setProperty( "xmpp.fqdn", fqdn.toLowerCase() );
        }
    }

    @Override
    public String getXMPPDomain()
    {
        return JiveGlobals.getProperty("xmpp.domain", getHostname() ).toLowerCase();
    }

    @Override
    public Date getLastStarted() {
        return startDate;
    }
}
