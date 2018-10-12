/*
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
import org.jivesoftware.admin.AdminConsole;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.text.DecimalFormat;
import java.util.*;

/**
 * Command that returns information about the server and some basic statistics. This command
 * can only be executed by administrators or components of the server.
 *
 * TODO Use i18n
 * 
 * @author Gaston Dombiak
 */
public class GetServerStats extends AdHocCommand {

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
        field.setLabel(LocaleUtils.getLocalizedString("index.server_name"));
        field.setVariable("name");
        field.addValue(AdminConsole.getAppName());

        field = form.addField();
        field.setLabel(LocaleUtils.getLocalizedString("index.version"));
        field.setVariable("version");
        field.addValue(AdminConsole.getVersionString());

        field = form.addField();
        field.setLabel(LocaleUtils.getLocalizedString("index.domain_name"));
        field.setVariable("domain");
        field.addValue(XMPPServer.getInstance().getServerInfo().getXMPPDomain());

        field = form.addField();
        field.setLabel(LocaleUtils.getLocalizedString("index.jvm"));
        field.setVariable("os");
        String vmName = System.getProperty("java.vm.name");
        if (vmName == null) {
            vmName = "";
        }
        else {
            vmName = " -- " + vmName;
        }
        field.addValue(System.getProperty("java.version") + " " +System.getProperty("java.vendor") +vmName);

        field = form.addField();
        field.setLabel(LocaleUtils.getLocalizedString("index.uptime"));
        field.setVariable("uptime");
        field.addValue(XMPPDateTimeFormat.format(XMPPServer.getInstance().getServerInfo().getLastStarted()));

        DecimalFormat mbFormat = new DecimalFormat("#0.00");
        DecimalFormat percentFormat = new DecimalFormat("#0.0");
        Runtime runtime = Runtime.getRuntime();
        double freeMemory = (double)runtime.freeMemory()/(1024*1024);
        double maxMemory = (double)runtime.maxMemory()/(1024*1024);
        double totalMemory = (double)runtime.totalMemory()/(1024*1024);
        double usedMemory = totalMemory - freeMemory;
        double percentFree = ((maxMemory - usedMemory)/maxMemory)*100.0;
        double percentUsed = 100 - percentFree;
        field = form.addField();
        field.setLabel(LocaleUtils.getLocalizedString("index.memory"));
        field.setVariable("memory");
        field.addValue(mbFormat.format(usedMemory) + " MB of " + mbFormat.format(maxMemory) + " MB (" +
                percentFormat.format(percentUsed) + "%) used");

        // Make sure that we are only counting based on bareJIDs and not fullJIDs
        Collection<ClientSession> sessions = SessionManager.getInstance().getSessions();
        Set<String> users = new HashSet<>(sessions.size());
        int availableSessions = 0;
        for (ClientSession session : sessions) {
            if (session.getPresence().isAvailable()) {
                users.add(session.getAddress().toBareJID());
                availableSessions++;
            }
        }
        field = form.addField();
        field.setLabel("Available Users");
        field.setVariable("activeusersnum");
        field.addValue(users.size());

        field = form.addField();
        field.setLabel("Available Users Sessions");
        field.setVariable("sessionsnum");
        field.addValue(availableSessions);

        command.add(form.getElement());
    }

    @Override
    protected List<Action> getActions(SessionData data) {
        //Do nothing since there are no stages
        return null;
    }

    @Override
    public String getCode() {
        return "http://jabber.org/protocol/admin#get-server-stats";
    }

    @Override
    public String getDefaultLabel() {
        return "Get basic statistics of the server.";
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

    /**
     * Returns if the requester can access this command. Only admins and components
     * are allowed to execute this command.
     *
     * @param requester the JID of the user requesting to execute this command.
     * @return true if the requester can access this command.
     */
    @Override
    public boolean hasPermission(JID requester) {
        return super.hasPermission(requester) || InternalComponentManager.getInstance().hasComponent(requester);
    }
}
