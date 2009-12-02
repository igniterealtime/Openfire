/**
 * $RCSfile$
 * $Revision: 28140 $
 * $Date: 2006-03-06 17:18:46 -0800 (Mon, 06 Mar 2006) $
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.dom4j.Element;
import org.jivesoftware.openfire.fastpath.util.TaskEngine;
import org.jivesoftware.util.FastDateFormat;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.xmpp.workgroup.interceptor.InterceptorManager;
import org.jivesoftware.xmpp.workgroup.interceptor.OfferInterceptorManager;
import org.jivesoftware.xmpp.workgroup.interceptor.PacketRejectedException;
import org.jivesoftware.xmpp.workgroup.request.UserRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

/**
 * <p>A 'live' agent session.</p>
 * <p>Agent sessions are only created and maintaned for available agents
 * (although they may be show-dnd).</p>
 *
 * @author Derek DeMoro
 */
public class AgentSession {

	private static final Logger Log = LoggerFactory.getLogger(AgentSession.class);

    private static final FastDateFormat UTC_FORMAT = FastDateFormat.getInstance("yyyyMMdd'T'HH:mm:ss", TimeZone.getTimeZone("GMT+0"));

    private Presence presence;
    private Collection<Workgroup> workgroups = new ConcurrentLinkedQueue<Workgroup>();
    private Offer offer;
    /**
     * Flag that indicates if the agent requested to get information of agents of all the workgroups
     * where the agent has joined.
     */
    private boolean requestedAgentInfo = false;
    private Map<Workgroup, Queue<ChatInfo>> chatInfos = new ConcurrentHashMap<Workgroup, Queue<ChatInfo>>();
    /**
     * By default maxChats has a value of -1 which means that #getMaxChats will return
     * the max number of chats per agent defined in the workgroup. The agent may overwrite
     * the default value but the new value will not be persisted for future sessions.
     */
    private int maxChats;
    private Agent agent;

    private JID address;
    private long id;

    private Date lastChatTime;

    /**
     * @param address the XMPPAddress to create an <code>AgentSession</code>
     * @param agent   the <code>Agent</code>
     */
    public AgentSession(JID address, Agent agent) {
        this.id = -1;
        this.agent = agent;
        this.address = address;
        maxChats = -1;
        presence = new Presence();
    }

    public Presence getPresence() {
        return presence;
    }

    /**
     * Updates the presence of the AgentSession with the new received presence. The max number of
     * chats and number of current chats will be updated if that information was included in the presence.
     * If no information was provided then default values of queues, workgroups or general settings will
     * be used instead.
     *
     * @param packet the new presence sent by the agent.
     */
    public void updatePresence(Presence packet) {
        // Create a copy of the received Presence to use as the presence of the AgentSession
        Presence sessionPresence = packet.createCopy();
        // Remove the "agent-status" element from the new AgentSession's presence
        Element child = sessionPresence.getChildElement("agent-status", "http://jabber.org/protocol/workgroup");
        sessionPresence.getElement().remove(child);
        // Set the new presence to the AgentSession
        presence = sessionPresence;

        // Set the new maximum number of chats and the number of current chats to the
        // AgentSession based on the values sent within the presence (if any)
        Element elem = packet.getChildElement("agent-status", "http://jabber.org/protocol/workgroup");
        if (elem != null) {
            Iterator<Element> metaIter = elem.elementIterator();
            while (metaIter.hasNext()) {
                Element agentStatusElement = metaIter.next();
                if ("max-chats".equals(agentStatusElement.getName())) {
                    String maxChats = agentStatusElement.getText();
                    if (maxChats == null || maxChats.trim().length() == 0) {
                        setMaxChats(-1);
                    }
                    else {
                        setMaxChats(Integer.parseInt(maxChats));
                    }
                }
            }
        }
    }

    public void join(Workgroup workgroup) {
        boolean added = false;
        boolean alreadyJoined = workgroups.contains(workgroup);
        if(!alreadyJoined){
            added = workgroups.add(workgroup);
        }

        for (RequestQueue requestQueue : workgroup.getRequestQueues()) {
            if (requestQueue.isMember(getAgent())) {
                if (added) {
                    requestQueue.getAgentSessionList().addAgentSession(this);
                }
                requestQueue.sendStatus(getJID());
                requestQueue.sendDetailedStatus(getJID());
            }
        }

        updateStatus(workgroup);

        if (added) {
            workgroup.agentJoined(this);
            // Initialize the list that will hold the chats in the workgroup
            chatInfos.put(workgroup, new ConcurrentLinkedQueue<ChatInfo>());
        }
    }

    public void depart(Workgroup workgroup) {
        boolean removed = workgroups.remove(workgroup);
        if (removed) {
            for (RequestQueue requestQueue : workgroup.getRequestQueues()) {
                requestQueue.getAgentSessionList().removeAgentSession(this);
            }
            if (workgroups.isEmpty()) {
                getAgent().closeSession(getJID());
            }
            updateStatus(workgroup);
            workgroup.agentDeparted(this);
        }
    }

    public int getCurrentChats(Workgroup group) {
        return getChats(group).size();
    }

    private void updateStatus(Workgroup workgroup) {
        if (getMaxChats(workgroup) > getCurrentChats(workgroup) && offer != null) {
            offer.removeRejector(this);
        }
        sendStatusToAllAgents(workgroup);
        // When the agent becomes unavailable he will stop receiving presence updates from other
        // agents so the agent will need to request information from agents once again and discard
        // the old presences since they are outdated
        if (!presence.isAvailable()) {
            requestedAgentInfo = false;
        }
    }

    /**
     * Sends a packet to this agent with all the agents that belong to this agent's workgroup. This
     * packet will be sent only when the agent has requested it.<p>
     * <p/>
     * Once the list of agents has been sent the agent will start to receive updates when new
     * agents are added or removed from the workgroup as well as when agents become available
     * or unavailable.
     *
     * @param packet request made by agent.
     * @param workgroup the workgroup whose agents will be sent to the requester.
     */
    public void sendAgentsInWorkgroup(IQ packet, final Workgroup workgroup) {
        IQ statusPacket = IQ.createResultIQ(packet);
        Element agentStatusRequest = statusPacket.setChildElement("agent-status-request",
            "http://jabber.org/protocol/workgroup");
        for (Agent agentInWorkgroup : workgroup.getAgents()) {
            if (agentInWorkgroup == agent) {
                continue;
            }
            // Add the information of the agent
            agentStatusRequest.add(agentInWorkgroup.getAgentInfo());
        }
        // Send the response for this queue
        WorkgroupManager.getInstance().send(statusPacket);
        // Upate the flag to indicate that the agent has requested information about the other
        // agents. This implies that from now on this agent will start to receive presence
        // updates from the other workgroup agents when they become unavailable or new agents
        // join the workgroup
        requestedAgentInfo = true;

        // Send the presence of the available agents to this agent
        // Note: Execute this process in another thread since we want to release this thread as
        // soon as possible so other requests may be read and processed
        TaskEngine.getInstance().submit(new Runnable() {
            public void run() {
                try {
                    sendStatusOfAllAgents(workgroup);
                }
                catch (Exception e) {
                    Log.error("Error sending status of all agents", e);
                }
            }
        });
    }

    /**
     * Sends information of the agent to the agent that requested it.
     *
     * @param packet the original packet that made the request to obtain the agent's info.
     */
    public void sendAgentInfo(IQ packet) {
        IQ statusPacket = IQ.createResultIQ(packet);
        Element agentInfo = statusPacket.setChildElement("agent-info",
            "http://jivesoftware.com/protocol/workgroup");
        agentInfo.addElement("jid").setText(getAgent().getAgentJID().toBareJID());
        agentInfo.addElement("name").setText(getAgent().getNickname());
        // Send the response
        WorkgroupManager.getInstance().send(statusPacket);
    }

    /**
     * Sends the presence of each available agent in the workgroup to this agent.
     *
     * @param workgroup the workgroup whose agents' presences will be sent to this agent.
     */
    private void sendStatusOfAllAgents(Workgroup workgroup) {
        for (AgentSession agentSession : workgroup.getAgentSessions()) {
            if (!agentSession.getJID().equals(address)) {
                Presence statusPacket = agentSession.getPresence().createCopy();
                statusPacket.setFrom(agentSession.getJID());
                statusPacket.setTo(address);
                // Add the agent-status element
                agentSession.getAgentStatus(statusPacket,workgroup);
                workgroup.send(statusPacket);
            }
        }
    }

    /**
     * Sends this agent's status to all other agents in the Workgroup.
     *
     * @param workgroup the workgroup whose agent will be notified
     */
    private void sendStatusToAllAgents(Workgroup workgroup) {
        for (AgentSession agentSession : workgroup.getAgentSessions()) {
            // Only send presences to Agents that are available and had requested to
            // receive other agents' information
            if (agentSession.hasRequestedAgentInfo() && !agentSession.getJID().equals(address)) {
                Presence statusPacket = presence.createCopy();
                statusPacket.setFrom(address);
                statusPacket.setTo(agentSession.getJID());
                // Add the agent-status element
                getAgentStatus(statusPacket, workgroup);

                workgroup.send(statusPacket);
            }
        }
    }

    private void getAgentStatus(Presence statusPacket, Workgroup workgroup) {
        Element agentStatus = statusPacket.getElement().addElement("agent-status",
            "http://jabber.org/protocol/workgroup");
        // Add the workgroup JID as an attribute to the agent status element
        agentStatus.addAttribute("jid", workgroup.getJID().toBareJID());
        // Add max-chats element to the agent status element
        Element maxChats = agentStatus.addElement("max-chats");
        maxChats.setText(Integer.toString(getMaxChats(workgroup)));

        // Add information about the current chats to the agent status element
        Element currentChats = agentStatus.addElement("current-chats",
            "http://jivesoftware.com/protocol/workgroup");
        for (ChatInfo chatInfo : getChats(workgroup)) {
            Element chatElement = currentChats.addElement("chat");
            chatElement.addAttribute("sessionID", chatInfo.getSessionID());
            chatElement.addAttribute("userID", chatInfo.getUserID());
            chatElement.addAttribute("startTime", UTC_FORMAT.format(chatInfo.getDate()));

            // Check for question
            if (chatInfo.getQuestion() != null) {
                chatElement.addAttribute("question", chatInfo.getQuestion());
            }

            // Check for username
            if (chatInfo.getUsername() != null) {
                chatElement.addAttribute("username", chatInfo.getUsername());
            }

            // Check for email
            if (chatInfo.getEmail() != null) {
                chatElement.addAttribute("email", chatInfo.getEmail());
            }

        }
    }

    public String toString() {
        return "AI-" + Integer.toHexString(hashCode()) +
            " JID " + address.toString() +
            " CC " + Integer.toString(chatInfos.size()) +
            " MC " + Integer.toString(maxChats);
    }

    /**
     * Send an offer
     *
     * @param offer the <code>Offer</code> to send.
     * @param offerPacket the packet to send to the agent with the offer.
     * @return true if the packet was sent to the agent.
     */
    public boolean sendOffer(Offer offer, IQ offerPacket) {
        synchronized (this) {
            if (this.offer != null) {
                return false;
            }
            this.offer = offer;
        }

        try {
            offer.addPendingSession(this);

            InterceptorManager interceptorManager = OfferInterceptorManager.getInstance();
            try {
                Workgroup workgroup = offer.getRequest().getWorkgroup();
                interceptorManager.invokeInterceptors(workgroup.getJID().toBareJID(),
                    offerPacket, false, false);
                // Send the Offer to the agent
                WorkgroupManager.getInstance().send(offerPacket);
                interceptorManager.invokeInterceptors(workgroup.getJID().toBareJID(),
                    offerPacket, false, true);
            }
            catch (PacketRejectedException e) {
                Log.warn("Offer was not sent " +
                    "due to interceptor REJECTION: " + offerPacket.toXML(), e);
            }
            return true;

        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            return false;
        }
    }

    public void sendRevoke(Offer offer, IQ agentRevoke) {
        if (this.offer == null || !this.offer.equals(offer)) {
            return;
        }
        try {
            WorkgroupManager.getInstance().send(agentRevoke);
            // Clear the offer associated with this agent session
            removeOffer(offer);
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
    }

    public Agent getAgent() {
        return agent;
    }

    /**
     * Returns the <code>Workgroup</code> where this session is working.
     *
     * @return the <code>Workgroup</code> where this session is working.
     */
    public Collection<Workgroup> getWorkgroups() {
        return workgroups;
    }

    public int getMaxChats(Workgroup workgroup) {
        int max = maxChats;
        // Get the upper and lower limits
        int upper = workgroup.getMaxChats();
        int lower = workgroup.getMinChats();
        // Ensure that max chats is in the limits
        if (max == -1) {
            max = upper;
        }
        if (max < lower) {
            max = lower;
        }
        if (max > upper) {
            max = upper;
        }
        return max;
    }

    private void setMaxChats(int max) {
        maxChats = max;
    }

    // Sessions NEVER set min chats
    public void setMinChats(int min) {
    }

    /**
     * Returns a collection with the JID of the users that this agent is having a chat with.
     *
     * @param workgroup workgroup to get is chats.
     * @return a collection with the JID of the users that this agent is having a chat with.
     */
    public Collection<JID> getUsersJID(Workgroup workgroup) {
        Collection<ChatInfo> chats = getChats(workgroup);
        Collection<JID> jids = new ArrayList<JID>(chats.size());
        for (ChatInfo info : chats) {
            jids.add(info.getUserJID());
        }
        return jids;
    }

    public boolean equals(Object o) {
        boolean match = false;
        if (o instanceof AgentSession) {
            match = ((AgentSession)o).getJID().equals(address);
        }
        return match;
    }

    public JID getJID() {
        return address;
    }

    public long getID() {
        return id;
    }

    public String getUsername() {
        return address.getNode();
    }

    /**
     * Returns true if the agent has requested to receive other agents' information. Until the
     * agent requests to receive other agents' information he won't receive individual presence
     * updates of other agents.
     *
     * @return true if the agent has requested to receive other agents' information.
     */
    boolean hasRequestedAgentInfo() {
        return requestedAgentInfo;
    }

    /**
     * Returns true if the agent's presence is available and his status is nor DND (do not disturb)
     * neither XA (extended away). These are the possible statuses and their meanings:<ul>
     * <li>chat - Indicates the agent is available to chat (is idle and ready to handle more
     * conversations).</li>
     * <li>away - The agent is busy (possibly with other chats). The agent may still be able to
     * handle other chats but an offer rejection is likely..</li>
     * <li>xa - The agent is physically away from their terminal and should not have a chat routed
     * to them.</li>
     * <li>dnd - The agent is busy and should not be disturbed. However, special case, or extreme
     * urgency chats may still be offered to the agent although offer rejection or offer timeouts
     * are highly likely.</li>
     * </ul>
     *
     * @return true if the agent's presence is available and his status is nor DND neither XA.
     */
    public boolean isAvailableToChat() {
        return (presence.getType() == null && presence.getShow() != Presence.Show.dnd &&
            presence.getShow() != Presence.Show.xa && presence.getShow() != Presence.Show.away);
    }

    /**
     * Adds information of a new chat that this agent is having with a user.
     *
     * @param workgroup workgroup where the chat has started.
     * @param sessionID the id of the session that identifies the chat.
     * @param request   the initial request made by the user.
     * @param date      the date when the agent joined the chat.
     */
    public void addChatInfo(Workgroup workgroup, String sessionID, UserRequest request, Date date) {
        Queue<ChatInfo> queue = chatInfos.get(workgroup);
        // Check if the agent has started a chat in a workgroup that he never joined (e.g. transfers)
        if (queue == null) {
            synchronized (workgroup) {
                queue = chatInfos.get(workgroup);
                if (queue == null) {
                    queue = new ConcurrentLinkedQueue<ChatInfo>();
                    chatInfos.put(workgroup, queue);
                }
            }
        }
        queue.add(new ChatInfo(sessionID, request, date));
        // Update all agents with a new agent-status packet with the current-chats updated.
        sendStatusToAllAgents(workgroup);
    }

    /**
     * Removes information about a chat since the agent left the conversation.
     *
     * @param workgroup workgroup where the chat existed.
     * @param sessionID the id of the session that identifies the chat.
     */
    public void removeChatInfo(Workgroup workgroup, String sessionID) {
        Queue<ChatInfo> chats = chatInfos.get(workgroup);
        for (ChatInfo chatInfo : chats) {
            if (sessionID.equals(chatInfo.getSessionID())) {
                // Update last chat ended date
                lastChatTime = new Date();

                chats.remove(chatInfo);
                // Update all agents with a new agent-status packet with the current-chats updated.
                sendStatusToAllAgents(workgroup);
                break;
            }
        }
    }

    /**
     * Returns a list with the actual chats info that the agent is having at the moment. The
     * returned collection is a snapshot of the chats so it will not be updated if a chat finished
     * or a new one has started.
     *
     * @param workgroup workgroup to get its chats.
     * @return a list with the actual chats info that the agent is having at the moment.
     */
    public Collection<ChatInfo> getChatsInfo(Workgroup workgroup) {
        return Collections.unmodifiableCollection(getChats(workgroup));
    }

    private Collection<ChatInfo> getChats(Workgroup workgroup) {
        Queue<ChatInfo> chats = chatInfos.get(workgroup);
        if (chats != null) {
            return chats;
        }
        return Collections.emptyList();
    }

    /**
     * This agent is not longer related to this offer. The agent may have been selected to answer
     * the user's request or the offer has been assigned to another agent or the request was
     * cancelled.
     *
     * @param offer the offer that is not longer related to this agent.
     */
    public void removeOffer(Offer offer) {
        if (offer.equals(this.offer)) {
            this.offer = null;
        }
        else {
            Log.debug("Offer not removed. " +
                "To remove: " +
                offer +
                " existing " +
                this.offer);
        }
    }

    /**
     * Returns true if the agent has received an offer and the server is still waiting for an
     * answer.
     *
     * @return true if the agent has received an offer and the server is still waiting for an
     *         answer.
     */
    public boolean isWaitingOfferAnswer() {
        return offer != null;
    }

    /**
     * Represents information about a Chat where this Agent is participating.
     *
     * @author Gaston Dombiak
     */
    public static class ChatInfo implements Comparable<ChatInfo> {

        private String sessionID;
        private String userID;
        private JID userJID;
        private Date date;
        private Workgroup workgroup;

        // Add extra metadata
        private String email;
        private String username;
        private String question;

        public ChatInfo(String sessionID, UserRequest request, Date date) {
            this.sessionID = sessionID;
            this.userID = request.getUserID();
            this.userJID = request.getUserJID();
            this.workgroup = request.getWorkgroup();
            this.date = date;

            Map<String, List<String>> metadata = request.getMetaData();

            if (metadata.containsKey("email")) {
                email = listToString(metadata.get("email"));
            }

            if (metadata.containsKey("username")) {
                username = listToString(metadata.get("username"));
            }

            if (metadata.containsKey("question")) {
                question = listToString(metadata.get("question"));
            }
        }

        /**
         * Returns the sessionID associated to this chat. Each chat will have a unique sessionID
         * that could be used for retrieving the whole transcript of the conversation.
         *
         * @return the sessionID associated to this chat.
         */
        public String getSessionID() {
            return sessionID;
        }

        /**
         * Returns the user unique identification of the user that made the initial request and
         * for which this chat was generated. If the user joined using an anonymous connection
         * then the userID will be the value of the ID attribute of the USER element. Otherwise,
         * the userID will be the bare JID of the user that made the request.
         *
         * @return the user unique identification of the user that made the initial request.
         */
        public String getUserID() {
            return userID;
        }

        /**
         * Returns the JID of the user that made the initial request and for which this chat
         * was generated.
         *
         * @return the JID of the user that made the initial request and for which this chat
         *         was generated.
         */
        public JID getUserJID() {
            return userJID;
        }

        /**
         * Returns the date when this agent joined the chat.
         *
         * @return the date when this agent joined the chat.
         */
        public Date getDate() {
            return date;
        }

        /**
         * Returns the email address of the user the agent is chatting with.
         *
         * @return the email address of the user the agent is chatting with.
         */
        public String getEmail() {
            return email;
        }

        /**
         * Return the username of the user the agent is chatting with.
         *
         * @return the username of the user the agent is chatting with.
         */
        public String getUsername() {
            return username;
        }

        /**
         * Return the question the user asked, if any.
         *
         * @return the question the user asked, if any.
         */
        public String getQuestion() {
            return question;
        }

        /**
         * Returns the packets sent to the room together with the date when the packet was sent.
         * The returned map will include both Presences and Messages.
         *
         * @return the packets sent to the room together with the date when the packet was sent.
         */
        public Map<Packet, java.util.Date> getPackets() {
            return workgroup.getTranscript(getSessionID());
        }

        public int compareTo(ChatInfo otherInfo) {
            return date.compareTo(otherInfo.getDate());
        }
    }

    /**
     * Returns a list as a comma delimited string.
     *
     * @param list the list of strings.
     * @return a comma delimited list of strings.
     */
    private static String listToString(List<String> list) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            String entry = list.get(i);
            builder.append(entry);
            if (i != (list.size() - 1)) {
                builder.append(",");
            }
        }
        return builder.toString();
    }

    /**
     * Return the time the last chat ended.
     *
     * @return the time.
     */
    public Date getTimeLastChatEnded() {
        return lastChatTime;
    }
}