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

import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.WorkgroupManager;
import org.dom4j.Element;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.util.Arrays;
import java.util.List;

/**
 * Command that allows to delete existing workgroups.
 *
 * @author Gaston Dombiak
 *
 * TODO Use i18n
 */
public class DeleteWorkgroup extends AdHocCommand {
    protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle("Delete workgroup");
        form.addInstruction("Fill out this form to delete a workgroup.");

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.jid_single);
        field.setLabel("Workgroup's JID");
        field.setVariable("workgroup");
        field.setRequired(true);

        // Add the form to the command
        command.add(form.getElement());
    }

    public void execute(SessionData data, Element command) {
        Element note = command.addElement("note");
        // Get requested group
        WorkgroupManager workgroupManager = WorkgroupManager.getInstance();

        // Load the workgroup
        try {
            Workgroup workgroup = workgroupManager.getWorkgroup(new JID(data.getData().get("workgroup").get(0)));
            workgroupManager.deleteWorkgroup(workgroup);
        } catch (UserNotFoundException e) {
            // Group not found
            note.addAttribute("type", "error");
            note.setText("Workgroup not found");
            return;
        } catch (Exception e) {
            // Group not found
            note.addAttribute("type", "error");
            note.setText("Error executing the command");
            return;
        }

        note.addAttribute("type", "info");
        note.setText("Operation finished successfully");
    }

    public String getCode() {
        return "http://jabber.org/protocol/admin#delete-workgroup";
    }

    public String getDefaultLabel() {
        return "Delete workgroup";
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
