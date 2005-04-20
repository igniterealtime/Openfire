/**
 * Copyright (C) 2005 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.plugin;

import java.io.File;
import java.util.Map;

import org.jivesoftware.messenger.SessionManager;
import org.jivesoftware.messenger.XMPPServer;
import org.jivesoftware.messenger.container.Plugin;
import org.jivesoftware.messenger.container.PluginManager;
import org.jivesoftware.messenger.event.UserEventDispatcher;
import org.jivesoftware.messenger.event.UserEventListener;
import org.jivesoftware.messenger.user.User;
import org.jivesoftware.util.JiveGlobals;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

public class RegistrationPlugin implements Plugin {
    private PluginManager pluginManager;
    private SessionManager sessionManager;
    private RegistrationUserEventListener listener = new RegistrationUserEventListener();
    
    private static String serverName;
    private boolean registrationNotificationEnabled;
    private boolean registrationWelcomeEnabled;
    private String contact;
    
    public RegistrationPlugin() {
        sessionManager = SessionManager.getInstance();
        serverName = XMPPServer.getInstance().getServerInfo().getName();
        
        registrationNotificationEnabled = JiveGlobals.getBooleanProperty("registration.notification.enabled", false);
        setRegistrationNotificationEnabled(registrationNotificationEnabled);
        
        registrationWelcomeEnabled = JiveGlobals.getBooleanProperty("registration.welcome.enabled", false);
        setRegistrationNotificationEnabled(registrationWelcomeEnabled);
        
        contact = JiveGlobals.getProperty("registration.notification.contact", "admin");
                
        UserEventDispatcher.addListener(listener);
    }

    public String getName() {
        return pluginManager.getName(this);
    }

    public String getDescription() {
        return pluginManager.getDescription(this);
    }

    public String getAuthor() {
        return pluginManager.getAuthor(this);
    }

    public String getVersion() {
        return pluginManager.getVersion(this);
    }
    
    public void processPacket(Packet packet) {
    }

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        pluginManager = manager;
    }

    public void destroyPlugin() {
        UserEventDispatcher.removeListener(listener);
        pluginManager = null;
        sessionManager = null;
    }
    
    public void setRegistrationNotificationEnabled(boolean enable) {
        JiveGlobals.setProperty("registration.notification.enabled", enable ? "true" : "false");
    }
    
    public boolean registrationNotificationEnabled() {
        return JiveGlobals.getBooleanProperty("registration.notification.enabled", false);
    }
    
    public void setContact(String contact) {
        this.contact = contact;
        JiveGlobals.setProperty("registration.notification.contact", contact);
    }
    
    public String getContact() {
        return contact;
    }
    
    public void setRegistrationWelcomeEnabled(boolean enable) {
        JiveGlobals.setProperty("registration.welcome.enabled", enable ? "true" : "false");
    }
    
    public boolean registrationWelcomeEnabled() {
        return JiveGlobals.getBooleanProperty("registration.welcome.enabled", false);
    }
    
    public void setWelcomeMessage(String message) {
        JiveGlobals.setProperty("registration.welcome.message", message);
    }
    
    public String getWelcomeMessage() {
        return JiveGlobals.getProperty("registration.welcome.message", "Welcome to Jive Messenger!");
    }
    
    private void sendRegistrationNotificatonMessage(User user) {
        String msg = " A new user with the username of '" + user.getUsername() + "' just registered";
        
        sessionManager.sendServerMessage(new JID(getContact() + "@" + serverName),
                "Registration Notification",
                msg);
    }
    
    private void sendWelcomeMessage(User user) {
        sessionManager.sendServerMessage(new JID(user.getUsername() + "@" + serverName),
                "Welcome",
                getWelcomeMessage());
    }
    
    //TODO JM-170
    //TODO add the ability for the admin to monitor when users are deleted?
    private class RegistrationUserEventListener implements UserEventListener {
        public void userCreated(User user, Map params) {
            if (registrationNotificationEnabled) {
                sendRegistrationNotificatonMessage(user);
            }
            
            if (registrationWelcomeEnabled) {
                sendWelcomeMessage(user);
            }
        }

        public void userDeleting(User user, Map params) {           
        }

        public void userModified(User user, Map params) {
        }
    }
}
