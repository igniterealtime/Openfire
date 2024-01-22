/*
 * Copyright (C) 2004-2008 Jive Software, 2017-2024 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.commands.admin.muc;

import org.dom4j.Element;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.openfire.muc.NotAllowedException;
import org.jivesoftware.util.LocaleUtils;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.locks.Lock;

/**
 * Allows via AdHoc commands the creation of a Multi-User Chat room.
 *
 * @author Alexander Wenckus
 */
public class CreateMUCRoom extends AdHocCommand {
    @Override
    public String getCode() {
        return "http://jabber.org/protocol/admin#create-muc-room";
    }

    @Override
    public String getDefaultLabel() {
        return LocaleUtils.getLocalizedString("commands.admin.muc.createmucroom.label");
    }

    @Override
    public int getMaxStages(@Nonnull final SessionData data) {
        return 1;
    }

    @Override
    public void execute(@Nonnull SessionData sessionData, Element command) {
        final Locale preferredLocale = SessionManager.getInstance().getLocaleForSession(sessionData.getOwner());

        Element note = command.addElement("note");
        Collection<JID> admins = XMPPServer.getInstance().getAdmins();
        if (admins.size() <= 0) {
            note.addAttribute("type", "error");
            note.setText(LocaleUtils.getLocalizedString("commands.admin.muc.createmucroom.note.server-needs-admin", preferredLocale));
            return;
        }
        Map<String, List<String>> data = sessionData.getData();

        // Let's find the requested MUC service to create the room in
        String servicehostname = get(data, "servicename", 0);
        if (servicehostname == null) {
            note.addAttribute("type", "error");
            note.setText(LocaleUtils.getLocalizedString("commands.admin.muc.createmucroom.note.servicename-missing", preferredLocale));
            return;
        }
        // Remove the server's domain name from the passed hostname
        String servicename = servicehostname.replace("."+XMPPServer.getInstance().getServerInfo().getXMPPDomain(), "");
        MultiUserChatService mucService;
        mucService = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(servicename);
        if (mucService == null) {
            note.addAttribute("type", "error");
            note.setText(LocaleUtils.getLocalizedString("commands.admin.muc.createmucroom.note.servicename-invalid", preferredLocale));
            return;
        }
        if (!mucService.isServiceEnabled()) {
            note.addAttribute("type", "error");
            note.setText(LocaleUtils.getLocalizedString("commands.admin.muc.createmucroom.note.service-disabled", preferredLocale));
            return;
        }
        // Let's create the jid and check that they are a local user
        String roomname = get(data, "roomname", 0);
        if (roomname == null) {
            note.addAttribute("type", "error");
            note.setText(LocaleUtils.getLocalizedString("commands.admin.muc.createmucroom.note.roomname-missing", preferredLocale));
            return;
        }
        roomname = JID.nodeprep(roomname);
        JID admin = admins.iterator().next();

        boolean isPersistent;
        try {
            final String value = get( data, "persistent", 0 );
            if ( value == null ) { // this field is not required.
                isPersistent = false;
            } else {
                isPersistent = DataForm.parseBoolean( value );
            }
        } catch ( ParseException e ) {
            note.addAttribute("type", "error");
            note.setText(LocaleUtils.getLocalizedString("commands.admin.muc.createmucroom.note.persistent-invalid", preferredLocale));
            return;
        }

        boolean isPublic;
        try {
            final String value = get( data, "public", 0 );
            if ( value == null ) { // this field is not required.
                isPublic = false;
            } else {
                isPublic = DataForm.parseBoolean( value );
            }
        } catch ( ParseException e ) {
            note.addAttribute("type", "error");
            note.setText(LocaleUtils.getLocalizedString("commands.admin.muc.createmucroom.note.public-invalid", preferredLocale));
            return;
        }

        final Lock lock = mucService.getChatRoomLock(roomname);
        lock.lock();
        try {
            MUCRoom room;
            try {
                room = mucService.getChatRoom(roomname, admin);
            }
            catch (NotAllowedException e) {
                note.addAttribute("type", "error");
                note.setText(LocaleUtils.getLocalizedString("commands.admin.muc.createmucroom.note.no-permission", preferredLocale));
                return;
            }

            room.setPersistent(isPersistent);
            room.setPublicRoom(isPublic);

            String password = get(data, "password", 0);
            if (password != null) {
                room.setPassword(password);
            }

            // Make sure that other cluster nodes see the changes made here.
            mucService.syncChatRoom(room);
        } finally {
            lock.unlock();
        }
    }

    @Override
    protected void addStageInformation(@Nonnull final SessionData data, Element command) {
        final Locale preferredLocale = SessionManager.getInstance().getLocaleForSession(data.getOwner());

        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle(LocaleUtils.getLocalizedString("commands.admin.muc.createmucroom.form.title", preferredLocale));
        form.addInstruction(LocaleUtils.getLocalizedString("commands.admin.muc.createmucroom.form.instruction", preferredLocale));

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel(LocaleUtils.getLocalizedString("commands.admin.muc.createmucroom.form.field.roomname.label", preferredLocale));
        field.setVariable("roomname");
        field.setRequired(true);

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel(LocaleUtils.getLocalizedString("commands.admin.muc.createmucroom.form.field.servicename.label", preferredLocale));
        field.setVariable("servicename");
        field.setRequired(true);

        field = form.addField();
        field.setType(FormField.Type.text_private);
        field.setLabel(LocaleUtils.getLocalizedString("commands.admin.muc.createmucroom.form.field.password.label", preferredLocale));
        field.setVariable("password");

        field = form.addField();
        field.setType(FormField.Type.text_private);
        field.setLabel(LocaleUtils.getLocalizedString("commands.admin.muc.createmucroom.form.field.password-verify.label", preferredLocale));
        field.setVariable("password-verify");

        field = form.addField();
        field.setType(FormField.Type.boolean_type);
        field.setLabel(LocaleUtils.getLocalizedString("commands.admin.muc.createmucroom.form.field.persistent.label", preferredLocale));
        field.setVariable("persistent");

        field = form.addField();
        field.setType(FormField.Type.boolean_type);
        field.setLabel(LocaleUtils.getLocalizedString("commands.admin.muc.createmucroom.form.field.public.label", preferredLocale));
        field.setVariable("public");

        // Add the form to the command
        command.add(form.getElement());
    }

    @Override
    protected List<Action> getActions(@Nonnull final SessionData data) {
        return Collections.singletonList(Action.complete);
    }

    @Override
    protected Action getExecuteAction(@Nonnull final SessionData data) {
        return AdHocCommand.Action.complete;
    }

}
