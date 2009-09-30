/**
 * $RCSfile$
 * $Revision: 27735 $
 * $Date: 2006-02-23 20:45:24 -0800 (Thu, 23 Feb 2006) $
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

package org.jivesoftware.xmpp.workgroup.request;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.database.SequenceManager;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.xmpp.workgroup.AgentSession;
import org.jivesoftware.xmpp.workgroup.Offer;
import org.jivesoftware.xmpp.workgroup.RequestQueue;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.utils.FastpathConstants;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

/**
 * <p>Database compatible workgroup request information.</p>
 *
 * @author Derek DeMoro
 */
public abstract class Request {

    private static final Map<String, Request> requests = new ConcurrentHashMap<String, Request>();

    protected final String requestID;

    private Date creationTime;
    /**
     * Timestamp that indicates when the last user joined the support MUC room.
     */
    protected long joinedRoom;
    /**
     * Workgroup that issued the offer.
     */
    protected Workgroup workgroup;

    private boolean notify = false;
    protected Offer offer;

    protected Map<String, List<String>> metaData = new HashMap<String, List<String>>();

    private static String newRequestID() {
        // Create a requestID for the new request
        long requestCounter = SequenceManager.nextID(FastpathConstants.WORKGROUP_QUEUE);
        String requestID = (StringUtils.randomString(6) + Long.toString(requestCounter)).toLowerCase();
        // Replace possible white spaces with the '_' character
        return requestID.replace(' ', '_');
    }

    /**
     * Returns an existing request based on it unique ID. If none was found then a NotFoundException
     * exception will be thrown.<p>
     *
     * Requests are tracked once an initial offer has been sent to an agent. Tracking of requests is
     * stopped once the request was cancelled or accepted.
     *
     * @param requestID the unique identifier of the request.
     * @return an existing request based on it unique ID.
     * @throws org.jivesoftware.util.NotFoundException if the request could not be found.
     */
    public static Request getRequest(String requestID) throws NotFoundException {
        Request request = requests.get(requestID);
        if (request == null) {
            Log.debug("Request not found by ID: " + requestID);
            throw new NotFoundException();
        }
        return request;
    }

    protected Request() {
        creationTime = new Date();
        requestID = newRequestID();
    }

    public Offer getOffer() {
        return offer;
    }

    public void setOffer(Offer offer) {
        this.offer = offer;
    }

    public void setNotify(boolean notify) {
        this.notify = notify;
    }

    public boolean isNotify() {
        return notify;
    }

    public Date getCreationTime() {
        return creationTime;
    }

    public String getSessionID() {
        return requestID;
    }

    /**
     * Send a Cancel notice to the queue when a user has left.
     *
     * @param type the type of Cancel. The type will be used to explain the reason of the departure.
     * @see CancelType
     */
    public void cancel(Request.CancelType type) {
        // Stop tracking this request
        requests.remove(requestID);
        if (offer != null) {
            offer.cancel();
        }
    }

    /**
     * Returns true if the user that made this request joined a room to have a chat with an agent.
     *
     * @return true if the user that made this request joined a room to have a chat with an agent.
     */
    public boolean hasJoinedRoom() {
        return joinedRoom > 0;
    }

    /**
     * Returns the Date when the user joined the room to have a chat with an agent. Or answer
     * <tt>null</tt> if the user never joined a chat room.
     *
     * @return the Date when the user joined the room to have a chat with an agent.
     */
    public Date getJoinedRoomTime() {
        if (joinedRoom > 0) {
            return new Date(joinedRoom);
        }
        return null;
    }

    public abstract Element getSessionElement();

    public Workgroup getWorkgroup() {
        return workgroup;
    }

    public Map<String, List<String>> getMetaData() {
        return metaData;
    }

    public Element getMetaDataElement() {
        QName qName = DocumentHelper.createQName("metadata", DocumentHelper.createNamespace("",
                "http://jivesoftware.com/protocol/workgroup"));
        Element metaDataElement = DocumentHelper.createElement(qName);

        for (String name : metaData.keySet()) {
            List<String> values = metaData.get(name);

            for (String value : values) {
                Element elem = metaDataElement.addElement("value");
                elem.addAttribute("name", name).setText(value);
            }
        }
        return metaDataElement;
    }

    /**
     * Updates the session table with the new session state and queueWaitTime.
     *
     * @param state the state of this session.
     * @param offerTime the new offer time.
     */
    public abstract void updateSession(int state, long offerTime);

    /**
     * Notification event indicating that an agent has accepted the offer. Subclasses should
     * react accordingly (e.g. create a room and send invitations to agent and user that made the request). 
     *
     * @param agentSession the agent that previously accepted the offer.
     */
    public void offerAccepted(AgentSession agentSession) {
        // Stop tracking this request
        requests.remove(requestID);
    }

    /**
     * Sends an offer to the specified agent for this request. The agent may accept or reject
     * the offer. Moreover, the offer may be revoked while the agent hasn't answered it. This
     * may happen for several reasons: request was cancelled, offer timed out, etc.
     *
     * @param session the agent that will get the offer.
     * @param queue queue that is sending the offer.
     * @return true if the offer was sent to the agent.
     */
    public boolean sendOffer(AgentSession session, RequestQueue queue) {
        // Keep track of this request by its ID
        requests.put(requestID, this);
        // Create new IQ to Send
        IQ offerPacket = new IQ();
        offerPacket.setFrom(queue.getWorkgroup().getJID());
        offerPacket.setTo(session.getJID());
        offerPacket.setType(IQ.Type.set);

        Element offerElement = offerPacket.setChildElement("offer", "http://jabber.org/protocol/workgroup");
        offerElement.addAttribute("id", requestID);
        offerElement.addAttribute("jid", getUserJID().toString());

        Element metaDataElement = getMetaDataElement();
        offerElement.add(metaDataElement);

        Element timeoutElement = offerElement.addElement("timeout");
        timeoutElement.setText(Long.toString(offer.getTimeout() / 1000));

        offerElement.add(getSessionElement());
        addOfferContent(offerElement);
        return session.sendOffer(offer, offerPacket);
    }

    /**
     * Revokes an offer that was previously sent to an agent.
     *
     * @param session the agent session that will get the revoke.
     * @param queue queue that is sending the offer.
     */
    public void sendRevoke(AgentSession session, RequestQueue queue) {
        IQ agentRevoke = new IQ();
        agentRevoke.setFrom(queue.getWorkgroup().getJID());
        agentRevoke.setTo(session.getJID());
        agentRevoke.setType(IQ.Type.set);

        Element revoke = agentRevoke.setChildElement("offer-revoke", "http://jabber.org/protocol/workgroup");
        revoke.addAttribute("id", requestID);
        revoke.addAttribute("jid", getUserJID().toString());
        revoke.addElement("reason").setText("The offer has timed out");
        revoke.add(getSessionElement());
        addRevokeContent(revoke);

        session.sendRevoke(offer, agentRevoke);
    }

    abstract void addOfferContent(Element offerElement);

    abstract void addRevokeContent(Element revoke);

    abstract JID getUserJID();

    public abstract void checkRequest(String roomID);

    /**
     * Notification message indicating that someone has joined the room where the support
     * session is taking place.
     *
     * @param roomJID the jid of the room where the occupant has joined.
     * @param user the JID of the user that joined the room.
     */
    public abstract void userJoinedRoom(JID roomJID, JID user);

    public enum CancelType {

        /**
         * An agent was not found so the user that sent the offer will be removed from the queue.
         */
        AGENT_NOT_FOUND("agent-not-found"),

        /**
         * The user requested to depart the queue or an administrator asked to remove the user from
         * the queue.
         */
        DEPART("departure-requested");

        private String description;

        CancelType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Returns a string representation of a list of strings. Each string contained in the
     * collection will be separated by a '/' character.
     *
     * @param values the collection of strings to encode.
     * @return a string representation of a list of strings.
     */
    public static String encodeMetadataValue(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (Iterator<String> it = values.iterator(); it.hasNext();) {
            builder.append(it.next());
            if (it.hasNext()) {
                builder.append("/");
            }
        }
        return builder.toString();
    }

    /**
     * Returns a collection of strings from an encoded string. The encoded string used a '/'
     * character as a separator between each value.
     *
     * @param values the encoded string where the values are separated by '/'.
     * @return a collection of strings from an encoded string.
     */
    public static List<String> decodeMetadataValue(String values) {
        List<String> answers = new ArrayList<String>();
        StringTokenizer tokenizer = new StringTokenizer(values, "/");
        while (tokenizer.hasMoreTokens()) {
            answers.add(tokenizer.nextToken());
        }
        return answers;
    }
}