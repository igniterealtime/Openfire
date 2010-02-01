/**
 * $RCSfile$
 * $Revision: 3144 $
 * $Date: 2005-12-01 14:20:11 -0300 (Thu, 01 Dec 2005) $
 *
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
package org.jivesoftware.openfire.commands.event;

import org.dom4j.Element;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.event.GroupEventDispatcher;
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
 * Notifies the that a admin was removed from the group. It can be used by user providers to notify Openfire of the
 * deletion of a admin of the group.
 *
 * @author Gabriel Guarincerri
 */
public class GroupAdminRemoved extends AdHocCommand {
    public String getCode() {
        return "http://jabber.org/protocol/event#group-admin-removed";
    }

    public String getDefaultLabel() {
        return "Group admin removed";
    }

    public int getMaxStages(SessionData data) {
        return 1;
    }

    public void execute(SessionData sessionData, Element command) {
        Element note = command.addElement("note");

        Map<String, List<String>> data = sessionData.getData();

        // Get the group name
        String groupname;
        try {
            groupname = get(data, "groupName", 0);
        }
        catch (NullPointerException npe) {
            note.addAttribute("type", "error");
            note.setText("Group name required parameter.");
            return;
        }

        // Creates event params.
        Map<String, Object> params = null;

        try {

            // Get the admin
            String admin = get(data, "admin", 0);

            // Adds the admin
            params = new HashMap<String, Object>();
            params.put("admin", admin);
        }
        catch (NullPointerException npe) {
            note.addAttribute("type", "error");
            note.setText("Admin required parameter.");
            return;
        }

        // Sends the event
        Group group;
        try {
            group = GroupManager.getInstance().getGroup(groupname, true);

            // Fire event.
            GroupEventDispatcher.dispatchEvent(group, GroupEventDispatcher.EventType.admin_removed, params);

        } catch (GroupNotFoundException e) {
            note.addAttribute("type", "error");
            note.setText("Group not found.");
        }

        // Answer that the operation was successful
        note.addAttribute("type", "info");
        note.setText("Operation finished successfully");
    }

    protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle("Dispatching a group admin removed event.");
        form.addInstruction("Fill out this form to dispatch a group admin removed event.");

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel("The group name of the group");
        field.setVariable("groupName");
        field.setRequired(true);

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel("The username of the admin");
        field.setVariable("admin");
        field.setRequired(true);

        // Add the form to the command
        command.add(form.getElement());
    }

    protected List<Action> getActions(SessionData data) {
        return Arrays.asList(Action.complete);
    }

    protected Action getExecuteAction(SessionData data) {
        return Action.complete;
    }

    public boolean hasPermission(JID requester) {
        return super.hasPermission(requester) || InternalComponentManager.getInstance().hasComponent(requester);
    }
}