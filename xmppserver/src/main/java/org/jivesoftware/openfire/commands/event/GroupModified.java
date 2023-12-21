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
 * Notifies the that a group was modified. It can be used by user providers to notify Openfire of the
 * modification of a group.
 *
 * @author Gabriel Guarincerri
 */
public class GroupModified extends AdHocCommand {
    @Override
    public String getCode() {
        return "http://jabber.org/protocol/event#group-modified";
    }

    @Override
    public String getDefaultLabel() {
        return "Group modified";
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

        final String type = get(data, "changeType", 0);
        final String originalValue = get(data, "originalValue", 0);
        final String propertyKey = get(data, "propertyKey", 0);
        if (StringUtils.isBlank(type)) {
            inputValidationErrors.add("The parameter 'changeType' is required, but is missing.");
        } else {
            switch (type) {
                case "nameModified":
                    if (StringUtils.isBlank(originalValue)) {
                        inputValidationErrors.add("For changeType 'nameModified', parameter 'originalValue' is required.");
                    }
                    break;

                case "descriptionModified":
                    if (StringUtils.isBlank(originalValue)) {
                        inputValidationErrors.add("For changeType 'descriptionModified', parameter 'originalValue' is required.");
                    }
                    break;

                case "propertyAdded":
                    if (StringUtils.isBlank(propertyKey)) {
                        inputValidationErrors.add("For changeType 'propertyAdded', parameter 'propertyKey' is required.");
                    }
                    break;

                case "propertyModified":
                    if (StringUtils.isBlank(originalValue)) {
                        inputValidationErrors.add("For changeType 'propertyModified', parameter 'originalValue' is required.");
                    }
                    if (StringUtils.isBlank(propertyKey)) {
                        inputValidationErrors.add("For changeType 'propertyModified', parameter 'propertyKey' is required.");
                    }
                    break;

                case "propertyDeleted":
                    if (StringUtils.isBlank(originalValue)) {
                        inputValidationErrors.add("For changeType 'propertyDeleted', parameter 'originalValue' is required.");
                    }
                    if (StringUtils.isBlank(propertyKey)) {
                        inputValidationErrors.add("For changeType 'propertyDeleted', parameter 'propertyKey' is required.");
                    }
                    break;
            }
        }

        if (!inputValidationErrors.isEmpty()) {
            note.addAttribute("type", "error");
            note.setText(StringUtils.join(inputValidationErrors, " "));
            return;
        }

        // Perform post-processing (cache updates and event notifications).
        switch (type) {
            case "nameModified":
                GroupManager.getInstance().renameGroupPostProcess(group, originalValue);
                break;

            case "descriptionModified":
                GroupManager.getInstance().redescribeGroupPostProcess(group, originalValue);
                break;

            case "propertyAdded":
                GroupManager.getInstance().propertyAddedPostProcess(group, propertyKey);
                break;

            case "propertyModified":
                GroupManager.getInstance().propertyModifiedPostProcess(group, propertyKey, originalValue);
                break;

            case "propertyDeleted":
                GroupManager.getInstance().propertyDeletedPostProcess(group, propertyKey, originalValue);
                break;
        }

        // Answer that the operation was successful
        note.addAttribute("type", "info");
        note.setText("Operation finished successfully");
    }

    @Override
    protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle("Dispatching a group created event.");
        form.addInstruction("Fill out this form to dispatch a group created event.");

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel("The group name of the group that was created");
        field.setVariable("groupName");
        field.setRequired(true);

        field.setType(FormField.Type.list_single);
        field.setLabel("Change type");
        field.setVariable("changeType");
        field.addOption("Name modified", "nameModified");
        field.addOption("Description modified", "descriptionModified");
        field.addOption("Property modified", "propertyModified");
        field.addOption("Property added", "propertyAdded");
        field.addOption("Property deleted", "propertyDeleted");
        field.addOption("Other", "other");
        field.setRequired(true);

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel("Original value");
        field.setVariable("originalValue");

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel("Name of the property");
        field.setVariable("propertyKey");


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
