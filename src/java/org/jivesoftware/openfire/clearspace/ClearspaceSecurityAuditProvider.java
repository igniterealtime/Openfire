/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package org.jivesoftware.openfire.clearspace;

import org.jivesoftware.openfire.security.SecurityAuditProvider;
import org.jivesoftware.openfire.security.SecurityAuditEvent;
import org.jivesoftware.openfire.security.EventNotFoundException;

import java.util.List;
import java.util.Date;

/**
 * The ClearspaceSecurityAuditProvider uses the AuditService web service inside of Clearspace
 * to send audit logs into Clearspace's own audit handler.  It also refers the admin to a URL
 * inside the Clearspace admin console where they can view the logs.
 *
 * @author Daniel Henninger
 */
public class ClearspaceSecurityAuditProvider implements SecurityAuditProvider {

    /**
     * Generate a ClearspaceSecurityAuditProvider instance.
     */
    public ClearspaceSecurityAuditProvider() {

    }

    /**
     * The ClearspaceSecurityAuditProvider will log events into Clearspace via the AuditService
     * web service, provided by Clearspace.
     * @see org.jivesoftware.openfire.security.SecurityAuditProvider#logEvent(String, String, String)
     */
    public void logEvent(String username, String summary, String details) {
        // TODO: Will need to log event.
    }

    /**
     * The ClearspaceSecurityAuditProvider does not retrieve audit entries from Clearspace.  Instead
     * it refers the admin to a URL where they can read the logs.
     * @see org.jivesoftware.openfire.security.SecurityAuditProvider#getEvents(String, Integer, Integer, java.util.Date, java.util.Date)
     */
    public List<SecurityAuditEvent> getEvents(String username, Integer skipEvents, Integer numEvents, Date startTime, Date endTime) {
        // This is not used.
        return null;
    }

    /**
     * The ClearspaceSecurityAuditProvider does not retrieve audit entries from Clearspace.  Instead
     * it refers the admin to a URL where they can read the logs.
     * @see org.jivesoftware.openfire.security.SecurityAuditProvider#getEvent(Integer)
     */
    public SecurityAuditEvent getEvent(Integer msgID) throws EventNotFoundException {
        // This is not used.
        return null;
    }

    /**
     * The ClearspaceSecurityAuditProvider does not retrieve audit entries from Clearspace.  Instead
     * it refers the admin to a URL where they can read the logs.
     * @see org.jivesoftware.openfire.security.SecurityAuditProvider#isWriteOnly()
     */
    public boolean isWriteOnly() {
        return true;
    }

    /**
     * The ClearspaceSecurityAuditProvider does not retrieve audit entries from Clearspace.  Instead
     * it refers the admin to a URL where they can read the logs.
     * @see org.jivesoftware.openfire.security.SecurityAuditProvider#getAuditURL()
     */
    public String getAuditURL() {
        // TODO: Retrieve proper URL and set.
        return null;
    }

}
