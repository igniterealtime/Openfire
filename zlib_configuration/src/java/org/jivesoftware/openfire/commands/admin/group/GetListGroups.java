/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.commands.admin.group;

import org.dom4j.Element;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.roster.RosterManager;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command that allows to retrieve a list of existing groups.
 *
 * @author Gaston Dombiak
 *
 * TODO Use i18n
 */
public class GetListGroups extends AdHocCommand {

    @Override
	protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle("Requesting List of Existing Groups");
        form.addInstruction("Fill out this form to request list of groups.");

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.list_single);
        field.setLabel("Start from page number");
        field.setVariable("start");
        field.addValue("0");
        field.addOption("0", "0");
        field.addOption("25", "25");
        field.addOption("50", "50");
        field.addOption("75", "75");
        field.addOption("100", "100");
        field.addOption("150", "150");
        field.addOption("200", "200");
        field.setRequired(true);

        field = form.addField();
        field.setType(FormField.Type.list_single);
        field.setLabel("Maximum number of items to show");
        field.setVariable("max_items");
        field.addValue("25");
        field.addOption("25", "25");
        field.addOption("50", "50");
        field.addOption("75", "75");
        field.addOption("100", "100");
        field.addOption("150", "150");
        field.addOption("200", "200");
        field.addOption("None", "none");
        field.setRequired(true);

        // Add the form to the command
        command.add(form.getElement());
    }

    @Override
	public void execute(SessionData data, Element command) {
        String start = data.getData().get("start").get(0);
        String max_items = data.getData().get("max_items").get(0);
        int nStart = 0;
        if (start != null) {
            try {
                nStart = Integer.parseInt(start);
            }
            catch (NumberFormatException e) {
                // Do nothing. Assume default value
            }
        }
        int maxItems = 100000;
        if (max_items != null && "none".equals(max_items)) {
            try {
                maxItems = Integer.parseInt(max_items);
            }
            catch (NumberFormatException e) {
                // Do nothing. Assume that all users are being requested
            }
        }

        DataForm form = new DataForm(DataForm.Type.result);

        form.addReportedField("name", "Name", FormField.Type.text_single);
        form.addReportedField("desc", "Description", FormField.Type.text_multi);
        form.addReportedField("count", "User Count", FormField.Type.text_single);
        form.addReportedField("shared", "Shared group?", FormField.Type.boolean_type);
        form.addReportedField("display", "Display Name", FormField.Type.text_single);
        form.addReportedField("visibility", "Visibility", FormField.Type.text_single);
        form.addReportedField("groups", "Show group to members' rosters of these groups", FormField.Type.text_multi);

        // Add groups to the result
        for (Group group : GroupManager.getInstance().getGroups(nStart, maxItems)) {
            boolean isSharedGroup = RosterManager.isSharedGroup(group);
            Map<String, String> properties = group.getProperties();
            Map<String,Object> fields = new HashMap<String,Object>();
            fields.put("name", group.getName());
            fields.put("desc", group.getDescription());
            fields.put("count", group.getMembers().size() + group.getAdmins().size());
            fields.put("shared", isSharedGroup);
            fields.put("display",
                    (isSharedGroup ? properties.get("sharedRoster.displayName") : ""));
            String showInRoster =
                    (isSharedGroup ? properties.get("sharedRoster.showInRoster") : "");
            if ("onlyGroup".equals(showInRoster) &&
                    properties.get("sharedRoster.groupList").trim().length() > 0) {
                // Show shared group to other groups
                showInRoster = "spefgroups";
            }
            fields.put("visibility", showInRoster);
            fields.put("groups", (isSharedGroup ? properties.get("sharedRoster.groupList") : ""));
            form.addItemFields(fields);
        }
        command.add(form.getElement());
    }

    @Override
	public String getCode() {
        return "http://jabber.org/protocol/admin#get-groups";
    }

    @Override
	public String getDefaultLabel() {
        return "Get List of Existing Groups";
    }

    @Override
	protected List<AdHocCommand.Action> getActions(SessionData data) {
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
