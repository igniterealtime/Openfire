/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.archive;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.muc.MUCEventDispatcher;
import org.jivesoftware.openfire.muc.MUCEventListener;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.picocontainer.Startable;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import java.util.Date;

/**
 * Interceptor of MUC events of the local conferencing service. The interceptor is responsible
 * for reacting to users joining and leaving rooms as well as messages being sent to rooms.
 *
 * @author Gaston Dombiak
 */
public class GroupConversationInterceptor implements MUCEventListener, Startable {


    private ConversationManager conversationManager;

    public GroupConversationInterceptor(ConversationManager conversationManager) {
        this.conversationManager = conversationManager;
    }

    public void roomCreated(JID roomJID) {
        //Do nothing
    }

    public void roomDestroyed(JID roomJID) {
        // Process this event in the senior cluster member or local JVM when not in a cluster
        if (ClusterManager.isSeniorClusterMember()) {
            conversationManager.roomConversationEnded(roomJID, new Date());
        }
        else {
            ConversationEventsQueue eventsQueue = conversationManager.getConversationEventsQueue();
            eventsQueue.addGroupChatEvent(conversationManager.getRoomConversationKey(roomJID),
                    ConversationEvent.roomDestroyed(roomJID, new Date()));
        }
    }

    public void occupantJoined(JID roomJID, JID user, String nickname) {
        // Process this event in the senior cluster member or local JVM when not in a cluster
        if (ClusterManager.isSeniorClusterMember()) {
            conversationManager.joinedGroupConversation(roomJID, user, nickname, new Date());
        }
        else {
            ConversationEventsQueue eventsQueue = conversationManager.getConversationEventsQueue();
            eventsQueue.addGroupChatEvent(conversationManager.getRoomConversationKey(roomJID),
                    ConversationEvent.occupantJoined(roomJID, user, nickname, new Date()));
        }
    }

    public void occupantLeft(JID roomJID, JID user) {
        // Process this event in the senior cluster member or local JVM when not in a cluster
        if (ClusterManager.isSeniorClusterMember()) {
            conversationManager.leftGroupConversation(roomJID, user, new Date());
            // If there are no more occupants then consider the group conversarion over
            MUCRoom mucRoom = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(roomJID).getChatRoom(roomJID.getNode());
            if (mucRoom != null &&  mucRoom.getOccupantsCount() == 0) {
                conversationManager.roomConversationEnded(roomJID, new Date());
            }
        }
        else {
            ConversationEventsQueue eventsQueue = conversationManager.getConversationEventsQueue();
            eventsQueue.addGroupChatEvent(conversationManager.getRoomConversationKey(roomJID),
                    ConversationEvent.occupantLeft(roomJID, user, new Date()));
        }
    }

    public void nicknameChanged(JID roomJID, JID user, String oldNickname, String newNickname) {
        // Process this event in the senior cluster member or local JVM when not in a cluster
        if (ClusterManager.isSeniorClusterMember()) {
            occupantLeft(roomJID, user);
            // Sleep 1 millisecond so that there is a delay between logging out and logging in
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                // Ignore
            }
            occupantJoined(roomJID, user, newNickname);
        }
        else {
            ConversationEventsQueue eventsQueue = conversationManager.getConversationEventsQueue();
            eventsQueue.addGroupChatEvent(conversationManager.getRoomConversationKey(roomJID),
                    ConversationEvent.nicknameChanged(roomJID, user, newNickname, new Date()));
        }
    }

    public void messageReceived(JID roomJID, JID user, String nickname, Message message) {
        // Process this event in the senior cluster member or local JVM when not in a cluster
        if (ClusterManager.isSeniorClusterMember()) {
            conversationManager.processRoomMessage(roomJID, user, nickname, message.getBody(), new Date());
        }
        else {
            boolean withBody = conversationManager.isRoomArchivingEnabled() && (
                    conversationManager.getRoomsArchived().isEmpty() ||
                            conversationManager.getRoomsArchived().contains(roomJID.getNode()));

            ConversationEventsQueue eventsQueue = conversationManager.getConversationEventsQueue();
            eventsQueue.addGroupChatEvent(conversationManager.getRoomConversationKey(roomJID),
                    ConversationEvent.roomMessageReceived(roomJID, user, nickname, withBody ? message.getBody() : null, new Date()));
        }
    }
     
    public void privateMessageRecieved(JID toJID, JID fromJID, Message message) {
        if(message.getBody() != null) {
             if (ClusterManager.isSeniorClusterMember()) {
                 conversationManager.processMessage(fromJID, toJID, message.getBody(), new Date());
             }
             else {
                 ConversationEventsQueue eventsQueue = conversationManager.getConversationEventsQueue();
                 eventsQueue.addChatEvent(conversationManager.getConversationKey(fromJID, toJID),
                         ConversationEvent.chatMessageReceived(toJID, fromJID,
                                 conversationManager.isMessageArchivingEnabled() ? message.getBody() : null,
                                 new Date()));
             }
         }
    }

    public void roomSubjectChanged(JID roomJID, JID user, String newSubject) {
        // Do nothing
    }

    public void start() {
        MUCEventDispatcher.addListener(this);
    }

    public void stop() {
        MUCEventDispatcher.removeListener(this);
        conversationManager = null;
    }
}
