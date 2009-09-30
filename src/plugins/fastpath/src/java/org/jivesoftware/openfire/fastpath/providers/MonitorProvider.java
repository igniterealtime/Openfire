/**
 * $RCSfile: ,v $
 * $Revision: $
 * $Date:  $
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

import org.jivesoftware.xmpp.workgroup.DbProperties;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.WorkgroupManager;
import org.jivesoftware.xmpp.workgroup.WorkgroupProvider;
import org.dom4j.Element;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;

import java.util.StringTokenizer;

public class MonitorProvider implements WorkgroupProvider {

    public boolean handleGet(IQ packet) {
        Element iq = packet.getChildElement();
        String name = iq.getName();

        return "monitor".equals(name);
    }

    public boolean handleSet(IQ packet) {
        Element iq = packet.getChildElement();
        String name = iq.getName();

        return "monitor".equals(name);
    }

    public void executeGet(IQ packet, Workgroup workgroup) {
        IQ reply = IQ.createResultIQ(packet);

        JID from = packet.getFrom();
        String bareJID = from.toBareJID();

        boolean isMonitor = false;

        // Retrieve the sound settings.
        String monitors = workgroup.getProperties().getProperty("monitors");
        if (monitors != null) {
            StringTokenizer tkn = new StringTokenizer(monitors, ",");
            while (tkn.hasMoreTokens()) {
                String agent = tkn.nextToken();
                if (agent.equalsIgnoreCase(bareJID)) {
                    isMonitor = true;
                }
            }
        }

        Element monitorElement = reply.setChildElement("monitor", "http://jivesoftware.com/protocol/workgroup");

        if (!isMonitor) {
            monitorElement.addElement("isMonitor").setText("false");
        }
        else {
            monitorElement.addElement("isMonitor").setText("true");
        }


        workgroup.send(reply);
    }

    public void executeSet(IQ packet, Workgroup workgroup) {
        IQ reply = null;
        Element iq = packet.getChildElement();

        try {
            JID from = packet.getFrom();
            String bareJID = from.toBareJID();
            if (!isOwner(bareJID, workgroup)) {
                reply = IQ.createResultIQ(packet);
                reply.setChildElement(packet.getChildElement().createCopy());
                reply.setError(new PacketError(PacketError.Condition.forbidden));
                workgroup.send(reply);
                return;
            }

            // Verify that an agent is requesting this information.
            WorkgroupManager workgroupManager = WorkgroupManager.getInstance();
            if (iq.element("makeOwner") != null) {
                String sessionID = iq.element("makeOwner").attributeValue("sessionID");
                final String serviceName = workgroupManager.getMUCServiceName();
                final String roomName = sessionID + "@" + serviceName;
                final String roomJID = roomName + "/" + workgroup.getJID().getNode();

                IQ iqPacket = new IQ(IQ.Type.set);
                iqPacket.setTo(roomName);
                iqPacket.setFrom(workgroup.getFullJID());

                Element query = iqPacket.setChildElement("query", "http://jabber.org/protocol/muc#owner");
                Element item = query.addElement("item");
                item.addAttribute("affiliation", "owner");
                item.addAttribute("jid", packet.getFrom().toBareJID());
                workgroup.send(iqPacket);
            }


            reply = IQ.createResultIQ(packet);
        }
        catch (Exception e) {
            reply = IQ.createResultIQ(packet);
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(new PacketError(PacketError.Condition.item_not_found));
        }
        workgroup.send(reply);
    }

    private boolean isOwner(String jid, Workgroup workgroup) {
        DbProperties props = workgroup.getProperties();
        // Add monitors if you wish :)
        String monitors = props.getProperty("monitors");
        if (monitors != null) {
            StringTokenizer tkn = new StringTokenizer(monitors, ",");
            while (tkn.hasMoreTokens()) {
                String monitor = tkn.nextToken();
                if (monitor.equalsIgnoreCase(jid)) {
                    return true;
                }
            }
        }
        return false;
    }
}

