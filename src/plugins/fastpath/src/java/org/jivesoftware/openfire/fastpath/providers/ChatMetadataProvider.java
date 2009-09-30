/**
 * $RCSfile$
 * $Revision: 19357 $
 * $Date: 2005-07-21 09:40:33 -0700 (Thu, 21 Jul 2005) $
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
import java.util.HashMap;
import java.util.Map;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.Log;
import org.jivesoftware.xmpp.workgroup.AgentNotFoundException;
import org.jivesoftware.xmpp.workgroup.AgentSession;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.WorkgroupProvider;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;

public class ChatMetadataProvider implements WorkgroupProvider {

    private static final String GET_SESSION_METADATA =
            "SELECT metadataName, metadataValue FROM fpSessionMetadata WHERE sessionID=?";

    public boolean handleGet(IQ packet) {
        Element iq = packet.getChildElement();
        String name = iq.getName();

        return "chat-metadata".equals(name);
    }

    public boolean handleSet(IQ packet) {
        return false;
    }

    public void executeGet(IQ packet, Workgroup workgroup) {
        IQ reply = IQ.createResultIQ(packet);
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

        Element chatSessions = reply.setChildElement("chat-metadata", "http://jivesoftware.com/protocol/workgroup");


        Element iq = packet.getChildElement();
        String sessionID = iq.element("sessionID").getTextTrim();

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Map<String, String> map = new HashMap<String, String>();
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GET_SESSION_METADATA);
            pstmt.setString(1, sessionID);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                String name = rs.getString(1);
                String value = rs.getString(2);
                map.put(name, value);
            }
        }
        catch (Exception ex) {
            Log.error(ex);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        // Add metadata
        chatSessions.add(getMetaDataElement(map));

        workgroup.send(reply);
    }

    public Element getMetaDataElement(Map<String, String> metaData) {
        QName qName = DocumentHelper.createQName("metadata", DocumentHelper.createNamespace("",
            "http://jivesoftware.com/protocol/workgroup"));
        Element metaDataElement = DocumentHelper.createElement(qName);

        for (Map.Entry<String, String> entry : metaData.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            Element elem = metaDataElement.addElement("value");
            elem.addAttribute("name", name).setText(value);
        }
        return metaDataElement;
    }

    public void executeSet(IQ packet, Workgroup workgroup) {

    }
}
