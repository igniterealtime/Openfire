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

import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.container.Container;
import org.jivesoftware.messenger.audit.AuditManager;
import org.jivesoftware.messenger.audit.Auditor;
import org.jivesoftware.messenger.JiveGlobals;

import java.util.*;

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
    private static final int MAX_FILE_SIZE = 10;
    private static final int MAX_FILE_COUNT = 10;
    private static final int DEFAULT_LOG_TIMEOUT = 120000;

    public AuditManagerImpl() {
        super("Audit Manager");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        JiveGlobals.setProperty("xmpp.audit.active", enabled ? "true" : "false");
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
        filters = (String[])xpath.toArray(filters);
        // TODO: save XPath values!
    }

    public Iterator getXPathFilters() {
        return xpath.iterator();
    }

    // #########################################################################
    // Basic module methods
    // #########################################################################

    public void initialize(Container container) {
        super.initialize(container);
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
        auditor = new AuditorImpl(this);
        auditor.setMaxValues(maxSize, maxCount);
        auditor.setLogTimeout(logTimeout);
    }

    public void stop() {
        if (auditor != null) {
            auditor.stop();
        }
    }
}
