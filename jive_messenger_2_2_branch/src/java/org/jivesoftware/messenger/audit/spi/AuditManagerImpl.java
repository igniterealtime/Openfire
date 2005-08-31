/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.audit.spi;

import org.jivesoftware.messenger.Session;
import org.jivesoftware.messenger.XMPPServer;
import org.jivesoftware.messenger.audit.AuditManager;
import org.jivesoftware.messenger.audit.Auditor;
import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.interceptor.InterceptorManager;
import org.jivesoftware.messenger.interceptor.PacketInterceptor;
import org.jivesoftware.util.JiveGlobals;
import org.xmpp.packet.Packet;
import org.xmpp.packet.JID;

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
    private int maxSize;
    private int maxCount;
    private int logTimeout;
    private String logDir;
    private Collection<String> ignoreList = new ArrayList<String>();
    private static final int MAX_FILE_SIZE = 10;
    private static final int MAX_FILE_COUNT = 10;
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

    public int getMaxFileSize() {
        return maxSize;
    }

    public void setMaxFileSize(int size) {
        maxSize = size;
        auditor.setMaxValues(maxSize, maxCount);
        JiveGlobals.setProperty("xmpp.audit.maxsize", Integer.toString(size));
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

    public int getMaxFileCount() {
        return maxCount;
    }

    public void setMaxFileCount(int count) {
        maxCount = count;
        auditor.setMaxValues(maxSize, maxCount);
        JiveGlobals.setProperty("xmpp.audit.maxcount", Integer.toString(count));
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
        maxSize = JiveGlobals.getIntProperty("xmpp.audit.maxsize", MAX_FILE_SIZE);
        maxCount = JiveGlobals.getIntProperty("xmpp.audit.maxcount", MAX_FILE_COUNT);
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
        auditor.setMaxValues(maxSize, maxCount);
        auditor.setLogTimeout(logTimeout);
        auditor.setLogDir(logDir);

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
