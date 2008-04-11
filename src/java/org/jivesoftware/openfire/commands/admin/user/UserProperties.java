/**
 * $RCSfile:  $
 * $Revision:  $
 * $Date:  $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.openfire.commands.admin.user;

import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.User;
import org.dom4j.Element;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.util.List;
import java.util.Arrays;

/**
 *  An adhoc command to retrieve the properties of the user.
 *
 * @author Alexander Wenckus
 */
public class UserProperties extends AdHocCommand {
    public String getCode() {
        return "http://jabber.org/protocol/admin#get-user-properties";
    }

    public String getDefaultLabel() {
        return "Get User Properties";
    }

    public int getMaxStages(SessionData data) {
        return 1;
    }

    public void execute(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.result);

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        List<String> accounts = data.getData().get("accountjids");

        if (accounts != null && accounts.size() > 0) {
            populateResponseFields(form, accounts);
        }

        command.add(form.getElement());
    }

    private void populateResponseFields(DataForm form, List<String> accounts) {
        FormField jidField = form.addField();
        jidField.setVariable("accountjids");

        FormField emailField = form.addField();
        emailField.setVariable("email");

        FormField nameField = form.addField();
        nameField.setVariable("name");

        UserManager manager = UserManager.getInstance();
        for(String account : accounts) {
            User user;
            try {
                JID jid = new JID(account);
                user = manager.getUser(jid.getNode());
            }
            catch (Exception ex) {
                continue;
            }

            jidField.addValue(account);
            emailField.addValue(user.getEmail());
            nameField.addValue(user.getName());
        }
    }

    protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle("Retrieve Users' Information");
        form.addInstruction("Fill out this form to retrieve users' information.");

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.jid_multi);
        field.setLabel("The list of Jabber IDs to retrive the information");
        field.setVariable("accountjids");
        field.setRequired(true);

        // Add the form to the command
        command.add(form.getElement());
    }

    protected List<Action> getActions(SessionData data) {
        return Arrays.asList(AdHocCommand.Action.complete);
    }

    protected AdHocCommand.Action getExecuteAction(SessionData data) {
        return AdHocCommand.Action.complete;
    }
}
