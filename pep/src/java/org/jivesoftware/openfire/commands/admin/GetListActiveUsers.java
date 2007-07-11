/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.commands.admin;

import org.dom4j.Element;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.session.ClientSession;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;

import java.util.*;

/**
 * Command that allows to retrieve a list of all active users.
 *
 * @author Gaston Dombiak
 *
 * TODO Use i18n
 */
public class GetListActiveUsers extends AdHocCommand {

    protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle("Requesting List of Active Users");
        form.addInstruction("Fill out this form to request the active users of this service.");

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.list_single);
        field.setLabel("Maximum number of items to show");
        field.setVariable("max_items");
        field.addOption("25", "25");
        field.addOption("50", "50");
        field.addOption("75", "75");
        field.addOption("100", "100");
        field.addOption("150", "150");
        field.addOption("200", "200");
        field.addOption("None", "none");

        // Add the form to the command
        command.add(form.getElement());
    }

    public void execute(SessionData data, Element command) {
        String max_items = data.getData().get("max_items").get(0);
        int maxItems = -1;
        if (max_items != null && "none".equals(max_items)) {
            try {
                maxItems = Integer.parseInt(max_items);
            }
            catch (NumberFormatException e) {
                // Do nothing. Assume that all users are being requested
            }
        }

        DataForm form = new DataForm(DataForm.Type.result);

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setLabel("The list of active users");
        field.setVariable("activeuserjids");

        // Get list of users (i.e. bareJIDs) that are connected to the server
        Collection<ClientSession> sessions = SessionManager.getInstance().getSessions();
        Set<String> users = new HashSet<String>(sessions.size());
        for (ClientSession session : sessions) {
            if (session.getPresence().isAvailable()) {
                users.add(session.getAddress().toBareJID());
            }
            if (maxItems > 0 && users.size() >= maxItems) {
                break;
            }
        }
        // Add users to the result
        for (String user : users) {
            field.addValue(user);
        }
        command.add(form.getElement());
    }

    public String getCode() {
        return "http://jabber.org/protocol/admin#get-active-users";
    }

    public String getDefaultLabel() {
        return "Get List of Active Users";
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
}
