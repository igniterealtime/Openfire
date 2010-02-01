/**

 * $RCSfile$
 * $Revision$
 * $Date: 2006-08-07 21:12:21 -0700 (Mon, 07 Aug 2006) $
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
