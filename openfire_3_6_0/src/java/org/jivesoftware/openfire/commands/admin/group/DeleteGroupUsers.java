/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.commands.admin.group;

import org.dom4j.Element;
import org.jivesoftware.util.Log;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.util.Arrays;
import java.util.List;

/**
 * Command that allows to delete members or admins from a given group.
 *
 * @author Gaston Dombiak
 *
 * TODO Use i18n
 */
public class DeleteGroupUsers extends AdHocCommand {
    protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle("Delete members or admins from a group");
        form.addInstruction("Fill out this form to delete members or admins from a group.");

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel("Group Name");
        field.setVariable("group");
        field.setRequired(true);

        field = form.addField();
        field.setType(FormField.Type.jid_multi);
        field.setLabel("Users");
        field.setVariable("users");
        field.setRequired(true);

        // Add the form to the command
        command.add(form.getElement());
    }

    public void execute(SessionData data, Element command) {
        Element note = command.addElement("note");
        // Check if groups cannot be modified (backend is read-only)
        if (GroupManager.getInstance().isReadOnly()) {
            note.addAttribute("type", "error");
            note.setText("Groups are read only");
            return;
        }
        // Get requested group
        Group group;
        try {
            group = GroupManager.getInstance().getGroup(data.getData().get("group").get(0));
        } catch (GroupNotFoundException e) {
            // Group not found
            note.addAttribute("type", "error");
            note.setText("Group name does not exist");
            return;
        }

        boolean withErrors = false;
        for (String user : data.getData().get("users")) {
            try {
                group.getAdmins().remove(new JID(user));
                group.getMembers().remove(new JID(user));
            } catch (Exception e) {
                Log.warn("User not deleted from group", e);
                withErrors = true;
            }
        }

        note.addAttribute("type", "info");
        note.setText("Operation finished" + (withErrors ? " with errors" : " successfully"));
    }

    public String getCode() {
        return "http://jabber.org/protocol/admin#delete-group-members";
    }

    public String getDefaultLabel() {
        return "Delete members or admins from a group";
    }

    protected List<Action> getActions(SessionData data) {
        return Arrays.asList(AdHocCommand.Action.complete);
    }

    protected AdHocCommand.Action getExecuteAction(SessionData data) {
        return AdHocCommand.Action.complete;
    }

    public int getMaxStages(SessionData data) {
        return 1;
    }
}
