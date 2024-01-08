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
 * Edits the list of external domains that are allowed to connect to Openfire
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @see <a href="https://xmpp.org/extensions/xep-0133.html#edit-whitelist">XEP-0133 Service Administration: Edit Whitelist</a>
 */
public class EditAllowedList extends AdHocCommand
{
    @Override
    public String getCode() {
        return "http://jabber.org/protocol/admin#edit-whitelist";
    }

    @Override
    public String getDefaultLabel() {
        return LocaleUtils.getLocalizedString("commands.admin.editallowedlist.label");
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
        final List<String> listed = new ArrayList<>();
        for ( final String whitelistjid : data.get( "whitelistjids" ))
        {
            JID domain;
            try
            {
                domain = new JID( whitelistjid );
                if (domain.getResource() != null || domain.getNode() != null) {
                    note.addAttribute( "type", "error" );
                    note.setText(LocaleUtils.getLocalizedString("commands.admin.editallowedlist.note.jid-domain-required", List.of(whitelistjid), preferredLocale));
                    requestError = true;
                }

                listed.add(domain.getDomain());
            }
            catch ( NullPointerException npe )
            {
                note.addAttribute( "type", "error" );
                note.setText(LocaleUtils.getLocalizedString("commands.admin.editallowedlist.note.jid-required", preferredLocale));
                requestError = true;
            }
            catch (IllegalArgumentException npe)
            {
                note.addAttribute( "type", "error" );
                note.setText(LocaleUtils.getLocalizedString("commands.admin.editallowedlist.note.jid-invalid", preferredLocale));
                requestError = true;
            }
        }

        if ( requestError )
        {
            // We've collected all errors. Return without applying changes.
            return;
        }

        // No errors.
        final Collection<RemoteServerConfiguration> allowedServers = RemoteServerManager.getAllowedServers();
        for (final String domain : listed) {
            if (allowedServers.stream().noneMatch(s -> s.getDomain().equals(domain))) {
                final RemoteServerConfiguration configuration = new RemoteServerConfiguration( domain );
                configuration.setPermission( RemoteServerConfiguration.Permission.allowed );
                RemoteServerManager.allowAccess(configuration);
            }
        }
        for (final RemoteServerConfiguration allowedServer : allowedServers) {
            if ( !listed.contains(allowedServer.getDomain())) {
                // No longer on the list.
                RemoteServerManager.deleteConfiguration(allowedServer.getDomain());
            }
        }

        // Answer that the operation was successful
        note.addAttribute("type", "info");
        note.setText(LocaleUtils.getLocalizedString("commands.global.operation.finished.success", preferredLocale));
    }

    @Override
    protected void addStageInformation(@Nonnull final SessionData data, Element command) {
        final Locale preferredLocale = SessionManager.getInstance().getLocaleForSession(data.getOwner());

        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle(LocaleUtils.getLocalizedString("commands.admin.editallowedlist.form.title", preferredLocale));
        form.addInstruction(LocaleUtils.getLocalizedString("commands.admin.editallowedlist.form.instruction", preferredLocale));

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.jid_multi);
        field.setLabel(LocaleUtils.getLocalizedString("commands.admin.editallowedlist.form.field.whitelistjids.label", preferredLocale));
        field.setVariable("whitelistjids");
        field.setRequired(true);
        for (RemoteServerConfiguration allowed : RemoteServerManager.getAllowedServers()) {
            field.addValue(new JID(null, allowed.getDomain(), null));
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
