/**
 * $RCSfile$
 * $Revision: 19196 $
 * $Date: 2005-06-30 14:15:20 -0700 (Thu, 30 Jun 2005) $
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

package org.jivesoftware.xmpp.workgroup;

import org.jivesoftware.xmpp.workgroup.request.Request;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * <p>A simple, in-memory implementation of an Offer.</p>
 * <p/>
 * <p>Offers are not designed to survive the shutdown of the server.</p>
 *
 * @author Derek DeMoro
 */
public class Offer {
    /**
     * User associated with this Offer
     */
    private Request request;
    /**
     * RequestQueue that is sending this offer to the queue's agents.
     */
    private RequestQueue queue;
    /**
     * AgentSessions that received this offer and have not yet answered. Once the invitation has
     * been sent this list will be cleared.
     */
    private List<AgentSession> pendingSessions = new CopyOnWriteArrayList<AgentSession>();
    /**
     * AgentSessions that accepted this offer and one of them will be choosen to start a
     * conversation. Once the invitation has been sent this list will be cleared.
     */
    private List<AgentSession> acceptedSessions = new CopyOnWriteArrayList<AgentSession>();
    /**
     * The (server) time the Offer was made to the agent, initialized in constructor.
     */
    private Date offerTime;
    /**
     * The timeout of the Offer in milliseconds or -1 for no timeout.
     */
    private long timeout = 20000;
    /**
     * The set of agents that have rejected this Offer.
     */
    private Set<String> rejections = new HashSet<String>();
    // ########################################################################
    /**
     * <p>Temporary auto re-offer timeouts for rejectors.</p>
     * <p/>
     * <p>Matt wants agent rejectors to be automatically re-offered
     * offers after a timeout regardless of the agent's actions.
     * The motivation is to ensure offers don't stay idle in the queue
     * waiting for an agent to change their status to get another offer.</p>
     * <p/>
     * <p>The proper behavior shoud be to have the agent app notifyEvent the server
     * when the agent is ready to accept new offers (or be re-offered old ones)
     * at user defined times. Manually is probably preferred (press button on
     * app to indicate "ready for next". Or could be automatic via a timer.
     * In either case driving this from the server is a poor solution
     * and should be fixed ASAP.</p>
     */
    private Map<String, Date> rejectionTimes = new HashMap<String, Date>();

    /**
     * The length in milliseconds before automatically removing an agent
     * from the rejector list.
     */
    private long rejectionTimeout;

    /**
     * Flag indicating the offer has been cancelled or not.
     */
    private boolean cancelled;

    /**
     * Flag indicating an invitation was sent to an agent or not. An invitation will be sent after
     * an agent accepted the offer and was chosen to atend the user's request.
     */
    private boolean invitationSent;

    /**
     * Defined States *
     */
    public static final int USER_CANCELLED = 0;
    public static final int ROUTE_EXPIRED = 1;
    public static final int ROUTED = 2;


    /**
     * Create an offer based on the request.
     *
     * @param request The request this offer is for
     * @param queue   The request queue that is sending this offer to the agents.
     * @param rejectionTimeout the number of milliseconds to wait until expiring an agent rejection.
     */
    public Offer(Request request, RequestQueue queue, long rejectionTimeout) {
        this.request = request;
        this.queue = queue;
        this.rejectionTimeout = rejectionTimeout;
        offerTime = new Date();
        cancelled = false;
        invitationSent = false;
        request.setOffer(this);
    }

    public Request getRequest() {
        return request;
    }

    public boolean isAccepted() {
        return !acceptedSessions.isEmpty();
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void accept(AgentSession agentSession) {
        acceptedSessions.add(agentSession);
        pendingSessions.remove(agentSession);
    }

    public void reject(AgentSession agentSession) {
        if (pendingSessions.contains(agentSession)) {
            addRejector(agentSession);
            pendingSessions.remove(agentSession);
            agentSession.removeOffer(this);
        }
    }

    private void addRejector(AgentSession agentSession) {
        rejections.add(agentSession.getJID().toBareJID());
        rejectionTimes.put(agentSession.getJID().toBareJID(), new Date());
    }

    public void removeRejector(AgentSession agentSession) {
        rejections.remove(agentSession.getJID().toBareJID());
        rejectionTimes.remove(agentSession.getJID().toBareJID());
    }

    public boolean isRejector(AgentSession agentSession) {
        Date rejectionTime = rejectionTimes.get(agentSession.getJID().toBareJID());
        boolean rejector = false;
        if (rejectionTime != null) {
            if (rejectionTime.getTime() > System.currentTimeMillis() - rejectionTimeout) {
                rejector = true;
            }
            else {
                rejectionTimes.remove(agentSession.getJID().toBareJID());
            }
        }
        return rejector;
    }

    public List<AgentSession> getAcceptedSessions() {
        return Collections.unmodifiableList(acceptedSessions);
    }

    public Collection<String> getRejections() {
        return rejections;
    }

    public Date getOfferTime() {
        return offerTime;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public void invite(AgentSession agentSession) {
        if (acceptedSessions.contains(agentSession) && request != null) {
            // Ask the workgroup to send invitations to the agent and to the user that made the
            // request. The Workgroup will create a MUC room and send invitations to the agent and
            // the user.
            request.offerAccepted(agentSession);
            updateUserSession(ROUTED);
            invitationSent = true;
        }
    }

    public void waitForResolution() {
        long timeoutTime = offerTime.getTime() + timeout;
        while (timeoutTime > System.currentTimeMillis() && !isAccepted() && !pendingSessions.isEmpty()) {
            try {
                Thread.sleep(500); // half second polling
            }
            catch (InterruptedException e) {
                // do nothing
            }
        }
        if (!isAccepted()) {
            try {
                for (AgentSession session : pendingSessions) {
                    request.sendRevoke(session, queue);
                    reject(session);
                }
            }
            catch (Exception e) {
                // Ignore
            }
        }
    }

    public void cancel() {
        cancelled = true;

        // Handle when customer cancels.
        if (!pendingSessions.isEmpty() || !acceptedSessions.isEmpty()) {
            for (AgentSession session : pendingSessions) {
                request.sendRevoke(session, queue);
            }
            for (AgentSession session : acceptedSessions) {
                request.sendRevoke(session, queue);
            }
            pendingSessions.clear();
            acceptedSessions.clear();
            updateUserSession(USER_CANCELLED);
        }
        else {
            updateUserSession(ROUTE_EXPIRED);
        }
    }

    public void addPendingSession(AgentSession agentSession) {
        pendingSessions.add(agentSession);
        // reset the Offer time
        offerTime = new Date();
    }

    /**
     * Returns true if the offer is still outstanding. An offer is considered outstanding if it has
     * not been cancelled and an invitation was not yet sent to an agent.
     *
     * @return true if the offer is still outstanding.
     */
    public boolean isOutstanding() {
        return !cancelled && !invitationSent;
    }

    public int hashCode() {
        return request.hashCode();
    }

    public boolean equals(Object obj) {
        boolean eq = false;
        if (obj instanceof Offer){
            Offer otherOffer = (Offer)obj;
            eq = request.equals(otherOffer.getRequest());
        }
        return eq;
    }

    /**
     * Updates the database tables with new session state, waitTime and metadata.
     *
     * @param state the state of this session.
     */
    private void updateUserSession(int state) {
        // Update the current session.
        request.updateSession(state, offerTime.getTime());
    }
}
