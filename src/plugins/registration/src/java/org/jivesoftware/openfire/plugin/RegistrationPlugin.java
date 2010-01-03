/**
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

package org.jivesoftware.openfire.plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jivesoftware.admin.AuthCheckFilter;
import org.jivesoftware.openfire.MessageRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.event.UserEventDispatcher;
import org.jivesoftware.openfire.event.UserEventListener;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.util.EmailService;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

/**
 * Registration plugin.
 *
 * @author Ryan Graham.
 */
public class RegistrationPlugin implements Plugin {
    
    private static final Logger Log = LoggerFactory.getLogger(RegistrationPlugin.class);
    
    private static final String URL = "registration/sign-up.jsp";
   
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
     * The expected value is a boolean, if true any users will be able to register at the following
     * url http://[SERVER_NAME}:9090/plugins/registration/sign-up.jsp
     */
    private static final String WEB_ENABLED = "registration.web.enabled";
    
    /**
      * The expected value is a boolean, if true any users will be need to verify its a human at the
     * following url http://[SERVER_NAME}:9090/plugins/registration/sign-up.jsp
     */
    private static final String RECAPTCHA_ENABLED = "registration.recaptcha.enabled";
    
    /**
     * The expected value is a boolean, if true recaptcha uses the noscript tag.
     */
    private static final String RECAPTCHA_NOSCRIPT = "registration.recaptcha.noscript";
    
    /**
     * The expected value is a String that contains the public key for the recaptcha login.
     */
    private static final String RECAPTCHA_PUBLIC_KEY = "registration.recaptcha.key.public";
    
    /**
     * The expected value is a String that contains the private key for the recaptcha login.
     */
    private static final String RECAPTCHA_PRIVATE_KEY = "registration.recaptcha.key.private";
    
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
    
    /**
     * The expected value is a String that contains the text that will be displayed in the header
     * of the sign-up.jsp, if the property #WEB_ENABLED is set to true.
     */
    private static final String HEADER = "registration.header";

    private RegistrationUserEventListener listener = new RegistrationUserEventListener();
    
    private String serverName;
    private JID serverAddress;
    private MessageRouter router;
    
    private List<String> imContacts = new ArrayList<String>();
    private List<String> emailContacts = new ArrayList<String>();
    
    public RegistrationPlugin() {
        serverName = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
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

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        AuthCheckFilter.addExclude(URL);
    }

    public void destroyPlugin() {
        AuthCheckFilter.removeExclude(URL);
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
        return JiveGlobals.getProperty(WELCOME_MSG, "Welcome to Openfire!");
    }
    
    public void setGroupEnabled(boolean enable) {
        JiveGlobals.setProperty(GROUP_ENABLED, enable ? "true" : "false");
    }
    
    public boolean groupEnabled() {
        return JiveGlobals.getBooleanProperty(GROUP_ENABLED, false);
    }
    
    public void setWebEnabled(boolean enable) {
        JiveGlobals.setProperty(WEB_ENABLED, enable ? "true" : "false");
    }
   
    public boolean webEnabled() {
        return JiveGlobals.getBooleanProperty(WEB_ENABLED, false);
    }
    
    public String webRegistrationAddress() {
        return  "http://" + XMPPServer.getInstance().getServerInfo().getXMPPDomain() + ":"
            + JiveGlobals.getXMLProperty("adminConsole.port") + "/plugins/" + URL;
    }
    
    public void setReCaptchaEnabled(boolean enable) {
        JiveGlobals.setProperty(RECAPTCHA_ENABLED, enable ? "true" : "false");
    }
    
    public boolean reCaptchaEnabled() {
        return JiveGlobals.getBooleanProperty(RECAPTCHA_ENABLED, false);
    }
    
    public void setReCaptchaNoScript(boolean enable) {
        JiveGlobals.setProperty(RECAPTCHA_NOSCRIPT, enable ? "true" : "false");
    }
    
    public boolean reCaptchaNoScript() {
        return JiveGlobals.getBooleanProperty(RECAPTCHA_NOSCRIPT, true);
    }
    
    public void setReCaptchaPublicKey(String publicKey) {
        JiveGlobals.setProperty(RECAPTCHA_PUBLIC_KEY, publicKey);
    }
    
    public String getReCaptchaPublicKey() {
        return JiveGlobals.getProperty(RECAPTCHA_PUBLIC_KEY);
    }
    
    public void setReCaptchaPrivateKey(String privateKey) {
        JiveGlobals.setProperty(RECAPTCHA_PRIVATE_KEY, privateKey);
    }
    
    public String getReCaptchaPrivateKey() {
        return JiveGlobals.getProperty(RECAPTCHA_PRIVATE_KEY);
    }
    
    public void setGroup(String group) {
        JiveGlobals.setProperty(REGISTRAION_GROUP, group);
    }
    
    public String getGroup() {
        return JiveGlobals.getProperty(REGISTRAION_GROUP);
    }
    
    public void setHeader(String message) {
        JiveGlobals.setProperty(HEADER, message);
    }

    public String getHeader() {
        return JiveGlobals.getProperty(HEADER, "Web Sign-In");
    }
    
    private class RegistrationUserEventListener implements UserEventListener {
        public void userCreated(User user, Map<String, Object> params) {
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

        public void userDeleting(User user, Map<String, Object> params) {
        }

        public void userModified(User user, Map<String, Object> params) {
        }
        
        private void sendIMNotificatonMessage(User user) {
            String msg = " A new user with the username '" + user.getUsername() + "' just registered.";
            
            for (String contact : getIMContacts()) {
                router.route(createServerMessage(contact + "@" + serverName,
                            "Registration Notification", msg));
            }
        }
        
        private void sendAlertEmail(User user) {
            String subject = "User Registration";
            String body = " A new user with the username '" + user.getUsername() + "' just registered.";
            
            EmailService emailService = EmailService.getInstance();
            for (String toAddress : emailContacts) {
               try {
                   emailService.sendMessage(null, toAddress, "Openfire", "no_reply@" + serverName,
                           subject, body, null);
               }
               catch (Exception e) {
                   Log.error(e.getMessage(), e);
               }
           }
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
                group.getMembers().add(XMPPServer.getInstance().createJID(user.getUsername(), null));
            }
            catch (GroupNotFoundException e) {
                Log.error(e.getMessage(), e);
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

        // Must at least match x@x.xx. 
        return address.matches(".{1,}[@].{1,}[.].{2,}");
    }
}
