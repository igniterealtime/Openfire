/**
 * $RCSfile:  $
 * $Revision:  $
 * $Date:  $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
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
    public String getCode() {
        return "http://jabber.org/protocol/admin#status-http-bind";
    }

    public String getDefaultLabel() {
        return "Current Http Bind Status";
    }

    public int getMaxStages(SessionData data) {
        return 0;
    }

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

    protected void addStageInformation(SessionData data, Element command) {
        // no stages, do nothing.
    }

    protected List<Action> getActions(SessionData data) {
        return Collections.emptyList();
    }

    protected Action getExecuteAction(SessionData data) {
        return null;
    }


    @Override
    public boolean hasPermission(JID requester) {
        return super.hasPermission(requester) || InternalComponentManager.getInstance().hasComponent(requester);
    }
}
