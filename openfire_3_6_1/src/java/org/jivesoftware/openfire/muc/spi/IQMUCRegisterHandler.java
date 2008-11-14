/**
 * $RCSfile$
 * $Revision: 1623 $
 * $Date: 2005-07-12 18:40:57 -0300 (Tue, 12 Jul 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.muc.spi;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.forms.DataForm;
import org.jivesoftware.openfire.forms.FormField;
import org.jivesoftware.openfire.forms.spi.XDataFormImpl;
import org.jivesoftware.openfire.forms.spi.XFormFieldImpl;
import org.jivesoftware.openfire.muc.ConflictException;
import org.jivesoftware.openfire.muc.ForbiddenException;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.util.ElementUtil;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
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
            XDataFormImpl registrationForm = new XDataFormImpl(DataForm.TYPE_FORM);
            registrationForm.setTitle(LocaleUtils.getLocalizedString("muc.form.reg.title"));
            registrationForm.addInstruction(LocaleUtils
                    .getLocalizedString("muc.form.reg.instruction"));

            XFormFieldImpl field = new XFormFieldImpl("FORM_TYPE");
            field.setType(FormField.TYPE_HIDDEN);
            field.addValue("http://jabber.org/protocol/muc#register");
            registrationForm.addField(field);

            field = new XFormFieldImpl("muc#register_first");
            field.setType(FormField.TYPE_TEXT_SINGLE);
            field.setLabel(LocaleUtils.getLocalizedString("muc.form.reg.first-name"));
            field.setRequired(true);
            registrationForm.addField(field);

            field = new XFormFieldImpl("muc#register_last");
            field.setType(FormField.TYPE_TEXT_SINGLE);
            field.setLabel(LocaleUtils.getLocalizedString("muc.form.reg.last-name"));
            field.setRequired(true);
            registrationForm.addField(field);

            field = new XFormFieldImpl("muc#register_roomnick");
            field.setType(FormField.TYPE_TEXT_SINGLE);
            field.setLabel(LocaleUtils.getLocalizedString("muc.form.reg.nickname"));
            field.setRequired(true);
            registrationForm.addField(field);

            field = new XFormFieldImpl("muc#register_url");
            field.setType(FormField.TYPE_TEXT_SINGLE);
            field.setLabel(LocaleUtils.getLocalizedString("muc.form.reg.url"));
            registrationForm.addField(field);

            field = new XFormFieldImpl("muc#register_email");
            field.setType(FormField.TYPE_TEXT_SINGLE);
            field.setLabel(LocaleUtils.getLocalizedString("muc.form.reg.email"));
            registrationForm.addField(field);

            field = new XFormFieldImpl("muc#register_faqentry");
            field.setType(FormField.TYPE_TEXT_MULTI);
            field.setLabel(LocaleUtils.getLocalizedString("muc.form.reg.faqentry"));
            registrationForm.addField(field);

            // Create the probeResult and add the registration form
            probeResult = DocumentHelper.createElement(QName.get("query", "jabber:iq:register"));
            probeResult.add(registrationForm.asXMLElement());
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
                        XDataFormImpl registrationForm = new XDataFormImpl();
                        registrationForm.parse(formElement);
                        // Get the desired nickname sent in the form
                        Iterator<String> values = registrationForm.getField("muc#register_roomnick")
                                .getValues();
                        String nickname = (values.hasNext() ? values.next() : null);

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
