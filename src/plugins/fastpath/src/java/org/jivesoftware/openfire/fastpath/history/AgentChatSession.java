/**

 * $RCSfile$
 * $Revision$
 * $Date: 2006-08-07 21:12:21 -0700 (Mon, 07 Aug 2006) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.fastpath.history;

/**
 * Represents the data model for one agent chat session.
 *
 * @author Derek DeMoro
 */
public class AgentChatSession {
    String agentJID;
    long startTime;
    long endTime;

    /**
     * Creates a new instance of AgentChatSession
     * @param agentJID the agents JID
     * @param startTime the time in the agent joined the conversation.
     * @param endTime the time the agent left the conversation.
     */
    public AgentChatSession(String agentJID, long startTime, long endTime) {
        this.agentJID = agentJID;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getAgentJID() {
        return agentJID;
    }


    public void setAgentJID(String agentJID) {
        this.agentJID = agentJID;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }
}
