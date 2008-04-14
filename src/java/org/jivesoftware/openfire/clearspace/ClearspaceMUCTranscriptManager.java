package org.jivesoftware.openfire.clearspace;

import org.jivesoftware.openfire.muc.MUCEventListener;
import org.jivesoftware.openfire.muc.MUCEventDispatcher;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.TaskEngine;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.JiveGlobals;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.IQ;
import org.dom4j.Element;

import java.util.*;

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

    /**
     * Group chat events that are pending to be sent to Clearspace.
     */
    private final List<ClearspaceMUCTranscriptEvent> roomEvents;

    private final TaskEngine taskEngine;
    private TimerTask  transcriptUpdateTask;

    private final int MAX_QUEUE_SIZE = 64;
    private final long  FLUSH_PERIOD =
            JiveGlobals.getLongProperty("clearspace.transcript.flush.period", JiveConstants.MINUTE * 2);

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
            public void run() {
                if (roomEvents.isEmpty()) {
                    return;
                }

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
                            break;
                        case occupantLeft:
                            mucEventElement = transcriptElement.addElement("presence");
                            mucEventElement.addAttribute("type", "unavailable");
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

                // Send the transcript-update packet to Clearspace.
                IQ result = ClearspaceManager.getInstance().query(packet, 15000);
                if (result == null) {
                    // No answer was received from Clearspace.
                    Log.warn("Did not get a reply from sending a transcript-update packet to Clearspace.");

                    // Return early so that the room-events queue is not cleared.
                    return;
                }

                // We can clear the queue now.
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
        // We want to flush the queue immediately when a room is destroyed.
        forceQueueFlush();
    }

    public void occupantJoined(JID roomJID, JID user, String nickname) {
        addGroupChatEvent(ClearspaceMUCTranscriptEvent.occupantJoined(roomJID, user, new Date().getTime()));
    }

    public void occupantLeft(JID roomJID, JID user) {
        addGroupChatEvent(ClearspaceMUCTranscriptEvent.occupantLeft(roomJID, user, new Date().getTime()));
    }

    public void nicknameChanged(JID roomJID, JID user, String oldNickname, String newNickname) {
        // Do nothing
    }

    public void messageReceived(JID roomJID, JID user, String nickname, Message message) {
        addGroupChatEvent(ClearspaceMUCTranscriptEvent.messageReceived(roomJID, user, message.getBody(),
                                                                       new Date().getTime()));
    }

    public void roomSubjectChanged(JID roomJID, JID user, String newSubject) {
        addGroupChatEvent(ClearspaceMUCTranscriptEvent.roomSubjectChanged(roomJID, user, newSubject,
                                                                          new Date().getTime()));
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
