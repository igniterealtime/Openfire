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

package org.jivesoftware.net;

import java.util.Iterator;

import org.jivesoftware.util.BasicResultFilter;

/**
 * Manages and maintains server connections.<p>
 *
 * Beyond simple access to active connections on the server, the
 * connection manager can ensure connections are alive, and boot
 * connections that are idle according to an idle policy.
 *
 * @author Iain Shigeoka
 */
public interface ConnectionManager {

    int getConnectionCount();
    Iterator getConnections();
    Iterator getConnections(BasicResultFilter filter);
    void addConnection(Connection conn);
    void removeConnection(Connection conn);
    ConnectionMonitor getConnectedMonitor();

    /**
     * Connection configuration monitors records any changes in a connection's
     * configuration for administration and use in determining runtime behavior.<p>
     *
     * Samples are the number of connection changes and the rate is measured
     * in connections per second. Although some administration events may
     * change connection configurations, it is expected that the primary events
     * recorded are in protocol-level connection state changes such as the
     * establishment of TLS over a previously insecure connection via SASL.
     */
    ConnectionMonitor getConfigMonitor();

    ConnectionMonitor getDisconnectedMonitor();
}