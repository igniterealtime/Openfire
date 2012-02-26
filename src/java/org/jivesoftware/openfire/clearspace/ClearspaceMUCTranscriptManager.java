/**
 * Copyright (C) 2004-2009 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.clearspace;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimerTask;

import org.dom4j.Element;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.muc.MUCEventDispatcher;
import org.jivesoftware.openfire.muc.MUCEventListener;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MultiUserChatManager;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.TaskEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

/**
 * Stores MUC events that are intended to be recorded as a transcript for a group chat room in Clearspace.
 * A task will periodically flush the queue of MUC events, sending them to the Clearspace component via XMPP
 * for parsing and storing.
 *
 * Clearspace is expected to handle the packets containing MUC events by parsing them as they come in, accumulating
 * them into a daily group chat transcript for the room it is associated with.
 *
 * The task will flush each queue of MUC events assoicated with a room based on either the size of the queue, or time.
 * If the size of the queue exceeds a limit we have set, or a certain period of time has elapsed,
 * the queue will be sent to Clearspace -- whichever happens first. (When we say size of the queue, we really mean
 * the effective size as it will appear in a transcript-update packet).
 *
 * Example of a transcript-update packet:
 *     <iq type='set' to='clearspace.example.org' from='clearspace-conference.example.org'>
 *         <transcript-update xmlns='http://jivesoftware.com/clearspace'>
 *             <presence from='user1@example.org'>
 *                 <roomjid>14-1234@clearspace-conference.example.org</roomjid>
 *                 <timestamp>1207933781000</timestamp>
 *             </presence>
 *             <message from='user1@example.org'>
 *                 <roomjid>14-1234@clearspace-conference.example.org</roomjid>
 *                 <timestamp>1207933783000</timestamp>
 *                 <body>user2, I won the lottery!</body>
 *             </message>
 *             <message from='user2@example.org'>
 *                 <roomjid>14-1234@clearspace-conference.example.org</roomjid>
 *                 <timestamp>1207933785000</timestamp>
 *                 <body>WHAT?!</body>
 *             </message>
 *             <message from='user1@example.org'>
 *                 <roomjid>14-1234@clearspace-conference.example.org</roomjid>
 *                 <timestamp>1207933787000</timestamp>
 *                 <body>April Fools!</body>
 *             </message>
 *             <presence from='user3@example.org' type='unavailable'>
 *                 <roomjid>14-1234@clearspace-conference.example.org</roomjid>
 *                 <timestamp>1207933789000</timestamp>
 *             </presence>
 *             <message from="user2@example.org">
 *                 <roomjid>14-1234@clearspace-conference.example.org</roomjid>
 *                 <timestamp>120793379100</timestamp>
 *                 <body>Wow, that was lame.</body>
 *             </message>
 *
 *               ...
 *         </transcript-update>
 *     </iq>
 *
 * @author Armando Jagucki
 */
public class ClearspaceMUCTranscriptManager implements MUCEventListener {

	private static final Logger Log = LoggerFactory.getLogger(ClearspaceMUCTranscriptManager.class);

    /**
     * Group chat events that are pending to be sent to Clearspace.
     */
    private final List<ClearspaceMUCTranscriptEvent> roomEvents;

    private final TaskEngine taskEngine;
    private TimerTask  transcriptUpdateTask;

    private final int MAX_QUEUE_SIZE = 64;
    private final long  FLUSH_PERIOD =
            JiveGlobals.getLongProperty("clearspace.transcript.flush.period", JiveConstants.MINUTE * 1);

    private String csMucDomain;
    private String csComponentAddress;

    public ClearspaceMUCTranscriptManager(TaskEngine taskEngine) {
        this.taskEngine = taskEngine;
        roomEvents = new ArrayList<ClearspaceMUCTranscriptEvent>();

        String xmppDomain = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
        csMucDomain = ClearspaceManager.MUC_SUBDOMAIN + "." + xmppDomain;
        csComponentAddress = ClearspaceManager.CLEARSPACE_COMPONENT + "." + xmppDomain;
    }

    public void start() {
        MUCEventDispatcher.addListener(this);

        // Schedule a task for this new transcript event queue.
        transcriptUpdateTask = new TimerTask() {
            @Override
			public void run() {
                if (roomEvents.isEmpty()) {
                    return;
                }

                // Store JIDs of rooms that had presence changes, to track occupant counts
                // TODO: Refactor out into a different class
                Set<String> presenceRoomJids = new HashSet<String>();

                // Create the transcript-update packet
                IQ packet = new IQ();
                packet.setTo(csComponentAddress);
                packet.setFrom(csMucDomain);
                packet.setType(IQ.Type.set);
                Element transcriptElement = packet.setChildElement("transcript-update", "http://jivesoftware.com/clearspace");

                for (ClearspaceMUCTranscriptEvent event : roomEvents) {
                    // Add event to the packet
                    Element mucEventElement = null;

                    switch (event.type) {
                        case messageReceived:
                            mucEventElement = transcriptElement.addElement("message");
                            mucEventElement.addElement("body").setText(event.content);
                            break;
                        case occupantJoined:
                            mucEventElement = transcriptElement.addElement("presence");
                            presenceRoomJids.add(event.roomJID.toBareJID());
                            break;
                        case occupantLeft:
                            mucEventElement = transcriptElement.addElement("presence");
                            mucEventElement.addAttribute("type", "unavailable");
                            presenceRoomJids.add(event.roomJID.toBareJID());
                            break;
                        case roomSubjectChanged:
                            mucEventElement = transcriptElement.addElement("subject-change");
                            mucEventElement.addElement("subject").setText(event.content);
                            break;
                    }

                    // Now add those event fields that are common to all elements in the transcript-update packet.
                    if (mucEventElement != null) {
                        if (event.user != null) {
                            mucEventElement.addAttribute("from", event.user.toBareJID());
                        }
                        if (event.roomJID != null) {
                            mucEventElement.addElement("roomjid").setText(event.roomJID.toBareJID());
                        }
                        mucEventElement.addElement("timestamp").setText(Long.toString(event.timestamp));
                    }
                }

                // Add occupant count updates to packet
                // TODO: Refactor out into a different class
                MultiUserChatManager mucManager = XMPPServer.getInstance().getMultiUserChatManager();
                for (String roomJid : presenceRoomJids) {
                    JID jid = new JID(roomJid);
                    MultiUserChatService mucService = mucManager.getMultiUserChatService(jid);
                    MUCRoom room = mucService.getChatRoom(jid.getNode());
                    // Not count room owners as occupants
                    int totalOccupants = room.getOccupantsCount();
                    for (JID owner : room.getOwners()) {
                        try {
                            if (!room.getOccupantsByBareJID(owner).isEmpty()) {
                                totalOccupants--;
                            }
                        } catch (UserNotFoundException e) {
                            // Ignore
                        }
                    }

                    Element occUpdateElement = transcriptElement.addElement("occupant-count-update");
                    occUpdateElement.addElement("roomjid").setText(roomJid);
                    occUpdateElement.addElement("count").setText(Integer.toString(totalOccupants));
                }

                // Send the transcript-update packet to Clearspace.
                IQ result = ClearspaceManager.getInstance().query(packet, 15000);
                if (result == null) {
                    // No answer was received from Clearspace.
                    Log.warn("Did not get a reply from sending a transcript-update packet to Clearspace.");

                    // Return early so that the room-events queue is not cleared.
                    return;
                }
                else if (result.getType() == IQ.Type.error) {
                    // Clearspace was not able to process the transcript-update
                    Log.warn("Clearspace received a transcript-update packet but was not able to process it." + result.toXML());

                    // Return early so that the room-events queue is not cleared.
                    return;
                }

                // We can clear the queue now, as Clearspace has processed the transcript-update packet.
                roomEvents.clear();
            }
        };

        taskEngine.schedule(transcriptUpdateTask, FLUSH_PERIOD, FLUSH_PERIOD);
    }

    public void stop() {
        MUCEventDispatcher.removeListener(this);
    }

    public void roomCreated(JID roomJID) {
        // Do nothing
    }

    public void roomDestroyed(JID roomJID) {
        // Do nothing
    }

    public void occupantJoined(JID roomJID, JID user, String nickname) {
        if (isClearspaceRoom(roomJID) && !isRoomOwner(roomJID, user)) {
            addGroupChatEvent(ClearspaceMUCTranscriptEvent.occupantJoined(roomJID, user, new Date().getTime()));
        }
    }

    public void occupantLeft(JID roomJID, JID user) {
        if (isClearspaceRoom(roomJID) && !isRoomOwner(roomJID, user)) {
            addGroupChatEvent(ClearspaceMUCTranscriptEvent.occupantLeft(roomJID, user, new Date().getTime()));
        }
    }

    public void nicknameChanged(JID roomJID, JID user, String oldNickname, String newNickname) {
        // Do nothing
    }

    public void messageReceived(JID roomJID, JID user, String nickname, Message message) {
        if (isClearspaceRoom(roomJID) && !isRoomOwner(roomJID, user)) {
            addGroupChatEvent(ClearspaceMUCTranscriptEvent.messageReceived(roomJID, user, message.getBody(),
                    new Date().getTime()));
        }
    }

    public void privateMessageRecieved(JID fromJID, JID toJID, Message message) {
    }
    
    public void roomSubjectChanged(JID roomJID, JID user, String newSubject) {
        if (isClearspaceRoom(roomJID) && !isRoomOwner(roomJID, user)) {
            addGroupChatEvent(ClearspaceMUCTranscriptEvent.roomSubjectChanged(roomJID, user, newSubject,
                    new Date().getTime()));
        }
    }

    private boolean isRoomOwner(JID roomJID, JID user) {
        if (user == null || roomJID == null) {
            return false;
        }
        MultiUserChatService chatService =
                XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(roomJID);
        MUCRoom room = chatService.getChatRoom(roomJID.getNode());
        return room != null && room.getOwners().contains(user.toBareJID());
    }

    private boolean isClearspaceRoom(JID roomJID) {
        return roomJID.getDomain().equals(csMucDomain);
    }

    /**
     * Queues the group chat event to be later sent to Clearspace.
     *
     * @param event MUC transcript event.
     */
    private void addGroupChatEvent(ClearspaceMUCTranscriptEvent event) {
        roomEvents.add(event);

        // Check if we have exceeded the allowed size before a flush should occur.
        if (roomEvents.size() > MAX_QUEUE_SIZE) {
            // Flush the queue immediately and reschedule the task.
            forceQueueFlush();
        }
    }

    /**
     * Forces the transcript-event queue to be sent to Clearspace by running the transcript-update
     * task immediately.
     *
     * The transcript-update task is then rescheduled.
     */
    private void forceQueueFlush() {
        transcriptUpdateTask.cancel();
        transcriptUpdateTask.run();
        taskEngine.schedule(transcriptUpdateTask, FLUSH_PERIOD, FLUSH_PERIOD);
    }
}
