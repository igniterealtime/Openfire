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

package org.jivesoftware.openfire.commands;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.commands.admin.*;
import org.jivesoftware.openfire.commands.admin.group.*;
import org.jivesoftware.openfire.commands.admin.user.AddUser;
import org.jivesoftware.openfire.commands.admin.user.DeleteUser;
import org.jivesoftware.openfire.commands.admin.user.AuthenticateUser;
import org.jivesoftware.openfire.commands.admin.user.ChangeUserPassword;
import org.jivesoftware.openfire.commands.admin.user.UserProperties;
import org.jivesoftware.openfire.commands.event.*;
import org.jivesoftware.openfire.commands.generic.Ping;
import org.jivesoftware.openfire.disco.*;
import org.jivesoftware.openfire.handler.IQHandler;
import org.xmpp.forms.DataForm;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

import java.util.*;

/**
 * An AdHocCommandHandler is responsbile for providing discoverable information about the
 * supported commands and for handling commands requests. This is an implementation of JEP-50:
 * Ad-Hoc Commands.<p>
 *
 * Ad-hoc commands that require user interaction will have one or more stages. For each stage the
 * user will complete a data form and send it back to the server. The data entered by the user is
 * kept in a SessionData. Instances of {@link AdHocCommand} are stateless. In order to prevent
 * "bad" users from consuming all system memory there exists a limit of simultaneous commands that
 * a user might perform. Configure the system property {@code "xmpp.command.limit"} to control
 * this limit. User sessions will also timeout and their data destroyed if they have not been
 * executed within a time limit since the session was created. The default timeout value is 10
 * minutes. The timeout value can be modified by setting the system property
 * {@code "xmpp.command.timeout"}.<p>
 *
 * New commands can be added dynamically by sending the message {@link #addCommand(AdHocCommand)}.
 * The command will immediatelly appear in the disco#items list and might be executed by those
 * users with enough execution permissions.
 *
 * @author Gaston Dombiak
 */
public class AdHocCommandHandler extends IQHandler
        implements ServerFeaturesProvider, DiscoInfoProvider, DiscoItemsProvider {

    private static final String NAMESPACE = "http://jabber.org/protocol/commands";

    private String serverName;
    private IQHandlerInfo info;
    private IQDiscoInfoHandler infoHandler;
    private IQDiscoItemsHandler itemsHandler;
    /**
     * Manager that keeps the list of ad-hoc commands and processing command requests.
     */
    private AdHocCommandManager manager;

    public AdHocCommandHandler() {
        super("Ad-Hoc Commands Handler");
        info = new IQHandlerInfo("command", NAMESPACE);
        manager = new AdHocCommandManager();
    }

    @Override
    public IQ handleIQ(IQ packet) throws UnauthorizedException {
        return manager.process(packet);
    }

    @Override
    public IQHandlerInfo getInfo() {
        return info;
    }

    @Override
    public Iterator<String> getFeatures() {
        return Collections.singleton(NAMESPACE).iterator();
    }

    @Override
    public Iterator<Element> getIdentities(String name, String node, JID senderJID) {
        Element identity = DocumentHelper.createElement("identity");
        identity.addAttribute("category", "automation");
        identity.addAttribute("type", NAMESPACE.equals(node) ? "command-list" : "command-node");
        return Collections.singleton(identity).iterator();
    }

    @Override
    public Iterator<String> getFeatures(String name, String node, JID senderJID) {
        return Arrays.asList(NAMESPACE, "jabber:x:data").iterator();
    }

    @Override
    public DataForm getExtendedInfo(String name, String node, JID senderJID) {
        return null;
    }
    
    @Override
    public Set<DataForm> getExtendedInfos(String name, String node, JID senderJID) {
        return new HashSet<DataForm>();
    }

    @Override
    public boolean hasInfo(String name, String node, JID senderJID) {
        if (NAMESPACE.equals(node)) {
            return true;
        }
        else {
            // Only include commands that the sender can execute
            AdHocCommand command = manager.getCommand(node);
            return command != null && command.hasPermission(senderJID);
        }
    }

    @Override
    public Iterator<DiscoItem> getItems(String name, String node, JID senderJID) {
        List<DiscoItem> answer = new ArrayList<>();
        if (!NAMESPACE.equals(node)) {
            answer = Collections.emptyList();
        }
        else {
            for (AdHocCommand command : manager.getCommands()) {
                // Only include commands that the sender can invoke (i.e. has enough permissions)
                if (command.hasPermission(senderJID)) {
                    final DiscoItem item = new DiscoItem(new JID(serverName),
                            command.getLabel(), command.getCode(), null);
                    answer.add(item);
                }
            }
        }
        return answer.iterator();
    }

    @Override
    public void initialize(XMPPServer server) {
        super.initialize(server);
        serverName = server.getServerInfo().getXMPPDomain();
        infoHandler = server.getIQDiscoInfoHandler();
        itemsHandler = server.getIQDiscoItemsHandler();
    }

    @Override
    public void start() throws IllegalStateException {
        super.start();
        infoHandler.setServerNodeInfoProvider(NAMESPACE, this);
        itemsHandler.setServerNodeInfoProvider(NAMESPACE, this);
        // Add the "out of the box" commands
        addDefaultCommands();
    }

    @Override
    public void stop() {
        super.stop();
        infoHandler.removeServerNodeInfoProvider(NAMESPACE);
        itemsHandler.removeServerNodeInfoProvider(NAMESPACE);
        // Stop commands
        for (AdHocCommand command : manager.getCommands()) {
            stopCommand(command);
        }
    }

    /**
     * Adds a new command to the list of supported ad-hoc commands by this server. The new
     * command will appear in the discoverable items list and will be executed for those users
     * with enough permission.
     *
     * @param command the new ad-hoc command to add.
     */
    public void addCommand(AdHocCommand command) {
        manager.addCommand(command);
        startCommand(command);
    }

    /**
     * Removes the command from the list of ad-hoc commands supported by this server. The command
     * will no longer appear in the discoverable items list.
     *
     * @param command the ad-hoc command to remove.
     */
    public void removeCommand(AdHocCommand command) {
        if (manager.removeCommand(command)) {
            stopCommand(command);
        }
    }

    private void addDefaultCommands() {
        // TODO Complete when out of the box commands are implemented
        addCommand(new GetNumberActiveUsers());
        addCommand(new GetNumberOnlineUsers());
        addCommand(new GetNumberUserSessions());
        addCommand(new GetListActiveUsers());
        addCommand(new GetUsersPresence());
        addCommand(new GetListGroups());
        addCommand(new GetListGroupUsers());
        addCommand(new AddGroupUsers());
        addCommand(new DeleteGroupUsers());
        addCommand(new AddGroup());
        addCommand(new UpdateGroup());
        addCommand(new DeleteGroup());
        addCommand(new AddUser());
        addCommand(new DeleteUser());
        addCommand(new AuthenticateUser());
        addCommand(new ChangeUserPassword());
        addCommand(new UserProperties());
        addCommand(new PacketsNotification());
        addCommand(new GetServerStats());
        addCommand(new HttpBindStatus());
        addCommand(new UserCreated());
        addCommand(new UserModified());
        addCommand(new UserDeleting());
        addCommand(new GroupCreated());
        addCommand(new GroupDeleting());
        addCommand(new GroupModified());
        addCommand(new GroupMemberAdded());
        addCommand(new GroupMemberRemoved());
        addCommand(new GroupAdminAdded());
        addCommand(new GroupAdminRemoved());
        addCommand(new VCardCreated());
        addCommand(new VCardDeleting());
        addCommand(new VCardModified());
        addCommand(new GetAdminConsoleInfo());
        addCommand(new Ping());
    }

    private void startCommand(AdHocCommand command) {
        infoHandler.setServerNodeInfoProvider(command.getCode(), this);
        itemsHandler.setServerNodeInfoProvider(command.getCode(), this);
    }

    private void stopCommand(AdHocCommand command) {
        infoHandler.removeServerNodeInfoProvider(command.getCode());
        itemsHandler.removeServerNodeInfoProvider(command.getCode());
    }
}
