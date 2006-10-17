/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.commands.admin;

import org.dom4j.Element;
import org.jivesoftware.wildfire.XMPPServer;
import org.jivesoftware.wildfire.auth.AuthFactory;
import org.jivesoftware.wildfire.commands.AdHocCommand;
import org.jivesoftware.wildfire.commands.SessionData;
import org.jivesoftware.wildfire.user.UserNotFoundException;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.util.Arrays;
import java.util.List;

/**
 * Command that allows to get the password of existing users.
 *
 * @author Gaston Dombiak
 *
 * TODO Use i18n
 */
public class GetUserPassword extends AdHocCommand {
    public String getCode() {
        return "http://jabber.org/protocol/admin#get-user-password";
    }

    public String getDefaultLabel() {
        return "Get User Password";
    }

    public int getMaxStages(SessionData data) {
        return 1;
    }

    public void execute(SessionData data, Element command) {
        Element note = command.addElement("note");
        // Check if groups cannot be modified (backend is read-only)
        if (!AuthFactory.getAuthProvider().supportsPasswordRetrieval()) {
            note.addAttribute("type", "error");
            note.setText("Retrieval of passwords is not supported.");
            return;
        }
        JID account = new JID(data.getData().get("accountjid").get(0));
        if (!XMPPServer.getInstance().isLocal(account)) {
            note.addAttribute("type", "error");
            note.setText("Cannot get password of remote user.");
            return;
        }
        // Get user password
        String password;
        try {
            password = AuthFactory.getAuthProvider().getPassword(account.getNode());
        } catch (UserNotFoundException e) {
            // Group not found
            note.addAttribute("type", "error");
            note.setText("User does not exist.");
            return;
        }
        // Return the passowrd of the user
        DataForm form = new DataForm(DataForm.Type.result);

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setVariable("accountjid");
        field.addValue(account.toString());

        field = form.addField();
        field.setVariable("password");
        field.addValue(password);

        command.add(form.getElement());

        // Answer that the operation was successful
        note.addAttribute("type", "info");
        note.setText("Operation finished successfully");
    }

    protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle("Getting a User\u2019s Password");
        form.addInstruction("Fill out this form to get a user\u2019s password.");

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.jid_single);
        field.setLabel("The Jabber ID for which to retrieve the password");
        field.setVariable("accountjid");
        field.setRequired(true);

        // Add the form to the command
        command.add(form.getElement());
    }

    protected List<AdHocCommand.Action> getActions(SessionData data) {
        return Arrays.asList(AdHocCommand.Action.complete);
    }

    protected AdHocCommand.Action getExecuteAction(SessionData data) {
        return AdHocCommand.Action.complete;
    }


    public boolean hasPermission(JID requester) {
        return super.hasPermission(requester) &&
                AuthFactory.getAuthProvider().supportsPasswordRetrieval();
    }
}
