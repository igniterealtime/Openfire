/**
 * $RCSfile: ,v $
 * $Revision: 1.0 $
 * $Date: 2005/05/25 04:20:03 $
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.fastpath.providers;

import org.jivesoftware.xmpp.workgroup.AgentSession;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.WorkgroupProvider;
import org.dom4j.Element;
import org.xmpp.packet.IQ;

public class TrackingProvider implements WorkgroupProvider {

    public boolean handleGet(IQ packet) {
        return false;
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

        return "tracker".equals(name);
    }

    public void executeGet(IQ packet, Workgroup workgroup) {

    }

    public void executeSet(IQ packet, Workgroup workgroup) {
        IQ reply = null;
        Element iq = packet.getChildElement();

        // Send back reply
        reply = IQ.createResultIQ(packet);
        workgroup.send(reply);

        IQ update = new IQ();
        Element elem = update.setChildElement("tracker", "http://jivesoftware.com/protocol/workgroup");

        // Check if user is leaving.
        Element leaving = iq.element("leaving");
        if (leaving != null) {
            elem.addElement("leaving").setText("true");
            for (AgentSession session : workgroup.getAgentSessions()) {
                update.setTo(session.getJID());
                update.setType(IQ.Type.set);
                workgroup.send(update);
            }
            return;
        }

        String url = iq.element("url").getTextTrim();
        String title = iq.element("title").getTextTrim();
        String referrer = iq.element("referrer").getTextTrim();
        String uniqueID = iq.element("uniqueID").getTextTrim();
        String ipAddress = iq.element("ipAddress").getTextTrim();

        // Otherwise, notify of new user on site.
        elem.addElement("url").setText(url);
        elem.addElement("title").setText(title);
        elem.addElement("referrer").setText(referrer);
        elem.addElement("uniqueID").setText(uniqueID);
        elem.addElement("ipAddress").setText(ipAddress);

        for (AgentSession session : workgroup.getAgentSessions()) {
            update.setTo(session.getJID());
            update.setType(IQ.Type.set);
            workgroup.send(update);
        }

    }

}
