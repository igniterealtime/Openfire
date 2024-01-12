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
import org.jivesoftware.openfire.roster.RosterManager;
import org.jivesoftware.util.LocaleUtils;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Command that allows to retrieve a list of existing groups.
 *
 * @author Gaston Dombiak
 */
public class GetListGroups extends AdHocCommand {

    @Override
    protected void addStageInformation(@Nonnull final SessionData data, Element command) {
        final Locale preferredLocale = SessionManager.getInstance().getLocaleForSession(data.getOwner());

        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle(LocaleUtils.getLocalizedString("commands.admin.group.getlistgroups.form.title", preferredLocale));
        form.addInstruction(LocaleUtils.getLocalizedString("commands.admin.group.getlistgroups.form.instruction", preferredLocale));

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.list_single);
        field.setLabel(LocaleUtils.getLocalizedString("commands.global.operation.pagination.start", preferredLocale));
        field.setVariable("start");
        field.addValue("0");
        field.addOption("0", "0");
        field.addOption("25", "25");
        field.addOption("50", "50");
        field.addOption("75", "75");
        field.addOption("100", "100");
        field.addOption("150", "150");
        field.addOption("200", "200");
        field.setRequired(true);

        field = form.addField();
        field.setType(FormField.Type.list_single);
        field.setLabel(LocaleUtils.getLocalizedString("commands.global.operation.pagination.max_items", preferredLocale));
        field.setVariable("max_items");
        field.addValue("25");
        field.addOption("25", "25");
        field.addOption("50", "50");
        field.addOption("75", "75");
        field.addOption("100", "100");
        field.addOption("150", "150");
        field.addOption("200", "200");
        field.addOption(LocaleUtils.getLocalizedString("commands.global.operation.pagination.none", preferredLocale), "none");
        field.setRequired(true);

        // Add the form to the command
        command.add(form.getElement());
    }

    @Override
    public void execute(@Nonnull final SessionData data, Element command) {
        final Locale preferredLocale = SessionManager.getInstance().getLocaleForSession(data.getOwner());

        int nStart = 0;
        final List<String> start_data = data.getData().get("start");
        if (start_data != null && !start_data.isEmpty()) {
            String start = start_data.get(0);
            if (start != null && !"none".equals(start)) {
                try {
                    nStart = Integer.parseInt(start);
                } catch (NumberFormatException e) {
                    // Do nothing. Assume that all users are being requested
                }
            }
        }
        int maxItems = 100000;
        final List<String> max_items_data = data.getData().get("max_items");
        if (max_items_data != null && !max_items_data.isEmpty()) {
            String max_items = max_items_data.get(0);
            if (max_items != null && !"none".equals(max_items)) {
                try {
                    maxItems = Integer.parseInt(max_items);
                } catch (NumberFormatException e) {
                    // Do nothing. Assume that all users are being requested
                }
            }
        }

        DataForm form = new DataForm(DataForm.Type.result);

        form.addReportedField("name", LocaleUtils.getLocalizedString("commands.admin.group.getlistgroups.form.reportedfield.name.label", preferredLocale), FormField.Type.text_single);
        form.addReportedField("desc", LocaleUtils.getLocalizedString("commands.admin.group.getlistgroups.form.reportedfield.desc.label", preferredLocale), FormField.Type.text_multi);
        form.addReportedField("count", LocaleUtils.getLocalizedString("commands.admin.group.getlistgroups.form.reportedfield.count.label", preferredLocale), FormField.Type.text_single);
        form.addReportedField("shared", LocaleUtils.getLocalizedString("commands.admin.group.getlistgroups.form.reportedfield.shared.label", preferredLocale), FormField.Type.boolean_type);
        form.addReportedField("display", LocaleUtils.getLocalizedString("commands.admin.group.getlistgroups.form.reportedfield.display.label", preferredLocale), FormField.Type.text_single);
        form.addReportedField("visibility", LocaleUtils.getLocalizedString("commands.admin.group.getlistgroups.form.reportedfield.visibility.label", preferredLocale), FormField.Type.text_single);
        form.addReportedField("groups", LocaleUtils.getLocalizedString("commands.admin.group.getlistgroups.form.reportedfield.groups.label", preferredLocale), FormField.Type.text_multi);

        // Add groups to the result
        for (Group group : GroupManager.getInstance().getGroups(nStart, maxItems)) {
            boolean isSharedGroup = RosterManager.isSharedGroup(group);
            Map<String, String> properties = group.getProperties();
            Map<String,Object> fields = new HashMap<>();
            fields.put("name", group.getName());
            fields.put("desc", group.getDescription());
            fields.put("count", group.getMembers().size() + group.getAdmins().size());
            fields.put("shared", isSharedGroup);
            fields.put("display", (isSharedGroup ? group.getSharedDisplayName() : ""));
            final String showInRoster;
            if (!isSharedGroup || group.getSharedWith() == null) {
                showInRoster = "";
            } else {
                switch (group.getSharedWith()) {
                    case nobody:
                        showInRoster = LocaleUtils.getLocalizedString("commands.admin.group.getlistgroups.form.reportedfield.visibility.nobody.label", preferredLocale);
                        break;
                    case everybody:
                        showInRoster = LocaleUtils.getLocalizedString("commands.admin.group.getlistgroups.form.reportedfield.visibility.everybody.label", preferredLocale);
                        break;
                    case usersOfGroups:
                        final List<String> sharedWith = group.getSharedWithUsersInGroupNames();
                        if (sharedWith.isEmpty() || (sharedWith.size() == 1 && sharedWith.contains(group.getName()))) {
                            showInRoster = LocaleUtils.getLocalizedString("commands.admin.group.getlistgroups.form.reportedfield.visibility.onlygroup.label", preferredLocale);
                        } else {
                            showInRoster = LocaleUtils.getLocalizedString("commands.admin.group.getlistgroups.form.reportedfield.visibility.spefgroups.label", preferredLocale);
                        }
                        break;
                    default:
                        showInRoster = group.getSharedWith().toString();
                        break;
                }
            }
            fields.put("visibility", showInRoster);
            fields.put("groups", (isSharedGroup ? String.join(",", group.getSharedWithUsersInGroupNames()) : ""));
            form.addItemFields(fields);
        }
        command.add(form.getElement());
    }

    @Override
    public String getCode() {
        return "http://jabber.org/protocol/admin#get-groups";
    }

    @Override
    public String getDefaultLabel() {
        return LocaleUtils.getLocalizedString("commands.admin.group.getlistgroups.label");
    }

    @Override
    protected List<AdHocCommand.Action> getActions(@Nonnull final SessionData data) {
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
