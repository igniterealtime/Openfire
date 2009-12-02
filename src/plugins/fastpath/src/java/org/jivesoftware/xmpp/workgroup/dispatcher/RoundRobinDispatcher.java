/**
 * $RCSfile$
 * $Revision: 32833 $
 * $Date: 2006-08-02 15:52:36 -0700 (Wed, 02 Aug 2006) $
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;

import org.jivesoftware.openfire.fastpath.util.TaskEngine;
import org.jivesoftware.openfire.fastpath.util.WorkgroupUtils;
import org.jivesoftware.util.BeanUtils;
import org.jivesoftware.util.ClassUtils;
import org.jivesoftware.util.ConcurrentHashSet;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.xmpp.workgroup.AgentSession;
import org.jivesoftware.xmpp.workgroup.AgentSessionList;
import org.jivesoftware.xmpp.workgroup.AgentSessionListener;
import org.jivesoftware.xmpp.workgroup.Offer;
import org.jivesoftware.xmpp.workgroup.RequestQueue;
import org.jivesoftware.xmpp.workgroup.UnauthorizedException;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.WorkgroupResultFilter;
import org.jivesoftware.xmpp.workgroup.request.Request;
import org.jivesoftware.xmpp.workgroup.request.UserRequest;
import org.jivesoftware.xmpp.workgroup.spi.JiveLiveProperties;
import org.jivesoftware.xmpp.workgroup.spi.dispatcher.DbDispatcherInfoProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>Implements simple round robin dispatching of offers to agents.</p>
 * <p>Agents are offered requests one at a time with no agent being offer
 * the same request twice (unless their current-chats status changes).</p>
 *
 * @author Derek DeMoro
 * @author Iain Shigeoka
 */
public class RoundRobinDispatcher implements Dispatcher, AgentSessionListener {
	
	private static final Logger Log = LoggerFactory.getLogger(RoundRobinDispatcher.class);
			
    /**
     * <p>The circular list of agents in the pool.</p>
     */
    private List<AgentSession> agentList;

    private RequestQueue queue;

    /**
     * <p>Prop manager for the dispatcher.</p>
     */
    private JiveLiveProperties properties;
    private DispatcherInfo info;
    private DispatcherInfoProvider infoProvider = new DbDispatcherInfoProvider();
    private AgentSelector agentSelector = WorkgroupUtils.getAvailableAgentSelectors().get(0);
    /**
     * A set of all outstanding offers in the workgroup<p>
     *
     * Let's the server route offer responses to the correct offer.
     */
    private ConcurrentHashSet<Offer> offers = new ConcurrentHashSet<Offer>();

    /**
     * Creates a new dispatcher for the queue. The dispatcher will have a Timer with a unique task
     * that will get the requests from the queue and will try to send an offer to the agents.
     *
     * @param queue the queue that contains the requests and the agents that may attend the
     *        requests.
     */
    public RoundRobinDispatcher(RequestQueue queue) {
        this.queue = queue;
        agentList = new LinkedList<AgentSession>();
        properties = new JiveLiveProperties("fpDispatcherProp", queue.getID());
        try {
            info = infoProvider.getDispatcherInfo(queue.getWorkgroup(), queue.getID());
        }
        catch (NotFoundException e) {
            Log.error("Queue ID " + queue.getID(), e);
        }
        // Recreate the agentSelector to use for selecting the best agent to receive the offer
        loadAgentSelector();

        // Fill the list of AgentSessions that are active in the queue.  Once the list has been
        // filled this dispatcher will be notified when new AgentSessions join the queue or leave
        // the queue
        fillAgentsList();

        TaskEngine.getInstance().scheduleAtFixedRate(new TimerTask() {
            public void run() {
                checkForNewRequests();
            }
        }, 2000, 2000);
    }

    private void checkForNewRequests() {
        for(Request request : queue.getRequests()){
            // While there are requests pendings try to dispatch an offer for the request to an agent
            // Skip this request if there exists an offer for this requests that is being processed
            if (request.getOffer() != null && offers.contains(request.getOffer())) {
                continue;
            }
            injectRequest(request);
        }
    }

    public void injectRequest(Request request) {
        // Create a new Offer for the request and add it to the list of active offers
        final Offer offer = new Offer(request, queue, getAgentRejectionTimeout());
        offer.setTimeout(info.getOfferTimeout());
        offers.add(offer);
        // Process this offer in another thread
        Thread offerThread = new Thread("Dispatch offer - queue: " + queue.getName()) {
            public void run() {
                dispatch(offer);
                // Remove this offer from the list of active offers
                offers.remove(offer);
            }
        };
        offerThread.start();
    }

    /**
     * Dispatch the given request to one or more agents in the agent pool.<p>
     *
     * If this method returns, it is assumed that the request was properly
     * dispatched.The only exception is if an agent is not in the pool for routing
     * within the agent timeout period, the dispatch will throw an AgentNotFoundException
     * so the request can be re-routed.
     *
     * @param offer the offer to send to the best agent available.
     */
    public void dispatch(Offer offer) {
        // The time when the request should timeout
        long timeoutTime = System.currentTimeMillis() + info.getRequestTimeout();
        final Request request = offer.getRequest();
        boolean canBeInQueue = request instanceof UserRequest;
        Map<String,List<String>> map = request.getMetaData();
        String initialAgent = map.get("agent") == null || map.get("agent").isEmpty() ? null : map.get("agent").get(0);
        String ignoreAgent = map.get("ignore") == null || map.get("ignore").isEmpty() ? null : map.get("ignore").get(0);
        // Log debug trace
        Log.debug("RR - Dispatching request: " + request + " in queue: " + queue.getAddress());

        // Send the offer to the best agent. While the offer has not been accepted send it to the
        // next best agent. If there aren't any agent available then skip this section and proceed
        // to overflow the current request
        if (!agentList.isEmpty()) {
            for (long timeRemaining = timeoutTime - System.currentTimeMillis();
                 !offer.isAccepted() && timeRemaining > 0 && !offer.isCancelled();
                 timeRemaining = timeoutTime - System.currentTimeMillis()) {

                try {
                    AgentSession session = getBestNextAgent(initialAgent, ignoreAgent, offer);
                    if (session == null && agentList.isEmpty()) {
                        // Stop looking for an agent since there are no more agent available
                         break;
                    }
                    else if (session == null || offer.isRejector(session)) {
                        initialAgent = null;
                        Thread.sleep(1000);
                    }
                    else {
                        // Recheck for changed maxchat setting
                        Workgroup workgroup = request.getWorkgroup();
                        if (session.getCurrentChats(workgroup) < session.getMaxChats(workgroup)) {
                            // Set the timeout of the offer based on the remaining time of the
                            // initial request and the default offer timeout
                            timeRemaining = timeoutTime - System.currentTimeMillis();
                            offer.setTimeout(timeRemaining < info.getOfferTimeout() ?
                                    timeRemaining : info.getOfferTimeout());

                            // Make the offer and wait for a resolution to the offer
                            if (!request.sendOffer(session, queue)) {
                                // Log debug trace
                                Log.debug("RR - Offer for request: " + offer.getRequest() +
                                        " FAILED TO BE SENT to agent: " +
                                        session.getJID());
                                continue;
                            }
                            // Log debug trace
                            Log.debug("RR - Offer for request: " + offer.getRequest() + " SENT to agent: " +
                                    session.getJID());

                            offer.waitForResolution();
                            // If the offer was accepted, we send out the invites
                            // and reset the offer
                            if (offer.isAccepted()) {
                                // Get the first agent that accepted the offer
                                AgentSession selectedAgent = offer.getAcceptedSessions().get(0);
                                // Log debug trace
                                Log.debug("RR - Agent: " + selectedAgent.getJID() +
                                        " ACCEPTED request: " +
                                        request);
                                // Create the room and send the invitations
                                offer.invite(selectedAgent);
                                // Notify the agents that accepted the offer that the offer process
                                // has finished
                                for (AgentSession agent : offer.getAcceptedSessions()) {
                                    agent.removeOffer(offer);
                                }
                                if (canBeInQueue) {
                                    // Remove the user from the queue since his request has
                                    // been accepted
                                    queue.removeRequest((UserRequest) request);
                                }
                            }
                        }
                        else {
                            // Log debug trace
                            Log.debug("RR - Selected agent: " + session.getJID() +
                                    " has reached max number of chats");
                        }
                    }
                }
                catch (Exception e) {
                    Log.error(e.getMessage(), e);
                }
            }
        }
        if (!offer.isAccepted() && !offer.isCancelled()) {
            // Calculate the maximum time limit for an unattended request before cancelling it
            long limit = request.getCreationTime().getTime() +
                    (info.getRequestTimeout() * (getOverflowTimes() + 1));
            if (limit - System.currentTimeMillis() <= 0 || !canBeInQueue) {
                // Log debug trace
                Log.debug("RR - Cancelling request that maxed out overflow limit or cannot be queued: " + request);
                // Cancel the request if it has overflowed 'n' times
                request.cancel(Request.CancelType.AGENT_NOT_FOUND);
            }
            else {
                // Overflow if request timed out and was not dispatched and max number of overflows
                // has not been reached yet
                overflow(offer);
                // If there is no other queue to overflow then cancel the request
                if (!offer.isAccepted() && !offer.isCancelled()) {
                    // Log debug trace
                    Log.debug("RR - Cancelling request that didn't overflow: " + request);
                    request.cancel(Request.CancelType.AGENT_NOT_FOUND);
                }
            }
        }
    }

    /**
     * <p>Overflow the current request into another queue if possible.</p>
     * <p/>
     * <p>Future versions of the dispatcher may wish to overflow in
     * more sophisticated ways. Currently we do it according to overflow
     * rules: none (no overflow), backup (to a backup if it exists and is
     * available, or randomly.</p>
     *
     * @param offer the offer to place in the overflow queue.
     */
    private void overflow(Offer offer) {
        RequestQueue backup = null;
        if (RequestQueue.OverflowType.OVERFLOW_BACKUP.equals(queue.getOverflowType())) {
            backup = queue.getBackupQueue();
            // Check that the backup queue has agents available otherwise discard it
            if (backup != null && !backup.getAgentSessionList().containsAvailableAgents()) {
                backup = null;
            }
        }
        else if (RequestQueue.OverflowType.OVERFLOW_RANDOM.equals(queue.getOverflowType())) {
            backup = getRandomQueue();
        }
        // If a backup queue was found then cancel this offer, remove the request from the queue
        // and add the request in the backup queue
        if (backup != null) {
            offer.cancel();
            UserRequest request = (UserRequest) offer.getRequest();
            // Remove the request from the queue since it is going to be added to another
            // queue
            queue.removeRequest(request);
            // Log debug trace
            Log.debug("RR - Overflowing request: " + request + " to queue: " +
                    backup.getAddress());
            backup.addRequest(request);
        }
    }

    /**
     * Returns a queue that was randomly selected.
     *
     * @return a queue that was randomly selected.
     */
    private RequestQueue getRandomQueue() {
        int qCount = queue.getWorkgroup().getRequestQueueCount();
        if (qCount > 1) {
            // Build a list of all queues eligible for overflow
            LinkedList<RequestQueue> overflowQueueList = new LinkedList<RequestQueue>();
            for (RequestQueue overflowQueue : queue.getWorkgroup().getRequestQueues()) {
                if (!queue.equals(overflowQueue) && overflowQueue.getAgentSessionList().containsAvailableAgents()) {
                    overflowQueueList.addLast(overflowQueue);
                }
            }

            // If there are any eligible queues
            if (overflowQueueList.size() > 0) {
                // choose the random index of the overflow queue to use
                int targetIndex = (int) Math.floor(((float) (overflowQueueList.size())) * Math.random());
                if (targetIndex < overflowQueueList.size()) {
                    return overflowQueueList.get(targetIndex);
                }
            }
        }
        return null;
    }

    /**
     * <p>Locate the next 'best' agent to receive an offer.</p>
     * <p>Routing is based on show-status, max-chats, and who has
     * already rejected the offer.
     * show status is ranked from most available to least:
     * chat, default (no show status), away,
     * and xa. A show status of dnd indicates no offers should be routed to an agent.
     * The general algorithm is:</p>
     * <ul>
     * <li>Mark the current position.</li>
     * <li>Start iterating around the circular queue until all agents
     * have been considered. For each agent:
     * <ul>
     * <li>Skip if session is null. Should only occur if no agents are in the list.</li>
     * <li>Skip if session show state is DND. Never route to agents that are dnd.</li>
     * <li>Skip if session current-chats is equal to or higher than max-chats.</li>
     * <li>Replace current best if:
     * <ul>
     * <li>No current best. Any agent is better than none.</li>
     * <li>If session hasn't rejected offer but current best has.</li>
     * <li>If both session and current best have not rejected the
     * offer and session show-status is higher.</li>
     * <li>If both session and current best have rejected offer and
     * session show-status is higher.</li>
     * </ul></li>
     * </ul></li>
     * </li>
     *
     * @param initialAgent the initial agent requested by the user.
     * @param ignoreAgent agent that should not be considered as available.
     * @param offer the offer about to be sent to the best available agent.
     * @return the best agent.
     */
    private AgentSession getBestNextAgent(String initialAgent, String ignoreAgent, Offer offer) {
        AgentSession bestSession;

        // Look for specified agent in agent list
        if (initialAgent != null) {
            final AgentSessionList agentSessionList = queue.getAgentSessionList();
            for (AgentSession agentSession : agentSessionList.getAgentSessions()) {
                String sessionAgent = agentSession.getAgent().getAgentJID().toBareJID();
                boolean match = sessionAgent.startsWith(initialAgent.toLowerCase());
                Workgroup workgroup = offer.getRequest().getWorkgroup();
                if (agentSession.isAvailableToChat() &&
                        agentSession.getCurrentChats(workgroup) < agentSession.getMaxChats(workgroup) && match) {
                    bestSession = agentSession;
                    // Log debug trace
                    Log.debug("RR - Initial agent: " + bestSession.getJID() +
                            " will receive offer for request: " +
                            offer.getRequest());
                    return bestSession;
                }
            }
        }

        // Let's iterate through each agent and check availability
        final AgentSessionList agentSessionList = queue.getAgentSessionList();
        final List<AgentSession> possibleSessions = new ArrayList<AgentSession>();
        for (AgentSession agentSession : agentSessionList.getAgentSessions()) {
            String sessionAgent = agentSession.getAgent().getAgentJID().toBareJID();
            boolean ignore = ignoreAgent != null && sessionAgent.startsWith(ignoreAgent.toLowerCase());
            if (!ignore && validateAgent(agentSession, offer)) {
                possibleSessions.add(agentSession);
            }
        }

        // Select the best agent from the list of possible agents
        if (possibleSessions.size() > 0) {
            AgentSession s = agentSelector.bestAgentFrom(possibleSessions, offer);
            // Log debug trace
            Log.debug("RR - Agent SELECTED: " + s.getJID() +
                    " for receiving offer for request: " +
                    offer.getRequest());
            return s;
        }
        return null;
    }

    /**
     * Returns true if the agent session may receive an offer. An agent session may receive new
     * offers if:
     *
     * 1) the presence status of the agent allows to receive offers
     * 2) the maximum of chats has not been reached for the agent
     * 3) the agent has not rejected the offer before
     * 4) the agent does not have to answer a previuos offer
     *
     * @param session the session to check if it may receive an offer
     * @param offer the offer to send.
     * @return true if the agent session may receive an offer.
     */
    private boolean validateAgent(AgentSession session, Offer offer) {
        if (agentSelector.validateAgent(session, offer)) {
            // Log debug trace
            Log.debug("RR - Agent: " + session.getJID() +
                    " MAY receive offer for request: " +
                    offer.getRequest());
            return true;
        }
        // Log debug trace
        Log.debug("RR - Agent: " + session.getJID() +
                " MAY NOT receive offer for request: " +
                offer.getRequest());
        return false;
    }

    /**
     * <p>Generate the agents offer list.</p>
     */
    private void fillAgentsList() {
        AgentSessionList agentSessionList = queue.getAgentSessionList();
        agentSessionList.addAgentSessionListener(this);
        for (AgentSession agentSession : agentSessionList.getAgentSessions()) {
            if (!agentList.contains(agentSession)) {
                agentList.add(agentSession);
            }
        }
    }

    /**
     * Returns the max number of times a request may overflow. Once the request has exceded this
     * number it will be cancelled. This limit avoids infinite overflow loops.
     *
     * @return the max number of times a request may overflow.
     */
    private long getOverflowTimes() {
        return JiveGlobals.getIntProperty("xmpp.live.request.overflow", 3);
    }

    /**
     * Returns the number of milliseconds to wait until expiring an agent rejection.
     *
     * @return the number of milliseconds to wait until expiring an agent rejection.
     */
    private long getAgentRejectionTimeout() {
        return JiveGlobals.getIntProperty("xmpp.live.rejection.timeout", 20000);
    }

    public void notifySessionAdded(AgentSession session) {
        if (!agentList.contains(session)) {
            agentList.add(session);
        }
    }

    public void notifySessionRemoved(AgentSession session) {
        agentList.remove(session);
        for (Offer offer : offers) {
            offer.reject(session);
        }
    }

    public DispatcherInfo getDispatcherInfo() {
        return info;
    }

    public void setDispatcherInfo(DispatcherInfo info) throws UnauthorizedException {
        try {
            infoProvider.updateDispatcherInfo(queue.getID(), info);
            this.info = info;
        }
        catch (NotFoundException e) {
            Log.error(e.getMessage(), e);
        }
        catch (UnsupportedOperationException e) {
            Log.error(e.getMessage(), e);
        }
    }

    public int getOfferCount() {
        return offers.size();
    }

    public Iterator<Offer> getOffers() {
        return offers.iterator();
    }

    public Iterator<Offer> getOffers(WorkgroupResultFilter filter) {
        return filter.filter(offers.iterator());
    }

    public String getProperty(String name) {
        return properties.getProperty(name);
    }

    public void setProperty(String name, String value) throws UnauthorizedException {
        properties.setProperty(name, value);
    }

    public void deleteProperty(String name) throws UnauthorizedException {
        properties.deleteProperty(name);
    }

    public Collection<String> getPropertyNames() {
        return properties.getPropertyNames();
    }

    public AgentSelector getAgentSelector() {
        return agentSelector;
    }

    public void setAgentSelector(AgentSelector agentSelector) {
        this.agentSelector = agentSelector;
        // Delete all agentSelectorproperties.
        try {
            for (String property : getPropertyNames()) {
               if (property.startsWith("agentSelector")) {
                   deleteProperty(property);
               }
            }
        }
        catch (Exception e) {
           Log.error(e.getMessage(), e);
       }
        // Save the agentSelectoras a property of the dispatcher
        try {
            Map<String, String> propertyMap = getPropertiesMap(agentSelector, "agentSelector.");
            for (Map.Entry<String, String> entry : propertyMap.entrySet()) {
                setProperty(entry.getKey(), entry.getValue());
            }
        }
        catch (Exception e) {
           Log.error(e.getMessage(), e);
       }
    }

    private Map<String, String> getPropertiesMap(AgentSelector agentSelector, String context) {
        // Build the properties map that will be saved later
        Map<String, String> propertyMap = new HashMap<String, String>();
        // Write out class name
        propertyMap.put(context + "className", agentSelector.getClass().getName());

        // Write out all properties
        Map<String, String> props = BeanUtils.getProperties(agentSelector);
        for (Map.Entry<String, String> entry : props.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            if (value != null && !"".equals(value)) {
                propertyMap.put(context + "properties." + name, value);
            }
        }
        return propertyMap;
    }

    private void loadAgentSelector() {
        try {
            String context = "agentSelector.";
            String className = getProperty(context + "className");
            if (className == null) {
                // Do nothing and use the BasicAgentSelector
                return;
            }
            Class agentSelectorClass = loadClass(className);
            agentSelector = (AgentSelector) agentSelectorClass.newInstance();

            // Load properties.
            Collection<String> props = getChildrenPropertyNames(context + "properties", getPropertyNames());
            Map<String, String> agentSelectorProps = new HashMap<String, String>();

            for (String key : props) {
                String value = getProperty(key);
                // Get the bean property name, which is everything after the last '.' in the
                // xml property name.
                agentSelectorProps.put(key.substring(key.lastIndexOf(".")+1), value);
            }

            // Set properties on the bean
            BeanUtils.setProperties(agentSelector, agentSelectorProps);
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
    }

    private Class loadClass(String className) throws ClassNotFoundException {
        try {
            return ClassUtils.forName(className);
        }
        catch (ClassNotFoundException e) {
            return this.getClass().getClassLoader().loadClass(className);
        }
    }

    /**
     * Returns a child property names given a parent and an Iterator of property names.
     *
     * @param parent parent property name.
     * @param properties all property names to search.
     * @return an Iterator of child property names.
     */
    private static Collection<String> getChildrenPropertyNames(String parent, Collection<String> properties) {
        List<String> results = new ArrayList<String>();
        for (String name : properties) {
            if (name.startsWith(parent) && !name.equals(parent)) {
                results.add(name);
            }
        }
        return results;
    }

    public void shutdown() {
        // Do nothing
    }

}
