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
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.util.LocaleUtils;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Command that allows to update a given group.
 *
 * @author Gaston Dombiak
 */
public class UpdateGroup extends AdHocCommand {
    @Override
    protected void addStageInformation(@Nonnull final SessionData data, Element command) {
        final Locale preferredLocale = SessionManager.getInstance().getLocaleForSession(data.getOwner());

        DataForm form = new DataForm(DataForm.Type.form);
        if (data.getStage() == 0) {
            form.setTitle(LocaleUtils.getLocalizedString("commands.admin.group.updategroup.form.stage0.title", preferredLocale));
            form.addInstruction(LocaleUtils.getLocalizedString("commands.admin.group.updategroup.form.stage0.instruction", preferredLocale));

            FormField field = form.addField();
            field.setType(FormField.Type.hidden);
            field.setVariable("FORM_TYPE");
            field.addValue("http://jabber.org/protocol/admin");

            field = form.addField();
            field.setType(FormField.Type.text_single);
            field.setLabel(LocaleUtils.getLocalizedString("commands.admin.group.updategroup.form.stage0.field.group.label", preferredLocale));
            field.setVariable("group");
            field.setRequired(true);
        }
        else
        {
            // Check if groups cannot be modified (backend is read-only)
            if (GroupManager.getInstance().isReadOnly()) {
                Element note = command.addElement("note");
                note.addAttribute("type", "error");
                note.setText(LocaleUtils.getLocalizedString("commands.admin.group.updategroup.note.groups-readonly", preferredLocale));
                return;
            }
            // Get requested group
            Group group;
            try {
                group = GroupManager.getInstance().getGroup(data.getData().get("group").get(0));
            } catch (GroupNotFoundException e) {
                // Group not found
                Element note = command.addElement("note");
                note.addAttribute("type", "error");
                note.setText(LocaleUtils.getLocalizedString("commands.admin.group.updategroup.note.group-does-not-exist", preferredLocale));
                return;
            }

            form.setTitle(LocaleUtils.getLocalizedString("commands.admin.group.updategroup.form.stage1.title", preferredLocale));
            form.addInstruction(LocaleUtils.getLocalizedString("commands.admin.group.updategroup.form.stage1.instruction", preferredLocale));

            FormField field = form.addField();
            field.setType(FormField.Type.hidden);
            field.setVariable("FORM_TYPE");
            field.addValue("http://jabber.org/protocol/admin");

            field = form.addField();
            field.setType(FormField.Type.text_multi);
            field.setLabel(LocaleUtils.getLocalizedString("commands.admin.group.updategroup.form.stage1.field.desc.label", preferredLocale));
            field.setVariable("desc");
            if (group.getDescription() != null) {
                field.addValue(group.getDescription());
            }

            field = form.addField();
            field.setType(FormField.Type.list_single);
            field.setLabel(LocaleUtils.getLocalizedString("commands.admin.group.updategroup.form.stage1.field.showinroster.label", preferredLocale));
            field.setVariable("showInRoster");
            field.addOption(LocaleUtils.getLocalizedString("commands.admin.group.updategroup.form.stage1.field.showinroster.option.nobody.label", preferredLocale), "nobody");
            field.addOption(LocaleUtils.getLocalizedString("commands.admin.group.updategroup.form.stage1.field.showinroster.option.everybody.label", preferredLocale), "everybody");
            field.addOption(LocaleUtils.getLocalizedString("commands.admin.group.updategroup.form.stage1.field.showinroster.option.onlygroup.label", preferredLocale), "onlyGroup");
            field.addOption(LocaleUtils.getLocalizedString("commands.admin.group.updategroup.form.stage1.field.showinroster.option.spefgroups.label", preferredLocale), "spefgroups");
            field.setRequired(true);
            if (group.getSharedWith() != null) {
                final String showInRoster;
                switch (group.getSharedWith()) {
                    case nobody:
                        showInRoster = "nobody";
                        break;
                    case everybody:
                        showInRoster = "everybody";
                        break;
                    case usersOfGroups:
                        final List<String> sharedWith = group.getSharedWithUsersInGroupNames();
                        if (sharedWith.isEmpty() || (sharedWith.size() == 1 && sharedWith.contains(group.getName()))) {
                            showInRoster = "onlyGroup";
                        } else {
                            showInRoster = "spefgroups";
                        }
                        break;
                    default:
                        showInRoster = group.getSharedWith().toString();
                        break;
                }
                field.addValue(showInRoster);
            } else {
                field.addValue("nobody");
            }

            field = form.addField();
            field.setType(FormField.Type.list_multi);
            field.setVariable("groupList");
            for (Group otherGroup : GroupManager.getInstance().getGroups()) {
                field.addOption(otherGroup.getName(), otherGroup.getName());
            }
            final List<String> groupList = group.getSharedWithUsersInGroupNames();
            for (final String othergroup : groupList) {
                field.addValue(othergroup);
            }

            field = form.addField();
            field.setType(FormField.Type.text_single);
            field.setLabel(LocaleUtils.getLocalizedString("commands.admin.group.updategroup.form.stage1.field.displayname.label", preferredLocale));
            field.setVariable("displayName");
            String displayName = group.getSharedDisplayName();
            if (displayName != null) {
                field.addValue(displayName);
            }
        }

        // Add the form to the command
        command.add(form.getElement());
    }

    @Override
    public void execute(@Nonnull final SessionData data, Element command) {
        final Locale preferredLocale = SessionManager.getInstance().getLocaleForSession(data.getOwner());

        Element note = command.addElement("note");
        // Get requested group
        Group group;
        try {
            group = GroupManager.getInstance().getGroup(data.getData().get("group").get(0));
        } catch (GroupNotFoundException e) {
            // Group not found
            note.addAttribute("type", "error");
            note.setText(LocaleUtils.getLocalizedString("commands.admin.group.updategroup.note.group-does-not-exist", preferredLocale));
            return;
        }

        List<String> desc = data.getData().get("desc");
        if (desc != null) {
            group.setDescription(desc.get(0));
        }

        String showInRoster = data.getData().get("showInRoster").get(0);
        String displayName;
        if (data.getData().get("displayName") != null && !data.getData().get("displayName").isEmpty()) {
            displayName = data.getData().get("displayName").get(0);
        } else {
            displayName = group.getSharedDisplayName();
        }
        List<String> groupList = data.getData().get("groupList");

        switch (showInRoster) {
            case "nobody":
                // New group is not a shared group
                group.shareWithNobody();
                break;

            case "everybody":
                if (displayName != null ) {
                    group.shareWithEverybody(displayName);
                }
                break;

            case "spefgroups":
                if (displayName != null ) {
                    group.shareWithUsersInGroups(groupList, displayName);
                }
                break;

            case "onlyGroup":
                if (displayName != null ) {
                    group.shareWithUsersInSameGroup(displayName);
                }
                break;
        }

        note.addAttribute("type", "info");
        note.setText(LocaleUtils.getLocalizedString("commands.global.operation.finished.success", preferredLocale));
    }

    @Override
    public String getCode() {
        return "http://jabber.org/protocol/admin#update-group";
    }

    @Override
    public String getDefaultLabel() {
        return LocaleUtils.getLocalizedString("commands.admin.group.updategroup.label");
    }

    @Override
    protected List<Action> getActions(@Nonnull final SessionData data) {
        if (data.getStage() == 0) {
            return Collections.singletonList(Action.next);
        }
        else if (data.getStage() == 1) {
            return Arrays.asList(AdHocCommand.Action.next, AdHocCommand.Action.prev, AdHocCommand.Action.complete);
        }
        return Collections.singletonList(Action.complete);
    }

    @Override
    protected AdHocCommand.Action getExecuteAction(@Nonnull final SessionData data) {
        if (data.getStage() == 0) {
            return AdHocCommand.Action.next;
        }
        return AdHocCommand.Action.complete;
    }

    @Override
    public int getMaxStages(@Nonnull final SessionData data) {
        return 2;
    }
}
