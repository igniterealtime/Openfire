/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.commands.clearspace;

import org.dom4j.Element;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MUCRole;
import org.jivesoftware.util.Log;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.util.List;
import java.util.Map;
import java.util.Arrays;

/**
 * Command that triggers a user to be removed from a chat room.  CS needs this for now because
 * flash apps are destroyed before the entire logout process can be completed when a page
 * is unloaded.
 *
 * @author Daniel Henninger
 */
public class DisconnectUserFromRoom extends AdHocCommand {
    public String getCode() {
        return "http://jabber.org/protocol/clearspace#disconnect-from-chat";
    }

    public String getDefaultLabel() {
        return "Disconnect a user from a chat room";
    }

    public int getMaxStages(SessionData data) {
        return 1;
    }

    public void execute(SessionData sessionData, Element command) {
        Element note = command.addElement("note");

        Map<String, List<String>> data = sessionData.getData();
        
        Log.debug("Got command "+command.asXML());

        // Gets the userjid
        String userjid = get(data, "userjid", 0);

        if (userjid == null || "".equals(userjid)) {
            note.addAttribute("type", "error");
            note.setText("User JID is empty.");
            return;
        }

        // Gets the roomjid
        String roomjid = get(data, "roomjid", 0);

        if (roomjid == null || "".equals(roomjid)) {
            note.addAttribute("type", "error");
            note.setText("Room JID is empty.");
            return;
        }

        JID rjid = new JID(roomjid);
        JID ujid = new JID(userjid);
        String nickname = ujid.getNode();
        Log.debug("userjid = "+userjid);
        Log.debug("roomjid = "+roomjid);
        Log.debug("nickname = "+nickname);
        Log.debug("rjid = "+rjid);
        Log.debug("ujid = "+ujid);
        MultiUserChatService service = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(rjid);
        if (service != null) {
            MUCRoom room = service.getChatRoom(rjid.getNode());
            if (room != null) {
                try {
                    for (MUCRole role : room.getOccupantsByBareJID(ujid.toBareJID())) {
                        if (role.getNickname().equals(nickname)) {
                            room.leaveRoom(role);
                            // Answer that the operation was successful
                            note.addAttribute("type", "info");
                            note.setText("Operation finished successfully");
                            return;
                        }
                    }
                    note.addAttribute("type", "error");
                    note.setText("Occupant not found in room specified.");
                }
                catch (UserNotFoundException e) {
                    note.addAttribute("type", "error");
                    note.setText("Occupant not found in room specified.");
                }
            }
            else {
                note.addAttribute("type", "error");
                note.setText("Room not found on MUC service.");
            }
        }
        else {
            note.addAttribute("type", "error");
            note.setText("Service for room JID not found.");
        }
    }

    protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle("Disconnect user from chat");
        form.addInstruction("Fill out this form to disconnect a user from a chat room.");

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.text_private);
        field.setLabel("The user JID to remove from room");
        field.setVariable("userjid");
        field.setRequired(true);

        field = form.addField();
        field.setType(FormField.Type.text_private);
        field.setLabel("The room JID to remove the user from");
        field.setVariable("roomjid");
        field.setRequired(true);

        // Add the form to the command
        command.add(form.getElement());
    }

    protected List<Action> getActions(SessionData data) {
        return Arrays.asList(Action.complete);
    }

    protected Action getExecuteAction(SessionData data) {
        return Action.complete;
    }

    public boolean hasPermission(JID requester) {
        return (super.hasPermission(requester) || InternalComponentManager.getInstance().hasComponent(requester));
    }
}