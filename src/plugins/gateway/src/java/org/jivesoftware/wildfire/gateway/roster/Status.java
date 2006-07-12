/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway.roster;

import java.io.Serializable;

/**
 * Status object.
 * 
 * @author Noah Campbell
 */
public final class Status implements Serializable {
    
    /** The serialVersionUID. */
    private static final long serialVersionUID = 1L;
    
    
    /** The subscribed. */
    private boolean subscribed = false;
    
    
    /** The online. */
    private boolean online = false;
    
    /** The value. */
    private String value;
    /**
     * @return status a string representation of the status's state.
     */
    public String getValue() { return value; }
    /**
     * @param value the new value of the status
     */
    public void updateValue(String value) { this.value = value; }
    /**
     * @return Returns the subscribed.
     */
    public boolean isSubscribed() {
        return subscribed;
    }
    /**
     * @param subscribed The subscribed to set.
     */
    public void setSubscribed(boolean subscribed) {
        this.subscribed = subscribed;
    }
    /**
     * @return Returns the online.
     */
    public boolean isOnline() {
        return this.online;
    }
    /**
     * @param online The online to set.
     */
    public void setOnline(boolean online) {
        this.online = online;
    }
    
}
