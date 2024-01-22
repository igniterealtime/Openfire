/*
 * Copyright (C) 2004-2008 Jive Software, 2017-2024 Ignite Realtime Foundation. All rights reserved.
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
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.http.HttpBindManager;
import org.jivesoftware.util.LocaleUtils;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * AdHoc command to return the current status of the HTTP-bind service. The command returns whether
 * the service is currently enabled, if it is enabled it will return the HTTP address on
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
        return LocaleUtils.getLocalizedString("commands.admin.httpbindstatus.label");
    }

    @Override
    public int getMaxStages(@Nonnull final SessionData data) {
        return 0;
    }

    @Override
    public void execute(@Nonnull final SessionData data, Element command) {
        final Locale preferredLocale = SessionManager.getInstance().getLocaleForSession(data.getOwner());

        DataForm form = new DataForm(DataForm.Type.result);

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        HttpBindManager manager = HttpBindManager.getInstance();
        boolean isEnabled = manager.isHttpBindEnabled();
        field = form.addField();
        field.setType(FormField.Type.boolean_type);
        field.setLabel(LocaleUtils.getLocalizedString("commands.admin.httpbindstatus.form.field.httpbindenabled.label", preferredLocale));
        field.setVariable("httpbindenabled");
        field.addValue(String.valueOf(isEnabled));

        if (isEnabled) {
            field = form.addField();
            field.setType(FormField.Type.text_single);
            field.setLabel(LocaleUtils.getLocalizedString("commands.admin.httpbindstatus.form.field.httpbindaddress.label", preferredLocale));
            field.setVariable("httpbindaddress");
            field.addValue(manager.getHttpBindUnsecureAddress());

            field = form.addField();
            field.setType(FormField.Type.text_single);
            field.setLabel(LocaleUtils.getLocalizedString("commands.admin.httpbindstatus.form.field.httpbindsecureaddress.label", preferredLocale));
            field.setVariable("httpbindsecureaddress");
            field.addValue(manager.getHttpBindSecureAddress());

            String jsUrl = manager.getJavaScriptUrl();
            if (jsUrl != null) {
                field = form.addField();
                field.setType(FormField.Type.text_single);
                field.setLabel(LocaleUtils.getLocalizedString("commands.admin.httpbindstatus.form.field.javascriptaddress.label", preferredLocale));
                field.setVariable("javascriptaddress");
                field.addValue(jsUrl);
            }

            field = form.addField();
            field.setType(FormField.Type.text_single);
            field.setLabel(LocaleUtils.getLocalizedString("commands.admin.httpbindstatus.form.field.websocketaddress.label", preferredLocale));
            field.setVariable("websocketaddress");
            field.addValue(manager.getWebsocketUnsecureAddress());

            field = form.addField();
            field.setType(FormField.Type.text_single);
            field.setLabel(LocaleUtils.getLocalizedString("commands.admin.httpbindstatus.form.field.websocketsecureaddress.label", preferredLocale));
            field.setVariable("websocketsecureaddress");
            field.addValue(manager.getWebsocketSecureAddress());
        }

        command.add(form.getElement());
    }

    @Override
    protected void addStageInformation(@Nonnull final SessionData data, Element command) {
        // no stages, do nothing.
    }

    @Override
    protected List<Action> getActions(@Nonnull final SessionData data) {
        return Collections.emptyList();
    }

    @Override
    protected Action getExecuteAction(@Nonnull final SessionData data) {
        return null;
    }


    @Override
    public boolean hasPermission(JID requester) {
        return super.hasPermission(requester) || InternalComponentManager.getInstance().hasComponent(requester);
    }
}
