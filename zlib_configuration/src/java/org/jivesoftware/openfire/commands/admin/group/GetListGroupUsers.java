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

import org.dom4j.Element;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command that allows to retrieve list members of a given group.
 *
 * @author Gaston Dombiak
 *
 * TODO Use i18n
 */
public class GetListGroupUsers extends AdHocCommand {
    @Override
	protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle("Requesting List of Group Members");
        form.addInstruction("Fill out this form to request list of group members and admins.");

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel("Group Name");
        field.setVariable("group");
        field.setRequired(true);

        // Add the form to the command
        command.add(form.getElement());
    }

    @Override
	public void execute(SessionData data, Element command) {
        Group group;
        try {
            group = GroupManager.getInstance().getGroup(data.getData().get("group").get(0));
        } catch (GroupNotFoundException e) {
            // Group not found
            Element note = command.addElement("note");
            note.addAttribute("type", "error");
            note.setText("Group name does not exist");
            return;
        }

        DataForm form = new DataForm(DataForm.Type.result);

        form.addReportedField("jid", "User", FormField.Type.jid_single);
        form.addReportedField("admin", "Description", FormField.Type.boolean_type);

        // Add group members the result
        for (JID memberJID : group.getMembers()) {
            Map<String,Object> fields = new HashMap<String,Object>();
            fields.put("jid", memberJID.toString());
            fields.put("admin", false);
            form.addItemFields(fields);
        }
        // Add group admins the result
        for (JID memberJID : group.getAdmins()) {
            Map<String,Object> fields = new HashMap<String,Object>();
            fields.put("jid", memberJID.toString());
            fields.put("admin", true);
            form.addItemFields(fields);
        }
        command.add(form.getElement());
    }

    @Override
	public String getCode() {
        return "http://jabber.org/protocol/admin#get-group-members";
    }

    @Override
	public String getDefaultLabel() {
        return "Get List of Group Members";
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
