/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.plugin;

import org.jivesoftware.messenger.container.Plugin;
import org.jivesoftware.messenger.container.PluginManager;
import org.jivesoftware.messenger.*;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;

/**
 * Broadcast service plugin. It accepts messages and broadcasts them out to
 * groups of connected users. The address <tt>all@[serviceName].[server]</tt> is
 * reserved for sending a broadcast message to all connected users. Otherwise,
 * broadcast messages can be sent to groups of users by using addresses
 * in the form <tt>[group]@[serviceName].[server]</tt>.
 *
 * @author Matt Tucker
 */
public class BroadcastPlugin implements Plugin, Component {

    private String serviceName;
    private SessionManager sessionManager;
    private List<String> allowedUsers;
    private boolean groupMembersAllowed;

    /**
     * Constructs a new broadcast plugin.
     */
    public BroadcastPlugin() {
        serviceName = JiveGlobals.getProperty("plugin.broadcast.serviceName", "broadcast");
        groupMembersAllowed = JiveGlobals.getBooleanProperty(
                "plugin.broadcast.groupMembersAllowed");
        allowedUsers = new ArrayList<String>();
    }

    // Plugin Interface

    public String getName() {
        return "Broadcast Plugin";
    }

    public String getDescription() {
        return "Broadcasts messages to users.";
    }

    public String getAuthor() {
        return "Jive Software";
    }

    public String getVersion() {
        return "1.0";
    }

    public void initialize(PluginManager manager, File pluginDirectory) {
        sessionManager = SessionManager.getInstance();

        // Register as a component.
        ComponentManager.getInstance().addComponent(serviceName, this);
    }

    public void destroy() {
        // Unregister component.
        ComponentManager.getInstance().removeComponent(serviceName);
        sessionManager = null;
    }

    // Component Interface

    public void processPacket(Packet packet) {
        // Only respond to incoming messages. TODO: handle disco, presence, etc.
        if (packet instanceof Message) {
            Message message = (Message)packet;
            String name = message.getTo().getNode();
            // Check to see if trying to broadcast to all connected users.
            if ("all".equals(name)) {
                if (allowedUsers.size() > 0) {
                    // See if the user is allowed to send the message.
                    String address = message.getFrom().toBareJID();
                    if (!allowedUsers.contains(address)) {
                        Message error = new Message();
                        if (message.getID() != null) {
                            error.setID(message.getID());
                        }
                        error.setError(PacketError.Condition.not_allowed);
                        error.setTo(message.getFrom());
                        error.setSubject("Error sending broadcast message");
                        error.setBody("Not allowed to send a broadcast message to " +
                                message.getTo());
                        ComponentManager.getInstance().sendPacket(error);
                        return;
                    }
                }
                sessionManager.sendServerMessage(message.getSubject(), message.getBody());
            }
            // See if the name is a group.
           
            // Otherwise, the address is recognized so send an error message back.
            else {
                Message error = new Message();
                if (message.getID() != null) {
                    error.setID(message.getID());
                }
                error.setTo(message.getFrom());
                error.setError(PacketError.Condition.not_allowed);
                error.setSubject("Error sending broadcast message");
                error.setBody("Not allowed to send a broadcast message to " +
                        message.getTo());
                ComponentManager.getInstance().sendPacket(error);
            }
        }
    }

    // Other Methods

    /**
     * Returns the service name of this component, which is "broadcast" by default.
     *
     * @return the service name of this component.
     */
    public String getServiceName() {
        return serviceName;
    }

    /**
     * Sets the service name of this component, which is "broadcast" by default.
     *
     * @param serviceName the service name of this component.
     */
    public void setServiceName(String serviceName) {
        if (serviceName == null) {
            throw new NullPointerException("Service name cannot be null");
        }
        if (this.serviceName.equals(serviceName)) {
            return;
        }
        JiveGlobals.setProperty("plugin.broadcast.serviceName", serviceName);
        // Re-register the service.
        ComponentManager.getInstance().removeComponent(this.serviceName);
        ComponentManager.getInstance().addComponent(serviceName, this);
        this.serviceName = serviceName;
    }

    /**
     * Returns a collection of the addresses of users allowed to send broadcast
     * messages. If no users are defined, anyone can send broadcast messages.
     * Additional users may also be allowed to send broadcast messages to
     * specific groups depending on the group settings.
     *
     * @return the users allowed to send broadcast messages.
     */
    public Collection<String> getGlobalAllowedUsers() {
        return allowedUsers;
    }

    /**
     * Returns true if normal group members are allowed to send broadcast messages
     * to groups they belong to. Otherwise, only group administrators can send
     * broadcast messages to groups. Global allowed users can also send messages to
     * groups.
     *
     * @return true if group members are allowed to broadcast messages; otherwise only
     *      group admins are allowed.
     */
    public boolean isGroupMembersAllowed() {
        return groupMembersAllowed;
    }

    /**
     * Sets whether normal group members are allowed to send broadcast messages
     * to groups they belong to. Otherwise, only group administrators can send
     * broadcast messages to groups. Global allowed users can also send messages to
     * groups.
     *
     * @param allowed true if group members are allowed to broadcast messages; otherwise only
     *      group admins are allowed.
     */
    public void setGroupMembersAllowed(boolean allowed) {
        this.groupMembersAllowed = allowed;
        JiveGlobals.setProperty("plugin.broadcast.groupMembersAllowed", Boolean.toString(allowed));
    }
}