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

    @Override
    protected List<Action> getActions(SessionData data) {
        //Do nothing since there are no stages
        return null;
    }

    @Override
    public String getCode() {
        return "http://jabber.org/protocol/admin#get-console-info";
    }

    @Override
    public String getDefaultLabel() {
        return "Get admin console info.";
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
