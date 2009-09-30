/**
 * $RCSfile$
 * $Revision: 19158 $
 * $Date: 2005-06-27 15:15:06 -0700 (Mon, 27 Jun 2005) $
 *
 * Copyright (C) 1999-2006 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.fastpath.events;

import org.jivesoftware.openfire.fastpath.history.AgentChatSession;
import org.jivesoftware.openfire.fastpath.history.ChatSession;
import org.jivesoftware.openfire.fastpath.history.ChatTranscriptManager;
import org.jivesoftware.xmpp.workgroup.AgentSession;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.event.WorkgroupEventDispatcher;
import org.jivesoftware.xmpp.workgroup.event.WorkgroupEventListener;
import org.jivesoftware.xmpp.workgroup.utils.ModelUtil;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.EmailService;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * EmailTranscriptEvent sends emails to specified users on end of chat events.
 *
 * @author Derek DeMoro
 */
public class EmailTranscriptEvent implements WorkgroupEventListener {

    public EmailTranscriptEvent() {
        WorkgroupEventDispatcher.addListener(this);
        Log.debug("EmailTranscriptEvent initialized.");
    }

    public void workgroupCreated(Workgroup workgroup) {
    }

    public void workgroupDeleting(Workgroup workgroup) {
    }

    public void workgroupDeleted(Workgroup workgroup) {
    }

    public void workgroupOpened(Workgroup workgroup) {
    }

    public void workgroupClosed(Workgroup workgroup) {
    }

    public void agentJoined(Workgroup workgroup, AgentSession agentSession) {
    }

    public void agentDeparted(Workgroup workgroup, AgentSession agentSession) {
    }

    public void chatSupportStarted(Workgroup workgroup, String sessionID) {
    }

    public void chatSupportFinished(Workgroup workgroup, String sessionID) {
        Log.debug("Chat Support Finished, sending transcripts");

        final EmailService emailService = EmailService.getInstance();

        String property = JiveGlobals.getProperty("mail.configured");
        if (!ModelUtil.hasLength(property)) {
            Log.debug("Mail settings are not configured, transcripts will not be sent.");
            return;
        }

        final ChatSession chatSession = ChatTranscriptManager.getChatSession(sessionID);
        if (chatSession == null || chatSession.getFirstSession() == null) {
            return;
        }

        final StringBuilder builder = new StringBuilder();

        SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyy hh:mm a");

        // Get duration of conversation
        Date date = new Date(chatSession.getFirstSession().getStartTime());
        int duration = getChatDuration(date, chatSession);

        TreeMap<String, List<String>> map = new TreeMap<String, List<String>>(chatSession.getMetadata());

        String body = JiveGlobals.getProperty("chat.transcript.body");

        if (ModelUtil.hasLength(body)) {
            builder.append(body).append("\n\n");
        }

        builder.append("formname=chat transcript\n");
        extractAndDisplay(builder, "question", map);
        display(builder, "fullname", chatSession.getCustomerName());
        extractAndDisplay(builder, "email", map);
        extractAndDisplay(builder, "Location", map);
        extractAndDisplay(builder, "userID", map);
        extractAndDisplay(builder, "username", map);
        extractAndDisplay(builder, "workgroup", map);
        display(builder, "chatduration", String.valueOf(duration));
        display(builder, "chatdate", formatter.format(date));
        if (chatSession.getFirstSession() != null && chatSession.getFirstSession().getAgentJID() != null) {
            try {
                display(builder, "agent", new JID(chatSession.getFirstSession().getAgentJID()).toBareJID());
            }
            catch (Exception e) {
                Log.debug("Could not display agent in transcript.", e);
            }
        }
        for (Iterator<Map.Entry<String, List<String>>> iterator = map.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<String, List<String>> entry = iterator.next();
            display(builder, entry.getKey(), getListItem(entry.getValue()));
        }
        builder.append("ctranscript=\n");
        builder.append(ChatTranscriptManager.getTextTranscriptFromSessionID(sessionID));

        String subject = JiveGlobals.getProperty("chat.transcript.subject");
        String from = JiveGlobals.getProperty("chat.transcript.from");
        String to = JiveGlobals.getProperty("chat.transcript.to");

        if (!ModelUtil.hasLength(subject) || !ModelUtil.hasLength(from)) {
            Log.debug("Transcript settings (chat.transcript.subject, chat.transcript.from) are not configured, " +
                    "transcripts will not be sent.");
            return;
        }

        if (ModelUtil.hasLength(to)) {
            emailService.sendMessage("Chat Transcript", to, "Chat Transcript", from, subject, builder.toString(), null);
            Log.debug("Transcript sent to " + to);
        }

        // NOTE: Do not sent to the customer. They will receive a prompt for a seperate chat transcript
        // that does not contain agent information.

        // Send to Agents
        UserManager um = UserManager.getInstance();
        for (Iterator iterator = chatSession.getAgents(); iterator.hasNext();) {
            AgentChatSession agentSession = (AgentChatSession)iterator.next();
            try {
                User user = um.getUser(new JID(agentSession.getAgentJID()).getNode());
                emailService.sendMessage("Chat Transcript", user.getEmail(), "Chat Transcript", from, subject, builder.toString(), null);
                Log.debug("Transcript sent to agent " + agentSession.getAgentJID());
            }
            catch (UserNotFoundException e) {
                Log.error("Email Transcript Not Sent:" +
                        "Could not load agent user object for jid " + agentSession.getAgentJID());
            }
        }
    }

    private void extractAndDisplay(StringBuilder builder, String var, TreeMap<String, List<String>> map) {
        List<String> list = map.remove(var);
        if (list != null) {
            String value = getListItem(list);
            if (ModelUtil.hasLength(value)) {
                display(builder, var, value);
            }
        }
    }

    private void display(StringBuilder builder, String var, String value) {
        builder.append(var).append("=").append(value).append("\n");
    }

    private String getListItem(List<String> list) {
        StringBuffer sb = new StringBuffer();
        for (Iterator<String> iterator = list.iterator(); iterator.hasNext();) {
            String s = iterator.next();
            sb.append(s);
            if (iterator.hasNext()) {
                sb.append(",");
            }
        }

        return sb.toString();
    }

    private int getChatDuration(Date start, ChatSession session) {
        long startTime = start.getTime();
        long end = startTime;
        List agents = session.getAgentList();
        for (Iterator iterator = agents.iterator(); iterator.hasNext();) {
            AgentChatSession chatSession = (AgentChatSession)iterator.next();
            if (end < chatSession.getEndTime()) {
                end = chatSession.getEndTime();
            }
        }

        return (int)((end - startTime) / 1000 / 60);
    }

    public void agentJoinedChatSupport(Workgroup workgroup, String sessionID, AgentSession agentSession) {
    }

    public void agentLeftChatSupport(Workgroup workgroup, String sessionID, AgentSession agentSession) {
    }

    public void shutdown() {
        WorkgroupEventDispatcher.removeListener(this);
    }
}
