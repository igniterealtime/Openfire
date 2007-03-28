/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.pubsub;

import org.dom4j.Element;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.util.Arrays;
import java.util.List;

/**
 * Ad-hoc command that sends pending subscriptions to node owners.
 *
 * @author Matt Tucker
 */
class PendingSubscriptionsCommand extends AdHocCommand {

    private PubSubService service;

    PendingSubscriptionsCommand(PubSubService service) {
        this.service = service;
    }

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
                formField.addOption(null, node.getNodeID());
            }
        }
        // Add the form to the command
        command.add(form.getElement());
    }

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

    public String getCode() {
        return "http://jabber.org/protocol/pubsub#get-pending";
    }

    public String getDefaultLabel() {
        return LocaleUtils.getLocalizedString("pubsub.command.pending-subscriptions.label");
    }

    protected List<Action> getActions(SessionData data) {
        return Arrays.asList(Action.complete);
    }

    protected Action getExecuteAction(SessionData data) {
        return Action.complete;
    }

    public int getMaxStages(SessionData data) {
        return 1;
    }

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
