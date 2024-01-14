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
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.LocaleUtils;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 *  An adhoc command to retrieve the properties of the user.
 *
 * @author Alexander Wenckus
 */
public class UserProperties extends AdHocCommand {
    @Override
    public String getCode() {
        return "http://jabber.org/protocol/admin#get-user-properties";
    }

    @Override
    public String getDefaultLabel() {
        return LocaleUtils.getLocalizedString("commands.admin.user.userproperties.label");
    }

    @Override
    public int getMaxStages(@Nonnull final SessionData data) {
        return 1;
    }

    @Override
    public void execute(@Nonnull final SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.result);

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        List<String> accounts = data.getData().get("accountjids");

        if (accounts != null && !accounts.isEmpty()) {
            populateResponseFields(form, accounts);
        }

        command.add(form.getElement());
    }

    private void populateResponseFields(DataForm form, List<String> accounts) {
        FormField jidField = form.addField();
        jidField.setType(FormField.Type.jid_multi);
        jidField.setVariable("accountjids");

        FormField emailField = form.addField();
        emailField.setType(FormField.Type.text_multi);
        emailField.setVariable("email");

        FormField nameField = form.addField();
        nameField.setType(FormField.Type.text_multi);
        nameField.setVariable("name");

        UserManager manager = UserManager.getInstance();
        for(String account : accounts) {
            User user;
            try {
                JID jid = new JID(account);
                user = manager.getUser(jid.getNode());
            }
            catch (Exception ex) {
                continue;
            }

            jidField.addValue(account);
            emailField.addValue(Objects.requireNonNullElse(user.getEmail(), ""));
            nameField.addValue(user.getName());
        }
    }

    @Override
    protected void addStageInformation(@Nonnull final SessionData data, Element command) {
        final Locale preferredLocale = SessionManager.getInstance().getLocaleForSession(data.getOwner());

        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle(LocaleUtils.getLocalizedString("commands.admin.user.userproperties.form.title", preferredLocale));
        form.addInstruction(LocaleUtils.getLocalizedString("commands.admin.user.userproperties.form.instruction", preferredLocale));

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.jid_multi);
        field.setLabel(LocaleUtils.getLocalizedString("commands.admin.user.userproperties.form.field.accountjid.label", preferredLocale));
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
    protected AdHocCommand.Action getExecuteAction(@Nonnull final SessionData data) {
        return AdHocCommand.Action.complete;
    }
}
