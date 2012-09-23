/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007-2009 Jive Software. All rights reserved.
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

package com.jivesoftware.util.cache;

import java.util.Set;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.NodeID;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.EntryListener;

/**
 * Base listener for cache events in the cluster. This class helps keep track
 * of nodes and their elements. The actual tracking information is kept in
 * {@link ClusterListener}. This information is then used when a node goes
 * down to proper clean up can be done.
 *
 * @author Tom Evans
 * @author Pete Matern
 * @author Gaston Dombiak
 */
class CacheListener implements EntryListener {
    protected final String cacheName;
    private ClusterListener clusterListener;

    public CacheListener(ClusterListener clusterListener, String cacheName) {
        this.clusterListener = clusterListener;
        this.cacheName = cacheName;
    }

    public void entryAdded(EntryEvent event) {
        handleMapEvent(event, false);
    }

    public void entryUpdated(EntryEvent event) {
        handleMapEvent(event, false);
    }

    public void entryRemoved(EntryEvent event) {
        handleMapEvent(event, true);
    }

    public void entryEvicted(EntryEvent event) {
        handleMapEvent(event, true);
    }

    void handleMapEvent(EntryEvent event, boolean removal) {
        NodeID nodeID = NodeID.getInstance(event.getMember().getUuid().getBytes());
        //ignore items which this node has added
        if (!XMPPServer.getInstance().getNodeID().equals(nodeID)) {
            Set<String> sessionJIDS = clusterListener.lookupJIDList(nodeID, cacheName);
            if (removal) {
                sessionJIDS.remove(event.getKey().toString());
            }
            else {
                sessionJIDS.add(event.getKey().toString());
            }
        }
    }

}
