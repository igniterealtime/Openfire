/*
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

import java.util.ArrayList;
import java.util.List;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.muc.ConflictException;
import org.jivesoftware.openfire.muc.ForbiddenException;
import org.jivesoftware.openfire.muc.MUCRole;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.util.ElementUtil;
import org.jivesoftware.util.LocaleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.Presence;

/**
 * This class is responsible for handling packets with namespace jabber:iq:register that were
 * sent to the MUC service.  MultiUserChatServer will receive all the IQ packets and if the
 * namespace of the IQ is jabber:iq:register then this class will handle the packet.
 * 
 * @author Gaston Dombiak
 */
class IQMUCRegisterHandler {

    private static final Logger Log = LoggerFactory.getLogger(IQMUCRegisterHandler.class);

    private static final Element probeResult;
    
    static {
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
    
    private final MultiUserChatService mucService;

    public IQMUCRegisterHandler(MultiUserChatService mucService) {
        this.mucService = mucService;
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
        else if (!room.isRegistrationEnabled() ||
                 (packet.getFrom() != null && 
                  MUCRole.Affiliation.outcast == room.getAffiliation(packet.getFrom().asBareJID()))) {
            // The room does not accept users to register or
            // the user is an outcast and is not allowed to register
            reply = IQ.createResultIQ(packet);
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(PacketError.Condition.not_allowed);
            return reply;
        }

        if (IQ.Type.get == packet.getType()) {
            reply = IQ.createResultIQ(packet);
            String nickname = room.getReservedNickname(packet.getFrom());
            Element currentRegistration = probeResult.createCopy();
            if (nickname != null) {
                // The user is already registered with the room so answer a completed form
                ElementUtil.setProperty(currentRegistration, "query.registered", null);
                currentRegistration.addElement("username").addText(nickname);

                Element form = currentRegistration.element(QName.get("x", "jabber:x:data"));
                currentRegistration.remove(form);
//                @SuppressWarnings("unchecked")
//				Iterator<Element> fields = form.elementIterator("field");
//
//                Element field;
//                while (fields.hasNext()) {
//                    field = fields.next();
//                    if ("muc#register_roomnick".equals(field.attributeValue("var"))) {
//                        field.addElement("value").addText(nickname);
//                    }
//                }
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
                List<Presence> presences = new ArrayList<>();

                reply = IQ.createResultIQ(packet);
                Element iq = packet.getChildElement();

                if (ElementUtil.includesProperty(iq, "query.remove")) {
                    // The user is deleting his registration
                    presences.addAll(room.addNone(packet.getFrom(), room.getRole()));
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
                        presences.addAll(room.addMember(packet.getFrom(),
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
                Log.error(e.getMessage(), e);
            }
        }
        return reply;
    }
}
