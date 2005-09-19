/**
 * Copyright (C) 2005 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.plugin;

import org.jivesoftware.messenger.MessageRouter;
import org.jivesoftware.messenger.XMPPServer;
import org.jivesoftware.messenger.container.Plugin;
import org.jivesoftware.messenger.container.PluginManager;
import org.jivesoftware.messenger.event.UserEventDispatcher;
import org.jivesoftware.messenger.event.UserEventListener;
import org.jivesoftware.messenger.group.Group;
import org.jivesoftware.messenger.group.GroupManager;
import org.jivesoftware.messenger.group.GroupNotFoundException;
import org.jivesoftware.messenger.user.User;
import org.jivesoftware.util.EmailService;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeUtility;

/**
 * Registration plugin.
 *
 * @author Ryan Graham.
 */
public class RegistrationPlugin implements Plugin {
    /**
     * The expected value is a boolean, if true all contacts specified in the property #IM_CONTACTS
     * will receive a notification when a new user registers. The default value is false.
     */
    private static final String IM_NOTIFICATION_ENABLED = "registration.imnotification.enabled";
    
    /**
     * The expected value is a boolean, if true all contacts specified in the property #EMAIL_CONTACTS 
     * will receive a notification when a new user registers. The default value is false.
     */
    private static final String EMAIL_NOTIFICATION_ENABLED = "registration.emailnotification.enabled";
    
    /**
     * The expected value is a boolean, if true any user who registers will receive the welcome 
     * message specified in the property #WELCOME_MSG. The default value is false.
     */
    private static final String WELCOME_ENABLED = "registration.welcome.enabled";
    
    /**
     * The expected value is a boolean, if true any user who registers will be added to the group 
     * specified in the property #REGISTRAION_GROUP. The default value is false.
     */
    private static final String GROUP_ENABLED = "registration.group.enabled";
    
    /**
     * The expected value is a comma separated String of usernames who will receive a instant
     * message when a new user registers if the property #IM_NOTIFICATION_ENABLED is set to true.
     */
    private static final String IM_CONTACTS = "registration.notification.imContacts";
    
    /**
     * The expected value is a comma separated String of email addresses who will receive an email
     * when a new user registers, if the property #EMAIL_NOTIFICATION_ENABLED is set to true.
     */
    private static final String EMAIL_CONTACTS = "registration.notification.emailContacts";
    
    /**
     * The expected value is a String that contains the message that will be sent to a new user
     * when they register, if the property #WELCOME_ENABLED is set to true.
     */
    private static final String WELCOME_MSG = "registration.welcome.message";
    
    /**
     * The expected value is a String that contains the name of the group that a new user will 
     * be added to when they register, if the property #GROUP_ENABLED is set to true.
     */
    private static final String REGISTRAION_GROUP = "registration.group";

    private RegistrationUserEventListener listener = new RegistrationUserEventListener();
    
    private String serverName;
    private JID serverAddress;
    private MessageRouter router;
    
    private List<String> imContacts = new ArrayList<String>();
    private List<String> emailContacts = new ArrayList<String>();
    
    public RegistrationPlugin() {
        serverName = XMPPServer.getInstance().getServerInfo().getName();
        serverAddress = new JID(serverName);
        router = XMPPServer.getInstance().getMessageRouter();
       
        String imcs = JiveGlobals.getProperty(IM_CONTACTS);
        if (imcs != null) {
             imContacts.addAll(Arrays.asList(imcs.split(",")));
        }
         
        String ecs = JiveGlobals.getProperty(EMAIL_CONTACTS);
        if (ecs != null) {
            emailContacts.addAll(Arrays.asList(ecs.split(",")));
        }
                
        UserEventDispatcher.addListener(listener);
        
        //delete properties from version 1.0
        JiveGlobals.deleteProperty("registration.notification.contact");
        JiveGlobals.deleteProperty("registration.notification.enabled");
    }

    public void processPacket(Packet packet) {
    }

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
    }

    public void destroyPlugin() {
        UserEventDispatcher.removeListener(listener);
        serverAddress = null;
        listener = null;
        router = null;
    }
    
    public void setIMNotificationEnabled(boolean enable) {
        JiveGlobals.setProperty(IM_NOTIFICATION_ENABLED, enable ? "true" : "false");
    }
    
    public boolean imNotificationEnabled() {
        return JiveGlobals.getBooleanProperty(IM_NOTIFICATION_ENABLED, false);
    }
    
    public void setEmailNotificationEnabled(boolean enable) {
        JiveGlobals.setProperty(EMAIL_NOTIFICATION_ENABLED, enable ? "true" : "false");
    }
    
    public boolean emailNotificationEnabled() {
        return JiveGlobals.getBooleanProperty(EMAIL_NOTIFICATION_ENABLED, false);
    }
    
    public Collection<String> getIMContacts() {
        Collections.sort(imContacts);
        return imContacts;
    }
   
    public void addIMContact(String contact) {
        if (!imContacts.contains(contact.trim().toLowerCase())) {
            imContacts.add(contact.trim().toLowerCase());
            JiveGlobals.setProperty(IM_CONTACTS, propPrep(imContacts));
        }
    }

    public void removeIMContact(String contact) {
        imContacts.remove(contact.trim().toLowerCase());
        if (imContacts.size() == 0) {
            JiveGlobals.deleteProperty(IM_CONTACTS);
        }
        else {
            JiveGlobals.setProperty(IM_CONTACTS, propPrep(imContacts));
        }
    }

    public Collection<String> getEmailContacts() {
        Collections.sort(emailContacts);
        return emailContacts;
    }

    public void addEmailContact(String contact) {
        if (!emailContacts.contains(contact.trim())) {
            emailContacts.add(contact.trim());
            JiveGlobals.setProperty(EMAIL_CONTACTS, propPrep(emailContacts));
        }
    }

    public void removeEmailContact(String contact) {
        emailContacts.remove(contact);
        if (emailContacts.size() == 0) {
            JiveGlobals.deleteProperty(EMAIL_CONTACTS);
        }
        else {
            JiveGlobals.setProperty(EMAIL_CONTACTS, propPrep(emailContacts));
        }
    }
    
    public void setWelcomeEnabled(boolean enable) {
       JiveGlobals.setProperty(WELCOME_ENABLED, enable ? "true" : "false");
    }
   
    public boolean welcomeEnabled() {
        return JiveGlobals.getBooleanProperty(WELCOME_ENABLED, false);
    }

    public void setWelcomeMessage(String message) {
        JiveGlobals.setProperty(WELCOME_MSG, message);
    }

    public String getWelcomeMessage() {
        return JiveGlobals.getProperty(WELCOME_MSG, "Welcome to Jive Messenger!");
    }
    
    public void setGroupEnabled(boolean enable) {
        JiveGlobals.setProperty(GROUP_ENABLED, enable ? "true" : "false");
    }
    
    public boolean groupEnabled() {
        return JiveGlobals.getBooleanProperty(GROUP_ENABLED, false);
    }
    
    public void setGroup(String group) {
        JiveGlobals.setProperty(REGISTRAION_GROUP, group);
    }
    
    public String getGroup() {
        return JiveGlobals.getProperty(REGISTRAION_GROUP);
    }
    
    private class RegistrationUserEventListener implements UserEventListener {
        public void userCreated(User user, Map params) {
            if (imNotificationEnabled()) {
                sendIMNotificatonMessage(user);
            }
            
            if (emailNotificationEnabled()) {
                sendAlertEmail(user);
            }
            
            if (welcomeEnabled()) {
                sendWelcomeMessage(user);
            }
            
            if (groupEnabled()) {
                addUserToGroup(user);
            }
        }

        public void userDeleting(User user, Map params) {           
        }

        public void userModified(User user, Map params) {
        }
        
        private void sendIMNotificatonMessage(User user) {
            String msg = " A new user with the username of '" + user.getUsername() + "' just registered";
            
            for (String contact : getIMContacts()) {
                router.route(createServerMessage(contact + "@" + serverName,
                            "Registration Notification", msg));
            }
        }
        
        private void sendAlertEmail(User user) {
            String msg = " A new user with the username of '" + user.getUsername() + "' just registered";
            
            List<MimeMessage> messages = new ArrayList<MimeMessage>();
            EmailService emailService = EmailService.getInstance();
            MimeMessage message = emailService.createMimeMessage();
            String encoding = MimeUtility.mimeCharset("iso-8859-1");
            for (String toAddress : emailContacts) {
               try {
                   message.setRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(toAddress));
                   message.setFrom(new InternetAddress("no_reply@" + serverName, "Jive Messneger", encoding));
                   message.setText(msg);
                   message.setSubject("User Registration");
                   
                   messages.add(message);
               } catch (Exception e) {
                   Log.error(e);
               }
           }
            
            emailService.sendMessages(messages);
        }
        
        private void sendWelcomeMessage(User user) {
            router.route(createServerMessage(user.getUsername() + "@" + serverName, "Welcome",
                    getWelcomeMessage()));
        }
        
        private Message createServerMessage(String to, String subject, String body) {
            Message message = new Message();
            message.setTo(to);
            message.setFrom(serverAddress);
            if (subject != null) {
                message.setSubject(subject);
            }
            message.setBody(body);
            return message;
        }
        
        private void addUserToGroup(User user) {
            try {
                GroupManager groupManager =  GroupManager.getInstance();
                Group group = groupManager.getGroup(getGroup());
                group.getMembers().add(user.getUsername());
            }
            catch (GroupNotFoundException e) {
                Log.error(e);
            }
        }
    }
    
    private String propPrep(Collection<String> props) {
        StringBuilder buf = new StringBuilder();
        Iterator<String> iter = props.iterator();
        while (iter.hasNext()) {
            String con = iter.next();
            buf.append(con);
            
            if (iter.hasNext()) {
                buf.append(",");
            }
        }
        return buf.toString();
    }
    
    public boolean isValidAddress(String address) {
        if (address == null) {
            return false;
        }

        //must at least match x@x.xx 
        if (!address.matches(".{1,}[@].{1,}[.].{2,}")) {
            return false;
        }

        return true;
    }
}
