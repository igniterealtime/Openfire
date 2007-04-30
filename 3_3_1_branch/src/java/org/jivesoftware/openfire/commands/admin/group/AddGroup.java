/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.commands.admin.group;

import org.dom4j.Element;
import org.jivesoftware.util.Log;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupAlreadyExistsException;
import org.jivesoftware.openfire.group.GroupManager;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Command that allows to create and configure new goups.
 *
 * @author Gaston Dombiak
 *
 * TODO Use i18n
 */
public class AddGroup extends AdHocCommand {
    protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle("Create new group");
        form.addInstruction("Fill out this form to create a new group.");

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel("Group Name");
        field.setVariable("group");
        field.setRequired(true);

        field = form.addField();
        field.setType(FormField.Type.text_multi);
        field.setLabel("Description");
        field.setVariable("desc");

        field = form.addField();
        field.setType(FormField.Type.jid_multi);
        field.setLabel("Initial members");
        field.setVariable("members");

        field = form.addField();
        field.setType(FormField.Type.list_single);
        field.setLabel("Shared group visibility");
        field.setVariable("showInRoster");
        field.addValue("nobody");
        field.addOption("Disable sharing group in rosters", "nobody");
        field.addOption("Show group in all users' rosters", "everybody");
        field.addOption("Show group in group members' rosters", "onlyGroup");
        field.addOption("Show group to members' rosters of these groups", "spefgroups");
        field.setRequired(true);

        field = form.addField();
        field.setType(FormField.Type.list_multi);
        field.setVariable("groupList");
        for (Group group : GroupManager.getInstance().getGroups()) {
            field.addOption(group.getName(), group.getName());
        }

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel("Group Display Name");
        field.setVariable("displayName");

        // Add the form to the command
        command.add(form.getElement());
    }

    public void execute(SessionData data, Element command) {
        Element note = command.addElement("note");
        // Check if groups cannot be modified (backend is read-only)
        if (GroupManager.getInstance().isReadOnly()) {
            note.addAttribute("type", "error");
            note.setText("Groups are read only");
            return;
        }
        // Get requested group
        Group group;
        try {
            group = GroupManager.getInstance().createGroup(data.getData().get("group").get(0));
        } catch (GroupAlreadyExistsException e) {
            // Group not found
            note.addAttribute("type", "error");
            note.setText("Group already exists");
            return;
        }

        List<String> desc = data.getData().get("desc");
        if (desc != null && !desc.isEmpty()) {
            group.setDescription(desc.get(0));
        }

        List<String> members = data.getData().get("members");
        boolean withErrors = false;
        if (members != null) {
            Collection<JID> users = group.getMembers();
            for (String user : members) {
                try {
                    users.add(new JID(user));
                } catch (Exception e) {
                    Log.warn("User not added to group", e);
                    withErrors = true;
                }
            }
        }

        String showInRoster = data.getData().get("showInRoster").get(0);
        if ("nobody".equals(showInRoster)) {
            // New group is not a shared group
            group.getProperties().put("sharedRoster.showInRoster", "nobody");
            group.getProperties().put("sharedRoster.displayName", " ");
            group.getProperties().put("sharedRoster.groupList", " ");
        }
        else {
            // New group is configured as a shared group
            if ("spefgroups".equals(showInRoster)) {
                // Show shared group to other groups
                showInRoster = "onlyGroup";
            }
            List<String> displayName = data.getData().get("displayName");
            List<String> groupList = data.getData().get("groupList");
            if (displayName != null) {
                group.getProperties().put("sharedRoster.showInRoster", showInRoster);
                group.getProperties().put("sharedRoster.displayName", displayName.get(0));
                if (groupList != null) {
                    StringBuilder buf = new StringBuilder();
                    String sep = "";
                    for (String groupName : groupList) {
                        buf.append(sep).append(groupName);
                        sep = ",";
                    }
                    group.getProperties().put("sharedRoster.groupList", buf.toString());
                }
            }
            else {
                withErrors = true;
            }
        }

        note.addAttribute("type", "info");
        note.setText("Operation finished" + (withErrors ? " with errors" : " successfully"));
    }

    public String getCode() {
        return "http://jabber.org/protocol/admin#add-group";
    }

    public String getDefaultLabel() {
        return "Create new group";
    }

    protected List<Action> getActions(SessionData data) {
        return Arrays.asList(AdHocCommand.Action.complete);
    }

    protected AdHocCommand.Action getExecuteAction(SessionData data) {
        return AdHocCommand.Action.complete;
    }

    public int getMaxStages(SessionData data) {
        return 1;
    }
}
