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

package org.jivesoftware.openfire.plugin;

import org.dom4j.Element;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
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
        boolean targetAll = "all".equals(toNode);
        if (targetAll) {
            // See if the user is allowed to send the packet.
            JID address = new JID(packet.getFrom().toBareJID());
            if (allowedUsers.isEmpty() || allowedUsers.contains(address)) {
                canProceed = true;
            }
        }
        else {
            try {
                if (toNode != null) {
                    group = groupManager.getGroup(toNode);
                    boolean isGroupUser = group.isUser(packet.getFrom()) ||
                            group.isUser(new JID(packet.getFrom().toBareJID()));
                    if (disableGroupPermissions || (groupMembersAllowed && isGroupUser) ||
                            allowedUsers.contains(new JID(packet.getFrom().toBareJID()))) {
                        canProceed = true;
                    }
                }
            }
            catch (GroupNotFoundException e) {
                // Ignore.
            }
        }
        if (packet instanceof Message) {
            // Respond to incoming messages
            Message message = (Message)packet;
            processMessage(message, targetAll, group, canProceed);
        }
        else if (packet instanceof Presence) {
            // Respond to presence subscription request or presence probe
            Presence presence = (Presence) packet;
            processPresence(canProceed, presence);
        }
        else if (packet instanceof IQ) {
            // Handle disco packets
            IQ iq = (IQ) packet;
            // Ignore IQs of type ERROR or RESULT
            if (IQ.Type.error == iq.getType() || IQ.Type.result == iq.getType()) {
                return;
            }
            processIQ(iq, targetAll, group, canProceed);
        }
    }

    private void processMessage(Message message, boolean targetAll, Group group,
            boolean canProceed) {
        // Check to see if trying to broadcast to all connected users.
        if (targetAll) {
            if (!canProceed) {
                Message error = new Message();
                if (message.getID() != null) {
                    error.setID(message.getID());
                }
                error.setError(PacketError.Condition.not_allowed);
                error.setTo(message.getFrom());
                error.setFrom(message.getTo());
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
                error.setFrom(message.getTo());
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
                error.setFrom(message.getTo());
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

    private void processPresence(boolean canProceed, Presence presence) {
        try {
            if (Presence.Type.subscribe == presence.getType()) {
                // Accept all presence requests if user has permissions
                // Reply that the subscription request was approved or rejected
                Presence reply = new Presence();
                reply.setTo(presence.getFrom());
                reply.setFrom(presence.getTo());
                reply.setType(canProceed ? Presence.Type.subscribed : Presence.Type.unsubscribed);
                componentManager.sendPacket(this, reply);
            }
            else if (Presence.Type.unsubscribe == presence.getType()) {
                // Send confirmation of unsubscription
                Presence reply = new Presence();
                reply.setTo(presence.getFrom());
                reply.setFrom(presence.getTo());
                reply.setType(Presence.Type.unsubscribed);
                componentManager.sendPacket(this, reply);
                if (!canProceed) {
                    // Send unavailable presence of the service
                    reply = new Presence();
                    reply.setTo(presence.getFrom());
                    reply.setFrom(presence.getTo());
                    reply.setType(Presence.Type.unavailable);
                    componentManager.sendPacket(this, reply);
                }
            }
            else if (Presence.Type.probe == presence.getType()) {
                // Send that the service is available
                Presence reply = new Presence();
                reply.setTo(presence.getFrom());
                reply.setFrom(presence.getTo());
                if (!canProceed) {
                    // Send forbidden error since user is not allowed
                    reply.setError(PacketError.Condition.forbidden);
                }
                componentManager.sendPacket(this, reply);
            }
        }
        catch (ComponentException e) {
            componentManager.getLog().error(e);
        }
    }

    private void processIQ(IQ iq, boolean targetAll, Group group,
            boolean canProceed) {
        IQ reply = IQ.createResultIQ(iq);
        Element childElement = iq.getChildElement();
        String namespace = childElement.getNamespaceURI();
        Element childElementCopy = iq.getChildElement().createCopy();
        reply.setChildElement(childElementCopy);
        if ("http://jabber.org/protocol/disco#info".equals(namespace)) {
            if (iq.getTo().getNode() == null) {
                // Return service identity and features
                Element identity = childElementCopy.addElement("identity");
                identity.addAttribute("category", "component");
                identity.addAttribute("type", "generic");
                identity.addAttribute("name", "Broadcast service");
                childElementCopy.addElement("feature")
                        .addAttribute("var", "http://jabber.org/protocol/disco#info");
                childElementCopy.addElement("feature")
                        .addAttribute("var", "http://jabber.org/protocol/disco#items");
            }
            else {
                if (targetAll) {
                    // Return identity and features of the "all" group
                    Element identity = childElementCopy.addElement("identity");
                    identity.addAttribute("category", "component");
                    identity.addAttribute("type", "generic");
                    identity.addAttribute("name", "Broadcast all connected users");
                    childElementCopy.addElement("feature")
                            .addAttribute("var", "http://jabber.org/protocol/disco#info");
                }
                else if (group != null && canProceed) {
                    // Return identity and features of the "all" group
                    Element identity = childElementCopy.addElement("identity");
                    identity.addAttribute("category", "component");
                    identity.addAttribute("type", "generic");
                    identity.addAttribute("name", "Broadcast " + group.getName());
                    childElementCopy.addElement("feature")
                            .addAttribute("var", "http://jabber.org/protocol/disco#info");
                }
                else {
                    // Group not found or not allowed to use that group so
                    // answer item_not_found error
                    reply.setError(PacketError.Condition.item_not_found);
                }
            }
        }
        else if ("http://jabber.org/protocol/disco#items".equals(namespace)) {
            if (iq.getTo().getNode() == null) {
                // Return the list of groups hosted by the service that can be used by the user
                Collection<Group> groups;
                JID address = new JID(iq.getFrom().toBareJID());
                if (allowedUsers.contains(address)) {
                    groups = groupManager.getGroups();
                }
                else {
                    groups = groupManager.getGroups(iq.getFrom());
                }
                for (Group userGroup : groups) {
                    try {
                        JID groupJID = new JID(userGroup.getName() + "@" + serviceName + "." +
                                componentManager.getServerName());
                        childElementCopy.addElement("item")
                                .addAttribute("jid", groupJID.toString());
                    }
                    catch (Exception e) {
                        // Group name is not valid to be used as a JID
                    }
                }
                if (allowedUsers.isEmpty() || allowedUsers.contains(address)) {
                    // Add the "all" group to the list
                    childElementCopy.addElement("item").addAttribute("jid",
                            "all@" + serviceName + "." + componentManager.getServerName());
                }
            }
        }
        else {
            // Answer an error since the server can't handle the requested namespace
            reply.setError(PacketError.Condition.service_unavailable);
        }
        try {
            componentManager.sendPacket(this, reply);
        }
        catch (Exception e) {
            componentManager.getLog().error(e);
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