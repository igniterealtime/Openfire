/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.net;

import org.jivesoftware.net.Connection;

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
