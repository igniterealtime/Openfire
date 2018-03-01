/*
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

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Notifies the deletion of a user. It can be used by user providers to notify Openfire of the
 * deletion of a user.
 *
 * @author Gabriel Guarincerri
 */
public class UserDeleting extends AdHocCommand {
    @Override
    public String getCode() {
        return "http://jabber.org/protocol/event#user-deleting";
    }

    @Override
    public String getDefaultLabel() {
        return "Deleting a User";
    }

    @Override
    public int getMaxStages(SessionData data) {
        return 1;
    }

    @Override
    public void execute(SessionData sessionData, Element command) {
        Element note = command.addElement("note");

        Map<String, List<String>> data = sessionData.getData();

        // Gets the username
        String username;
        try {
            username = get(data, "username", 0);
        }
        catch (NullPointerException npe) {
            note.addAttribute("type", "error");
            note.setText("Username required parameter.");
            return;
        }

        // Sends the event
        User user;
        try {
            // Gets current user
            user = UserManager.getInstance().getUser(username);
            
            Map<String, Object> params = Collections.emptyMap();
            UserEventDispatcher.dispatchEvent(user, UserEventDispatcher.EventType.user_deleting, params);
        } catch (UserNotFoundException e) {
            // It's ok, user doesn't exist, so deleting it is nothing
        }

        // Answer that the operation was successful
        note.addAttribute("type", "info");
        note.setText("Operation finished successfully");
    }

    @Override
    protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle("Dispatching a user deleting event.");
        form.addInstruction("Fill out this form to dispatch a user deleting event.");

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel("The username of the user that is being deleted");
        field.setVariable("username");
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
