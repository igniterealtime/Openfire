/*
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

package org.jivesoftware.xmpp.workgroup.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jivesoftware.xmpp.workgroup.AgentSession;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dispatches workgroup events. Since the dispatching is done on the "main" thread
 * it is important that WorkgroupEventListeners finish their jobs as soon as possible
 * or otherwise their jobs should be executed in another thread.
 *
 * @author Gaston Dombiak
 */
public class WorkgroupEventDispatcher {

    private static final Logger Log = LoggerFactory.getLogger(WorkgroupEventDispatcher.class);
    
    private static List<WorkgroupEventListener> listeners =
            new CopyOnWriteArrayList<WorkgroupEventListener>();

    private WorkgroupEventDispatcher() {
        // Not instantiable.
    }

    /**
     * Registers a listener to receive events.
     *
     * @param listener the listener.
     */
    public static void addListener(WorkgroupEventListener listener) {
        if (listener == null) {
            throw new NullPointerException();
        }
        listeners.add(listener);
    }

    /**
     * Unregisters a listener to receive events.
     *
     * @param listener the listener.
     */
    public static void removeListener(WorkgroupEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Notification message that a workgroup has been created.
     *
     * @param workgroup the workgroup that has just been created.
     */
    public static void workgroupCreated(Workgroup workgroup) {
        for (WorkgroupEventListener listener : listeners) {
            try {
                listener.workgroupCreated(workgroup);
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
        }
    }

    /**
     * Notification message that a workgroup is being removed.
     *
     * @param workgroup the workgroup being removed.
     */
    public static void workgroupDeleting(Workgroup workgroup) {
        for (WorkgroupEventListener listener : listeners) {
            try {
                listener.workgroupDeleting(workgroup);
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
        }
    }

    /**
     * Notification message that a workgroup has been removed.
     *
     * @param workgroup the removed workgroup.
     */
    public static void workgroupDeleted(Workgroup workgroup) {
        for (WorkgroupEventListener listener : listeners) {
            try {
                listener.workgroupDeleted(workgroup);
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
        }
    }

    /**
     * Notification message that a workgroup was closed and is now opened since
     * an agent is now available and the schedule is ok.
     *
     * @param workgroup the workgroup that has opened.
     */
    public static void workgroupOpened(Workgroup workgroup) {
        for (WorkgroupEventListener listener : listeners) {
            try {
                listener.workgroupOpened(workgroup);
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
        }
    }

    /**
     * Notification message that a workgroup has closed since no agent is available
     * or because of its schedule.
     *
     * @param workgroup the workgroup that has just closed.
     */
    public static void workgroupClosed(Workgroup workgroup) {
        for (WorkgroupEventListener listener : listeners) {
            try {
                listener.workgroupClosed(workgroup);
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
        }
    }

    /**
     * Notification message that an Agent has joined a Workgroup.
     *
     * @param workgroup    the workgroup where the agent has joined.
     * @param agentSession the session of the agent that has started.
     */
    public static void agentJoined(Workgroup workgroup, AgentSession agentSession) {
        for (WorkgroupEventListener listener : listeners) {
            try {
                listener.agentJoined(workgroup, agentSession);
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
        }
    }

    /**
     * Notification message that an Agent has left a Workgroup.
     *
     * @param workgroup    the workgroup where the agent has left.
     * @param agentSession the session of the agent that has ended.
     */
    public static void agentDeparted(Workgroup workgroup, AgentSession agentSession) {
        for (WorkgroupEventListener listener : listeners) {
            try {
                listener.agentDeparted(workgroup, agentSession);
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
        }
    }

    /**
     * Notification message that a support chat has been started.
     *
     * @param workgroup the workgroup providing the support.
     * @param sessionID the ID of the session that uniquely identifies the chat.
     */
    public static void chatSupportStarted(Workgroup workgroup, String sessionID) {
        for (WorkgroupEventListener listener : listeners) {
            try {
                listener.chatSupportStarted(workgroup, sessionID);
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
        }
    }

    /**
     * Notification message that a support chat has been finished.
     *
     * @param workgroup the workgroup that was providing the support.
     * @param sessionID the ID of the session that uniquely identifies the chat.
     */
    public static void chatSupportFinished(Workgroup workgroup, String sessionID) {
        for (WorkgroupEventListener listener : listeners) {
            try {
                listener.chatSupportFinished(workgroup, sessionID);
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
        }
    }

    /**
     * Notification message that an agent has joined a chat session. The agent could
     * be the initial agent that accepted the initial offer or may be an agent that
     * was invited to participate in the chat or maybe an agent that accepted a chat
     * tranfer.
     *
     * @param workgroup    the workgroup providing the support.
     * @param sessionID    the ID of the session that uniquely identifies the chat.
     * @param agentSession the session of the agent that joined the chat.
     */
    public static void agentJoinedChatSupport(Workgroup workgroup, String sessionID,
            AgentSession agentSession) {
        for (WorkgroupEventListener listener : listeners) {
            try {
                listener.agentJoinedChatSupport(workgroup, sessionID, agentSession);
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
        }
    }

    /**
     * Notification message that an agent has left a chat session.
     *
     * @param workgroup    the workgroup providing the support.
     * @param sessionID    the ID of the session that uniquely identifies the chat.
     * @param agentSession the session of the agent that left the chat.
     */
    public static void agentLeftChatSupport(Workgroup workgroup, String sessionID,
            AgentSession agentSession) {
        for (WorkgroupEventListener listener : listeners) {
            try {
                listener.agentLeftChatSupport(workgroup, sessionID, agentSession);
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
        }
    }

}
