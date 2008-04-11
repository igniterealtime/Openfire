/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.cluster;

import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.cache.CacheFactory;
import org.jivesoftware.util.cache.ClusterTask;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashMap;
import java.util.Map;

/**
 * Command that returns basic information about this JVM like number of client sessions,
 * server sessions and amount of free and used memory.
 *
 * @author Gaston Dombiak
 */
public class GetBasicStatistics implements ClusterTask {
    public static final String NODE = "node";
    public static final String CLIENT = "client";
    public static final String INCOMING = "incoming";
    public static final String OUTGOING = "outgoing";
    public static final String MEMORY_CURRENT = "memory_cur";
    public static final String MEMORY_MAX = "memory_max";

    private Map<String, Object> values;

    public Object getResult() {
        return values;
    }

    public void run() {
        SessionManager manager = SessionManager.getInstance();
        values = new HashMap<String, Object>();
        values.put(NODE, CacheFactory.getClusterMemberID());
        // Collect number of authenticated users
        values.put(CLIENT, manager.getUserSessionsCount(true));
        // Collect number of incoming server connections
        values.put(INCOMING, manager.getIncomingServerSessionsCount(true));
        // Collect number of outgoing server connections
        values.put(OUTGOING, XMPPServer.getInstance().getRoutingTable().getServerSessionsCount());
        // Calculate free and used memory
        Runtime runtime = Runtime.getRuntime();
        double freeMemory = (double) runtime.freeMemory() / (1024 * 1024);
        double maxMemory = (double) runtime.maxMemory() / (1024 * 1024);
        double totalMemory = (double) runtime.totalMemory() / (1024 * 1024);
        double usedMemory = totalMemory - freeMemory;
        values.put(MEMORY_CURRENT, usedMemory);
        values.put(MEMORY_MAX, maxMemory);
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        // Ignore
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        // Ignore
    }
}
