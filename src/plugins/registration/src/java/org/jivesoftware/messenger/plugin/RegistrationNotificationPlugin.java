/**
 * Copyright (C) 2005 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.plugin;

import java.io.File;
import java.util.Iterator;

import org.dom4j.Element;
import org.jivesoftware.messenger.JiveGlobals;
import org.jivesoftware.messenger.Session;
import org.jivesoftware.messenger.SessionManager;
import org.jivesoftware.messenger.SessionNotFoundException;
import org.jivesoftware.messenger.XMPPServer;
import org.jivesoftware.messenger.container.Plugin;
import org.jivesoftware.messenger.container.PluginManager;
import org.jivesoftware.messenger.forms.spi.XDataFormImpl;
import org.jivesoftware.messenger.interceptor.InterceptorManager;
import org.jivesoftware.messenger.interceptor.PacketInterceptor;
import org.jivesoftware.util.Log;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

public class RegistrationNotificationPlugin implements Plugin {
    private RegistrationInterceptor interceptor;
    
    private PluginManager pluginManager;
    private SessionManager sessionManager;
    
    private static String serverName;
    private boolean serviceEnabled;
    private String contact;
    
    public RegistrationNotificationPlugin() {
        interceptor = new RegistrationInterceptor();        
        
        sessionManager = SessionManager.getInstance();
        serverName = XMPPServer.getInstance().getServerInfo().getName();
        
        serviceEnabled = JiveGlobals.getBooleanProperty("registration.notification.enabled", true);
        setServiceEnabled(serviceEnabled);
        
        contact = JiveGlobals.getProperty("registration.notification.contact");
        if (contact == null) {
            contact = "admin";
            JiveGlobals.setProperty("registration.notification.contact", contact);
        }
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
        InterceptorManager.getInstance().removeInterceptor(interceptor);
        pluginManager = null;
        sessionManager = null;
    }
    
    public void setServiceEnabled(boolean enable) {
        serviceEnabled = enable;
        JiveGlobals.setProperty("registration.notification.enabled", serviceEnabled ? "true" : "false");
        
        if (enable) {
            InterceptorManager.getInstance().addInterceptor(interceptor);
        } else {
            InterceptorManager.getInstance().removeInterceptor(interceptor);
        }        
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
    
    private void interceptRegistration(Packet packet) {
        if (packet instanceof IQ) {
            IQ iqPacket = (IQ) packet;
            
            if (IQ.Type.set.equals(iqPacket.getType())) {
                Element childElement = iqPacket.getChildElement();
                String namespace = null;
                if (childElement != null) {
                    namespace = childElement.getNamespaceURI();
                }
                
                if ("jabber:iq:register".equals(namespace)) {
                    //this is similiar to the logic used in IQRegisterHandler
                    XDataFormImpl registrationForm = null;
                    Element iqElement = iqPacket.getChildElement();
                    Element formElement = iqElement.element("x");
                    
                    String username = null;;
                    // Check if a form was used to provide the registration info
                    if (formElement != null) {
                        Iterator<String> values = registrationForm.getField("username").getValues();
                        username = (values.hasNext() ? values.next() : " ");
                    }
                    else {
                        // Get the registration info from the query elements
                        username = iqElement.elementText("username");
                    }
                    
                    String msg = " A new user with the username of '" + username + "' just attempted to register";                    
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
            }
        }
    }
    
    private class RegistrationInterceptor implements PacketInterceptor {
        public void interceptPacket(Packet packet, Session session, boolean read, boolean processed) {
            if (serviceEnabled()) {
                interceptRegistration(packet);
            }
        }
    }
}
