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
 * Notifies the that an admin was added to the group. It can be used by user providers to notify Openfire of the
 * addition of an admin to a group.
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
        return LocaleUtils.getLocalizedString("commands.event.groupadminadded.label");
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
            inputValidationErrors.add(LocaleUtils.getLocalizedString("commands.event.groupadminadded.note.groupname-required", preferredLocale));
        } else {
            try {
                group = GroupManager.getInstance().getGroup(groupName);
            } catch (GroupNotFoundException e) {
                inputValidationErrors.add(LocaleUtils.getLocalizedString("commands.event.groupadminadded.note.group-does-not-exist", List.of(groupName), preferredLocale));
            }
        }

        final String wasMemberValue = get(data, "wasMember", 0);
        if (StringUtils.isBlank(wasMemberValue)) {
            inputValidationErrors.add(LocaleUtils.getLocalizedString("commands.event.groupadminadded.note.wasmember-required", preferredLocale));
        }
        final boolean wasMember = "1".equals(wasMemberValue) || Boolean.parseBoolean(wasMemberValue);

        JID admin;
        final String adminValue = get(data, "admin", 0);
        if (StringUtils.isBlank(adminValue)) {
            inputValidationErrors.add(LocaleUtils.getLocalizedString("commands.event.groupadminadded.note.admin-required", preferredLocale));
            return;
        } else {
            try {
                admin = new JID(adminValue);
            } catch (IllegalArgumentException e) {
                inputValidationErrors.add(LocaleUtils.getLocalizedString("commands.event.groupadminadded.note.admin-jid-invalid", List.of(adminValue), preferredLocale));
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
        note.setText(LocaleUtils.getLocalizedString("commands.global.operation.finished.success", preferredLocale));
    }

    @Override
    protected void addStageInformation(@Nonnull final SessionData data, Element command) {
        final Locale preferredLocale = SessionManager.getInstance().getLocaleForSession(data.getOwner());

        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle(LocaleUtils.getLocalizedString("commands.event.groupadminadded.form.title", preferredLocale));
        form.addInstruction(LocaleUtils.getLocalizedString("commands.event.groupadminadded.form.instruction", preferredLocale));

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel(LocaleUtils.getLocalizedString("commands.event.groupadminadded.form.field.groupname.label", preferredLocale));
        field.setVariable("groupName");
        field.setRequired(true);

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel(LocaleUtils.getLocalizedString("commands.event.groupadminadded.form.field.admin.label", preferredLocale));
        field.setVariable("admin");
        field.setRequired(true);

        field = form.addField();
        field.setType(FormField.Type.boolean_type);
        field.setLabel(LocaleUtils.getLocalizedString("commands.event.groupadminadded.form.field.wasmember.label", preferredLocale));
        field.setVariable("wasMember");
        field.setRequired(true);

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
