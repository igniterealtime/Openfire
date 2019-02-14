/*
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
package org.jivesoftware.openfire.commands.admin.user;

import org.dom4j.Element;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.component.InternalComponentManager;
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
 * Delete a user from Openfire if the provider is not read-only. See
 * <a href="http://www.xmpp.org/extensions/xep-0133.html#delete-user">Service Administration:
 * Delete User</a>
 *
 * @author John Georgiadis
 */
public class DeleteUser extends AdHocCommand {
    @Override
    public String getCode() {
        return "http://jabber.org/protocol/admin#delete-user";
    }

    @Override
    public String getDefaultLabel() {
        return "Delete a User";
    }

    @Override
    public int getMaxStages(SessionData data) {
        return 1;
    }

    @Override
    public void execute(SessionData sessionData, Element command) {
        Element note = command.addElement("note");
        // Check if users cannot be modified (backend is read-only)
        if (UserManager.getUserProvider().isReadOnly()) {
            note.addAttribute("type", "error");
            note.setText("User provider is read only. Users cannot be deleted.");
            return;
        }
        Map<String, List<String>> data = sessionData.getData();

        // Let's create the jids and check that they are a local user
        boolean requestError = false;
        final List<User> toDelete = new ArrayList<>();
        for ( final String accountjid : data.get( "accountjids" ))
        {
            JID account;
            try
            {
                account = new JID( accountjid );

                if ( !XMPPServer.getInstance().isLocal( account ) )
                {
                    note.addAttribute( "type", "error" );
                    note.setText( "Cannot delete remote user: " + accountjid );
                    requestError = true;
                }
                else
                {
                    User user = UserManager.getInstance().getUser( account.getNode() );
                    toDelete.add( user );
                }
            }
            catch ( NullPointerException npe )
            {
                note.addAttribute( "type", "error" );
                note.setText( "JID required parameter." );
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
            // We've collected all errors. Return without deleting anything.
            return;
        }

        // No errors. Delete all users.
        for ( final User user : toDelete ) {
            UserManager.getInstance().deleteUser(user);
        }

        // Answer that the operation was successful
        note.addAttribute("type", "info");
        note.setText("Operation finished successfully");
    }

    @Override
    protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle("Deleting one or more users");
        form.addInstruction("Fill out this form to delete users.");

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.jid_multi);
        field.setLabel("The Jabber ID(s) for the account to be deleted");
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
    protected AdHocCommand.Action getExecuteAction(SessionData data) {
        return AdHocCommand.Action.complete;
    }

    @Override
    public boolean hasPermission(JID requester) {
        return (super.hasPermission(requester) || InternalComponentManager.getInstance().hasComponent(requester))
                && !UserManager.getUserProvider().isReadOnly();
    }
}
