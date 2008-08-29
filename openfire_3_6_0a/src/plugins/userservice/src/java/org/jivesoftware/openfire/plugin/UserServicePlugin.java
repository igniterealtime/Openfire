/**
 * $Revision: 1722 $
 * $Date: 2005-07-28 15:19:16 -0700 (Thu, 28 Jul 2005) $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.plugin;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.PropertyEventListener;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.xmpp.packet.JID;

import java.io.File;
import java.util.*;

/**
 * Plugin that allows the administration of users via HTTP requests.
 *
 * @author Justin Hunt
 */
public class UserServicePlugin implements Plugin, PropertyEventListener {
    private UserManager userManager;
    private XMPPServer server;

    private String secret;
    private boolean enabled;
    private Collection<String> allowedIPs;

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        server = XMPPServer.getInstance();
        userManager = server.getUserManager();

        secret = JiveGlobals.getProperty("plugin.userservice.secret", "");
        // If no secret key has been assigned to the user service yet, assign a random one.
        if (secret.equals("")){
            secret = StringUtils.randomString(8);
            setSecret(secret);
        }
        
        // See if the service is enabled or not.
        enabled = JiveGlobals.getBooleanProperty("plugin.userservice.enabled", false);

        // Get the list of IP addresses that can use this service. An empty list means that this filter is disabled.
        allowedIPs = StringUtils.stringToCollection(JiveGlobals.getProperty("plugin.userservice.allowedIPs", ""));

        // Listen to system property events
        PropertyEventDispatcher.addListener(this);
    }

    public void destroyPlugin() {
        userManager = null;
        // Stop listening to system property events
        PropertyEventDispatcher.removeListener(this);
    }

    public void createUser(String username, String password, String name, String email, String groupNames)
            throws UserAlreadyExistsException
    {
        userManager.createUser(username, password, name, email);

        if (groupNames != null) {
            Collection<Group> groups = new ArrayList<Group>();
            StringTokenizer tkn = new StringTokenizer(groupNames, ",");
            while (tkn.hasMoreTokens()) {
                try {
                    groups.add(GroupManager.getInstance().getGroup(tkn.nextToken()));
                } catch (GroupNotFoundException e) {
                    // Ignore this group
                }
            }
            for (Group group : groups) {
                group.getMembers().add(server.createJID(username, null));
            }
        }
    }
    
    public void deleteUser(String username) throws UserNotFoundException{
        User user = getUser(username);
        userManager.deleteUser(user);
    }
    
    public void updateUser(String username, String password, String name, String email, String groupNames)
            throws UserNotFoundException
    {
        User user = getUser(username);
        user.setPassword(password);
        user.setName(name);
        user.setEmail(email);

        if (groupNames != null) {
            Collection<Group> newGroups = new ArrayList<Group>();
            StringTokenizer tkn = new StringTokenizer(groupNames, ",");
            while (tkn.hasMoreTokens()) {
                try {
                    newGroups.add(GroupManager.getInstance().getGroup(tkn.nextToken()));
                } catch (GroupNotFoundException e) {
                    // Ignore this group
                }
            }

            Collection<Group> existingGroups = GroupManager.getInstance().getGroups(user);
            // Get the list of groups to add to the user
            Collection<Group> groupsToAdd =  new ArrayList<Group>(newGroups);
            groupsToAdd.removeAll(existingGroups);
            // Get the list of groups to remove from the user
            Collection<Group> groupsToDelete =  new ArrayList<Group>(existingGroups);
            groupsToDelete.removeAll(newGroups);

            // Add the user to the new groups
            for (Group group : groupsToAdd) {
                group.getMembers().add(server.createJID(username, null));
            }
            // Remove the user from the old groups
            for (Group group : groupsToDelete) {
                group.getMembers().remove(server.createJID(username, null));
            }
        }
    }
    
    /**
     * Returns the the requested user or <tt>null</tt> if there are any
     * problems that don't throw an error.
     *
     * @param username the username of the local user to retrieve.
     * @return the requested user.
     * @throws UserNotFoundException if the requested user
     *         does not exist in the local server.
     */
    private User getUser(String username) throws UserNotFoundException {
        JID targetJID = server.createJID(username, null);
        // Check that the sender is not requesting information of a remote server entity
        if (targetJID.getNode() == null) {
            // Sender is requesting presence information of an anonymous user
            throw new UserNotFoundException("Username is null");
        }
        return userManager.getUser(targetJID.getNode());
    }
    
    /**
     * Returns the secret key that only valid requests should know.
     *
     * @return the secret key.
     */
    public String getSecret() {
        return secret;
    }

    /**
     * Sets the secret key that grants permission to use the userservice.
     *
     * @param secret the secret key.
     */
    public void setSecret(String secret) {
        JiveGlobals.setProperty("plugin.userservice.secret", secret);
        this.secret = secret;
    }

    public Collection<String> getAllowedIPs() {
        return allowedIPs;
    }

    public void setAllowedIPs(Collection<String> allowedIPs) {
        JiveGlobals.setProperty("plugin.userservice.allowedIPs", StringUtils.collectionToString(allowedIPs));
        this.allowedIPs = allowedIPs;
    }

    /**
     * Returns true if the user service is enabled. If not enabled, it will not accept
     * requests to create new accounts.
     *
     * @return true if the user service is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables or disables the user service. If not enabled, it will not accept
     * requests to create new accounts.
     *
     * @param enabled true if the user service should be enabled.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        JiveGlobals.setProperty("plugin.userservice.enabled",  enabled ? "true" : "false");
    }

    public void propertySet(String property, Map<String, Object> params) {
        if (property.equals("plugin.userservice.secret")) {
            this.secret = (String)params.get("value");
        }
        else if (property.equals("plugin.userservice.enabled")) {
            this.enabled = Boolean.parseBoolean((String)params.get("value"));
        }
        else if (property.equals("plugin.userservice.allowedIPs")) {
            this.allowedIPs = StringUtils.stringToCollection((String)params.get("value"));
        }
    }

    public void propertyDeleted(String property, Map<String, Object> params) {
        if (property.equals("plugin.userservice.secret")) {
            this.secret = "";
        }
        else if (property.equals("plugin.userservice.enabled")) {
            this.enabled = false;
        }
        else if (property.equals("plugin.userservice.allowedIPs")) {
            this.allowedIPs = Collections.emptyList();
        }
    }

    public void xmlPropertySet(String property, Map<String, Object> params) {
        // Do nothing
    }

    public void xmlPropertyDeleted(String property, Map<String, Object> params) {
        // Do nothing
    }
}