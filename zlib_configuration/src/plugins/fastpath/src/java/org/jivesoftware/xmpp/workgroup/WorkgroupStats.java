/**
 * $RCSfile$
 * $Revision: 28701 $
 * $Date: 2006-03-20 09:03:47 -0800 (Mon, 20 Mar 2006) $
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

package org.jivesoftware.xmpp.workgroup;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.FastDateFormat;
import org.jivesoftware.xmpp.workgroup.utils.ModelUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;

public class WorkgroupStats {

	private static final Logger Log = LoggerFactory.getLogger(WorkgroupStats.class);
	
    private static final String GET_SESSIONS_WITH_TRANSCRIPTS =
            "SELECT sessionID, startTime, endTime FROM fpSession WHERE workgroupID=? AND " +
            "userID=? AND transcript IS NOT NULL";
    private static final String GET_TRANSCRIPT =
            "SELECT transcript FROM fpSession WHERE sessionID=?";
    private static final String GET_SESSION_AGENTS =
            "SELECT sessionID, agentJID, joinTime, leftTime FROM fpAgentSession WHERE sessionID=?";

    private static final FastDateFormat UTC_FORMAT = FastDateFormat
        .getInstance("yyyyMMdd'T'HH:mm:ss", TimeZone.getTimeZone("GMT+0"));

    private List<Object[]> chatList = new ArrayList<Object[]>();
    private List<Object[]> transferList = new ArrayList<Object[]>();
    private Workgroup workgroup;

    // Defined Variables used in Stats
    public final static String ACTION = "ACTION";
    public final static String END_OF_CHAT = "END_OF_CHAT";
    public final static String START_OF_CHAT = "START_OF_CHAT";
    public final static String START_TIME = "START_TIME";
    public final static String END_TIME = "END_TIME";
    public final static String AGENT_JID = "AGENT_JID";
    public final static String AGENT_TRANSFER = "AGENT_TRANSFER";
    public final static String OTHER_AGENT_JID = "OTHER_AGENT_JID";
    public final static String CHAT_ROOM = "CHAT_ROOM";
    public final static String WORKGROUP_NAME = "WORKGROUP_NAME";

    public WorkgroupStats(Workgroup workgroup) {
        this.workgroup = workgroup;
    }

    public void processStatistics(Map<String, String> map) {
        final String action = map.get(ACTION);
        if (END_OF_CHAT.equals(action)) {
            String agent = map.get(AGENT_JID);
            Long startTime = new Long(map.get(START_TIME));
            Long endTime = new Long(map.get(END_TIME));
            // String chatRoom = (String)map.get(CHAT_ROOM);
            // String workgroupName = (String)map.get(WORKGROUP_NAME);
            chatList.add(new Object[]{agent, startTime, endTime});
        }
        else if (AGENT_TRANSFER.equals(action)) {
            final String agent = map.get(AGENT_JID);
            final Long startTime = new Long(map.get(START_TIME));
            final Long transferTime = new Long(map.get(END_TIME));
            final String agentTransferedTo = map.get(OTHER_AGENT_JID);
            transferList.add(new Object[]{agent, startTime, transferTime, agentTransferedTo});
        }
    }

    public Iterator<Object[]> getCompletedChats() {
        return chatList.iterator();
    }

    public Iterator<Object[]> getChatsTransfered() {
        return transferList.iterator();
    }

    public void getChatTranscripts(IQ iq, String uniqueUserID) {
        try {
            IQ replyPacket = IQ.createResultIQ(iq);
            Element transcripts = replyPacket.setChildElement("transcripts",
                "http://jivesoftware.com/protocol/workgroup");
            transcripts.addAttribute("userID", uniqueUserID);

            Connection con = null;
            PreparedStatement pstmt = null;
            ResultSet rs = null;
            try {
                con = DbConnectionManager.getConnection();
                pstmt = con.prepareStatement(GET_SESSIONS_WITH_TRANSCRIPTS);
                pstmt.setLong(1, workgroup.getID());
                pstmt.setString(2, uniqueUserID);
                rs = pstmt.executeQuery();
                while (rs.next()) {
                    String sessionID = rs.getString(1);
                    String joinTime = rs.getString(2);
                    String leftTime = rs.getString(3);

                    Element transcript = transcripts.addElement("transcript");

                    // Add the sessionID attribute
                    transcript.addAttribute("sessionID", sessionID);
                    // Add the list of agents involved in this session
                    Element agentElement = transcript.addElement("agents");
                    addAgentsToElement(agentElement, sessionID);
                    // Add the date when the attribute
                    if (joinTime != null && joinTime.length() > 0) {
                        transcript.addElement("joinTime").setText(UTC_FORMAT.format(new Date(Long.parseLong(joinTime))));
                    }
                    if (leftTime != null && leftTime.length() > 0) {
                        transcript.addElement("leftTime").setText(UTC_FORMAT.format(new Date(Long.parseLong(leftTime))));
                    }
                }
            }
            catch (Exception ex) {
                Log.error(
                    "Error retrieving chat transcript(s)", ex);
            }
            finally {
               DbConnectionManager.closeConnection(rs, pstmt, con);

                workgroup.send(replyPacket);
            }
        }
        catch (Exception ex) {
            Log.error(ex.getMessage(), ex);
        }
    }

    public void getChatTranscript(IQ iq, String sessionID) {
        final IQ reply = IQ.createResultIQ(iq);

        String transcriptXML = null;
        try {
            Element transcript = reply.setChildElement("transcript",
                "http://jivesoftware.com/protocol/workgroup");
            transcript.addAttribute("sessionID", sessionID);
            Connection con = null;
            PreparedStatement pstmt = null;
            ResultSet rs = null;
            try {
                con = DbConnectionManager.getConnection();
                pstmt = con.prepareStatement(GET_TRANSCRIPT);
                pstmt.setString(1, sessionID);
                rs = pstmt.executeQuery();
                if (rs.next()) {
                    transcriptXML = DbConnectionManager.getLargeTextField(rs, 1);
                }
            }
            catch (SQLException sqle) {
                Log.error(sqle.getMessage(), sqle);
            }
            finally {
                DbConnectionManager.closeConnection(rs, pstmt, con);
            }
            if (transcriptXML != null) {
                Document element = DocumentHelper.parseText(transcriptXML);
                // Add the Messages and Presences contained in the retrieved transcript element
                for (Iterator<Element> it = element.getRootElement().elementIterator(); it.hasNext();) {
                    Element packet = it.next();
                    transcript.add(packet.createCopy());
                }
            }
            workgroup.send(reply);
        }
        catch (Exception ex) {
            Log.error(
                    "There was an error retrieving the following transcript. SessionID = " +
                    sessionID + " Transcript=" + transcriptXML, ex);

            reply.setChildElement(iq.getChildElement().createCopy());
            reply.setError(new PacketError(PacketError.Condition.item_not_found));
            workgroup.send(reply);
        }
    }

    private void addAgentsToElement(Element elem, String sessionID) {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GET_SESSION_AGENTS);
            pstmt.setString(1, sessionID);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                String agentJID = rs.getString(2);
                String joinTime = rs.getString(3);
                String leftTime = rs.getString(4);

                final Element agentElement = elem.addElement("agent");
                agentElement.addElement("agentJID").setText(agentJID);

                if (ModelUtil.hasLength(joinTime)) {
                    agentElement.addElement("joinTime").setText(UTC_FORMAT.format(new Date(Long.parseLong(joinTime))));
                }

                if (ModelUtil.hasLength(leftTime)) {
                    agentElement.addElement("leftTime").setText(UTC_FORMAT.format(new Date(Long.parseLong(leftTime))));
                }
            }
        }
        catch (Exception ex) {
            Log.error(ex.getMessage(), ex);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }
}