package com.tempstop;

import org.jivesoftware.openfire.MessageRouter;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.PresenceManager;
import org.jivesoftware.openfire.vcard.VCardManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.EmailService;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

//import java.io.File;
import java.io.*;

public class emailOnAway implements Plugin, PacketInterceptor {

    private InterceptorManager interceptorManager;
    private UserManager userManager;
    private PresenceManager presenceManager;
    private EmailService emailService;
    private MessageRouter messageRouter;
    private VCardManager vcardManager;
    
    public emailOnAway() {
        interceptorManager = InterceptorManager.getInstance();
    emailService = EmailService.getInstance();
        messageRouter = XMPPServer.getInstance().getMessageRouter();
    presenceManager = XMPPServer.getInstance().getPresenceManager();
    userManager = XMPPServer.getInstance().getUserManager();
    vcardManager = VCardManager.getInstance();
    }

    public void initializePlugin(PluginManager pManager, File pluginDirectory) {
        // register with interceptor manager
        interceptorManager.addInterceptor(this);
    }

    public void destroyPlugin() {
        // unregister with interceptor manager
        interceptorManager.removeInterceptor(this);
    }

    private Message createServerMessage(String to, String from, String emailTo) {
        Message message = new Message();
        message.setTo(to);
        message.setFrom(from);
        message.setSubject("I'm away");
        if (JiveGlobals.getBooleanProperty("plugin.emailonaway.showemail", true)) { 
            message.setBody("I'm currently away. Your message has been forwarded to my email address  (" + emailTo + ")."); 
            } else { message.setBody("I'm currently away. Your message has been forwarded to my email address."); }
        return message;
    }

    public void interceptPacket(Packet packet, Session session, boolean read,
            boolean processed) throws PacketRejectedException {
    
    String emailTo = null;
    String emailFrom = null;

    if((!processed) && 
        (!read) && 
        (packet instanceof Message) && 
        (packet.getTo() != null)) { 

        Message msg = (Message) packet;
        
        if(msg.getType() == Message.Type.chat) {
        try {
            User userTo = userManager.getUser(packet.getTo().getNode());
            if(presenceManager.getPresence(userTo).toString().toLowerCase().indexOf("away") != -1) {
            // Status isn't away
            if(msg.getBody() != null) {
                // Build email/sms
                // The to email address
                emailTo = vcardManager.getVCardProperty(userTo.getUsername(), "EMAIL");
                if(emailTo == null || emailTo.length() == 0) {
                emailTo = vcardManager.getVCardProperty(userTo.getUsername(), "EMAIL:USERID");
                if(emailTo == null || emailTo.length() == 0) {
                    emailTo = userTo.getEmail();
                    if(emailTo == null || emailTo.length() == 0) {
                    emailTo = packet.getTo().getNode() + "@" + packet.getTo().getDomain();
                    }
                }
                }
                // The From email address
                User userFrom = userManager.getUser(packet.getFrom().getNode());
                emailFrom = vcardManager.getVCardProperty(userFrom.getUsername(), "EMAIL");
                if(emailFrom == null || emailFrom.length() == 0) {
                emailFrom = vcardManager.getVCardProperty(userFrom.getUsername(), "EMAIL:USERID");
                if(emailFrom == null || emailFrom.length() == 0) {
                    emailFrom = userFrom.getEmail();
                    if(emailFrom == null || emailFrom.length() == 0) {
                    emailFrom = packet.getFrom().getNode() + "@" + packet.getFrom().getDomain();
                    }
                }
                }

//			    System.err.println(vcardManager.getVCardProperty(userTo.getUsername(), "EMAIL:USERID"));
                // Send email/sms
                // if this is an sms... modify the recipient address
                emailService.sendMessage(userTo.getName(), 
                emailTo, 
                userFrom.getName(), 
                emailFrom,
                "IM",
                msg.getBody(), 
                null);
                
                // Notify the sender that this went to email/sms
                messageRouter.route(createServerMessage(packet.getFrom().getNode() + "@" + packet.getFrom().getDomain(), packet.getTo().getNode() + "@" + packet.getTo().getDomain(), emailTo));

            }
            }
        } catch (UserNotFoundException e) {
        }
        }
    }
    }
}
