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
import static org.jivesoftware.openfire.clearspace.ClearspaceManager.HttpType.POST;
import org.jivesoftware.util.Log;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

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

    protected static final String AUDIT_URL_PREFIX = "auditService/";

    private ClearspaceManager manager;

    /**
     * Generate a ClearspaceSecurityAuditProvider instance.
     */
    public ClearspaceSecurityAuditProvider() {
        // Gets the manager
        manager = ClearspaceManager.getInstance();
    }

    /**
     * The ClearspaceSecurityAuditProvider will log events into Clearspace via the AuditService
     * web service, provided by Clearspace.
     * @see org.jivesoftware.openfire.security.SecurityAuditProvider#logEvent(String, String, String)
     */
    public void logEvent(String username, String summary, String details) {
        try {
            // Request to log event
            String path = AUDIT_URL_PREFIX + "audit";

            // Creates the XML with the data
            Document auditDoc =  DocumentHelper.createDocument();
            Element rootE = auditDoc.addElement("auditEvent");
            Element userE = rootE.addElement("username");
            userE.addText(username);
            Element descE = rootE.addElement("description");
            if (summary != null) {
                descE.addText(summary);
            }
            Element detlE = rootE.addElement("details");
            if (details != null) {
                detlE.addText(details);
            }

            manager.executeRequest(POST, path, auditDoc.asXML());
        }
        catch (Exception e) {
            // Error while setting properties?
            Log.error("Unable to send audit log via REST service to Clearspace:", e);
        }
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
     * @see org.jivesoftware.openfire.security.SecurityAuditProvider#getEventCount() 
     */
    public Integer getEventCount() {
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
        String url = ClearspaceManager.getInstance().getConnectionURI();
        if (url != null) {
            url += "admin/view-audit-log.jspa";
            return url;
        }
        else {
            return null;
        }
    }

    /**
     * Clearspace handles logging it's own user events.
     * @see org.jivesoftware.openfire.security.SecurityAuditProvider#blockUserEvents()
     */
    public boolean blockUserEvents() {
        return true;
    }

    /**
     * Clearspace handles logging it's own group events.
     * @see org.jivesoftware.openfire.security.SecurityAuditProvider#blockGroupEvents()
     */
    public boolean blockGroupEvents() {
        return true;
    }

}
