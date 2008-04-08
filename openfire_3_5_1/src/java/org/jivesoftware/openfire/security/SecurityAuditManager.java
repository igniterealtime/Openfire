/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package org.jivesoftware.openfire.security;

import org.jivesoftware.util.*;

import java.util.Map;
import java.util.List;
import java.util.Date;

/**
 * The SecurityAuditManager manages the SecurityAuditProvider configured for this server, and provides
 * a proper conduit for making security log entries and looking them up.  Ideally there is no reason
 * for outside classes to interact directly with a provider.
 *
 * The provider can be specified in <tt>openfire.xml</tt> by adding:
 *  ...
 *    <provider>
 *       <securityAudit>
 *          <className>my.security.audit.provider</className>
 *       </securityAudit>
 *    </provider>
 *  ...
 *
 * @author Daniel Henninger
 */
public class SecurityAuditManager {

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

    private SecurityAuditProvider provider;

    /**
     * Constructs a SecurityAuditManager, setting up the provider, and a listener.
     */
    private SecurityAuditManager() {
        // Load an security audit provider.
        initProvider();

        // Detect when a new security audit provider class is set
        PropertyEventListener propListener = new PropertyEventListener() {
            public void propertySet(String property, Map params) {
                //Ignore
            }

            public void propertyDeleted(String property, Map params) {
                //Ignore
            }

            public void xmlPropertySet(String property, Map params) {
                if ("provider.securityAudit.className".equals(property)) {
                    initProvider();
                }
            }

            public void xmlPropertyDeleted(String property, Map params) {
                //Ignore
            }
        };
        PropertyEventDispatcher.addListener(propListener);
    }

    /**
     * Initializes the server's security audit provider, based on configuration and defaults to
     * DefaultSecurityAuditProvider if the specified provider is not valid or not specified.
     */
    private void initProvider() {
        String className = JiveGlobals.getXMLProperty("provider.securityAudit.className",
                "org.jivesoftware.openfire.security.DefaultSecurityAuditProvider");
        // Check if we need to reset the provider class
        if (provider == null || !className.equals(provider.getClass().getName())) {
            try {
                Class c = ClassUtils.forName(className);
                provider = (SecurityAuditProvider) c.newInstance();
            }
            catch (Exception e) {
                Log.error("Error loading security audit provider: " + className, e);
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
