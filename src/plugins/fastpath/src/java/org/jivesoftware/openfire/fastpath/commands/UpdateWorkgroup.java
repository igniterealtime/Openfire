package org.jivesoftware.openfire.fastpath.commands;


import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.WorkgroupManager;
import org.jivesoftware.xmpp.workgroup.RequestQueue;
import org.jivesoftware.xmpp.workgroup.Agent;
import org.dom4j.Element;
import org.jivesoftware.openfire.fastpath.util.WorkgroupUtils;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.StringUtils;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command that allows to update existing workgroups.
 *
 *
 * TODO Use i18n
 */
public class UpdateWorkgroup extends AdHocCommand {
    private static final Logger Log = LoggerFactory.getLogger(UpdateWorkgroup.class);

    @Override
    protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle("Update workgroup");
        form.addInstruction("Fill out this form to update a workgroup.");

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.jid_single);
        field.setLabel("Workgroup's JID");
        field.setVariable("workgroup");
        field.setRequired(true);

        field = form.addField();
        field.setType(FormField.Type.text_multi);
        field.setLabel("Username of the members");
        field.setVariable("members");

        // Add the form to the command
        command.add(form.getElement());
    }

    @Override
    public void execute(SessionData sessionData, Element command) {
        Element note = command.addElement("note");
        Map<String, List<String>> data = sessionData.getData();
        // Get requested group
        WorkgroupManager workgroupManager = WorkgroupManager.getInstance();

        // Load the workgroup
        try {
            String wgName= data.get("workgroup").get(0);
            List<String> members = data.get("members");
            String agents = StringUtils.collectionToString(members);

            Workgroup workgroup = workgroupManager.getWorkgroup(new JID(wgName));

            for (RequestQueue requestQueue : workgroup.getRequestQueues()) // only one default queue
            {
                for (Agent agent : workgroupManager.getAgentManager().getAgents())
                {
                    if (requestQueue.isMember(agent))
                    {
                        requestQueue.removeMember(agent);
                    }
                }

                WorkgroupUtils.addAgents(requestQueue, agents);
            }

        } catch (UserNotFoundException e) {
            // Group not found
            note.addAttribute("type", "error");
            note.setText("Workgroup not found");
            return;
        } catch (Exception e) {
            // Group not found
            Log.error("UpdateWorkgroup", e);
            note.addAttribute("type", "error");
            note.setText("Error executing the command");
            return;
        }

        note.addAttribute("type", "info");
        note.setText("Operation finished successfully");
    }

    @Override
    public String getCode() {
        return "http://jabber.org/protocol/admin#update-workgroup";
    }

    @Override
    public String getDefaultLabel() {
        return "Update workgroup";
    }

    @Override
    protected List<Action> getActions(SessionData data) {
        return Arrays.asList(AdHocCommand.Action.complete);
    }

    @Override
    protected AdHocCommand.Action getExecuteAction(SessionData data) {
        return AdHocCommand.Action.complete;
    }

    @Override
    public int getMaxStages(SessionData data) {
        return 1;
    }
}
