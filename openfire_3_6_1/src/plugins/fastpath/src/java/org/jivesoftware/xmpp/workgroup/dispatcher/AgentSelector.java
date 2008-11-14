/**
 * $Revision: 32923 $
 * $Date: 2006-08-04 14:53:43 -0700 (Fri, 04 Aug 2006) $
 *
 * Copyright (C) 2004-2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.xmpp.workgroup.dispatcher;

import org.jivesoftware.xmpp.workgroup.AgentSession;
import org.jivesoftware.xmpp.workgroup.Offer;

import java.util.List;

/**
 * Algorithm that selects the best agent of the queue for receiving an offer.
 *
 * @author Gaston Dombiak
 */
public interface AgentSelector {

    /**
     * Returns true if an agent session may receive an offer. Once all the agent sessions of
     * the queue have been validated
     * {@link #bestAgentFrom(List, Offer)}  will
     * be invoked to select the agent that will effectively get the offer.
     *
     * @param session the session to check if it may receive an offer.
     * @param offer the offer that the agent may receive.
     * @return true if the agent session may receive an offer.
     */
    boolean validateAgent(AgentSession session, Offer offer);

    /**
     * Returns the agent that will receive the offer from the previously validated list
     * of agents.
     *
     * @param possibleSessions the list of agents that are in condition to get the offer.
     * @param offer the offer that the agent may receive.
     * @return the agent that will receive the offer from the previously validated list
     *         of agents.
     */
    AgentSession bestAgentFrom(List<AgentSession> possibleSessions, Offer offer);

}
