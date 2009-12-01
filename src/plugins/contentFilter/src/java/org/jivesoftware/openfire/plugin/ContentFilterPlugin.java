/**
 * $RCSfile$
 * $Revision: 1594 $
 * $Date: 2005-07-04 18:08:42 +0100 (Mon, 04 Jul 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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
import java.util.regex.PatternSyntaxException;

import org.jivesoftware.openfire.MessageRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.EmailService;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

/**
 * Content filter plugin.
 * 
 * @author Conor Hayes
 */
public class ContentFilterPlugin implements Plugin, PacketInterceptor {

	private static final Logger Log = LoggerFactory.getLogger(ContentFilterPlugin.class);

    /**
     * The expected value is a boolean, if true the user identified by the value
     * of the property #VIOLATION_NOTIFICATION_CONTACT_PROPERTY will be notified
     * every time there is a content match, otherwise no notification will be
     * sent. Then default value is false.
     */
    public static final String VIOLATION_NOTIFICATION_ENABLED_PROPERTY = "plugin.contentFilter.violation.notification.enabled";

    /**
     * The expected value is a user name. The default value is "admin".
     */
    public static final String VIOLATION_NOTIFICATION_CONTACT_PROPERTY = "plugin.contentFilter.violation.notification.contact";

    /**
     * The expected value is a boolean, if true the user identified by the value
     * of the property #VIOLATION_NOTIFICATION_CONTACT_PROPERTY, will also
     * receive a copy of the offending packet. The default value is false.
     */
    public static final String VIOLATION_INCLUDE_ORIGNAL_PACKET_ENABLED_PROPERTY = "plugin.contentFilter.violation.notification.include.original.enabled";

    /**
     * The expected value is a boolean, if true the user identified by the value
     * of the property #VIOLATION_NOTIFICATION_CONTACT_PROPERTY, will receive
     * notification by IM. The default value is true.
     */
    public static final String VIOLATION_NOTIFICATION_BY_IM_ENABLED_PROPERTY = "plugin.contentFilter.violation.notification.by.im.enabled";

    /**
     * The expected value is a boolean, if true the user identified by the value
     * of the property #VIOLATION_NOTIFICATION_CONTACT_PROPERTY, will receive
     * notification by email. The default value is false.
     */
    public static final String VIOLATION_NOTIFICATION_BY_EMAIL_ENABLED_PROPERTY = "plugin.contentFilter.violation.notification.by.email.enabled";

    /**
     * The expected value is a boolean, if true the sender will be notified when
     * a message is rejected, otherwise the message will be silently
     * rejected,i.e. the sender will not know that the message was rejected and
     * the receiver will not get the message. The default value is false.
     */
    public static final String REJECTION_NOTIFICATION_ENABLED_PROPERTY = "plugin.contentFilter.rejection.notification.enabled";

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
     * The expected value is a boolean, if true Presence packets will be
     * filtered
     */
    public static final String FILTER_STATUS_ENABLED_PROPERTY = "plugin.contentFilter.filter.status.enabled";

    /**
     * The expected value is a boolean, if true the value of #MASK_PROPERTY will
     * be used to mask matching content.
     */
    public static final String MASK_ENABLED_PROPERTY = "plugin.contentFilter.mask.enabled";

    /**
     * The expected value is a string. If this property is set any matching
     * content will not be rejected but masked with the given value. Setting a
     * content mask means that property #SENDER_NOTIFICATION_ENABLED_PROPERTY is
     * ignored. The default value is "**".
     */
    public static final String MASK_PROPERTY = "plugin.contentFilter.mask";
    
    /**
     * The expected value is a boolean, if false packets whose contents matches one
     * of the supplied regular expressions will be rejected, otherwise the packet will
     * be accepted and may be optionally masked. The default value is false.
     * @see #MASK_ENABLED_PROPERTY
     */
    public static final String ALLOW_ON_MATCH_PROPERTY = "plugin.contentFilter.allow.on.match";

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
     * flags if original packet should be included in the message to the
     * violation contact.
     */
    private boolean violationIncludeOriginalPacketEnabled;

    /**
     * flags if violation contact should be notified by IM.
     */
    private boolean violationNotificationByIMEnabled;

    /**
     * flags if violation contact should be notified by email.
     */
    private boolean violationNotificationByEmailEnabled;

    /**
     * flag if patterns should be used
     */
    private boolean patternsEnabled;

    /**
     * the patterns to use
     */
    private String patterns;

    /**
     * flag if Presence packets should be filtered.
     */
    private boolean filterStatusEnabled;

    /**
     * flag if mask should be used
     */
    private boolean maskEnabled;

    /**
     * the mask to use
     */
    private String mask;
    
    /**
     * flag if matching content should be accepted or rejected. 
     */
    private boolean allowOnMatch;
    
    /**
     * violation notification messages will be from this JID
     */
    private JID violationNotificationFrom;

    public ContentFilterPlugin() {
        contentFilter = new ContentFilter();
        interceptorManager = InterceptorManager.getInstance();
        violationNotificationFrom = new JID(XMPPServer.getInstance()
                .getServerInfo().getXMPPDomain());
        messageRouter = XMPPServer.getInstance().getMessageRouter();
    }

    /**
     * Restores the plugin defaults.
     */
    public void reset() {
        setViolationNotificationEnabled(false);
        setViolationContact("admin");
        setViolationNotificationByIMEnabled(true);
        setViolationNotificationByEmailEnabled(false);
        setViolationIncludeOriginalPacketEnabled(false);
        setRejectionNotificationEnabled(false);
        setRejectionMessage("Message rejected. This is an automated server response");
        setPatternsEnabled(false);
        setPatterns("fox,dog");        
        setFilterStatusEnabled(false);
        setMaskEnabled(false);
        setMask("***");
        setAllowOnMatch(false);
    }
    
    public boolean isAllowOnMatch() {
        return allowOnMatch;
    }
    
    public void setAllowOnMatch(boolean allow) {
        allowOnMatch = allow;
        JiveGlobals.setProperty(ALLOW_ON_MATCH_PROPERTY, allow ? "true"
                : "false");
        
        changeContentFilterMask();
    }
    
    public boolean isMaskEnabled() {
        return maskEnabled;
    }

    public void setMaskEnabled(boolean enabled) {
        maskEnabled = enabled;
        JiveGlobals.setProperty(MASK_ENABLED_PROPERTY, enabled ? "true"
                : "false");

        changeContentFilterMask();
    }

    public void setMask(String mas) {
        mask = mas;
        JiveGlobals.setProperty(MASK_PROPERTY, mas);

        changeContentFilterMask();
    }

    private void changeContentFilterMask() {
        if (allowOnMatch && maskEnabled) {
            contentFilter.setMask(mask);
        } else {
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

    public boolean isFilterStatusEnabled() {
        return filterStatusEnabled;
    }

    public void setFilterStatusEnabled(boolean enabled) {
        filterStatusEnabled = enabled;
        JiveGlobals.setProperty(FILTER_STATUS_ENABLED_PROPERTY,
                enabled ? "true" : "false");
    }

    private void changeContentFilterPatterns() {
        if (patternsEnabled) {
            contentFilter.setPatterns(patterns);
        } else {
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
        JiveGlobals.setProperty(VIOLATION_NOTIFICATION_CONTACT_PROPERTY,
                contact);
    }

    public String getViolationContact() {
        return violationContact;
    }

    public boolean isViolationIncludeOriginalPacketEnabled() {
        return violationIncludeOriginalPacketEnabled;
    }

    public void setViolationIncludeOriginalPacketEnabled(boolean enabled) {
        violationIncludeOriginalPacketEnabled = enabled;
        JiveGlobals.setProperty(
                VIOLATION_INCLUDE_ORIGNAL_PACKET_ENABLED_PROPERTY,
                enabled ? "true" : "false");
    }

    public boolean isViolationNotificationByIMEnabled() {
        return violationNotificationByIMEnabled;
    }

    public void setViolationNotificationByIMEnabled(boolean enabled) {
        violationNotificationByIMEnabled = enabled;
        JiveGlobals.setProperty(VIOLATION_NOTIFICATION_BY_IM_ENABLED_PROPERTY,
                enabled ? "true" : "false");
    }

    public boolean isViolationNotificationByEmailEnabled() {
        return violationNotificationByEmailEnabled;
    }

    public void setViolationNotificationByEmailEnabled(boolean enabled) {
        violationNotificationByEmailEnabled = enabled;
        JiveGlobals.setProperty(
                VIOLATION_NOTIFICATION_BY_EMAIL_ENABLED_PROPERTY,
                enabled ? "true" : "false");
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
        violationContact = JiveGlobals.getProperty(
                VIOLATION_NOTIFICATION_CONTACT_PROPERTY, "admin");

        // default to true
        violationNotificationByIMEnabled = JiveGlobals.getBooleanProperty(
                VIOLATION_NOTIFICATION_BY_IM_ENABLED_PROPERTY, true);

        // default to false
        violationNotificationByEmailEnabled = JiveGlobals.getBooleanProperty(
                VIOLATION_NOTIFICATION_BY_EMAIL_ENABLED_PROPERTY, false);

        // default to false
        violationIncludeOriginalPacketEnabled = JiveGlobals.getBooleanProperty(
                VIOLATION_INCLUDE_ORIGNAL_PACKET_ENABLED_PROPERTY, false);

        // default to false
        rejectionNotificationEnabled = JiveGlobals.getBooleanProperty(
                REJECTION_NOTIFICATION_ENABLED_PROPERTY, false);

        // default to english
        rejectionMessage = JiveGlobals.getProperty(REJECTION_MSG_PROPERTY,
                "Message rejected. This is an automated server response");

        // default to false
        patternsEnabled = JiveGlobals.getBooleanProperty(
                PATTERNS_ENABLED_PROPERTY, false);

        // default to "fox,dog"
        patterns = JiveGlobals.getProperty(PATTERNS_PROPERTY, "fox,dog");

        try {
            changeContentFilterPatterns();
        }
        catch (PatternSyntaxException e) {
            Log.warn("Resetting to default patterns of ContentFilterPlugin", e);
            // Existing patterns are invalid so reset to default ones
            setPatterns("fox,dog");
        }

        // default to false
        filterStatusEnabled = JiveGlobals.getBooleanProperty(
                FILTER_STATUS_ENABLED_PROPERTY, false);

        // default to false
        maskEnabled = JiveGlobals.getBooleanProperty(MASK_ENABLED_PROPERTY,
                false);       

        // default to "***"
        mask = JiveGlobals.getProperty(MASK_PROPERTY, "***");
        
        // default to false
        allowOnMatch = JiveGlobals.getBooleanProperty(
                ALLOW_ON_MATCH_PROPERTY, false);
        
        //v1.2.2 backwards compatibility
        if (maskEnabled) {
            allowOnMatch = true;
        }
        
        changeContentFilterMask();
    }

    /**
     * @see org.jivesoftware.openfire.container.Plugin#destroyPlugin()
     */
    public void destroyPlugin() {
        // unregister with interceptor manager
        interceptorManager.removeInterceptor(this);
    }

    public void interceptPacket(Packet packet, Session session, boolean read,
            boolean processed) throws PacketRejectedException {

        if (isValidTargetPacket(packet, read, processed)) {

            Packet original = packet;

            if (Log.isDebugEnabled()) {
                Log.debug("Content filter: intercepted packet:"
                        + original.toString());
            }

            // make a copy of the original packet only if required,
            // as it's an expensive operation
            if (violationNotificationEnabled
                    && violationIncludeOriginalPacketEnabled && maskEnabled) {
                original = packet.createCopy();
            }

            // filter the packet
            boolean contentMatched = contentFilter.filter(packet);

            if (Log.isDebugEnabled()) {
                Log.debug("Content filter: content matched? " + contentMatched);
            }

            // notify admin of violations
            if (contentMatched && violationNotificationEnabled) {

                if (Log.isDebugEnabled()) {
                    Log.debug("Content filter: sending violation notification");
                    Log.debug("Content filter: include original msg? "
                            + this.violationIncludeOriginalPacketEnabled);
                }

                sendViolationNotification(original);
            }

            // msg will either be rejected silently, rejected with
            // some notification to sender, or allowed and optionally masked.
            // allowing a message without masking can be useful if the admin
            // simply wants to get notified of matches without interrupting
            // the conversation in the  (spy mode!)
            if (contentMatched) {
                
                if (allowOnMatch) {
                                        
                    if (Log.isDebugEnabled()) {
                        Log.debug("Content filter: allowed content:"
                                + packet.toString());
                    }
                    
                    // no further action required
                    
                } else {
                    // msg must be rejected
                    if (Log.isDebugEnabled()) {
                        Log.debug("Content filter: rejecting packet");
                    }

                    PacketRejectedException rejected = new PacketRejectedException(
                            "Packet rejected with disallowed content!");

                    if (rejectionNotificationEnabled) {
                        // let the sender know about the rejection, this is
                        // only possible/useful if the content is not masked
                        rejected.setRejectionMessage(rejectionMessage);
                    }

                    throw rejected;
                }
            }
        }
    }

    private boolean isValidTargetPacket(Packet packet, boolean read,
            boolean processed) {
        return patternsEnabled
                && !processed
                && read
                && (packet instanceof Message || (filterStatusEnabled && packet instanceof Presence));
    }

    private void sendViolationNotification(Packet originalPacket) {
        String subject = "Content filter notification! ("
                + originalPacket.getFrom().getNode() + ")";

        String body;
        if (originalPacket instanceof Message) {
            Message originalMsg = (Message) originalPacket;
            body = "Disallowed content detected in message from:"
                    + originalMsg.getFrom()
                    + " to:"
                    + originalMsg.getTo()
                    + ", message was "
                    + (allowOnMatch ? "allowed" + (contentFilter.isMaskingContent() ? " and masked." : " but not masked.") : "rejected.")
                    + (violationIncludeOriginalPacketEnabled ? "\nOriginal subject:"
                            + (originalMsg.getSubject() != null ? originalMsg
                                    .getSubject() : "")
                            + "\nOriginal content:"
                            + (originalMsg.getBody() != null ? originalMsg
                                    .getBody() : "")
                            : "");

        } else {
            // presence
            Presence originalPresence = (Presence) originalPacket;
            body = "Disallowed status detected in presence from:"
                    + originalPresence.getFrom()
                    + ", status was "
                    + (allowOnMatch ? "allowed" + (contentFilter.isMaskingContent() ? " and masked." : " but not masked.") : "rejected.")
                    + (violationIncludeOriginalPacketEnabled ? "\nOriginal status:"
                            + originalPresence.getStatus()
                            : "");
        }

        if (violationNotificationByIMEnabled) {

            if (Log.isDebugEnabled()) {
                Log.debug("Content filter: sending IM notification");
            }
            sendViolationNotificationIM(subject, body);
        }

        if (violationNotificationByEmailEnabled) {

            if (Log.isDebugEnabled()) {
                Log.debug("Content filter: sending email notification");
            }
            sendViolationNotificationEmail(subject, body);
        }
    }

    private void sendViolationNotificationIM(String subject, String body) {
        Message message = createServerMessage(subject, body);
        // TODO consider spining off a separate thread here,
        // in high volume situations, it will result in
        // in faster response and notification is not required
        // to be real time.
        messageRouter.route(message);
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

    private void sendViolationNotificationEmail(String subject, String body) {
        try {
            User user = UserManager.getInstance().getUser(violationContact);
            
            //this is automatically put on a another thread for execution.
            EmailService.getInstance().sendMessage(user.getName(), user.getEmail(), "Openfire",
                "no_reply@" + violationNotificationFrom.getDomain(), subject, body, null);

        }
        catch (Throwable e) {
            // catch throwable in case email setup is invalid
            Log.error("Content Filter: Failed to send email, please review Openfire setup", e);
        }
    }
}