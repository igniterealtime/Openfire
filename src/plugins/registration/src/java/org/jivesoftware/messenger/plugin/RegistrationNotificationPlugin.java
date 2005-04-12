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
import org.jivesoftware.messenger.SessionNotFoundException;
import org.jivesoftware.messenger.XMPPServer;
import org.jivesoftware.messenger.container.Plugin;
import org.jivesoftware.messenger.container.PluginManager;
import org.jivesoftware.messenger.event.UserEventDispatcher;
import org.jivesoftware.messenger.event.UserEventListener;
import org.jivesoftware.messenger.user.User;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

public class RegistrationNotificationPlugin implements Plugin {
    private PluginManager pluginManager;
    private SessionManager sessionManager;
	private RegistrationUserEventListener listener = new RegistrationUserEventListener();
    
    private static String serverName;
    private boolean serviceEnabled;
    private String contact;
    
    public RegistrationNotificationPlugin() {
        sessionManager = SessionManager.getInstance();
        serverName = XMPPServer.getInstance().getServerInfo().getName();
        
        serviceEnabled = JiveGlobals.getBooleanProperty("registration.notification.enabled", true);
        setServiceEnabled(serviceEnabled);
        
        contact = JiveGlobals.getProperty("registration.notification.contact");
        if (contact == null) {
            contact = "admin";
            JiveGlobals.setProperty("registration.notification.contact", contact);
        }
		
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
    
    public void setServiceEnabled(boolean enable) {
        serviceEnabled = enable;
        JiveGlobals.setProperty("registration.notification.enabled", serviceEnabled ? "true" : "false");
    }
    
    public boolean serviceEnabled() {
        return "true".equals(JiveGlobals.getProperty("registration.notification.enabled"));
    }
    
    public void setContact(String contact) {
        this.contact = contact;
        JiveGlobals.setProperty("registration.notification.contact", contact);
    }
    
    public String getContact() {
        return contact;
    }
	
	//TODO JM-170
	//TODO add the ability to have a admin configurable messange sent to newly registered user
	//TODO add the ability for the admin to monitor when users are created and/or deleted?
	private class RegistrationUserEventListener implements UserEventListener {
		public void userCreated(User user, Map params) {
			String msg = " A new user with the username of '" + user.getUsername() + "' just registered";                    
            try {
                sessionManager.sendServerMessage(new JID(getContact() + "@" + serverName),
                        "Registration Notification",
                        msg);
            }
            catch (SessionNotFoundException e) {
                Log.error("SessionNotFoundException: could not send the following message to: "
                        + getContact()
                        + msg);
            }
		}

		public void userDeleting(User user, Map params) {			
		}

		public void userModified(User user, Map params) {
		}
	}
}
