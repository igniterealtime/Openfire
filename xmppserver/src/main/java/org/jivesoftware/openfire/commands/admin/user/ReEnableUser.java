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
package org.jivesoftware.openfire.commands.admin.user;

import org.dom4j.Element;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.lockout.LockOutManager;
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
 * Re-Enables a user.
 *
 * The implementation uses Openfire's LockOutManager to apply the configuration state.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @see <a href="https://xmpp.org/extensions/xep-0133.html#reenable-user">XEP-0133 Service Administration: Re-Enable User</a>
 * @see LockOutManager
 */
public class ReEnableUser extends AdHocCommand
{
    @Override
    public String getCode() {
        return "http://jabber.org/protocol/admin#reenable-user";
    }

    @Override
    public String getDefaultLabel() {
        return LocaleUtils.getLocalizedString("commands.admin.user.reenableuser.label");
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

        // Check if locks can be set (backend is read-only)
        if (LockOutManager.getLockOutProvider().isReadOnly()) {
            note.addAttribute("type", "error");
            note.setText(LocaleUtils.getLocalizedString("commands.admin.user.reenableuser.note.lockout-readonly", preferredLocale));
            return;
        }

        Map<String, List<String>> data = sessionData.getData();

        // Let's create the jids and check that they are a local user
        boolean requestError = false;
        final List<User> users = new ArrayList<>();
        for ( final String accountjid : data.get( "accountjids" ))
        {
            JID account;
            try
            {
                account = new JID( accountjid );

                if ( !XMPPServer.getInstance().isLocal( account ) )
                {
                    note.addAttribute( "type", "error" );
                    note.setText(LocaleUtils.getLocalizedString("commands.admin.user.reenableuser.note.jid-not-local", List.of(accountjid), preferredLocale));
                    requestError = true;
                }
                else
                {
                    User user = UserManager.getInstance().getUser( account.getNode() );
                    users.add( user );
                }
            }
            catch ( NullPointerException npe )
            {
                note.addAttribute( "type", "error" );
                note.setText(LocaleUtils.getLocalizedString("commands.admin.user.reenableuser.note.jid-required", preferredLocale));
                requestError = true;
            }
            catch (IllegalArgumentException npe)
            {
                note.addAttribute( "type", "error" );
                note.setText(LocaleUtils.getLocalizedString("commands.admin.user.reenableuser.note.jid-invalid", preferredLocale));
                requestError = true;
            }
            catch ( UserNotFoundException e )
            {
                note.addAttribute( "type", "error" );
                note.setText(LocaleUtils.getLocalizedString("commands.admin.user.reenableuser.note.user-does-not-exist", List.of(accountjid), preferredLocale));
                requestError = true;
            }
        }

        if ( requestError )
        {
            // We've collected all errors. Return without applying changes.
            return;
        }

        // No errors. Re-Enable all users.
        for (final User user : users) {
            LockOutManager.getInstance().enableAccount(user.getUsername());
        }

        // Answer that the operation was successful
        note.addAttribute("type", "info");
        note.setText(LocaleUtils.getLocalizedString("commands.global.operation.finished.success", preferredLocale));
    }

    @Override
    protected void addStageInformation(@Nonnull final SessionData data, Element command) {
        final Locale preferredLocale = SessionManager.getInstance().getLocaleForSession(data.getOwner());

        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle(LocaleUtils.getLocalizedString("commands.admin.user.reenableuser.form.title", preferredLocale));
        form.addInstruction(LocaleUtils.getLocalizedString("commands.admin.user.reenableuser.form.instruction", preferredLocale));

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.jid_multi);
        field.setLabel(LocaleUtils.getLocalizedString("commands.admin.user.reenableuser.form.field.accountjid.label", preferredLocale));
        field.setVariable("accountjids");
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
        return (super.hasPermission(requester) || InternalComponentManager.getInstance().hasComponent(requester))
                && !LockOutManager.getLockOutProvider().isReadOnly();
    }
}
