/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2023 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire.audit.spi;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.audit.AuditManager;
import org.jivesoftware.openfire.audit.Auditor;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

import java.io.File;
import java.time.Duration;
import java.util.*;

/**
 * Implementation of the AuditManager interface.
 */
public class AuditManagerImpl extends BasicModule implements AuditManager, PropertyEventListener {

    private boolean enabled;
    private boolean auditMessage;
    private boolean auditPresence;
    private boolean auditIQ;
    private boolean auditXPath;
    private List<String> xpath = new LinkedList<>();
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
     * Max duration to keep audit information. Once the limit has been reached
     * audit files that contain information that exceed the limit will be deleted.
     */
    private Duration retention;

    /**
     * the time between successive executions of the task that will save
     * the queued audited packets to a permanent store.
     */
    private Duration logTimeout;
    private String logDir;
    private Collection<String> ignoreList = new ArrayList<>();
    private static final int MAX_TOTAL_SIZE = 1000;
    private static final int MAX_FILE_SIZE = 10;
    private static final Duration MAX_DAYS = Duration.ofDays(-1);
    private static final Duration DEFAULT_LOG_TIMEOUT = Duration.ofMinutes(2);
    private AuditorInterceptor interceptor;

    public AuditManagerImpl() {
        super("Audit Manager");
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        JiveGlobals.setProperty("xmpp.audit.active", enabled ? "true" : "false");
        processEnabled(enabled);
    }

    @Override
    public Auditor getAuditor() {
        if (auditor == null) {
            throw new IllegalStateException("Must initialize audit manager first");
        }
        return auditor;
    }

    @Override
    public int getMaxTotalSize() {
        return maxTotalSize;
    }

    @Override
    public void setMaxTotalSize(int size) {
        maxTotalSize = size;
        auditor.setMaxValues(maxTotalSize, maxFileSize, retention);
        JiveGlobals.setProperty("xmpp.audit.totalsize", Integer.toString(size));
    }

    @Override
    public int getMaxFileSize() {
        return maxFileSize;
    }

    @Override
    public void setMaxFileSize(int size) {
        maxFileSize = size;
        auditor.setMaxValues(maxTotalSize, maxFileSize, retention);
        JiveGlobals.setProperty("xmpp.audit.filesize", Integer.toString(size));
    }

    @Override
    public Duration getRetention() {
        return retention;
    }

    @Override
    public void setRetention(Duration duration) {
        retention = validateDuration(duration);
        auditor.setMaxValues(maxTotalSize, maxFileSize, retention);
        JiveGlobals.setProperty("xmpp.audit.days", Long.toString(duration.toDays())); // TODO fix loss of precision while remaining compatible with existing properties.
    }

    @Override
    public Duration getLogTimeout() {
        return logTimeout;
    }

    @Override
    public void setLogTimeout(Duration logTimeout) {
        this.logTimeout = logTimeout;
        auditor.setLogTimeout(logTimeout);
        JiveGlobals.setProperty("xmpp.audit.logtimeout", String.valueOf(logTimeout.toMillis()));
    }

    @Override
    public String getLogDir() {
        return logDir;
    }

    @Override
    public void setLogDir(String logDir) {
        this.logDir = logDir;
        auditor.setLogDir(logDir);
        JiveGlobals.setProperty("xmpp.audit.logdir", logDir);
    }

    @Override
    public boolean isAuditMessage() {
        return auditMessage;
    }

    @Override
    public void setAuditMessage(boolean auditMessage) {
        this.auditMessage = auditMessage;
        JiveGlobals.setProperty("xmpp.audit.message", auditMessage ? "true" : "false");
    }

    @Override
    public boolean isAuditPresence() {
        return auditPresence;
    }

    @Override
    public void setAuditPresence(boolean auditPresence) {
        this.auditPresence = auditPresence;
        JiveGlobals.setProperty("xmpp.audit.presence", auditPresence ? "true" : "false");
    }

    @Override
    public boolean isAuditIQ() {
        return auditIQ;
    }

    @Override
    public void setAuditIQ(boolean auditIQ) {
        this.auditIQ = auditIQ;
        JiveGlobals.setProperty("xmpp.audit.iq", Boolean.toString(auditIQ));
    }

    @Override
    public boolean isAuditXPath() {
        return auditXPath;
    }

    @Override
    public void setAuditXPath(boolean auditXPath) {
        this.auditXPath = auditXPath;
        JiveGlobals.setProperty("xmpp.audit.xpath", Boolean.toString(auditXPath));
    }

    @Override
    public void addXPath(String xpathExpression) {
        xpath.add(xpathExpression);
        saveXPath();
    }

    @Override
    public void removeXPath(String xpathExpression) {
        xpath.remove(xpathExpression);
        saveXPath();
    }

    private void saveXPath() {
        // TODO: save XPath values!
        //String[] filters = new String[xpath.size()];
        //filters = (String[]) xpath.toArray(filters); 
    }

    @Override
    public Iterator<String> getXPathFilters() {
        return xpath.iterator();
    }

    @Override
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
                ignoreString.append(',').append(username);
            }
        }
        JiveGlobals.setProperty("xmpp.audit.ignore", ignoreString.toString());
    }

    @Override
    public Collection<String> getIgnoreList() {
        return Collections.unmodifiableCollection(ignoreList);
    }

    // #########################################################################
    // Basic module methods
    // #########################################################################

    @Override
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
        retention = Duration.ofDays(JiveGlobals.getIntProperty("xmpp.audit.days", (int)MAX_DAYS.toDays()));
        logTimeout = Duration.ofMillis(JiveGlobals.getIntProperty("xmpp.audit.logtimeout", (int)DEFAULT_LOG_TIMEOUT.toMillis()));
        logDir = JiveGlobals.getProperty("xmpp.audit.logdir", JiveGlobals.getHomePath() +
                File.separator + "logs");
        processIgnoreString(JiveGlobals.getProperty("xmpp.audit.ignore", ""));

        auditor = new AuditorImpl(this);
        auditor.setMaxValues(maxTotalSize, maxFileSize, retention);
        auditor.setLogDir(logDir);
        auditor.setLogTimeout(logTimeout);

        interceptor = new AuditorInterceptor();
        processEnabled(enabled);
        PropertyEventDispatcher.addListener(this);
    }

    private void processIgnoreString(String ignoreString) {
        ignoreList.clear();
        // Decode the ignore list
        StringTokenizer tokenizer = new StringTokenizer(ignoreString, ",");
        while (tokenizer.hasMoreTokens()) {
            String username = tokenizer.nextToken().trim();
            ignoreList.add(username);
        }
    }
    
    private void processEnabled(boolean enabled) {
        // Add or remove the auditor interceptor depending on the enabled status
        if (enabled) {
            InterceptorManager.getInstance().addInterceptor(interceptor);
        } else {
            InterceptorManager.getInstance().removeInterceptor(interceptor);
        }
    }

    private Duration validateDuration(Duration duration) {
        if (duration.isNegative()) {
            return Duration.ofDays(-1);
        }
        if (duration.isZero()) {
            return Duration.ofDays(1);
        }
        return duration;
    }
    
    @Override
    public void stop() {
        if (auditor != null) {
            auditor.stop();
        }
    }

    @Override
    public void propertySet(String property, Map<String, Object> params) {
        final Object val = params.get("value");
        if (!( val instanceof String ))
        {
            return;
        }
        String value = (String) val;
        switch (property) {
            case "xmpp.audit.active":
                enabled = Boolean.parseBoolean(value);
                processEnabled(enabled);
                break;
            case "xmpp.audit.message":
                auditMessage = Boolean.parseBoolean(value);
                break;
            case "xmpp.audit.presence":
                auditPresence = Boolean.parseBoolean(value);
                break;
            case "xmpp.audit.iq":
                auditIQ = Boolean.parseBoolean(value);
                break;
            case "xmpp.audit.xpath":
                auditXPath = Boolean.parseBoolean(value);
                break;
            case "xmpp.audit.totalsize":
                maxTotalSize = parseIntegerOrDefault(value, MAX_TOTAL_SIZE);
                auditor.setMaxValues(maxTotalSize, maxFileSize, retention);
                break;
            case "xmpp.audit.filesize":
                maxFileSize = parseIntegerOrDefault(value, MAX_FILE_SIZE);
                auditor.setMaxValues(maxTotalSize, maxFileSize, retention);
                break;
            case "xmpp.audit.days":
                retention = validateDuration(Duration.ofDays(parseIntegerOrDefault(value, (int)MAX_DAYS.toDays())));
                auditor.setMaxValues(maxTotalSize, maxFileSize, retention);
                break;
            case "xmpp.audit.logtimeout":
                logTimeout = Duration.ofMillis(parseIntegerOrDefault(value, (int)DEFAULT_LOG_TIMEOUT.toMillis()));
                auditor.setLogTimeout(logTimeout);
                break;
            case "xmpp.audit.logdir":
                File d = null;
                if (!"".equals(value.trim())) {
                    d = new File(value);
                }
                logDir = (d == null || !d.exists() || !d.canRead() || !d.canWrite() || !d
                        .isDirectory()) ? JiveGlobals.getHomePath()
                        + File.separator + "logs" : value;
                auditor.setLogDir(logDir);
                break;
            case "xmpp.audit.ignore":
                processIgnoreString(value);
                break;
        }
    }

    private int parseIntegerOrDefault(String intValue, int defaultValue) {
        try {
            return Integer.parseInt(intValue);
        } catch (NumberFormatException nfe) {
            return defaultValue;
        }
    }
    
    @Override
    public void propertyDeleted(String property, Map<String, Object> params) {
        propertySet(property, Collections.emptyMap());
    }

    @Override
    public void xmlPropertySet(String property, Map<String, Object> params) {
    }

    @Override
    public void xmlPropertyDeleted(String property, Map<String, Object> params) {
    }    
    
    private class AuditorInterceptor implements PacketInterceptor {

        @Override
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
