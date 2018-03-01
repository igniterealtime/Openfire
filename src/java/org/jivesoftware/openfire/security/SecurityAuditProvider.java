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

import java.util.List;
import java.util.Date;

/**
 * A SecurityAuditProvider handles storage and retrieval of security audit logs.  If set to
 * write-only, the logs are not viewable from Openfire's admin console.  An optional URL
 * can be provided for the location of where the logs can be viewed.
 * 
 * @author Daniel Henninger
 */
public interface SecurityAuditProvider {

    /**
     * Records a security event in the audit logs.
     *
     * @param username Username of user who performed the security event.
     * @param summary Short description of the event, similar to a subject.
     * @param details Detailed description of the event, can be null if not desired.
     */
    void logEvent( String username, String summary, String details );

    /**
     * Retrieves security events that have occurred, filtered by the parameters passed.
     * The events will be in order of most recent to least recent.  The provider is expected to
     * create and fill out to the best of it's knowledge a list of SecurityAuditEvent objects.
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
     */
    List<SecurityAuditEvent> getEvents( String username, Integer skipEvents, Integer numEvents, Date startTime, Date endTime );

    /**
     * Retrieves a specific event by ID.  The provider is expected to create and fill out to
     * the best of it's knowledge a SecurityAuditEvent object.
     *
     * @param msgID ID number of event to retrieve.
     * @return SecurityAuditEvent object with information from retrieved event.
     * @throws EventNotFoundException if event was not found.
     */
    SecurityAuditEvent getEvent( Integer msgID ) throws EventNotFoundException;

    /**
     * Retrieves number of events recorded.
     *
     * @return Number of events that have been recorded.
     */
    Integer getEventCount();

    /**
     * Returns true if the provider logs can be read by Openfire for display from Openfire's
     * own admin interface.  If false, the administrative interface will place a stub in place
     * to indicate that audit logs can not be read from this interface.  The provider can
     * specify a URL that will be displayed on this stub to point at where the logs can
     * be read.
     *
     * @see #getAuditURL()
     * @return True or false if the logs can be read remotely.
     */
    boolean isWriteOnly();

    /**
     * Retrieves a URL that can be visited to read the logs audited by this provider.  This
     * is typically used if you are referring to another interface that displays the audit
     * logs via another applications own interface.  This is only useful if isWriteOnly is set
     * to true.  You can safely return null to this if you don't need it, and also if you simply
     * do not have a URL for an audit viewer interface.  The URL will only be referenced if it
     * is not null.
     *
     * @see #isWriteOnly()
     * @return String represented URL that can be visited to view the audit logs.
     */
    String getAuditURL();

    /**
     * Returns true if the provider should not send user change (create, edit, delete, etc) related
     * security events on through.  This is typically used if the service the provider is working
     * with audits it's own user events and does not need Openfire to duplicate the event.
     *
     * @return True if we should block user related security audit events from being handled.
     */
    boolean blockUserEvents();

    /**
     * Returns true if the provider should not send group change (create, edit, delete, etc) related
     * security events on through.  This is typically used if the service the provider is working
     * with audits it's own group events and does not need Openfire to duplicate the event.
     *
     * @return True if we should block group related security audit events from being handled.
     */
    boolean blockGroupEvents();

}
