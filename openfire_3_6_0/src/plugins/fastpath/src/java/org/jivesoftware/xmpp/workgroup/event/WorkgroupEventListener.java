/**
 * $RCSfile$
 * $Revision: 18906 $
 * $Date: 2005-05-12 17:10:48 -0700 (Thu, 12 May 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.xmpp.workgroup.event;

import org.jivesoftware.xmpp.workgroup.AgentSession;
import org.jivesoftware.xmpp.workgroup.Workgroup;

/**
 * Interface to listen for workgroup events. Use the
 * {@link WorkgroupEventDispatcher#addListener(WorkgroupEventListener) addListener}
 * method to register for events.<p>
 *
 * Execution of each method should be really fast since the invocation will be done
 * on the "main" thread. Otherwise, another thread should be used for doing the real
 * job.
 *
 * @author Gaston Dombiak
 */
public interface WorkgroupEventListener {

    /**
     * Notification message that a workgroup has been created.
     *
     * @param workgroup the workgroup that has just been created.
     */
    public void workgroupCreated(Workgroup workgroup);

    /**
     * Notification message that a workgroup is being removed.
     *
     * @param workgroup the workgroup being removed.
     */
    public void workgroupDeleting(Workgroup workgroup);

    /**
     * Notification message that a workgroup has been removed.
     *
     * @param workgroup the removed workgroup.
     */
    public void workgroupDeleted(Workgroup workgroup);

    /**
     * Notification message that a workgroup was closed and is now opened since
     * an agent is now available and the schedule is ok.
     *
     * @param workgroup the workgroup that has opened.
     */
    public void workgroupOpened(Workgroup workgroup);

    /**
     * Notification message that a workgroup has closed since no agent is available
     * or because of its schedule.
     *
     * @param workgroup the workgroup that has just closed.
     */
    public void workgroupClosed(Workgroup workgroup);

    /**
     * Notification message that an Agent has joined a Workgroup or his presence has
     * been modified. Every time the agent changes his presence this event will
     * be triggered.
     *
     * @param workgroup    the workgroup where the agent has joined or changed his presence.
     * @param agentSession the session of the agent that has started.
     */
    public void agentJoined(Workgroup workgroup, AgentSession agentSession);

    /**
     * Notification message that an Agent has left a Workgroup.
     *
     * @param workgroup    the workgroup where the agent has left.
     * @param agentSession the session of the agent that has ended.
     */
    public void agentDeparted(Workgroup workgroup, AgentSession agentSession);

    /**
     * Notification message that a support chat has been started.
     *
     * @param workgroup the workgroup providing the support.
     * @param sessionID the ID of the session that uniquely identifies the chat.
     */
    public void chatSupportStarted(Workgroup workgroup, String sessionID);

    /**
     * Notification message that a support chat has been finished.
     *
     * @param workgroup the workgroup that was providing the support.
     * @param sessionID the ID of the session that uniquely identifies the chat.
     */
    public void chatSupportFinished(Workgroup workgroup, String sessionID);

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
    public void agentJoinedChatSupport(Workgroup workgroup, String sessionID,
            AgentSession agentSession);

    /**
     * Notification message that an agent has left a chat session.
     *
     * @param workgroup    the workgroup providing the support.
     * @param sessionID    the ID of the session that uniquely identifies the chat.
     * @param agentSession the session of the agent that left the chat.
     */
    public void agentLeftChatSupport(Workgroup workgroup, String sessionID,
            AgentSession agentSession);
}
