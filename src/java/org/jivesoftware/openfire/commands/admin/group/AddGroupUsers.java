/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.commands.admin.group;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.dom4j.Element;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

/**
 * Command that allows to add members or admins to a given group.
 *
 * @author Gaston Dombiak
 *
 * TODO Use i18n
 */
public class AddGroupUsers extends AdHocCommand {
	
	private static final Logger Log = LoggerFactory.getLogger(AddGroupUsers.class);

    @Override
	protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle("Add members or admins to a group");
        form.addInstruction("Fill out this form to add new members or admins to a group.");

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
        field.setType(FormField.Type.boolean_type);
        field.setLabel("Admin");
        field.setVariable("admin");
        field.addValue(false);
        field.setRequired(true);

        field = form.addField();
        field.setType(FormField.Type.jid_multi);
        field.setLabel("Users");
        field.setVariable("users");
        field.setRequired(true);

        // Add the form to the command
        command.add(form.getElement());
    }

    @Override
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

        String admin = data.getData().get("admin").get(0);
        boolean isAdmin = "1".equals(admin) || "true".equals(admin);
        Collection<JID> users = (isAdmin ? group.getAdmins() : group.getMembers());

        boolean withErrors = false;
        for (String user : data.getData().get("users")) {
            try {
                users.add(new JID(user));
            } catch (Exception e) {
                Log.warn("User not added to group", e);
                withErrors = true;
            }
        }

        note.addAttribute("type", "info");
        note.setText("Operation finished" + (withErrors ? " with errors" : " successfully"));
    }

    @Override
	public String getCode() {
        return "http://jabber.org/protocol/admin#add-group-members";
    }

    @Override
	public String getDefaultLabel() {
        return "Add members or admins to a group";
    }

    @Override
	protected List<Action> getActions(SessionData data) {
        return Arrays.asList(AdHocCommand.Action.complete);
    }

    @Override
	protected AdHocCommand.Action getExecuteAction(SessionData data) {
        return AdHocCommand.Action.complete;
    }

    @Override
	public int getMaxStages(SessionData data) {
        return 1;
    }
}
