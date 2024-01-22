/*
 * Copyright (C) 2004-2008 Jive Software, 2017-2024 Ignite Realtime Foundation. All rights reserved.
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
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.event.UserEventDispatcher;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.LocaleUtils;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import java.util.*;

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
        return LocaleUtils.getLocalizedString("commands.event.usermodified.label");
    }

    @Override
    public int getMaxStages(@Nonnull final SessionData data) {
        return 1;
    }

    @Override
    public void execute(@Nonnull SessionData sessionData, Element command) {
        final Locale preferredLocale = SessionManager.getInstance().getLocaleForSession(sessionData.getOwner());

        Element note = command.addElement("note");

        Map<String, List<String>> data = sessionData.getData();

        // Get the username
        String username;
        try {
            username = get(data, "username", 0);
        }
        catch (NullPointerException npe) {
            note.addAttribute("type", "error");
            note.setText(LocaleUtils.getLocalizedString("commands.event.usermodified.note.username-required", preferredLocale));
            return;
        }

        // Get the modification type
        String type;
        try {
            type = get(data, "changeType", 0);
        }
        catch (NullPointerException npe) {
            note.addAttribute("type", "error");
            note.setText(LocaleUtils.getLocalizedString("commands.event.usermodified.note.changetype-required", preferredLocale));
            return;
        }

        // Identifies the value variable
        String valueVariable = null;
        String valueVariableName = null;

        if ("nameModified".equals(type) || "emailModified".equals(type) ||
                "creationDateModified".equals(type) || "modificationDateModified".equals(type)) {

            valueVariable = "originalValue";
            valueVariableName = LocaleUtils.getLocalizedString("commands.event.usermodified.form.field.originalvalue.label", preferredLocale);

        } else if ("propertyModified".equals(type) || "propertyAdded".equals(type) ||
                "propertyDeleted".equals(type)) {

            valueVariable = "propertyKey";
            valueVariableName = LocaleUtils.getLocalizedString("commands.event.usermodified.form.field.propertykey.label", preferredLocale);

        }

        // Creates event params.
        Map<String, Object> params = new HashMap<>();

        // Gets the value of the change if it exists
        String value;
        if (valueVariable != null) {
            try {
                // Gets the value
                value = get(data, valueVariable, 0);
                // Adds it to the event params
                params.put(valueVariable, value);

            } catch (NullPointerException npe) {
                note.addAttribute("type", "error");
                note.setText(LocaleUtils.getLocalizedString("commands.event.usermodified.note.wildcard-required", List.of(valueVariableName), preferredLocale)
                );
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
            note.setText(LocaleUtils.getLocalizedString("commands.event.usermodified.note.user-does-not-exist", preferredLocale));
            return;
        }

        // Answer that the operation was successful
        note.addAttribute("type", "info");
        note.setText(LocaleUtils.getLocalizedString("commands.global.operation.finished.success", preferredLocale));
    }

    @Override
    protected void addStageInformation(@Nonnull final SessionData data, Element command) {
        final Locale preferredLocale = SessionManager.getInstance().getLocaleForSession(data.getOwner());

        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle(LocaleUtils.getLocalizedString("commands.event.usermodified.form.title", preferredLocale));
        form.addInstruction(LocaleUtils.getLocalizedString("commands.event.usermodified.form.instruction", preferredLocale));

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel(LocaleUtils.getLocalizedString("commands.event.usermodified.form.field.username.label", preferredLocale));
        field.setVariable("username");
        field.setRequired(true);

        field.setType(FormField.Type.list_single);
        field.setLabel(LocaleUtils.getLocalizedString("commands.event.usermodified.form.field.changetype.label", preferredLocale));
        field.setVariable("changeType");
        field.addOption(LocaleUtils.getLocalizedString("commands.event.usermodified.form.field.changetype.option.namemodified.label", preferredLocale), "nameModified");
        field.addOption(LocaleUtils.getLocalizedString("commands.event.usermodified.form.field.changetype.option.emailmodified.label", preferredLocale), "emailModified");
        field.addOption(LocaleUtils.getLocalizedString("commands.event.usermodified.form.field.changetype.option.passwordmodified.label", preferredLocale), "passwordModified");
        field.addOption(LocaleUtils.getLocalizedString("commands.event.usermodified.form.field.changetype.option.creationdatemodified.label", preferredLocale), "creationDateModified");
        field.addOption(LocaleUtils.getLocalizedString("commands.event.usermodified.form.field.changetype.option.modificationdatemodified.label", preferredLocale), "modificationDateModified");
        field.addOption(LocaleUtils.getLocalizedString("commands.event.usermodified.form.field.changetype.option.propertymodified.label", preferredLocale), "propertyModified");
        field.addOption(LocaleUtils.getLocalizedString("commands.event.usermodified.form.field.changetype.option.propertyadded.label", preferredLocale), "propertyAdded");
        field.addOption(LocaleUtils.getLocalizedString("commands.event.usermodified.form.field.changetype.option.propertydeleted.label", preferredLocale), "propertyDeleted");
        field.addOption(LocaleUtils.getLocalizedString("commands.event.usermodified.form.field.changetype.option.other.label", preferredLocale), "other");
        field.setRequired(true);

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel(LocaleUtils.getLocalizedString("", preferredLocale));
        field.setVariable("originalValue");

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel(LocaleUtils.getLocalizedString("", preferredLocale));
        field.setVariable("propertyKey");

        // Add the form to the command
        command.add(form.getElement());
    }

    @Override
    protected List<Action> getActions(@Nonnull final SessionData data) {
        return Collections.singletonList(Action.complete);
    }

    @Override
    protected Action getExecuteAction(@Nonnull final SessionData data) {
        return Action.complete;
    }

    @Override
    public boolean hasPermission(JID requester) {
        return super.hasPermission(requester) || InternalComponentManager.getInstance().hasComponent(requester);
    }
}
