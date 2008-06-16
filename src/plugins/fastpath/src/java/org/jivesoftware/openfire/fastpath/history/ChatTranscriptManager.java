/**
 * $RCSfile$
 * $Revision: 3144 $
 * $Date: 2005-12-01 14:20:11 -0300 (Thu, 01 Dec 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.fastpath.history;

import org.jivesoftware.xmpp.workgroup.DbProperties;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.WorkgroupManager;
import org.jivesoftware.xmpp.workgroup.request.Request;
import org.jivesoftware.xmpp.workgroup.utils.ModelUtil;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.EmailService;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.StringUtils;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.packet.JID;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Utility class to control the update and retrieval of Chat Transcripts within the
 * Fastpath system.
 *
 * @author Derek DeMoro
 */
public class ChatTranscriptManager {

    private static final String GET_WORKGROUP_SESSIONS =
            "SELECT sessionID, userID, startTime, endTime, queueWaitTime, state " +
            "FROM fpSession WHERE workgroupID=? AND startTime>=? AND endTime<=?";
    private static final String GET_SESSION_TRANSCRIPT =
            "SELECT transcript FROM fpSession WHERE sessionID=?";
    private static final String GET_SESSION =
            "SELECT userID, workgroupID, transcript, startTime, endTime, queueWaitTime, state " +
            "FROM fpSession WHERE sessionID=?";
    private static final String GET_SESSION_AGENTS =
            "SELECT agentJID, joinTime, leftTime FROM fpAgentSession WHERE sessionID=?";
    private static final String GET_SESSION_META_DATA =
            "SELECT metadataName, metadataValue FROM fpSessionMetadata WHERE sessionID=?";

    private ChatTranscriptManager() {
    }

    /**
     * Returns a collection of ChatSessions for a particular workgroup.
     *
     * @param workgroup the workgroup.
     * @param start retrieve all ChatSessions at or after this specified date.
     * @param end retrieve all ChatSessions before or on this specified date.
     * @return a collection of ChatSessions.
     */
    public static Collection<ChatSession> getChatSessionsForWorkgroup(Workgroup workgroup,
            Date start, Date end)
    {
        final List<ChatSession> resultList = new ArrayList<ChatSession>();

        long wgID = workgroup.getID();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GET_WORKGROUP_SESSIONS);
            pstmt.setLong(1, wgID);
            pstmt.setString(2, StringUtils.dateToMillis(start));
            pstmt.setString(3, StringUtils.dateToMillis(end));
            rs = pstmt.executeQuery();
            while (rs.next()) {
                String sessionID = rs.getString(1);
                String userID = rs.getString(2);
                String startTime = rs.getString(3);
                String endTime = rs.getString(4);
                long queueWaitTime = rs.getLong(5);
                int state = rs.getInt(6);

                ChatSession session = new ChatSession();
                session.setSessionID(sessionID);
                session.setUserID(userID);
                session.setWorkgroupID(wgID);
                if (startTime.trim().length() > 0) {
                    session.setStartTime(Long.parseLong(startTime));
                }
                if (endTime.trim().length() > 0) {
                    session.setEndTime(Long.parseLong(endTime));
                }

                session.setQueueWaitTime(queueWaitTime);
                session.setState(state);

                populateSessionWithMetadata(session);
                populateSessionWithAgents(session);
                resultList.add(session);
            }
        }
        catch (Exception ex) {
            ComponentManagerFactory.getComponentManager().getLog().error(ex);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        // Sort by date
        Collections.sort(resultList, dateComparator);
        return resultList;
    }

    /**
     * Return the plain text version of a chat transcript.
     *
     * @param sessionID the sessionID of the <code>ChatSession</code>
     * @return the plain text version of a chat transcript.
     */
    public static String getTextTranscriptFromSessionID(String sessionID) {
        String transcript = null;

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GET_SESSION_TRANSCRIPT);
            pstmt.setString(1, sessionID);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                transcript = DbConnectionManager.getLargeTextField(rs, 1);
            }
        }
        catch (Exception ex) {
            ComponentManagerFactory.getComponentManager().getLog().error(ex);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        if (transcript == null || "".equals(transcript)) {
            return "";
        }

        // Define time zone used in the transcript.
        SimpleDateFormat UTC_FORMAT = new SimpleDateFormat(JiveConstants.XMPP_DELAY_DATETIME_FORMAT);
        UTC_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));

        final SimpleDateFormat formatter = new SimpleDateFormat("h:mm a");

        Document element = null;
        try {
            element = DocumentHelper.parseText(transcript);
        }
        catch (DocumentException e) {
            ComponentManagerFactory.getComponentManager().getLog().error(e);
        }

        StringBuilder buf = new StringBuilder();

        // Add the Messages and Presences contained in the retrieved transcript element
        for (Iterator it = element.getRootElement().elementIterator(); it.hasNext();) {
            Element packet = (Element)it.next();
            String name = packet.getName();

            String message = "";
            String from = "";
            if ("presence".equals(name)) {
                String type = packet.attributeValue("type");
                from = new JID(packet.attributeValue("from")).getResource();
                if (type == null) {
                    message = from + " has joined the room";
                }
                else {
                    message = from + " has left the room";
                }
            }
            else if ("message".equals(name)) {
                from = new JID(packet.attributeValue("from")).getResource();
                message = packet.elementText("body");
                message = StringUtils.escapeHTMLTags(message);
            }

            List el = packet.elements("x");
            Iterator iter = el.iterator();
            while (iter.hasNext()) {
                Element ele = (Element)iter.next();
                if ("jabber:x:delay".equals(ele.getNamespaceURI())) {
                    String stamp = ele.attributeValue("stamp");
                    try {
                        String formattedDate;
                        synchronized (UTC_FORMAT) {
                            Date d = UTC_FORMAT.parse(stamp);
                            formattedDate = formatter.format(d);
                        }

                        if ("presence".equals(name)) {
                            buf.append("[").append(formattedDate).append("] ").append(message)
                                    .append("\n");
                        }
                        else {
                            buf.append("[").append(formattedDate).append("] ").append(from)
                                    .append(": ").append(message).append("\n");
                        }
                    }
                    catch (ParseException e) {
                        ComponentManagerFactory.getComponentManager().getLog().error(e);
                    }
                }
            }
        }

        return buf.toString();
    }

    /**
     * Retrieves a <code>ChatSession</code> based on it's session ID.
     *
     * @param sessionID the session ID.
     * @return the ChatSession or null if no ChatSession is found.
     */
    public static ChatSession getChatSession(String sessionID) {
        final ChatSession session = new ChatSession();

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GET_SESSION);
            pstmt.setString(1, sessionID);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                String userID = rs.getString(1);
                long workgroupID = rs.getLong(2);
                String transcript = DbConnectionManager.getLargeTextField(rs, 3);
                String startTime = rs.getString(4);
                String endTime = rs.getString(5);
                long queueWaitTime = rs.getLong(6);
                int state = rs.getInt(7);

                session.setSessionID(sessionID);
                session.setWorkgroupID(workgroupID);
                session.setUserID(userID);
                session.setTranscript(formatTranscript(transcript));
                if (startTime.trim().length() > 0) {
                    session.setStartTime(Long.parseLong(startTime));
                }
                if (endTime.trim().length() > 0) {
                    session.setEndTime(Long.parseLong(endTime));
                }

                session.setQueueWaitTime(queueWaitTime);
                session.setState(state);

                if (startTime.trim().length() > 0 && endTime.trim().length() > 0) {
                    populateSessionWithMetadata(session);
                    populateSessionWithAgents(session);
                }
            }
        }
        catch (Exception ex) {
            ComponentManagerFactory.getComponentManager().getLog().error(ex);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        return session;
    }

    /**
     * Adds all metadata associated with a <code>ChatSession</code>
     *
     * @param session the ChatSession.
     */
    public static void populateSessionWithMetadata(ChatSession session) {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GET_SESSION_META_DATA);
            pstmt.setString(1, session.getSessionID());
            rs = pstmt.executeQuery();
            Map<String, List<String>> metadata = new HashMap<String, List<String>>();
            while (rs.next()) {
                String name = rs.getString(1);
                String value = rs.getString(2);
                metadata.put(name, Request.decodeMetadataValue(value));
            }
            session.setMetadata(metadata);
        }
        catch (Exception ex) {
            ComponentManagerFactory.getComponentManager().getLog().error(ex);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    /**
     * Adds all participating agents to a <code>ChatSession</code>
     *
     * @param session the ChatSession.
     */
    public static void populateSessionWithAgents(ChatSession session) {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GET_SESSION_AGENTS);
            pstmt.setString(1, session.getSessionID());
            rs = pstmt.executeQuery();
            while (rs.next()) {
                String agentJID = rs.getString(1);
                String joinTime = rs.getString(2);
                String endTime = rs.getString(3);
                long start = -1;
                if (joinTime != null && joinTime.trim().length() > 0) {
                    start = Long.parseLong(joinTime);
                }

                long end = -1;
                if (endTime != null && endTime.trim().length() > 0) {
                    end = Long.parseLong(endTime);
                }
                AgentChatSession agentSession = new AgentChatSession(agentJID, start, end);
                session.addAgent(agentSession);
            }
        }
        catch (Exception ex) {
            ComponentManagerFactory.getComponentManager().getLog().error(ex);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    /**
     * Formats a given XML Chat Transcript.
     *
     * @param transcript the XMP ChatTranscript.
     * @return the pretty-version of a transcript.
     */
    public static String formatTranscript(String transcript) {
        if (transcript == null || "".equals(transcript)) {
            return "";
        }
        final SimpleDateFormat UTC_FORMAT = new SimpleDateFormat(JiveConstants.XMPP_DELAY_DATETIME_FORMAT);
        UTC_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));

        final SimpleDateFormat formatter = new SimpleDateFormat("h:mm a");


        Document element = null;
        try {
            element = DocumentHelper.parseText(transcript);
        }
        catch (DocumentException e) {
            ComponentManagerFactory.getComponentManager().getLog().error(e);
        }

        StringBuilder buf = new StringBuilder();
        String conv1 = null;

        // Add the Messages and Presences contained in the retrieved transcript element
        for (Iterator it = element.getRootElement().elementIterator(); it.hasNext();) {
            Element packet = (Element)it.next();
            String name = packet.getName();

            String message = "";
            String from = "";
            if ("presence".equals(name)) {
                String type = packet.attributeValue("type");
                from = new JID(packet.attributeValue("from")).getResource();
                if (type == null) {
                    message = from + " has joined the room";
                }
                else {
                    message = from + " has left the room";
                }
            }
            else if ("message".equals(name)) {
                from = new JID(packet.attributeValue("from")).getResource();
                message = packet.elementText("body");
                message = StringUtils.escapeHTMLTags(message);
                if(conv1 == null){
                    conv1 = from;
                }
            }

            List el = packet.elements("x");
            Iterator iter = el.iterator();
            while (iter.hasNext()) {
                Element ele = (Element)iter.next();
                if ("jabber:x:delay".equals(ele.getNamespaceURI())) {
                    String stamp = ele.attributeValue("stamp");
                    try {
                        String formattedDate;
                        synchronized (UTC_FORMAT) {
                            Date d = UTC_FORMAT.parse(stamp);
                            formattedDate = formatter.format(d);
                        }

                        if ("presence".equals(name)) {
                            buf.append(
                                    "<tr valign=\"top\"><td class=\"notification-label\" colspan=2 nowrap>[")
                                    .append(formattedDate).append("] ").append(message)
                                    .append("</td></tr>");
                        }
                        else {
                            String cssClass = conv1.equals(from) ? "conversation-label1" : "conversation-label2";
                            buf.append(
                                    "<tr valign=\"top\"><td width=1% class=\""+cssClass+"\" nowrap>[")
                                    .append(formattedDate).append("] ").append(from)
                                    .append(":</td><td class=\"conversation-body\">").append(message).append("</td></tr>");
                        }
                    }
                    catch (ParseException e) {
                        ComponentManagerFactory.getComponentManager().getLog().error(e);
                    }
                }
            }
        }

        return buf.toString();
    }

    /**
     * Sends a transcript mapped by it's sessionID.
     *
     * @param sessionID the sessionID of the Chat.
     * @param from      specify who the email is from.
     * @param to        specify the email address of the person to receive the email.
     * @param body      specify header text to use in the email.
     * @param subject   specify the subject of this email.
     */
    public static void sendTranscriptByMail(String sessionID, String from, String to, String body, String subject) {
        final ChatSession chatSession = getChatSession(sessionID);
        if (chatSession != null) {
            final StringBuilder builder = new StringBuilder();
            String transcript = chatSession.getTranscript();

            if (ModelUtil.hasLength(body)) {
                builder.append(body);
            }

            builder.append("<br/>");
            builder.append("<table>").append(transcript).append("</table>");

            EmailService emailService = EmailService.getInstance();
            emailService.sendMessage(null, to, null, from, subject, null, builder.toString());
        }
    }

    /**
     * Sends a transcript mapped by it's sessionID using the transcript settings.
     *
     * @param sessionID the sessionID of the Chat.
     * @param to the email address to send the transcript to.
     */
    public static void sendTranscriptByMail(String sessionID, String to) {
        final ChatSession chatSession = getChatSession(sessionID);
        final WorkgroupManager workgroupManager = WorkgroupManager.getInstance();
        Workgroup workgroup = null;
        for (Workgroup wgroup : workgroupManager.getWorkgroups()) {
            if (wgroup.getID() == chatSession.getWorkgroupID()) {
                workgroup = wgroup;
                break;
            }
        }

        // If for some reason, the workgroup could not be found.
        if (workgroup == null) {
            return;
        }


        DbProperties props = workgroup.getProperties();
        String context = "jive.transcript";
        String from = props.getProperty(context + ".from");
        if (from == null) {
            from = workgroup.getJID().toBareJID();
        }

        String fromEmail = props.getProperty(context + ".fromEmail");
        if (fromEmail == null) {
            fromEmail = workgroup.getJID().toBareJID();
        }

        String subject = props.getProperty(context + ".subject");
        if (subject == null) {
            subject = "Chat transcript";
        }

        String message = props.getProperty(context + ".message");
        if (message == null) {
            message = "";
        }


        if (chatSession != null) {
            final StringBuilder builder = new StringBuilder();
            String transcript = chatSession.getTranscript();

            if (ModelUtil.hasLength(message)) {
                builder.append(message);
            }

            builder.append("<br/>");
            builder.append("<table>").append(transcript).append("</table>");

            EmailService emailService = EmailService.getInstance();
            emailService.sendMessage(null, to, from, fromEmail, subject, null, builder.toString());
        }
    }

    /**
     * Sorts ChatSessions by date.
     */
    static final Comparator<ChatSession> dateComparator = new Comparator<ChatSession>() {
        public int compare(ChatSession item1, ChatSession item2) {
            float int1 = item1.getStartTime();
            float int2 = item2.getStartTime();

            if (int1 == int2) {
                return 0;
            }

            if (int1 > int2) {
                return -1;
            }

            if (int1 < int2) {
                return 1;
            }

            return 0;
        }
    };
}