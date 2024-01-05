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
import org.dom4j.QName;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.roster.RosterManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gets the roster of a user
 *
 * The implementation uses Openfire's LockOutManager to apply the configuration state.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @see <a href="https://xmpp.org/extensions/xep-0133.html#get-user-roster">XEP-0133 Service Administration: Get User Roster</a>
 */
// TODO Use i18n
public class GetUserRoster extends AdHocCommand
{
    @Override
    public String getCode() {
        return "http://jabber.org/protocol/admin#get-user-roster";
    }

    @Override
    public String getDefaultLabel() {
        return "Get User Roster";
    }

    @Override
    public int getMaxStages(SessionData data) {
        return 1;
    }

    @Override
    public void execute(SessionData sessionData, Element command)
    {
        Element note = command.addElement("note");

        // Check if rosters are enabled
        if (!RosterManager.isRosterServiceEnabled()) {
            note.addAttribute("type", "error");
            note.setText("Roster service is disabled.");
            return;
        }

        Map<String, List<String>> data = sessionData.getData();

        boolean requestError = false;
        Map<JID, Roster> rosters = new HashMap<>();
        final RosterManager rosterManager = XMPPServer.getInstance().getRosterManager();
        for ( final String accountjid : data.get( "accountjids" ))
        {
            JID account;
            try
            {
                account = new JID( accountjid );

                if ( !XMPPServer.getInstance().isLocal( account ) )
                {
                    note.addAttribute( "type", "error" );
                    note.setText( "Cannot obtain roster for: " + accountjid );
                    requestError = true;
                }
                else
                {
                    rosters.put(account, rosterManager.getRoster(account.getNode()));
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
            catch ( UserNotFoundException e )
            {
                note.addAttribute( "type", "error" );
                note.setText( "User not found: " + accountjid );
                requestError = true;
            }
        }

        if (rosters.size() > 1) {
            note.addAttribute( "type", "error" );
            note.setText( "Unable to return rosters for more than one user at a time." );
            requestError = true;
        }

        if ( requestError )
        {
            // We've collected all errors. Return without applying changes.
            return;
        }

        // No errors.
        for (final Map.Entry<JID, Roster> entry : rosters.entrySet()) {
            final JID owner = entry.getKey();
            final Roster roster = entry.getValue();

            DataForm form = new DataForm(DataForm.Type.result);

            FormField field = form.addField();
            field.setType(FormField.Type.hidden);
            field.setVariable("FORM_TYPE");
            field.addValue("http://jabber.org/protocol/admin");

            field = form.addField();
            field.setType(FormField.Type.jid_multi);
            field.setVariable("accountjids");
            field.addValue(owner);

            final Element rosterElement = roster.getReset().getElement()
                .element(QName.get("query", "jabber:iq:roster"));

            if (rosterElement != null) {
                form.getElement().add(rosterElement);
            }

            command.add(form.getElement());
        }

        // Answer that the operation was successful
        note.addAttribute("type", "info");
        note.setText("Operation finished successfully");
    }

    @Override
    protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle("Get user roster");
        form.addInstruction("Fill out this form to get the roster of a user.");

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.jid_multi);
        field.setLabel("The Jabber ID of the user for which to retrieve the roster.");
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
        return (super.hasPermission(requester) || InternalComponentManager.getInstance().hasComponent(requester))
                && RosterManager.isRosterServiceEnabled();
    }
}
