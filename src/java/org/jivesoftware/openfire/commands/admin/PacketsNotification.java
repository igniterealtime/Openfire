/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.commands.admin;

import org.dom4j.Element;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.interceptor.PacketCopier;
import org.xmpp.component.Component;
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
        Component component = InternalComponentManager.getInstance().getComponent(componentJID);
        // Create or update subscription of the component to receive packet notifications
        PacketCopier.getInstance().addSubscriber(componentJID, component, iqEnabled,
                messageEnabled, presenceEnabled, incoming, processed);

        // Inform that everything went fine
        Element note = command.addElement("note");
        note.addAttribute("type", "info");
        note.setText("Operation finished successfully");
    }

    public String getCode() {
        return "http://jabber.org/protocol/admin#packets_notification";
    }

    public String getDefaultLabel() {
        return "Get notifications of packet activity";
    }

    protected List<Action> getActions(SessionData data) {
        return Arrays.asList(Action.complete);
    }

    protected Action getExecuteAction(SessionData data) {
        return Action.complete;
    }

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
    public boolean hasPermission(JID requester) {
        return InternalComponentManager.getInstance().getComponent(requester) != null;
    }
}
