/**
 * $RCSfile:  $
 * $Revision:  $
 * $Date:  $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.openfire.commands.admin.user;

import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.XMPPServer;
import org.dom4j.Element;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.util.List;
import java.util.Arrays;
import java.util.Map;

/**
 * Adds a user to Openfire if the provider is not read-only. See
 * <a href="http://www.xmpp.org/extensions/xep-0133.html#add-user">Service Administration:
 * Add User</a>
 *
 * @author Alexander Wenckus
 */
public class AddUser extends AdHocCommand {
    public String getCode() {
        return "http://jabber.org/protocol/admin#add-user";
    }

    public String getDefaultLabel() {
        return "Add a User";
    }

    public int getMaxStages(SessionData data) {
        return 1;
    }

    public void execute(SessionData sessionData, Element command) {
        Element note = command.addElement("note");
        // Check if groups cannot be modified (backend is read-only)
        if (UserManager.getUserProvider().isReadOnly()) {
            note.addAttribute("type", "error");
            note.setText("User provider is read only. New users cannot be created.");
            return;
        }
        Map<String, List<String>> data = sessionData.getData();

        // Let's create the jid and check that they are a local user
        JID account;
        try {
            account = new JID(get(data, "accountjid", 0));
        }
        catch (NullPointerException npe) {
            note.addAttribute("type", "error");
            note.setText("JID required parameter.");
            return;
        }
        if (!XMPPServer.getInstance().isLocal(account)) {
            note.addAttribute("type", "error");
            note.setText("Cannot create remote user.");
            return;
        }

        String password = get(data, "password", 0);
        String passwordRetry = get(data, "password-verify", 0);

        if (password == null || "".equals(password) || !password.equals(passwordRetry)) {
            note.addAttribute("type", "error");
            note.setText("Passwords do not match.");
            return;
        }

        String email = get(data, "email", 0);
        String givenName = get(data, "given_name", 0);
        String surName = get(data, "surname", 0);
        String name = (givenName == null ? "" : givenName) + (surName == null ? "" : surName);
        name = (name.equals("") ? null : name);

        try {
            UserManager.getInstance().createUser(account.getNode(), password, name, email);
        }
        catch (UserAlreadyExistsException e) {
            note.addAttribute("type", "error");
            note.setText("User already exists.");
            return;
        }
        // Answer that the operation was successful
        note.addAttribute("type", "info");
        note.setText("Operation finished successfully");
    }

    protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle("Adding a user");
        form.addInstruction("Fill out this form to add a user.");

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.jid_single);
        field.setLabel("The Jabber ID for the account to be added");
        field.setVariable("accountjid");
        field.setRequired(true);

        field = form.addField();
        field.setType(FormField.Type.text_private);
        field.setLabel("The password for this account");
        field.setVariable("password");

        field = form.addField();
        field.setType(FormField.Type.text_private);
        field.setLabel("Retype password");
        field.setVariable("password-verify");

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel("Email address");
        field.setVariable("email");

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel("Given name");
        field.setVariable("given_name");

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel("Family name");
        field.setVariable("surname");

        // Add the form to the command
        command.add(form.getElement());
    }

    private String get(Map<String, List<String>> data, String key, int value) {
        List<String> list = data.get(key);
        if (list == null) {
            return null;
        }
        else {
            return list.get(value);
        }
    }

    protected List<Action> getActions(SessionData data) {
        return Arrays.asList(AdHocCommand.Action.complete);
    }

    protected AdHocCommand.Action getExecuteAction(SessionData data) {
        return AdHocCommand.Action.complete;
    }

    public boolean hasPermission(JID requester) {
        return super.hasPermission(requester) && !UserManager.getUserProvider().isReadOnly();
    }
}
