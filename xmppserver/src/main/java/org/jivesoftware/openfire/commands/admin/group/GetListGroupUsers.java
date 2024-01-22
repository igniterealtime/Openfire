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
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Command that allows to retrieve list members of a given group.
 *
 * @author Gaston Dombiak
 */
public class GetListGroupUsers extends AdHocCommand {
    @Override
    protected void addStageInformation(@Nonnull final SessionData data, Element command) {
        final Locale preferredLocale = SessionManager.getInstance().getLocaleForSession(data.getOwner());

        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle(LocaleUtils.getLocalizedString("commands.admin.group.getlistgroupusers.form.title", preferredLocale));
        form.addInstruction(LocaleUtils.getLocalizedString("commands.admin.group.getlistgroupusers.form.instruction", preferredLocale));

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel(LocaleUtils.getLocalizedString("commands.admin.group.getlistgroupusers.form.field.group.label", preferredLocale));
        field.setVariable("group");
        field.setRequired(true);

        // Add the form to the command
        command.add(form.getElement());
    }

    @Override
    public void execute(@Nonnull final SessionData data, Element command) {
        final Locale preferredLocale = SessionManager.getInstance().getLocaleForSession(data.getOwner());

        Group group;
        try {
            group = GroupManager.getInstance().getGroup(data.getData().get("group").get(0));
        } catch (GroupNotFoundException e) {
            // Group not found
            Element note = command.addElement("note");
            note.addAttribute("type", "error");
            note.setText(LocaleUtils.getLocalizedString("commands.admin.group.getlistgroupusers.note.group-does-not-exist", preferredLocale));
            return;
        }

        DataForm form = new DataForm(DataForm.Type.result);

        form.addReportedField("jid", LocaleUtils.getLocalizedString("commands.admin.group.getlistgroupusers.form.reportedfield.jid.label", preferredLocale), FormField.Type.jid_single);
        form.addReportedField("admin", LocaleUtils.getLocalizedString("commands.admin.group.getlistgroupusers.form.reportedfield.admin.label", preferredLocale), FormField.Type.boolean_type);

        // Add group members the result
        for (JID memberJID : group.getMembers()) {
            Map<String,Object> fields = new HashMap<>();
            fields.put("jid", memberJID.toString());
            fields.put("admin", false);
            form.addItemFields(fields);
        }
        // Add group admins the result
        for (JID memberJID : group.getAdmins()) {
            Map<String,Object> fields = new HashMap<>();
            fields.put("jid", memberJID.toString());
            fields.put("admin", true);
            form.addItemFields(fields);
        }
        command.add(form.getElement());
    }

    @Override
    public String getCode() {
        return "http://jabber.org/protocol/admin#get-group-members";
    }

    @Override
    public String getDefaultLabel() {
        return LocaleUtils.getLocalizedString("commands.admin.group.getlistgroupusers.label");
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
