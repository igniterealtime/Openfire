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
package org.jivesoftware.openfire.commands.admin.muc;

import org.dom4j.Element;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.openfire.muc.NotAllowedException;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.text.ParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
        return "Create a Multi-user Chat";
    }

    @Override
    public int getMaxStages(SessionData data) {
        return 1;
    }

    @Override
    public void execute(SessionData sessionData, Element command) {
        Element note = command.addElement("note");
        Collection<JID> admins = XMPPServer.getInstance().getAdmins();
        if (admins.size() <= 0) {
            note.addAttribute("type", "error");
            note.setText("Server needs admin user to be able to create rooms.");
            return;
        }
        Map<String, List<String>> data = sessionData.getData();

        // Let's find the requested MUC service to create the room in
        String servicehostname = get(data, "servicename", 0);
        if (servicehostname == null) {
            note.addAttribute("type", "error");
            note.setText("Service name must be specified.");
            return;
        }
        // Remove the server's domain name from the passed hostname
        String servicename = servicehostname.replace("."+XMPPServer.getInstance().getServerInfo().getXMPPDomain(), "");
        MultiUserChatService mucService;
        mucService = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(servicename);
        if (mucService == null) {
            note.addAttribute("type", "error");
            note.setText("Invalid service name specified.");
            return;
        }
        if (!mucService.isServiceEnabled()) {
            note.addAttribute("type", "error");
            note.setText("Multi user chat is disabled for specified service.");
            return;
        }
        // Let's create the jid and check that they are a local user
        String roomname = get(data, "roomname", 0);
        if (roomname == null) {
            note.addAttribute("type", "error");
            note.setText("Room name must be specified.");
            return;
        }
        JID admin = admins.iterator().next();
        MUCRoom room;
        try {
            room = mucService.getChatRoom(roomname, admin);
        }
        catch (NotAllowedException e) {
            note.addAttribute("type", "error");
            note.setText("No permission to create rooms.");
            return;
        }

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
            note.setText("persistent has invalid value. Needs to be boolean.");
            return;
        }
        room.setPersistent(isPersistent);

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
            note.setText("public has invalid value. Needs to be boolean.");
            return;
        }
        room.setPublicRoom(isPublic);

        String password = get(data, "password", 0);
        if (password != null) {
            room.setPassword(password);
        }
    }

    @Override
    protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle("Create a multi-user chat room");
        form.addInstruction("Fill out this form to create a multi-user chat room.");

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel("The name of the room");
        field.setVariable("roomname");
        field.setRequired(true);

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel("The service (hostname) to create the room on");
        field.setVariable("servicename");
        field.setRequired(true);

        field = form.addField();
        field.setType(FormField.Type.text_private);
        field.setLabel("The password for this account");
        field.setVariable("password");

        field = form.addField();
        field.setType(FormField.Type.text_private);
        field.setLabel("Retype password");
        field.setVariable("password-verify");

        field = form.addField();
        field.setType(FormField.Type.boolean_type);
        field.setLabel("Room is persistent");
        field.setVariable("persistent");

        field = form.addField();
        field.setType(FormField.Type.boolean_type);
        field.setLabel("Is the room public");
        field.setVariable("public");

        // Add the form to the command
        command.add(form.getElement());
    }

    @Override
    protected List<Action> getActions(SessionData data) {
        return Collections.singletonList(Action.complete);
    }

    @Override
    protected Action getExecuteAction(SessionData data) {
        return AdHocCommand.Action.complete;
    }

}
