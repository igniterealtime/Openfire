/**
 * $RCSfile$
 * $Revision: 19253 $
 * $Date: 2005-07-07 17:20:43 -0700 (Thu, 07 Jul 2005) $
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
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
