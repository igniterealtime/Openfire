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

/**
 * <p>ConnectionMonitors record the activity of Connections for administration
 * and use in determining runtime behavior.</p>
 *
 * <p>Samples are the number of connection events and the rate is measured
 * in connection events per second.</p>
 *
 * @author Iain Shigeoka
 */
public interface ConnectionMonitor extends Monitor {
    /**
     * <p>Adds a connection sample to the monitor as soon as
     * the connection is accepted.</p>
     *
     * @param conn the connection made.
     */
    void addSample(Connection conn);
}
