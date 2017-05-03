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

package org.jivesoftware.openfire.fastpath.history;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Represents the data model for one complete Chat Session in Fastpath.
 *
 * @author Derek DeMoro
 */
public class ChatSession {

    private String sessionID;
    private String userID;
    private long workgroupID;
    private String transcript;
    private long startTime;
    private long endTime;

    private List<AgentChatSession> agentList = new ArrayList<AgentChatSession>();

    public Map<String, List<String>> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, List<String>> metadata) {
        this.metadata = metadata;
    }

    public void addAgent(AgentChatSession chatSession) {
        agentList.add(chatSession);
    }

    public Iterator<AgentChatSession> getAgents() {
        return agentList.iterator();
    }

    public List<AgentChatSession> getAgentList() {
        return agentList;
    }

    private Map<String, List<String>> metadata;

    public String getSessionID() {
        return sessionID;
    }

    public void setSessionID(String sessionID) {
        this.sessionID = sessionID;
    }

    public String getTranscript() {
        return transcript;
    }

    public void setTranscript(String transcript) {
        this.transcript = transcript;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public long getWorkgroupID() {
        return workgroupID;
    }

    public void setWorkgroupID(long workgroupID) {
        this.workgroupID = workgroupID;
    }

    public String getUserID() {
        return userID;
    }

    public void setUserID(String userID) {
        this.userID = userID;
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

    public long getQueueWaitTime() {
        return queueWaitTime;
    }

    public void setQueueWaitTime(long queueWaitTime) {
        this.queueWaitTime = queueWaitTime;
    }

    public String getCustomerName() {
        String name = metadata.get("username") == null || metadata.get("username").isEmpty() ?
            null : metadata.get("username").get(0);
        if (name == null || name.trim().length() == 0) {
            name = "Customer";
        }
        return name;
    }

    public String getEmail() {
        String email = metadata.get("email") == null || metadata.get("email").isEmpty() ?
            null : metadata.get("email").get(0);
        if (email == null || email.trim().length() == 0) {
            email = "n/ae";
        }
        return email;
    }

    public String getQuestion() {
        String question = metadata.get("question") == null || metadata.get("question").isEmpty() ? null : metadata.get("question").get(0);
        if (question == null || question.trim().length() == 0) {
            question = "n/a";
        }
        return question;
    }

    public AgentChatSession getFirstSession() {
        long startTime = -1;
        AgentChatSession returnSession = null;

        Iterator<AgentChatSession> iter = getAgents();
        while (iter.hasNext()) {
            AgentChatSession agent = iter.next();
            if (agent.getStartTime() <= startTime || startTime == -1) {
                startTime = agent.getStartTime();
                returnSession = agent;
            }
        }
        return returnSession;
    }

    long queueWaitTime;
    int state;


}
