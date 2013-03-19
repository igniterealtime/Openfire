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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.xmpp.workgroup.AgentSession;
import org.jivesoftware.xmpp.workgroup.RequestQueue;
import org.jivesoftware.xmpp.workgroup.UserCommunicationMethod;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.chatbot.ChatbotSession;
import org.jivesoftware.xmpp.workgroup.spi.WorkgroupCompatibleClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

/**
 * Requests made by users to get support by some agent.
 *
 * @author Gaston Dombiak
 */
public class UserRequest extends Request {

	private static final Logger Log = LoggerFactory.getLogger(UserRequest.class);
	
    private static final String INSERT_SESSION =
            "INSERT INTO fpSession(sessionID, userID, workgroupID, state, queueWaitTime, " +
            "startTime, endTime) values(?,?,?,?,?,?,?)";
    private static final String UPDATE_SESSION =
            "UPDATE fpSession SET state=?, queueWaitTime=?, endTime=? WHERE sessionID=?";
    private static final String INSERT_META_DATA =
            "INSERT INTO fpSessionMetadata(sessionID, metadataName, metadataValue) VALUES(?,?,?)";

    /**
     * Flag that indicates if an invitation that was not answered was offered again to the user.
     * The way in which the offer will be offered again to the user may vary according to the type
     * of client used by the user. For instance, if the user is using a chatbot to join the
     * workgroup then a new message may be sent to the user asking if he wants to receive a new
     * offer or cancel the request.
     */
    private boolean invitationChecked = false;

    private JID userJID;
    private String userID;
    private boolean anonymousUser;

    /**
     * Keeps the object that represents the type of client that the user is using.
     */
    private UserCommunicationMethod communicationMethod;
    private RequestQueue queue = null;
    private boolean savedToDB = false;
    /**
     * Timestamp that indicates when an invitation was sent to the user that made this request.
     */
    private long invitationSent;
    /**
     * ID of the room where the user was invited.
     */
    private String invitedRoomID;
    /**
     * Requests that are related to a user request. For instance, an invitation request sent to an
     * agent that is related to this user request.
     */
    private Queue<Request> relatedRequests = new ConcurrentLinkedQueue<Request>();

    /**
     * Returns an existing request given the requesting user's address and workgroup or throws
     * NotFoundException if none was found.
     *
     * @param workgroup the workgroup that the user us trying to join.
     * @param address the address to check.
     * @return a request given the requesting user's address and workgroup.
     * @throws org.jivesoftware.util.NotFoundException if the request could not be found.
     */
    public static UserRequest getRequest(Workgroup workgroup, JID address) throws NotFoundException {
        UserRequest request = null;
        for (RequestQueue requestQueue : workgroup.getRequestQueues()) {
            if (request == null) {
                request = requestQueue.getRequest(address);
            }
        }
        if (request == null) {
            Log.debug("Request not found for " +
                    address.toString());
            throw new NotFoundException();
        }
        return request;
    }

    public UserRequest(IQ packet, Workgroup wg) {
        super();
        this.userJID = packet.getFrom();
        this.userID = userJID.toBareJID();
        this.anonymousUser = false;
        this.workgroup = wg;
        // Requests to join a workgroup made using an IQ are assumed to be using a Workgroup
        // compatible client
        this.communicationMethod = WorkgroupCompatibleClient.getInstance();

        Iterator<Element> elementIter = packet.getChildElement().elementIterator();
        while (elementIter.hasNext()) {
            Element element = elementIter.next();
            if ("queue-notifications".equals(element.getName())) {
                setNotify(true);
            }
            else if ("user".equals(element.getName())) {
                userID = element.attributeValue("id");
                if (userID != null && userID.length() > 0) {
                    anonymousUser = !userJID.toString().equals(userID) &&
                            !userJID.toBareJID().equals(userID);
                }
            }
            else if ("metadata".equals(element.getName())) {
                for (Iterator<Element> i = element.elementIterator(); i.hasNext();) {
                    Element item = i.next();
                    if ("value".equals(item.getName())) {
                        String name = item.attributeValue("name");
                        if (name != null) {
                            metaData.put(name, Arrays.asList(item.getTextTrim()));
                        }
                    }
                }
            }
        }

        // Create metadata from submitted form.
        DataForm submittedForm = (DataForm)packet.getExtension(DataForm.ELEMENT_NAME,
                DataForm.NAMESPACE);

        for (FormField field : submittedForm.getFields()) {
            metaData.put(field.getVariable(), field.getValues());
        }

        // Omit certain items
        metaData.remove("password");
    }

    /**
     * Creates a new request made by a user using a chatbot as the communication media.
     *
     * @param session the chatbot session that holds all the information sent by the user.
     * @param wg      the workgroup that the user wants to join.
     */
    public UserRequest(ChatbotSession session, Workgroup wg) {
        super();
        this.userJID = session.getUserJID();
        this.userID = userJID.toBareJID();
        this.anonymousUser = false;
        this.workgroup = wg;
        // Use the chatbot of the session as the method to communicate with the user that
        // made the request
        this.communicationMethod = session.getChatbot();
        // Always set that users using a bot want to be notified of the position in the queue
        setNotify(true);
        // Use the stored attributes in the session as the metadata of the request
        metaData.putAll(session.getAttributes());

        // Make sure that the following keys are present in the metadata
        if (!metaData.containsKey("userID")) {
            metaData.put("userID", Arrays.asList(userJID.toString()));
        }
    }

    /**
     * Update the current position of the user in the queue. This will send
     * the packet directly to the user.
     *
     * @param isPolling true if using polling mode.
     */
    public void updateQueueStatus(boolean isPolling) {
        try {
            // Notify the user his status in the queue
            communicationMethod.notifyQueueStatus(workgroup.getJID(), userJID, this, isPolling);
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
    }

    @Override
	public void checkRequest(String roomID) {
        if (getInvitationSentTime() != null && !hasJoinedRoom()) {
            checkInvitation(roomID);
        }
        for (Request request : relatedRequests) {
            request.checkRequest(roomID);
        }
    }

    public void setRequestQueue(RequestQueue queue) {
        this.queue = queue;
    }

    public RequestQueue getRequestQueue() {
        return queue;
    }

    /**
     * Returns the ID of the room where the user was invited to have a chat with an agent or
     * <tt>null</tt> if the user was never invited.
     *
     * @return the ID of the room where the user was invited to have a chat with an agent or
     *         <tt>null</tt> if the user was never invited.
     */
    public String getInvitedRoomID() {
        return invitedRoomID;
    }

    /**
     * Returns the Date when an invitation to join a room was sent to the user that made this
     * request. Or answer <tt>null</tt> if an invitation was never sent.
     *
     * @return the Date when an invitation to join a room was sent to the user that made this
     *         request.
     */
    public Date getInvitationSentTime() {
        if (invitationSent > 0) {
            return new Date(invitationSent);
        }
        return null;
    }

    public int getPosition() {
        int pos = -1;
        if (queue != null) {
            pos = queue.getPosition(this);
        }
        return pos;
    }

    public int getTimeStatus() {
        int averageTime = queue == null ? 0 : queue.getAverageTime();
        int timeStatus;
        if (averageTime == 0) {
            timeStatus = (getPosition() + 1) * 15;
        }
        else {
            timeStatus = (getPosition() + 1) * averageTime;
        }
        return timeStatus;
    }

    @Override
	public JID getUserJID() {
        return userJID;
    }

    /**
     * Returns the user unique identification. If the user joined using an anonymous connection
     * then the userID will be the value of the ID attribute of the USER element. Otherwise, the
     * userID will be the bare JID of the user that made the request.
     *
     * @return the user unique identification.
     */
    public String getUserID() {
        return userID;
    }

    /**
     * Returns true if the request was made by a user that is using an anonymous session. For
     * anonymous user the userJID will be something like "server.com/a9d32h" and will vary
     * each time the user creates a new connection whilst the userID will represent the user
     * unique identification that may be used across several sessions.
     *
     * @return true if the request was made by a user that is using an anonymous session.
     */
    public boolean isAnonymousUser() {
        return anonymousUser;
    }

    /**
     * The request is being asked to verify if a new invitation must be sent to the user that
     * didn't answer the previous invitation. The request will only retry one more time.
     *
     * @param roomID the id of the room where the user was invited.
     */
    public void checkInvitation(String roomID) {
        if (!invitationChecked && !hasJoinedRoom() &&
                System.currentTimeMillis() - invitationSent > 10000) {
            invitationChecked = true;
            communicationMethod.checkInvitation(this);
        }
    }

    /**
     * Notification message saying that the request has been accepted by an agent and that
     * invitations have been sent to the agent and the user that made the request.
     *
     * @param roomID the id of the room for which invitations were sent.
     */
    public void invitationsSent(String roomID) {
        invitationSent = System.currentTimeMillis();
        invitedRoomID = roomID;
        communicationMethod.invitationsSent(this);
    }

    /**
     * Notification message saying that the user that made the request has joined the room to have
     * a chat with an agent.
     *
     * @param roomID the id of the room where the user has joined.
     */
    public void supportStarted(String roomID) {
        joinedRoom = System.currentTimeMillis();
        communicationMethod.supportStarted(this);
    }

    /**
     * Notification message saying that the support session has finished. At this point all room
     * occupants have left the room.
     */
    public void supportEnded() {
        communicationMethod.supportEnded(this);
    }

    @Override
	public void userJoinedRoom(JID roomJID, JID user) {
        // Notify related requests that new a occupant has joined the room
        for (Request request : relatedRequests) {
            request.userJoinedRoom(roomJID, user);
        }

    }

    /**
     * Adds a request that is somehow related to this user request. This is usually the case when
     * an invitation or transfer was sent to another agent.  For these cases a new Request is generated
     * that is related to this request. Since all these requests will be interested in the room activitiy
     * we need to propagate support events (i.e. supportStarted and supportEnded).
     *
     * @param request the request that is related to this request.
     */
    public void addRelatedRequest(Request request) {
        relatedRequests.add(request);
    }

    /**
     * Remvoes a request that is no longer related to this user request.
     *
     * @param request the request that is no longer related to this request.
     */
    public void removeRelatedRequest(Request request) {
        relatedRequests.remove(request);
    }

    @Override
	public void cancel(Request.CancelType type) {
        super.cancel(type);

        JID sender = workgroup.getJID();
        if (queue != null) {
            sender = queue.getWorkgroup().getJID();
            queue.removeRequest(this);
        }
        try {
            // Notify the user that he has left the queue
            communicationMethod.notifyQueueDepartued(sender, userJID, this, type);
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
    }

    @Override
	void addOfferContent(Element offerElement) {
        // Flag the offer as a user request
        offerElement.addElement("user-request");
        // Add custom extension that includes the userID if the session belongs to an
        // anonymous user
        if (isAnonymousUser()) {
            Element element = offerElement.addElement("user", "http://jivesoftware.com/protocol/workgroup");
            element.addAttribute("id", getUserID());
        }
    }

    @Override
	void addRevokeContent(Element revoke) {
        // Add custom extension that includes the userID if the session belongs to an
        // anonymous user
        if (isAnonymousUser()) {
            Element element = revoke.addElement("user", "http://jivesoftware.com/protocol/workgroup");
            element.addAttribute("id", getUserID());
        }
    }

    @Override
	public Element getSessionElement() {
        QName qName = DocumentHelper.createQName("session", DocumentHelper.createNamespace("", "http://jivesoftware.com/protocol/workgroup"));
        Element sessionElement = DocumentHelper.createElement(qName);
        sessionElement.addAttribute("id", requestID);
        sessionElement.addAttribute("workgroup", getWorkgroup().getJID().toString());
        return sessionElement;
    }
    /**
     * Sends an invitation to the agent that previously accepted the offer to join a room.
     * Agents need to join a room to be able to chat (and fulfil the request) with the
     * user that sent the request.
     *
     * @param agentSession the agent that previously accepted the offer.
     */
    @Override
	public void offerAccepted(AgentSession agentSession) {
        super.offerAccepted(agentSession);
        // Ask the workgroup to send invitations to the agent and to the user that made the
        // request. The Workgroup will create a MUC room and send invitations to the agent and
        // the user.
        getWorkgroup().sendInvitation(agentSession, this);
    }

    @Override
	public void updateSession(int state, long offerTime) {
        boolean inserted = false;
        long queueWaitTime = new Date().getTime() - offerTime;
        String tempDate = StringUtils.dateToMillis(new Date());

        // Gather all information needed.
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            synchronized (this) {
                if (!savedToDB) {
                    pstmt = con.prepareStatement(INSERT_SESSION);
                    pstmt.setString(1, requestID);
                    pstmt.setString(2, getUserID());
                    pstmt.setLong(3, getWorkgroup().getID());
                    pstmt.setInt(4, state);
                    pstmt.setLong(5, queueWaitTime);
                    pstmt.setString(6, tempDate);
                    pstmt.setString(7, tempDate);
                    pstmt.executeUpdate();
                    savedToDB = true;
                    inserted = true;
                }
                else {
                    pstmt = con.prepareStatement(UPDATE_SESSION);
                    pstmt.setInt(1, state);
                    pstmt.setLong(2, queueWaitTime);
                    pstmt.setString(3, tempDate);
                    pstmt.setString(4, requestID);
                    pstmt.executeUpdate();
                }
            }
        }
        catch (Exception ex) {
            Log.error(
                    "There was an issue handling offer update using sessionID " + requestID, ex);
        }
        finally {
           DbConnectionManager.closeConnection(pstmt, con);
        }
        if (inserted) {
            saveMetadata();
        }
    }

    private void saveMetadata() {
        final Map<String, List<String>> map = getMetaData();

        Connection con = null;
        try {
            con = DbConnectionManager.getConnection();
            PreparedStatement pstmt = con.prepareStatement(INSERT_META_DATA);
            for (String key : map.keySet()) {
                List<String> values = map.get(key);

                pstmt.setString(1, requestID);
                pstmt.setString(2, key);
                pstmt.setString(3, encodeMetadataValue(values));
                pstmt.executeUpdate();
            }
            pstmt.close();
        }
        catch (SQLException e) {
            Log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(con);
        }
    }
}
