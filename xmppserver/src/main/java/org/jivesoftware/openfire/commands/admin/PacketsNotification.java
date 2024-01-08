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
import org.jivesoftware.openfire.interceptor.PacketCopier;
import org.jivesoftware.util.LocaleUtils;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Command that allows to retrieve the presence of all active users.
 *
 * @author Gaston Dombiak
 *
 * TODO Create command for removing subscriptions. Subscriptions will now be removed when component disconnects.
 */
public class PacketsNotification extends AdHocCommand {

    @Override
    protected void addStageInformation(@Nonnull final SessionData data, Element command) {
        final Locale preferredLocale = SessionManager.getInstance().getLocaleForSession(data.getOwner());

        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle(LocaleUtils.getLocalizedString("commands.admin.packetsnotification.form.title", preferredLocale));
        form.addInstruction(LocaleUtils.getLocalizedString("commands.admin.packetsnotification.form.instruction", preferredLocale));

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.list_multi);
        field.setLabel(LocaleUtils.getLocalizedString("commands.admin.packetsnotification.form.field.packet_type.label", preferredLocale));
        field.setVariable("packet_type");
        field.addOption(LocaleUtils.getLocalizedString("commands.admin.packetsnotification.form.field.packet_type.option.presence.label", preferredLocale), "presence");
        field.addOption(LocaleUtils.getLocalizedString("commands.admin.packetsnotification.form.field.packet_type.option.iq.label", preferredLocale), "iq");
        field.addOption(LocaleUtils.getLocalizedString("commands.admin.packetsnotification.form.field.packet_type.option.message.label", preferredLocale), "message");
        field.setRequired(true);

        field = form.addField();
        field.setType(FormField.Type.list_single);
        field.setLabel(LocaleUtils.getLocalizedString("commands.admin.packetsnotification.form.field.direction.label", preferredLocale));
        field.setVariable("direction");
        field.addOption(LocaleUtils.getLocalizedString("commands.admin.packetsnotification.form.field.direction.option.incoming.label", preferredLocale), "incoming");
        field.addOption(LocaleUtils.getLocalizedString("commands.admin.packetsnotification.form.field.direction.option.outgoing.label", preferredLocale), "outgoing");
        field.setRequired(true);

        field = form.addField();
        field.setType(FormField.Type.list_single);
        field.setLabel(LocaleUtils.getLocalizedString("commands.admin.packetsnotification.form.field.processed.label", preferredLocale));
        field.setVariable("processed");
        field.addOption(LocaleUtils.getLocalizedString("commands.admin.packetsnotification.form.field.processed.option.false.label", preferredLocale), "false");
        field.addOption(LocaleUtils.getLocalizedString("commands.admin.packetsnotification.form.field.processed.option.true.label", preferredLocale), "true");
        field.setRequired(true);

        // Add the form to the command
        command.add(form.getElement());
    }

    @Override
    public void execute(@Nonnull final SessionData data, Element command) {
        final Locale preferredLocale = SessionManager.getInstance().getLocaleForSession(data.getOwner());

        boolean presenceEnabled = false;
        boolean messageEnabled = false;
        boolean iqEnabled = false;
        for (String packet_type : data.getData().get("packet_type")) {
            if ("presence".equals(packet_type)) {
                presenceEnabled = true;
            }
            else if ("iq".equals(packet_type)) {
                iqEnabled = true;
            }
            else if ("message".equals(packet_type)) {
                messageEnabled = true;
            }
        }

        boolean incoming = "incoming".equals(data.getData().get("direction").get(0));
        boolean processed = "true".equals(data.getData().get("processed").get(0));

        JID componentJID = data.getOwner();
        // Create or update subscription of the component to receive packet notifications
        PacketCopier.getInstance()
                .addSubscriber(componentJID, iqEnabled, messageEnabled, presenceEnabled, incoming, processed);

        // Inform that everything went fine
        Element note = command.addElement("note");
        note.addAttribute("type", "info");
        note.setText(LocaleUtils.getLocalizedString("commands.global.operation.finished.success", preferredLocale));
    }

    @Override
    public String getCode() {
        return "http://jabber.org/protocol/admin#packets_notification";
    }

    @Override
    public String getDefaultLabel() {
        return LocaleUtils.getLocalizedString("commands.admin.packetsnotification.label");
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
     * Returns if the requester can access this command. Only components are allowed to
     * execute this command.
     *
     * @param requester the JID of the user requesting to execute this command.
     * @return true if the requester can access this command.
     */
    @Override
    public boolean hasPermission(JID requester) {
        return InternalComponentManager.getInstance().hasComponent(requester);
    }
}
