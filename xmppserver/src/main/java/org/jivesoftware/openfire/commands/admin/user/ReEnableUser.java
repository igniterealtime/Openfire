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
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.lockout.LockOutManager;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Re-Enables a user.
 *
 * The implementation uses Openfire's LockOutManager to apply the configuration state.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @see <a href="https://xmpp.org/extensions/xep-0133.html#reenable-user">XEP-0133 Service Administration: Re-Enable User</a>
 * @see LockOutManager
 */
// TODO Use i18n
public class ReEnableUser extends AdHocCommand
{
    @Override
    public String getCode() {
        return "http://jabber.org/protocol/admin#reenable-user";
    }

    @Override
    public String getDefaultLabel() {
        return "Re-Enable a User";
    }

    @Override
    public int getMaxStages(SessionData data) {
        return 1;
    }

    @Override
    public void execute(SessionData sessionData, Element command)
    {
        Element note = command.addElement("note");

        // Check if locks can be set (backend is read-only)
        if (LockOutManager.getLockOutProvider().isReadOnly()) {
            note.addAttribute("type", "error");
            note.setText("LockOut provider is read only. Users cannot be re-enabled.");
            return;
        }

        Map<String, List<String>> data = sessionData.getData();

        // Let's create the jids and check that they are a local user
        boolean requestError = false;
        final List<User> users = new ArrayList<>();
        for ( final String accountjid : data.get( "accountjids" ))
        {
            JID account;
            try
            {
                account = new JID( accountjid );

                if ( !XMPPServer.getInstance().isLocal( account ) )
                {
                    note.addAttribute( "type", "error" );
                    note.setText( "Cannot re-enable remote user: " + accountjid );
                    requestError = true;
                }
                else
                {
                    User user = UserManager.getInstance().getUser( account.getNode() );
                    users.add( user );
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

        if ( requestError )
        {
            // We've collected all errors. Return without applying changes.
            return;
        }

        // No errors. Re-Enable all users.
        for (final User user : users) {
            LockOutManager.getInstance().enableAccount(user.getUsername());
        }

        // Answer that the operation was successful
        note.addAttribute("type", "info");
        note.setText("Operation finished successfully");
    }

    @Override
    protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle("Re-enable one or more users");
        form.addInstruction("Fill out this form to re-enable one or more users.");

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.jid_multi);
        field.setLabel("The Jabber ID(s) to re-enable");
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
                && !LockOutManager.getLockOutProvider().isReadOnly();
    }
}
