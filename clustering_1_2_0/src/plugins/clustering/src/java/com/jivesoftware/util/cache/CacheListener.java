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

import com.tangosol.util.MapEvent;
import com.tangosol.util.MapListener;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.NodeID;

import java.util.Set;

/**
 * Base listener for cache events in the cluster. This class helps keep track
 * of nodes and their elements. The actual tracking information is kept in
 * {@link ClusterListener}. This information is then used when a node goes
 * down to proper clean up can be done
 *
 * @author Pete Matern
 * @author Gaston Dombiak
 */
abstract class CacheListener implements MapListener {
    protected final String cacheName;
    private ClusterListener clusterListener;

    public CacheListener(ClusterListener clusterListener, String cacheName) {
        this.clusterListener = clusterListener;
        this.cacheName = cacheName;
    }

    public void entryInserted(MapEvent mapEvent) {
        handleMapEvent(mapEvent, false);
    }

    public void entryUpdated(MapEvent mapEvent) {
        handleMapEvent(mapEvent, false);
    }

    public void entryDeleted(MapEvent mapEvent) {
        handleMapEvent(mapEvent, true);
    }

    void handleMapEvent(MapEvent mapEvent, boolean removal) {
        NodeID nodeID = getNodeID(mapEvent, removal);
        //ignore items which this node has added
        if (!XMPPServer.getInstance().getNodeID().equals(nodeID)) {
            Set<String> sessionJIDS = clusterListener.lookupJIDList(nodeID, cacheName);
            if (removal) {
                sessionJIDS.remove(mapEvent.getKey().toString());
            }
            else {
                sessionJIDS.add(mapEvent.getKey().toString());
            }
        }
    }

    abstract NodeID getNodeID(MapEvent mapEvent, boolean removal);
}
