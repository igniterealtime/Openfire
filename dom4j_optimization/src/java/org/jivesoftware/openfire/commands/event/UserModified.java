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
import org.jivesoftware.openfire.event.UserEventDispatcher;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Notifies the that a user was modified. It can be used by user providers to notify Openfire of the
 * modification of a user.
 *
 * @author Gabriel Guarincerri
 */
public class UserModified extends AdHocCommand {
    @Override
	public String getCode() {
        return "http://jabber.org/protocol/event#user-modified";
    }

    @Override
	public String getDefaultLabel() {
        return "User modified";
    }

    @Override
	public int getMaxStages(SessionData data) {
        return 1;
    }

    @Override
	public void execute(SessionData sessionData, Element command) {
        Element note = command.addElement("note");

        Map<String, List<String>> data = sessionData.getData();

        // Get the username
        String username;
        try {
            username = get(data, "username", 0);
        }
        catch (NullPointerException npe) {
            note.addAttribute("type", "error");
            note.setText("Username required parameter.");
            return;
        }

        // Get the modification type
        String type;
        try {
            type = get(data, "changeType", 0);
        }
        catch (NullPointerException npe) {
            note.addAttribute("type", "error");
            note.setText("Change type required parameter.");
            return;
        }

        // Identifies the value variable
        String valueVariable = null;
        String valueVariableName = null;

        if ("nameModified".equals(type) || "emailModified".equals(type) ||
                "creationDateModified".equals(type) || "modificationDateModified".equals(type)) {

            valueVariable = "originalValue";
            valueVariableName = "Original value";

        } else if ("propertyModified".equals(type) || "propertyAdded".equals(type) ||
                "propertyDeleted".equals(type)) {

            valueVariable = "propertyKey";
            valueVariableName = "Property key";

        }

        // Creates event params.
        Map<String, Object> params = new HashMap<String, Object>();

        // Gets the value of the change if it exist
        String value;
        if (valueVariable != null) {
            try {
                // Gets the value
                value = get(data, valueVariable, 0);
                // Adds it to the event params
                params.put(valueVariable, value);

            } catch (NullPointerException npe) {
                note.addAttribute("type", "error");
                note.setText(valueVariableName + " required parameter.");
                return;
            }
        }

        // Adds the type of change
        params.put("type", type);

        // Sends the event
        User user;
        try {
            // Loads the updated user
            user = UserManager.getUserProvider().loadUser(username);

            // Fire event.
            UserEventDispatcher.dispatchEvent(user, UserEventDispatcher.EventType.user_modified,
                    params);

        } catch (UserNotFoundException e) {
            note.addAttribute("type", "error");
            note.setText("User not found.");
            return;
        }

        // Answer that the operation was successful
        note.addAttribute("type", "info");
        note.setText("Operation finished successfully");
    }

    @Override
	protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle("Dispatching a user updated event.");
        form.addInstruction("Fill out this form to dispatch a user updated event.");

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel("The username of the user that was updated");
        field.setVariable("username");
        field.setRequired(true);

        field.setType(FormField.Type.list_single);
        field.setLabel("Change type");
        field.setVariable("changeType");
        field.addOption("Name modified", "nameModified");
        field.addOption("Email modified", "emailModified");
        field.addOption("Password modified", "passwordModified");
        field.addOption("Creation date modified", "creationDateModified");
        field.addOption("Modification date modified", "modificationDateModified");
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
        return Arrays.asList(Action.complete);
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