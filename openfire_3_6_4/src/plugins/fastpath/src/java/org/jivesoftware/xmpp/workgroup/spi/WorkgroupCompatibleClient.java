/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2004-2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.xmpp.workgroup.spi;

import org.jivesoftware.xmpp.workgroup.UserCommunicationMethod;
import org.jivesoftware.xmpp.workgroup.request.Request;
import org.jivesoftware.xmpp.workgroup.request.UserRequest;
import org.dom4j.Element;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

/**
 * This communication method will be used when a user is using a client that supports the
 * Workgroup JEP. Most probably the user is using the LA's web client. If the Workgroup
 * JEP is implemented by standard XMPP clients then this class will be used too.<p>
 *
 * Only one instance of this class will exist for all the users since no state is stored for
 * each user session.
 *
 * @author Gaston Dombiak
 */
public class WorkgroupCompatibleClient implements UserCommunicationMethod {

    private static WorkgroupCompatibleClient instance = new WorkgroupCompatibleClient();

    /**
     * Returns the unique instance of this class.
     *
     * @return the unique instance of this class.
     */
    public static UserCommunicationMethod getInstance() {
        return instance;
    }

    /**
     * Hide the default constructor so there could only be one instance of this class
     */
    private WorkgroupCompatibleClient() {
        super();
    }

    public void notifyQueueStatus(JID sender, JID receiver, UserRequest request, boolean isPolling) {
        Packet statusPacket;
        if (isPolling) {
            statusPacket = new IQ();
        }
        else {
            statusPacket = new Message();
        }
        statusPacket.setFrom(sender);
        statusPacket.setTo(receiver);

        // Add Queue Status Packet to IQ
        Element status = statusPacket.getElement().addElement("queue-status",
                "http://jabber.org/protocol/workgroup");

        // Add Time Element
        Element time = status.addElement("time");
        time.setText(Integer.toString(request.getTimeStatus()));

        // Add Position Element
        Element position = status.addElement("position");
        position.setText(Integer.toString(request.getPosition() + 1));
        status.add(request.getSessionElement());

        // Send the queue status
        request.getWorkgroup().send(statusPacket);
    }

    public void notifyQueueDepartued(JID sender, JID receiver, UserRequest request,
            Request.CancelType type) {
        Message message = new Message();
        if (sender != null) {
            message.setFrom(sender);
        }
        message.setTo(receiver);
        Element depart = message.getElement().addElement("depart-queue",
                "http://jabber.org/protocol/workgroup");
        // Add an element that explains the reason why the user is being removed from the queue
        depart.addElement(type.getDescription());
        // Send the notification
        request.getWorkgroup().send(message);
    }

    public void invitationsSent(UserRequest request) {
        //Do nothing
    }

    public void checkInvitation(UserRequest request) {
        // Send a new invitation to the user
        request.getWorkgroup().sendUserInvitiation(request, request.getInvitedRoomID());
    }

    public void supportStarted(UserRequest request) {
        //Do nothing
    }

    public void supportEnded(UserRequest request) {
        //Do nothing
    }
}
