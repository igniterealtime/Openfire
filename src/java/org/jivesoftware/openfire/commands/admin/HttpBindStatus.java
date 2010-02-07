/**
 * $RCSfile$
 * $Revision: 3144 $
 * $Date: 2005-12-01 14:20:11 -0300 (Thu, 01 Dec 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.http.HttpBindManager;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.util.Collections;
import java.util.List;

/**
 * AdHoc command to return the current status of the HTTP-bind service. The command returns whether
 * or not the service is currently enabled, if it is enabled it will return the HTTP address on
 * which the service can be reached.
 *
 * @author Alexander Wenckus
 */
public class HttpBindStatus extends AdHocCommand {
    @Override
	public String getCode() {
        return "http://jabber.org/protocol/admin#status-http-bind";
    }

    @Override
	public String getDefaultLabel() {
        return "Current Http Bind Status";
    }

    @Override
	public int getMaxStages(SessionData data) {
        return 0;
    }

    @Override
	public void execute(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.result);

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        HttpBindManager manager = HttpBindManager.getInstance();
        boolean isEnabled = manager.isHttpBindEnabled();
        field = form.addField();
        field.setLabel("Http Bind Enabled");
        field.setVariable("httpbindenabled");
        field.addValue(String.valueOf(isEnabled));

        if (isEnabled) {
            field = form.addField();
            field.setLabel("Http Bind Address");
            field.setVariable("httpbindaddress");
            field.addValue(manager.getHttpBindUnsecureAddress());

            field = form.addField();
            field.setLabel("Http Bind Secure Address");
            field.setVariable("httpbindsecureaddress");
            field.addValue(manager.getHttpBindSecureAddress());

            String jsUrl = manager.getJavaScriptUrl();
            if (jsUrl != null) {
                field = form.addField();
                field.setLabel("Http Bind JavaScript Address");
                field.setVariable("javascriptaddress");
                field.addValue(jsUrl);
            }
        }

        command.add(form.getElement());
    }

    @Override
	protected void addStageInformation(SessionData data, Element command) {
        // no stages, do nothing.
    }

    @Override
	protected List<Action> getActions(SessionData data) {
        return Collections.emptyList();
    }

    @Override
	protected Action getExecuteAction(SessionData data) {
        return null;
    }


    @Override
    public boolean hasPermission(JID requester) {
        return super.hasPermission(requester) || InternalComponentManager.getInstance().hasComponent(requester);
    }
}
