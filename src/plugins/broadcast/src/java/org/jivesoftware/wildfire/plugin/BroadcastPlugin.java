/**
 * $RCSfile: BroadcastPlugin.java,v $
 * $Revision: 3117 $
 * $Date: 2005-11-25 22:57:29 -0300 (Fri, 25 Nov 2005) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.plugin;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;
import org.jivesoftware.wildfire.SessionManager;
import org.jivesoftware.wildfire.XMPPServer;
import org.jivesoftware.wildfire.auth.UnauthorizedException;
import org.jivesoftware.wildfire.container.Plugin;
import org.jivesoftware.wildfire.container.PluginManager;
import org.jivesoftware.wildfire.group.Group;
import org.jivesoftware.wildfire.group.GroupManager;
import org.jivesoftware.wildfire.group.GroupNotFoundException;
import org.xmpp.component.Component;
import org.xmpp.component.ComponentException;
import org.xmpp.component.ComponentManager;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.packet.*;

import java.io.File;
import java.util.*;

/**
 * Broadcast service plugin. It accepts messages and broadcasts them out to
 * groups of connected users. The address <tt>all@[serviceName].[server]</tt> is
 * reserved for sending a broadcast message to all connected users. Otherwise,
 * broadcast messages can be sent to groups of users by using addresses
 * in the form <tt>[group]@[serviceName].[server]</tt>.
 *
 * @author Matt Tucker
 */
public class BroadcastPlugin implements Plugin, Component, PropertyEventListener {

    private String serviceName;
    private SessionManager sessionManager;
    private GroupManager groupManager;
    private List<JID> allowedUsers;
    private boolean groupMembersAllowed;
    private boolean disableGroupPermissions;
    private ComponentManager componentManager;
    private PluginManager pluginManager;

    /**
     * Constructs a new broadcast plugin.
     */
    public BroadcastPlugin() {
        serviceName = JiveGlobals.getProperty("plugin.broadcast.serviceName", "broadcast");
        disableGroupPermissions = JiveGlobals.getBooleanProperty(
                "plugin.broadcast.disableGroupPermissions");
        groupMembersAllowed = JiveGlobals.getBooleanProperty(
                "plugin.broadcast.groupMembersAllowed", true);
        allowedUsers = stringToList(JiveGlobals.getProperty("plugin.broadcast.allowedUsers", ""));
    }

    // Plugin Interface

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        pluginManager = manager;
        sessionManager = SessionManager.getInstance();
        groupManager = GroupManager.getInstance();

        // Register as a component.
        componentManager = ComponentManagerFactory.getComponentManager();
        try {
            componentManager.addComponent(serviceName, this);
        }
        catch (Exception e) {
            componentManager.getLog().error(e);
        }
        PropertyEventDispatcher.addListener(this);
    }

    public void destroyPlugin() {
        PropertyEventDispatcher.removeListener(this);
        // Unregister component.
        try {
            componentManager.removeComponent(serviceName);
        }
        catch (Exception e) {
            componentManager.getLog().error(e);
        }
        componentManager = null;
        pluginManager = null;
        sessionManager = null;
        groupManager = null;
        allowedUsers.clear();
    }

    public void initialize(JID jid, ComponentManager componentManager) {
    }

    public void start() {
    }

    public void shutdown() {
    }

    // Component Interface

    public String getName() {
        // Get the name from the plugin.xml file.
        return pluginManager.getName(this);
    }

    public String getDescription() {
        // Get the description from the plugin.xml file.
        return pluginManager.getDescription(this);
    }

    public void processPacket(Packet packet) {
        boolean canProceed = false;
        Group group = null;
        String toNode = packet.getTo().getNode();
        // Check if user is allowed to send packet to this service[+group]
        if ("all".equals(toNode)) {
            // See if the user is allowed to send the packet.
            JID address = new JID(packet.getFrom().toBareJID());
            if (allowedUsers.isEmpty() || allowedUsers.contains(address)) {
                canProceed = true;
            }
        }
        else {
            try {
                group = groupManager.getGroup(toNode);
                boolean isGroupUser = group.isUser(packet.getFrom()) ||
                        group.isUser(new JID(packet.getFrom().toBareJID()));
                if (disableGroupPermissions || (groupMembersAllowed && isGroupUser) ||
                        allowedUsers.contains(new JID(packet.getFrom().toBareJID()))) {
                    canProceed = true;
                }
            }
            catch (GroupNotFoundException e) {
            }
        }
        // Only respond to incoming messages. TODO: handle disco, presence, etc.
        if (packet instanceof Message) {
            Message message = (Message)packet;
            // Check to see if trying to broadcast to all connected users.
            if ("all".equals(toNode)) {
                if (!canProceed) {
                    Message error = new Message();
                    if (message.getID() != null) {
                        error.setID(message.getID());
                    }
                    error.setError(PacketError.Condition.not_allowed);
                    error.setTo(message.getFrom());
                    error.setSubject("Error sending broadcast message");
                    error.setBody("Not allowed to send a broadcast message to " +
                            message.getTo());
                    try {
                        componentManager.sendPacket(this, error);
                    }
                    catch (Exception e) {
                        componentManager.getLog().error(e);
                    }
                    return;
                }
                try {
                    sessionManager.broadcast(message);
                }
                catch (UnauthorizedException ue) {
                    Log.error(ue);
                }
            }
            // See if the name is a group.
            else {
                if (group == null) {
                    // The address is not recognized so send an error message back.
                    Message error = new Message();
                    if (message.getID() != null) {
                        error.setID(message.getID());
                    }
                    error.setTo(message.getFrom());
                    error.setError(PacketError.Condition.not_allowed);
                    error.setSubject("Error sending broadcast message");
                    error.setBody("Address not valid: " +
                            message.getTo());
                    try {
                        componentManager.sendPacket(this, error);
                    }
                    catch (Exception e) {
                        componentManager.getLog().error(e);
                    }
                }
                else if (canProceed) {
                    // Broadcast message to group users. Users that are offline will get
                    // the message when they come back online
                    for (JID userJID : group.getMembers()) {
                        Message newMessage = message.createCopy();
                        newMessage.setTo(userJID);
                        try {
                            componentManager.sendPacket(this, newMessage);
                        }
                        catch (Exception e) {
                            componentManager.getLog().error(e);
                        }
                    }
                }
                else {
                    // Otherwise, the address is recognized so send an error message back.
                    Message error = new Message();
                    if (message.getID() != null) {
                        error.setID(message.getID());
                    }
                    error.setTo(message.getFrom());
                    error.setError(PacketError.Condition.not_allowed);
                    error.setSubject("Error sending broadcast message");
                    error.setBody("Not allowed to send a broadcast message to " +
                            message.getTo());
                    try {
                        componentManager.sendPacket(this, error);
                    }
                    catch (Exception e) {
                        componentManager.getLog().error(e);
                    }
                }
            }
        }
        else if (packet instanceof Presence) {
            Presence presence = (Presence) packet;
            try {
                if (!canProceed) {
                    // Send forbidden error since user is not allowed
                    Presence reply = new Presence();
                    reply.setID(presence.getID());
                    reply.setTo(presence.getFrom());
                    reply.setFrom(presence.getTo());
                    reply.setError(PacketError.Condition.forbidden);
                    componentManager.sendPacket(this, reply);
                    return;
                }

                if (Presence.Type.subscribe == presence.getType()) {
                    // Accept all presence requests
                    // Reply that the subscription request was approved
                    Presence reply = new Presence();
                    reply.setTo(presence.getFrom());
                    reply.setFrom(presence.getTo());
                    reply.setType(Presence.Type.subscribed);
                    componentManager.sendPacket(this, reply);
                    // Send that the service is available
                    /*reply = new Presence();
                    reply.setTo(presence.getFrom());
                    reply.setFrom(presence.getTo());
                    componentManager.sendPacket(this, reply);*/
                }
                else if (Presence.Type.unsubscribe == presence.getType()) {
                    // Send confirmation of unsubscription
                    Presence reply = new Presence();
                    reply.setTo(presence.getFrom());
                    reply.setFrom(presence.getTo());
                    reply.setType(Presence.Type.unsubscribed);
                    componentManager.sendPacket(this, reply);
                    // Send unavailable presence of the service
                    reply = new Presence();
                    reply.setTo(presence.getFrom());
                    reply.setFrom(presence.getTo());
                    reply.setType(Presence.Type.unavailable);
                    componentManager.sendPacket(this, reply);
                }
                else if (Presence.Type.probe == presence.getType()) {
                    // Send that the service is available
                    Presence reply = new Presence();
                    reply.setTo(presence.getFrom());
                    reply.setFrom(presence.getTo());
                    componentManager.sendPacket(this, reply);
                }
            }
            catch (ComponentException e) {
                componentManager.getLog().error(e);
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
        JiveGlobals.setProperty("plugin.broadcast.serviceName", serviceName);
    }

    /**
     * Returns a collection of the addresses of users allowed to send broadcast
     * messages. If no users are defined, anyone can send broadcast messages to
     * all users. Additional users may also be allowed to send broadcast messages
     * to specific groups depending on the group settings.
     *
     * @return the users allowed to send broadcast messages.
     */
    public Collection<JID> getGlobalAllowedUsers() {
        return allowedUsers;
    }

    /**
     * Sets the collection of addresses of users allowed to send broadcast
     * messages. If the collection is empty, anyone can send broadcast messages.
     * Additional users may also be allowed to send broadcast messages to
     * specific groups depending on the group settings.
     *
     * @param allowedUsers collection of users allowed to send broadcast messages
     *      to all users.
     */
    public void setGlobalAllowedUsers(Collection<String> allowedUsers) {
        StringBuilder buf = new StringBuilder();
        for (String jid : allowedUsers) {
            buf.append(jid).append(",");
        }
        JiveGlobals.setProperty("plugin.broadcast.allowedUsers", buf.toString());
    }

    /**
     * Returns true if all permission checking on sending messages to groups is disabled
     * (enabled by default). When disabled, any user in the system can send a message to
     * a group.
     *
     * @return true if group permission checking is disabled.
     */
    public boolean isGroupPermissionsDisabled() {
        return disableGroupPermissions;
    }

    /**
     * Enables or disables permission checking when sending messages to a group. When
     * disabled, any user in the system can send a message to a group.
     *
     * @param disableGroupPermissions true if group permission checking should be disabled.
     */
    public void setGroupPermissionsDisabled(boolean disableGroupPermissions) {
        this.disableGroupPermissions = disableGroupPermissions;
        JiveGlobals.setProperty("plugin.broadcast.disableGroupPermissions",
                Boolean.toString(disableGroupPermissions));
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

    // PropertyEventListener Methods

    public void propertySet(String property, Map params) {
        if (property.equals("plugin.broadcast.groupMembersAllowed")) {
            this.groupMembersAllowed = Boolean.parseBoolean((String)params.get("value"));
        }
        else if (property.equals("plugin.broadcast.disableGroupPermissions")) {
            this.disableGroupPermissions = Boolean.parseBoolean((String)params.get("value"));
        }
        else if (property.equals("plugin.broadcast.allowedUsers")) {
            this.allowedUsers = stringToList((String)params.get("value"));
        }
        else if (property.equals("plugin.broadcast.serviceName")) {
            changeServiceName((String)params.get("value"));
        }
    }

    public void propertyDeleted(String property, Map params) {
        if (property.equals("plugin.broadcast.groupMembersAllowed")) {
            this.groupMembersAllowed = true;
        }
        else if (property.equals("plugin.broadcast.disableGroupPermissions")) {
            this.disableGroupPermissions = false;
        }
        else if (property.equals("plugin.broadcast.allowedUsers")) {
            this.allowedUsers = Collections.emptyList();
        }
        else if (property.equals("plugin.broadcast.serviceName")) {
            changeServiceName("broadcast");
        }
    }

    public void xmlPropertySet(String property, Map params) {
        // Ignore.
    }

    public void xmlPropertyDeleted(String property, Map params) {
        // Ignore.
    }

    /**
     * Changes the service name to a new value.
     *
     * @param serviceName the service name.
     */
    private void changeServiceName(String serviceName) {
         if (serviceName == null) {
            throw new NullPointerException("Service name cannot be null");
        }
        if (this.serviceName.equals(serviceName)) {
            return;
        }

        // Re-register the service.
        try {
            componentManager.removeComponent(this.serviceName);
        }
        catch (Exception e) {
            componentManager.getLog().error(e);
        }
        try {
            componentManager.addComponent(serviceName, this);
        }
        catch (Exception e) {
            componentManager.getLog().error(e);
        }
        this.serviceName = serviceName;
    }

    /**
     * Returns a comma-delimitted list of strings into a Collection of Strings.
     *
     * @param str the String.
     * @return a list.
     */
    private List<JID> stringToList(String str) {
        List<JID> values = new ArrayList<JID>();
        StringTokenizer tokens = new StringTokenizer(str, ",");
        while (tokens.hasMoreTokens()) {
            String value = tokens.nextToken().trim();
            if (!value.equals("")) {
                // See if this is a full JID or just a username.
                if (value.contains("@")) {
                    values.add(new JID(value));
                }
                else {
                    values.add(XMPPServer.getInstance().createJID(value, null));
                }
            }
        }
        return values;
    }
}