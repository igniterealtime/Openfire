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
package org.jivesoftware.openfire.commands.event;

import org.dom4j.Element;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.vcard.VCardEventDispatcher;
import org.jivesoftware.openfire.vcard.VCardManager;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Notifies the that a vCard was modified. It can be used by user providers to notify Openfire of the
 * modification of a vCard.
 *
 * @author Gabriel Guarincerri
 */
public class VCardModified extends AdHocCommand {
    @Override
	public String getCode() {
        return "http://jabber.org/protocol/event#vcard-modified";
    }

    @Override
	public String getDefaultLabel() {
        return "VCard modified";
    }

    @Override
	public int getMaxStages(SessionData data) {
        return 1;
    }

    @Override
	public void execute(SessionData sessionData, Element command) {
        Element note = command.addElement("note");

        Map<String, List<String>> data = sessionData.getData();

        // Get the username
        String username;
        try {
            username = get(data, "username", 0);
        }
        catch (NullPointerException npe) {
            note.addAttribute("type", "error");
            note.setText("Username required parameter.");
            return;
        }

        // Loads the updated vCard
        Element vCard = VCardManager.getProvider().loadVCard(username);

        if (vCard == null) {
            note.addAttribute("type", "error");
            note.setText("VCard not found.");
            return;
        }

        // Fire event.
        VCardEventDispatcher.dispatchVCardUpdated(username, vCard);

        // Answer that the operation was successful
        note.addAttribute("type", "info");
        note.setText("Operation finished successfully");
    }

    @Override
	protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle("Dispatching a vCard updated event.");
        form.addInstruction("Fill out this form to dispatch a vCard updated event.");

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel("The username of the user who's vCard was updated");
        field.setVariable("username");
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
        return super.hasPermission(requester) || InternalComponentManager.getInstance().hasComponent(requester);
    }
}