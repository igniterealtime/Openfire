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
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.commands.AdHocCommand;
import org.jivesoftware.openfire.commands.SessionData;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.container.AdminConsolePlugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.io.IOException;
import java.net.*;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Command that returns information about the admin console. This command
 * can only be executed by administrators or components of the server.
 *
 * @author Gabriel Guardincerri
 */
public class GetAdminConsoleInfo extends AdHocCommand {

    protected void addStageInformation(SessionData data, Element command) {
        //Do nothing since there are no stages
    }

    public void execute(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.result);

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");


        // Gets a valid bind interface
        PluginManager pluginManager = XMPPServer.getInstance().getPluginManager();
        AdminConsolePlugin adminConsolePlugin = ((AdminConsolePlugin) pluginManager.getPlugin("admin"));

        String bindInterface = adminConsolePlugin.getBindInterface();
        int adminPort = adminConsolePlugin.getAdminUnsecurePort();
        int adminSecurePort = adminConsolePlugin.getAdminSecurePort();

        if (bindInterface == null) {
            Enumeration<NetworkInterface> nets = null;
            try {
                nets = NetworkInterface.getNetworkInterfaces();
            } catch (SocketException e) {
                // We failed to discover a valid IP address where the admin console is running
                return;
            }
            for (NetworkInterface netInterface : Collections.list(nets)) {
                boolean found = false;
                Enumeration<InetAddress> addresses = netInterface.getInetAddresses();
                for (InetAddress address : Collections.list(addresses)) {
                    if ("127.0.0.1".equals(address.getHostAddress()) || "0:0:0:0:0:0:0:1".equals(address.getHostAddress())) {
                        continue;
                    }
                    Socket socket = new Socket();
                    InetSocketAddress remoteAddress = new InetSocketAddress(address, adminPort > 0 ? adminPort : adminSecurePort);
                    try {
                        socket.connect(remoteAddress);
                        bindInterface = address.getHostAddress();
                        found = true;
                        break;
                    } catch (IOException e) {
                        // Ignore this address. Let's hope there is more addresses to validate
                    }
                }
                if (found) {
                    break;
                }
            }
        }

        // If there is no valid bind interface, return an error
        if (bindInterface == null) {
            Element note = command.addElement("note");
            note.addAttribute("type", "error");
            note.setText("Couldn't find a valid interface.");
            return;            
        }

        // Add the bind interface
        field = form.addField();
        field.setLabel("Bind interface");
        field.setVariable("bindInterface");
        field.addValue(bindInterface);

        // Add the port
        field = form.addField();
        field.setLabel("Port");
        field.setVariable("adminPort");
        field.addValue(adminPort);

        // Add the secure port
        field = form.addField();
        field.setLabel("Secure port");
        field.setVariable("adminSecurePort");
        field.addValue(adminSecurePort);

        command.add(form.getElement());
    }

    protected List<Action> getActions(SessionData data) {
        //Do nothing since there are no stages
        return null;
    }

    public String getCode() {
        return "http://jabber.org/protocol/admin#get-console-info";
    }

    public String getDefaultLabel() {
        return "Get admin console info.";
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
        return super.hasPermission(requester) || InternalComponentManager.getInstance().hasComponent(requester);
    }
}