/**
 * $RCSfile$
 * $Revision: 19370 $
 * $Date: 2005-07-21 16:52:58 -0700 (Thu, 21 Jul 2005) $
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
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
package org.jivesoftware.openfire.fastpath.providers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.dom4j.Element;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.Log;
import org.jivesoftware.xmpp.workgroup.AgentNotFoundException;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.WorkgroupProvider;
import org.jivesoftware.xmpp.workgroup.utils.ModelUtil;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;

/**
 * AgentHistory is responsible for retrieving the information for one agent.
 */
public class AgentHistory implements WorkgroupProvider {

    private static final String GET_AGENT_SESSIONS =
            "SELECT sessionID, joinTime, leftTime FROM fpAgentSession WHERE agentJID=?";
    private static final String GET_SESSION_METADATA =
            "SELECT metadataname, metadatavalue FROM fpSessionMetadata WHERE sessionID=?";

    public boolean handleGet(IQ packet) {
        Element iq = packet.getChildElement();
        String name = iq.getName();

        return "chat-sessions".equals(name);
    }

    public boolean handleSet(IQ packet) {
        return false;
    }

    public void executeGet(IQ packet, Workgroup workgroup) {
        IQ reply = IQ.createResultIQ(packet);
        try {
            if (workgroup.getAgentManager().getAgentSession(packet.getFrom()) == null) {
                reply = IQ.createResultIQ(packet);
                reply.setChildElement(packet.getChildElement().createCopy());
                reply.setError(new PacketError(PacketError.Condition.not_authorized));
                workgroup.send(reply);
                return;
            }
        }
        catch (AgentNotFoundException e) {
            reply = IQ.createResultIQ(packet);
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(new PacketError(PacketError.Condition.not_authorized));
            workgroup.send(reply);
            return;
        }

        // Create session list
        List<AgentHistoryModel> sessionList = new ArrayList<AgentHistoryModel>();

        Element chatSessions = reply.setChildElement("chat-sessions", "http://jivesoftware.com/protocol/workgroup");


        Element iq = packet.getChildElement();
        String agentJID = iq.attributeValue("agentJID");
        String ms = iq.attributeValue("maxSessions");
        String date = iq.attributeValue("startDate");
        long startTime = 0;
        if (date != null) {
            startTime = Long.parseLong(date);
        }

        int maxSessions = Integer.parseInt(ms);
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GET_AGENT_SESSIONS);
            pstmt.setString(1, agentJID);
            ResultSet result = pstmt.executeQuery();
            while (result.next()) {
                String sessionID = result.getString(1);
                String joinTime = result.getString(2);
                String leftTime = result.getString(3);

                AgentHistoryModel model = new AgentHistoryModel();
                if (ModelUtil.hasLength(joinTime) && ModelUtil.hasLength(leftTime)) {
                    try {
                        long start = Long.valueOf(joinTime);
                        long end = Long.valueOf(leftTime);
                        long totalTime = end - start;

                        model.setSessionID(sessionID);
                        model.setJoinTime(joinTime);
                        model.setDuration(Long.toString(totalTime));

                        if (start >= startTime) {
                            sessionList.add(model);
                        }
                    }
                    catch (NumberFormatException e) {
                        Log.error(e);
                    }
                }
            }
            result.close();
        }
        catch (Exception ex) {
            Log.error(ex);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }

        buildAndSend(sessionList, chatSessions, reply, workgroup, maxSessions);
    }

    public void buildAndSend(List<AgentHistoryModel> list, Element chatSessions, IQ reply, Workgroup workgroup, int maxSessions) {
        for (AgentHistoryModel model : getNewList(list, maxSessions)) {
            Element session = chatSessions.addElement("chat-session");

            session.addElement("sessionID").setText(model.getSessionID());
            session.addElement("date").setText(model.getJoinTime());

            session.addElement("duration").setText(model.getDuration());
            session.addElement("visitorsName").setText(model.getName() == null ?
                    "" : model.getName());
            session.addElement("visitorsEmail").setText(model.getEmail() == null ?
                    "" : model.getEmail());
            session.addElement("question").setText(model.getQuestion() == null ?
                    "" : model.getQuestion());
        }
        workgroup.send(reply);
    }

    private List<AgentHistoryModel> getNewList(List<AgentHistoryModel> sessions, int maxSessions) {
        List<AgentHistoryModel> newList = new ArrayList<AgentHistoryModel>();

        Collections.sort(sessions, new Comparator<AgentHistoryModel>() {
            public int compare(AgentHistoryModel m1, AgentHistoryModel m2) {
                String str1 = m1.getJoinTime();
                String str2 = m2.getJoinTime();

                long i1 = Long.valueOf(str1);
                long i2 = Long.valueOf(str2);

                // Check if identical
                if (i1 == i2) {
                    return 0;
                }

                if (i1 > i2) {
                    return -1;
                }
                else if (i1 < i2) {
                    return 1;
                }
                else {
                    return 0;
                }
            }
        });

        int max = maxSessions < sessions.size() ? maxSessions : sessions.size();

        for (int i = 0; i < max; i++) {
            AgentHistoryModel model = sessions.get(i);
            appendSessionInformation(model);
            newList.add(model);
        }

        return newList;
    }

    private void appendSessionInformation(AgentHistoryModel model) {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GET_SESSION_METADATA);
            pstmt.setString(1, model.getSessionID());
            rs = pstmt.executeQuery();
            while (rs.next()) {
                String name = rs.getString(1);
                String value = rs.getString(2);
                if (name.equals("username")) {
                    model.setName(value);
                }
                else if (name.equals("email")) {
                    model.setEmail(value);
                }
                else if (name.equals("question")) {
                    model.setQuestion(value);
                }
            }
        }
        catch (Exception ex) {
            Log.error(ex);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    public void executeSet(IQ packet, Workgroup workgroup) {

    }

    private class AgentHistoryModel {
        private String sessionID;
        private String joinTime;
        private String duration;
        private String name;
        private String email;
        private String question;

        AgentHistoryModel() {

        }

        public String getSessionID() {
            return sessionID;
        }

        public void setSessionID(String sessionID) {
            this.sessionID = sessionID;
        }

        public void setJoinTime(String joinTime) {
            this.joinTime = joinTime;
        }

        public String getJoinTime() {
            return joinTime;
        }

        public void setDuration(String duration) {
            this.duration = duration;
        }

        public String getDuration() {
            return duration;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getEmail() {
            return email;
        }

        public void setQuestion(String question) {
            this.question = question;
        }

        public String getQuestion() {
            return question;
        }

    }
}