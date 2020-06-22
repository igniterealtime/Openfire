/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.pubsub;

import org.dom4j.Element;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.util.LocaleUtils;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.util.Collections;
import java.util.List;

/**
 * Ad-hoc command that sends pending subscriptions to node owners.
 *
 * @author Matt Tucker
 */
public class PendingSubscriptionsCommand extends AdHocCommand {

    private PubSubService service;

    public PendingSubscriptionsCommand(PubSubService service) {
        this.service = service;
    }

    @Override
    protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle(LocaleUtils.getLocalizedString("pubsub.command.pending-subscriptions.title"));
        form.addInstruction(
                LocaleUtils.getLocalizedString("pubsub.command.pending-subscriptions.instruction"));

        FormField formField = form.addField();
        formField.setVariable("pubsub#node");
        formField.setType(FormField.Type.list_single);
        formField.setLabel(
                LocaleUtils.getLocalizedString("pubsub.command.pending-subscriptions.node"));
        for (Node node : service.getNodes()) {
            if (!node.isCollectionNode() && node.isAdmin(data.getOwner())) {
                formField.addOption(null, node.getUniqueIdentifier().getNodeId());
            }
        }
        // Add the form to the command
        command.add(form.getElement());
    }

    @Override
    public void execute(SessionData data, Element command) {
        Element note = command.addElement("note");
        List<String> nodeIDs = data.getData().get("pubsub#node");
        if (nodeIDs.isEmpty()) {
            // No nodeID was provided by the requester
            note.addAttribute("type", "error");
            note.setText(LocaleUtils.getLocalizedString(
                    "pubsub.command.pending-subscriptions.error.idrequired"));
        }
        else if (nodeIDs.size() > 1) {
            // More than one nodeID was provided by the requester
            note.addAttribute("type", "error");
            note.setText(LocaleUtils.getLocalizedString(
                    "pubsub.command.pending-subscriptions.error.manyIDs"));
        }
        else {
            Node node = service.getNode(nodeIDs.get(0));
            if (node != null) {
                if (node.isAdmin(data.getOwner())) {
                    note.addAttribute("type", "info");
                    note.setText(LocaleUtils.getLocalizedString(
                            "pubsub.command.pending-subscriptions.success"));

                    for (NodeSubscription subscription : node.getPendingSubscriptions()) {
                        subscription.sendAuthorizationRequest(data.getOwner());
                    }
                }
                else {
                    // Requester is not an admin of the specified node
                    note.addAttribute("type", "error");
                    note.setText(LocaleUtils.getLocalizedString(
                            "pubsub.command.pending-subscriptions.error.forbidden"));
                }
            }
            else {
                // Node with the specified nodeID was not found
                note.addAttribute("type", "error");
                note.setText(LocaleUtils.getLocalizedString(
                        "pubsub.command.pending-subscriptions.error.badid"));
            }
        }
    }

    @Override
    public String getCode() {
        return "http://jabber.org/protocol/pubsub#get-pending";
    }

    @Override
    public String getDefaultLabel() {
        return LocaleUtils.getLocalizedString("pubsub.command.pending-subscriptions.label");
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
    public int getMaxStages(SessionData data) {
        return 1;
    }

    @Override
    public boolean hasPermission(JID requester) {
        // User has permission if he is an owner of at least one node or is a sysadmin
        for (Node node : service.getNodes()) {
            if (!node.isCollectionNode() && node.isAdmin(requester)) {
                return true;
            }
        }
        return false;
    }
}
