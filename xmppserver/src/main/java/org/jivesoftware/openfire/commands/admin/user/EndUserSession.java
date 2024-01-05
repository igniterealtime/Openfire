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
package org.jivesoftware.openfire.commands.admin.user;

import org.dom4j.Element;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.session.ClientSession;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.util.*;

/**
 * End a user session
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @see <a href="https://xmpp.org/extensions/xep-0133.html#end-user-session">XEP-0133 Service Administration: End User Session</a>
 */
// TODO Use i18n
public class EndUserSession extends AdHocCommand
{
    @Override
    public String getCode() {
        return "http://jabber.org/protocol/admin#end-user-session";
    }

    @Override
    public String getDefaultLabel() {
        return "End User Session";
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

        // Let's create the jids and check that they are a local user
        boolean requestError = false;
        final List<JID> addresses = new ArrayList<>();
        for ( final String accountjid : data.get( "accountjids" ))
        {
            JID address;
            try
            {
                address = new JID( accountjid );

                if ( !XMPPServer.getInstance().isLocal( address ) )
                {
                    note.addAttribute( "type", "error" );
                    note.setText( "Cannot end session of remote user: " + accountjid );
                    requestError = true;
                } else {
                    addresses.add(address);
                }
            }
            catch ( NullPointerException npe )
            {
                note.addAttribute( "type", "error" );
                note.setText( "JID required parameter." );
                requestError = true;
            }
            catch (IllegalArgumentException npe)
            {
                note.addAttribute( "type", "error" );
                note.setText( "Invalid values were provided. Please provide one or more valid JIDs." );
                requestError = true;
            }
        }

        if ( requestError )
        {
            // We've collected all errors. Return without applying changes.
            return;
        }

        // No errors. Disable all users.
        for (final JID address : addresses) {
            // Note: If the JID is of the form <user@host>, the service MUST end all of the user's sessions; if the JID
            // is of the form <user@host/resource>, the service MUST end only the session associated with that resource.
            final Collection<ClientSession> sessions = new HashSet<>();
            if (address.getResource() != null) {
                // Full JID: only close this session.
                sessions.add(SessionManager.getInstance().getSession(address));
            } else {
                // Bare JID: close all sessions for the user.
                sessions.addAll(SessionManager.getInstance().getSessions(address.getNode()));
            }

            for (final ClientSession session : sessions) {
                session.close();
            }
        }

        // Answer that the operation was successful
        note.addAttribute("type", "info");
        note.setText("Operation finished successfully");
    }

    @Override
    protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle("Ending a User Session");
        form.addInstruction("Fill out this form to end a user's session.");

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.jid_multi);
        field.setLabel("The Jabber ID(s) for which to end sessions");
        field.setVariable("accountjids");
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

    @Override
    public boolean hasPermission(JID requester) {
        return super.hasPermission(requester) || InternalComponentManager.getInstance().hasComponent(requester);
    }
}
