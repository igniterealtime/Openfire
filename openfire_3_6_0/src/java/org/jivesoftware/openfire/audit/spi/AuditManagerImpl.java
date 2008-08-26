/**
 * $RCSfile$
 * $Revision: 1632 $
 * $Date: 2005-07-15 02:49:00 -0300 (Fri, 15 Jul 2005) $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.audit.spi;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.audit.AuditManager;
import org.jivesoftware.openfire.audit.Auditor;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.session.Session;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

import java.io.File;
import java.util.*;

/**
 * Implementation of the AuditManager interface.
 */
public class AuditManagerImpl extends BasicModule implements AuditManager {

    private boolean enabled;
    private boolean auditMessage;
    private boolean auditPresence;
    private boolean auditIQ;
    private boolean auditXPath;
    private List xpath = new LinkedList();
    private AuditorImpl auditor = null;
    /**
     * Max size in bytes that all audit log files may have. When the limit is reached
     * oldest audit log files will be removed until total size is under the limit.
     */
    private int maxTotalSize;
    /**
     * Max size in bytes that each audit log file may have. Once the limit has been
     * reached a new audit file will be created.
     */
    private int maxFileSize;
    /**
     * Max number of days to keep audit information. Once the limit has been reached
     * audit files that contain information that exceed the limit will be deleted.
     */
    private int maxDays;
    private int logTimeout;
    private String logDir;
    private Collection<String> ignoreList = new ArrayList<String>();
    private static final int MAX_TOTAL_SIZE = 1000;
    private static final int MAX_FILE_SIZE = 10;
    private static final int MAX_DAYS = -1;
    private static final int DEFAULT_LOG_TIMEOUT = 120000;
    private AuditorInterceptor interceptor;

    public AuditManagerImpl() {
        super("Audit Manager");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        JiveGlobals.setProperty("xmpp.audit.active", enabled ? "true" : "false");
        // Add or remove the auditor interceptor depending on the enabled status
        if (enabled) {
            InterceptorManager.getInstance().addInterceptor(interceptor);
        }
        else {
            InterceptorManager.getInstance().removeInterceptor(interceptor);
        }
    }

    public Auditor getAuditor() {
        if (auditor == null) {
            throw new IllegalStateException("Must initialize audit manager first");
        }
        return auditor;
    }

    public int getMaxTotalSize() {
        return maxTotalSize;
    }

    public void setMaxTotalSize(int size) {
        maxTotalSize = size;
        auditor.setMaxValues(maxTotalSize, maxFileSize, maxDays);
        JiveGlobals.setProperty("xmpp.audit.totalsize", Integer.toString(size));
    }

    public int getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(int size) {
        maxFileSize = size;
        auditor.setMaxValues(maxTotalSize, maxFileSize, maxDays);
        JiveGlobals.setProperty("xmpp.audit.filesize", Integer.toString(size));
    }

    public int getMaxDays() {
        return maxDays;
    }

    public void setMaxDays(int count) {
        if (count < -1) {
            count = -1;
        }
        if (count == 0) {
            count = 1;
        }
        maxDays = count;
        auditor.setMaxValues(maxTotalSize, maxFileSize, maxDays);
        JiveGlobals.setProperty("xmpp.audit.days", Integer.toString(count));
    }

    public int getLogTimeout() {
        return logTimeout;
    }

    public void setLogTimeout(int logTimeout) {
        this.logTimeout = logTimeout;
        auditor.setLogTimeout(logTimeout);
        JiveGlobals.setProperty("xmpp.audit.logtimeout", Integer.toString(logTimeout));
    }

    public String getLogDir() {
        return logDir;
    }

    public void setLogDir(String logDir) {
        this.logDir = logDir;
        auditor.setLogDir(logDir);
        JiveGlobals.setProperty("xmpp.audit.logdir", logDir);
    }

    public boolean isAuditMessage() {
        return auditMessage;
    }

    public void setAuditMessage(boolean auditMessage) {
        this.auditMessage = auditMessage;
        JiveGlobals.setProperty("xmpp.audit.message", auditMessage ? "true" : "false");
    }

    public boolean isAuditPresence() {
        return auditPresence;
    }

    public void setAuditPresence(boolean auditPresence) {
        this.auditPresence = auditPresence;
        JiveGlobals.setProperty("xmpp.audit.presence", auditPresence ? "true" : "false");
    }

    public boolean isAuditIQ() {
        return auditIQ;
    }

    public void setAuditIQ(boolean auditIQ) {
        this.auditIQ = auditIQ;
        JiveGlobals.setProperty("xmpp.audit.iq", Boolean.toString(auditIQ));
    }

    public boolean isAuditXPath() {
        return auditXPath;
    }

    public void setAuditXPath(boolean auditXPath) {
        this.auditXPath = auditXPath;
        JiveGlobals.setProperty("xmpp.audit.xpath", Boolean.toString(auditXPath));
    }

    public void addXPath(String xpathExpression) {
        xpath.add(xpathExpression);
        saveXPath();
    }

    public void removeXPath(String xpathExpression) {
        xpath.remove(xpathExpression);
        saveXPath();
    }

    private void saveXPath() {
        String[] filters = new String[xpath.size()];
        filters = (String[]) xpath.toArray(filters);
        // TODO: save XPath values!
    }

    public Iterator getXPathFilters() {
        return xpath.iterator();
    }

    public void setIgnoreList(Collection<String> usernames) {
        if (ignoreList.equals(usernames)) {
            return;
        }
        ignoreList = usernames;
        // Encode the collection
        StringBuilder ignoreString = new StringBuilder();
        for (String username : ignoreList) {
            if (ignoreString.length() == 0) {
                ignoreString.append(username);
            }
            else {
                ignoreString.append(",").append(username);
            }
        }
        JiveGlobals.setProperty("xmpp.audit.ignore", ignoreString.toString());
    }

    public Collection<String> getIgnoreList() {
        return Collections.unmodifiableCollection(ignoreList);
    }

    // #########################################################################
    // Basic module methods
    // #########################################################################

    public void initialize(XMPPServer server) {
        super.initialize(server);
        enabled = JiveGlobals.getBooleanProperty("xmpp.audit.active");
        auditMessage = JiveGlobals.getBooleanProperty("xmpp.audit.message");
        auditPresence = JiveGlobals.getBooleanProperty("xmpp.audit.presence");
        auditIQ = JiveGlobals.getBooleanProperty("xmpp.audit.iq");
        auditXPath = JiveGlobals.getBooleanProperty("xmpp.audit.xpath");
        // TODO: load xpath values!
//        String[] filters = context.getProperties("xmpp.audit.filter.xpath");
//        for (int i = 0; i < filters.length; i++) {
//            xpath.add(filters[i]);
//        }
        maxTotalSize = JiveGlobals.getIntProperty("xmpp.audit.totalsize", MAX_TOTAL_SIZE);
        maxFileSize = JiveGlobals.getIntProperty("xmpp.audit.filesize", MAX_FILE_SIZE);
        maxDays = JiveGlobals.getIntProperty("xmpp.audit.days", MAX_DAYS);
        logTimeout = JiveGlobals.getIntProperty("xmpp.audit.logtimeout", DEFAULT_LOG_TIMEOUT);
        logDir = JiveGlobals.getProperty("xmpp.audit.logdir", JiveGlobals.getHomeDirectory() +
                File.separator + "logs");
        String ignoreString = JiveGlobals.getProperty("xmpp.audit.ignore", "");
        // Decode the ignore list
        StringTokenizer tokenizer = new StringTokenizer(ignoreString, ", ");
        while (tokenizer.hasMoreTokens()) {
            String username = tokenizer.nextToken();
            ignoreList.add(username);
        }

        auditor = new AuditorImpl(this);
        auditor.setMaxValues(maxTotalSize, maxFileSize, maxDays);
        auditor.setLogDir(logDir);
        auditor.setLogTimeout(logTimeout);

        interceptor = new AuditorInterceptor();
        if (enabled) {
            InterceptorManager.getInstance().addInterceptor(interceptor);
        }
    }

    public void stop() {
        if (auditor != null) {
            auditor.stop();
        }
    }

    private class AuditorInterceptor implements PacketInterceptor {

        public void interceptPacket(Packet packet, Session session, boolean read, boolean processed) {
            if (!processed) {
                // Ignore packets sent or received by users that are present in the ignore list
                JID from = packet.getFrom();
                JID to = packet.getTo();
                if ((from == null || !ignoreList.contains(from.getNode())) &&
                        (to == null || !ignoreList.contains(to.getNode()))) {
                    auditor.audit(packet, session);
                }
            }
        }
    }
}
