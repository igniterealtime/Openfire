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
import org.jivesoftware.messenger.container.ModuleContext;
import org.jivesoftware.messenger.audit.AuditManager;
import org.jivesoftware.messenger.audit.Auditor;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class AuditManagerImpl extends BasicModule implements AuditManager {

    private boolean enabled;
    private boolean auditMessage;
    private boolean auditPresence;
    private boolean auditIQ;
    private boolean auditXPath;
    private List xpath = new LinkedList();
    private ModuleContext context;
    private AuditorImpl auditor = null;
    private int maxSize;
    private int maxCount;
    private static final int MAX_FILE_SIZE = 10;
    private static final int MAX_FILE_COUNT = 10;

    public AuditManagerImpl() {
        super("Audit Manager");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        context.setProperty("xmpp.audit.active", enabled ? "true" : "false");
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
        context.setProperty("xmpp.audit.maxsize", Integer.toString(size));
    }

    public int getMaxFileCount() {
        return maxCount;
    }

    public void setMaxFileCount(int count) {
        maxCount = count;
        auditor.setMaxValues(maxSize, maxCount);
        context.setProperty("xmpp.audit.maxcount", Integer.toString(count));
    }

    public boolean isAuditMessage() {
        return auditMessage;
    }

    public void setAuditMessage(boolean auditMessage) {
        this.auditMessage = auditMessage;
        context.setProperty("xmpp.audit.message", auditMessage ? "true" : "false");
    }

    public boolean isAuditPresence() {
        return auditPresence;
    }

    public void setAuditPresence(boolean auditPresence) {
        this.auditPresence = auditPresence;
        context.setProperty("xmpp.audit.presence", auditPresence ? "true" : "false");
    }

    public boolean isAuditIQ() {
        return auditIQ;
    }

    public void setAuditIQ(boolean auditIQ) {
        this.auditIQ = auditIQ;
        if (auditIQ) {
            context.setProperty("xmpp.audit.iq", auditIQ ? "true" : "false");
        }
        else {
            context.setProperty("xmpp.audit.iq", "false");
        }
    }

    public boolean isAuditXPath() {
        return auditXPath;
    }

    public void setAuditXPath(boolean auditXPath) {
        this.auditXPath = auditXPath;
        context.setProperty("xmpp.audit.xpath", auditXPath ? "true" : "false");
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
        context.setProperties("xmpp.audit.filter.xpath", filters);
    }

    public Iterator getXPathFilters() {
        return xpath.iterator();
    }

    // #########################################################################
    // Basic module methods
    // #########################################################################

    public void initialize(ModuleContext context, Container container) {
        super.initialize(context, container);
        this.context = context;
        setEnabled("true".equals(context.getProperty("xmpp.audit.active")));
        setAuditMessage("true".equals(context.getProperty("xmpp.audit.message")));
        setAuditPresence("true".equals(context.getProperty("xmpp.audit.presence")));
        setAuditIQ("true".equals(context.getProperty("xmpp.audit.iq")));
        setAuditXPath("true".equals(context.getProperty("xmpp.audit.xpath")));
        String[] filters = context.getProperties("xmpp.audit.filter.xpath");
        for (int i = 0; i < filters.length; i++) {
            xpath.add(filters[i]);
        }
        saveXPath();
        String prop = context.getProperty("xmpp.audit.maxsize");
        maxSize = MAX_FILE_SIZE;
        if (prop != null && prop.trim().length() > 0) {
            maxSize = Integer.parseInt(prop);
        }
        prop = context.getProperty("xmpp.audit.maxcount");
        maxCount = MAX_FILE_COUNT;
        if (prop != null && prop.trim().length() > 0) {
            maxCount = Integer.parseInt(prop);
        }
        auditor = new AuditorImpl(this, context);
        auditor.setMaxValues(maxSize, maxCount);
    }

    public void stop() {
        if (auditor != null) {
            auditor.close();
        }
    }
}
