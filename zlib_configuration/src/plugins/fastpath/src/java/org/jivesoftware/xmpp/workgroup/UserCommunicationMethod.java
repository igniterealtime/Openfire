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

package org.jivesoftware.xmpp.workgroup;

import org.jivesoftware.xmpp.workgroup.request.Request;
import org.jivesoftware.xmpp.workgroup.request.UserRequest;
import org.xmpp.packet.JID;

/**
 * Users may try to join a workgroup using clients with different capabilities. Therefore, the
 * way for sending notifications to the user may differ depending on the client capabilities.<p>
 *
 * For instance, for sending notifications to a client that supports the
 * <a href="http://www.jabber.org/jeps/jep-0142.html">Workgroup protocol</a> packets that
 * follow the specification will be used. On the other hand, if a user contacted the workgroup
 * using a standard XMPP client that does not have support for the Workgroup protocol, using
 * possibly a chatbot, then standard XMPP packets with no special extensions will be used.
 *
 * @author Gaston Dombiak
 */
public interface UserCommunicationMethod {

    /**
     * Notifies the user his status in the waiting queue. Users will normaly receive a packet
     * that contains the position in the queue as well as the estimated remaining time.
     *
     * @param sender    the JID of the queue whose status is being notifed.
     * @param receiver  the JID of the user that will receive the notification.
     * @param request   the UserRequest that the user made to join a workgroup.
     * @param isPolling true if the user requested the information.
     */
    void notifyQueueStatus(JID sender, JID receiver, UserRequest request, boolean isPolling);

    /**
     * Notifies the user that he has left the waiting queue. Users may leave the waiting
     * queue because they cancelled their initial request or because no agent was found or
     * nobody accepted the request and the request timed out. This kind of notifications
     * usually include the reason for the departure.
     *
     * @param sender   the JID of the queue that the user left.
     * @param receiver the JID of the user that will receive the notification.
     * @param request  the UserRequest that the user made to join a workgroup.
     * @param type     the type of Cancel. The type will be used to explain the reason of the
     *                 departure.
     */
    void notifyQueueDepartued(JID sender, JID receiver, UserRequest request, Request.CancelType type);

    /**
     * Notification message saying that the request has been accepted by an agent and that
     * invitations have been sent to the agent and the user that made the request.
     *
     * @param request  the Request that the user made to join a workgroup.
     */
    void invitationsSent(UserRequest request);

    /**
     * Notification message saying that the user that made the request has joined the room to have
     * a chat with an agent.
     *
     * @param request  the Request that the user made to join a workgroup.
     */
    void supportStarted(UserRequest request);

    /**
     * Notification message saying that the support session with an agent has ended. Implementors
     * may decide to gather information about quality of service at this point.
     *
     * @param request  the Request that the user made to join a workgroup.
     */
    void supportEnded(UserRequest request);

    /**
     * A previous invitation to the specified room was not answered so offer again the user
     * the option to receive a new invitation. Different implementations may decide to unilaterally
     * send a new invitation while others may ask the user for his opinion.
     *
     * @param request the Request that the user made to join a workgroup.
     */
    void checkInvitation(UserRequest request);
}
