/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package org.jivesoftware.openfire.security;

import org.jivesoftware.database.JiveID;
import org.jivesoftware.util.JiveConstants;

import java.util.Date;

/**
 * Representation of a single security event retrieved from the logs.  This should include any
 * information that you would need regarding the event.
 *
 * @author Daniel Henninger
 */
@JiveID(JiveConstants.SECURITY_AUDIT)
public class SecurityAuditEvent {

    private long msgID;
    private String username;
    private Date eventStamp;
    private String summary;
    private String node;
    private String details;

    /**
     * Retrieves the unique ID of this event.
     * @return the ID.
     */
    public long getMsgID() {
        return msgID;
    }

    /**
     * Sets the unique ID of this event.
     * @param msgID the ID.
     */
    public void setMsgID(long msgID) {
        this.msgID = msgID;
    }

    /**
     * Retrieves the username of the user who performed this event.
     * @return the username.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the username of the user who performed this event.
     * @param username Username of user.
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Retrieves the time stamp of when this event occurred.
     * @return The time stamp as a Date object.
     */
    public Date getEventStamp() {
        return eventStamp;
    }

    /**
     * Sets the time stamp of when this event occurred.
     * @param eventStamp The time stamp as a Date object.
     */
    public void setEventStamp(Date eventStamp) {
        this.eventStamp = eventStamp;
    }

    /**
     * Returns the summary, or short description of what transpired in the event.
     * @return The summary.
     */
    public String getSummary() {
        return summary;
    }

    /**
     * Sets the summary, or short description of what transpired in the event.
     * @param summary The summary.
     */
    public void setSummary(String summary) {
        this.summary = summary;
    }

    /**
     * Retrieves the node that triggered the event, usually a hostname or IP address.
     * @return The node.
     */
    public String getNode() {
        return node;
    }

    /**
     * Sets the node that triggered the event, usually a hostname or IP address.
     * @param node Hostname or IP address.
     */
    public void setNode(String node) {
        this.node = node;
    }

    /**
     * Retrieves detailed information about what occurred in the event.
     * @return The possibly long details of the event.  Can be null.
     */
    public String getDetails() {
        return details;
    }

    /**
     * Sets the detailed information about what occured in the event.
     * @param details The possibly long details of the event.  Can be null.
     */
    public void setDetails(String details) {
        this.details = details;
    }

}
