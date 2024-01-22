/*
 * Copyright (C) 2024 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.commands.admin;

import org.dom4j.Element;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.admin.AdminManager;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.server.RemoteServerConfiguration;
import org.jivesoftware.openfire.server.RemoteServerManager;
import org.jivesoftware.util.LocaleUtils;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Edits the list of Openfire administrators
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @see <a href="https://xmpp.org/extensions/xep-0133.html#edit-admin">XEP-0133 Service Administration: Edit Admin List</a>
 */
public class EditAdminList extends AdHocCommand
{
    @Override
    public String getCode() {
        return "http://jabber.org/protocol/admin#edit-admin";
    }

    @Override
    public String getDefaultLabel() {
        return LocaleUtils.getLocalizedString("commands.admin.editadminlist.label");
    }

    @Override
    public int getMaxStages(@Nonnull final SessionData data) {
        return 1;
    }

    @Override
    public void execute(@Nonnull SessionData sessionData, Element command)
    {
        final Locale preferredLocale = SessionManager.getInstance().getLocaleForSession(sessionData.getOwner());
        Element note = command.addElement("note");

        Map<String, List<String>> data = sessionData.getData();

        boolean requestError = false;
        final List<JID> listed = new ArrayList<>();
        for ( final String adminjid : data.get( "adminjids" ))
        {
            try
            {
                listed.add(new JID(adminjid));
            }
            catch (IllegalArgumentException npe)
            {
                note.addAttribute( "type", "error" );
                note.setText(LocaleUtils.getLocalizedString("commands.admin.editadminlist.note.jid-invalid", preferredLocale));
                requestError = true;
            }
        }

        if ( requestError )
        {
            // We've collected all errors. Return without applying changes.
            return;
        }

        // No errors.
        AdminManager.getInstance().setAdminJIDs(listed);

        // Answer that the operation was successful
        note.addAttribute("type", "info");
        note.setText(LocaleUtils.getLocalizedString("commands.global.operation.finished.success", preferredLocale));
    }

    @Override
    protected void addStageInformation(@Nonnull final SessionData data, Element command) {
        final Locale preferredLocale = SessionManager.getInstance().getLocaleForSession(data.getOwner());

        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle(LocaleUtils.getLocalizedString("commands.admin.editadminlist.form.title", preferredLocale));
        form.addInstruction(LocaleUtils.getLocalizedString("commands.admin.editadminlist.form.instruction", preferredLocale));

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.jid_multi);
        field.setLabel(LocaleUtils.getLocalizedString("commands.admin.editadminlist.form.field.adminjids.label", preferredLocale));
        field.setVariable("adminjids");
        field.setRequired(true);
        for (final JID admin : AdminManager.getInstance().getAdminAccounts()) {
            field.addValue(admin);
        }

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
