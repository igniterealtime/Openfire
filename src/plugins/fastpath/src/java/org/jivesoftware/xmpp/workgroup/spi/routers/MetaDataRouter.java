/**
 * $RCSfile$
 * $Revision: 3144 $
 * $Date: 2005-12-01 14:20:11 -0300 (Thu, 01 Dec 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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
package org.jivesoftware.xmpp.workgroup.spi.routers;

import java.util.List;
import java.util.Map;

import org.jivesoftware.xmpp.workgroup.DbProperties;
import org.jivesoftware.xmpp.workgroup.RequestQueue;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.request.UserRequest;
import org.jivesoftware.xmpp.workgroup.routing.RequestRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>Routes requests to the queue with the most matching meta-data.</p>
 *
 * @author Derek DeMoro
 */
public class MetaDataRouter extends RequestRouter {

	private static final Logger Log = LoggerFactory.getLogger(MetaDataRouter.class);
	
    public MetaDataRouter() {
    }

    public String getTitle(){
        return "Live Assistant Metadata Router";
    }

    public String getDescription(){
        return "Used for routing to the best possible queue based on it's metadata name-value pairs in the queue.";
    }

    @Override
	public boolean isConfigurable(){
        return false;
    }

    @Override
	public boolean handleRequest(Workgroup workgroup, UserRequest request) {
        boolean success = false;
        Map<String,List<String>> metaData = request.getMetaData();
        if (metaData != null) {
            // Route to queue with most matching meta-data
            RequestQueue bestQueue = routeNoOverflow(workgroup, metaData);
            if (bestQueue != null) {
                setRoutingQueue(bestQueue.getID());
                success = true;
            }
        }
        return success;
    }

    private RequestQueue routeNoOverflow(Workgroup workgroup, Map<String,List<String>> metaData) {
        RequestQueue bestQueue = null;
        int bestMatch = -1;
        int currentMatch;
        for (RequestQueue requestQueue : workgroup.getRequestQueues()) {
            // Skip queues that doesn't have agents at the moment
            if (!requestQueue.isOpened()) {
                continue;
            }
            int overflowValue = requestQueue.getOverflowType().ordinal();
            switch (overflowValue) {
                case 1: // random - route to best that's available
                    // (or none if no queues are available)
                    if (requestQueue.getAgentSessionList().containsAvailableAgents()) {
                        currentMatch = calculateMatchScore(requestQueue, metaData);
                        if (currentMatch > bestMatch) {
                            bestQueue = requestQueue;
                            bestMatch = currentMatch;
                        }
                    }
                    break;
                case 2: // backup - route to best or best's backup
                    currentMatch = calculateMatchScore(requestQueue, metaData);
                    if (currentMatch > bestMatch) {
                        if (!requestQueue.getAgentSessionList().containsAvailableAgents()
                                && requestQueue.getBackupQueue() != null) {
                            // Route to backup queue if no agents available and
                            // backup queue set
                            bestQueue = requestQueue.getBackupQueue();
                        }
                        else {
                            bestQueue = requestQueue;
                        }
                        bestMatch = currentMatch;
                    }
                    break;
                default: // none - route to best
                    currentMatch = calculateMatchScore(requestQueue, metaData);
                    if (currentMatch > bestMatch) {
                        bestQueue = requestQueue;
                        bestMatch = currentMatch;
                    }
            }
        }
        return bestQueue;
    }

    /**
     * <p>Calculates the match score between the queue's properties and the
     * given info metadata</p>
     * <p/>
     * <p>Matching is done by simple comparison of the metadata name value
     * pairs, and the property name value pairs of the queue. The total
     * number of matches found is the match score.</p>
     *
     * @param queue    The queue to inspect for properties
     * @param metaData The metada to locate name value pairs
     * @return The number of matches found
     */
    private int calculateMatchScore(RequestQueue queue, Map<String,List<String>> metaData) {
        int currentMatch = 0;
        try {
            DbProperties queueProps = queue.getProperties();
            for (String name : metaData.keySet()) {
                List<String> values = metaData.get(name);
                String queueProp = queueProps.getProperty(name);
                for (String value : values) {
                    if (queueProp != null && queueProp.equalsIgnoreCase(value)) {
                        currentMatch++;
                        break;
                    }
                }
            }
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
        return currentMatch;
    }
}
