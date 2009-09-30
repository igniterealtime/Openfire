/**
 * $RCSfile$
 * $Revision: 19253 $
 * $Date: 2005-07-07 17:20:43 -0700 (Thu, 07 Jul 2005) $
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
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.WorkgroupManager;
import org.jivesoftware.xmpp.workgroup.WorkgroupProvider;
import org.dom4j.Element;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;

public class SearchProvider implements WorkgroupProvider {

    public boolean handleGet(IQ packet) {
        Element iq = packet.getChildElement();
        String name = iq.getName();

        return "search-settings".equals(name);
    }

    public boolean handleSet(IQ packet) {
        return false;
    }

    public void executeGet(IQ packet, Workgroup workgroup) {
        IQ reply = IQ.createResultIQ(packet);


        // Retrieve the web chat setting.
        String kbURL = workgroup.getProperties().getProperty("kb");
        String forumURL = workgroup.getProperties().getProperty("forums");

        // Check that the sender of this IQ is an agent
        WorkgroupManager workgroupManager = WorkgroupManager.getInstance();
        try {
            workgroupManager.getAgentManager().getAgent(packet.getFrom());
        }
        catch (AgentNotFoundException e) {
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(new PacketError(PacketError.Condition.item_not_found));
            workgroup.send(reply);
            return;
        }

        Element searchSetting = reply.setChildElement("search-settings", "http://jivesoftware.com/protocol/workgroup");
        if (forumURL != null) {
            searchSetting.addElement("forums").setText(forumURL);
        }

        if (kbURL != null) {
            searchSetting.addElement("kb").setText(kbURL);
        }
        workgroup.send(reply);
    }

    public void executeSet(IQ packet, Workgroup workgroup) {

    }
}
