/*
 * Copyright (C) 2004-2008 Jive Software, 2017-2022 Ignite Realtime Foundation. All rights reserved.
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
 * Notifies the that a member was removed from the group. It can be used by user providers to notify Openfire of the
 * deletion of a member of the group.
 *
 * @author Gabriel Guarincerri
 */
public class GroupMemberRemoved extends AdHocCommand {
    @Override
    public String getCode() {
        return "http://jabber.org/protocol/event#group-member-removed";
    }

    @Override
    public String getDefaultLabel() {
        return "Group member removed";
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

        JID member;
        final String memberValue = get(data, "member", 0);
        if (StringUtils.isBlank(memberValue)) {
            inputValidationErrors.add("The parameter 'member' is required, but is missing.");
            return;
        } else {
            try {
                member = new JID(memberValue);
            } catch (IllegalArgumentException e) {
                inputValidationErrors.add("The value for parameter 'member' should be a valid JID, but '" + memberValue + "' is not.");
                return;
            }
        }

        if (!inputValidationErrors.isEmpty()) {
            note.addAttribute("type", "error");
            note.setText(StringUtils.join(inputValidationErrors, " "));
            return;
        }

        // Perform post-processing (cache updates and event notifications).
        GroupManager.getInstance().memberRemovedPostProcess(group, member);

        // Answer that the operation was successful
        note.addAttribute("type", "info");
        note.setText("Operation finished successfully");
    }

    @Override
    protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle("Dispatching a group member removed event.");
        form.addInstruction("Fill out this form to dispatch a group member removed event.");

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
        field.setLabel("Member");
        field.setVariable("member");
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
