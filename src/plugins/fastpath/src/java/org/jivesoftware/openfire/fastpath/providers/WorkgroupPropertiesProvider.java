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

import org.dom4j.Attribute;
import org.dom4j.Element;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.Log;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.WorkgroupProvider;
import org.jivesoftware.xmpp.workgroup.utils.ModelUtil;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

public class WorkgroupPropertiesProvider implements WorkgroupProvider {

    public boolean handleGet(IQ packet) {
        Element iq = packet.getChildElement();
        String name = iq.getName();

        return "workgroup-properties".equals(name);
    }

    public boolean handleSet(IQ packet) {
        return false;
    }

    public void executeGet(IQ packet, Workgroup workgroup) {
        IQ reply = IQ.createResultIQ(packet);

        // Retrieve the sound settings.
        String authRequired = workgroup.getProperties().getProperty("authRequired");

        Element returnPacket = reply.setChildElement("workgroup-properties",
                "http://jivesoftware.com/protocol/workgroup");
        if (ModelUtil.hasLength(authRequired)) {
            returnPacket.addElement("authRequired").setText(authRequired);
        }
        else {
            returnPacket.addElement("authRequired").setText("false");
        }

        Element iq = packet.getChildElement();
        Attribute attr = iq.attribute("jid");
        if (attr != null && ModelUtil.hasLength(iq.attribute("jid").getText())) {
            String jid = iq.attribute("jid").getText();
            UserManager userManager = UserManager.getInstance();
            try {
                User user = userManager.getUser(new JID(jid).getNode());
                String email = user.getEmail();
                String fullName = user.getName();
                returnPacket.addElement("email").setText(email);
                returnPacket.addElement("name").setText(fullName);
            }
            catch (UserNotFoundException e) {
                Log.error(e);
            }

        }


        workgroup.send(reply);
    }

    public void executeSet(IQ packet, Workgroup workgroup) {

    }
}
