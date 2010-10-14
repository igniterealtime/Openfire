/**
 * $RCSfile$
 * $Revision: 3144 $
 * $Date: 2005-12-01 14:20:11 -0300 (Thu, 01 Dec 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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
    @Override
	public String getCode() {
        return "http://jabber.org/protocol/clearspace#change-sharedsecret";
    }

    @Override
	public String getDefaultLabel() {
        return "Change the share secret";
    }

    @Override
	public int getMaxStages(SessionData data) {
        return 1;
    }

    @Override
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

    @Override
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

    @Override
	protected List<Action> getActions(SessionData data) {
        return Arrays.asList(Action.complete);
    }

    @Override
	protected Action getExecuteAction(SessionData data) {
        return Action.complete;
    }

    @Override
	public boolean hasPermission(JID requester) {
        return (super.hasPermission(requester) || InternalComponentManager.getInstance().hasComponent(requester));
    }
}