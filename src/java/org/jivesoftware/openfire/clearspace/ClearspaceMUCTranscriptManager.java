package org.jivesoftware.openfire.clearspace;

import org.jivesoftware.openfire.muc.MUCEventListener;
import org.jivesoftware.openfire.muc.MUCEventDispatcher;
import org.jivesoftware.util.TaskEngine;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.cache.CacheSizes;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

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
 * TODO: Add example
 *
 * @author Armando Jagucki
 */
public class ClearspaceMUCTranscriptManager implements MUCEventListener {

    /**
     * Group chat events that are pending to be sent to Clearspace.
     * Key: MUC Room JID; Value: List of group chat events.
     */
    private final List<ClearspaceMUCTranscriptEvent> roomEvents;

    private final TaskEngine taskEngine;
    private TimerTask  transcriptUpdateTask;

    private final int MAX_QUEUE_SIZE = 32768;   // TODO: Fine tune this size value during testing
    private final long  FLUSH_PERIOD = JiveConstants.MINUTE * 1;    // TODO: FIXME: Set to 5 when done testing

    public ClearspaceMUCTranscriptManager(TaskEngine taskEngine) {
        this.taskEngine = taskEngine;
        roomEvents = new ArrayList<ClearspaceMUCTranscriptEvent>();
    }

    public void start() {
        MUCEventDispatcher.addListener(this);

        // Schedule a task for this new transcript event queue.
        transcriptUpdateTask = new TimerTask() {
            public void run() {
                if (roomEvents.isEmpty()) {
                    return;
                }

                for (ClearspaceMUCTranscriptEvent event : roomEvents) {
                    // Add event to the packet

                    // TODO: FIXME: Remove after finished testing
                    Log.info(event.roomJID.getNode() + " - " + event.nickname + ": " + event.body);
                }

                // TODO: Send the packet to Clearspace

                // We can empty the queue now
                roomEvents.clear();
            }
        };

        taskEngine.schedule(transcriptUpdateTask, FLUSH_PERIOD, FLUSH_PERIOD);
    }

    public void stop() {
        MUCEventDispatcher.removeListener(this);
    }

    public void roomCreated(JID roomJID) {
        // TODO: Implement
    }

    public void roomDestroyed(JID roomJID) {
        // TODO: Implement
    }

    public void occupantJoined(JID roomJID, JID user, String nickname) {
        // TODO: Implement
    }

    public void occupantLeft(JID roomJID, JID user) {
        // TODO: Implement
    }

    public void nicknameChanged(JID roomJID, JID user, String oldNickname, String newNickname) {
        // TODO: Implement
    }

    public void messageReceived(JID roomJID, JID user, String nickname, Message message) {
        addGroupChatEvent(ClearspaceMUCTranscriptEvent.roomMessageReceived(roomJID, user, nickname, message.getBody(),
                                                                           new Date()));
    }

    /**
     * Queues the group chat event to be later sent to Clearspace.
     *
     * @param event MUC transcript event.
     */
    private void addGroupChatEvent(ClearspaceMUCTranscriptEvent event) {
        roomEvents.add(event);

        // Check if we have exceeded the allowed size before a flush should occur.
        if (getEffectiveQueueSize(roomEvents) > MAX_QUEUE_SIZE) {
            // Flush the queue immediately and reschedule the task.
            transcriptUpdateTask.cancel();
            transcriptUpdateTask.run();
            taskEngine.schedule(transcriptUpdateTask, FLUSH_PERIOD, FLUSH_PERIOD);
        }
    }

    /**
     * Used to estimate the 'effective' size of the event queue as represented in the
     * transcript-update packet sent by ClearspaceMUCTranscriptManager. We are not calculating
     * the size of the object in memory, but rather the approximate size of the resulting packet
     * to be sent, in bytes.
     *
     * @return the estimated size of the event queue represented as a transcript-update packet.
     */
    public int getEffectiveQueueSize(List<ClearspaceMUCTranscriptEvent> queue) {
        return CacheSizes.sizeOfCollection(queue);
    }
}
