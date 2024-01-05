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
package org.jivesoftware.openfire.commands.admin;

import org.dom4j.Element;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Sends a message to all online users
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @see <a href="https://xmpp.org/extensions/xep-0133.html#announce">XEP-0133 Send Announcement to Online Users</a>
 */
// TODO Use i18n
public class SendAnnouncementToOnlineUsers extends AdHocCommand
{
    @Override
    public String getCode() {
        return "http://jabber.org/protocol/admin#announce";
    }

    @Override
    public String getDefaultLabel() {
        return "Send Announcement to Online Users";
    }

    @Override
    public int getMaxStages(SessionData data) {
        return 1;
    }

    @Override
    public void execute(SessionData sessionData, Element command)
    {
        Element note = command.addElement("note");

        Map<String, List<String>> data = sessionData.getData();

        boolean requestError = false;
        final List<String> announcement = data.get("announcement");
        if (announcement == null || announcement.isEmpty()) {
            note.addAttribute( "type", "error" );
            note.setText("Please provide text for the announcement.");
            requestError = true;
        }

        if ( requestError )
        {
            // We've collected all errors. Return without applying changes.
            return;
        }

        // No errors.
        XMPPServer.getInstance().getSessionManager().sendServerMessage(null, String.join(System.lineSeparator(), announcement));

        // Answer that the operation was successful
        note.addAttribute("type", "info");
        note.setText("Operation finished successfully");
    }

    @Override
    protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle("Making an Announcement");
        form.addInstruction("Fill out this form to make an announcement to all active users of this service.");

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.text_multi);
        field.setLabel("Announcement");
        field.setVariable("announcement");
        field.setRequired(true);

        // Add the form to the command
        command.add(form.getElement());
    }

    @Override
    protected List<Action> getActions(SessionData data) {
        return Collections.singletonList(Action.complete);
    }

    @Override
    protected Action getExecuteAction(SessionData data) {
        return Action.complete;
    }
}
