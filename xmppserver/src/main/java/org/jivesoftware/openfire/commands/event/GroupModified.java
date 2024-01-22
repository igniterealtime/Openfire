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

import org.apache.commons.lang3.StringUtils;
import org.dom4j.Element;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.util.LocaleUtils;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
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
        return LocaleUtils.getLocalizedString("commands.event.groupmodified.label")
            ;
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

        // Input validation
        final Set<String> inputValidationErrors = new HashSet<>();

        Group group = null;
        final String groupName = get(data, "groupName", 0);
        if (StringUtils.isBlank(groupName)) {
            inputValidationErrors.add(LocaleUtils.getLocalizedString("commands.event.groupmodified.note.groupname-required", preferredLocale));
        } else {
            try {
                group = GroupManager.getInstance().getGroup(groupName);
            } catch (GroupNotFoundException e) {
                inputValidationErrors.add(LocaleUtils.getLocalizedString("commands.event.groupmodified.note.group-does-not-exist", List.of(groupName), preferredLocale));
            }
        }

        final String type = get(data, "changeType", 0);
        final String originalValue = get(data, "originalValue", 0);
        final String propertyKey = get(data, "propertyKey", 0);
        if (StringUtils.isBlank(type)) {
            inputValidationErrors.add(LocaleUtils.getLocalizedString("commands.event.groupmodified.note.changetype-required", preferredLocale));
        } else {
            switch (type) {
                case "nameModified":
                    if (StringUtils.isBlank(originalValue)) {
                        inputValidationErrors.add(LocaleUtils.getLocalizedString("commands.event.groupmodified.note.for-namemodified-originalvalue-required", preferredLocale));
                    }
                    break;

                case "descriptionModified":
                    if (StringUtils.isBlank(originalValue)) {
                        inputValidationErrors.add(LocaleUtils.getLocalizedString("commands.event.groupmodified.note.for-descriptionmodified-originalvalue-required", preferredLocale));
                    }
                    break;

                case "propertyAdded":
                    if (StringUtils.isBlank(propertyKey)) {
                        inputValidationErrors.add(LocaleUtils.getLocalizedString("commands.event.groupmodified.note.for-propertyadded-propertykey-required", preferredLocale));
                    }
                    break;

                case "propertyModified":
                    if (StringUtils.isBlank(originalValue)) {
                        inputValidationErrors.add(LocaleUtils.getLocalizedString("commands.event.groupmodified.note.for-propertymodified-originalvalue-required", preferredLocale));
                    }
                    if (StringUtils.isBlank(propertyKey)) {
                        inputValidationErrors.add(LocaleUtils.getLocalizedString("commands.event.groupmodified.note.for-propertymodified-propertykey-required", preferredLocale));
                    }
                    break;

                case "propertyDeleted":
                    if (StringUtils.isBlank(originalValue)) {
                        inputValidationErrors.add(LocaleUtils.getLocalizedString("commands.event.groupmodified.note.for-propertydeleted-originalvalue-required", preferredLocale));
                    }
                    if (StringUtils.isBlank(propertyKey)) {
                        inputValidationErrors.add(LocaleUtils.getLocalizedString("commands.event.groupmodified.note.for-propertydeleted-propertykey-required", preferredLocale));
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
        note.setText(LocaleUtils.getLocalizedString("commands.global.operation.finished.success", preferredLocale));
    }

    @Override
    protected void addStageInformation(@Nonnull final SessionData data, Element command) {
        final Locale preferredLocale = SessionManager.getInstance().getLocaleForSession(data.getOwner());

        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle(LocaleUtils.getLocalizedString("commands.event.groupmodified.form.title", preferredLocale));
        form.addInstruction(LocaleUtils.getLocalizedString("commands.event.groupmodified.form.instruction", preferredLocale));

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel(LocaleUtils.getLocalizedString("commands.event.groupmodified.form.field.groupname.label", preferredLocale));
        field.setVariable("groupName");
        field.setRequired(true);

        field.setType(FormField.Type.list_single);
        field.setLabel(LocaleUtils.getLocalizedString("commands.event.groupmodified.form.field.changetype.label", preferredLocale));
        field.setVariable("changeType");
        field.addOption(LocaleUtils.getLocalizedString("commands.event.groupmodified.form.field.changetype.option.namemodified.label", preferredLocale), "nameModified");
        field.addOption(LocaleUtils.getLocalizedString("commands.event.groupmodified.form.field.changetype.option.descriptionmodified.label", preferredLocale), "descriptionModified");
        field.addOption(LocaleUtils.getLocalizedString("commands.event.groupmodified.form.field.changetype.option.propertymodified.label", preferredLocale), "propertyModified");
        field.addOption(LocaleUtils.getLocalizedString("commands.event.groupmodified.form.field.changetype.option.propertyadded.label", preferredLocale), "propertyAdded");
        field.addOption(LocaleUtils.getLocalizedString("commands.event.groupmodified.form.field.changetype.option.propertydeleted.label", preferredLocale), "propertyDeleted");
        field.addOption(LocaleUtils.getLocalizedString("commands.event.groupmodified.form.field.changetype.option.other.label", preferredLocale), "other");
        field.setRequired(true);

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel(LocaleUtils.getLocalizedString("commands.event.groupmodified.form.field.originalvalue.label", preferredLocale));
        field.setVariable("originalValue");

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel(LocaleUtils.getLocalizedString("commands.event.groupmodified.form.field.propertykey.label", preferredLocale));
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
