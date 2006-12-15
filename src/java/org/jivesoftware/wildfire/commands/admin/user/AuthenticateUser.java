/**
 * $RCSfile:  $
 * $Revision:  $
 * $Date:  $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.wildfire.commands.admin.user;

import org.jivesoftware.wildfire.commands.AdHocCommand;
import org.jivesoftware.wildfire.commands.SessionData;
import org.jivesoftware.wildfire.user.UserManager;
import org.jivesoftware.wildfire.user.User;
import org.jivesoftware.wildfire.user.UserNotFoundException;
import org.jivesoftware.wildfire.auth.AuthFactory;
import org.jivesoftware.wildfire.auth.UnauthorizedException;
import org.jivesoftware.wildfire.XMPPServer;
import org.jivesoftware.wildfire.component.InternalComponentManager;
import org.dom4j.Element;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.util.List;
import java.util.Arrays;

/**
 * Takes a user's username and password to authenticate them against the Wildfire authprovider.
 *
 * @author Alexander Wenckus
 */
public class AuthenticateUser extends AdHocCommand {
    public String getCode() {
        return "http://jabber.org/protocol/admin#authenticate-user";
    }

    public String getDefaultLabel() {
        return "Authenticate User";
    }

    public int getMaxStages(SessionData data) {
        return 1;
    }

    public void execute(SessionData data, Element command) {
        Element note = command.addElement("note");
        // Check if groups cannot be modified (backend is read-only)
        if (UserManager.getUserProvider().isReadOnly()) {
            note.addAttribute("type", "error");
            note.setText("Users are read only. Changing password is not allowed.");
            return;
        }
        JID account;
        try {
            account = new JID(data.getData().get("accountjid").get(0));
        }
        catch (NullPointerException ne) {
            note.addAttribute("type", "error");
            note.setText("JID required parameter.");
            return;
        }
        if (!XMPPServer.getInstance().isLocal(account)) {
            note.addAttribute("type", "error");
            note.setText("Cannot authenticate remote user.");
            return;
        }
        String password = data.getData().get("password").get(0);
        // Get requested user
        User user;
        try {
            user = UserManager.getInstance().getUser(account.getNode());
        }
        catch (UserNotFoundException e) {
            // User not found
            note.addAttribute("type", "error");
            note.setText("User does not exists.");
            return;
        }

        try {
            AuthFactory.getAuthProvider().authenticate(user.getUsername(), password);
        }
        catch (UnauthorizedException e) {
            // Auth failed
            note.addAttribute("type", "error");
            note.setText("Authentication failed.");
            return;
        }
        // Answer that the operation was successful
        note.addAttribute("type", "info");
        note.setText("Operation finished successfully.");
    }

    protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle("Authenticating a user");
        form.addInstruction("Fill out this form to authenticate a user.");

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel("The username for this account");
        field.setVariable("username");
        field.setRequired(true);

        field = form.addField();
        field.setType(FormField.Type.text_private);
        field.setLabel("The password for this account");
        field.setVariable("password");
        field.setRequired(true);

        // Add the form to the command
        command.add(form.getElement());
    }

    protected List<Action> getActions(SessionData data) {
        return Arrays.asList(AdHocCommand.Action.complete);
    }

    protected Action getExecuteAction(SessionData data) {
        return AdHocCommand.Action.complete;
    }

    @Override
    public boolean hasPermission(JID requester) {
        return super.hasPermission(requester) ||
                InternalComponentManager.getInstance().getComponent(requester) != null;
    }
}
