/*
 * Copyright (C) 2004-2008 Jive Software, 2022 Ignite Realtime Foundation. All rights reserved.
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

import org.apache.commons.lang3.StringUtils;
import org.dom4j.Element;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.util.*;

/**
 * Notifies the that a admin was added to the group. It can be used by user providers to notify Openfire of the
 * aditon of a admin to a group.
 *
 * @author Gabriel Guarincerri
 */
public class GroupAdminAdded extends AdHocCommand {
    @Override
    public String getCode() {
        return "http://jabber.org/protocol/event#group-admin-added";
    }

    @Override
    public String getDefaultLabel() {
        return "Group admin added";
    }

    @Override
    public int getMaxStages(SessionData data) {
        return 1;
    }

    @Override
    public void execute(SessionData sessionData, Element command) {
        Element note = command.addElement("note");

        Map<String, List<String>> data = sessionData.getData();

        // Input validation
        final Set<String> inputValidationErrors = new HashSet<>();

        Group group = null;
        final String groupName = get(data, "groupName", 0);
        if (StringUtils.isBlank(groupName)) {
            inputValidationErrors.add("The parameter 'groupName' is required, but is missing.");
        } else {
            try {
                group = GroupManager.getInstance().getGroup(groupName);
            } catch (GroupNotFoundException e) {
                inputValidationErrors.add("The group '" + groupName + "' does not exist.");
            }
        }

        final String wasMemberValue = get(data, "wasMember", 0);
        if (StringUtils.isBlank(wasMemberValue)) {
            inputValidationErrors.add("The parameter 'wasMember' is required, but is missing.");
        }
        final boolean wasMember = "1".equals(wasMemberValue) || Boolean.parseBoolean(wasMemberValue);

        JID admin;
        final String adminValue = get(data, "admin", 0);
        if (StringUtils.isBlank(adminValue)) {
            inputValidationErrors.add("The parameter 'admin' is required, but is missing.");
            return;
        } else {
            try {
                admin = new JID(adminValue);
            } catch (IllegalArgumentException e) {
                inputValidationErrors.add("The value for parameter 'admin' should be a valid JID, but '" + adminValue + "' is not.");
                return;
            }
        }

        if (!inputValidationErrors.isEmpty()) {
            note.addAttribute("type", "error");
            note.setText(StringUtils.join(inputValidationErrors, " "));
            return;
        }

        // Perform post-processing (cache updates and event notifications).
        GroupManager.getInstance().adminAddedPostProcess(group, admin, wasMember);

        // Answer that the operation was successful
        note.addAttribute("type", "info");
        note.setText("Operation finished successfully");
    }

    @Override
    protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle("Dispatching a group admin added event.");
        form.addInstruction("Fill out this form to dispatch a group admin added event.");

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
        field.setLabel("The username of the new admin");
        field.setVariable("admin");
        field.setRequired(true);

        field = form.addField();
        field.setType(FormField.Type.boolean_type);
        field.setLabel("Was this user previously a member?");
        field.setVariable("wasMember");
        field.setRequired(true);

        // Add the form to the command
        command.add(form.getElement());
    }

    @Override
    protected List<Action> getActions(SessionData data) {
        return Collections.singletonList(Action.complete);
    }

    @Override
    protected Action getExecuteAction(SessionData data) {
        return Action.complete;
    }

    @Override
    public boolean hasPermission(JID requester) {
        return super.hasPermission(requester) || InternalComponentManager.getInstance().hasComponent(requester);
    }
}
