/**
 * $RCSfile$
 * $Revision: 1594 $
 * $Date: 2005-07-04 14:08:42 -0300 (Mon, 04 Jul 2005) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.plugin;

import org.jivesoftware.wildfire.MessageRouter;
import org.jivesoftware.wildfire.Session;
import org.jivesoftware.wildfire.XMPPServer;
import org.jivesoftware.wildfire.container.Plugin;
import org.jivesoftware.wildfire.container.PluginManager;
import org.jivesoftware.wildfire.interceptor.InterceptorManager;
import org.jivesoftware.wildfire.interceptor.PacketInterceptor;
import org.jivesoftware.wildfire.interceptor.PacketRejectedException;
import org.jivesoftware.util.JiveGlobals;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import java.io.File;

/**
 * Content filter plugin.
 *
 * @author Conor Hayes
 */
public class ContentFilterPlugin implements Plugin, PacketInterceptor {

    /**
     * The expected value is a boolean, if true the user identified by the value
     * of the property #VIOLATION_NOTIFICATION_CONTACT_PROPERTY will be notified
     * every time there is a content match, otherwise no notification will be
     * sent. Then default value is false.
     */
    public static final String VIOLATION_NOTIFICATION_ENABLED_PROPERTY =
            "plugin.contentFilter.violation.notification.enabled";

    /**
     * The expected value is a user name. The default value is "admin".
     */
    public static final String VIOLATION_NOTIFICATION_CONTACT_PROPERTY =
            "plugin.contentFilter.violation.notification.contact";

    /**
     * The expected value is a boolean, if true the sender will be notified when a
     * message is rejected, otherwise the message will be silently rejected,i.e. the
     * sender will not know that the message was rejected and the receiver will
     * not get the message. The default value is false.
     */
    public static final String REJECTION_NOTIFICATION_ENABLED_PROPERTY =
            "plugin.contentFilter.rejection.notification.enabled";

    /**
     * The expected value is a string, containing the desired message for the
     * sender notification.
     */
    public static final String REJECTION_MSG_PROPERTY = "plugin.contentFilter.rejection.msg";

    /**
     * The expected value is a boolean, if true the value of #PATTERNS_PROPERTY
     * will be used for pattern matching.
     */
    public static final String PATTERNS_ENABLED_PROPERTY = "plugin.contentFilter.patterns.enabled";

    /**
     * The expected value is a comma separated string of regular expressions.
     */
    public static final String PATTERNS_PROPERTY = "plugin.contentFilter.patterns";

    /**
     * The expected value is a boolean, if true the value of #MASK_PROPERTY will
     * be used to mask matching content.
     */
    public static final String MASK_ENABLED_PROPERTY = "plugin.contentFilter.mask.enabled";

    /**
     * The expected value is a string. If this property is set any
     * matching content will not be rejected but masked with the given value.
     * Setting a content mask means that property #SENDER_NOTIFICATION_ENABLED_PROPERTY
     * is ignored. The default value is "**".
     */
    public static final String MASK_PROPERTY = "plugin.contentFilter.mask";

    /**
     * the hook into the inteceptor chain
     */
    private InterceptorManager interceptorManager;

    /**
     * used to send violation notifications
     */
    private MessageRouter messageRouter;

    /**
     * delegate that does the real work of this plugin
     */
    private ContentFilter contentFilter;

    /**
     * flags if sender should be notified of rejections
     */
    private boolean rejectionNotificationEnabled;

    /**
     * the rejection msg to send
     */
    private String rejectionMessage;

    /**
     * flags if content matches should result in admin notification
     */
    private boolean violationNotificationEnabled;

    /**
     * the admin user to send violation notifications to
     */
    private String violationContact;

    /**
     * flag if patterns should be used
     */
    private boolean patternsEnabled;

    /**
     * the patterns to use
     */
    private String patterns;

    /**
     * flag if mask should be used
     */
    private boolean maskEnabled;

    /**
     * the mask to use
     */
    private String mask;

    /**
     * violation notification messages will be from this JID
     */
    private JID violationNotificationFrom;

    public ContentFilterPlugin() {
        contentFilter = new ContentFilter();
        interceptorManager = InterceptorManager.getInstance();
        violationNotificationFrom = new JID(XMPPServer.getInstance()
                .getServerInfo().getName());
        messageRouter = XMPPServer.getInstance().getMessageRouter();
    }

    public boolean isMaskEnabled() {
        return maskEnabled;
    }

    public void setMaskEnabled(boolean enabled) {
        maskEnabled = enabled;
        JiveGlobals.setProperty(MASK_ENABLED_PROPERTY, enabled ? "true" : "false");

        changeContentFilterMask();
    }

    public void setMask(String mas) {
        mask = mas;
        JiveGlobals.setProperty(MASK_PROPERTY, mas);

        changeContentFilterMask();
    }

    private void changeContentFilterMask() {
        if (maskEnabled) {
            contentFilter.setMask(mask);
        }
        else {
            contentFilter.clearMask();
        }
    }

    public String getMask() {
        return mask;
    }

    public boolean isPatternsEnabled() {
        return patternsEnabled;
    }

    public void setPatternsEnabled(boolean enabled) {
        patternsEnabled = enabled;
        JiveGlobals.setProperty(PATTERNS_ENABLED_PROPERTY, enabled ? "true"
                : "false");

        changeContentFilterPatterns();
    }

    public void setPatterns(String patt) {
        patterns = patt;
        JiveGlobals.setProperty(PATTERNS_PROPERTY, patt);

        changeContentFilterPatterns();
    }

    private void changeContentFilterPatterns() {
        if (patternsEnabled) {
            contentFilter.setPatterns(patterns);
        }
        else {
            contentFilter.clearPatterns();
        }
    }

    public String getPatterns() {
        return patterns;
    }

    public boolean isRejectionNotificationEnabled() {
        return rejectionNotificationEnabled;
    }

    public void setRejectionNotificationEnabled(boolean enabled) {
        rejectionNotificationEnabled = enabled;
        JiveGlobals.setProperty(REJECTION_NOTIFICATION_ENABLED_PROPERTY,
                enabled ? "true" : "false");
    }

    public String getRejectionMessage() {
        return rejectionMessage;
    }

    public void setRejectionMessage(String message) {
        this.rejectionMessage = message;
        JiveGlobals.setProperty(REJECTION_MSG_PROPERTY, message);
    }

    public boolean isViolationNotificationEnabled() {
        return violationNotificationEnabled;
    }

    public void setViolationNotificationEnabled(boolean enabled) {
        violationNotificationEnabled = enabled;
        JiveGlobals.setProperty(VIOLATION_NOTIFICATION_ENABLED_PROPERTY,
                enabled ? "true" : "false");
    }

    public void setViolationContact(String contact) {
        violationContact = contact;
        JiveGlobals.setProperty(VIOLATION_NOTIFICATION_CONTACT_PROPERTY, contact);
    }

    public String getViolationContact() {
        return violationContact;
    }

    public void initializePlugin(PluginManager pManager, File pluginDirectory) {
        // configure this plugin
        initFilter();

        // register with interceptor manager
        interceptorManager.addInterceptor(this);
    }

    private void initFilter() {
        // default to false
        violationNotificationEnabled = JiveGlobals.getBooleanProperty(
                VIOLATION_NOTIFICATION_ENABLED_PROPERTY, false);

        // default to "admin"
        violationContact = JiveGlobals.getProperty(VIOLATION_NOTIFICATION_CONTACT_PROPERTY,
                "admin");

        // default to false
        rejectionNotificationEnabled = JiveGlobals.getBooleanProperty(
                REJECTION_NOTIFICATION_ENABLED_PROPERTY, false);

        // default to english
        rejectionMessage = JiveGlobals.getProperty(REJECTION_MSG_PROPERTY,
                "Message rejected. This is an automated server response");

        // default to false
        patternsEnabled = JiveGlobals.getBooleanProperty(PATTERNS_ENABLED_PROPERTY,
                false);

        //default to "fox,dog"
        patterns = JiveGlobals.getProperty(PATTERNS_PROPERTY, "fox,dog");

        changeContentFilterPatterns();

        // default to false
        maskEnabled = JiveGlobals.getBooleanProperty(MASK_ENABLED_PROPERTY, false);

        //default to "***"
        mask = JiveGlobals.getProperty(MASK_PROPERTY, "***");

        changeContentFilterMask();
    }

    /**
     * @see org.jivesoftware.wildfire.container.Plugin#destroyPlugin()
     */
    public void destroyPlugin() {
        // unregister with interceptor manager
        interceptorManager.removeInterceptor(this);
    }


    public void interceptPacket(Packet packet, Session session, boolean read,
            boolean processed) throws PacketRejectedException {
        if (patternsEnabled && !processed && (packet instanceof Message)) {
            Message msg = (Message) packet;

            // filter the message
            boolean contentMatched = contentFilter.filter(msg);

            // notify contact of violations
            if (contentMatched && violationNotificationEnabled) {
                sendViolationNotification(msg);
            }

            // reject the message if not masking content
            if (contentMatched && !maskEnabled) {
                PacketRejectedException rejected = new PacketRejectedException(
                        "Message rejected with disallowed content!");

                if (rejectionNotificationEnabled) {
                    // let the sender know about the rejection, this is
                    // only possible/useful if the content is not masked
                    rejected.setRejectionMessage(rejectionMessage);
                }

                throw rejected;
            }
        }
    }

    private void sendViolationNotification(Message offendingMsg) {
        String subject = "Content filter notification!";

        String msg = "Disallowed content detected in message from:"
                + offendingMsg.getFrom() + " to:" + offendingMsg.getTo()
                + ", message was "
                + (contentFilter.isMaskingContent() ? "altered" : "rejected");

        messageRouter.route(createServerMessage(subject, msg));
    }

    private Message createServerMessage(String subject, String body) {
        Message message = new Message();
        message.setTo(violationContact + "@"
                + violationNotificationFrom.getDomain());
        message.setFrom(violationNotificationFrom);
        message.setSubject(subject);
        message.setBody(body);
        return message;
    }
}