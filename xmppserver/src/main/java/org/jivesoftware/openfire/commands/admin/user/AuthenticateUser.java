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
package org.jivesoftware.openfire.commands.admin.user;

import org.dom4j.Element;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.auth.ConnectionException;
import org.jivesoftware.openfire.auth.InternalUnauthenticatedException;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.LocaleUtils;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Takes a user's username and password to authenticate them against the Openfire authprovider.
 *
 * @author Alexander Wenckus
 */
public class AuthenticateUser extends AdHocCommand {
    @Override
    public String getCode() {
        return "http://jabber.org/protocol/admin#authenticate-user";
    }

    @Override
    public String getDefaultLabel() {
        return LocaleUtils.getLocalizedString("commands.admin.user.authenticateuser.label");
    }

    @Override
    public int getMaxStages(@Nonnull final SessionData data) {
        return 1;
    }

    @Override
    public void execute(@Nonnull final SessionData data, Element command) {
        final Locale preferredLocale = SessionManager.getInstance().getLocaleForSession(data.getOwner());

        Element note = command.addElement("note");
        JID account;
        try {
            account = new JID(data.getData().get("accountjid").get(0));
        }
        catch (IllegalArgumentException e) {
            note.addAttribute("type", "error");
            note.setText(LocaleUtils.getLocalizedString("commands.admin.user.authenticateuser.note.jid-invalid", preferredLocale));
            return;
        }
        catch (NullPointerException ne) {
            note.addAttribute("type", "error");
            note.setText(LocaleUtils.getLocalizedString("commands.admin.user.authenticateuser.note.jid-required", preferredLocale));
            return;
        }
        if (!XMPPServer.getInstance().isLocal(account)) {
            note.addAttribute("type", "error");
            note.setText(LocaleUtils.getLocalizedString("commands.admin.user.authenticateuser.note.jid-not-local", preferredLocale));
            return;
        }
        String password = data.getData().get("password").get(0);
        // Get requested user
        User user;
        try {
            user = UserManager.getInstance().getUser(account.getNode());
        }
        catch (UserNotFoundException e) {
            // User not found
            note.addAttribute("type", "error");
            note.setText(LocaleUtils.getLocalizedString("commands.admin.user.authenticateuser.note.user-does-not-exist", preferredLocale));
            return;
        }

        try {
            AuthFactory.authenticate(user.getUsername(), password);
        }
        catch (UnauthorizedException | ConnectionException | InternalUnauthenticatedException e) {
            // Auth failed
            note.addAttribute("type", "error");
            note.setText(LocaleUtils.getLocalizedString("commands.admin.user.authenticateuser.note.authentication-failed", preferredLocale));
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
        form.setTitle(LocaleUtils.getLocalizedString("commands.admin.user.authenticateuser.form.title", preferredLocale));
        form.addInstruction(LocaleUtils.getLocalizedString("commands.admin.user.authenticateuser.form.instruction", preferredLocale));

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.jid_single);
        field.setLabel(LocaleUtils.getLocalizedString("commands.admin.user.authenticateuser.form.field.accountjid.label", preferredLocale));
        field.setVariable("accountjid");
        field.setRequired(true);

        field = form.addField();
        field.setType(FormField.Type.text_private);
        field.setLabel(LocaleUtils.getLocalizedString("commands.admin.user.authenticateuser.form.field.password.label", preferredLocale));
        field.setVariable("password");
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
        return AdHocCommand.Action.complete;
    }

    @Override
    public boolean hasPermission(JID requester) {
        return super.hasPermission(requester) || InternalComponentManager.getInstance().hasComponent(requester);
    }
}
