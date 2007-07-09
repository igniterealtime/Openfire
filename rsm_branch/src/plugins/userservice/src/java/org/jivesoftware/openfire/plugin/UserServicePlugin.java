/**
 * $Revision: 1722 $
 * $Date: 2005-07-28 15:19:16 -0700 (Thu, 28 Jul 2005) $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.plugin;

import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.StringUtils;
import org.xmpp.packet.JID;

import java.io.File;

/**
 * Plugin that allows the administration of users via HTTP requests.
 *
 * @author Justin Hunt
 */
public class UserServicePlugin implements Plugin {

    private UserManager userManager;
    private String hostname;
    private String secret;
    private boolean enabled;

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        XMPPServer server = XMPPServer.getInstance();
        userManager = server.getUserManager();
        hostname = server.getServerInfo().getName();

        secret = JiveGlobals.getProperty("plugin.userservice.secret", "");
        // If no secret key has been assigned to the user service yet, assign a random one.
        if (secret.equals("")){
            secret = StringUtils.randomString(8);
            setSecret(secret);
        }
        
        // See if the service is enabled or not.
        enabled = JiveGlobals.getBooleanProperty("plugin.userservice.enabled", false);
    }

    public void destroyPlugin() {
        userManager = null;
    }

    public void createUser(String username, String password, String name, String email)
            throws UserAlreadyExistsException
    {
        userManager.createUser(username, password, name, email);                
    }
    
    public void deleteUser(String jid) throws UserNotFoundException{
        User user = getUser(jid);
        userManager.deleteUser(user);
    }
    
    public void updateUser(String jid, String password, String name, String email)
            throws UserNotFoundException
    {
        User user = getUser(jid);
        user.setPassword(password);
        user.setName(name);
        user.setEmail(email);
    }
    
    /**
     * Returns the the requested user or <tt>null</tt> if there are any
     * problems that don't throw an error.
     *
     * @param jid the bare JID of the entity whose presence is being probed.
     * @return the requested user.
     * @throws UserNotFoundException if the requested user
     *         does not exist in the local server.
     */
    private User getUser(String jid) throws UserNotFoundException {
        JID targetJID = new JID(jid);
        // Check that the sender is not requesting information of a remote server entity
        if (targetJID.getDomain() == null || XMPPServer.getInstance().isRemote(targetJID)) {
            throw new UserNotFoundException("Domain does not matches local server domain");
        }
        if (!hostname.equals(targetJID.getDomain())) {
            // Sender is requesting information about component presence
            // TODO Implement this
            throw new UserNotFoundException("Presence of components not supported yet!");
        }
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
}