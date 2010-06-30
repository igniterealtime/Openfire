/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.interceptor.PacketCopier;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.util.Arrays;
import java.util.List;

/**
 * Command that allows to retrieve the presence of all active users.
 *
 * @author Gaston Dombiak
 *
 * TODO Use i18n
 * TODO Create command for removing subscriptions. Subscriptions will now be removed when component disconnects.
 */
public class PacketsNotification extends AdHocCommand {

    @Override
	protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle("Receiving notification of packets activity");
        form.addInstruction("Fill out this form to configure packets to receive.");

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.list_multi);
        field.setLabel("Type of packet");
        field.setVariable("packet_type");
        field.addOption("Presence", "presence");
        field.addOption("IQ", "iq");
        field.addOption("Message", "message");
        field.setRequired(true);

        field = form.addField();
        field.setType(FormField.Type.list_single);
        field.setLabel("Direction");
        field.setVariable("direction");
        field.addOption("Incoming", "incoming");
        field.addOption("Outgoing", "outgoing");
        field.setRequired(true);

        field = form.addField();
        field.setType(FormField.Type.list_single);
        field.setLabel("Processing time");
        field.setVariable("processed");
        field.addOption("Before processing", "false");
        field.addOption("After processing", "true");
        field.setRequired(true);

        // Add the form to the command
        command.add(form.getElement());
    }

    @Override
	public void execute(SessionData data, Element command) {
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
        note.setText("Operation finished successfully");
    }

    @Override
	public String getCode() {
        return "http://jabber.org/protocol/admin#packets_notification";
    }

    @Override
	public String getDefaultLabel() {
        return "Get notifications of packet activity";
    }

    @Override
	protected List<Action> getActions(SessionData data) {
        return Arrays.asList(Action.complete);
    }

    @Override
	protected Action getExecuteAction(SessionData data) {
        return Action.complete;
    }

    @Override
	public int getMaxStages(SessionData data) {
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
