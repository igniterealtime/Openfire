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

package org.jivesoftware.openfire.commands.clearspace;

import org.dom4j.Element;
import org.jivesoftware.openfire.clearspace.ClearspaceManager;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.util.List;

/**
 * Command that generates a new nonce to be used to SSO between OF and CS.
 *
 * @author Gabriel Guardincerri
 */
public class GenerateNonce extends AdHocCommand {

    @Override
	protected void addStageInformation(SessionData data, Element command) {
        //Do nothing since there are no stages
    }

    @Override
	public void execute(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.result);

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setLabel(getLabel());
        field.setVariable("nonce");
        field.addValue(ClearspaceManager.getInstance().generateNonce());

        command.add(form.getElement());
    }

    @Override
	protected List<Action> getActions(SessionData data) {
        //Do nothing since there are no stages
        return null;
    }

    @Override
	public String getCode() {
        return "http://jabber.org/protocol/clearspace#generate-nonce";
    }

    @Override
	public String getDefaultLabel() {
        // TODO Use i18n
        return "New nonce";
    }

    @Override
	protected Action getExecuteAction(SessionData data) {
        //Do nothing since there are no stages
        return null;
    }

    @Override
	public int getMaxStages(SessionData data) {
        return 0;
    }

    @Override
	public boolean hasPermission(JID requester) {
        return (super.hasPermission(requester) || InternalComponentManager.getInstance().hasComponent(requester));
    }
    
}