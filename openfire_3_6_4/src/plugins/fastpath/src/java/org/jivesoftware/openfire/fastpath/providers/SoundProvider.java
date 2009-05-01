/**
 * $RCSfile: ,v $
 * $Revision: $
 * $Date:  $
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.fastpath.providers;

import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.WorkgroupProvider;
import org.jivesoftware.xmpp.workgroup.utils.ModelUtil;
import org.dom4j.Element;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;

public class SoundProvider implements WorkgroupProvider {

    public boolean handleGet(IQ packet) {
        Element iq = packet.getChildElement();
        String name = iq.getName();

        return "sound-settings".equals(name);
    }

    public void executeGet(IQ packet, Workgroup workgroup) {
        IQ reply = IQ.createResultIQ(packet);

        // Retrieve the sound settings.
        String outgoingMessage = workgroup.getProperties().getProperty("outgoingSound");
        String incomingMessage = workgroup.getProperties().getProperty("incomingSound");

        Element soundSetting = reply.setChildElement("sound-settings", "http://jivesoftware.com/protocol/workgroup");
        if (ModelUtil.hasLength(outgoingMessage) && ModelUtil.hasLength(incomingMessage)) {
            soundSetting.addElement("outgoingSound").setText(outgoingMessage);
            soundSetting.addElement("incomingSound").setText(incomingMessage);
        }
        else {
            // Throw error
            reply = IQ.createResultIQ(packet);
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(new PacketError(PacketError.Condition.item_not_found));
        }


        workgroup.send(reply);
    }

    public void executeSet(IQ packet, Workgroup workgroup) {

    }

    public boolean handleSet(IQ packet) {
        return false;
    }


}
