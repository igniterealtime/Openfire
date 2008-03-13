/**
 * $RCSfile:  $
 * $Revision:  $
 * $Date:  $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.openfire.commands.clearspace;

import org.dom4j.Element;
import org.jivesoftware.openfire.clearspace.ClearspaceManager;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Changes the shared secret between Openfire and Clearspace
 *
 * @author Gabriel Guardincerri
 */
public class ChangeSharedSecret extends AdHocCommand {
    public String getCode() {
        return "http://jabber.org/protocol/clearspace#change-sharedsecret";
    }

    public String getDefaultLabel() {
        return "Change the share secret";
    }

    public int getMaxStages(SessionData data) {
        return 1;
    }

    public void execute(SessionData sessionData, Element command) {
        Element note = command.addElement("note");

        Map<String, List<String>> data = sessionData.getData();

        // Gets the old shared secret
        String oldSharedSecret = get(data, "oldSharedSecret", 0);

        if (oldSharedSecret == null || "".equals(oldSharedSecret)) {
            note.addAttribute("type", "error");
            note.setText("Old shared secret is empty or do not match.");
            return;
        }

        // Gets the new shared secret
        String newSharedSecret = get(data, "newSharedSecret", 0);

        if (newSharedSecret == null || "".equals(newSharedSecret)) {
            note.addAttribute("type", "error");
            note.setText("New shared secret is empty or do not match.");
            return;
        }

        // Checks if the old shared secret is OK
        ClearspaceManager manager = ClearspaceManager.getInstance();
        if (!manager.getSharedSecret().equals(oldSharedSecret)) {
            note.addAttribute("type", "error");
            note.setText("Old shared secret is not valid.");
            return;
        }

        // Sets the new shared secret
        ClearspaceManager.getInstance().setSharedSecret(newSharedSecret);

        // Answer that the operation was successful
        note.addAttribute("type", "info");
        note.setText("Operation finished successfully");
    }

    protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle("Changing the share secret");
        form.addInstruction("Fill out this form to change the shared secret.");

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.text_private);
        field.setLabel("The old shared secret");
        field.setVariable("oldSharedSecret");
        field.setRequired(true);

        field = form.addField();
        field.setType(FormField.Type.text_private);
        field.setLabel("The new shared secret");
        field.setVariable("newSharedSecret");
        field.setRequired(true);

        // Add the form to the command
        command.add(form.getElement());
    }

    private String get(Map<String, List<String>> data, String key, int value) {
        List<String> list = data.get(key);
        if (list == null) {
            return null;
        }
        else {
            return list.get(value);
        }
    }

    protected List<Action> getActions(SessionData data) {
        return Arrays.asList(Action.complete);
    }

    protected Action getExecuteAction(SessionData data) {
        return Action.complete;
    }

    public boolean hasPermission(JID requester) {
        return (super.hasPermission(requester) || InternalComponentManager.getInstance().hasComponent(requester));
    }
}