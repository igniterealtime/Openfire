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

package org.jivesoftware.openfire.commands.admin;

import org.dom4j.Element;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.session.ClientSession;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Command that allows to retrieve the number of registered users who are online at
 * any one moment. By "online user" is meant any user or account that currently has
 * an IM session that may or may no be available.
 *
 * @author Gaston Dombiak
 */
public class GetNumberOnlineUsers extends AdHocCommand {

    protected void addStageInformation(SessionData data, Element command) {
        //Do nothing since there are no stages
    }

    public void execute(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.result);

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setLabel(getLabel());
        field.setVariable("onlineusersnum");
        // Make sure that we are only counting based on bareJIDs and not fullJIDs
        Collection<ClientSession> sessions = SessionManager.getInstance().getSessions();
        Set<String> users = new HashSet<String>(sessions.size());
        for (ClientSession session : sessions) {
            users.add(session.getAddress().toBareJID());
        }
        field.addValue(users.size());

        command.add(form.getElement());
    }

    protected List<Action> getActions(SessionData data) {
        //Do nothing since there are no stages
        return null;
    }

    public String getCode() {
        return "http://jabber.org/protocol/admin#get-online-users-num";
    }

    public String getDefaultLabel() {
        // TODO Use i18n
        return "Number of Online Users";
    }

    protected Action getExecuteAction(SessionData data) {
        //Do nothing since there are no stages
        return null;
    }

    public int getMaxStages(SessionData data) {
        return 0;
    }
}
