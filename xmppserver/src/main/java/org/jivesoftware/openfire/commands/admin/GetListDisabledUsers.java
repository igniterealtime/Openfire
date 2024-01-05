/*
 * Copyright (C) 2024 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire.commands.admin;

import org.dom4j.Element;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.lockout.LockOutManager;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Command that allows to retrieve a list of all disabled users.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @see <a href="https://xmpp.org/extensions/xep-0133.html#get-disabled-users-list">XEP-0133 Service Administration: Get List of Disabled Users</a>
 */
// TODO Use i18n
public class GetListDisabledUsers extends AdHocCommand {

    @Override
    protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle("Requesting List of Disabled Users");
        form.addInstruction("Fill out this form to request the disabled users of this service.");

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

    @Override
    public void execute(SessionData data, Element command) {
        String max_items = data.getData().get("max_items").get(0);
        int maxItems = -1;
        if (max_items != null && !"none".equals(max_items)) {
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
        field.setType(FormField.Type.jid_multi);
        field.setLabel("The list of all disabled users");
        field.setVariable("disableduserjids");

        // TODO improve on this, as this is not efficient on systems with large amounts of users.
        final LockOutManager lockOutManager = LockOutManager.getInstance();
        for (final User user : UserManager.getInstance().getUsers()) {
            if (lockOutManager.isAccountDisabled(user.getUsername())) {
                field.addValue(XMPPServer.getInstance().createJID(user.getUsername(), null));
            }
        }

        command.add(form.getElement());
    }

    @Override
    public String getCode() {
        return "http://jabber.org/protocol/admin#get-disabled-users-list";
    }

    @Override
    public String getDefaultLabel() {
        return "Get List of Disabled Users";
    }

    @Override
    protected List<Action> getActions(SessionData data) {
        return Collections.singletonList(Action.complete);
    }

    @Override
    protected Action getExecuteAction(SessionData data) {
        return Action.complete;
    }

    @Override
    public int getMaxStages(SessionData data) {
        return 1;
    }
}
