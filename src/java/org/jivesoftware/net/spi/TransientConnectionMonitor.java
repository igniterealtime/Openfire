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
package org.jivesoftware.net.spi;

import org.jivesoftware.net.Connection;
import org.jivesoftware.net.ConnectionMonitor;
import org.jivesoftware.net.spi.BasicTransientMonitor;
import org.jivesoftware.net.Connection;

/**
 * <p>Transient (in-memory) implementation of a connection monitor.</p>
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
