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

package org.jivesoftware.net.spi;

import org.jivesoftware.net.Connection;
import org.jivesoftware.net.ConnectionMonitor;
import org.jivesoftware.net.spi.BasicTransientMonitor;
import org.jivesoftware.net.Connection;

/**
 * Transient (in-memory) implementation of a connection monitor.
 *
 * @author Iain Shigeoka
 */
public class TransientConnectionMonitor
        extends BasicTransientMonitor
        implements ConnectionMonitor {

    public void addSample(Connection conn) {
        addSample(1);
    }
}
