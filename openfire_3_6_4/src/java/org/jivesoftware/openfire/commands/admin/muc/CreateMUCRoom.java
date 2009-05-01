/**
 * $RCSfile$
 * $Revision: 3144 $
 * $Date: 2005-12-01 14:20:11 -0300 (Thu, 01 Dec 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Allows via AdHoc commands the creation of a Multi-User Chat room.
 *
 * @author Alexander Wenckus
 */
public class CreateMUCRoom extends AdHocCommand {
    public String getCode() {
        return "http://jabber.org/protocol/admin#create-muc-room";
    }

    public String getDefaultLabel() {
        return "Create a Multi-user Chat";
    }

    public int getMaxStages(SessionData data) {
        return 1;
    }

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

        boolean isPersistent = "1".equals(get(data, "persistent", 0));
        room.setPersistent(isPersistent);

        boolean isPublic = "1".equals(get(data, "public", 0));
        room.setPublicRoom(isPublic);

        String password = get(data, "password", 0);
        if (password != null) {
            room.setPassword(password);
        }
    }

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

    protected List<Action> getActions(SessionData data) {
        return Arrays.asList(AdHocCommand.Action.complete);
    }

    protected Action getExecuteAction(SessionData data) {
        return AdHocCommand.Action.complete;
    }

}
