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

package org.jivesoftware.openfire.commands.admin;

import org.dom4j.Element;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.util.LocaleUtils;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Command that allows to retrieve the presence of all active users.
 *
 * @author Gaston Dombiak
 */
public class GetUsersPresence extends AdHocCommand {

    @Override
    protected void addStageInformation(@Nonnull final SessionData data, Element command) {
        final Locale preferredLocale = SessionManager.getInstance().getLocaleForSession(data.getOwner());

        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle(LocaleUtils.getLocalizedString("commands.admin.getuserspresence.form.title", preferredLocale));
        form.addInstruction(LocaleUtils.getLocalizedString("commands.admin.getuserspresence.form.instruction", preferredLocale));

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.list_single);
        field.setLabel(LocaleUtils.getLocalizedString("commands.global.operation.pagination.max_items", preferredLocale));
        field.setVariable("max_items");
        field.addOption("25", "25");
        field.addOption("50", "50");
        field.addOption("75", "75");
        field.addOption("100", "100");
        field.addOption("150", "150");
        field.addOption("200", "200");
        field.addOption(LocaleUtils.getLocalizedString("commands.global.operation.pagination.none", preferredLocale), "none");

        // Add the form to the command
        command.add(form.getElement());
    }

    @Override
    public void execute(@Nonnull final SessionData data, Element command) {
        final Locale preferredLocale = SessionManager.getInstance().getLocaleForSession(data.getOwner());

        int maxItems = -1;
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

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.text_multi);
        field.setLabel(LocaleUtils.getLocalizedString("commands.admin.getuserspresence.form.field.activeuserpresences.label", preferredLocale));
        field.setVariable("activeuserpresences");

        // Get list of users (i.e. bareJIDs) that are connected to the server
        Collection<ClientSession> sessions = SessionManager.getInstance().getSessions();
        int index = 1;
        for (ClientSession session : sessions) {
            if (session.getPresence().isAvailable()) {
                field.addValue(session.getPresence().toXML());
            }
            if (maxItems > 0 && index >= maxItems) {
                break;
            }
        }
        command.add(form.getElement());
    }

    @Override
    public String getCode() {
        return "http://jabber.org/protocol/admin#get-active-presences";
    }

    @Override
    public String getDefaultLabel() {
        return LocaleUtils.getLocalizedString("commands.admin.getuserspresence.label");
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
    public int getMaxStages(@Nonnull final SessionData data) {
        return 1;
    }

    /**
     * Returns if the requester can access this command. Admins and components are allowed to
     * execute this command.
     *
     * @param requester the JID of the entity requesting to execute this command.
     * @return true if the requester can access this command.
     */
    @Override
    public boolean hasPermission(JID requester) {
        return super.hasPermission(requester) || InternalComponentManager.getInstance().hasComponent(requester);
    }
}
