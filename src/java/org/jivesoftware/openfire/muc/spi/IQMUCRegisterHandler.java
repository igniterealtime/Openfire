/**
 * $RCSfile$
 * $Revision: 1623 $
 * $Date: 2005-07-12 18:40:57 -0300 (Tue, 12 Jul 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.muc.spi;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.muc.ConflictException;
import org.jivesoftware.openfire.muc.ForbiddenException;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.util.ElementUtil;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.Presence;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * This class is responsible for handling packets with namespace jabber:iq:register that were
 * sent to the MUC service.  MultiUserChatServer will receive all the IQ packets and if the
 * namespace of the IQ is jabber:iq:register then this class will handle the packet.
 * 
 * @author Gaston Dombiak
 */
class IQMUCRegisterHandler {

    private static Element probeResult;
    private MultiUserChatService mucService;

    public IQMUCRegisterHandler(MultiUserChatService mucService) {
        this.mucService = mucService;
        initialize();
    }

    public void initialize() {
        if (probeResult == null) {
            // Create the registration form of the room which contains information
            // such as: first name, last name and  nickname.
            final DataForm registrationForm = new DataForm(DataForm.Type.form);
            registrationForm.setTitle(LocaleUtils.getLocalizedString("muc.form.reg.title"));
            registrationForm.addInstruction(LocaleUtils
                    .getLocalizedString("muc.form.reg.instruction"));

            final FormField fieldForm = registrationForm.addField();
            fieldForm.setVariable("FORM_TYPE");
            fieldForm.setType(FormField.Type.hidden);
            fieldForm.addValue("http://jabber.org/protocol/muc#register");

            final FormField fieldReg = registrationForm.addField();
            fieldReg.setVariable("muc#register_first");
            fieldReg.setType(FormField.Type.text_single);
            fieldReg.setLabel(LocaleUtils.getLocalizedString("muc.form.reg.first-name"));
            fieldReg.setRequired(true);

            final FormField fieldLast = registrationForm.addField();
            fieldLast.setVariable("muc#register_last");
            fieldLast.setType(FormField.Type.text_single);
            fieldLast.setLabel(LocaleUtils.getLocalizedString("muc.form.reg.last-name"));
            fieldLast.setRequired(true);

            final FormField fieldNick = registrationForm.addField();
            fieldNick.setVariable("muc#register_roomnick");
            fieldNick.setType(FormField.Type.text_single);
            fieldNick.setLabel(LocaleUtils.getLocalizedString("muc.form.reg.nickname"));
            fieldNick.setRequired(true);

            final FormField fieldUrl = registrationForm.addField();
            fieldUrl.setVariable("muc#register_url");
            fieldUrl.setType(FormField.Type.text_single);
            fieldUrl.setLabel(LocaleUtils.getLocalizedString("muc.form.reg.url"));

            final FormField fieldMail = registrationForm.addField();
            fieldMail.setVariable("muc#register_email");
            fieldMail.setType(FormField.Type.text_single);
            fieldMail.setLabel(LocaleUtils.getLocalizedString("muc.form.reg.email"));

            final FormField fieldFaq = registrationForm.addField();
            fieldFaq.setVariable("muc#register_faqentry");
            fieldFaq.setType(FormField.Type.text_single);
            fieldFaq.setLabel(LocaleUtils.getLocalizedString("muc.form.reg.faqentry"));

            // Create the probeResult and add the registration form
            probeResult = DocumentHelper.createElement(QName.get("query", "jabber:iq:register"));
            probeResult.add(registrationForm.getElement());
        }
    }

    public IQ handleIQ(IQ packet) {
        IQ reply = null;
        // Get the target room
        MUCRoom room = null;
        String name = packet.getTo().getNode();
        if (name != null) {
            room = mucService.getChatRoom(name);
        }
        if (room == null) {
            // The room doesn't exist so answer a NOT_FOUND error
            reply = IQ.createResultIQ(packet);
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(PacketError.Condition.item_not_found);
            return reply;
        }
        else if (!room.isRegistrationEnabled()) {
            // The room does not accept users to register
            reply = IQ.createResultIQ(packet);
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(PacketError.Condition.not_allowed);
            return reply;
        }

        if (IQ.Type.get == packet.getType()) {
            reply = IQ.createResultIQ(packet);
            String nickname = room.getReservedNickname(packet.getFrom().toBareJID());
            Element currentRegistration = probeResult.createCopy();
            if (nickname != null) {
                // The user is already registered with the room so answer a completed form
                ElementUtil.setProperty(currentRegistration, "query.registered", null);
                Element form = currentRegistration.element(QName.get("x", "jabber:x:data"));
                Iterator fields = form.elementIterator("field");
                Element field;
                while (fields.hasNext()) {
                    field = (Element) fields.next();
                    if ("muc#register_roomnick".equals(field.attributeValue("var"))) {
                        field.addElement("value").addText(nickname);
                    }
                }
                reply.setChildElement(currentRegistration);
            }
            else {
                // The user is not registered with the room so answer an empty form
                reply.setChildElement(currentRegistration);
            }
        }
        else if (IQ.Type.set ==  packet.getType()) {
            try {
                // Keep a registry of the updated presences
                List<Presence> presences = new ArrayList<Presence>();

                reply = IQ.createResultIQ(packet);
                Element iq = packet.getChildElement();

                if (ElementUtil.includesProperty(iq, "query.remove")) {
                    // The user is deleting his registration
                    presences.addAll(room.addNone(packet.getFrom().toBareJID(), room.getRole()));
                }
                else {
                    // The user is trying to register with a room
                    Element formElement = iq.element("x");
                    // Check if a form was used to provide the registration info
                    if (formElement != null) {
                        // Get the sent form
                        final DataForm registrationForm = new DataForm(formElement);
                        // Get the desired nickname sent in the form
                        List<String> values = registrationForm.getField("muc#register_roomnick")
                                .getValues();
                        String nickname = (!values.isEmpty() ? values.get(0) : null);

                        // TODO The rest of the fields of the form are ignored. If we have a
                        // requirement in the future where we need those fields we'll have to change
                        // MUCRoom.addMember in order to receive a RegistrationInfo (new class)

                        // Add the new member to the members list
                        presences.addAll(room.addMember(packet.getFrom().toBareJID(),
                                nickname,
                                room.getRole()));
                    }
                    else {
                        reply.setChildElement(packet.getChildElement().createCopy());
                        reply.setError(PacketError.Condition.bad_request);
                    }
                }
                // Send the updated presences to the room occupants
                for (Presence presence : presences) {
                    room.send(presence);
                }

            }
            catch (ForbiddenException e) {
                reply = IQ.createResultIQ(packet);
                reply.setChildElement(packet.getChildElement().createCopy());
                reply.setError(PacketError.Condition.forbidden);
            }
            catch (ConflictException e) {
                reply = IQ.createResultIQ(packet);
                reply.setChildElement(packet.getChildElement().createCopy());
                reply.setError(PacketError.Condition.conflict);
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
        return reply;
    }
}
