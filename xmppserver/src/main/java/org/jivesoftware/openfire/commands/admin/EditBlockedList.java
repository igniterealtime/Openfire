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
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.server.RemoteServerConfiguration;
import org.jivesoftware.openfire.server.RemoteServerManager;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.util.*;

/**
 * Edits the list of external domains that are disallowed to connect to Openfire
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @see <a href="https://xmpp.org/extensions/xep-0133.html#edit-blacklist">XEP-0133 Service Administration: Edit Blacklist</a>
 */
// TODO Use i18n
public class EditBlockedList extends AdHocCommand
{
    @Override
    public String getCode() {
        return "http://jabber.org/protocol/admin#edit-blacklist";
    }

    @Override
    public String getDefaultLabel() {
        return "Edit Blocked List";
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
        final List<String> listed = new ArrayList<>();
        for ( final String blacklistjid : data.get( "blacklistjids" ))
        {
            JID domain;
            try
            {
                domain = new JID( blacklistjid );
                if (domain.getResource() != null || domain.getNode() != null) {
                    note.addAttribute( "type", "error" );
                    note.setText( "Cannot add an address that contains a node or resource part (only use domains): " + blacklistjid );
                    requestError = true;
                }

                if (XMPPServer.getInstance().isLocal(domain)) {
                    note.addAttribute( "type", "error" );
                    note.setText( "Cannot add our own address: " + blacklistjid );
                    requestError = true;
                }

                listed.add(domain.getDomain());
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

        // No errors.
        final Collection<RemoteServerConfiguration> blockedServers = RemoteServerManager.getBlockedServers();
        for (final String domain : listed) {
            if (blockedServers.stream().noneMatch(s -> s.getDomain().equals(domain))) {
                RemoteServerManager.blockAccess(domain);
            }
        }
        for (final RemoteServerConfiguration blockedServer : blockedServers) {
            if ( !listed.contains(blockedServer.getDomain())) {
                // Unblock - no longer on the list.
                RemoteServerManager.deleteConfiguration(blockedServer.getDomain());
            }
        }

        // Answer that the operation was successful
        note.addAttribute("type", "info");
        note.setText("Operation finished successfully");
    }

    @Override
    protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle("Editing the Blocked list");
        form.addInstruction("Fill out this form to edit the list of entities with whom communications are disallowed.");

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.jid_multi);
        field.setLabel("The blocked list");
        field.setVariable("blacklistjids");
        field.setRequired(true);
        for (RemoteServerConfiguration blockedServer : RemoteServerManager.getBlockedServers()) {
            field.addValue(new JID(null, blockedServer.getDomain(), null));
        }

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
