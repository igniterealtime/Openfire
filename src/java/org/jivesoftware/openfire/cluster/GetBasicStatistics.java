/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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
public class GetBasicStatistics implements ClusterTask<Map<String, Object>> {
    public static final String NODE = "node";
    public static final String CLIENT = "client";
    public static final String INCOMING = "incoming";
    public static final String OUTGOING = "outgoing";
    public static final String MEMORY_CURRENT = "memory_cur";
    public static final String MEMORY_MAX = "memory_max";

    private Map<String, Object> values;

    @Override
    public Map<String, Object> getResult() {
        return values;
    }

    @Override
    public void run() {
        SessionManager manager = SessionManager.getInstance();
        values = new HashMap<>();
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

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        // Ignore
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        // Ignore
    }
}
