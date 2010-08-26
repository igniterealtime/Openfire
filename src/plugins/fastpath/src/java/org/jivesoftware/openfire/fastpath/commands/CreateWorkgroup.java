/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
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
package org.jivesoftware.openfire.fastpath.commands;

import org.jivesoftware.openfire.fastpath.util.WorkgroupUtils;
import org.dom4j.Element;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.StringUtils;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Command that allows to create new workgroups.
 *
 * @author Gaston Dombiak
 *
 * TODO Use i18n
 */
public class CreateWorkgroup extends AdHocCommand {
    @Override
	public String getCode() {
        return "http://jabber.org/protocol/admin#add-workgroup";
    }

    @Override
	public String getDefaultLabel() {
        return "Add a Workgroup";
    }

    @Override
	public int getMaxStages(SessionData data) {
        return 1;
    }

    @Override
	public void execute(SessionData sessionData, Element command) {
        Element note = command.addElement("note");
        Map<String, List<String>> data = sessionData.getData();

        // Get the name of the new workgroup
        String wgName= get(data, "name", 0);
        String description = get(data, "description", 0);
        List<String> members = data.get("members");
        String agents = StringUtils.collectionToString(members);

        if (wgName == null) {
            note.addAttribute("type", "error");
            note.setText("Please specify the name of the workgroup.");
            return;
        }

        Map<String, String> errors = WorkgroupUtils.createWorkgroup(wgName, description, agents);

        if (!errors.isEmpty()) {
            note.addAttribute("type", "error");
            // TODO check errors. give better error message
            note.setText("Error creating workgroup.");
            return;
        }
        // Answer that the operation was successful
        note.addAttribute("type", "info");
        note.setText("Operation finished successfully");
    }

    @Override
	protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle("Adding a new workgroup");
        form.addInstruction("Fill out this form to add a workgroup.");

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel("The name of the workgroup to be added");
        field.setVariable("name");
        field.setRequired(true);

        field = form.addField();
        field.setType(FormField.Type.text_multi);
        field.setLabel("Username of the members");
        field.setVariable("members");

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel("Description");
        field.setVariable("description");

        // Add the form to the command
        command.add(form.getElement());
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
	public boolean hasPermission(JID requester) {
        return super.hasPermission(requester) && !UserManager.getUserProvider().isReadOnly();
    }
}
