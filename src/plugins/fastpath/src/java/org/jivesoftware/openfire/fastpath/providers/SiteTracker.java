/**
 * $RCSfile: ,v $
 * $Revision: 1.0 $
 * $Date: 2005/05/25 04:20:03 $
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

import org.jivesoftware.xmpp.workgroup.AgentNotFoundException;
import org.jivesoftware.xmpp.workgroup.AgentSession;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.WorkgroupProvider;
import org.dom4j.Element;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;

import java.util.*;

public class SiteTracker implements WorkgroupProvider {
    private Map<String, SiteUser> siteUsers = new HashMap<String, SiteUser>();

    public boolean handleGet(IQ packet) {
        Element iq = packet.getChildElement();
        String name = iq.getName();

        return "site-user-history".equals(name);
    }

    /**
     * Check to see if the packet is a tracker packet.
     *
     * @param packet the packet to check.
     * @return true if we should handle this packet.
     */
    public boolean handleSet(IQ packet) {
        Element iq = packet.getChildElement();
        String name = iq.getName();

        if ("site-user".equals(name)) {
            return true;
        }
        else if ("site-invite".equals(name)) {
            return true;
        }
        return false;
    }

    public void executeGet(IQ packet, Workgroup workgroup) {
        Element iq = packet.getChildElement();
        String name = iq.getName();

        if ("site-user-history".equals(name)) {
            handleUserHistoryRequest(packet, workgroup);
        }
    }

    private void handleUserHistoryRequest(IQ packet, Workgroup workgroup) {
        IQ reply;
        Element iq = packet.getChildElement();

        try {
            AgentSession agentSession = workgroup.getAgentManager().getAgentSession(packet.getFrom());
            if (agentSession == null) {
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

        // Define default values
        String sessionID = iq.attribute("sessionID").getText();

        reply = IQ.createResultIQ(packet);

        Element views = reply.setChildElement("site-user-history", "http://jivesoftware.com/protocol/workgroup");
        views.addAttribute("sessionID", sessionID);

        SiteUser siteUser = siteUsers.get(sessionID);
        if (siteUser != null) {
            for (PageView view : siteUser.getViews()) {
                Element pageView = views.addElement("page-view");
                pageView.addElement("title").setText(view.getTitle());
                pageView.addElement("url").setText(view.getUrl());
                pageView.addElement("time").setText(Long.toString(view.getTimeViewed()));
            }

            workgroup.send(reply);
        }

    }

    public void executeSet(IQ packet, Workgroup workgroup) {
        Element iq = packet.getChildElement();
        String name = iq.getName();


        if ("site-user".equals(name)) {
            handleSiteUser(packet, workgroup);
        }
        else if ("site-invite".equals(name)) {
            handleInvitation(packet, workgroup);
        }

    }

    private void handleSiteUser(IQ packet, Workgroup workgroup) {
        IQ reply;
        Element iq = packet.getChildElement();

        // Define default values
        IQ update = new IQ();
        Element elem = update.setChildElement("site-user", "http://jivesoftware.com/protocol/workgroup");
        String sessionID = iq.attribute("sessionID").getText();
        elem.addAttribute("sessionID", sessionID);

        // Check if a users session has expired
        Element sessionExpired = iq.element("expired");
        if (sessionExpired != null) {
            siteUsers.remove(sessionID);
            elem.addElement("sessionExpired").setText("true");
            for (AgentSession session : workgroup.getAgentSessions()) {
                update.setTo(session.getJID());
                update.setType(IQ.Type.set);
                workgroup.send(update);
            }
            reply = IQ.createResultIQ(packet);
            workgroup.send(reply);
            return;
        }

        Element chatting = iq.element("chatting");
        if (chatting != null) {
            elem.addElement("chatting").setText("true");
            for (AgentSession session : workgroup.getAgentSessions()) {
                update.setTo(session.getJID());
                update.setType(IQ.Type.set);
                workgroup.send(update);
            }
            reply = IQ.createResultIQ(packet);
            workgroup.send(reply);
            return;
        }

        // Check if user left a page
        Element leaving = iq.element("leftPage");
        if (leaving != null) {
            elem.addElement("leftPage").setText("true");
            for (AgentSession session : workgroup.getAgentSessions()) {
                update.setTo(session.getJID());
                update.setType(IQ.Type.set);
                workgroup.send(update);
            }
            reply = IQ.createResultIQ(packet);
            workgroup.send(reply);

            // Handle pageView
            SiteUser siteUser = siteUsers.get(sessionID);
            if (siteUser != null) {
                PageView lastView = siteUser.getLastView();
                if (lastView != null) {
                    lastView.setEndTime(new Date());
                }
            }
            return;
        }

        // If user has not left a page and the session has not expired. This user
        // has entered a new page.

        String url = iq.element("url").getTextTrim();
        String title = iq.element("title").getTextTrim();
        String referrer = iq.element("referrer").getTextTrim();
        String ipAddress = iq.element("ipAddress").getTextTrim();
        String userID = iq.element("userID").getTextTrim();

        // Send back reply
        reply = IQ.createResultIQ(packet);
        workgroup.send(reply);


        elem.addElement("url").setText(url);
        elem.addElement("title").setText(title);
        elem.addElement("referrer").setText(referrer);
        elem.addElement("userID").setText(userID);
        elem.addElement("ipAddress").setText(ipAddress);

        // Add to tracking information
        SiteUser siteUser = siteUsers.get(sessionID);
        if (siteUser == null) {
            siteUser = new SiteUser();
        }
        PageView pageView = new PageView();
        pageView.setStartTime(new Date());
        pageView.setTitle(title);
        pageView.setUrl(url);

        siteUser.addView(pageView);
        siteUser.setJID(reply.getTo());

        siteUsers.put(sessionID, siteUser);


        for (AgentSession session : workgroup.getAgentSessions()) {
            update.setTo(session.getJID());
            update.setType(IQ.Type.set);
            workgroup.send(update);
        }
    }

    private void handleInvitation(IQ packet, Workgroup workgroup) {
        Element iq = packet.getChildElement();

        // Define default values
        IQ update = new IQ();
        Element elem = update.setChildElement("site-invite", "http://jivesoftware.com/protocol/workgroup");
        String sessionID = iq.attribute("sessionID").getText();
        elem.addAttribute("sessionID", sessionID);

        SiteUser siteUser = siteUsers.get(sessionID);
        
        IQ reply = IQ.createResultIQ(packet);
        if (siteUser == null) {
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(new PacketError(PacketError.Condition.item_not_found));
            workgroup.send(reply);
            return;
        }
        else {
            // Send back reply
            workgroup.send(reply);
        }

        String agent = iq.element("agent").getText();
        String message = iq.element("message").getText();

        elem.addElement("agent").setText(agent);
        elem.addElement("message").setText(message);
        update.setTo(siteUser.getJID());
        update.setType(IQ.Type.set);
        workgroup.send(update);
    }


    private class SiteUser {
        private List<PageView> views = new ArrayList<PageView>();
        private JID jid = null;

        public void addView(PageView view) {
            views.add(view);
        }

        public void setJID(JID jid) {
            this.jid = jid;
        }

        public JID getJID() {
            return jid;
        }

        public Collection<PageView> getViews() {
            return views;
        }

        public PageView getLastView() {
            if (views.size() > 0) {
                return views.get(views.size() - 1);
            }
            return null;
        }
    }

    private class PageView {
        private String url;
        private String title;
        private Date startTime;
        private Date endTime;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public long getTimeViewed() {
            if (startTime != null) {
                long start = startTime.getTime();
                long end = endTime != null ? endTime.getTime() : 0;
                if (end == 0) {
                    return new Date().getTime() - start;
                }
                else {
                    return end - start;
                }
            }
            return 0;
        }


        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public Date getStartTime() {
            return startTime;
        }

        public void setStartTime(Date startTime) {
            this.startTime = startTime;
        }

        public Date getEndTime() {
            return endTime;
        }

        public void setEndTime(Date endTime) {
            this.endTime = endTime;
        }
    }

}
