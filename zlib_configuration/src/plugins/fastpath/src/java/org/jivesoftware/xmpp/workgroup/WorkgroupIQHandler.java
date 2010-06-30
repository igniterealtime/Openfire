/**
 * $RCSfile$
 * $Revision: 28498 $
 * $Date: 2006-03-13 09:51:42 -0800 (Mon, 13 Mar 2006) $
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

import org.dom4j.Element;
import org.jivesoftware.openfire.fastpath.WorkgroupSettings;
import org.jivesoftware.openfire.fastpath.settings.chat.ChatSettingsManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.xmpp.workgroup.interceptor.InterceptorManager;
import org.jivesoftware.xmpp.workgroup.interceptor.OfferInterceptorManager;
import org.jivesoftware.xmpp.workgroup.interceptor.PacketRejectedException;
import org.jivesoftware.xmpp.workgroup.interceptor.QueueInterceptorManager;
import org.jivesoftware.xmpp.workgroup.request.InvitationRequest;
import org.jivesoftware.xmpp.workgroup.request.Request;
import org.jivesoftware.xmpp.workgroup.request.TransferRequest;
import org.jivesoftware.xmpp.workgroup.request.UserRequest;
import org.jivesoftware.xmpp.workgroup.utils.ModelUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;

/**
 * <p>The Workgroup's IQ handler processes all incoming IQ packets sent to the workgroup.</p>
 * <p/>
 * <p>Currently the workgroup recognizes:</p>
 * <ul>
 * <li>IQ for user joins
 * <ul>
 * <li>available - Join the workgoup and create agent session</li>
 * <li>unavailable - Depart the workgroup and delete agent session</li>
 * </ul>
 * </li>
 * <li>IQ for iq-private (used in global agent macro storage)
 * <ul>
 * <li>get - Allowed for any agent member of the workgroup</li>
 * <li>set - Allowed for any agent member of the workgroup with
 * admin priviledges</li>
 * </ul>
 * </li>
 * </ul>
 *
 * @author Derek DeMoro
 */
public class WorkgroupIQHandler {

	private static final Logger Log = LoggerFactory.getLogger(WorkgroupIQHandler.class);
	
    private Workgroup workgroup;
    private WorkgroupSettings workgroupSettings = null;

    private WorkgroupProviderManager providerManager;
    private AgentManager agentManager;

    public WorkgroupIQHandler() {
        workgroupSettings = new WorkgroupSettings();
        providerManager = WorkgroupProviderManager.getInstance();
        agentManager = WorkgroupManager.getInstance().getAgentManager();
    }

    public void setWorkgroup(Workgroup workgroup) {
        this.workgroup = workgroup;
    }

    public void process(IQ packet) {
        try {
            IQ.Type type = packet.getType();
            if (type == IQ.Type.set) {
                handleIQSet(packet);
            }
            else if (type == IQ.Type.get) {
                handleIQGet(packet);
            }
            else if (type == IQ.Type.result) {
                handleIQResult(packet);
            }
            else if (type == IQ.Type.error) {
                handleIQError(packet);
            }
            else {
                IQ reply = IQ.createResultIQ(packet);
                if (packet.getChildElement() != null) {
                    reply.setChildElement(packet.getChildElement().createCopy());
                }
                reply.setError(new PacketError(PacketError.Condition.bad_request));
                workgroup.send(reply);
            }
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
            IQ reply = IQ.createResultIQ(packet);
            if (packet.getChildElement() != null) {
                reply.setChildElement(packet.getChildElement().createCopy());
            }
            reply.setError(new PacketError(PacketError.Condition.internal_server_error));
            workgroup.send(reply);
        }
    }

    private void handleIQSet(IQ packet) {
        IQ reply;
        // TODO: verify namespace and send error if wrong
        Element iq = packet.getChildElement();

        JID sender = packet.getFrom();
        reply = IQ.createResultIQ(packet);
        reply.setFrom(workgroup.getJID());
        String queryName = iq.getName();
        String queryNamespace = iq.getNamespace().toString();

        if ("join-queue".equals(queryName)) {
            InterceptorManager interceptorManager = QueueInterceptorManager.getInstance();
            try {
                interceptorManager.invokeInterceptors(workgroup.getJID().toBareJID(), packet, true,
                        false);
                // Received a Join Queue request from a visitor, create a new request.
                UserRequest request = new UserRequest(packet, workgroup);
                // Let the workgroup process the new request
                if (!workgroup.queueRequest(request)) {
                    // It was not possible to add the request to a queue so answer that the
                    // workgroup is not accepting new join-queue requests
                    reply.setChildElement(packet.getChildElement().createCopy());
                    reply.setError(new PacketError(PacketError.Condition.service_unavailable));
                }
                interceptorManager.invokeInterceptors(workgroup.getJID().toBareJID(), packet, true,
                        true);
            }
            catch (PacketRejectedException e) {
                workgroup.rejectPacket(packet, e);
                reply = null;
            }
        }
        else if ("depart-queue".equals(queryName)) {
            // Visitor is departing queue
            try {
                Request request = UserRequest.getRequest(workgroup, sender);
                InterceptorManager interceptorManager = QueueInterceptorManager.getInstance();
                try {
                    interceptorManager.invokeInterceptors(workgroup.getJID().toBareJID(), packet,
                            true, false);
                    request.cancel(Request.CancelType.DEPART);
                    iq.add(request.getSessionElement());
                    interceptorManager.invokeInterceptors(workgroup.getJID().toBareJID(), packet,
                            true, true);
                }
                catch (PacketRejectedException e) {
                    workgroup.rejectPacket(packet, e);
                    reply = null;
                }
            }
            catch (NotFoundException e) {
                reply.setChildElement(packet.getChildElement().createCopy());
                reply.setError(new PacketError(PacketError.Condition.item_not_found));
                Log.debug("Request not found" +
                        " while departing queue:", e);
            }
        }
        else if ("offer-accept".equals(queryName)) {
            try {
                InterceptorManager interceptorManager = OfferInterceptorManager.getInstance();
                String id = iq.attributeValue("id");
                String jid = iq.attributeValue("jid");
                if (id != null || jid  != null) {
                    Request request;
                    if (id != null) {
                        // Search request by its unique ID
                        request = Request.getRequest(id);
                    }
                    else  {
                        // Old version of FP refers to requests by the user's jid. This old version
                        // implements transfers and invitations on the client and not the server side.
                        // Therefore, for each user's jid there is always a unique Request
                        request = UserRequest.getRequest(workgroup, new JID(jid));
                    }
                    Offer offer = request.getOffer();
                    if (offer != null && offer.isOutstanding()) {
                        AgentSession agentSession = agentManager.getAgentSession(packet.getFrom());
                        if (agentSession == null) {
                            reply.setChildElement(packet.getChildElement().createCopy());
                            reply.setError(new PacketError(PacketError.Condition.item_not_found));
                            Log
                                    .debug("Agent not found while accepting offer");
                        }
                        else {
                            try {
                                interceptorManager.invokeInterceptors(workgroup.getJID().toBareJID(),
                                        packet, true, false);
                                offer.accept(agentSession);
                                interceptorManager.invokeInterceptors(workgroup.getJID().toBareJID(),
                                        packet, true, true);
                            }
                            catch (PacketRejectedException e) {
                                workgroup.rejectPacket(packet, e);
                                reply = null;
                            }
                        }
                    }
                    else {
                        reply.setChildElement(packet.getChildElement().createCopy());
                        reply.setError(new PacketError(PacketError.Condition.not_acceptable));
                    }
                }
            }
            catch (NotFoundException e) {
                reply.setChildElement(packet.getChildElement().createCopy());
                reply.setError(new PacketError(PacketError.Condition.item_not_found));
                Log.debug("Request not found " +
                        "while accepting offer: ", e);
            }
            catch (AgentNotFoundException e) {
                reply.setChildElement(packet.getChildElement().createCopy());
                reply.setError(new PacketError(PacketError.Condition.item_not_found));
                Log.debug("Agent not found " +
                        "while accepting offer: ", e);
            }
        }
        else if ("offer-reject".equals(queryName)) {
            try {
                InterceptorManager interceptorManager = OfferInterceptorManager.getInstance();
                String id = iq.attributeValue("id");
                String jid = iq.attributeValue("jid");
                if (id != null || jid  != null) {
                    Request request;
                    if (id != null) {
                        // Search request by its unique ID
                        request = Request.getRequest(id);
                    }
                    else  {
                        // Old version of FP refers to requests by the user's jid. This old version
                        // implements transfers and invitations on the client and not the server side.
                        // Therefore, for each user's jid there is always a unique Request
                        request = UserRequest.getRequest(workgroup, new JID(jid));
                    }
                    Offer offer = request.getOffer();
                    if (offer != null) {
                        AgentSession agentSession = agentManager.getAgentSession(packet.getFrom());
                        if (agentSession == null) {
                            reply.setChildElement(packet.getChildElement().createCopy());
                            reply.setError(new PacketError(PacketError.Condition.item_not_found));
                            Log
                                    .debug("Agent not found while accepting offer");
                        }
                        else {
                            try {
                                interceptorManager.invokeInterceptors(workgroup.getJID().toBareJID(),
                                        packet, true, false);
                                offer.reject(agentSession);
                                interceptorManager.invokeInterceptors(workgroup.getJID().toBareJID(),
                                        packet, true, true);
                            }
                            catch (PacketRejectedException e) {
                                workgroup.rejectPacket(packet, e);
                                reply = null;
                            }
                        }
                    }
                }
            }
            catch (NotFoundException e) {
                reply.setChildElement(packet.getChildElement().createCopy());
                reply.setError(new PacketError(PacketError.Condition.item_not_found));
                Log.debug("Request not found " +
                        "while rejecting offer: ", e);
            }
            catch (AgentNotFoundException e) {
                reply.setChildElement(packet.getChildElement().createCopy());
                reply.setError(new PacketError(PacketError.Condition.item_not_found));
                Log.debug("Agent not found " +
                        "while accepting offer: ", e);
            }
        }
        else if ("invite".equals(queryName)) {
            // Get the type of inviation (i.e. entity type is being invited)
            InvitationRequest request = new InvitationRequest(packet, workgroup);
            workgroup.processInvitation(request, packet);
            reply = null;
        }
        else if ("transfer".equals(queryName)) {
            // Get the type of transfer (i.e. entity type is going to get the transfer offer)
            TransferRequest request = new TransferRequest(packet, workgroup);
            workgroup.processTransfer(request, packet);
            reply = null;
        }
        else if ("jabber:iq:private".equals(queryNamespace)) {
            // IQ private for agents global macro storage
            setIQPrivate(packet);
        }
        else if ("agent-info".equals(queryName)) {
            if (!JiveGlobals.getBooleanProperty("xmpp.live.agent.change-properties", true)) {
                // Answer that agents are not allowed to change their properties (feature disabled)
                reply = IQ.createResultIQ(packet);
                reply.setChildElement(packet.getChildElement().createCopy());
                reply.setError(new PacketError(PacketError.Condition.service_unavailable));
            }
            else {
                try {
                    AgentSession agentSession = agentManager.getAgentSession(packet.getFrom());
                    if (agentSession == null) {
                        reply = IQ.createResultIQ(packet);
                        reply.setChildElement(packet.getChildElement().createCopy());
                        reply.setError(new PacketError(PacketError.Condition.item_not_found));
                    }
                    else {
                        String allowsToChange = agentSession.getAgent().getProperties().getProperty("change-properties");
                        if (!"false".equals(allowsToChange)) {
                            // Set the new agent's info
                            agentSession.getAgent().updateAgentInfo(packet);
                        }
                        else {
                            // Answer that this agent is not allowed to change his properties
                            reply = IQ.createResultIQ(packet);
                            reply.setChildElement(packet.getChildElement().createCopy());
                            reply.setError(new PacketError(PacketError.Condition.service_unavailable));
                        }
                    }
                }
                catch (AgentNotFoundException e) {
                    reply = IQ.createResultIQ(packet);
                    reply.setChildElement(packet.getChildElement().createCopy());
                    reply.setError(new PacketError(PacketError.Condition.item_not_found));
                }
            }
        }
        else {
            // Check all Workgroup Providers for handling this SET request. If
            // none are found, send bad request error.
            for (WorkgroupProvider provider : providerManager.getWorkgroupProviders()) {
                // Handle packet?
                if (provider.handleSet(packet)) {
                    // If provider accepts responsibility, hand off packet.
                    provider.executeSet(packet, workgroup);
                    return;
                }
            }

            dropPacket(packet);
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(new PacketError(PacketError.Condition.bad_request));
        }

        if (reply != null) {
            workgroup.send(reply);
        }
    }

    private void handleIQGet(IQ packet) {
        IQ reply = null;
        // TODO: verify namespace and send error if wrong
        Element iq = packet.getChildElement();
        UserRequest request;

        final WorkgroupStats stats = new WorkgroupStats(workgroup);

        String name = iq.getName();
        String namespace = iq.getNamespaceURI();
        if ("queue-status".equals(name)) {
            try {
                request = UserRequest.getRequest(workgroup, packet.getFrom());
                request.updateQueueStatus(true);
            }
            catch (NotFoundException e) {
                reply = IQ.createResultIQ(packet);
                reply.setChildElement(packet.getChildElement().createCopy());
                reply.setError(new PacketError(PacketError.Condition.item_not_found));
            }
        }
        else if ("transcripts".equals(name)) {
            try {
                // Check if the sender of the packet is a connected Agent to this workgroup.
                // Otherwise return a not_authorized
                if (agentManager.getAgentSession(packet.getFrom()) == null) {
                    reply = IQ.createResultIQ(packet);
                    reply.setChildElement(packet.getChildElement().createCopy());
                    reply.setError(new PacketError(PacketError.Condition.not_authorized));
                }
                else {
                    String userID = iq.attributeValue("userID");
                    stats.getChatTranscripts(packet, userID);
                }
            }
            catch (AgentNotFoundException e) {
                reply = IQ.createResultIQ(packet);
                reply.setChildElement(packet.getChildElement().createCopy());
                reply.setError(new PacketError(PacketError.Condition.not_authorized));
            }
        }
        else if ("transcript".equals(name)) {
            try {
                // Check if the sender of the packet is a connected Agent to this workgroup.
                // Otherwise return a not_authorized
                if (agentManager.getAgentSession(packet.getFrom()) == null) {
                    reply = IQ.createResultIQ(packet);
                    reply.setChildElement(packet.getChildElement().createCopy());
                    reply.setError(new PacketError(PacketError.Condition.not_authorized));
                }
                else {
                    String sessionID = iq.attributeValue("sessionID");
                    stats.getChatTranscript(packet, sessionID);
                }
            }
            catch (AgentNotFoundException e) {
                reply = IQ.createResultIQ(packet);
                reply.setChildElement(packet.getChildElement().createCopy());
                reply.setError(new PacketError(PacketError.Condition.not_authorized));
            }
        }
        else if ("agent-status-request".equals(name)) {
            try {
                AgentSession agentSession = agentManager.getAgentSession(packet.getFrom());
                if (agentSession == null) {
                    reply = IQ.createResultIQ(packet);
                    reply.setChildElement(packet.getChildElement().createCopy());
                    reply.setError(new PacketError(PacketError.Condition.item_not_found));
                }
                else {
                    agentSession.sendAgentsInWorkgroup(packet, workgroup);
                }
            }
            catch (AgentNotFoundException e) {
                reply = IQ.createResultIQ(packet);
                reply.setChildElement(packet.getChildElement().createCopy());
                reply.setError(new PacketError(PacketError.Condition.item_not_found));
            }
        }
        else if ("agent-info".equals(name)) {
            try {
                // Send the agent's info to the session that requested its own information
                AgentSession agentSession = agentManager.getAgentSession(packet.getFrom());
                if (agentSession == null) {
                    reply = IQ.createResultIQ(packet);
                    reply.setChildElement(packet.getChildElement().createCopy());
                    reply.setError(new PacketError(PacketError.Condition.item_not_found));
                }
                else {
                    agentSession.sendAgentInfo(packet);
                }
            }
            catch (AgentNotFoundException e) {
                reply = IQ.createResultIQ(packet);
                reply.setChildElement(packet.getChildElement().createCopy());
                reply.setError(new PacketError(PacketError.Condition.item_not_found));
            }
        }
        else if ("occupants-info".equals(name)) {
            try {
                // Just check that the packet was sent by a logged agent to this workgroup
                AgentSession agentSession = agentManager.getAgentSession(packet.getFrom());
                if (agentSession == null) {
                    reply = IQ.createResultIQ(packet);
                    reply.setChildElement(packet.getChildElement().createCopy());
                    reply.setError(new PacketError(PacketError.Condition.not_authorized));
                }
                else {
                    // Send information about the occupants of the requested room
                    String roomID = iq.attributeValue("roomID");
                    workgroup.sendOccupantsInfo(packet, roomID);
                }
            }
            catch (AgentNotFoundException e) {
                reply = IQ.createResultIQ(packet);
                reply.setChildElement(packet.getChildElement().createCopy());
                reply.setError(new PacketError(PacketError.Condition.not_authorized));
            }
        }
        else if ("chat-settings".equals(name)) {
            ChatSettingsManager chatSettingsManager = ChatSettingsManager.getInstance();
            String key = iq.attributeValue("key");
            String type = iq.attributeValue("type");
            if (ModelUtil.hasLength(key)) {
                chatSettingsManager.getChatSettingByKey(packet, workgroup, key);
            }
            else if (ModelUtil.hasLength(type)) {
                try {
                    int typeInt = Integer.parseInt(type);
                    chatSettingsManager.getChatSettingsByType(packet, workgroup, typeInt);
                }
                catch (NumberFormatException e) {
                  // Bad type.
                }
            }
            else {
                chatSettingsManager.getAllChatSettings(packet, workgroup);
            }
        }
        else if ("jabber:iq:private".equals(namespace)) {
            // IQ private for agents global macro storage
            getIQPrivate(packet);
        }
        else if ("vcard-temp".equals(namespace)) {
            // Return workgroup's VCard
            getVCard(packet);
        }
        else {

            // Check all Workgroup Providers for handling this GET request. If
            // none are found, send bad request error.
            for (WorkgroupProvider provider : providerManager.getWorkgroupProviders()) {
                // Will provider handle the GET
                if (provider.handleGet(packet)) {
                    // Pass off packet
                    provider.executeGet(packet, workgroup);
                    return;
                }
            }

            dropPacket(packet);
            reply = IQ.createResultIQ(packet);
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(new PacketError(PacketError.Condition.bad_request));
        }
        if (reply != null) {
            workgroup.send(reply);
        }
    }

    private void setIQPrivate(IQ packet) {
        Element frag = packet.getChildElement();
        Element dataElement = (Element)frag.elementIterator().next();
        workgroupSettings.add(workgroup.getJID().toBareJID(), dataElement);
    }

    private void getIQPrivate(IQ packet) {
        WorkgroupSettings settings = new WorkgroupSettings();

        IQ replyPacket = null;
        Element child = packet.getChildElement();
        Element dataElement = (Element)child.elementIterator().next();

        if (dataElement != null) {
            if (IQ.Type.get.equals(packet.getType())) {
                replyPacket = IQ.createResultIQ(packet);
                Element dataStored = settings.get(workgroup.getJID().toBareJID(), dataElement);
                dataStored.setParent(null);

                child.remove(dataElement);
                child.setParent(null);
                replyPacket.setChildElement(child);
                child.add(dataStored);
            }
        }
        else {
            replyPacket = IQ.createResultIQ(packet);
            replyPacket.setChildElement("query", "jabber:iq:private");
        }

        workgroup.send(replyPacket);
    }

    private void getVCard(IQ packet) {
        IQ reply = IQ.createResultIQ(packet);
        Element vCard = packet.getChildElement().createCopy();
        reply.setChildElement(vCard);
        vCard.addElement("FN").setText(workgroup.getDisplayName());
        vCard.addElement("NICKNAME").setText(workgroup.getDisplayName());
        vCard.addElement("JABBERID").setText(workgroup.getJID().toString());

        workgroup.send(reply);
    }

    private void handleIQResult(IQ packet) {
        // Do nothing. Workgroups may receive IQ results from Agents after they accepted an offer.
    }

    private void handleIQError(IQ packet) {
        dropPacket(packet);
    }

    private void dropPacket(Packet packet) {
        Log.info("Dropped packet: " +
                packet.toString());
    }
}
