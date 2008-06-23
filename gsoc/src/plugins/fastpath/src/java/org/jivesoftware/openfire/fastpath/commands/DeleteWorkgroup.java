/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */
package org.jivesoftware.openfire.fastpath.commands;

import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.WorkgroupManager;
import org.dom4j.Element;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.util.Arrays;
import java.util.List;

/**
 * Command that allows to delete existing workgroups.
 *
 * @author Gaston Dombiak
 *
 * TODO Use i18n
 */
public class DeleteWorkgroup extends AdHocCommand {
    protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle("Delete workgroup");
        form.addInstruction("Fill out this form to delete a workgroup.");

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.jid_single);
        field.setLabel("Workgroup's JID");
        field.setVariable("workgroup");
        field.setRequired(true);

        // Add the form to the command
        command.add(form.getElement());
    }

    public void execute(SessionData data, Element command) {
        Element note = command.addElement("note");
        // Get requested group
        WorkgroupManager workgroupManager = WorkgroupManager.getInstance();

        // Load the workgroup
        try {
            Workgroup workgroup = workgroupManager.getWorkgroup(new JID(data.getData().get("workgroup").get(0)));
            workgroupManager.deleteWorkgroup(workgroup);
        } catch (UserNotFoundException e) {
            // Group not found
            note.addAttribute("type", "error");
            note.setText("Workgroup not found");
            return;
        } catch (Exception e) {
            // Group not found
            note.addAttribute("type", "error");
            note.setText("Error executing the command");
            return;
        }

        note.addAttribute("type", "info");
        note.setText("Operation finished successfully");
    }

    public String getCode() {
        return "http://jabber.org/protocol/admin#delete-workgroup";
    }

    public String getDefaultLabel() {
        return "Delete workgroup";
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
