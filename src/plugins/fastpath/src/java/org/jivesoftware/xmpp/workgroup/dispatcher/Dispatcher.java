/**
 * $RCSfile$
 * $Revision: 19280 $
 * $Date: 2005-07-11 18:11:05 -0700 (Mon, 11 Jul 2005) $
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

package org.jivesoftware.xmpp.workgroup.dispatcher;

import org.jivesoftware.xmpp.workgroup.DbProperties;
import org.jivesoftware.xmpp.workgroup.RequestQueue;
import org.jivesoftware.xmpp.workgroup.UnauthorizedException;
import org.jivesoftware.xmpp.workgroup.WorkgroupResultFilter;
import org.jivesoftware.xmpp.workgroup.request.Request;
import org.jivesoftware.util.NotFoundException;

import java.util.Iterator;

/**
 * <p>Dispatchers take requests from the workgroup request queue,
 * decide which agents receive offers, and make those offers.</p>
 * <p>A single dispatcher is dedicated to each workgroup request
 * queue (each workgroup request queue can have a different dispatcher).
 * You can change the workgroup request queue's dispatch behavior by
 * implementing and installing your own, custom dispatcher.</p>
 *
 * @author Derek DeMoro
 */
public interface Dispatcher extends DbProperties {

    /**
     * Returns the DispatcherInfo for this dispatcher.
     *
     * @return The dispatcher's info
     */
    DispatcherInfo getDispatcherInfo();

    /**
     * Sets the dispatcher's info.
     *
     * @param info The dispatcher's new info
     * @throws NotFoundException if the associated queue could not be found.
     * @throws UnauthorizedException if not allowed to set dispatcher info.
     */
    void setDispatcherInfo(DispatcherInfo info) throws NotFoundException, UnauthorizedException;

    /**
     * Returns the number of offers currently made.
     *
     * @return The number of outstanding offers
     */
    int getOfferCount();

    /**
     * <p>Obtain an iterator over the offers currently outstanding.</p>
     *
     * @return An iterator over Offers currently being made
     */
    Iterator getOffers();

    /**
     * <p>Obtain an iterator over the offers currently outstanding with results filtered by given filter.</p>
     *
     * @param filter The filter to apply to the search results
     * @return An iterator over Offers currently being made
     */
    Iterator getOffers(WorkgroupResultFilter filter);

    /**
     * Returns the algorithm that selects the best agent of the queue for receiving an offer.
     *
     * @return the algorithm that selects the best agent of the queue for receiving an offer.
     */
    AgentSelector getAgentSelector();

    /**
     * Sets the algorithm that selects the best agent of the queue for receiving an offer.
     *
     * @param agentSelector the algorithm that selects the best agent of the queue for receiving an offer.
     */
    void setAgentSelector(AgentSelector agentSelector);

    /**
     * Injects a request to be immediately processed. The request is not added to the {@link RequestQueue}.
     *
     * @param request the request to be processed.
     */
    void injectRequest(Request request);
}