/*
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
package org.jivesoftware.openfire.security;

import java.util.Date;
import java.util.List;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The SecurityAuditManager manages the SecurityAuditProvider configured for this server, and provides
 * a proper conduit for making security log entries and looking them up.  Ideally there is no reason
 * for outside classes to interact directly with a provider.
 *
 * The provider can be specified in system properties by adding:
 *
 * <ul>
 * <li>{@code provider.securityAudit.className = my.security.audit.provider}</li>
 * </ul>
 *
 * @author Daniel Henninger
 */
public class SecurityAuditManager {

    public static final SystemProperty<Class> AUDIT_PROVIDER = SystemProperty.Builder.ofType(Class.class)
        .setKey("provider.securityAudit.className")
        .setBaseClass(SecurityAuditProvider.class)
        .setDefaultValue(DefaultSecurityAuditProvider.class)
        .addListener(SecurityAuditManager::initProvider)
        .setDynamic(true)
        .build();


    private static final Logger Log = LoggerFactory.getLogger(SecurityAuditManager.class);

    // Wrap this guy up so we can mock out the SecurityAuditManager class.
    private static class SecurityAuditManagerContainer {
        private static SecurityAuditManager instance = new SecurityAuditManager();
    }

    /**
     * Returns the currently-installed SecurityAuditProvider. <b>Warning:</b> in virtually all
     * cases the security audit provider should not be used directly. Instead, the appropriate
     * methods in SecurityAuditManager should be called. Direct access to the security audit
     * provider is only provided for special-case logic.
     *
     * @return the current SecurityAuditProvider.
     */
    public static SecurityAuditProvider getSecurityAuditProvider() {
        return SecurityAuditManagerContainer.instance.provider;
    }

    /**
     * Returns a singleton instance of SecurityAuditManager.
     *
     * @return a SecurityAuditManager instance.
     */
    public static SecurityAuditManager getInstance() {
        return SecurityAuditManagerContainer.instance;
    }

    private static SecurityAuditProvider provider;

    /**
     * Constructs a SecurityAuditManager, setting up the provider, and a listener.
     */
    private SecurityAuditManager() {
        // Load an security audit provider.
        initProvider(AUDIT_PROVIDER.getValue());
    }

    /**
     * Initializes the server's security audit provider, based on configuration and defaults to
     * DefaultSecurityAuditProvider if the specified provider is not valid or not specified.
     */
    private static void initProvider(final Class clazz) {
        if (provider == null || !clazz.equals(provider.getClass())) {
            try {
                provider = (SecurityAuditProvider) clazz.newInstance();
            }
            catch (Exception e) {
                Log.error("Error loading security audit provider: " + clazz.getName(), e);
                provider = new DefaultSecurityAuditProvider();
            }
        }
    }

    /**
     * Records a security event in the audit logs.
     *
     * @param username Username of user who performed the security event.
     * @param summary Short description of the event, similar to a subject.
     * @param details Detailed description of the event, can be null if not desired.
     */
    public void logEvent(String username, String summary, String details) {
        provider.logEvent(username, summary, details);
    }

    /**
     * Retrieves security events that have occurred, filtered by the parameters passed.
     * The events will be in order of most recent to least recent.
     *
     * Any parameters that are left null are to be ignored.  In other words, if username is null,
     * then no specific username is being searched for.
     *
     * @param username Username of user to look up.  Can be null for no username filtering.
     * @param skipEvents Number of events to skip past (typically for paging).  Can be null for first page.
     * @param numEvents Number of events to retrieve.  Can be null for "all" events.
     * @param startTime Oldest date of range of events to retrieve.  Can be null for forever.
     * @param endTime Most recent date of range of events to retrieve.  Can be null for "now".
     * @return Array of security events.
     * @throws AuditWriteOnlyException if provider can not be read from.
     */
    public List<SecurityAuditEvent> getEvents(String username, Integer skipEvents, Integer numEvents, Date startTime, Date endTime) throws AuditWriteOnlyException {
        if (provider.isWriteOnly()) {
            throw new AuditWriteOnlyException();
        }
        return provider.getEvents(username, skipEvents, numEvents, startTime, endTime);
    }

    /**
     * Retrieves a specific event by ID in the form of a SecurityAuditEvent.
     *
     * @param msgID ID number of event to retrieve.
     * @return SecurityAuditEvent object with information from retrieved event.
     * @throws EventNotFoundException if event was not found.
     * @throws AuditWriteOnlyException if provider can not be read from.
     */
    public SecurityAuditEvent getEvent(Integer msgID) throws EventNotFoundException, AuditWriteOnlyException {
        if (provider.isWriteOnly()) {
            throw new AuditWriteOnlyException();
        }
        return provider.getEvent(msgID);
    }

}
