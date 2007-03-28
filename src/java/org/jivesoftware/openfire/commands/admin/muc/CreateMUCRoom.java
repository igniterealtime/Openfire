/**
 * $RCSfile:  $
 * $Revision:  $
 * $Date:  $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.openfire.commands.admin.muc;

import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.muc.MultiUserChatServer;
import org.jivesoftware.openfire.muc.NotAllowedException;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.dom4j.Element;
import org.xmpp.packet.JID;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;

import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.Arrays;

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
        MultiUserChatServer server = XMPPServer.getInstance().getMultiUserChatServer();
        if (!server.isServiceEnabled()) {
            note.addAttribute("type", "error");
            note.setText("Multi user chat is disabled on server.");
            return;
        }
        Collection<JID> admins = XMPPServer.getInstance().getAdmins();
        if (admins.size() <= 0) {
            note.addAttribute("type", "error");
            note.setText("Server needs admin user to be able to create rooms.");
            return;
        }
        Map<String, List<String>> data = sessionData.getData();

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
            room = server.getChatRoom(roomname, admin);
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

    private String get(Map<String, List<String>> data, String key, int value) {
        List<String> list = data.get(key);
        if (list == null) {
            return null;
        }
        else {
            return list.get(value);
        }
    }
}
