/*
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

import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.WorkgroupProvider;
import org.jivesoftware.xmpp.workgroup.disco.DiscoFeaturesProvider;
import org.jivesoftware.xmpp.workgroup.utils.ModelUtil;
import org.jivesoftware.openfire.fastpath.history.ChatTranscriptManager;
import org.dom4j.Element;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;
import org.jivesoftware.util.EmailService;
import org.jivesoftware.util.JiveGlobals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Allows agents and web client w/authentication to send email.
 */
public class EmailProvider implements WorkgroupProvider, DiscoFeaturesProvider {

    public boolean handleGet(IQ packet) {
        return false;
    }

    public boolean handleSet(IQ packet) {
        Element iq = packet.getChildElement();
        String name = iq.getName();

        return "send-email".equals(name);
    }

    public void executeGet(IQ packet, Workgroup workgroup) {
        // Nothing.
    }

    public void executeSet(IQ packet, Workgroup workgroup) {
        IQ reply = IQ.createResultIQ(packet);
        Element iq = packet.getChildElement();

        String from = iq.element("fromAddress").getTextTrim();
        String to = iq.element("toAddress").getTextTrim();

        String subject = iq.element("subject").getTextTrim();
        String body = iq.element("message").getTextTrim();

        // Need to replace the \\n for \n to allow for text sending.
        body = body.replace("\\n", "\n");

        String html = iq.element("useHTML").getTextTrim();
        boolean useHTML = false;
        if ("true".equals(html)) {
            useHTML = true;
        }

        String sessionID = null;
        if(iq.element("sessionID") != null){
            sessionID = iq.element("sessionID").getTextTrim();
        }

        // Handle missing information.
        if (!ModelUtil.hasLength(from) || !ModelUtil.hasLength(to) ||
                !ModelUtil.hasLength(subject) || (!ModelUtil.hasLength(body) && !ModelUtil.hasLength(sessionID))) {
            reply = IQ.createResultIQ(packet);
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(new PacketError(PacketError.Condition.not_acceptable));
            workgroup.send(reply);
            return;
        }


        if (ModelUtil.hasLength(sessionID)) {
            ChatTranscriptManager.sendTranscriptByMail(sessionID, to);
        }
        else {
            EmailService emailService = EmailService.getInstance();
            if (!useHTML) {
                emailService.sendMessage(null, to, null, from, subject, body, null);
            }
            else {
                emailService.sendMessage(null, to, null, from, subject, null, body);
            }
        }
        workgroup.send(reply);
    }

    public Collection<String> getFeatures() {
        String property = JiveGlobals.getProperty("mail.configured");
        if (!ModelUtil.hasLength(property)) {
            return Collections.emptyList();
        }

        List<String> list = new ArrayList<String>();
        list.add("jive:email:provider");
        return list;
    }

}
