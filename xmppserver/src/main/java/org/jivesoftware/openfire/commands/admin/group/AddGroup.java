/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2024 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire.commands.admin.group;

import org.dom4j.Element;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupAlreadyExistsException;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNameInvalidException;
import org.jivesoftware.util.LocaleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Command that allows to create and configure new groups.
 *
 * @author Gaston Dombiak
 */
public class AddGroup extends AdHocCommand {
    
    private static final Logger Log = LoggerFactory.getLogger(AddGroup.class);

    @Override
    protected void addStageInformation(@Nonnull final SessionData data, Element command) {
        final Locale preferredLocale = SessionManager.getInstance().getLocaleForSession(data.getOwner());

        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle(LocaleUtils.getLocalizedString("commands.admin.group.addgroup.form.title", preferredLocale));
        form.addInstruction(LocaleUtils.getLocalizedString("commands.admin.group.addgroup.form.instruction", preferredLocale));

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel(LocaleUtils.getLocalizedString("commands.admin.group.addgroup.form.field.group.label", preferredLocale));
        field.setVariable("group");
        field.setRequired(true);

        field = form.addField();
        field.setType(FormField.Type.text_multi);
        field.setLabel(LocaleUtils.getLocalizedString("commands.admin.group.addgroup.form.field.desc.label", preferredLocale));
        field.setVariable("desc");

        field = form.addField();
        field.setType(FormField.Type.jid_multi);
        field.setLabel(LocaleUtils.getLocalizedString("commands.admin.group.addgroup.form.field.members.label", preferredLocale));
        field.setVariable("members");

        field = form.addField();
        field.setType(FormField.Type.list_single);
        field.setLabel(LocaleUtils.getLocalizedString("commands.admin.group.addgroup.form.field.showinroster.label", preferredLocale));
        field.setVariable("showInRoster");
        field.addValue("nobody");
        field.addOption(LocaleUtils.getLocalizedString("commands.admin.group.addgroup.form.field.showinroster.option.nobody.label", preferredLocale), "nobody");
        field.addOption(LocaleUtils.getLocalizedString("commands.admin.group.addgroup.form.field.showinroster.option.everybody.label", preferredLocale), "everybody");
        field.addOption(LocaleUtils.getLocalizedString("commands.admin.group.addgroup.form.field.showinroster.option.onlygroup.label", preferredLocale), "onlyGroup");
        field.addOption(LocaleUtils.getLocalizedString("commands.admin.group.addgroup.form.field.showinroster.option.spefgroups.label", preferredLocale), "spefgroups");
        field.setRequired(true);

        field = form.addField();
        field.setType(FormField.Type.list_multi);
        field.setVariable("groupList");
        for (Group group : GroupManager.getInstance().getGroups()) {
            field.addOption(group.getName(), group.getName());
        }

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel(LocaleUtils.getLocalizedString("commands.admin.group.addgroup.form.field.displayname.label", preferredLocale));
        field.setVariable("displayName");

        // Add the form to the command
        command.add(form.getElement());
    }

    @Override
    public void execute(@Nonnull final SessionData data, Element command) {
        final Locale preferredLocale = SessionManager.getInstance().getLocaleForSession(data.getOwner());

        Element note = command.addElement("note");
        // Check if groups cannot be modified (backend is read-only)
        if (GroupManager.getInstance().isReadOnly()) {
            note.addAttribute("type", "error");
            note.setText(LocaleUtils.getLocalizedString("commands.admin.group.addgroup.note.groups-readonly", preferredLocale));
            return;
        }
        // Get requested group
        Group group;
        try {
            group = GroupManager.getInstance().createGroup(data.getData().get("group").get(0));
        } catch (GroupAlreadyExistsException e) {
            // Group not found
            note.addAttribute("type", "error");
            note.setText(LocaleUtils.getLocalizedString("commands.admin.group.addgroup.note.group-exists", preferredLocale));
            return;
        } catch (GroupNameInvalidException e) {
            // Group name not valid
            note.addAttribute("type", "error");
            note.setText(LocaleUtils.getLocalizedString("commands.admin.group.addgroup.note.group-name-invalid", preferredLocale));
            return;
        }

        List<String> desc = data.getData().get("desc");
        if (desc != null && !desc.isEmpty()) {
            group.setDescription(desc.get(0));
        }

        List<String> members = data.getData().get("members");
        boolean withErrors = false;
        if (members != null) {
            Collection<JID> users = group.getMembers();
            for (String user : members) {
                try {
                    users.add(new JID(user));
                } catch (Exception e) {
                    Log.warn("User not added to group", e);
                    withErrors = true;
                }
            }
        }

        String showInRoster = data.getData().get("showInRoster").get(0);
        List<String> displayName = data.getData().get("displayName");
        List<String> groupList = data.getData().get("groupList");

        // New group is configured as a shared group
        switch (showInRoster) {
            case "nobody":
                // New group is not a shared group
                group.shareWithNobody();
                break;

            case "everybody":
                if (displayName == null) {
                    withErrors = true;
                } else {
                    group.shareWithEverybody(displayName.get(0));
                }
                break;

            case "spefgroups":
                if (displayName == null) {
                    withErrors = true;
                } else {
                    group.shareWithUsersInGroups(groupList, displayName.get(0));
                }
                break;

            case "onlyGroup":
                if (displayName == null) {
                    withErrors = true;
                } else {
                    group.shareWithUsersInSameGroup(displayName.get(0));
                }
                break;

            default:
                withErrors = true;
        }

        note.addAttribute("type", "info");
        note.setText(LocaleUtils.getLocalizedString((withErrors ? "commands.global.operation.finished.with-errors" : "commands.global.operation.finished.success"), preferredLocale));
    }

    @Override
    public String getCode() {
        return "http://jabber.org/protocol/admin#add-group";
    }

    @Override
    public String getDefaultLabel() {
        return LocaleUtils.getLocalizedString("commands.admin.group.addgroup.label");
    }

    @Override
    protected List<Action> getActions(@Nonnull final SessionData data) {
        return Collections.singletonList(Action.complete);
    }

    @Override
    protected AdHocCommand.Action getExecuteAction(@Nonnull final SessionData data) {
        return AdHocCommand.Action.complete;
    }

    @Override
    public int getMaxStages(@Nonnull final SessionData data) {
        return 1;
    }
}
