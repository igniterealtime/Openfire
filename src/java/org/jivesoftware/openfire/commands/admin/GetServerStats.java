/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.commands.admin;

import org.dom4j.Element;
import org.jivesoftware.admin.AdminConsole;
import org.jivesoftware.util.FastDateFormat;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.session.ClientSession;
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
    final private FastDateFormat dateFormat = FastDateFormat.getInstance(JiveConstants.XMPP_DATETIME_FORMAT, TimeZone.getTimeZone("UTC"));

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
        field.addValue(XMPPServer.getInstance().getServerInfo().getName());

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
        field.addValue(dateFormat.format(XMPPServer.getInstance().getServerInfo().getLastStarted()));

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
        Set<String> users = new HashSet<String>(sessions.size());
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

    protected List<Action> getActions(SessionData data) {
        //Do nothing since there are no stages
        return null;
    }

    public String getCode() {
        return "http://jabber.org/protocol/admin#get-server-stats";
    }

    public String getDefaultLabel() {
        return "Get basic statistics of the server.";
    }

    protected Action getExecuteAction(SessionData data) {
        //Do nothing since there are no stages
        return null;
    }

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
    public boolean hasPermission(JID requester) {
        return super.hasPermission(requester) || InternalComponentManager.getInstance().getComponent(requester) != null;
    }
}
