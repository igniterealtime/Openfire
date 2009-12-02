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

import org.dom4j.Attribute;
import org.dom4j.Element;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.WorkgroupProvider;
import org.jivesoftware.xmpp.workgroup.utils.ModelUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

public class WorkgroupPropertiesProvider implements WorkgroupProvider {

	private static final Logger Log = LoggerFactory.getLogger(WorkgroupPropertiesProvider.class);
	
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
                Log.error(e.getMessage(), e);
            }

        }


        workgroup.send(reply);
    }

    public void executeSet(IQ packet, Workgroup workgroup) {

    }
}
