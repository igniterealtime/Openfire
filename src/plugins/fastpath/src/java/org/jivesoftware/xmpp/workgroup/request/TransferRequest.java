/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2004-2006 Jive Software. All rights reserved.
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

package org.jivesoftware.xmpp.workgroup.request;

import java.util.Arrays;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.xmpp.workgroup.AgentNotFoundException;
import org.jivesoftware.xmpp.workgroup.AgentSession;
import org.jivesoftware.xmpp.workgroup.RequestQueue;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.WorkgroupManager;
import org.jivesoftware.xmpp.workgroup.interceptor.RoomInterceptorManager;
import org.jivesoftware.xmpp.workgroup.routing.RoutingManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.muc.Invitation;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.PacketError;

/**
 * Request sent by an agent to transfer a session support to another agent.
 *
 * @author Gaston Dombiak
 */
public class TransferRequest extends Request {
	
	private static final Logger Log = LoggerFactory.getLogger(TransferRequest.class);
	
    /**
     * Time limit to wait for the invitee to join the support room. The limit is verified once the agent
     * accepted the offer or a MUC invitation was sent to the user.
     */
    private static final long JOIN_TIMEOUT = 60 * 1000;

    private Type type;
    private String sessionID;
    private JID inviter;
    /**
     * JID of the entity that will receive the transfer offer. This is not necessarily the JID of the
     * user since when inviting a workgroup or a queue the JID will be the JID of the queue or workgroup.
     * Check out actualInvitee to learn the actual entity that got the transfer offer.
     */
    private JID invitee;
    /**
     * JID of the entity that ended up receiving the transfer offer.
     */
    private JID actualInvitee;
    private String reason;
    /**
     * Original request made by a user. The invitation being sent is related to this user request.
     */
    private UserRequest userRequest;
    /**
     * Timestamp when the offer was accepted by the agent or when the MUC invitation was sent to a user.
     * This information will be used to monitor if user actually joined the support room before the limit
     * timeout is reached.
     */
    private long offerAccepted;

    public TransferRequest(IQ packet, Workgroup workgroup) {
        super();
        Element iq = packet.getChildElement();
        this.type = Type.valueOf(iq.attributeValue("type"));
        Element sessionElement = iq.element("session");
        sessionID = sessionElement.attributeValue("id");
        inviter = packet.getFrom();
        invitee = new JID(iq.elementTextTrim("invitee"));
        reason = iq.elementTextTrim("reason");
        this.workgroup = workgroup;
        String jid = sessionElement.attributeValue("workgroup");
        try {
            if (jid == null) {
                // Keep backwards compatibility. This "trick" will only work while the workgroup
                // that received the user request and that is making the transfer is the
                // same workgroup.
                userRequest = workgroup.getUserRequest(sessionID);
            }
            else {
                JID workgroupJID = new JID(jid);
		// Replace the workgroup if the original offer originated from a different
		// workgroup
		this.workgroup = WorkgroupManager.getInstance().getWorkgroup(workgroupJID);
		userRequest = this.workgroup.getUserRequest(sessionID);
            }
            // Notify the user request that is now related to this new request
            userRequest.addRelatedRequest(this);
            // Add metadata of original user request to this offer
            if (userRequest.getMetaData() != null) {
                metaData.putAll(userRequest.getMetaData());
            }
        }
        catch (Exception e) {
            Log.error("Workgroup not found for transfer: " + jid, e);
        }
    }

    public void execute() {
        if (Type.user == type) {
            AgentSession agentSession = null;
            // Verify if the invitee user is an agent that is currently logged
            try {
                agentSession = WorkgroupManager.getInstance().getAgentManager().getAgent(invitee).getAgentSession();
            }
            catch (AgentNotFoundException e) {
                // Ignore
            }

            // Only send muc invites to a particular user.
            if (true) {
                // Invitee is not an agent so send a standard MUC room invitation
                sendMUCInvitiation();
                // Keep track when the invitation was sent to the user
                offerAccepted = System.currentTimeMillis();
            }
            else {
                // Invite the agent to the room by sending an offer
                Workgroup workgroup = agentSession.getWorkgroups().iterator().next();
                RequestQueue requestQueue = workgroup.getRequestQueues().iterator().next();
                // Add the requested agent as the initial target agent to get the offer
                getMetaData().put("agent", Arrays.asList(invitee.toString()));
                getMetaData().put("ignore", Arrays.asList(inviter.toBareJID()));
                // Dispatch the request
                requestQueue.getDispatcher().injectRequest(this);
            }
        }
        else if (Type.queue == type) {
            // Send offer to the best again available in the requested queue
            Workgroup targetWorkgroup = WorkgroupManager.getInstance().getWorkgroup(invitee.getNode());
            if (targetWorkgroup == null) {
                // No workgroup was found for the specified invitee. Send a Message with the error
                sendErrorMessage("Specified workgroup was not found.");
                return;
            }
            try {
                RequestQueue requestQueue = targetWorkgroup.getRequestQueue(invitee.getResource());
                getMetaData().put("ignore", Arrays.asList(inviter.toBareJID()));
                // Dispatch the request
                requestQueue.getDispatcher().injectRequest(this);
            }
            catch (NotFoundException e) {
                // No queue was found for the specified invitee. Send a Message with the error
                sendErrorMessage("Specified queue was not found.");
            }
        }
        else if (Type.workgroup == type) {
            // Select the best queue based on the original request
            Workgroup targetWorkgroup = WorkgroupManager.getInstance().getWorkgroup(invitee.getNode());
            if (targetWorkgroup != null) {
                RequestQueue requestQueue = RoutingManager.getInstance().getBestQueue(targetWorkgroup, userRequest);
                getMetaData().put("ignore", Arrays.asList(inviter.toBareJID()));
                // Send offer to the best again available in the requested queue
                requestQueue.getDispatcher().injectRequest(this);
            }
            else {
                // No workgroup was found for the specified invitee. Send a Message with the error
                sendErrorMessage("Specified workgroup was not found.");
            }
        }
    }


    @Override
	public void updateSession(int state, long offerTime) {
        // Ignore
    }

    @Override
	public void offerAccepted(AgentSession agentSession) {
        super.offerAccepted(agentSession);
        // Keep track when the offer was accepted by the agent
        offerAccepted = System.currentTimeMillis();
    }

    @Override
	public boolean sendOffer(AgentSession session, RequestQueue queue) {
        // Keep track of the actual entity that received the transfer offer
        actualInvitee = session.getJID();
        return super.sendOffer(session, queue);
    }

    @Override
	void addOfferContent(Element offerElement) {
        Element inviteElement = offerElement.addElement("transfer", "http://jabber.org/protocol/workgroup");

        inviteElement.addAttribute("type", type.toString());

        inviteElement.addElement("inviter").setText(inviter.toString());

        String serviceName = WorkgroupManager.getInstance().getMUCServiceName();
        inviteElement.addElement("room").setText(sessionID + "@" + serviceName);

        inviteElement.add(userRequest.getMetaDataElement());

        inviteElement.addElement("reason").setText(reason);
    }

    @Override
	void addRevokeContent(Element revoke) {
    }

    @Override
	public Element getSessionElement() {
        // Add the workgroup of the original user request
        QName qName = DocumentHelper.createQName("session", DocumentHelper.createNamespace("", "http://jivesoftware.com/protocol/workgroup"));
        Element sessionElement = DocumentHelper.createElement(qName);
        sessionElement.addAttribute("id", sessionID);
        sessionElement.addAttribute("workgroup", userRequest.getWorkgroup().getJID().toString());
        return sessionElement;
    }

    @Override
	JID getUserJID() {
        return userRequest.getUserJID();
    }

    @Override
	public void userJoinedRoom(JID roomJID, JID user) {
        Log.debug("User "+user+" has joined "+roomJID+". User should be kicked.");
        if (actualInvitee != null && actualInvitee.toBareJID().equals(user.toBareJID())) {
            joinedRoom = System.currentTimeMillis();
            // This request has been completed so remove it from the list of related
            // requests of the original user request
            userRequest.removeRelatedRequest(this);
            // Kick the inviter from the room
            IQ kick = new IQ(IQ.Type.set);
            kick.setTo(roomJID);
            kick.setFrom(workgroup.getFullJID());
            Element childElement = kick.setChildElement("query", "http://jabber.org/protocol/muc#admin");
            Element item = childElement.addElement("item");
            item.addAttribute("jid", inviter.toString());
            item.addAttribute("role", "none");
            item.addElement("reason").setText("Transfer was successful");
            workgroup.send(kick);
            Log.debug("Sent kicked to  "+user+" in room "+roomJID+".");
        }
    }

    @Override
	public void checkRequest(String roomID) {
        // Monitor that the agent/user joined the room and if not send back an error to the inviter
        if (offerAccepted > 0 && !hasJoinedRoom() && System.currentTimeMillis() - offerAccepted > JOIN_TIMEOUT) {
            Log.debug("Agent or user failed to join room "+roomID);
            // Send error message to inviter
            sendErrorMessage("Agent or user failed to join the room.");
        }
    }

    private void sendErrorMessage(String body) {
        // Invitation request has failed. Inform inviter
        userRequest.removeRelatedRequest(this);

        Message message = new Message();
        message.setError(PacketError.Condition.recipient_unavailable);
        message.setTo(inviter);
        message.setFrom(workgroup.getJID());
        message.setBody(body);
        Element element = message.addChildElement("transfer", "http://jabber.org/protocol/workgroup");
        element.addAttribute("type", type.toString());
        Element sessionElement = element.addElement("session", "http://jivesoftware.com/protocol/workgroup");
        sessionElement.addAttribute("id", sessionID);
        element.addElement("inviter").setText(inviter.toString());
        element.addElement("invitee").setText(invitee.toString());
        workgroup.send(message);
    }

    /**
     * Sends a standard MUC invitation to the invitee.
     */
    private void sendMUCInvitiation() {
        // Keep track of the actual entity that received the transfer offer
        actualInvitee = invitee;

        final String serviceName = WorkgroupManager.getInstance().getMUCServiceName();
        final String roomName = sessionID + "@" + serviceName;

        Invitation invitation = new Invitation(invitee.toString(), reason);
        invitation.setTo(roomName);
        invitation.setFrom(inviter);
        // Add workgroup extension that includes the JID of the workgroup
        Element element = invitation.addChildElement("workgroup", "http://jabber.org/protocol/workgroup");
        element.addAttribute("jid", workgroup.getJID().toBareJID());
        // Add custom extension that includes the sessionID
        element = invitation.addChildElement("session", "http://jivesoftware.com/protocol/workgroup");
        element.addAttribute("id", sessionID);
        RoomInterceptorManager interceptorManager = RoomInterceptorManager.getInstance();
        interceptorManager.invokeInterceptors(workgroup.getJID().toBareJID(), invitation, false, false);
        workgroup.send(invitation);
        interceptorManager.invokeInterceptors(workgroup.getJID().toBareJID(), invitation, false, true);
    }

    /**
     * Type of entity to receive the transfer of a groupchat support session.
     */
    public static enum Type {
        /**
         * A user is going to receive the transfer of a groupchat support session. The user could be another agent
         * or just a regular XMPP user.
         */
        user,
        /**
         * Some agent of the specified queue is going to receive the transfer of the groupchat support session.
         */
        queue,
        /**
         * Some agent of the specified workgroup is going to receive the transfer of the groupchat support session.
         */
        workgroup
    }
}
