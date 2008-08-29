/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
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
        field.setVariable("nonce");
        field.addValue(ClearspaceManager.getInstance().generateNonce());

        command.add(form.getElement());
    }

    protected List<Action> getActions(SessionData data) {
        //Do nothing since there are no stages
        return null;
    }

    public String getCode() {
        return "http://jabber.org/protocol/clearspace#generate-nonce";
    }

    public String getDefaultLabel() {
        // TODO Use i18n
        return "New nonce";
    }

    protected Action getExecuteAction(SessionData data) {
        //Do nothing since there are no stages
        return null;
    }

    public int getMaxStages(SessionData data) {
        return 0;
    }

    public boolean hasPermission(JID requester) {
        return (super.hasPermission(requester) || InternalComponentManager.getInstance().hasComponent(requester));
    }
    
}