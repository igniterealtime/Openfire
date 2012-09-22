/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2009 Jive Software. All rights reserved.
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

package com.jivesoftware.util.cluster;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.util.cache.CacheFactory;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;

/**
 * A utility class which helps to gather Hazelcast stats and information.
 */
public class NodeRuntimeStats {

    private static final ResourceBundle config = ResourceBundle.getBundle("hazelcast-runtime");
    
    public static String getProviderConfig(String key) {
    	return config.getString(key);
    }

    /**
     * Returns a Map of HazelcastRuntimeStats.NodeInfo objects keyed by cluster Member objects.
     * A NodeInfo object is a collection of various Node stats.
     *
     * @return a Map of NodeInfo objects.
     */
    public static Map<NodeID, NodeInfo> getNodeInfo() {

        // Run cluster-wide stats query
    	Collection<Object> taskResult = CacheFactory.doSynchronousClusterTask(new NodeInfoTask(), true);
    	Map<NodeID, NodeInfo> result = new HashMap<NodeID, NodeInfo>();
    	for (Object tr : taskResult) {
    		NodeInfo nodeInfo = (NodeInfo) tr;
    		NodeID nodeId = NodeID.getInstance(nodeInfo.getNodeId());
    		result.put(nodeId, nodeInfo);
    	}
    	return result;
    }

    /**
     * Clears the cache stats.
     */
    public static void clearCacheStats() {
    	// not supported
    }

    /**
     * Encapsulates statistics and information about a cluster node.
     */
    public static class NodeInfoTask implements ClusterTask {
    	private Object result = null;
		@Override
		public void run() {
            // Get runtime stats - mem and time:
            Runtime runtime = Runtime.getRuntime();
            long free = runtime.freeMemory();
            long total = runtime.totalMemory();
            long max = runtime.maxMemory();
            long time = System.currentTimeMillis();

            result = new NodeInfo(CacheFactory.getClusterMemberID(), free, total, max, time);
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			ExternalizableUtil.getInstance().writeSerializable(out, (NodeInfo) result);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			result = ExternalizableUtil.getInstance().readSerializable(in);
		}

		@Override
		public Object getResult() { return result; }
    	
    }

    /**
     * Encapsulates statistics and information about a cluster node.
     */
    public static class NodeInfo implements Serializable {

        private byte[] nodeId;
        private long freeMem;
        private long totalMem;
        private long maxMem;
        private long time;

        NodeInfo(byte[] nodeId, long freeMem, long totalMem, long maxMem, long time)
        {
        	this.nodeId = nodeId;
            this.freeMem = freeMem;
            this.totalMem = totalMem;
            this.maxMem = maxMem;
            this.time = time;
        }

        /**
         * Returns the amount of free memory in the cluster node's VM (in bytes).
         *
         * @return the amount of free memory on the cluster node.
         */
        public byte[] getNodeId() {
            return nodeId;
        }

        /**
         * Returns the amount of free memory in the cluster node's VM (in bytes).
         *
         * @return the amount of free memory on the cluster node.
         */
        public long getFreeMem() {
            return freeMem;
        }

        /**
         * Returns the total amount of memory in the cluster node's VM (in bytes).
         *
         * @return the total amount of memory on the cluster node.
         */
        public long getTotalMem() {
            return totalMem;
        }

        /**
         * Returns the max amount of memory in the cluster node's VM (in bytes).
         *
         * @return the max amount of memory on the cluster node.
         */
        public long getMaxMem() {
            return maxMem;
        }

        /**
         * Returns the current time on the cluster node in long format. This is useful
         * monitoring information for applications that require the local times of each
         * cluster member be to roughly in-synch (on top of the standard Hazelcast
         * cluster time).<p>
         *
         * This value will always be somewhat inaccurate due to network delays, etc, so
         * should only be taken as an approximate value.
         *
         * @return the local time of the cluster node.
         */
        public long getTime() {
            return time;
        }

    }
}
