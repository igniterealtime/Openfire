/**
 * $RCSfile$
 * $Revision: 19047 $
 * $Date: 2005-06-13 17:30:31 -0700 (Mon, 13 Jun 2005) $
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */
package org.jivesoftware.openfire.fastpath.settings.offline;

import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.WorkgroupProvider;
import org.jivesoftware.xmpp.workgroup.utils.ModelUtil;
import org.dom4j.Element;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;

public class OfflineSettingsProvider implements WorkgroupProvider {

    public boolean handleGet(IQ packet) {
        Element iq = packet.getChildElement();
        String name = iq.getName();

        return "offline-settings".equals(name);
    }

    public boolean handleSet(IQ packet) {
        return false;
    }

    public void executeGet(IQ packet, Workgroup workgroup) {
        IQ reply = IQ.createResultIQ(packet);

        OfflineSettingsManager offlineSettingsManager = new OfflineSettingsManager();
        OfflineSettings settings;
        try {
            settings = offlineSettingsManager.getOfflineSettings(workgroup);
        }
        catch (OfflineSettingsNotFound offlineSettingsNotFound) {
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(new PacketError(PacketError.Condition.item_not_found));
            workgroup.send(reply);
            return;
        }

        Element offline = reply.setChildElement("offline-settings", "http://jivesoftware.com/protocol/workgroup");
        if (ModelUtil.hasLength(settings.getRedirectURL())) {
            offline.addElement("redirectPage").setText(settings.getRedirectURL());
        }
        else {
            offline.addElement("emailAddress").setText(settings.getEmailAddress());
            offline.addElement("offlineText").setText(settings.getOfflineText());
            offline.addElement("subject").setText(settings.getSubject());
        }
        workgroup.send(reply);
    }

    public void executeSet(IQ packet, Workgroup workgroup) {

    }
}
